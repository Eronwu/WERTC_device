package com.example.deviceapp;

import android.util.Log;
import java.io.IOException;

public class TouchControlService {
    private static final String TAG = "TouchControlService";
    
    public TouchControlService() {
        Log.d(TAG, "TouchControlService initialized");
    }
    
    public void handleControlEvent(ControlEvent event) {
        try {
            Log.d(TAG, "Handling control event: " + event.type + " at (" + event.x + ", " + event.y + ")");
            
            if (event.isSpecialKey()) {
                handleSpecialKey(event.getSpecialKeyCode());
            } else {
                handleTouch(event);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling control event", e);
        }
    }
    
    private void handleTouch(ControlEvent event) {
        try {
            if ("click".equals(event.type)) {
                executeShellTouch((int) event.x, (int) event.y);
            } else if ("long_click".equals(event.type)) {
                executeShellLongTouch((int) event.x, (int) event.y);
            } else if ("swipe".equals(event.type) && event.endX != null && event.endY != null) {
                executeShellSwipe(
                    (int) event.x, (int) event.y,
                    (int) event.endX.doubleValue(), (int) event.endY.doubleValue(),
                    event.duration != null ? event.duration : 500
                );
            }
        } catch (Exception e) {
            Log.e(TAG, "Error executing touch command", e);
        }
    }
    
    private void handleSpecialKey(int keyCode) {
        try {
            executeShellKeyEvent(keyCode);
        } catch (Exception e) {
            Log.e(TAG, "Error executing key event", e);
        }
    }
    
    private void executeShellTouch(int x, int y) throws IOException, InterruptedException {
        String command = String.format("input tap %d %d", x, y);
        executeShellCommand(command);
        Log.d(TAG, "Executed touch at (" + x + ", " + y + ")");
    }
    
    private void executeShellLongTouch(int x, int y) throws IOException, InterruptedException {
        // Simulate long touch with swipe command (same start and end point, longer duration)
        String command = String.format("input swipe %d %d %d %d 1000", x, y, x, y);
        executeShellCommand(command);
        Log.d(TAG, "Executed long touch at (" + x + ", " + y + ")");
    }
    
    private void executeShellSwipe(int startX, int startY, int endX, int endY, int duration) 
            throws IOException, InterruptedException {
        String command = String.format("input swipe %d %d %d %d %d", startX, startY, endX, endY, duration);
        executeShellCommand(command);
        Log.d(TAG, "Executed swipe from (" + startX + ", " + startY + ") to (" + endX + ", " + endY + ")");
    }
    
    private void executeShellKeyEvent(int keyCode) throws IOException, InterruptedException {
        String command = String.format("input keyevent %d", keyCode);
        executeShellCommand(command);
        Log.d(TAG, "Executed key event: " + keyCode);
    }
    
    private void executeShellCommand(String command) throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec(command);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            Log.w(TAG, "Shell command failed with exit code: " + exitCode + ", command: " + command);
        }
    }
}