package com.example.deviceapp;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import org.webrtc.VideoSource;
import org.webrtc.VideoFrame;
import org.webrtc.JavaI420Buffer;
import org.webrtc.VideoFrame.I420Buffer;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.VideoCapturer;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.EglBase;
import org.webrtc.CapturerObserver;

import java.util.concurrent.atomic.AtomicInteger;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;

public class ScreenCaptureService extends Service {
    private static final String TAG = "ScreenCaptureService";
    private static final int FRAME_RATE = 60; // Increased to 60 fps for ultra-low latency
    
    // Frame rate control
    private static final long TARGET_FRAME_INTERVAL_NS = 1000000000L / FRAME_RATE; // 16.67ms for 60fps
    private long lastFrameTimeNs = 0;
    
    // WebRTC screen capture components
    private ScreenCapturerAndroid screenCapturer;
    private SurfaceTextureHelper surfaceTextureHelper;
    private EglBase eglBase;
    private VideoSource videoSource;
    private HandlerThread captureThread;
    private Handler captureHandler;
    
    // Screen metrics
    private int screenWidth;
    private int screenHeight;
    private int screenDensity;
    
    private AtomicInteger frameCount = new AtomicInteger(0);
    
    // Capture state
    private boolean isCapturing = false;
    private Intent mediaProjectionData;
    private int mediaProjectionResultCode;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "ScreenCaptureService created");
        
        initScreenMetrics();
        initWebRTCComponents();
        
        // Set reference in WebSocketService
        WebSocketService webSocketService = WebSocketService.getInstance();
        if (webSocketService != null) {
            webSocketService.setScreenCaptureService(this);
            Log.d(TAG, "Set ScreenCaptureService reference in WebSocketService");
        } else {
            Log.w(TAG, "WebSocketService instance not available yet");
        }
    }
    
    // WebRTC CapturerObserver implementation
    private class ScreenCaptureObserver implements CapturerObserver {
        @Override
        public void onCapturerStarted(boolean success) {
            Log.d(TAG, "Screen capturer started: " + success);
            if (success) {
                frameCount.set(0);
            }
        }
        
        @Override
        public void onCapturerStopped() {
            Log.d(TAG, "Screen capturer stopped");
            isCapturing = false;
        }
        
        @Override
        public void onFrameCaptured(VideoFrame frame) {
            // TODO: current webrtc cannot support drop frame for unknown reason.
//            int currentFrame = frameCount.incrementAndGet();
//
//            // Frame rate control for ultra-low latency
//            long currentTimeNs = System.nanoTime();
//            if (lastFrameTimeNs > 0) {
//                long timeSinceLastFrame = currentTimeNs - lastFrameTimeNs;
//                if (timeSinceLastFrame < TARGET_FRAME_INTERVAL_NS) {
//                    // Drop frame to maintain target frame rate - use retain/release properly
//                    try {
//                        frame.release();
//                    } catch (Exception e) {
//                        Log.w(TAG, "Frame already released during rate control: " + e.getMessage());
//                    }
//                    return;
//                }
//            }
//            lastFrameTimeNs = currentTimeNs;

            // Only for debug log (commented out for performance)
            // Log.d(TAG, "Frame captured: " + currentFrame + ", timestamp: " + frame.getTimestampNs() +
            //           ", size: " + frame.getBuffer().getWidth() + "x" + frame.getBuffer().getHeight());
            
            // Send frame to WebRTC VideoSource through CapturerObserver
            if (videoSource != null && videoSource.getCapturerObserver() != null) {
                try {
                    // VideoSource will handle frame lifecycle, don't release here
                    videoSource.getCapturerObserver().onFrameCaptured(frame);
                    // Log.v(TAG, "Frame sent to WebRTC VideoSource successfully");
                } catch (Exception e) {
                    Log.e(TAG, "Error sending frame to VideoSource", e);
                    // Only release on error if frame hasn't been consumed
//                    try {
//                        frame.release();
//                    } catch (Exception releaseError) {
//                        Log.w(TAG, "Frame already released during error handling: " + releaseError.getMessage());
//                    }
                }
            } else {
                Log.w(TAG, "VideoSource or CapturerObserver is null, cannot send frame");
                // Only release if frame hasn't been consumed
                try {
                    frame.release();
                } catch (Exception e) {
                    Log.w(TAG, "Frame already released when VideoSource unavailable: " + e.getMessage());
                }
            }
        }
    }
    

    
    private static final String NOTIFICATION_CHANNEL_ID = "ScreenCaptureServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification notification = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle("屏幕共享服务")
                    .setContentText("正在进行屏幕共享")
                    .setSmallIcon(R.mipmap.ic_launcher) // 确保你的mipmap中有ic_launcher
                    .build();
        }
        startForeground(NOTIFICATION_ID, notification);

        if (intent != null) {
            int resultCode = intent.getIntExtra("resultCode", -1);
            Intent data = intent.getParcelableExtra("data");

            Log.d(TAG, "get parcelable data from MainActivity: " + data);
            if (resultCode == MainActivity.RESULT_OK && data != null) {
                this.mediaProjectionResultCode = resultCode;
                this.mediaProjectionData = data;

                // If VideoSource is already available, start capture.
                // Otherwise, capture will be started when setVideoSource is called.
                if (videoSource != null && !isCapturing) {
                    Log.d(TAG, "VideoSource is ready, starting screen capture immediately.");
                    startScreenCapture(resultCode, data);
                } else {
                    Log.d(TAG, "VideoSource not yet available. Storing projection data. Capture will start when WebRTC is ready.");
                }
            }
        }
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "屏幕共享服务通知",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        
        stopScreenCapture();
        
        // Clean up WebRTC components
        if (captureHandler != null) {
            captureHandler.post(() -> {
                try {
                    if (surfaceTextureHelper != null) {
                        surfaceTextureHelper.dispose();
                        surfaceTextureHelper = null;
                        Log.d(TAG, "SurfaceTextureHelper disposed");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error disposing SurfaceTextureHelper", e);
                }
            });
        }
        
        if (captureThread != null) {
            captureThread.quitSafely();
            try {
                captureThread.join(1000);
            } catch (InterruptedException e) {
                Log.w(TAG, "Interrupted while waiting for capture thread to finish");
            }
            captureThread = null;
            captureHandler = null;
        }
        

        
        if (eglBase != null) {
            eglBase.release();
            eglBase = null;
            Log.d(TAG, "EglBase released");
        }
        
        Log.d(TAG, "ScreenCaptureService destroyed");
    }
    
    private void initScreenMetrics() {
        WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);
        
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;
        
        Log.d(TAG, "Screen metrics: " + screenWidth + "x" + screenHeight + " density: " + screenDensity);
    }
    
