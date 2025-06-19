import 'package:flutter/material.dart';
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

class _ControlScreenState extends State<ControlScreen> {
  late WebSocketService _webSocketService;
  late WebRTCService _webRTCService;
  RTCVideoRenderer _remoteRenderer = RTCVideoRenderer();
  
  bool _isConnected = false;
  bool _isConnecting = false;
  String _connectionStatus = 'Disconnected';

  @override
  void initState() {
    super.initState();
    // Set landscape orientation and fullscreen
    SystemChrome.setPreferredOrientations([
      DeviceOrientation.landscapeLeft,
      DeviceOrientation.landscapeRight,
    ]);
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
    
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
    
    _remoteRenderer.dispose();
    _webRTCService.dispose();
    _webSocketService.dispose();
    super.dispose();
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
  }

  Future<void> _connectToDevice() async {
    setState(() {
      _isConnecting = true;
      _connectionStatus = 'Connecting...';
    });

    try {
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

  void _disconnect() {
    _webRTCService.dispose();
    _webSocketService.disconnect();
    
    setState(() {
      _isConnected = false;
      _connectionStatus = 'Disconnected';
    });
    
    Navigator.of(context).pop();
  }

  void _handleTap(TapUpDetails details) {
    if (!_isConnected) return;

    final RenderBox renderBox = context.findRenderObject() as RenderBox;
    final localPosition = renderBox.globalToLocal(details.globalPosition);
    
    // Convert to device coordinates (assuming same aspect ratio for now)
    final event = ControlEvent.click(localPosition.dx, localPosition.dy);
    _webRTCService.sendControlEvent(event);
  }

  void _handlePanUpdate(DragUpdateDetails details) {
    if (!_isConnected) return;

    final RenderBox renderBox = context.findRenderObject() as RenderBox;
    final localPosition = renderBox.globalToLocal(details.globalPosition);
    
    // For continuous movement, send as click events
    final event = ControlEvent.click(localPosition.dx, localPosition.dy);
    _webRTCService.sendControlEvent(event);
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
                          onPanUpdate: _handlePanUpdate,
                          child: RTCVideoView(
                            _remoteRenderer,
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
                        onPressed: _disconnect,
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

