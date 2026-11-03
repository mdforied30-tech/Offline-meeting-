package com.company.tracker

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import org.webrtc.*

/**
 * Owner's video screen — the "callee" side of the call. Opened by tapping
 * an employee's row in OwnerActivity. Signals through the same Firestore
 * "calls" collection the web app's webrtc.js uses.
 */
class ViewerActivity : AppCompatActivity() {

    private lateinit var renderer: SurfaceViewRenderer
    private var peerConnection: PeerConnection? = null
    private var callListener: ListenerRegistration? = null
    private var candidatesListener: ListenerRegistration? = null
    private val db = FirebaseFirestore.getInstance()
    private var employeeUid: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_viewer)

        employeeUid = intent.getStringExtra("employeeUid") ?: run { finish(); return }

        renderer = findViewById(R.id.remoteView)
        renderer.init(WebRtcFactory.eglBase.eglBaseContext, null)
        renderer.setMirror(false)

        startViewing()
    }

    private fun startViewing() {
        val ownerUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val factory = WebRtcFactory.peerConnectionFactory(this)
        val rtcConfig = PeerConnection.RTCConfiguration(
            listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        )
        val callRef = db.collection("calls").document(employeeUid)

        callListener = callRef.addSnapshotListener { snap, _ ->
            val data = snap?.data ?: return@addSnapshotListener
            if (data["calleeId"] as? String != ownerUid) return@addSnapshotListener
            val offerMap = data["offer"] as? Map<*, *> ?: return@addSnapshotListener
            if (peerConnection != null) return@addSnapshotListener // already connected/connecting

            val pc = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate) {
                    val d = hashMapOf(
                        "sdpMid" to candidate.sdpMid,
                        "sdpMLineIndex" to candidate.sdpMLineIndex,
                        "candidate" to candidate.sdp
                    )
                    callRef.collection("calleeCandidates").add(d)
                }
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
                override fun onSignalingChange(state: PeerConnection.SignalingState) {}
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {}
                override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
                override fun onAddStream(stream: MediaStream) {
                    runOnUiThread { stream.videoTracks.firstOrNull()?.addSink(renderer) }
                }
                override fun onRemoveStream(stream: MediaStream) {}
                override fun onDataChannel(channel: DataChannel) {}
                override fun onRenegotiationNeeded() {}
                override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
                    runOnUiThread {
                        (receiver.track() as? VideoTrack)?.addSink(renderer)
                    }
                }
            }) ?: return@addSnapshotListener
            peerConnection = pc

            val offerSdp = SessionDescription(SessionDescription.Type.OFFER, offerMap["sdp"] as String)
            pc.setRemoteDescription(object : SdpObserverAdapter() {
                override fun onSetSuccess() {
                    pc.createAnswer(object : SdpObserverAdapter() {
                                                override fun onCreateSuccess(desc: SessionDescription?) {
                            if (desc == null) return
                            pc.setLocalDescription(SdpObserverAdapter(), desc)
                            callRef.set(
                                mapOf("answer" to mapOf("type" to desc.type.canonicalForm(), "sdp" to desc.description)),
                                SetOptions.merge()
                            )
                        }
                    }, MediaConstraints())
                }
            }, offerSdp)

            candidatesListener = callRef.collection("callerCandidates")
                .addSnapshotListener { snap2, _ ->
                    snap2?.documentChanges?.forEach { change ->
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
    }

    override fun onDestroy() {
        super.onDestroy()
        callListener?.remove()
        candidatesListener?.remove()
        peerConnection?.close()
        renderer.release()
    }
}
