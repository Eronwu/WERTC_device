import 'package:flutter/material.dart';
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
    _initializeServices();
    _connectToDevice();
  }

  @override
  void dispose() {
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
      appBar: AppBar(
        title: Text(widget.device.name),
        actions: [
          IconButton(
            icon: const Icon(Icons.close),
            onPressed: _disconnect,
          ),
        ],
      ),
      body: Column(
        children: [
          // Connection status
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(16),
            color: _isConnected ? Colors.green[100] : Colors.red[100],
            child: Row(
              children: [
                Icon(
                  _isConnected ? Icons.check_circle : Icons.error,
                  color: _isConnected ? Colors.green : Colors.red,
                ),
                const SizedBox(width: 8),
                Text(
                  _connectionStatus,
                  style: TextStyle(
                    color: _isConnected ? Colors.green[800] : Colors.red[800],
                    fontWeight: FontWeight.bold,
                  ),
                ),
                if (_isConnecting) ...[
                  const Spacer(),
                  const SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  ),
                ],
              ],
            ),
          ),
          
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
          
          // Control buttons
          Container(
            padding: const EdgeInsets.all(16),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                ElevatedButton.icon(
                  onPressed: _isConnected ? () {
                    // Send back button event
                    final event = ControlEvent.click(-1, -1); // Special coordinates for back
                    _webRTCService.sendControlEvent(event);
                  } : null,
                  icon: const Icon(Icons.arrow_back),
                  label: const Text('Back'),
                ),
                ElevatedButton.icon(
                  onPressed: _isConnected ? () {
                    // Send home button event
                    final event = ControlEvent.click(-2, -2); // Special coordinates for home
                    _webRTCService.sendControlEvent(event);
                  } : null,
                  icon: const Icon(Icons.home),
                  label: const Text('Home'),
                ),
                ElevatedButton.icon(
                  onPressed: _isConnected ? () {
                    // Send menu button event
                    final event = ControlEvent.click(-3, -3); // Special coordinates for menu
                    _webRTCService.sendControlEvent(event);
                  } : null,
                  icon: const Icon(Icons.menu),
                  label: const Text('Menu'),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

