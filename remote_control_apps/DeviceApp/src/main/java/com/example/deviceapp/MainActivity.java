package com.example.deviceapp;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQUEST_CODE_SCREEN_CAPTURE = 1000;
    
    private TextView statusText;
    private Button startButton;
    private Button stopButton;
    
    private WebSocketService webSocketService;
    private ScreenCaptureService screenCaptureService;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initViews();
        setupClickListeners();
        updateStatus("Ready to start");
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
    
    private void startScreenCapture() {
        MediaProjectionManager projectionManager = 
            (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        
        Intent captureIntent = projectionManager.createScreenCaptureIntent();
        startActivityForResult(captureIntent, REQUEST_CODE_SCREEN_CAPTURE);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE) {
            if (resultCode == RESULT_OK) {
                // Start WebSocket service
                Intent wsIntent = new Intent(this, WebSocketService.class);
                startService(wsIntent);
                
                // Start screen capture service
                Intent captureIntent = new Intent(this, ScreenCaptureService.class);
                captureIntent.putExtra("resultCode", resultCode);
                captureIntent.putExtra("data", data);
                startService(captureIntent);
                
                startButton.setEnabled(false);
                stopButton.setEnabled(true);
                updateStatus("Screen capture started");
                
                Toast.makeText(this, "Screen capture started", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show();
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

