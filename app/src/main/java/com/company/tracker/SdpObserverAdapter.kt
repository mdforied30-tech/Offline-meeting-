package com.company.tracker

import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

/**
 * WebRTC's SdpObserver interface forces you to implement all four methods
 * every time, even when you only care about one. This gives safe no-op
 * defaults so each call site only overrides what it actually needs.
 */
open class SdpObserverAdapter : SdpObserver {
    override fun onCreateSuccess(description: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) {}
    override fun onSetFailure(error: String?) {}
}
