package com.example.deviceapp;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.content.pm.PackageManager;
import android.Manifest;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE_SCREEN_CAPTURE = 1000;
    private static final int REQUEST_CODE_PERMISSIONS = 1001;
    
    private TextView statusText;
    private Button startButton;
    private Button stopButton;
    
    private WebSocketService webSocketService;
    private ScreenCaptureService screenCaptureService;
    private MediaProjectionManager projectionManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initViews();
        setupClickListeners();
        
        // 初始化MediaProjectionManager
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        
        // 检查和请求必要的权限
        checkAndRequestPermissions();
        
        updateStatus("Ready to start");
    }
    
    /**
     * 检查和请求必要的权限
     */
    private void checkAndRequestPermissions() {
        // 检查是否支持屏幕录制
        if (projectionManager == null) {
            updateStatus("Device does not support screen capture");
            startButton.setEnabled(false);
            Toast.makeText(this, "设备不支持屏幕录制功能", Toast.LENGTH_LONG).show();
            return;
        }
        
        // 检查网络权限（如果需要）
        String[] permissions = {
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE
        };
        
        boolean allPermissionsGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false;
                break;
            }
        }
        
        if (!allPermissionsGranted) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_PERMISSIONS);
        } else {
            updateStatus("Permissions granted - Ready to start");
        }
        
        Log.d(TAG, "Permission check completed");
    }
    
    /**
     * 处理权限请求结果
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (allGranted) {
                updateStatus("All permissions granted - Ready to start");
                Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show();
            } else {
                updateStatus("Some permissions denied");
                Toast.makeText(this, "部分权限被拒绝，功能可能受限", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    private void initViews() {
        statusText = findViewById(R.id.status_text);
        startButton = findViewById(R.id.start_button);
        stopButton = findViewById(R.id.stop_button);
        
        stopButton.setEnabled(false);
    }
    
    private void setupClickListeners() {
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startScreenCapture();
            }
        });
        
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopScreenCapture();
            }
        });
    }
    
    private void setupServiceCommunication() {
        // Use a handler to delay the setup to ensure services are started
        new android.os.Handler().postDelayed(() -> {
            try {
                // Get service instances through static references or other means
                // This is a simplified approach - in production you might use bound services
                Log.d(TAG, "Setting up service communication");
            } catch (Exception e) {
                Log.e(TAG, "Error setting up service communication", e);
            }
        }, 1000);
    }
    
    private void startScreenCapture() {
        // 再次检查MediaProjectionManager
        if (projectionManager == null) {
            Toast.makeText(this, "屏幕录制服务不可用", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            Intent captureIntent = projectionManager.createScreenCaptureIntent();
            startActivityForResult(captureIntent, REQUEST_CODE_SCREEN_CAPTURE);
            updateStatus("Requesting screen capture permission...");
        } catch (Exception e) {
            Log.e(TAG, "Error starting screen capture", e);
            Toast.makeText(this, "启动屏幕录制失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            updateStatus("Failed to start screen capture");
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        Log.d(TAG, "onActivityResult - requestCode: " + requestCode + ", resultCode: " + resultCode + ", data: " + data);
        
        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE) {
            if (resultCode == RESULT_OK) {
                Log.d(TAG, "Screen capture permission granted");
                
                // Start WebSocket service first
                Intent webSocketIntent = new Intent(this, WebSocketService.class);
                startService(webSocketIntent);
                
                // Delay starting ScreenCaptureService to ensure WebSocketService is ready
                new android.os.Handler().postDelayed(() -> {
                    Intent captureIntent = new Intent(this, ScreenCaptureService.class);
                    captureIntent.putExtra("resultCode", resultCode);
                    captureIntent.putExtra("data", data);
                    startService(captureIntent);
                }, 500); // 500ms delay
                
                startButton.setEnabled(false);
                stopButton.setEnabled(true);
                updateStatus("Screen capture started");
                Toast.makeText(this, "屏幕录制已开始", Toast.LENGTH_SHORT).show();
            } else {
                Log.w(TAG, "Screen capture permission denied");
                updateStatus("Screen capture permission denied");
                Toast.makeText(this, "屏幕录制权限被拒绝", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void stopScreenCapture() {
        // Stop services
        stopService(new Intent(this, WebSocketService.class));
        stopService(new Intent(this, ScreenCaptureService.class));
        
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        updateStatus("Screen capture stopped");
        
        Toast.makeText(this, "Screen capture stopped", Toast.LENGTH_SHORT).show();
    }
    
    private void updateStatus(String status) {
        statusText.setText("Status: " + status);
    }
}

