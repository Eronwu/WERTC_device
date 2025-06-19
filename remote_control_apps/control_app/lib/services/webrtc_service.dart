import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:flutter_webrtc/flutter_webrtc.dart';
import '../models/control_event.dart';
import 'websocket_service.dart';

class WebRTCService {
  RTCPeerConnection? _peerConnection;
  RTCDataChannel? _dataChannel;
  MediaStream? _remoteStream;
  final WebSocketService _webSocketService;
  
  final StreamController<MediaStream> _remoteStreamController = 
      StreamController<MediaStream>.broadcast();
  
  Stream<MediaStream> get remoteStreamStream => _remoteStreamController.stream;
  
  WebRTCService(this._webSocketService) {
    _webSocketService.messageStream.listen(_handleSignalingMessage);
  }
  
  Future<void> initialize() async {
    // Create peer connection - removed STUN for local network optimization
    final configuration = {
      'iceServers': [], // Empty for local network only
    };
    
    _peerConnection = await createPeerConnection(configuration);
    
    // Handle ICE candidates
    _peerConnection!.onIceCandidate = (RTCIceCandidate candidate) {
      _webSocketService.sendIceCandidate(
        candidate.candidate!,
        candidate.sdpMid!,
        candidate.sdpMLineIndex!,
      );
    };
    
    // Handle remote stream (legacy callback)
    _peerConnection!.onAddStream = (MediaStream stream) {
      debugPrint('Remote stream added with ${stream.getVideoTracks().length} video tracks');
      for (var track in stream.getVideoTracks()) {
        debugPrint('Video track: ${track.id}, enabled: ${track.enabled}');
      }
      _remoteStream = stream;
      _remoteStreamController.add(stream);
    };
    
    _peerConnection!.onRemoveStream = (stream) {
      debugPrint('Remote stream removed');
      _remoteStream = null;
    };
    
    // Handle remote track (modern callback - preferred)
    _peerConnection!.onTrack = (event) {
      debugPrint('Remote track added: ${event.track.kind}, id: ${event.track.id}');
      debugPrint('Track streams: ${event.streams.length}');
      
      if (event.track.kind == 'video') {
        debugPrint('Video track received, enabled: ${event.track.enabled}');
        if (event.streams.isNotEmpty) {
          debugPrint('Adding video stream to controller');
          _remoteStream = event.streams[0];
          _remoteStreamController.add(event.streams[0]);
        } else {
          debugPrint('No streams associated with video track');
        }
      }
    };
    
    // Handle data channel
    _peerConnection!.onDataChannel = (RTCDataChannel channel) {
      debugPrint('Received data channel: ${channel.label}');
      _dataChannel = channel;
    };
    
    // Handle connection state changes
    _peerConnection!.onConnectionState = (state) {
      debugPrint('WebRTC connection state: $state');
    };
    
    _peerConnection!.onIceConnectionState = (state) {
      debugPrint('ICE connection state: $state');
    };
    
    _peerConnection!.onSignalingState = (state) {
      debugPrint('Signaling state: $state');
    };
    
    // Create data channel for sending control events
    _dataChannel = await _peerConnection!.createDataChannel(
      'control',
      RTCDataChannelInit()..ordered = true,
    );
    
    // Monitor data channel state
    _dataChannel!.onDataChannelState = (state) {
      debugPrint('Data channel state: $state');
    };
    
    debugPrint('WebRTC initialized');
  }
  
  Future<void> requestConnection() async {
    // Request WebRTC connection from device instead of creating offer
    _webSocketService.requestWebRTCConnection();
    debugPrint('Requested WebRTC connection from device');
  }
  
  Future<void> createOffer() async {
    if (_peerConnection == null) return;
    
    // Create offer with video constraints - use proper MediaConstraints format
    final constraints = <String, dynamic>{
      'mandatory': <String, dynamic>{
        'OfferToReceiveAudio': false,
        'OfferToReceiveVideo': true,
      },
      'optional': <Map<String, dynamic>>[],
    };
    
    final offer = await _peerConnection!.createOffer(constraints);
    await _peerConnection!.setLocalDescription(offer);
    
    _webSocketService.sendOffer(offer.sdp!);
    debugPrint('Created and sent offer');
  }
  
