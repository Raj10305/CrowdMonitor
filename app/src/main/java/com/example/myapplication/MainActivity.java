package com.example.myapplication;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.util.Size;
import android.widget.TextView;
import android.widget.Toast;

import com.example.myapplication.api.ApiClient;
import com.example.myapplication.api.ApiService;
import com.example.myapplication.models.CrowdResponse;
import com.example.myapplication.models.FrameRequest;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private PreviewView previewView;
    private TextView crowdText;
    private long lastSent = 0L;

    private ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private ProcessCameraProvider cameraProvider;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startCamera();
                } else {
                    Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        crowdText = findViewById(R.id.crowdText);

        // Check if permission already granted
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Restart camera if permission is granted and camera was previously initialized
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            if (cameraProvider != null) {
                bindCameraUseCases();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        networkExecutor.shutdown();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (Exception e) {
                Log.e(TAG, "startCamera error", e);
            }
        }, getMainExecutor());
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) {
            Log.w(TAG, "bindCameraUseCases: cameraProvider is null");
            return;
        }

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis imageAnalysis =
                new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

        imageAnalysis.setAnalyzer(
                Executors.newSingleThreadExecutor(),
                image -> {
                    long now = System.currentTimeMillis();

                    if (now - lastSent < 1500) {   // throttle: 1 frame per 1.5s
                        image.close();
                        return;
                    }

                    Bitmap bmp = imageProxyToBitmap(image);
                    image.close(); // release camera buffer immediately

                    if (bmp != null) {
                        lastSent = now;
                        sendFrameToServer(bmp);
                    }
                }
        );

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
            );
            Log.d(TAG, "Camera bound successfully");
        } catch (Exception e) {
            Log.e(TAG, "bindCameraUseCases error", e);
        }
    }

    // Convert ImageProxy → Bitmap
    private Bitmap imageProxyToBitmap(ImageProxy image) {
        try {
            ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
            ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
            ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];

            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            YuvImage yuvImage = new YuvImage(
                    nv21,
                    ImageFormat.NV21,
                    image.getWidth(),
                    image.getHeight(),
                    null
            );

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 80, out);

            byte[] jpeg = out.toByteArray();
            return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);

        } catch (Exception e) {
            Log.e(TAG, "imageProxyToBitmap error", e);
            return null;
        }
    }

    // Send frame to Python server (offloaded to network thread)
    private void sendFrameToServer(Bitmap bitmap) {

        networkExecutor.submit(() -> {
            try {
                // downscale to reduce network & CPU load
                Bitmap small = Bitmap.createScaledBitmap(bitmap, 640, 480, true);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                small.compress(Bitmap.CompressFormat.JPEG, 40, baos);

                String encoded = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

                ApiService api = ApiClient.getClient().create(ApiService.class);
                FrameRequest request = new FrameRequest(encoded);
                Call<CrowdResponse> call = api.detectCrowd(request);

                call.enqueue(new Callback<CrowdResponse>() {
                    @Override
                    public void onResponse(Call<CrowdResponse> call, Response<CrowdResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            int count = response.body().count;
                            String label = (count < 10) ? "LOW" :
                                    (count < 25) ? "MEDIUM" : "HIGH";

                            runOnUiThread(() -> {
                                crowdText.setText(label + " (" + count + ")");
                                Log.d(TAG, "Crowd count: " + count);
                            });
                        } else {
                            Log.e(TAG, "Server error: " + response.code());
                            runOnUiThread(() -> crowdText.setText("ERROR: " + response.code()));
                        }
                    }

                    @Override
                    public void onFailure(Call<CrowdResponse> call, Throwable t) {
                        Log.e(TAG, "API call failed", t);
                        runOnUiThread(() -> crowdText.setText("CONNECTION ERROR"));
                    }
                });

                small.recycle();
                bitmap.recycle();

            } catch (Exception e) {
                Log.e(TAG, "sendFrameToServer error", e);
            }
        });
    }
}