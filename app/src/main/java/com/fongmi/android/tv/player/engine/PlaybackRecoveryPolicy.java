package com.fongmi.android.tv.player.engine;

import androidx.media3.common.PlaybackException;

/**
 * Bounded, error-code driven recovery decisions for playback failures.
 *
 * <p>Kept free of player/Android state on purpose: the decision table is the part worth reviewing
 * and testing, while {@code ExoPlayerEngine} only performs the resulting action. Two properties
 * matter for correctness:
 *
 * <ul>
 *   <li><b>Bounded.</b> Retryable failures stop retrying after {@link #MAX_ATTEMPTS} so a dead
 *       endpoint can never spin the player forever.
 *   <li><b>Transient aware.</b> Connection failures and timeouts are retried instead of being
 *       reported as fatal, which is what a brief network hiccup used to look like.
 * </ul>
 */
public final class PlaybackRecoveryPolicy {

    /** Per-media-item retry budget for failures that can plausibly succeed on a second try. */
    public static final int MAX_ATTEMPTS = 2;

    private static final long RETRY_DELAY_BASE_MS = 500L;
    /**
     * Defensive ceiling only. With {@link #MAX_ATTEMPTS} == 2 the largest attempt index that can
     * request a delay is 1, so the effective backoff is 500 ms then 1000 ms; this cap (and the
     * index clamp in {@link #retryDelayMs(int)}) would only engage if the budget were raised.
     */
    private static final long RETRY_DELAY_MAX_MS = 3000L;

    public enum Action {
        /** The live edge was lost; seek to the default position and keep playing. */
        SEEK_DEFAULT,
        /** The codec failed; the manager switches decode mode and restarts. */
        SWITCH_DECODE,
        /** Container/manifest mismatch; restart with a different format hint. */
        RETRY_FORMAT,
        /** Transient transport failure; restart from the current position, same format. */
        RETRY_TRANSIENT,
        /** No safe automatic recovery is left. */
        FATAL
    }

    private PlaybackRecoveryPolicy() {
    }

    /**
     * @param errorCode a {@link PlaybackException} error code
     * @param attempt   how many automatic recoveries already ran for the current media item
     */
    public static Action decide(int errorCode, int attempt) {
        boolean budget = attempt < MAX_ATTEMPTS;
        return switch (errorCode) {
            case PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> Action.SEEK_DEFAULT;
            case PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                 PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
                 PlaybackException.ERROR_CODE_DECODING_FAILED,
                 PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> Action.SWITCH_DECODE;
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                 PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                 PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                 PlaybackException.ERROR_CODE_TIMEOUT -> budget ? Action.RETRY_TRANSIENT : Action.FATAL;
            case PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                 PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                 PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
                 PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                 PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> budget ? Action.RETRY_FORMAT : Action.FATAL;
            default -> Action.FATAL;
        };
    }

    /** Backoff before performing a retry, so a dead endpoint is not hammered. */
    public static long retryDelayMs(int attempt) {
        return Math.min(RETRY_DELAY_MAX_MS, RETRY_DELAY_BASE_MS << Math.max(0, Math.min(attempt, 8)));
    }
}
