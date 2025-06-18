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
        Log.d(TAG, "Encoded frame size: " + frameData.length + " bytes, timestamp: " + frameTimestamp);

        if (videoSource != null && videoSource.getCapturerObserver() != null) {
            // TODO: Implement proper H.264 decoding to YUV and then conversion to I420Buffer.
            // The current approach of sending raw H.264 data or an empty buffer will not work correctly.
            // For a functional solution, you need to decode the H.264 `frameData` into a YUV format,
            // then create an I420Buffer from that YUV data.
            // Example placeholder for where conversion should happen:
            // YuvImage yuvImage = decodeH264ToYuv(frameData, screenWidth, screenHeight);
            // I420Buffer i420Buffer = convertYuvToI420Buffer(yuvImage);

            // Using a dummy buffer for now will result in a black screen or incorrect video.
            // This highlights that the data flow is present, but the format is wrong.
            final I420Buffer i420Buffer;
            try {
                // Attempting to create a buffer, this is NOT a correct conversion
                // and is just to prevent a crash and show data is flowing.
                // A real implementation requires decoding H.264 to YUV first.
                Log.w(TAG, "Creating a placeholder I420Buffer. THIS IS NOT A CORRECT VIDEO FRAME.");
                i420Buffer = JavaI420Buffer.allocate(screenWidth, screenHeight);
                // You could try to fill this buffer with some pattern or color to verify if it's being sent
                // For example, fill with a color:
                // ByteBuffer y = i420Buffer.getDataY();
                // ByteBuffer u = i420Buffer.getDataU();
                // ByteBuffer v = i420Buffer.getDataV();
                // for(int i=0; i < y.capacity(); i++) y.put(i, (byte)128); // Grey
                // for(int i=0; i < u.capacity(); i++) u.put(i, (byte)0);
                // for(int i=0; i < v.capacity(); i++) v.put(i, (byte)0);

                VideoFrame videoFrame = new VideoFrame(i420Buffer, 0 /* rotation */, frameTimestamp * 1_000_000 /* ns */);
                videoSource.getCapturerObserver().onFrameCaptured(videoFrame);
                videoFrame.release(); // Release the frame after sending
                frameTimestamp++;
            } catch (Exception e) {
                Log.e(TAG, "Error creating or sending I420Buffer to WebRTC", e);
            }
        } else {
            Log.w(TAG, "videoSource or capturerObserver is null, cannot send frame");
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

