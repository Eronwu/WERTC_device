# 投屏黑屏问题修复说明

## 问题分析

通过分析日志和代码，发现投屏黑屏的主要原因：

### 1. 手机端问题
- **EglRenderer接收0帧视频**: 日志显示 `Frames received: 0. Dropped: 0. Rendered: 0`
- **缺少视频轨道处理**: 只有数据通道连接成功，没有视频流传输
- **SDP协商问题**: offer中没有正确请求视频流

### 2. 设备端问题
- **视频轨道添加方式错误**: 使用了 `addTrack()` 而不是 `addStream()`
- **初始化顺序问题**: 在处理offer之前没有创建视频轨道
- **视频源连接问题**: ScreenCapturer没有正确连接到VideoSource

### 3. 时序问题
- VideoSource在WebRTC连接建立后才设置
- 屏幕捕获可能在VideoSource准备好之前就尝试启动

## 修复方案

### 设备端修复 (DeviceApp)

#### 1. WebRTCManager.java
```java
// 修复视频轨道添加方式
private void createVideoTrack() {
    videoSource = peerConnectionFactory.createVideoSource(false);
    videoTrack = peerConnectionFactory.createVideoTrack("video_track", videoSource);
    
    // 创建媒体流并添加视频轨道
    MediaStream mediaStream = peerConnectionFactory.createLocalMediaStream("stream_id");
    mediaStream.addTrack(videoTrack);
    
    // 添加流到peer connection
    peerConnection.addStream(mediaStream);
    
    screenCaptureService.setVideoSource(videoSource);
}

// 修复初始化顺序
public void handleOffer(JsonObject offerJson) {
    // 在处理offer之前创建视频轨道
    createVideoTrack();
    // ... 其余代码
}
```

#### 2. ScreenCaptureService.java
```java
// 修复视频源连接
screenCapturer.initialize(
    surfaceTextureHelper,
    getApplicationContext(),
    videoSource.getCapturerObserver()  // 直接使用VideoSource的observer
);

// 修复时序问题
public void setVideoSource(VideoSource videoSource) {
    this.videoSource = videoSource;
    
    // 如果有待处理的捕获请求，立即开始
    if (mediaProjectionResultCode != 0 && mediaProjectionData != null && !isCapturing) {
        startScreenCapture(mediaProjectionResultCode, mediaProjectionData);
    }
}
```

### 手机端修复 (control_app)

#### 1. webrtc_service.dart
```dart
// 添加视频轨道处理
_peerConnection!.onTrack = (event) {
  debugPrint('Remote track added: ${event.track.kind}');
  if (event.streams.isNotEmpty) {
    _remoteStream = event.streams[0];
    _remoteStreamController.add(event.streams[0]);
  }
};

// 修复offer创建
Future<void> createOffer() async {
  final constraints = {
    'offerToReceiveAudio': false,
    'offerToReceiveVideo': true,  // 明确请求视频
  };
  
  final offer = await _peerConnection!.createOffer(constraints);
  // ... 其余代码
}
```

## 常见WebRTC投屏问题解决方案

基于搜索结果，WebRTC投屏黑屏是常见问题，主要解决方案包括：

1. **确保正确的SDP协商**: offer和answer都要包含视频轨道
2. **使用addStream而不是addTrack**: 某些设备兼容性更好
3. **正确的初始化顺序**: 先创建视频轨道再处理SDP
4. **视频源连接**: 确保ScreenCapturer正确连接到VideoSource
5. **权限检查**: 确保屏幕录制权限已授予

## 测试验证

修复后应验证：
1. 设备端日志显示视频轨道创建成功
2. 手机端日志显示接收到视频流和视频轨道
3. EglRenderer显示 `Frames received > 0`
4. 手机端显示设备屏幕内容而不是黑屏

## 调试建议

如果问题仍然存在：
1. 检查MediaProjection权限
2. 验证网络连接质量
3. 查看WebRTC统计信息
4. 测试不同的视频编解码器
5. 检查设备硬件编码支持