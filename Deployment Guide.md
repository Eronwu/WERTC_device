# Deployment Guide

## Prerequisites

### Development Environment
- Android Studio with Android SDK
- Flutter SDK 3.9+
- Java Development Kit (JDK) 17+
- Git

### Target Devices
- Android device with API level 31+ (Android 12+)
- iOS device with iOS 12+ or Android device with API level 21+

## Step-by-Step Deployment

### 1. DeviceApp (Android) Deployment

#### Build the Application
```bash
cd DeviceApp
./gradlew assembleDebug
```

#### Install on Target Device
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

#### Configure Permissions
1. Open Settings > Accessibility
2. Find "Device Screen Control" service
3. Enable the accessibility service
4. Grant screen capture permissions when prompted

### 2. ControlApp (Flutter) Deployment

#### Install Dependencies
```bash
cd control_app
flutter pub get
flutter pub run build_runner build
```

#### Build for Android
```bash
flutter build apk --release
```

#### Build for iOS
```bash
flutter build ios --release
```

#### Install on Device
```bash
# For Android
flutter install

# For iOS (requires Xcode)
open ios/Runner.xcworkspace
# Build and run from Xcode
```

### 3. Network Configuration

#### Ensure Network Connectivity
- Both devices must be on the same WiFi network
- Firewall should allow traffic on port 8080
- Router should support local device communication

#### Test Network Discovery
1. Start DeviceApp on target device
2. Note the IP address displayed
3. Open ControlApp on control device
4. Verify device appears in scan results

### 4. Testing the Connection

#### Basic Functionality Test
1. Start screen capture on DeviceApp
2. Connect from ControlApp
3. Verify video stream appears
4. Test touch controls
5. Test navigation buttons

#### Performance Testing
- Monitor frame rate and latency
- Test under different network conditions
- Verify hardware encoding is active
- Check memory and CPU usage

## Production Deployment Considerations

### Security
- Implement authentication mechanism
- Add encryption for sensitive data
- Restrict network access as needed
- Regular security updates

### Performance
- Optimize encoding parameters for target hardware
- Implement adaptive quality based on network
- Add connection quality monitoring
- Optimize battery usage

### User Experience
- Add connection status indicators
- Implement automatic reconnection
- Provide clear error messages
- Add user preferences and settings

### Monitoring
- Add crash reporting
- Implement usage analytics
- Monitor performance metrics
- Track connection reliability

## Troubleshooting Deployment Issues

### DeviceApp Issues
- **Build failures**: Check Android SDK version and dependencies
- **Permission errors**: Ensure app has system-level permissions
- **Screen capture fails**: Verify MediaProjection permissions
- **Network binding fails**: Check port availability

### ControlApp Issues
- **Flutter build errors**: Run `flutter doctor` to check setup
- **WebRTC failures**: Verify platform-specific WebRTC support
- **Network discovery fails**: Check network permissions
- **UI rendering issues**: Test on different screen sizes

### Network Issues
- **Devices not found**: Verify same network and port accessibility
- **Connection timeouts**: Check firewall and router settings
- **High latency**: Optimize network configuration
- **Frequent disconnections**: Investigate network stability

## Maintenance

### Regular Updates
- Keep dependencies updated
- Monitor for security vulnerabilities
- Update WebRTC library versions
- Test with new Android/iOS versions

### Performance Monitoring
- Track encoding performance
- Monitor network usage
- Analyze user feedback
- Optimize based on usage patterns

### Documentation
- Keep deployment guide updated
- Document configuration changes
- Maintain troubleshooting knowledge base
- Update API documentation

