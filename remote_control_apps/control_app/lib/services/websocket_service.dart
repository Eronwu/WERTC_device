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
  
  Stream<Map<String, dynamic>> get messageStream => _messageController.stream;
  bool get isConnected => _channel != null;
  
  Future<bool> connect(Device device) async {
    try {
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
          disconnect();
        },
        onDone: () {
          debugPrint('WebSocket connection closed');
          disconnect();
        },
      );
      
      // Wait a moment to ensure connection is established
      await Future.delayed(const Duration(milliseconds: 500));
      
      return true;
    } catch (e) {
      debugPrint('Failed to connect: $e');
      return false;
    }
  }
  
  void disconnect() {
    _channel?.sink.close();
    _channel = null;
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
    disconnect();
    _messageController.close();
  }
}

