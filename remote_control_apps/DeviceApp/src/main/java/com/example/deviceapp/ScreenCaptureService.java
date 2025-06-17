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
import android.view.Surface;
import android.view.WindowManager;

import java.io.IOException;
import java.nio.ByteBuffer;

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
    
    private int screenWidth;
    private int screenHeight;
    private int screenDensity;
    
    @Override
    public void onCreate() {
        super.onCreate();
        initScreenMetrics();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            int resultCode = intent.getIntExtra("resultCode", -1);
            Intent data = intent.getParcelableExtra("data");
            
            if (resultCode != -1 && data != null) {
                startScreenCapture(resultCode, data);
            }
        }
        return START_STICKY;
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
    }
    
    private void handleEncodedFrame(MediaCodec codec, int index, MediaCodec.BufferInfo info) {
        ByteBuffer encodedData = codec.getOutputBuffer(index);
        if (encodedData != null && info.size > 0) {
            // Extract encoded frame data
            byte[] frameData = new byte[info.size];
            encodedData.position(info.offset);
            encodedData.get(frameData, 0, info.size);
            
            // TODO: Send frame data to WebRTC
            Log.d(TAG, "Encoded frame: " + info.size + " bytes");
        }
        
        codec.releaseOutputBuffer(index, false);
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

