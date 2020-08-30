package tech.frangf.barcodereader;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@androidx.camera.core.ExperimentalGetImage
public class MainActivityCopy extends AppCompatActivity {
    private final int CAMERA_REQUEST_CODE = 100;
    private PreviewView viewFinder;
    private ExecutorService executorService;

    private boolean isCameraPermissionGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        viewFinder = findViewById(R.id.previewView);
        if (isCameraPermissionGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this, new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST_CODE);
        }
        executorService = Executors.newSingleThreadExecutor();
    }

    private void startCamera() {
        //Creamos petición asíncrona para solicitar la cámara.
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        //Creamos buffer intermedio entre la cámara (hardware) y el componente que lo muestra (View)
        Preview preview = new Preview.Builder().build();

        //Nos subscribimos al listener que nos índica cuando se nos permite el acceso a la cámara
        cameraProviderFuture.addListener(() -> {
            Log.d("CAMERAX", "cameraProviderFuture.addListener");
            try {
                //Obtenemos la instancia del proceso de la cámara y establecerlo a un ciclo de vida.
                ProcessCameraProvider processCameraProvider = cameraProviderFuture.get();
                //Conectamos el buffer de la cámara con la vista
                preview.setSurfaceProvider(viewFinder.createSurfaceProvider());

                //Crear objeto encargado de analizar
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder().build();
                //Establecer executor y listener al objeto encargado de analizar:
                    //Para ejecutar la tarea en otro hilo
                    //Para obtener cada vez que se obtiene un código de barras
                imageAnalysis.setAnalyzer(executorService, getAnalyzer());

                //Desvinculamos todos los casos del ciclo de vida de CameraX
                processCameraProvider.unbindAll();
                //Establecemos el proceso de la cámara con el ciclo de vida actual.
                Camera camera = processCameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private ImageAnalysis.Analyzer getAnalyzer() {
        return imageProxy -> {
            BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                    .build();
            Image mediaImage = imageProxy.getImage();
            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
            BarcodeScanner scanner = BarcodeScanning.getClient(options);
            Task<List<Barcode>> result =  scanner.process(image).addOnSuccessListener(barcodes -> {
                Log.d("CAMERAX", "ON SUCCESS GETTING BARCODES...");
                for (Barcode barcode : barcodes) {
                    Rect bounds = barcode.getBoundingBox();
                    Point[] corners = barcode.getCornerPoints();
                    String rawValue = barcode.getRawValue();
                    Log.v("CAMERAX", barcode.getDisplayValue());
                    int valueType = barcode.getValueType();
                    // See API reference for complete list of supported types
                    switch (valueType) {
                        case Barcode.TYPE_WIFI:
                            String ssid = barcode.getWifi().getSsid();
                            String password = barcode.getWifi().getPassword();
                            int type = barcode.getWifi().getEncryptionType();
                            Log.d("CAMERAX", ssid + " " + password);
                            break;
                        case Barcode.TYPE_URL:
                            String title = barcode.getUrl().getTitle();
                            String url = barcode.getUrl().getUrl();
                            break;
                    }
                }
            }).addOnFailureListener(e -> Log.d("CAMERAX", e.getMessage())
            ).addOnCompleteListener((barcodes)->{
                mediaImage.close();
                imageProxy.close();
            });
        };
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == CAMERA_REQUEST_CODE) {
            if (isCameraPermissionGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permisos no establecidos", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}