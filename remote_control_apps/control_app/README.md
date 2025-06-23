# control_app
以下是 `control_app` 的执行流程和各个关键组件的作用：

**1. `main.dart`**

*   **入口点**: 应用的入口点，通过 `runApp(const MyApp())` 启动。
*   **`MyApp`**: 根 Widget，设置应用的主题和导航到 `SplashScreen`。
*   **`SplashScreen`**: 应用启动时显示的第一个屏幕，负责请求必要的权限（摄像头和麦克风，用于 WebRTC），并在短暂延迟后导航到 `DeviceListScreen`。

**2. `screens/device_list_screen.dart`**

*   **设备发现**: 这是用户进入应用后看到的第一个主要界面，负责扫描和显示局域网内可用的 `DeviceApp` 设备。
*   **`_startScan()`**: 触发设备扫描，通过 `DeviceDiscoveryService` 发现设备。
*   **`_connectToDevice(Device device)`**: 当用户点击列表中的设备时，导航到 `ControlScreen`，并传递选定的设备信息。

**3. `services/device_discovery_service.dart`**

*   **设备扫描**: 负责在局域网内发现运行 `DeviceApp` 的设备。
*   **`discoverDevices()`**: 尝试直接连接到预设的 `_targetIP`（`192.168.10.182`）和端口 `4321`。它通过向目标 IP 发送 WebSocket ping 消息，并等待 `device_info` 响应来确认设备的存在和获取设备信息。
*   **`_scanDevice(String ipAddress, int port)`**: 具体的设备扫描逻辑，建立 WebSocket 连接，发送 ping 消息，并解析设备信息。

**4. `screens/control_screen.dart`**

*   **远程控制界面**: 连接到 `DeviceApp` 设备后，显示远程设备的屏幕流，并提供控制按钮。
*   **`_initializeServices()`**: 初始化 `WebSocketService` 和 `WebRTCService`，并设置 `RTCVideoRenderer` 来显示远程视频流。
*   **`_connectToDevice()`**: 负责建立与远程设备的 WebSocket 连接，并初始化 WebRTC 连接。它处理连接状态、重连逻辑和错误。
*   **手势处理 (`_handleTap`, `_handlePanStart`, `_handlePanUpdate`, `_handlePanEnd`, `_handleLongPress`)**: 将用户在视频流上的触摸、滑动和长按手势转换为设备屏幕上的坐标，并通过 `WebRTCService` 发送 `ControlEvent` 到远程设备。
*   **控制按钮**: 提供“返回”、“主页”和“菜单”等虚拟按键，通过发送特殊坐标的 `ControlEvent` 来模拟物理按键操作。
*   **`_disconnect()`**: 清理所有连接并返回设备列表界面。

**5. `services/websocket_service.dart`**

*   **WebSocket 通信**: 负责与 `DeviceApp` 建立和维护 WebSocket 连接，用于信令交换和控制事件的传输。
*   **`connect(Device device)`**: 建立 WebSocket 连接，并监听传入的消息、错误和连接关闭事件。
*   **`sendMessage(Map<String, dynamic> message)`**: 发送 JSON 格式的消息到远程设备。
*   **信令消息发送**: 封装了发送 WebRTC 信令消息（如 `offer`、`answer`、`ice_candidate`）和 `control_event` 的方法。
*   **重连机制**: 在连接断开时，会自动尝试重连到上次连接的设备。

**6. `services/webrtc_service.dart`**

*   **WebRTC 连接管理**: 负责建立和管理与 `DeviceApp` 的 WebRTC 连接，用于传输视频流和数据通道。
*   **`initialize()`**: 初始化 `RTCPeerConnection`，配置 ICE 服务器（此处为空，表示局域网内直连），并设置各种回调函数，如 `onIceCandidate`（处理 ICE 候选）、`onAddStream`/`onTrack`（接收远程视频流）和 `onDataChannel`（接收数据通道）。
*   **`requestConnection()`**: 向 `DeviceApp` 发送请求，启动 WebRTC 连接协商过程。
*   **`_handleSignalingMessage(Map<String, dynamic> message)`**: 处理从 `WebSocketService` 接收到的 WebRTC 信令消息（`offer`、`answer`、`ice_candidate`），并更新 `RTCPeerConnection` 的状态。
*   **`sendControlEvent(ControlEvent event)`**: 通过 WebRTC 数据通道发送控制事件到远程设备，实现低延迟的远程控制。
*   **优化**: 配置了多种 WebRTC 参数，旨在实现超低延迟的视频传输和控制响应。

**总结流程：**

1.  **启动**: `control_app` 启动，显示 `SplashScreen`，请求权限。
2.  **设备发现**: 进入 `DeviceListScreen`，`DeviceDiscoveryService` 扫描预设 IP 上的 `DeviceApp`。
3.  **连接**: 用户选择设备后，进入 `ControlScreen`，`WebSocketService` 建立与 `DeviceApp` 的 WebSocket 连接。
4.  **WebRTC 协商**: `WebRTCService` 通过 WebSocket 交换信令（请求连接、offer/answer、ICE candidate），建立 WebRTC 连接。
5.  **视频流**: WebRTC 连接建立后，`DeviceApp` 将屏幕捕获的视频流通过 WebRTC 发送到 `control_app`，并在 `ControlScreen` 中显示。
6.  **远程控制**: 用户在 `ControlScreen` 上进行触摸、滑动或点击控制按钮时，`ControlScreen` 将这些操作转换为 `ControlEvent`，并通过 `WebRTCService` 的数据通道发送到 `DeviceApp`。
7.  **执行控制**: `DeviceApp` 接收到 `ControlEvent` 后，通过 `TouchControlService` 或 `AccessibilityControlService` 执行相应的操作（如模拟点击、滑动、按键）。

这个 `control_app` 的设计通过 WebSocket 进行信令交换，WebRTC 进行媒体流和数据通道传输，实现了高效的远程控制功能。
        