private void initWebRTCComponents() {
        try {
            // Initialize EGL context for WebRTC
            eglBase = EglBase.create();
            
            // Create capture thread
            captureThread = new HandlerThread("ScreenCaptureThread");
            captureThread.start();
            captureHandler = new Handler(captureThread.getLooper());
            
            // Create SurfaceTextureHelper on the capture thread
            captureHandler.post(() -> {
                surfaceTextureHelper = SurfaceTextureHelper.create(
                    "ScreenCaptureSurfaceTextureHelper", 
                    eglBase.getEglBaseContext()
                );
                Log.d(TAG, "WebRTC components initialized successfully");
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize WebRTC components", e);
        }
    }
    

    
    private void startScreenCapture(int resultCode, Intent data) {
        try {
            Log.d(TAG, "Starting WebRTC-based screen capture");
            
            // Store MediaProjection data for ScreenCapturerAndroid
            this.mediaProjectionResultCode = resultCode;
            this.mediaProjectionData = data;
            
            // Stop any existing capture first to prevent conflicts
            if (isCapturing) {
                Log.d(TAG, "Stopping existing capture before starting new one");
                stopScreenCapture();
                // Wait a bit for cleanup to complete
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            // Initialize ScreenCapturerAndroid on the capture thread
            captureHandler.post(() -> {
                try {
                    // Ensure SurfaceTextureHelper is ready and not already listening
                    if (surfaceTextureHelper != null && videoSource != null) {
                        // Create MediaProjection callback
                        MediaProjection.Callback projectionCallback = new MediaProjection.Callback() {
                            @Override
                            public void onStop() {
                                Log.d(TAG, "MediaProjection stopped");
                                stopScreenCapture();
                            }
                        };
                        
                        // Create ScreenCapturerAndroid
                        screenCapturer = new ScreenCapturerAndroid(data, projectionCallback);
                        
                        // Use our custom observer that forwards frames to VideoSource
                        ScreenCaptureObserver customObserver = new ScreenCaptureObserver();
                        
                        // Initialize the capturer with SurfaceTextureHelper and custom observer
                        screenCapturer.initialize(
                            surfaceTextureHelper,
                            getApplicationContext(),
                            customObserver
                        );
                        
                        // Start capturing
                        screenCapturer.startCapture(screenWidth, screenHeight, FRAME_RATE);
                        isCapturing = true;
                        
                        Log.d(TAG, "WebRTC screen capture started successfully");
                    } else {
                        Log.e(TAG, "SurfaceTextureHelper or VideoSource not ready");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error starting WebRTC screen capture", e);
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting screen capture", e);
        }
    }
    
    // WebRTC VideoSource setter - called by WebRTCManager
    public void setVideoSource(VideoSource videoSource) {
        // Allow resetting VideoSource for reconnection scenarios
        if (this.videoSource != null) {
            Log.d(TAG, "VideoSource already set, updating with new VideoSource for reconnection");
        } else {
            Log.d(TAG, "Setting VideoSource for screen capture");
        }
        
        this.videoSource = videoSource;
        
        // If we have pending capture request and now have VideoSource, start capture
        if (mediaProjectionResultCode != 0 && mediaProjectionData != null && !isCapturing) {
            Log.d(TAG, "Starting delayed screen capture now that VideoSource is available");
            startScreenCapture(mediaProjectionResultCode, mediaProjectionData);
        }
    }
    

    

    
    public void stopScreenCapture() {
        try {
            Log.d(TAG, "Stopping WebRTC screen capture");
            
            isCapturing = false;
            
            // Stop screen capturer on capture thread
            if (captureHandler != null) {
                captureHandler.post(() -> {
                    try {
                        if (screenCapturer != null && surfaceTextureHelper != null) {
                            screenCapturer.stopCapture();
                            screenCapturer.dispose();
                            screenCapturer = null;
                            Log.d(TAG, "ScreenCapturerAndroid stopped and disposed");
                        } else if (screenCapturer != null) {
                            Log.w(TAG, "SurfaceTextureHelper is null, disposing screenCapturer without stopCapture");
                            screenCapturer.dispose();
                            screenCapturer = null;
                        }
                        
                        // Stop SurfaceTextureHelper listener to prevent "listener has already been set" error
                        if (surfaceTextureHelper != null) {
                            try {
                                surfaceTextureHelper.stopListening();
                                Log.d(TAG, "SurfaceTextureHelper listener stopped");
                            } catch (Exception e) {
                                Log.w(TAG, "Error stopping SurfaceTextureHelper listener: " + e.getMessage());
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error stopping screen capturer", e);
                    }
                });
            }
            

            
            Log.d(TAG, "WebRTC screen capture stopped successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping screen capture", e);
        }
    }
}

