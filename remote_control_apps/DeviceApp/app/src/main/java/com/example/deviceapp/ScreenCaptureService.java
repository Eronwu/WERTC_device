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

import java.io.IOException;
import java.nio.ByteBuffer;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

public class ScreenCaptureService extends Service {
    private static final String TAG = "ScreenCaptureService";
    private static final String MIME_TYPE = "video/avc"; // H.264
    private static final int FRAME_RATE = 30;
    private static final int I_FRAME_INTERVAL = 1;
    private static final int BIT_RATE = 2000000; // 2Mbps
    
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private MediaCodec encoder;
    private Surface inputSurface;
    private boolean isCapturing = false;
    private VideoSource videoSource;
    private long frameTimestamp = 0;
    
    private int screenWidth;
    private int screenHeight;
    private int screenDensity;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "ScreenCaptureService created");
        initScreenMetrics();
        
        // Set reference in WebSocketService
        WebSocketService webSocketService = WebSocketService.getInstance();
        if (webSocketService != null) {
            webSocketService.setScreenCaptureService(this);
            Log.d(TAG, "Set ScreenCaptureService reference in WebSocketService");
        } else {
            Log.w(TAG, "WebSocketService instance not available yet");
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
//                startScreenCapture(resultCode, data);
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
    
    private void startScreenCapture(int resultCode, Intent data) {
        try {
            // Initialize MediaProjection
            MediaProjectionManager projectionManager = 
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            mediaProjection = projectionManager.getMediaProjection(resultCode, data);
            Log.d(TAG, "get MediaProjection instance from MainActivity");

            // Initialize MediaCodec encoder
            initEncoder();
            
            // Create VirtualDisplay
            createVirtualDisplay();
            
            Log.d(TAG, "Screen capture started successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error starting screen capture", e);
        }
    }
    
    private void initEncoder() throws IOException {
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, screenWidth, screenHeight);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);
        
        // Enable low latency mode
        format.setInteger(MediaFormat.KEY_LATENCY, 0);
        format.setInteger(MediaFormat.KEY_PRIORITY, 0);
        
        encoder = MediaCodec.createEncoderByType(MIME_TYPE);
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        inputSurface = encoder.createInputSurface();
        
        encoder.setCallback(new MediaCodec.Callback() {
            @Override
            public void onInputBufferAvailable(MediaCodec codec, int index) {
                // Not used for surface input
            }
            
            @Override
            public void onOutputBufferAvailable(MediaCodec codec, int index, MediaCodec.BufferInfo info) {
                handleEncodedFrame(codec, index, info);
            }
            
            @Override
            public void onError(MediaCodec codec, MediaCodec.CodecException e) {
                Log.e(TAG, "MediaCodec error", e);
            }
            
            @Override
            public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
                Log.d(TAG, "Output format changed: " + format);
            }
        });
        
        encoder.start();
        Log.d(TAG, "MediaCodec started");
    }
    
    private void createVirtualDisplay() {
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "ScreenCapture",
            screenWidth,
            screenHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            inputSurface,
            null,
            null
        );
        Log.d(TAG, "VirtualDisplay created");
    }
    
    public void setVideoSource(VideoSource videoSource) {
        this.videoSource = videoSource;
    }
    
    private void handleEncodedFrame(MediaCodec codec, int index, MediaCodec.BufferInfo info) {
        ByteBuffer encodedData = codec.getOutputBuffer(index);
        if (encodedData != null && info.size > 0) {
            // Extract encoded frame data
            byte[] frameData = new byte[info.size];
            encodedData.position(info.offset);
            encodedData.get(frameData, 0, info.size);
            
            handleEncodedFrame(frameData);
        }
        
        codec.releaseOutputBuffer(index, false);
    }
    
    private void handleEncodedFrame(byte[] frameData) {
        Log.d(TAG, "Encoded frame size: " + frameData.length);
        
        if (videoSource != null) {
            // Convert H.264 frame to I420 format for WebRTC
            // Note: This is a simplified approach. In production, you might want to
            // use a more sophisticated conversion or use WebRTC's built-in encoders
            try {
                // Create a dummy I420 buffer for now
                // In a real implementation, you would decode the H.264 frame to YUV
                I420Buffer i420Buffer = JavaI420Buffer.allocate(screenWidth, screenHeight);
                
                VideoFrame videoFrame = new VideoFrame(i420Buffer, 0, frameTimestamp * 1000000); // Convert to nanoseconds
                videoSource.getCapturerObserver().onFrameCaptured(videoFrame);
                
                frameTimestamp++;
                videoFrame.release();
            } catch (Exception e) {
                Log.e(TAG, "Error sending frame to WebRTC", e);
            }
        }
    }
    
    private void stopScreenCapture() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        
        if (encoder != null) {
            try {
                encoder.stop();
                encoder.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping encoder", e);
            }
            encoder = null;
        }
        
        if (inputSurface != null) {
            inputSurface.release();
            inputSurface = null;
        }
        
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        
        Log.d(TAG, "Screen capture stopped");
    }
}

