package com.company.tracker

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import org.webrtc.*

/**
 * Employee side of the WebRTC call — the "caller". No UI, no button:
 * EmployeeActivity calls start() the moment the toggle goes on, and
 * stop() the moment it goes off (or the screen leaves foreground).
 * Signals through the same Firestore "calls" collection the web app's
 * webrtc.js already uses, so this is interoperable with owner.html too.
 */
object CallerClient {

    private var peerConnection: PeerConnection? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var callListener: ListenerRegistration? = null
    private var candidatesListener: ListenerRegistration? = null
    private var currentUid: String? = null
    private val db = FirebaseFirestore.getInstance()

    fun start(context: Context, ownerUid: String) {
        stop() // clean up any previous session first

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        currentUid = uid

        val factory = WebRtcFactory.peerConnectionFactory(context)
        val eglBaseContext = WebRtcFactory.eglBase.eglBaseContext
        val callRef = db.collection("calls").document(uid)

        val rtcConfig = PeerConnection.RTCConfiguration(
            listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        )

        val pc = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                val data = hashMapOf(
                    "sdpMid" to candidate.sdpMid,
                    "sdpMLineIndex" to candidate.sdpMLineIndex,
                    "candidate" to candidate.sdp
                )
                callRef.collection("callerCandidates").add(data)
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
            override fun onAddStream(stream: MediaStream) {}
            override fun onRemoveStream(stream: MediaStream) {}
            override fun onDataChannel(channel: DataChannel) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {}
        }) ?: return
        peerConnection = pc

        // Camera (front-facing if available) + mic — no local preview, by
        // design: the employee never sees their own feed, only the owner does.
        val capturer = createCameraCapturer(context) ?: return
        videoCapturer = capturer
        val surfaceHelper = SurfaceTextureHelper.create("CaptureThread", eglBaseContext)
        val videoSource = factory.createVideoSource(capturer.isScreencast)
        capturer.initialize(surfaceHelper, context, videoSource.capturerObserver)
        capturer.startCapture(1280, 720, 30)
        val videoTrack = factory.createVideoTrack("ARDAMSv0", videoSource)

        val audioSource = factory.createAudioSource(MediaConstraints())
        val audioTrack = factory.createAudioTrack("ARDAMSa0", audioSource)

        val stream = factory.createLocalMediaStream("ARDAMS")
        stream.addTrack(videoTrack)
        stream.addTrack(audioTrack)
        pc.addStream(stream)

        pc.createOffer(object : SdpObserverAdapter() {
                        override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                pc.setLocalDescription(SdpObserverAdapter(), desc)

                callRef.set(
                    hashMapOf(
                        "offer" to hashMapOf("type" to desc.type.canonicalForm(), "sdp" to desc.description),
                        "calleeId" to ownerUid,
                        "status" to "live"
                    )
                )

                callListener = callRef.addSnapshotListener { snap, _ ->
                    val data = snap?.data ?: return@addSnapshotListener
                    val answer = data["answer"] as? Map<*, *> ?: return@addSnapshotListener
                    if (pc.remoteDescription == null) {
                        val sdp = SessionDescription(SessionDescription.Type.ANSWER, answer["sdp"] as String)
                        pc.setRemoteDescription(SdpObserverAdapter(), sdp)
                    }
                }

                candidatesListener = callRef.collection("calleeCandidates")
                    .addSnapshotListener { snap, _ ->
                        snap?.documentChanges?.forEach { change ->
                            if (change.type == DocumentChange.Type.ADDED) {
                                val d = change.document.data
                                pc.addIceCandidate(
                                    IceCandidate(
                                        d["sdpMid"] as? String,
                                        (d["sdpMLineIndex"] as Long).toInt(),
                                        d["candidate"] as String
                                    )
                                )
                            }
                        }
                    }
            }
        }, MediaConstraints())
    }

    fun stop() {
        candidatesListener?.remove()
        callListener?.remove()
        candidatesListener = null
        callListener = null

        videoCapturer?.let {
            try { it.stopCapture() } catch (_: Exception) {}
            it.dispose()
        }
        videoCapturer = null

        peerConnection?.close()
        peerConnection = null

        currentUid?.let {
            db.collection("calls").document(it)
                .set(mapOf("status" to "ended"), SetOptions.merge())
        }
        currentUid = null
    }

    private fun createCameraCapturer(context: Context): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames
        for (name in deviceNames) {
            if (enumerator.isFrontFacing(name)) {
                enumerator.createCapturer(name, null)?.let { return it }
            }
        }
        for (name in deviceNames) {
            enumerator.createCapturer(name, null)?.let { return it }
        }
        return null
    }
}
