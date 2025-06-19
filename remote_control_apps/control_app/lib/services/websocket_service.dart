import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:web_socket_channel/web_socket_channel.dart';
import '../models/device.dart';
import '../models/control_event.dart';

class WebSocketService {
  WebSocketChannel? _channel;
  final StreamController<Map<String, dynamic>> _messageController = 
      StreamController<Map<String, dynamic>>.broadcast();
  final StreamController<bool> _connectionController = 
      StreamController<bool>.broadcast();
  
  Device? _lastDevice;
  Timer? _reconnectTimer;
  bool _isReconnecting = false;
  
  Stream<Map<String, dynamic>> get messageStream => _messageController.stream;
  Stream<bool> get connectionStream => _connectionController.stream;
  bool get isConnected => _channel != null;
  bool get isReconnecting => _isReconnecting;
  
  Future<bool> connect(Device device) async {
    try {
      _lastDevice = device;
      _cancelReconnectTimer();
      
      // Clean up any existing connection
      if (_channel != null) {
        try {
          _channel!.sink.close();
        } catch (e) {
          debugPrint('Error closing existing connection: $e');
        }
        _channel = null;
      }
      
      final uri = Uri.parse('ws://${device.ipAddress}:${device.port}');
      debugPrint('Connecting to: $uri');
      
      _channel = WebSocketChannel.connect(uri);
      
      // Listen for messages
      _channel!.stream.listen(
        (data) {
          try {
            final json = jsonDecode(data);
            _messageController.add(json);
          } catch (e) {
            debugPrint('Error parsing WebSocket message: $e');
          }
        },
        onError: (error) {
          debugPrint('WebSocket error: $error');
          _handleDisconnection();
        },
        onDone: () {
          debugPrint('WebSocket connection closed');
          _handleDisconnection();
        },
      );
      
      // Minimal delay for connection establishment
      await Future.delayed(const Duration(milliseconds: 50));
      
      // Connection ready immediately
      
      _connectionController.add(true);
      _isReconnecting = false;
      return true;
    } catch (e) {
      debugPrint('Failed to connect: $e');
      _connectionController.add(false);
      _scheduleReconnection();
      return false;
    }
  }
  
  void disconnect() {
    _cancelReconnectTimer();
    _channel?.sink.close();
    _channel = null;
    _connectionController.add(false);
  }

  void _handleDisconnection() {
    _channel = null;
    _connectionController.add(false);
    _scheduleReconnection();
  }

  void _scheduleReconnection() {
    if (_lastDevice != null && !_isReconnecting) {
      _isReconnecting = true;
      _reconnectTimer = Timer(const Duration(seconds: 3), () {
        if (_lastDevice != null) {
          debugPrint('Attempting to reconnect...');
          connect(_lastDevice!);
        }
      });
    }
  }

  void _cancelReconnectTimer() {
    _reconnectTimer?.cancel();
    _reconnectTimer = null;
  }
  
  void sendMessage(Map<String, dynamic> message) {
    if (_channel != null) {
      final jsonString = jsonEncode(message);
      _channel!.sink.add(jsonString);
      debugPrint('Sent: $jsonString');
    } else {
      debugPrint('Cannot send message: not connected');
    }
  }
  
  void sendOffer(String sdp) {
    sendMessage({
      'type': 'offer',
      'sdp': sdp,
    });
  }
  
  void sendAnswer(String sdp) {
    sendMessage({
      'type': 'answer',
      'sdp': sdp,
    });
  }
  
  void sendIceCandidate(String candidate, String sdpMid, int sdpMLineIndex) {
    sendMessage({
      'type': 'ice_candidate',
      'candidate': candidate,
      'sdpMid': sdpMid,
      'sdpMLineIndex': sdpMLineIndex,
    });
  }
  
  void sendControlEvent(ControlEvent event) {
    sendMessage({
      'type': 'control_event',
      'event': event.toJson(),
    });
  }
  
  void requestWebRTCConnection() {
    sendMessage({
      'type': 'start_webrtc',
    });
  }
  
  void dispose() {
    _cancelReconnectTimer();
    disconnect();
    if (!_messageController.isClosed) {
      _messageController.close();
    }
    if (!_connectionController.isClosed) {
      _connectionController.close();
    }
  }
}

