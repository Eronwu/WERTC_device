package com.example.deviceapp;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

public class AccessibilityControlService extends AccessibilityService {
    private static final String TAG = "AccessibilityControl";
    
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Handle accessibility events if needed
    }
    
    @Override
    public void onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted");
    }
    
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "Accessibility service connected");
    }
    
    public void performClick(float x, float y) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            Path clickPath = new Path();
            clickPath.moveTo(x, y);
            
            GestureDescription.StrokeDescription clickStroke = 
                new GestureDescription.StrokeDescription(clickPath, 0, 100);
            
            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            gestureBuilder.addStroke(clickStroke);
            
            boolean result = dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    Log.d(TAG, "Click gesture completed");
                }
                
                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    Log.d(TAG, "Click gesture cancelled");
                }
            }, null);
            
            Log.d(TAG, "Click dispatched: " + result + " at (" + x + ", " + y + ")");
        }
    }
    
    public void performSwipe(float startX, float startY, float endX, float endY, long duration) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            Path swipePath = new Path();
            swipePath.moveTo(startX, startY);
            swipePath.lineTo(endX, endY);
            
            GestureDescription.StrokeDescription swipeStroke = 
                new GestureDescription.StrokeDescription(swipePath, 0, duration);
            
            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            gestureBuilder.addStroke(swipeStroke);
            
            boolean result = dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    Log.d(TAG, "Swipe gesture completed");
                }
                
                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    Log.d(TAG, "Swipe gesture cancelled");
                }
            }, null);
            
            Log.d(TAG, "Swipe dispatched: " + result);
        }
    }
}