  Future<void> _handleSignalingMessage(Map<String, dynamic> message) async {
    final type = message['type'];
    debugPrint('Received signaling message: $type');
    
    switch (type) {
      case 'offer':
        // Handle both standard and nested sdp formats from device
        final sdpData = message['sdp'];
        final sdpString = sdpData is Map ? sdpData['sdp'] : sdpData;
        debugPrint('Received offer from device');
        await _handleOffer(sdpString);
        break;
      case 'answer':
        // Handle both standard and nested sdp formats from device
        final sdpData = message['sdp'];
        final sdpString = sdpData is Map ? sdpData['sdp'] : sdpData;
        debugPrint('Received answer from device');
        await _handleAnswer(sdpString);
        break;
      case 'ice_candidate':
        debugPrint('Received ICE candidate from device');
        await _handleIceCandidate(message);
        break;
      default:
        debugPrint('Unknown message type: $type');
    }
  }
  
  Future<void> _handleOffer(String sdp) async {
    if (_peerConnection == null) return;
    
    debugPrint('Setting remote offer SDP');
    await _peerConnection!.setRemoteDescription(
      RTCSessionDescription(sdp, 'offer'),
    );
    
    // Create answer - let WebRTC handle the direction based on the offer
    final constraints = <String, dynamic>{
      'mandatory': <String, dynamic>{
        'OfferToReceiveAudio': false,
        'OfferToReceiveVideo': true,
      },
      'optional': <Map<String, dynamic>>[],
    };
    
    debugPrint('Creating answer');
    final answer = await _peerConnection!.createAnswer(constraints);
    
    // The answer should automatically be recvonly to match the sendonly offer; only for debug msg
    // debugPrint('Answer SDP: ${answer.sdp}');
    
    await _peerConnection!.setLocalDescription(answer);
    
    _webSocketService.sendAnswer(answer.sdp!);
    debugPrint('Handled offer and sent answer');
  }
  
  Future<void> _handleAnswer(String sdp) async {
    if (_peerConnection == null) return;
    
    await _peerConnection!.setRemoteDescription(
      RTCSessionDescription(sdp, 'answer'),
    );
    
    debugPrint('Handled answer');
  }
  
  Future<void> _handleIceCandidate(Map<String, dynamic> message) async {
    if (_peerConnection == null) return;
    
    // Handle both standard and nested candidate formats from device
    final candidateData = message['candidate'];
    final candidateString = candidateData is Map ? candidateData['candidate'] : candidateData;
    final sdpMid = candidateData is Map ? candidateData['sdpMid'] : message['sdpMid'];
    final sdpMLineIndex = candidateData is Map ? candidateData['sdpMLineIndex'] : message['sdpMLineIndex'];
    
    final candidate = RTCIceCandidate(
      candidateString,
      sdpMid,
      sdpMLineIndex,
    );
    
    await _peerConnection!.addCandidate(candidate);
    debugPrint('Added ICE candidate');
  }
  
  void sendControlEvent(ControlEvent event) {
    if (_dataChannel?.state == RTCDataChannelState.RTCDataChannelOpen) {
      final jsonString = jsonEncode(event.toJson());
      _dataChannel!.send(RTCDataChannelMessage(jsonString));
      debugPrint('Sent control event: ${event.type} at (${event.x}, ${event.y})');
      debugPrint('JSON sent: $jsonString');
    } else {
      debugPrint('Data channel not open, cannot send control event');
    }
  }
  
  void cleanupConnection() {
    debugPrint('Cleaning up WebRTC connection...');
    _dataChannel?.close();
    _dataChannel = null;
    _peerConnection?.close();
    _peerConnection = null;
    _remoteStream = null;
    _disposed = false; // Reset disposed flag for reuse
    debugPrint('WebRTC connection cleanup completed');
  }

  bool get isConnected {
    return _peerConnection != null && 
           _dataChannel != null && 
           _dataChannel!.state == RTCDataChannelState.RTCDataChannelOpen;
  }

  bool get hasVideoStream {
    return _remoteStream != null && _remoteStream!.getVideoTracks().isNotEmpty;
  }

  bool _disposed = false;
  
  void dispose() {
    if (_disposed) return;
    _disposed = true;
    
    cleanupConnection();
    if (!_remoteStreamController.isClosed) {
      _remoteStreamController.close();
    }
  }
}

