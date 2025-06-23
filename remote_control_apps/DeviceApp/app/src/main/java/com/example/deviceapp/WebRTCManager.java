package com.example.deviceapp;

import android.content.Context;
import android.util.Log;

import org.webrtc.*;
import org.java_websocket.WebSocket;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

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
    private TouchControlService touchControlService;

    private final static int FRAME_RATE = 60; // Increased to 60 fps for lower latency
    private final static int MAX_BITRATE = 8000000; // Increased to 8 Mbps for better quality and lower latency
    private final static int MIN_BITRATE = 1000000; // Minimum 1 Mbps for consistent quality
    
    private static final String[] MANDATORY_FIELDS = {
        "OfferToReceiveAudio",
        "OfferToReceiveVideo"
    };
    
    public WebRTCManager(Context context, ScreenCaptureService screenCaptureService) {
        this.screenCaptureService = screenCaptureService;
        this.touchControlService = new TouchControlService();
        initializePeerConnectionFactory(context);
    }
    
    private void initializePeerConnectionFactory(Context context) {
        // Fast initialization - disable internal tracer for performance
        PeerConnectionFactory.InitializationOptions initializationOptions =
                PeerConnectionFactory.InitializationOptions.builder(context)
                        .setEnableInternalTracer(false)  // Disable for faster startup
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
        
        Log.d(TAG, "PeerConnectionFactory initialized with fast config");
    }
    
    public void createPeerConnection(WebSocket webSocket) {
        this.webSocket = webSocket;
        
        // Empty ICE servers for local network optimization
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE; // Fast gathering
        
        // Aggressive low latency optimizations
        rtcConfig.iceConnectionReceivingTimeout = 500; // Reduced to 500ms for faster connection
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA; // Faster key exchange
        rtcConfig.iceCandidatePoolSize = 0; // No pre-gathering for faster start
        rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.ALL;
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN; // Modern standard
        
        // Additional ultra-low latency optimizations
        rtcConfig.iceBackupCandidatePairPingInterval = 1000; // Faster backup candidate checking
        rtcConfig.iceCheckMinInterval = 100; // Faster ICE connectivity checks
//        rtcConfig.iceUnwritableTimeout = 1000; // Faster timeout for unwritable connections
//        rtcConfig.iceWritableTimeout = 1000; // Faster timeout for writable connections

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, new PeerConnectionObserver());
        
        // Create data channel for control events
        DataChannel.Init dataChannelInit = new DataChannel.Init();
        dataChannelInit.ordered = true;
        dataChannel = peerConnection.createDataChannel("control", dataChannelInit);
        
        // Set up observer for the locally created data channel
        setupDataChannelObserver(dataChannel);
        
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
                    // Set up encoding parameters for low latency screen sharing
                    for (RtpParameters.Encoding encoding : parameters.encodings) {
                        encoding.maxBitrateBps = MAX_BITRATE;
                        encoding.minBitrateBps = MIN_BITRATE;
                        encoding.maxFramerate = FRAME_RATE;
                        // TODO: need to set the value by setting button.
                        encoding.scaleResolutionDownBy = 2.0; // No downscaling
                        // Enable adaptive bitrate for network conditions
                        // google-webrtc-1.0.32006 version don't have this attribute and it can adjust bps auto.
//                        encoding.adaptive = true;
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
        Log.d(TAG, "Creating offer - fast mode");
        
        // Create offer with explicit send-only constraints for screen sharing
        MediaConstraints constraints = new MediaConstraints();
        // Explicitly disable receiving for send-only screen share
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"));
        
        peerConnection.createOffer(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.d(TAG, "Offer created successfully");
                // Only show in debug mode
//                Log.d(TAG, "Original Offer SDP: " + sessionDescription.description);

                // Optimize SDP for low latency screen sharing
                String modifiedSdp = sessionDescription.description
                    .replace("a=sendrecv", "a=sendonly")
                        .replace("a=fmtp:96", "a=fmtp:96 max-fr=60;max-fs=8160"); // Limit frame size
//                    // Force VP8 codec for lower latency (VP8 has better low-latency performance than H.264)
                        // RK3568 cannot support VP8 encoder!
//                    .replace("a=rtpmap:96 H264/90000", "a=rtpmap:96 VP8/90000")
//                    .replace("a=fmtp:96", "a=fmtp:96 max-fr=60;max-fs=8160;x-google-min-bitrate=1000000;x-google-max-bitrate=8000000;x-google-start-bitrate=4000000")
                    // Add ultra-low latency RTP parameters
//                    .replace("a=rtcp-fb:96", "a=rtcp-fb:96 nack\n" +
//                                              "a=rtcp-fb:96 nack pli\n" +
//                                              "a=rtcp-fb:96 ccm fir\n" +
//                                              "a=rtcp-fb:96 goog-remb\n" +
//                                              "a=rtcp-fb:96 transport-cc")
                    // Add jitter buffer optimization
//                    .replace("a=rtpmap:96", "a=rtpmap:96") + "\n" +
//                    "a=x-google-min-bitrate:1000000\n" +
//                    "a=x-google-max-bitrate:8000000\n" +
//                    "a=x-google-start-bitrate:4000000\n" +
//                    "a=x-google-cpu-overuse-detection:false\n" +
//                    "a=x-google-congestion-window:false";
                    
                SessionDescription fixedSessionDescription = new SessionDescription(
                    sessionDescription.type, modifiedSdp);

                // Only show in debug mode
//                Log.v(TAG, "Modified Offer SDP: " + fixedSessionDescription.description);
                
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
                try {
                    // Convert ByteBuffer to String
                    byte[] data = new byte[buffer.data.remaining()];
                    buffer.data.get(data);
                    String message = new String(data, StandardCharsets.UTF_8);
                    
                    Log.d(TAG, "Received data channel message: " + message);
                    
                    // Parse and handle control event
                    ControlEvent event = gson.fromJson(message, ControlEvent.class);
                    touchControlService.handleControlEvent(event);
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error handling data channel message", e);
                }
            }
        });
    }
}