package com.fongmi.android.tv.player.engine;

/** Runtime capability contract used to hide controls that cannot affect the active engine. */
public record PlaybackCapabilities(
        boolean volumeGain,
        boolean secondarySubtitle,
        boolean audioEqualizer,
        boolean audioDynamics,
        boolean videoColorAdjustments,
        boolean videoDetailEnhancement,
        boolean videoShaders) {

    public static PlaybackCapabilities forEngine(PlayerEngine.Type type) {
        return forEngine(type, true, true, true);
    }

    public static PlaybackCapabilities forEngine(PlayerEngine.Type type, boolean audio, boolean video, boolean sharpness) {
        // EXO uses a public PCM AudioProcessor + Media3 GlEffect chain. MPV uses public libmpv
        // af/equalizer properties; zero-copy MPV reports video=false at runtime because frames bypass GPU.
        return new PlaybackCapabilities(true, true, audio, audio, video, video && sharpness, video && type == PlayerEngine.Type.EXO);
    }
}
