# API Documentation

## DeviceApp WebSocket API

### Connection
- **URL**: `ws://<device_ip>:8080`
- **Protocol**: WebSocket

### Message Format
All messages are JSON objects with a `type` field indicating the message type.

### Message Types

#### Device Info (Server → Client)
Sent when client connects to provide device information.

```json
{
  "type": "device_info",
  "device_name": "Samsung Galaxy S21",
  "device_id": "ABC123456789"
}
```

#### WebRTC Offer (Client → Server)
WebRTC offer from control app to device.

```json
{
  "type": "offer",
  "sdp": "v=0\r\no=- 123456789 2 IN IP4 127.0.0.1\r\n..."
}
```

#### WebRTC Answer (Server → Client)
WebRTC answer from device to control app.

```json
{
  "type": "answer",
  "sdp": "v=0\r\no=- 987654321 2 IN IP4 127.0.0.1\r\n..."
}
```

#### ICE Candidate (Bidirectional)
ICE candidates for WebRTC connection establishment.

```json
{
  "type": "ice_candidate",
  "candidate": "candidate:1 1 UDP 2130706431 192.168.1.100 54400 typ host",
  "sdpMid": "0",
  "sdpMLineIndex": 0
}
```

#### Control Event (Client → Server)
Touch and gesture events from control app.

```json
{
  "type": "control_event",
  "event": {
    "type": "click",
    "x": 500.0,
    "y": 300.0,
    "timestamp": 1640995200000
  }
}
```

## ControlApp Flutter API

### Device Model
```dart
class Device {
  final String id;
  final String name;
  final String ipAddress;
  final int port;
  final bool isConnected;
}
```

### Control Event Model
```dart
class ControlEvent {
  final String type;        // "click" or "swipe"
  final double x;
  final double y;
  final double? endX;       // For swipe events
  final double? endY;       // For swipe events
  final int? duration;      // For swipe events (ms)
  final int timestamp;
}
```

### Services

#### DeviceDiscoveryService
```dart
class DeviceDiscoveryService {
  Stream<Device> get deviceStream;
  Future<List<Device>> discoverDevices();
  void dispose();
}
```

#### WebSocketService
```dart
class WebSocketService {
  Stream<Map<String, dynamic>> get messageStream;
  bool get isConnected;
  
  Future<bool> connect(Device device);
  void disconnect();
  void sendMessage(Map<String, dynamic> message);
  void sendOffer(String sdp);
  void sendAnswer(String sdp);
  void sendIceCandidate(String candidate, String sdpMid, int sdpMLineIndex);
  void sendControlEvent(ControlEvent event);
  void dispose();
}
```

#### WebRTCService
```dart
class WebRTCService {
  Stream<MediaStream> get remoteStreamStream;
  
  Future<void> initialize();
  Future<void> createOffer();
  void sendControlEvent(ControlEvent event);
  void dispose();
}
```

## WebRTC Data Channel Protocol

### Control Events
Control events are sent through WebRTC data channel for low latency.

#### Click Event
```json
{
  "type": "click",
  "x": 500.0,
  "y": 300.0,
  "timestamp": 1640995200000
}
```

#### Swipe Event
```json
{
  "type": "swipe",
  "x": 100.0,
  "y": 200.0,
  "endX": 400.0,
  "endY": 200.0,
  "duration": 500,
  "timestamp": 1640995200000
}
```

#### Special Navigation Events
```json
// Back button
{
  "type": "click",
  "x": -1,
  "y": -1,
  "timestamp": 1640995200000
}

// Home button
{
  "type": "click",
  "x": -2,
  "y": -2,
  "timestamp": 1640995200000
}

// Menu button
{
  "type": "click",
  "x": -3,
  "y": -3,
  "timestamp": 1640995200000
}
```

## Android AccessibilityService Integration

### Touch Event Injection
The DeviceApp uses AccessibilityService to inject touch events:

```java
public void performClick(float x, float y);
public void performSwipe(float startX, float startY, float endX, float endY, long duration);
```

### Coordinate System
- Origin (0,0) is at top-left corner
- X increases to the right
- Y increases downward
- Coordinates are in pixels

### Special Coordinates
- (-1, -1): Back button
- (-2, -2): Home button  
- (-3, -3): Menu/Recent apps button

## Error Handling

### WebSocket Errors
- Connection timeout: 5 seconds
- Automatic reconnection attempts
- Error codes follow WebSocket standard

### WebRTC Errors
- ICE connection failures
- Media stream errors
- Data channel state monitoring

### Control Event Errors
- Invalid coordinates (outside screen bounds)
- Accessibility service not enabled
- Permission denied errors

## Performance Metrics

### Video Encoding
- Target bitrate: 2-5 Mbps
- Frame rate: 30 fps
- Encoding latency: < 50ms
- GOP size: 30 frames

### Network Performance
- WebSocket latency: < 10ms (local network)
- WebRTC latency: < 100ms (local network)
- Packet loss tolerance: < 1%

### Control Responsiveness
- Touch event latency: < 50ms
- Gesture recognition: < 20ms
- Screen update rate: 30 fps

