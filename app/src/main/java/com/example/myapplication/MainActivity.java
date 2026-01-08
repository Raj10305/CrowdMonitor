package com.example.myapplication;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.myapplication.api.ApiClient;
import com.example.myapplication.api.ApiService;
import com.example.myapplication.models.CrowdResponse;
import com.example.myapplication.models.FrameRequest;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private PreviewView previewView;
    private TextView statusLabel, countLabel;
    private LinearLayout statusBox;
    private Button btnSend, btnHistory;

    private ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private ProcessCameraProvider cameraProvider;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startCamera();
                else Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        statusLabel = findViewById(R.id.statusLabel);
        countLabel = findViewById(R.id.countLabel);
        statusBox = findViewById(R.id.statusBox);
        btnSend = findViewById(R.id.btnSend);
        btnHistory = findViewById(R.id.btnHistory);

        btnSend.setOnClickListener(v -> captureOnce());

        btnHistory.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, HistoryActivity.class));
        });

        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            startCamera();
        else
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        networkExecutor.shutdown();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindCamera();
            } catch (Exception e) {
                Log.e(TAG, "Camera error", e);
            }
        }, getMainExecutor());
    }

    private void bindCamera() {
        if (cameraProvider == null) return;

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview
        );
    }

    private void captureOnce() {
        Bitmap bmp = previewView.getBitmap();
        if (bmp == null) {
            Toast.makeText(this, "Failed to capture frame", Toast.LENGTH_SHORT).show();
            return;
        }
        sendFrameToServer(bmp);
    }

    private void sendFrameToServer(Bitmap bitmap) {
        networkExecutor.submit(() -> {
            try {
                Bitmap scaled = Bitmap.createScaledBitmap(bitmap, 640, 480, true);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                scaled.compress(Bitmap.CompressFormat.JPEG, 70, baos);
                String encoded = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

                ApiService api = ApiClient.getClient().create(ApiService.class);
                api.detectCrowd(new FrameRequest(encoded)).enqueue(new Callback<CrowdResponse>() {
                    @Override
                    public void onResponse(Call<CrowdResponse> call, Response<CrowdResponse> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            updateUI("ERROR", 0, "#555555");
                            return;
                        }

                        int count = response.body().count;
                        String level, color;

                        if (count < 10) { level = "LOW"; color = "#25C26E"; }
                        else if (count < 25) { level = "MEDIUM"; color = "#E3B505"; }
                        else { level = "HIGH"; color = "#E63946"; }

                        updateUI(level, count, color);
                    }

                    @Override
                    public void onFailure(Call<CrowdResponse> call, Throwable t) {
                        updateUI("OFFLINE", 0, "#555555");
                    }
                });

                scaled.recycle();
                bitmap.recycle();
            } catch (Exception ignored) {}
        });
    }

    private void updateUI(String level, int count, String color) {
        runOnUiThread(() -> {
            statusLabel.setText(level);
            countLabel.setText(" (" + count + ")");
            statusBox.setBackgroundColor(android.graphics.Color.parseColor(color));
        });
    }
}
