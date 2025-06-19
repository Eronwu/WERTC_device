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
import android.os.Environment;
import android.media.MediaRecorder;
import android.media.MediaMuxer;

import org.webrtc.VideoSource;
import org.webrtc.VideoFrame;
import org.webrtc.JavaI420Buffer;
import org.webrtc.VideoFrame.I420Buffer;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.VideoCapturer;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.EglBase;
import org.webrtc.CapturerObserver;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentLinkedQueue;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;

public class ScreenCaptureService extends Service {
    private static final String TAG = "ScreenCaptureService";
    private static final int FRAME_RATE = 30;
    private static final int DEBUG_DURATION_MS = 20000; // 20 seconds for debug recording
    
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
    
    // Debug recording components
    private boolean isDebugRecording = false;
    private MediaRecorder debugMediaRecorder;
    private File debugOutputDir;
    private AtomicInteger frameCount = new AtomicInteger(0);
    private ConcurrentLinkedQueue<String> debugFrameQueue = new ConcurrentLinkedQueue<>();
    private Handler debugHandler;
    private HandlerThread debugThread;
    private long captureStartTime;
    
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
        initDebugComponents();
        
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
                captureStartTime = System.currentTimeMillis();
            }
        }
        
        @Override
        public void onCapturerStopped() {
            Log.d(TAG, "Screen capturer stopped");
            isCapturing = false;
        }
        
        @Override
        public void onFrameCaptured(VideoFrame frame) {
            int currentFrame = frameCount.incrementAndGet();
            long currentTime = System.currentTimeMillis();
            
            Log.v(TAG, "Frame captured: " + currentFrame + ", timestamp: " + frame.getTimestampNs() + 
                      ", size: " + frame.getBuffer().getWidth() + "x" + frame.getBuffer().getHeight());
            
            // Save debug frame if within 10 seconds
            if (currentTime - captureStartTime <= DEBUG_DURATION_MS) {
                saveDebugFrame(frame, currentFrame);
            }
            
            // Send frame to WebRTC VideoSource through CapturerObserver
            if (videoSource != null && videoSource.getCapturerObserver() != null) {
                try {
                    videoSource.getCapturerObserver().onFrameCaptured(frame);
                    Log.v(TAG, "Frame sent to WebRTC VideoSource successfully");
                } catch (Exception e) {
                    Log.e(TAG, "Error sending frame to VideoSource", e);
                }
            } else {
                Log.w(TAG, "VideoSource or CapturerObserver is null, cannot send frame");
            }
        }
    }
    
    private void startDebugRecording() {
        debugHandler.post(() -> {
            try {
                // Create debug video file
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(new Date());
                File debugVideoFile = new File(debugOutputDir, "screen_capture_" + timestamp + ".mp4");
                
                // Skip MediaRecorder debug recording to avoid conflicts with ScreenCapturerAndroid
                // Instead, just log frame capture info which we already do in saveDebugFrame
                Log.d(TAG, "Debug recording placeholder started: " + debugVideoFile.getAbsolutePath());
                Log.d(TAG, "Note: MediaRecorder disabled to avoid conflicts with WebRTC screen capture");
                
                // Stop debug logging after the specified duration
                debugHandler.postDelayed(() -> {
                    Log.d(TAG, "Debug recording period ended");
                    // Save final debug frame info
                    if (!debugFrameQueue.isEmpty()) {
                        saveDebugFrameInfo();
                    }
                }, DEBUG_DURATION_MS);
                
            } catch (Exception e) {
                Log.e(TAG, "Error in debug recording", e);
            }
        });
    }
    
    private void saveDebugFrame(VideoFrame frame, int frameNumber) {
        debugHandler.post(() -> {
            try {
                // Add frame info to debug queue
                String frameInfo = String.format(Locale.getDefault(),
                    "Frame %d: %dx%d, timestamp=%d, rotation=%d",
                    frameNumber,
                    frame.getBuffer().getWidth(),
                    frame.getBuffer().getHeight(),
                    frame.getTimestampNs(),
                    frame.getRotation());
                
                debugFrameQueue.offer(frameInfo);
                
                // Keep only recent frames
                while (debugFrameQueue.size() > 300) { // ~10 seconds at 30fps
                    debugFrameQueue.poll();
                }
                
                // Save frame info to file every 30 frames
                if (frameNumber % 30 == 0) {
                    saveDebugFrameInfo();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error saving debug frame info", e);
            }
        });
    }
    
    private void saveDebugFrameInfo() {
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date());
            File debugFile = new File(debugOutputDir, "frame_info_" + timestamp + ".txt");
            
            try (FileOutputStream fos = new FileOutputStream(debugFile)) {
                for (String frameInfo : debugFrameQueue) {
                    fos.write((frameInfo + "\n").getBytes());
                }
            }
            
            Log.d(TAG, "Debug frame info saved: " + debugFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Error saving debug frame info to file", e);
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
                startScreenCapture(resultCode, data);
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
        
        if (debugThread != null) {
            debugThread.quitSafely();
            try {
                debugThread.join(1000);
            } catch (InterruptedException e) {
                Log.w(TAG, "Interrupted while waiting for debug thread to finish");
            }
            debugThread = null;
            debugHandler = null;
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
    
    private void initDebugComponents() {
        try {
            // Create debug output directory
            debugOutputDir = new File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "ScreenCaptureDebug");
            if (!debugOutputDir.exists()) {
                debugOutputDir.mkdirs();
            }
            
            // Create debug thread
            debugThread = new HandlerThread("DebugThread");
            debugThread.start();
            debugHandler = new Handler(debugThread.getLooper());
            
            Log.d(TAG, "Debug components initialized, output dir: " + debugOutputDir.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize debug components", e);
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
                        
                        // Start debug recording after WebRTC capture is started
                        startDebugRecording();
                        
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
        // Prevent setting VideoSource multiple times to avoid duplicate capture attempts
        if (this.videoSource != null) {
            Log.w(TAG, "VideoSource already set, ignoring duplicate setVideoSource call");
            return;
        }
        
        this.videoSource = videoSource;
        Log.d(TAG, "VideoSource set for screen capture");
        
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
            
            // Stop debug recording
            if (debugHandler != null) {
                debugHandler.post(() -> {
                    try {
                        // Save final debug frame info
                        if (!debugFrameQueue.isEmpty()) {
                            saveDebugFrameInfo();
                        }
                        Log.d(TAG, "Debug frame logging stopped");
                    } catch (Exception e) {
                        Log.e(TAG, "Error stopping debug recording", e);
                    }
                });
            }
            
            Log.d(TAG, "WebRTC screen capture stopped successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping screen capture", e);
        }
    }
}

