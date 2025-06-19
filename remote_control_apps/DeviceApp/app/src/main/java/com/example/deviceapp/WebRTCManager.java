package com.example.deviceapp;

import android.content.Context;
import android.util.Log;

import org.webrtc.*;
import org.java_websocket.WebSocket;
import com.google.gson.JsonObject;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Arrays;
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
        
        // Create shared EGL context for consistent rendering
        EglBase rootEglBase = EglBase.create();
        
        // Configure video encoder/decoder factories with proper hardware acceleration
        VideoEncoderFactory encoderFactory = new DefaultVideoEncoderFactory(
                rootEglBase.getEglBaseContext(), true, true);
        VideoDecoderFactory decoderFactory = new DefaultVideoDecoderFactory(
                rootEglBase.getEglBaseContext());
        
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        options.networkIgnoreMask = 0; // Don't ignore any network types
        
        peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory();
        
        Log.d(TAG, "PeerConnectionFactory initialized with complete codec support");
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
        
        // Create video track from screen capture - only do this once during peer connection creation
        createVideoTrack();
    }
    
    private void createVideoTrack() {
        try {
            // Prevent creating video track multiple times
            if (videoSource != null || videoTrack != null) {
                Log.w(TAG, "Video track already created, skipping duplicate creation");
                return;
            }
            
            // Create video source from screen capture (isScreencast = true for screen capture)
            videoSource = peerConnectionFactory.createVideoSource(true);
            videoTrack = peerConnectionFactory.createVideoTrack("video_track", videoSource);
            
            // Enable video track
            videoTrack.setEnabled(true);
            
            // Use modern addTrack API instead of deprecated addStream
            // Create stream labels list for track association
            List<String> streamLabels = Arrays.asList("local_stream");
            
            // Add track to peer connection with stream labels
            RtpSender sender = peerConnection.addTrack(videoTrack, streamLabels);
            
            if (sender != null) {
                Log.d(TAG, "Video track added successfully via addTrack, sender: " + sender.id());
                
                // Configure sender parameters for screen sharing
                RtpParameters parameters = sender.getParameters();
                if (parameters != null) {
                    // Set up encoding parameters for screen sharing
                    for (RtpParameters.Encoding encoding : parameters.encodings) {
                        encoding.maxBitrateBps = 2000000; // 2 Mbps max for screen share
                        encoding.maxFramerate = 30; // 30 fps max
                    }
                    sender.setParameters(parameters);
                    Log.d(TAG, "Configured sender parameters for screen sharing");
                }
            } else {
                Log.e(TAG, "Failed to add video track - sender is null");
            }
            
            // Connect screen capture service to video source
            screenCaptureService.setVideoSource(videoSource);
            
            Log.d(TAG, "Video track created and added to peer connection using addTrack API");
        } catch (Exception e) {
            Log.e(TAG, "Error creating video track", e);
        }
    }
    
    public void createOfferWithVideo() {
        // Create offer with explicit send-only constraints for screen sharing
        MediaConstraints constraints = new MediaConstraints();
        // Explicitly disable receiving for send-only screen share
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"));
        
        peerConnection.createOffer(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.d(TAG, "Offer created successfully");
                Log.v(TAG, "Original Offer SDP: " + sessionDescription.description);
                
                // Fix SDP to use sendonly for video (screen sharing sender)
                String modifiedSdp = sessionDescription.description.replace("a=sendrecv", "a=sendonly");
                SessionDescription fixedSessionDescription = new SessionDescription(
                    sessionDescription.type, modifiedSdp);
                
                Log.v(TAG, "Modified Offer SDP: " + fixedSessionDescription.description);
                
                peerConnection.setLocalDescription(new SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {}
                    
                    @Override
                    public void onSetSuccess() {
                        Log.d(TAG, "Local description set successfully");
                        // Send offer to client
                        sendOffer(fixedSessionDescription);
                    }
                    
                    @Override
                    public void onCreateFailure(String s) {
                        Log.e(TAG, "Failed to set local description (create): " + s);
                    }
                    
                    @Override
                    public void onSetFailure(String s) {
                        Log.e(TAG, "Failed to set local description (set): " + s);
                    }
                }, fixedSessionDescription);
            }
            
            @Override
            public void onSetSuccess() {}
            
            @Override
            public void onCreateFailure(String s) {
                Log.e(TAG, "Failed to create offer: " + s);
            }
            
            @Override
            public void onSetFailure(String s) {}
        }, constraints);
    }
    
    private void sendOffer(SessionDescription offer) {
        JsonObject offerMessage = new JsonObject();
        offerMessage.addProperty("type", "offer");
        offerMessage.addProperty("sdp", offer.description);
        
        webSocket.send(gson.toJson(offerMessage));
        Log.d(TAG, "Sent offer to client");
    }
    
    public void handleOffer(JsonObject offerJson) {
        try {
            // This method now handles offers from clients (but in our new flow, device creates offers)
            // Keep it for backward compatibility
            
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
    
    public void handleAnswer(JsonObject answerJson) {
        try {
            String sdpString = answerJson.get("sdp").getAsString();
            String type = "answer";
            
            SessionDescription remoteDescription = new SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(type), sdpString);
            
            peerConnection.setRemoteDescription(new SdpObserver() {
                @Override
                public void onCreateSuccess(SessionDescription sessionDescription) {}
                
                @Override
                public void onSetSuccess() {
                    Log.d(TAG, "Remote answer set successfully");
                }
                
                @Override
                public void onCreateFailure(String s) {
                    Log.e(TAG, "Failed to set remote answer: " + s);
                }
                
                @Override
                public void onSetFailure(String s) {
                    Log.e(TAG, "Failed to set remote answer: " + s);
                }
            }, remoteDescription);
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling answer", e);
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
        answerMessage.addProperty("sdp", answer.description);
        
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
        candidateMessage.addProperty("candidate", candidate.sdp);
        candidateMessage.addProperty("sdpMid", candidate.sdpMid);
        candidateMessage.addProperty("sdpMLineIndex", candidate.sdpMLineIndex);
        
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