package com.example.deviceapp;

import android.content.Context;
import android.util.Log;

import org.webrtc.*;
import org.java_websocket.WebSocket;
import com.google.gson.JsonObject;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

public class WebRTCManager {
    private static final String TAG = "WebRTCManager";
    
    private PeerConnectionFactory peerConnectionFactory;
    private PeerConnection peerConnection;
    private VideoSource videoSource;
    private VideoTrack videoTrack;
    private DataChannel dataChannel;
    private WebSocket webSocket;
    private Gson gson = new Gson();
    private ScreenCaptureService screenCaptureService;
    
    private static final String[] MANDATORY_FIELDS = {
        "OfferToReceiveAudio",
        "OfferToReceiveVideo"
    };
    
    public WebRTCManager(Context context, ScreenCaptureService screenCaptureService) {
        this.screenCaptureService = screenCaptureService;
        initializePeerConnectionFactory(context);
    }
    
    private void initializePeerConnectionFactory(Context context) {
        PeerConnectionFactory.InitializationOptions initializationOptions =
                PeerConnectionFactory.InitializationOptions.builder(context)
                        .setEnableInternalTracer(true)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);
        
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .createPeerConnectionFactory();
    }
    
    public void createPeerConnection(WebSocket webSocket) {
        this.webSocket = webSocket;
        
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, new PeerConnectionObserver());
        
        // Create data channel for control events
        DataChannel.Init dataChannelInit = new DataChannel.Init();
        dataChannelInit.ordered = true;
        dataChannel = peerConnection.createDataChannel("control", dataChannelInit);
        
        // Create video track from screen capture
        createVideoTrack();
    }
    
    private void createVideoTrack() {
        // Create video source from screen capture
        videoSource = peerConnectionFactory.createVideoSource(false);
        videoTrack = peerConnectionFactory.createVideoTrack("video_track", videoSource);
        
        // Add video track to peer connection
        peerConnection.addTrack(videoTrack, List.of("stream_id"));
        
        // Connect screen capture service to video source
        screenCaptureService.setVideoSource(videoSource);
    }
    
    public void handleOffer(JsonObject offerJson) {
        try {
            String sdpString = offerJson.get("sdp").getAsString();
            String type = "offer";
            
            SessionDescription remoteDescription = new SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(type), sdpString);
            
            peerConnection.setRemoteDescription(new SdpObserver() {
                @Override
                public void onCreateSuccess(SessionDescription sessionDescription) {}
                
                @Override
                public void onSetSuccess() {
                    Log.d(TAG, "Remote description set successfully");
                    createAnswer();
                }
                
                @Override
                public void onCreateFailure(String s) {
                    Log.e(TAG, "Failed to set remote description: " + s);
                }
                
                @Override
                public void onSetFailure(String s) {
                    Log.e(TAG, "Failed to set remote description: " + s);
                }
            }, remoteDescription);
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling offer", e);
        }
    }
    
    private void createAnswer() {
        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        
        peerConnection.createAnswer(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                peerConnection.setLocalDescription(new SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {}
                    
                    @Override
                    public void onSetSuccess() {
                        // Send answer back to client
                        sendAnswer(sessionDescription);
                    }
                    
                    @Override
                    public void onCreateFailure(String s) {
                        Log.e(TAG, "Failed to set local description: " + s);
                    }
                    
                    @Override
                    public void onSetFailure(String s) {
                        Log.e(TAG, "Failed to set local description: " + s);
                    }
                }, sessionDescription);
            }
            
            @Override
            public void onSetSuccess() {}
            
            @Override
            public void onCreateFailure(String s) {
                Log.e(TAG, "Failed to create answer: " + s);
            }
            
            @Override
            public void onSetFailure(String s) {}
        }, constraints);
    }
    
    private void sendAnswer(SessionDescription answer) {
        JsonObject answerMessage = new JsonObject();
        answerMessage.addProperty("type", "answer");
        
        JsonObject sdpJson = new JsonObject();
        sdpJson.addProperty("type", answer.type.canonicalForm());
        sdpJson.addProperty("sdp", answer.description);
        answerMessage.add("sdp", sdpJson);
        
        webSocket.send(gson.toJson(answerMessage));
        Log.d(TAG, "Sent answer to client");
    }
    
    public void handleIceCandidate(JsonObject candidateJson) {
        try {
            String candidate = candidateJson.get("candidate").getAsString();
            String sdpMid = candidateJson.get("sdpMid").getAsString();
            int sdpMLineIndex = candidateJson.get("sdpMLineIndex").getAsInt();
            
            IceCandidate iceCandidate = new IceCandidate(sdpMid, sdpMLineIndex, candidate);
            peerConnection.addIceCandidate(iceCandidate);
            
            Log.d(TAG, "Added ICE candidate");
        } catch (Exception e) {
            Log.e(TAG, "Error handling ICE candidate", e);
        }
    }
    
    public void cleanup() {
        if (videoTrack != null) {
            videoTrack.dispose();
        }
        if (videoSource != null) {
            videoSource.dispose();
        }
        if (peerConnection != null) {
            peerConnection.close();
        }
        if (peerConnectionFactory != null) {
            peerConnectionFactory.dispose();
        }
    }
    
    private class PeerConnectionObserver implements PeerConnection.Observer {
        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
            Log.d(TAG, "Signaling state changed: " + signalingState);
        }
        
        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
            Log.d(TAG, "ICE connection state changed: " + iceConnectionState);
        }
        
        @Override
        public void onIceConnectionReceivingChange(boolean b) {
            Log.d(TAG, "ICE connection receiving change: " + b);
        }
        
        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
            Log.d(TAG, "ICE gathering state changed: " + iceGatheringState);
        }
        
        @Override
        public void onIceCandidate(IceCandidate iceCandidate) {
            Log.d(TAG, "New ICE candidate: " + iceCandidate.toString());
            sendIceCandidate(iceCandidate);
        }
        
        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
            Log.d(TAG, "ICE candidates removed");
        }
        
        @Override
        public void onAddStream(MediaStream mediaStream) {
            Log.d(TAG, "Stream added");
        }
        
        @Override
        public void onRemoveStream(MediaStream mediaStream) {
            Log.d(TAG, "Stream removed");
        }
        
        @Override
        public void onDataChannel(DataChannel dataChannel) {
            Log.d(TAG, "Data channel received");
            setupDataChannelObserver(dataChannel);
        }
        
        @Override
        public void onRenegotiationNeeded() {
            Log.d(TAG, "Renegotiation needed");
        }
        
        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
            Log.d(TAG, "Track added");
        }
    }
    
    private void sendIceCandidate(IceCandidate candidate) {
        JsonObject candidateMessage = new JsonObject();
        candidateMessage.addProperty("type", "ice_candidate");
        
        JsonObject candidateJson = new JsonObject();
        candidateJson.addProperty("candidate", candidate.sdp);
        candidateJson.addProperty("sdpMid", candidate.sdpMid);
        candidateJson.addProperty("sdpMLineIndex", candidate.sdpMLineIndex);
        candidateMessage.add("candidate", candidateJson);
        
        webSocket.send(gson.toJson(candidateMessage));
        Log.d(TAG, "Sent ICE candidate to client");
    }
    
    private void setupDataChannelObserver(DataChannel dataChannel) {
        dataChannel.registerObserver(new DataChannel.Observer() {
            @Override
            public void onBufferedAmountChange(long l) {}
            
            @Override
            public void onStateChange() {
                Log.d(TAG, "Data channel state: " + dataChannel.state());
            }
            
            @Override
            public void onMessage(DataChannel.Buffer buffer) {
                // Handle control events received from client
                Log.d(TAG, "Received data channel message");
                // TODO: Parse and handle control events
            }
        });
    }
}