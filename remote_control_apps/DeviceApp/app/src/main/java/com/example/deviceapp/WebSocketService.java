package com.example.deviceapp;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class WebSocketService extends Service {
    private static final String TAG = "WebSocketService";
    private static final int PORT = 4321;
    
    private WebSocketServer server;
    private final Map<WebSocket, String> clients = new ConcurrentHashMap<>();
    private final Map<WebSocket, WebRTCManager> webRTCManagers = new ConcurrentHashMap<>();
    private Gson gson = new Gson();
    private ScreenCaptureService screenCaptureService;
    
    private static WebSocketService instance;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "WebSocketService created");
        instance = this;
        stopExistingServer();
        startWebSocketServer();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    public void setScreenCaptureService(ScreenCaptureService screenCaptureService) {
        this.screenCaptureService = screenCaptureService;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        stopExistingServer();
    }
    
    public static WebSocketService getInstance() {
        return instance;
    }
    
    private void stopExistingServer() {
        if (server != null) {
            try {
                Log.d(TAG, "Stopping existing WebSocket server");
                server.stop(1000); // Wait up to 1 second for graceful shutdown
                server = null;
                Thread.sleep(100); // Brief pause to ensure port is released
            } catch (Exception e) {
                Log.e(TAG, "Error stopping WebSocket server", e);
            }
        }
    }
    
    private void startWebSocketServer() {
        try {
            // Bind to all network interfaces (0.0.0.0) instead of localhost
            server = new WebSocketServer(new InetSocketAddress("0.0.0.0", PORT)) {
                @Override
                public void onOpen(WebSocket conn, ClientHandshake handshake) {
                    Log.d(TAG, "New client connected: " + conn.getRemoteSocketAddress());
                    clients.put(conn, "");
                    
                    // Send device info
                    JsonObject deviceInfo = new JsonObject();
                    deviceInfo.addProperty("type", "device_info");
                    deviceInfo.addProperty("device_name", android.os.Build.MODEL);
                    deviceInfo.addProperty("device_id", android.os.Build.SERIAL);
                    
                    try {
                        conn.send(gson.toJson(deviceInfo));
                        Log.d(TAG, "Sent initial device info to new client");
                    } catch (Exception e) {
                        Log.e(TAG, "Error sending initial device info", e);
                    }
                }
            
                @Override
                public void onClose(WebSocket conn, int code, String reason, boolean remote) {
                    Log.d(TAG, "Client disconnected: " + conn.getRemoteSocketAddress() + 
                          ", code: " + code + ", reason: " + reason + ", remote: " + remote);
                    clients.remove(conn);

                    // Clean up WebRTC connection but keep reference for potential reconnection
                    WebRTCManager webRTCManager = webRTCManagers.remove(conn);
                    if (webRTCManager != null) {
                        Log.d(TAG, "Cleaning up WebRTC for disconnected client");
                        webRTCManager.cleanup();
                    }
                }

                @Override
                public void onMessage(WebSocket conn, String message) {
                    Log.d(TAG, "Received message: " + message);
                    handleMessage(conn, message);
                }

                @Override
                public void onError(WebSocket conn, Exception ex) {
                    Log.e(TAG, "WebSocket error", ex);
                }

                @Override
                public void onStart() {
                    Log.d(TAG, "WebSocket server started on 0.0.0.0:" + PORT);
                    Log.d(TAG, "Server address: " + server.getAddress());
                    // Log network interfaces for debugging
                    try {
                        java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
                        while (interfaces.hasMoreElements()) {
                            java.net.NetworkInterface ni = interfaces.nextElement();
                            if (ni.isUp() && !ni.isLoopback()) {
                                java.util.Enumeration<java.net.InetAddress> addresses = ni.getInetAddresses();
                                while (addresses.hasMoreElements()) {
                                    java.net.InetAddress addr = addresses.nextElement();
                                    if (addr instanceof java.net.Inet4Address) {
                                        Log.d(TAG, "Available network interface: " + ni.getName() + " - " + addr.getHostAddress());
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error listing network interfaces", e);
                    }
                }
            };
        
            // Enable address reuse to prevent "Address already in use" errors
            server.setReuseAddr(true);
        
            server.start();
            Log.d(TAG, "Starting WebSocket server on port " + PORT);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start WebSocket server", e);
            // Retry after a short delay
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    startWebSocketServer();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
    }
    
    private void handleMessage(WebSocket conn, String message) {
        try {
            JsonObject json = gson.fromJson(message, JsonObject.class);
            String type = json.get("type").getAsString();
            
            switch (type) {
                case "ping":
                    handlePing(conn, json);
                    break;
                case "start_webrtc":
                    handleStartWebRTC(conn, json);
                    break;
                case "offer":
                    handleOffer(conn, json);
                    break;
                case "answer":
                    handleAnswer(conn, json);
                    break;
                case "ice_candidate":
                    handleIceCandidate(conn, json);
                    break;
                case "control_event":
                    handleControlEvent(conn, json);
                    break;
                default:
                    Log.w(TAG, "Unknown message type: " + type);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling message", e);
        }
    }
    
    private void handlePing(WebSocket conn, JsonObject json) {
        // Handle ping request and respond with device info
        Log.d(TAG, "Handling ping request");
        
        // Check if connection is still open before sending response
        if (conn.isOpen()) {
            JsonObject deviceInfo = new JsonObject();
            deviceInfo.addProperty("type", "device_info");
            deviceInfo.addProperty("device_name", android.os.Build.MODEL);
            deviceInfo.addProperty("device_id", android.os.Build.SERIAL);
            
            try {
                conn.send(gson.toJson(deviceInfo));
                Log.d(TAG, "Sent device info response");
            } catch (Exception e) {
                Log.e(TAG, "Error sending device info response", e);
            }
        } else {
            Log.w(TAG, "Cannot send ping response - connection is closed");
        }
    }
    
    private void handleStartWebRTC(WebSocket conn, JsonObject json) {
        Log.d(TAG, "Fast WebRTC initialization starting");
        
        if (screenCaptureService == null) {
            Log.e(TAG, "ScreenCaptureService not available");
            return;
        }
        
        // Clean up any existing WebRTC manager for this connection only if it's a reconnection
        WebRTCManager existingManager = webRTCManagers.get(conn);
        if (existingManager != null) {
            Log.d(TAG, "Cleaning up existing WebRTC manager for fast reconnection");
            existingManager.cleanup();
            webRTCManagers.remove(conn);
        }
        
        // Create and initialize WebRTC manager in optimized sequence
        WebRTCManager webRTCManager = new WebRTCManager(this, screenCaptureService);
        webRTCManagers.put(conn, webRTCManager);
        
        // Create peer connection and offer in single operation
        webRTCManager.createPeerConnection(conn);
        webRTCManager.createOfferWithVideo();
        
        Log.d(TAG, "Fast WebRTC initialization completed - offer sent");
    }
    
    private void handleOffer(WebSocket conn, JsonObject json) {
        Log.d(TAG, "Handling WebRTC offer");
        
        if (screenCaptureService == null) {
            Log.e(TAG, "ScreenCaptureService not available");
            return;
        }
        
        // Create WebRTC manager for this connection if not exists
        WebRTCManager webRTCManager = webRTCManagers.get(conn);
        if (webRTCManager == null) {
            webRTCManager = new WebRTCManager(this, screenCaptureService);
            webRTCManager.createPeerConnection(conn);
            webRTCManagers.put(conn, webRTCManager);
        }
        
        // Handle the offer
        webRTCManager.handleOffer(json);
    }
    
    private void handleAnswer(WebSocket conn, JsonObject json) {
        Log.d(TAG, "Handling WebRTC answer");
        
        WebRTCManager webRTCManager = webRTCManagers.get(conn);
        if (webRTCManager != null) {
            // Handle answer from client (in new flow, device sends offer, client sends answer)
            webRTCManager.handleAnswer(json);
        } else {
            Log.w(TAG, "No WebRTC manager found for answer");
        }
    }
    
    private void handleIceCandidate(WebSocket conn, JsonObject json) {
        Log.d(TAG, "Handling ICE candidate");
        
        WebRTCManager webRTCManager = webRTCManagers.get(conn);
        if (webRTCManager != null) {
            webRTCManager.handleIceCandidate(json);
        } else {
            Log.w(TAG, "No WebRTC manager found for ICE candidate");
        }
    }
    
    private void handleControlEvent(WebSocket conn, JsonObject json) {
        // Handle control events from mobile app
        Log.d(TAG, "Handling control event");
        // TODO: Implement control event handling
    }
    
    public void broadcastMessage(String message) {
        for (WebSocket client : clients.keySet()) {
            if (client.isOpen()) {
                client.send(message);
            }
        }
    }
}

