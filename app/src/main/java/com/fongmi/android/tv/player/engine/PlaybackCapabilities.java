package com.fongmi.android.tv.player.engine;

/**
 * Explicit public-API capability contract. Unsupported private-AAR effects stay disabled instead
 * of exposing controls that silently do nothing or are too expensive for low-power devices.
 */
public record PlaybackCapabilities(
        boolean volumeGain,
        boolean secondarySubtitle,
        boolean audioEqualizer,
        boolean audioDynamics,
        boolean videoColorAdjustments,
        boolean videoShaders) {

    public static PlaybackCapabilities forEngine(PlayerEngine.Type type) {
        // LoudnessEnhancer backs EXO gain; MPV has native volume gain. The independently rendered
        // secondary subtitle overlay works with either engine. Remaining effects require a future
        // opt-in implementation and device performance gates.
        return new PlaybackCapabilities(true, true, false, false, false, false);
    }
}
