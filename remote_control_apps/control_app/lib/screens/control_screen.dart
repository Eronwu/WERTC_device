import 'package:flutter/material.dart';
import 'dart:math';
import 'package:flutter/services.dart';
import 'package:flutter_webrtc/flutter_webrtc.dart';
import '../models/device.dart';
import '../models/control_event.dart';
import '../services/websocket_service.dart';
import '../services/webrtc_service.dart';

class ControlScreen extends StatefulWidget {
  final Device device;

  const ControlScreen({Key? key, required this.device}) : super(key: key);

  @override
  State<ControlScreen> createState() => _ControlScreenState();
}

class _ControlScreenState extends State<ControlScreen> with WidgetsBindingObserver {
  late WebSocketService _webSocketService;
  late WebRTCService _webRTCService;
  RTCVideoRenderer _remoteRenderer = RTCVideoRenderer();
  final GlobalKey _videoKey = GlobalKey();
  
  bool _isConnected = false;
  bool _isConnecting = false;
  String _connectionStatus = 'Disconnected'; // Used in UI display
  
  // Gesture tracking for swipe detection
  Offset? _panStartPosition;
  Offset? _panEndPosition;
  bool _isPanning = false;

  @override
  void initState() {
    super.initState();
    // Set landscape orientation and fullscreen
    SystemChrome.setPreferredOrientations([
      DeviceOrientation.landscapeLeft,
      DeviceOrientation.landscapeRight,
    ]);
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
    
    // Add app lifecycle observer
    WidgetsBinding.instance.addObserver(this);
    
    _initializeServices();
    _connectToDevice();
  }

  @override
  void dispose() {
    // Restore portrait orientation and system UI
    SystemChrome.setPreferredOrientations([
      DeviceOrientation.portraitUp,
      DeviceOrientation.portraitDown,
    ]);
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
    
    // Remove app lifecycle observer
    WidgetsBinding.instance.removeObserver(this);
    
    _remoteRenderer.dispose();
    _webRTCService.dispose();
    _webSocketService.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) async {
    super.didChangeAppLifecycleState(state);
    
    switch (state) {
      case AppLifecycleState.paused:
        debugPrint('App paused - clearing video renderer');
        // Clear video stream to prevent rendering issues
        setState(() {
          _remoteRenderer.srcObject = null;
        });
        break;
      case AppLifecycleState.resumed:
        debugPrint('App resumed - performing full reconnection');
        await _handleAppResume();
        break;
      case AppLifecycleState.detached:
        debugPrint('App detached - cleaning up');
        _webRTCService.dispose();
        _webSocketService.disconnect();
        break;
      default:
        break;
    }
  }

  void _initializeServices() {
    _webSocketService = WebSocketService();
    _webRTCService = WebRTCService(_webSocketService);
    
    _remoteRenderer.initialize();
    
    // Listen for remote stream
    _webRTCService.remoteStreamStream.listen((stream) {
      setState(() {
        _remoteRenderer.srcObject = stream;
      });
    });
    
    // Listen for connection status changes (for UI updates only)
    _webSocketService.connectionStream.listen((connected) {
      setState(() {
        _isConnected = connected;
        _connectionStatus = connected ? 'Connected' : 
          (_webSocketService.isReconnecting ? 'Reconnecting...' : 'Disconnected');
      });
      
      // Don't automatically reinitialize WebRTC here - it's handled in _connectToDevice
    });
  }

