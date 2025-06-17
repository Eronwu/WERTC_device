# Remote Screen Control Applications

This project consists of two applications that work together to provide remote screen control functionality:

1. **DeviceApp** - Android application that runs on the target device (Android 12+)
2. **ControlApp** - Flutter application that runs on the control device (iOS/Android)

## Architecture Overview

The system uses a combination of WebSocket for signaling and WebRTC for real-time video streaming and control data transmission.

```
ControlApp (Flutter)
    ↕ WebSocket (signaling)
    ↕ WebRTC (video + data channel)
DeviceApp (Android)
    ↕ MediaCodec (hardware encoding)
    ↕ MediaProjection (screen capture)
    ↕ AccessibilityService (touch injection)
```

## Features

### DeviceApp (Android)
- Screen capture using MediaProjection API
- Hardware H.264 encoding with MediaCodec
- WebSocket server for signaling
- WebRTC peer connection for video streaming
- AccessibilityService for touch event injection
- Low-latency optimizations

### ControlApp (Flutter)
- Device discovery on local network
- WebSocket client for signaling
- WebRTC peer connection for receiving video
- Touch gesture capture and transmission
- Cross-platform support (iOS/Android)

## Technical Specifications

### Video Encoding
- Codec: H.264 (hardware accelerated)
- Bitrate: 2-5 Mbps (adaptive)
- Frame rate: 30 fps
- GOP: 30 frames (1 second I-frame interval)
- Low latency mode enabled

### Network Communication
- WebSocket port: 8080 (configurable)
- WebRTC with STUN servers
- Local network discovery
- ICE candidate exchange

### Control Features
- Single tap/click
- Swipe gestures
- Virtual navigation buttons (Back, Home, Menu)
- Coordinate transformation between devices

## Installation Requirements

### DeviceApp (Android)
- Android 12+ (API level 31+)
- System-level permissions for screen capture
- Accessibility service permissions
- Network access

### ControlApp (Flutter)
- Flutter SDK 3.9+
- iOS 12+ or Android 5.0+
- Camera and microphone permissions (for WebRTC)
- Network access

## Build Instructions

### DeviceApp
```bash
cd DeviceApp
./gradlew assembleDebug
```

### ControlApp
```bash
cd control_app
flutter pub get
flutter build apk  # For Android
flutter build ios  # For iOS
```

## Usage Instructions

1. Install DeviceApp on the target Android device
2. Enable Accessibility Service in device settings
3. Start the DeviceApp and begin screen capture
4. Install ControlApp on the control device
5. Connect to the same WiFi network
6. Open ControlApp and scan for devices
7. Select the target device to connect
8. Control the device remotely through the video stream

## Security Considerations

- Applications work only on local network
- No external server dependencies
- Screen capture requires explicit user permission
- Accessibility service requires manual enablement

## Performance Optimization

- Hardware encoding reduces CPU usage
- Direct surface connection minimizes memory copies
- WebRTC optimizations for low latency
- Adaptive bitrate based on network conditions

## Troubleshooting

### Common Issues
1. **Device not found**: Ensure both devices are on same network
2. **Permission denied**: Enable all required permissions
3. **Video not showing**: Check WebRTC connection status
4. **High latency**: Verify network quality and hardware encoding

### Debug Information
- Check logcat output for Android app
- Monitor Flutter debug console
- Verify WebSocket connection status
- Test network connectivity between devices

## Future Enhancements

- Audio streaming support
- File transfer capabilities
- Multiple device support
- Cloud relay for remote access
- Enhanced gesture recognition
- Screen recording functionality

## License

This project is for demonstration purposes. Please ensure compliance with local laws and device policies when using screen capture and remote control features.

