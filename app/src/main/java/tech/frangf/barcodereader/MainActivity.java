package tech.frangf.barcodereader;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;

import com.google.mlkit.vision.barcode.Barcode;

@androidx.camera.core.ExperimentalGetImage
public class MainActivity extends AppCompatActivity {
    private BarCodeReader barCodeReader;
    private PreviewView previewView;
    private Button btn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btn =findViewById(R.id.btn);
        previewView = findViewById(R.id.previewView);
        final MediaPlayer mediaPlayer = MediaPlayer.create(this,R.raw.beep);
        BarCodeReader.OnScanCodeReader listener = new BarCodeReader.OnScanCodeReader() {
            @Override
            public void onGetBarcode(Barcode barcode) {
                Log.d("CAMERAX", barcode.getDisplayValue());
                Toast.makeText(getApplicationContext(),barcode.getDisplayValue(),Toast.LENGTH_SHORT).show();
                barCodeReader.stopAnalyzer();
                final Handler handler = new Handler();
                handler.postDelayed(() -> barCodeReader.restartAnalyzer(), 500);
                mediaPlayer.start();
            }

            @Override
            public void onError(Exception e) {
                Log.d("CAMERAX", e.getMessage());
            }
        };
        barCodeReader = new BarCodeReader(this, listener, previewView);
        btn.setOnClickListener(v->{
            barCodeReader.restartAnalyzer();
        });
        barCodeReader.checkPermissionsAndStart();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == BarCodeReader.getCameraRequestCode()) {
            if (BarCodeReader.isCameraPermissionGranted(this)) {
                barCodeReader.start();
            } else {
                Toast.makeText(this, "Permisos no establecidos", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        barCodeReader.close();
        super.onDestroy();
    }
}