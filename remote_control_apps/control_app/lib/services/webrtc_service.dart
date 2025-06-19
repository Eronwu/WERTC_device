import 'dart:async';
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
    // Create peer connection
    final configuration = {
      'iceServers': [
        {'urls': 'stun:stun.l.google.com:19302'},
      ],
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
    
    // Handle remote stream
    _peerConnection!.onAddStream = (MediaStream stream) {
      debugPrint('Remote stream added with ${stream.getVideoTracks().length} video tracks');
      _remoteStream = stream;
      _remoteStreamController.add(stream);
    };
    
    _peerConnection!.onRemoveStream = (stream) {
      debugPrint('Remote stream removed');
      _remoteStream = null;
    };
    
    _peerConnection!.onTrack = (event) {
      debugPrint('Remote track added: ${event.track.kind}');
      if (event.streams.isNotEmpty) {
        _remoteStream = event.streams[0];
        _remoteStreamController.add(event.streams[0]);
      }
    };
    
    // Handle data channel
    _peerConnection!.onDataChannel = (RTCDataChannel channel) {
      debugPrint('Received data channel: ${channel.label}');
      _dataChannel = channel;
    };
    
    // Create data channel for control events
    _dataChannel = await _peerConnection!.createDataChannel(
      'control',
      RTCDataChannelInit()..ordered = true,
    );
    
    debugPrint('WebRTC initialized');
  }
  
  Future<void> createOffer() async {
    if (_peerConnection == null) return;
    
    // Create offer with video constraints
    final constraints = {
      'offerToReceiveAudio': false,
      'offerToReceiveVideo': true,
    };
    
    final offer = await _peerConnection!.createOffer(constraints);
    await _peerConnection!.setLocalDescription(offer);
    
    _webSocketService.sendOffer(offer.sdp!);
    debugPrint('Created and sent offer');
  }
  
  Future<void> _handleSignalingMessage(Map<String, dynamic> message) async {
    final type = message['type'];
    
    switch (type) {
      case 'offer':
        // Handle nested sdp object from device
        final sdpData = message['sdp'];
        final sdpString = sdpData is Map ? sdpData['sdp'] : sdpData;
        await _handleOffer(sdpString);
        break;
      case 'answer':
        // Handle nested sdp object from device
        final sdpData = message['sdp'];
        final sdpString = sdpData is Map ? sdpData['sdp'] : sdpData;
        await _handleAnswer(sdpString);
        break;
      case 'ice_candidate':
        await _handleIceCandidate(message);
        break;
    }
  }
  
  Future<void> _handleOffer(String sdp) async {
    if (_peerConnection == null) return;
    
    await _peerConnection!.setRemoteDescription(
      RTCSessionDescription(sdp, 'offer'),
    );
    
    final answer = await _peerConnection!.createAnswer();
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
    
    // Handle nested candidate object from device
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
      final jsonString = event.toJson().toString();
      _dataChannel!.send(RTCDataChannelMessage(jsonString));
      debugPrint('Sent control event: ${event.type}');
    } else {
      debugPrint('Data channel not open, cannot send control event');
    }
  }
  
  void dispose() {
    _dataChannel?.close();
    _peerConnection?.close();
    _remoteStreamController.close();
  }
}

