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
    private OnScanCodeReader listener;
    private ImageAnalysis imageAnalysis;

    /**
     * Listener que se ejecuta cuando escanea
     */
    interface OnScanCodeReader {
        /**
         * Obtienes el código de barras
         * @param barcode Código de barras leido
         */
        void onGetBarcode(Barcode barcode);
        /**
         * Obtienes la excepción
         * @param e excepción recibida
         */
        void onError(Exception e);
    }

    /**
     * Construye un objeto BarCodeReader que te permite abrir la cámara y escanear códigos de barra y QR.
     * @param activity activity desde la que abres la cámara.
     * @param listener OnScanCodeReader que ejecuta void onGetBarcode(Barcode barcode) o void onError(Exception e).
     * @param previewView Objeto obtenido del Layout que permite la visualizacion de la camara en su interior.
     */
    public BarCodeReader(Activity activity, OnScanCodeReader listener, PreviewView previewView) {
        this.activity = activity;
        this.listener = listener;
        this.previewView = previewView;
    }

    /**
     * Obtiene el requestCode que se pasa al método ActivityCompat.requestPermissions
     * @return el valor del requestCode
     */
    public static int getCameraRequestCode() {
        return cameraRequestCode;
    }

    /**
     * Establece el valor del requestCode pasado al método ActivityCompat.requestPermissions.
     * Se debe de establece antes de ejecutar checkPermissionsAndStart()
     * @param cameraRequestCode el nuevo valor para el requestCode
     */
    public static void setCameraRequestCode(int cameraRequestCode) {
        BarCodeReader.cameraRequestCode = cameraRequestCode;
    }

    /**
     * Si no se ha establecido ninguna opción para escanear de la clase Barcode (FORMAT_CODE_128, FORMAT_EAN_8, FORMAT_EAN_13,...)
     * se establece por defecto FORMAT_ALL_FORMATS.
     * @return el valor establecido y sino el asignado por defecto.
     */
    public BarcodeScannerOptions getBarcodeScannerOptions() {
        if (options == null) {
            options = new BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                    .build();
        }
        return options;
    }

    /**
     * Establece las opciones para escanear, en caso de no hacerlo se utilizará FORMAT_ALL_FORMATS.
     * @param barcodeScannerOptions
     */
    public void setBarcodeScannerOptions(BarcodeScannerOptions barcodeScannerOptions) {
        this.options = barcodeScannerOptions;
    }

    /**
     * Comprueba que tiene permisos para acivar la cámara y la inicia, y en su defecto solicita los permisos al usuario.
     */
    public void checkPermissionsAndStart() {
        if (isCameraPermissionGranted(activity)) {
            start();
        } else {
            ActivityCompat.requestPermissions(
                    activity, new String[]{Manifest.permission.CAMERA}, cameraRequestCode);
        }
    }

    /**
     * Inicia de nuevo el analizador luego de utilizar el método stopAnalyzer().
     */
    public void restartAnalyzer() {
        imageAnalysis.setAnalyzer(getExecutorService(), getAnalyzer());
    }

    /**
     * Para el analizador.
     */
    public void stopAnalyzer() {
        imageAnalysis.clearAnalyzer();
    }

    /**
     * Cierra los flujos y conexiones, y establece los valores de la clase a null.
     */
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

    /**
     * Inicia la cámara trasera mostrándose en el objeto PreviewView del Layout,
     * estableciendo el analizador que se ejecuta en cada frame con sus opciones de escaneo establecidas y en caso
     * de no establecerlas por defecto analizará todos los tipos de códigos de barras y QR.
     * El analizador ejecutará el listener OnScanCodeReader.onGetBarcode en caso de procesar adecuadamente el código de barras o QR.
     * El analizador ejecutará el listener OnScanCodeReader.onError en caso de NO procesar adecuadamente el código de barras o QR.
     */
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

    /**
     * Comprueba si el permiso de la cámara está concedido por el usuario.
     * @param activity activity para la que se comprueban los permisos
     * @return devuelve true en caso de tener permisos, false en caso contrario.
     */
    public static boolean isCameraPermissionGranted(Activity activity) {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Establece el analizador que trabajará con cada frame, con las opciones de escaneo definidas o las establecidas por defecto
     * @return ImageAnalysis.Analyzer utilizado para anlizar cada frame.
     */
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
                        if (listener != null) listener.onError(e);
                    })
                    //Cuando se termina se cierran los flujos
                    .addOnCompleteListener((barcodes) -> {
                        currentCameraImage.close();
                        imageProxy.close();
                    });
        };
    }

    /**
     * obtiene un ExecutorService que permite la ejecución del análisis en otro hilo.
     * @return
     */
    private ExecutorService getExecutorService() {
        if (executorService == null) {
            executorService = Executors.newSingleThreadExecutor();
        }
        return executorService;
    }
}
