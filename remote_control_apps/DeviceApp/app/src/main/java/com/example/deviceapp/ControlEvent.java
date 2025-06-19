package com.example.deviceapp;

import com.google.gson.annotations.SerializedName;

public class ControlEvent {
    @SerializedName("type")
    public String type;
    
    @SerializedName("x")
    public double x;
    
    @SerializedName("y")
    public double y;
    
    @SerializedName("endX")
    public Double endX;
    
    @SerializedName("endY")
    public Double endY;
    
    @SerializedName("duration")
    public Integer duration;
    
    @SerializedName("timestamp")
    public long timestamp;
    
    public ControlEvent() {}
    
    public ControlEvent(String type, double x, double y, long timestamp) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.timestamp = timestamp;
    }
    
    public boolean isSpecialKey() {
        return x < 0 && y < 0;
    }
    
    public int getSpecialKeyCode() {
        if (x == -1 && y == -1) return 4; // KEYCODE_BACK
        if (x == -2 && y == -2) return 3; // KEYCODE_HOME
        if (x == -3 && y == -3) return 82; // KEYCODE_MENU
        return -1;
    }
}