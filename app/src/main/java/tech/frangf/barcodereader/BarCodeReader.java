package tech.frangf.barcodereader;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.Image;

import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;

import java.io.Closeable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@androidx.camera.core.ExperimentalGetImage
public class BarCodeReader implements Closeable {
    private static int cameraRequestCode = 100;
    private ExecutorService executorService;
    private PreviewView previewView;
    private Activity activity;
    private BarcodeScannerOptions options;
    private OnSuccessBarCodeReader listener;
    private ImageAnalysis imageAnalysis;

    interface OnSuccessBarCodeReader {
        void onGetBarcode(Barcode barcode);
        void onErrorBarcode(Exception e);
    }

    public BarCodeReader(Activity activity, OnSuccessBarCodeReader listener, PreviewView previewView) {
        this.activity = activity;
        this.listener = listener;
        this.previewView = previewView;
    }

    public static int getCameraRequestCode() {
        return cameraRequestCode;
    }

    public static void setCameraRequestCode(int cameraRequestCode) {
        BarCodeReader.cameraRequestCode = cameraRequestCode;
    }

    public void setPreviewView(PreviewView previewView) {
        this.previewView = previewView;
    }

    public void setBarcodeScannerOptions(BarcodeScannerOptions barcodeScannerOptions) {
        this.options = barcodeScannerOptions;
    }

    public BarcodeScannerOptions getBarcodeScannerOptions() {
        if (options == null) {
            options = new BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                    .build();
        }
        return options;
    }

    public void checkPermissionsAndStart() {
        if (isCameraPermissionGranted(activity)) {
            start();
        } else {
            ActivityCompat.requestPermissions(
                    activity, new String[]{Manifest.permission.CAMERA}, cameraRequestCode);
        }
    }

    public void startAnalyzer() {
        imageAnalysis.setAnalyzer(getExecutorService(), getAnalyzer());
    }

    public void stopAnalyzer() {
        imageAnalysis.clearAnalyzer();
    }

    @Override
    public void close() {
        executorService.shutdown();
        imageAnalysis.clearAnalyzer();
        previewView = null;
        executorService = null;
        imageAnalysis = null;
        listener = null;
        activity = null;
        options = null;
    }

    public void start() {
        //Creamos petición asíncrona para solicitar la cámara.
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(activity);
        //Creamos buffer intermedio entre la cámara (hardware) y el componente que lo muestra previewView(View)
        Preview preview = new Preview.Builder().build();
        //Nos subscribimos al listener que nos índica cuando se nos permite el acceso a la cámara
        cameraProviderFuture.addListener(() -> {
            try {
                //Obtenemos la instancia del proceso de la cámara y establecerlo a un ciclo de vida.
                ProcessCameraProvider processCameraProvider = cameraProviderFuture.get();
                //Conectamos el buffer de la cámara con la vista
                preview.setSurfaceProvider(previewView.createSurfaceProvider());

                //Crear objeto encargado de analizar
                imageAnalysis = new ImageAnalysis.Builder().build();
                //Establecer executor y listener al objeto encargado de analizar:
                //Para ejecutar la tarea en otro hilo
                //Para obtener cada vez que se obtiene un código de barras
                imageAnalysis.setAnalyzer(getExecutorService(), getAnalyzer());
                //Desvinculamos todos los casos del ciclo de vida de CameraX
                processCameraProvider.unbindAll();
                //Establecemos el proceso de la cámara con el ciclo de vida actual.
                processCameraProvider.bindToLifecycle((LifecycleOwner) activity, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(activity));

    }

    public static boolean isCameraPermissionGranted(Activity activity) {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private ImageAnalysis.Analyzer getAnalyzer() {
        return imageProxy -> {
            //Obtener la imagen que se está mostrándo en la cámara
            Image currentCameraImage = imageProxy.getImage();
            //Obtener InputImage que nos permite analizar la imagen
            InputImage image = InputImage.fromMediaImage(currentCameraImage, imageProxy.getImageInfo().getRotationDegrees());
            //Crear escaner que analizará la imagen
            BarcodeScanner scanner = BarcodeScanning.getClient(getBarcodeScannerOptions());
            //Escanear imagen y establecer listeners
            scanner.process(image)
                    //Cuando no ocurra ningún error.
                    .addOnSuccessListener(barcodes -> {
                        //Leer todos los códigos de barras mostrados
                        for (Barcode barcode : barcodes) {
                            if (listener != null) {
                                listener.onGetBarcode(barcode);
                            }
                        }
                    })
                    //Cuando ocurre algún error
                    .addOnFailureListener(e -> {
                        if (listener != null) listener.onErrorBarcode(e);
                    })
                    //Cuando se termina se cierran los flujos
                    .addOnCompleteListener((barcodes) -> {
                        currentCameraImage.close();
                        imageProxy.close();
                    });
        };
    }

    private ExecutorService getExecutorService() {
        if (executorService == null) {
            executorService = Executors.newSingleThreadExecutor();
        }
        return executorService;
    }
}