  Future<void> _connectToDevice({bool isReconnection = false}) async {
    setState(() {
      _isConnecting = true;
      _connectionStatus = 'Connecting...';
    });

    try {
      // Only recreate services if this is a reconnection
      if (isReconnection) {
        // Ensure clean state before reconnecting
        await _recreateVideoRenderer();
        
        // Reinitialize services
        _webSocketService.dispose();
        _webRTCService.dispose();
        _initializeServices();
      }

      // Connect WebSocket
      final connected = await _webSocketService.connect(widget.device);
      if (!connected) {
        throw Exception('Failed to connect to device');
      }

      // Initialize WebRTC
      await _webRTCService.initialize();
      
      // Request WebRTC connection from device (device will create offer)
      await _webRTCService.requestConnection();

      setState(() {
        _isConnected = true;
        _connectionStatus = 'Connected';
      });

    } catch (e) {
      setState(() {
        _connectionStatus = 'Connection failed: $e';
      });
      
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Failed to connect: $e')),
      );
    } finally {
      setState(() {
        _isConnecting = false;
      });
    }
  }

  Future<void> _disconnect() async {
    // Clean up connections
    _webRTCService.dispose();
    _webSocketService.disconnect();
    
    // Recreate video renderer for clean state
    await _recreateVideoRenderer();
    
    setState(() {
      _isConnected = false;
      _connectionStatus = 'Disconnected';
    });
    
    Navigator.of(context).pop();
  }

  Future<void> _checkAndReconnect() async {
    if (!_isConnected && !_isConnecting) {
      debugPrint('Connection lost, attempting to reconnect...');
      await _connectToDevice(isReconnection: true);
    }
  }

  Future<void> _reinitializeWebRTC() async {
    try {
      debugPrint('Fast WebRTC reinit...');
      
      // Clean up current WebRTC connection first
      _webRTCService.cleanupConnection();
      
      // Recreate video renderer in parallel with WebRTC init
      final rendererFuture = _recreateVideoRenderer();
      final webrtcFuture = _webRTCService.initialize();
      
      // Wait for both to complete
      await Future.wait([rendererFuture, webrtcFuture]);
      
      // Request connection
      await _webRTCService.requestConnection();
      
      debugPrint('Fast WebRTC reinit complete');
    } catch (e) {
      debugPrint('Error in fast WebRTC reinit: $e');
    }
  }

  Future<void> _handleAppResume() async {
    try {
      debugPrint('Handling app resume - performing complete reconnection');
      
      // Use full reconnection logic which handles everything
      await _connectToDevice(isReconnection: true);
      
      debugPrint('App resume reconnection completed successfully');
    } catch (e) {
      debugPrint('Error during app resume reconnection: $e');
    }
  }

  Future<void> _recreateVideoRenderer() async {
    try {
      debugPrint('Recreating video renderer...');
      
      // Clear current stream and dispose renderer
      setState(() {
        _remoteRenderer.srcObject = null;
      });
      
      // Dispose old renderer
      await _remoteRenderer.dispose();
      
      // Create new renderer instance without delay
      _remoteRenderer = RTCVideoRenderer();
      await _remoteRenderer.initialize();
      
      debugPrint('Video renderer recreated');
    } catch (e) {
      debugPrint('Error recreating video renderer: $e');
    }
  }

  void _handleTap(TapUpDetails details) {
    if (!_isConnected || !_webRTCService.isConnected) {
      debugPrint('Cannot handle tap - not connected');
      return;
    }

    // Get the video widget's render box to convert coordinates
    final RenderBox? videoRenderBox = _getVideoRenderBox();
    if (videoRenderBox == null) return;
    
    final localPosition = videoRenderBox.globalToLocal(details.globalPosition);
    
    // Map coordinates from video display size to device screen size
    // Device screen: 1824x1080, Video display: actual widget size
    final videoSize = videoRenderBox.size;
    final deviceX = (localPosition.dx / videoSize.width) * 1824;
    final deviceY = (localPosition.dy / videoSize.height) * 1080;
    
    final event = ControlEvent.click(deviceX, deviceY);
    _webRTCService.sendControlEvent(event);
    debugPrint('Touch mapped: (${localPosition.dx}, ${localPosition.dy}) -> (${deviceX.toInt()}, ${deviceY.toInt()})');
  }

  void _handlePanStart(DragStartDetails details) {
    if (!_isConnected || !_webRTCService.isConnected) return;
    
    final RenderBox? videoRenderBox = _getVideoRenderBox();
    if (videoRenderBox == null) return;
    
    _panStartPosition = videoRenderBox.globalToLocal(details.globalPosition);
    _panEndPosition = _panStartPosition;
    _isPanning = true;
    debugPrint('Pan started at: $_panStartPosition');
  }

  void _handlePanUpdate(DragUpdateDetails details) {
    if (!_isConnected || !_webRTCService.isConnected || !_isPanning) return;
    
    final RenderBox? videoRenderBox = _getVideoRenderBox();
    if (videoRenderBox == null) return;
    
    _panEndPosition = videoRenderBox.globalToLocal(details.globalPosition);
  }

  void _handlePanEnd(DragEndDetails details) {
    if (!_isConnected || !_webRTCService.isConnected || !_isPanning || _panStartPosition == null || _panEndPosition == null) return;

    // Get the video widget's render box to convert coordinates
    final RenderBox? videoRenderBox = _getVideoRenderBox();
    if (videoRenderBox == null) return;
    
    // Calculate actual swipe distance
    final dx = _panEndPosition!.dx - _panStartPosition!.dx;
    final dy = _panEndPosition!.dy - _panStartPosition!.dy;
    final distance = sqrt(dx * dx + dy * dy);
    
    debugPrint('Pan ended. Distance: $distance, Start: $_panStartPosition, End: $_panEndPosition');
    
    // Only treat as swipe if distance is significant (reduced threshold)
    if (distance > 20) {
      final videoSize = videoRenderBox.size;
      
      // Map start and end positions to device coordinates
      final startX = (_panStartPosition!.dx / videoSize.width) * 1824;
      final startY = (_panStartPosition!.dy / videoSize.height) * 1080;
      final endX = (_panEndPosition!.dx / videoSize.width) * 1824;
      final endY = (_panEndPosition!.dy / videoSize.height) * 1080;
      
      // Calculate duration based on distance (faster swipes = shorter duration)
      final duration = (distance * 2).clamp(200, 800).toInt();
      
      final event = ControlEvent.swipe(startX, startY, endX, endY, duration);
      _webRTCService.sendControlEvent(event);
      debugPrint('Swipe: (${startX.toInt()}, ${startY.toInt()}) -> (${endX.toInt()}, ${endY.toInt()}) duration: ${duration}ms');
    } else {
      debugPrint('Distance too small for swipe: $distance');
    }
    
    _isPanning = false;
    _panStartPosition = null;
    _panEndPosition = null;
  }
  
  void _handleLongPress() {
    if (!_isConnected || !_webRTCService.isConnected) return;

    // For long press without specific coordinates, we'll use the center of the screen
    // In a real implementation, you'd track the last touch position
    const deviceX = 1824 / 2; // Center X
    const deviceY = 1080 / 2; // Center Y
    
    // Send long click event (we'll handle this in the device)
    final event = ControlEvent(
      type: 'long_click',
      x: deviceX,
      y: deviceY,
      timestamp: DateTime.now().millisecondsSinceEpoch,
    );
    _webRTCService.sendControlEvent(event);
    debugPrint('Long press at: (${deviceX.toInt()}, ${deviceY.toInt()})');
  }
  
  RenderBox? _getVideoRenderBox() {
    // Find the RTCVideoView widget's render box using the global key
    try {
      return _videoKey.currentContext?.findRenderObject() as RenderBox?;
    } catch (e) {
      debugPrint('Error getting video render box: $e');
      return null;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Stack(
        children: [
          // Main content area with video and controls side by side
          Row(
            children: [
              // Video display area
              Expanded(
                child: Container(
                  color: Colors.black,
                  child: _isConnected
                      ? GestureDetector(
                          onTapUp: _handleTap,
                          onPanStart: _handlePanStart,
                          onPanUpdate: _handlePanUpdate,
                          onPanEnd: _handlePanEnd,
                          onLongPress: _handleLongPress,
                          child: RTCVideoView(
                            _remoteRenderer,
                            key: _videoKey,
                            objectFit: RTCVideoViewObjectFit.RTCVideoViewObjectFitContain,
                          ),
                        )
                      : Center(
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Icon(
                                Icons.videocam_off,
                                size: 64,
                                color: Colors.grey[400],
                              ),
                              const SizedBox(height: 16),
                              Text(
                                'No video stream',
                                style: TextStyle(
                                  color: Colors.grey[400],
                                  fontSize: 18,
                                ),
                              ),
                            ],
                          ),
                        ),
                ),
              ),
              
              // Control sidebar on the right
              Container(
                width: 120,
                color: Colors.black87,
                child: Column(
                  children: [
                    // Connection status at top
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(8),
                      child: Column(
                        children: [
                          Icon(
                            _isConnected ? Icons.check_circle : Icons.error,
                            color: _isConnected ? Colors.green : Colors.red,
                            size: 20,
                          ),
                          const SizedBox(height: 4),
                          Text(
                            _isConnected ? 'Connected' : 'Disconnected',
                            style: TextStyle(
                              color: _isConnected ? Colors.green : Colors.red,
                              fontSize: 10,
                              fontWeight: FontWeight.bold,
                            ),
                            textAlign: TextAlign.center,
                          ),
                          if (_isConnecting)
                            const Padding(
                              padding: EdgeInsets.only(top: 4),
                              child: SizedBox(
                                width: 16,
                                height: 16,
                                child: CircularProgressIndicator(strokeWidth: 2),
                              ),
                            ),
                        ],
                      ),
                    ),
                    
                    // Control buttons in the center
                    Expanded(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          ElevatedButton(
                            onPressed: _isConnected ? () {
                              // Send back button event
                              final event = ControlEvent.click(-1, -1); // Special coordinates for back
                              _webRTCService.sendControlEvent(event);
                            } : null,
                            style: ElevatedButton.styleFrom(
                              minimumSize: const Size(80, 50),
                              backgroundColor: Colors.grey[800],
                              foregroundColor: Colors.white,
                            ),
                            child: const Column(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(Icons.arrow_back, size: 20),
                                Text('Back', style: TextStyle(fontSize: 10)),
                              ],
                            ),
                          ),
                          const SizedBox(height: 16),
                          ElevatedButton(
                            onPressed: _isConnected ? () {
                              // Send home button event
                              final event = ControlEvent.click(-2, -2); // Special coordinates for home
                              _webRTCService.sendControlEvent(event);
                            } : null,
                            style: ElevatedButton.styleFrom(
                              minimumSize: const Size(80, 50),
                              backgroundColor: Colors.grey[800],
                              foregroundColor: Colors.white,
                            ),
                            child: const Column(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(Icons.home, size: 20),
                                Text('Home', style: TextStyle(fontSize: 10)),
                              ],
                            ),
                          ),
                          const SizedBox(height: 16),
                          ElevatedButton(
                            onPressed: _isConnected ? () {
                              // Send menu button event
                              final event = ControlEvent.click(-3, -3); // Special coordinates for menu
                              _webRTCService.sendControlEvent(event);
                            } : null,
                            style: ElevatedButton.styleFrom(
                              minimumSize: const Size(80, 50),
                              backgroundColor: Colors.grey[800],
                              foregroundColor: Colors.white,
                            ),
                            child: const Column(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(Icons.menu, size: 20),
                                Text('Menu', style: TextStyle(fontSize: 10)),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),
                    
                    // Disconnect button at bottom
                    Padding(
                      padding: const EdgeInsets.all(8),
                      child: ElevatedButton(
                        onPressed: () async => await _disconnect(),
                        style: ElevatedButton.styleFrom(
                          minimumSize: const Size(80, 40),
                          backgroundColor: Colors.red[700],
                          foregroundColor: Colors.white,
                        ),
                        child: const Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(Icons.close, size: 16),
                            Text('Exit', style: TextStyle(fontSize: 10)),
                          ],
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          

        ],
      ),
    );
  }
}

