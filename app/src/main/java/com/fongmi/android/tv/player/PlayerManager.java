package com.fongmi.android.tv.player;

import android.net.Uri;
import android.os.SystemClock;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaChapter;
import androidx.media3.common.MediaEdition;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.ui.danmaku.DanmakuConfig;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.impl.ParseCallback;
import com.fongmi.android.tv.player.effect.PlayerEffectManager;
import com.fongmi.android.tv.player.effect.audio.AudioEffectBands;
import com.fongmi.android.tv.player.engine.PlaybackCapabilities;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.player.engine.PlayerEngineFactory;
import com.fongmi.android.tv.player.media.PlaySpec;
import com.fongmi.android.tv.player.parse.ParseJob;
import com.fongmi.android.tv.player.subtitle.SecondarySubtitleStore;
import com.fongmi.android.tv.player.track.TrackUtil;
import com.fongmi.android.tv.setting.AudioSetting;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.common.net.HttpHeaders;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class PlayerManager implements ParseCallback {

    private final Runnable runnable;
    private final Callback callback;
    private final PlayerEffectManager effects;
    private PlayerEngine engine;
    private VideoSize videoSize;
    private ParseJob parseJob;
    private PendingPreload pendingPreload;
    private PlaySpec spec;
    private Player player;
    private Sub secondarySub;
    private TrackSelectionOverride embeddedSecondarySelection;
    private String secondarySubtitleMemoryKey = "";

    private DanmakuConfig danmakuConfig;
    private long pendingStartPositionMs;
    private boolean danmakuEnabled;
    private boolean initTrack;
    private boolean mpvFallbackUsed;
    private int retry;
    private int decode;
    private int preferredEngine;
    private long secondarySubtitleOffsetMs;
    private long playStartRealtimeMs;
    private boolean openReported;
    private boolean liveMode;

    public PlayerManager(Callback callback) {
        this.callback = callback;
        this.runnable = this::onPlayTimeout;
        this.preferredEngine = PlayerSetting.getVodEngine();
        // PlaybackService may be created only for MediaSession browsing. Do not reserve libmpv's
        // process-global instance until an actual VOD/live source selects it.
        this.decode = PlayerSetting.getDecode(false, PlayerSetting.ENGINE_EXO);
        this.engine = PlayerEngineFactory.createExo(decode, false, listener);
        this.effects = new PlayerEffectManager(() -> engine);
        this.player = engine.getPlayer();
        applyPersistedVolumeGain();
        this.pendingStartPositionMs = C.TIME_UNSET;
        this.danmakuConfig = DanmakuSetting.getConfig();
        this.danmakuEnabled = DanmakuSetting.isShow();
    }

    public static MediaMetadata buildMetadata(String title, String artist, String artUri) {
        Uri artwork = TextUtils.isEmpty(artUri) ? null : Uri.parse(artUri);
        return new MediaMetadata.Builder().setTitle(title).setArtist(artist).setArtworkUri(artwork).build();
    }

    public void release() {
        App.removeCallbacks(runnable);
        if (player != null) player.removeListener(listener);
        if (engine != null) engine.release();
        engine = null;
        player = null;
    }

    public Player getPlayer() {
        return player;
    }

    public Tracks getCurrentTracks() {
        return player.getCurrentTracks();
    }

    public List<MediaChapter> getCurrentMediaChapters() {
        return player.getCurrentMediaChapters();
    }

    public List<MediaEdition> getCurrentMediaEditions() {
        return player.getCurrentMediaEditions();
    }

    public MediaItem getCurrentMediaItem() {
        return player.getCurrentMediaItem();
    }

    public int getPlaybackState() {
        return player.getPlaybackState();
    }

    public boolean isPlaying() {
        return player.isPlaying();
    }

    public boolean isReleased() {
        return player == null;
    }

    public String getUrl() {
        return spec != null ? spec.getUrl() : null;
    }

    public String getKey() {
        return spec != null ? spec.getKey() : null;
    }

    public List<Danmaku> getDanmakus() {
        return spec != null ? spec.getDanmakus() : null;
    }

    private void setDanmakus(List<Danmaku> items) {
        if (spec != null) spec.setDanmaku(getSelectedDanmaku(items));
        notifyDanmakuSourceChanged();
    }

    private void notifyDanmakuSourceChanged() {
        callback.onDanmakuSourceChanged(getSelectedDanmakuUri());
    }

    public MediaMetadata getMetadata() {
        return spec != null ? spec.getMetadata() : null;
    }

    public void setMetadata(MediaMetadata data) {
        if (spec != null) spec.setMetadata(data);
        MediaItem current = player.getCurrentMediaItem();
        if (current != null) player.replaceMediaItem(player.getCurrentMediaItemIndex(), current.buildUpon().setMediaMetadata(data).build());
    }

    public Map<String, String> getHeaders() {
        return spec == null || spec.getHeaders() == null ? new HashMap<>() : spec.getHeaders();
    }

    public float getSpeed() {
        return player.getPlaybackParameters().speed;
    }

    public boolean isEmpty() {
        return spec == null || TextUtils.isEmpty(spec.getUrl());
    }

    public boolean canPreloadNext() {
        return PlayerSetting.getVodEngine() == PlayerSetting.ENGINE_EXO
                && engine != null
                && engine.getType() == PlayerEngine.Type.EXO;
    }

    public boolean preload(PlaySpec spec, long startPositionMs) {
        if (!canPreloadNext() || spec == null) return false;
        pendingPreload = new PendingPreload(spec.checkUa(), Math.max(0, startPositionMs));
        startPreloadIfReady();
        return true;
    }

    public void clearPreload() {
        pendingPreload = null;
        if (engine != null) engine.clearPreload();
    }

    public boolean isPortrait() {
        return getVideoHeight() > getVideoWidth();
    }

    public boolean isLandscape() {
        return getVideoWidth() > getVideoHeight();
    }

    public boolean isLive() {
        return engine.isLive();
    }

    public boolean isVod() {
        return engine.isVod();
    }

    public boolean haveTrack(int type) {
        return TrackUtil.count(getCurrentTracks(), type) > 0;
    }

    public boolean haveEdition() {
        return !getCurrentMediaEditions().isEmpty();
    }

    public boolean haveChapter() {
        return !getCurrentMediaChapters().isEmpty();
    }

    public boolean haveDanmaku() {
        return !getSelectedDanmaku().isEmpty();
    }

    public boolean canSetOpening(long position, long duration) {
        return position > 0 && duration > 0 && position <= Constant.getOpEdLimit(duration);
    }

    public boolean canSetEnding(long position, long duration) {
        return position > 0 && duration > 0 && duration - position <= Constant.getOpEdLimit(duration);
    }

    public int getVideoWidth() {
        return videoSize == null ? 0 : videoSize.width;
    }

    public int getVideoHeight() {
        return videoSize == null ? 0 : videoSize.height;
    }

    public long getPosition() {
        return player.getCurrentPosition();
    }

    public String getSizeText() {
        return (getVideoWidth() == 0 && getVideoHeight() == 0) ? "" : getVideoWidth() + " x " + getVideoHeight();
    }

    public String getSpeedText() {
        return String.format(Locale.getDefault(), "%.2f", getSpeed());
    }

    public String getDecodeText() {
        // EXO only toggles software vs hardware MediaCodec. "兼容硬解/性能硬解" are MPV
        // (mediacodec-copy / mediacodec_embed) labels and must not be shown on EXO.
        if (getEngine() == PlayerSetting.ENGINE_EXO) {
            return ResUtil.getString(decode == PlayerEngine.SOFT ? R.string.decode_soft : R.string.decode_hard);
        }
        return ResUtil.getStringArray(R.array.select_decode)[decode];
    }

    public int getEngine() {
        return engine.getType() == PlayerEngine.Type.MPV ? PlayerSetting.ENGINE_MPV : PlayerSetting.ENGINE_EXO;
    }

    public PlaybackCapabilities getCapabilities() {
        return PlaybackCapabilities.forEngine(engine.getType(), effects.canSetAudioSetting(), effects.canSetVideoSetting(), effects.supportsVideoSharpness());
    }

    public boolean canSetAudioSetting() {
        return effects.canSetAudioSetting();
    }

    public boolean canSetVideoSetting() {
        return effects.canSetVideoSetting();
    }

    public AudioEffectBands getAudioSettingBands() {
        return effects.getAudioSettingBands();
    }

    public int getAudioSettingError() {
        return effects.getAudioSettingError();
    }

    public int getVideoSettingError() {
        return effects.getVideoSettingError();
    }

    public void refreshAudioSetting() {
        if (engine != null && engine.requiresAudioEffectRebuild() && AudioSetting.hasEffect(8)) {
            long position = Math.max(0, getPosition());
            setPlayer(engine.rebuild());
            startCurrent(position);
            return;
        }
        effects.refreshAudioSetting();
    }

    public void refreshVideoSetting() {
        effects.refreshVideoSetting();
    }

    public void setEngine(int targetEngine) {
        setEngine(targetEngine, true);
    }

    /** Apply a config-provided engine without overwriting the user's persistent preference. */
    public void setEngine(int targetEngine, boolean persist) {
        targetEngine = Math.clamp(targetEngine, PlayerSetting.ENGINE_EXO, PlayerSetting.ENGINE_MPV);
        boolean samePreference = preferredEngine == targetEngine;
        preferredEngine = targetEngine;
        if (persist) {
            if (liveMode) PlayerSetting.putLiveEngine(targetEngine);
            else PlayerSetting.putVodEngine(targetEngine);
        }
        decode = PlayerSetting.getDecode(liveMode, targetEngine);
        callback.onDecodeChanged();
        if (isEmpty()) {
            // The service starts with a lightweight Exo instance and may have no PlaySpec yet.
            // Apply an explicit engine choice immediately instead of leaving UI and engine apart.
            if (getEngine() != targetEngine) replaceIdleEngine(targetEngine);
            return;
        }
        if (samePreference && getEngine() == targetEngine) return;
        startCurrent();
    }

    /** Select an engine for the source that is about to be started, without restarting the old item. */
    public void setEngineForNextPlayback(int targetEngine) {
        targetEngine = Math.clamp(targetEngine, PlayerSetting.ENGINE_EXO, PlayerSetting.ENGINE_MPV);
        preferredEngine = targetEngine;
        decode = PlayerSetting.getDecode(liveMode, targetEngine);
        callback.onDecodeChanged();
        if (isEmpty() && getEngine() != targetEngine) replaceIdleEngine(targetEngine);
    }

    private void replaceIdleEngine(int targetEngine) {
        PlayerEngine old = engine;
        if (player != null) player.removeListener(listener);
        engine = PlayerEngineFactory.create(decode, targetEngine, liveMode, listener);
        if (old != null) old.release();
        setPlayer(engine.getPlayer());
    }

    public void setLiveMode(boolean liveMode) {
        boolean modeChanged = this.liveMode != liveMode;
        this.liveMode = liveMode;
        int target = liveMode ? PlayerSetting.getLiveEngine() : PlayerSetting.getVodEngine();
        int targetDecode = PlayerSetting.getDecode(liveMode, target);
        preferredEngine = target;
        if (engine == null) {
            decode = targetDecode;
            return;
        }
        engine.setLiveMode(liveMode);
        boolean decodeChanged = decode != targetDecode;
        decode = targetDecode;
        // A scene switch changes Exo LoadControl and MPV demux/cache policy even when the engine
        // type and decode mode are unchanged, so the old instance cannot simply be reused.
        if (getEngine() == target && (modeChanged || decodeChanged)) {
            engine.setDecode(decode);
            setPlayer(engine.rebuild());
        }
    }

    public String getPositionTime(long delta) {
        return Util.timeMs(Math.clamp(getPosition() + delta, 0, Math.max(0, getDuration())));
    }

    public long getDuration() {
        return player.getDuration();
    }

    public String getDurationTime() {
        return Util.timeMs(Math.max(0, getDuration()));
    }

    public void setSub(Sub sub) {
        if (sub == null || sub.isEmpty()) return;
        if (spec != null) spec.setSub(sub);
        if (engine.addSubtitle(sub)) play();
        else startCurrent();
    }

    @Nullable
    public Sub getSecondarySub() {
        return secondarySub;
    }

    public void setSecondarySub(@Nullable Sub sub) {
        secondarySub = sub == null || sub.isEmpty() ? null : sub;
        secondarySubtitleOffsetMs = 0;
        if (secondarySub != null) setEmbeddedSecondarySubtitle(null);
        rememberSecondarySubtitle();
        callback.onSecondarySubtitleChanged(secondarySub);
    }

    public boolean hasSecondarySubtitle() {
        return secondarySub != null || embeddedSecondarySelection != null;
    }

    public boolean supportsEmbeddedSecondarySubtitle() {
        return engine != null && engine.supportsEmbeddedSecondarySubtitle();
    }

    public List<SecondaryTrackOption> getEmbeddedSecondarySubtitleOptions() {
        if (!supportsEmbeddedSecondarySubtitle()) return List.of();
        java.util.ArrayList<SecondaryTrackOption> result = new java.util.ArrayList<>();
        for (Tracks.Group group : getCurrentTracks().getGroups()) {
            if (group.getType() != C.TRACK_TYPE_TEXT) continue;
            for (int i = 0; i < group.length; i++) {
                if (group.isTrackSelected(i)) continue;
                TrackSelectionOverride selection = new TrackSelectionOverride(group.getMediaTrackGroup(), List.of(i));
                result.add(new SecondaryTrackOption(group.getTrackFormat(i), selection, selection.equals(embeddedSecondarySelection)));
            }
        }
        return result;
    }

    public void setEmbeddedSecondarySubtitle(@Nullable TrackSelectionOverride selection) {
        embeddedSecondarySelection = selection;
        if (selection != null && secondarySub != null) {
            secondarySub = null;
            secondarySubtitleOffsetMs = 0;
            SecondarySubtitleStore.put(secondarySubtitleMemoryKey, null, 0);
            callback.onSecondarySubtitleChanged(null);
        }
        if (engine != null) {
            engine.setEmbeddedSecondarySubtitle(selection);
            engine.setEmbeddedSecondarySubtitleOffset(selection == null ? 0 : secondarySubtitleOffsetMs);
        }
    }

    public void clearSecondarySub() {
        secondarySub = null;
        secondarySubtitleOffsetMs = 0;
        SecondarySubtitleStore.put(secondarySubtitleMemoryKey, null, 0);
        callback.onSecondarySubtitleChanged(null);
    }

    private void clearSecondarySubTransient() {
        secondarySub = null;
        secondarySubtitleOffsetMs = 0;
        secondarySubtitleMemoryKey = "";
        embeddedSecondarySelection = null;
        if (engine != null) engine.setEmbeddedSecondarySubtitle(null);
        callback.onSecondarySubtitleChanged(null);
    }

    private void restoreSecondarySubtitle(PlaySpec target) {
        secondarySubtitleMemoryKey = SecondarySubtitleStore.keyOf(target);
        SecondarySubtitleStore.Entry saved = SecondarySubtitleStore.get(secondarySubtitleMemoryKey);
        secondarySub = saved == null ? null : saved.sub();
        secondarySubtitleOffsetMs = saved == null ? 0 : saved.offsetMs();
        if (!isRestorableSecondarySubtitle(secondarySub)) {
            secondarySub = null;
            secondarySubtitleOffsetMs = 0;
            SecondarySubtitleStore.put(secondarySubtitleMemoryKey, null, 0);
        }
        callback.onSecondarySubtitleChanged(secondarySub);
    }

    private boolean isRestorableSecondarySubtitle(@Nullable Sub sub) {
        if (sub == null || sub.isEmpty()) return false;
        Uri uri = sub.getUri();
        if (uri == null) return false;
        if (!TextUtils.isEmpty(uri.getScheme()) && !"file".equalsIgnoreCase(uri.getScheme())) return true;
        String path = "file".equalsIgnoreCase(uri.getScheme()) ? uri.getPath() : uri.toString();
        return !TextUtils.isEmpty(path) && new java.io.File(path).isFile();
    }

    private void rememberSecondarySubtitle() {
        SecondarySubtitleStore.put(secondarySubtitleMemoryKey, secondarySub, secondarySubtitleOffsetMs);
    }

    public long getSecondarySubtitleOffsetMs() {
        return secondarySubtitleOffsetMs;
    }

    public void setSecondarySubtitleOffsetMs(long offsetMs) {
        secondarySubtitleOffsetMs = Math.clamp(offsetMs, -TimeUnit.MINUTES.toMillis(10), TimeUnit.MINUTES.toMillis(10));
        if (embeddedSecondarySelection != null && engine != null) engine.setEmbeddedSecondarySubtitleOffset(secondarySubtitleOffsetMs);
        rememberSecondarySubtitle();
    }

    public void setFormat(String format) {
        if (spec != null) spec.setFormat(format);
        startCurrent();
    }

    public void selectChapter(MediaChapter chapter) {
        player.selectChapter(chapter);
    }

    public void selectEdition(MediaEdition edition) {
        player.selectEdition(edition);
    }

    public void setDanmakuConfig(DanmakuConfig config) {
        danmakuConfig = config;
        callback.onDanmakuConfigChanged(danmakuConfig);
    }

    public void setDanmakuEnabled(boolean enabled) {
        if (danmakuEnabled == enabled) return;
        danmakuEnabled = enabled;
        callback.onDanmakuEnabledChanged(danmakuEnabled);
    }

    public void sendDanmaku(String text) {
        callback.onDanmakuSent(text);
    }

    public String setSpeed(float speed) {
        if (!player.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH)) return getSpeedText();
        player.setPlaybackParameters(player.getPlaybackParameters().withSpeed(speed));
        return getSpeedText();
    }

    public String addSpeed() {
        float speed = getSpeed();
        float step = speed >= 2 ? 1f : 0.25f;
        return setSpeed(speed >= 5 ? 0.25f : Math.min(speed + step, 5.0f));
    }

    public String addSpeed(float value) {
        return setSpeed(Math.clamp(getSpeed() + value, 0.25f, 5.0f));
    }

    public String subSpeed(float value) {
        return setSpeed(Math.clamp(getSpeed() - value, 0.25f, 5.0f));
    }

    public String toggleSpeed() {
        return setSpeed(getSpeed() == 1 ? PlayerSetting.getSpeed() : 1);
    }

    public void setTrack(List<Track> tracks) {
        if (!tracks.isEmpty()) TrackUtil.setTrackSelection(player, tracks);
    }

    public void setSubtitleStyle() {
        if (engine != null) engine.setSubtitleStyle();
        callback.onSubtitleStyleChanged();
        callback.onDanmakuConfigChanged(DanmakuSetting.getConfig());
    }

    /** 1.0 = normal; up to 2.0 for quiet sources. Applies to ExoPlayer and MPV. */
    public void setVolumeGain(float gain) {
        float value = Math.clamp(gain, 0f, 2f);
        PlayerSetting.putVolumeGain(value);
        if (engine != null) engine.setVolumeGain(value);
    }

    public float getVolumeGain() {
        return PlayerSetting.getVolumeGain();
    }

    private void applyPersistedVolumeGain() {
        if (engine != null) engine.setVolumeGain(PlayerSetting.getVolumeGain());
    }

    public void play() {
        player.play();
    }

    public void pause() {
        player.pause();
    }

    public void stop() {
        engine.stop();
        stopParse();
    }

    public void clearMediaItems() {
        player.clearMediaItems();
    }

    public boolean isRepeatOne() {
        return player.getRepeatMode() == Player.REPEAT_MODE_ONE;
    }

    public void setRepeatOne(boolean repeat) {
        player.setRepeatMode(repeat ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
    }

    public void replay(long positionMs) {
        if (positionMs == C.TIME_UNSET) player.seekToDefaultPosition();
        else player.seekTo(positionMs);
        player.play();
    }

    public void seekTo(long time) {
        player.seekTo(time);
    }

    public long getTextOffsetMs() {
        return player.isCommandAvailable(Player.COMMAND_GET_TEXT_OFFSET) ? player.getTextOffsetMs() : 0;
    }

    public void setTextOffsetMs(long offsetMs) {
        if (player.isCommandAvailable(Player.COMMAND_SET_TEXT_OFFSET)) player.setTextOffsetMs(offsetMs);
    }

    public long getAudioOffsetMs() {
        return player.isCommandAvailable(Player.COMMAND_GET_AUDIO_OFFSET) ? player.getAudioOffsetMs() : 0;
    }

    public void setAudioOffsetMs(long offsetMs) {
        if (player.isCommandAvailable(Player.COMMAND_SET_AUDIO_OFFSET)) player.setAudioOffsetMs(offsetMs);
    }

    public void reset() {
        App.removeCallbacks(runnable);
        retry = 0;
        mpvFallbackUsed = false;
    }

    public void clear() {
        spec = null;
        clearSecondarySubTransient();
    }

    public void resetTrack() {
        TrackUtil.reset(player);
    }

    /** User-initiated decode switch: persists the choice so it survives restarts. */
    public void toggleDecode() {
        applyDecodeToggle(true);
    }

    /**
     * Automatic fallback after a decode error. Deliberately does NOT persist.
     * <p>
     * A one-off, transient hardware-decoder failure (e.g. the codec was momentarily held by another
     * app, or a single stream is unsupported) must not be written back as a standing preference.
     * If it were, every later stream in this scene would be pinned to software decode until the user
     * manually toggled it back — a silent, permanent quality regression. Codec-level fallback within
     * a session is already handled by {@code DefaultRenderersFactory.setEnableDecoderFallback(true)}.
     */
    private void toggleDecodeTransient() {
        applyDecodeToggle(false);
    }

    private void applyDecodeToggle(boolean persist) {
        decode = engine.getType() == PlayerEngine.Type.MPV
                ? (decode + 1) % (PlayerEngine.HARD_PERFORMANCE + 1)
                : (isHard() ? PlayerEngine.SOFT : PlayerEngine.HARD);
        if (persist) PlayerSetting.putDecode(liveMode, getEngine(), decode);
        long position = Math.max(0, getPosition());
        boolean rebuild = engine.setDecode(decode);
        callback.onDecodeChanged();
        if (!rebuild) return;
        setPlayer(engine.rebuild());
        startCurrent(position);
    }

    private void handleDecodeError(PlaybackException e) {
        if (++retry > 1) {
            callback.onError(engine.getErrorMessage(e));
        } else {
            Notify.show(R.string.error_decode_fallback);
            toggleDecodeTransient();
        }
    }

    private boolean isHard() {
        return decode != PlayerEngine.SOFT;
    }

    private void onPlayTimeout() {
        stop();
        callback.onError(ResUtil.getString(R.string.error_play_timeout));
    }

    private void ensureEngine(PlaySpec spec) {
        if (PlayerEngineFactory.matches(engine, preferredEngine, spec)) return;
        PlayerEngine old = engine;
        player.removeListener(listener);
        engine = PlayerEngineFactory.create(decode, preferredEngine, liveMode, spec, listener);
        // Release MPV while PlayerView still owns its valid Surface. Publishing the replacement
        // first detaches that Surface and makes MPV's asynchronous shutdown rebuild a surface-less
        // VO, which can leave the next channel black.
        old.release();
        setPlayer(engine.getPlayer());
    }

    private boolean fallbackMpvToExo() {
        if (mpvFallbackUsed || engine.getType() != PlayerEngine.Type.MPV || spec == null) {
            return false;
        }
        mpvFallbackUsed = true;
        long position = Math.max(0, getPosition());
        PlayerEngine old = engine;
        player.removeListener(listener);
        decode = decode == PlayerEngine.SOFT ? PlayerEngine.SOFT : PlayerEngine.HARD;
        engine = PlayerEngineFactory.createExo(decode, liveMode, listener);
        // Keep the old render target attached until native MPV shutdown is complete.
        old.release();
        setPlayer(engine.getPlayer());
        engine.start(spec, position);
        setDanmakus(spec.getDanmakus());
        App.post(runnable, Constant.TIMEOUT_PLAY);
        callback.onPrepare();
        initTrack = false;
        return true;
    }

    private void setPlayer(Player player) {
        this.player = player;
        embeddedSecondarySelection = null;
        applyPersistedVolumeGain();
        effects.refreshVideoSetting();
        callback.onPlayerRebuild(player);
    }

    public void browse(PlaySpec spec, long startPositionMs) {
        reset();
        clear();
        stopParse();
        start(spec, Constant.TIMEOUT_PLAY, startPositionMs);
    }

    public void start(PlaySpec spec, long timeout) {
        start(spec, timeout, C.TIME_UNSET);
    }

    public void start(PlaySpec spec, long timeout, long startPositionMs) {
        if (this.spec != spec) clearSecondarySubTransient();
        this.spec = spec;
        restoreSecondarySubtitle(spec);
        setMediaItem(timeout, startPositionMs);
    }

    public void parse(String key, Result result, boolean useParse, MediaMetadata metadata) {
        parse(key, result, useParse, metadata, C.TIME_UNSET);
    }

    public void parse(String key, Result result, boolean useParse, MediaMetadata metadata, long startPositionMs) {
        stopParse();
        clearSecondarySubTransient();
        pendingStartPositionMs = startPositionMs;
        spec = PlaySpec.fromParse(result, key, metadata);
        restoreSecondarySubtitle(spec);
        parseJob = ParseJob.create(this).start(result, useParse);
    }

    private void stopParse() {
        if (parseJob != null) parseJob.stop();
        parseJob = null;
        pendingStartPositionMs = C.TIME_UNSET;
    }

    private void setMediaItem(long timeout, long startPositionMs) {
        if (spec == null || spec.getUrl() == null) return;
        ensureEngine(spec.checkUa());
        pendingPreload = null;
        openReported = false;
        playStartRealtimeMs = SystemClock.elapsedRealtime();
        engine.start(spec, startPositionMs);
        setDanmakus(spec.getDanmakus());
        App.post(runnable, timeout);
        callback.onPrepare();
        initTrack = false;
    }

    private void startCurrent() {
        startCurrent(getPosition());
    }

    private void startCurrent(long startPositionMs) {
        setMediaItem(Constant.TIMEOUT_PLAY, startPositionMs);
    }

    private void startPreloadIfReady() {
        PendingPreload preload = pendingPreload;
        if (preload == null || player.getPlaybackState() != Player.STATE_READY) return;
        pendingPreload = null;
        engine.preload(preload.spec(), preload.startPositionMs());
    }

    private Danmaku getSelectedDanmaku(List<Danmaku> items) {
        if (items == null || items.isEmpty()) return Danmaku.empty();
        return items.stream().filter(Danmaku::isSelected).findFirst().orElse(items.get(0));
    }

    public Danmaku getSelectedDanmaku() {
        return getSelectedDanmaku(getDanmakus());
    }

    public Uri getSelectedDanmakuUri() {
        return getSelectedDanmaku().getUri();
    }

    public void setDanmaku(Danmaku item) {
        if (spec == null) return;
        spec.setDanmaku(item);
        notifyDanmakuSourceChanged();
    }

    public void addDanmaku(Danmaku item) {
        if (spec != null) spec.addDanmaku(item);
    }

    @Override
    public void onParseSuccess(Map<String, String> headers, String url, String from) {
        if (!TextUtils.isEmpty(from)) Notify.show(ResUtil.getString(R.string.parse_from, from));
        if (headers != null) headers.remove(HttpHeaders.RANGE);
        if (spec != null) spec.setHeaders(headers);
        if (spec != null) spec.setUrl(url);
        startCurrent(pendingStartPositionMs);
        pendingStartPositionMs = C.TIME_UNSET;
    }

    @Override
    public void onParseError() {
        pendingStartPositionMs = C.TIME_UNSET;
        callback.onError(ResUtil.getString(R.string.error_play_parse));
    }

    public interface Callback {

        void onPrepare();

        void onTracksChanged();

        void onDecodeChanged();

        void onMediaOptionsChanged();

        void onError(String msg);

        void onPlayerRebuild(Player newPlayer);

        void onDanmakuSourceChanged(Uri uri);

        void onDanmakuConfigChanged(DanmakuConfig config);

        void onDanmakuEnabledChanged(boolean enabled);

        void onDanmakuSent(String text);

        void onSecondarySubtitleChanged(@Nullable Sub sub);

        void onSubtitleStyleChanged();
    }

    private final Player.Listener listener = new Player.Listener() {

        @Override
        public void onPlaybackStateChanged(int state) {
            if (state == Player.STATE_READY || state == Player.STATE_ENDED) App.removeCallbacks(runnable);
            if (state == Player.STATE_READY) {
                engine.resetErrorBudget();
                if (!openReported && liveMode && spec != null) {
                    openReported = true;
                    LineQualityStore.recordSuccess(spec.getUrl(), Math.max(0, SystemClock.elapsedRealtime() - playStartRealtimeMs));
                }
                startPreloadIfReady();
            }
        }

        @Override
        public void onVideoSizeChanged(@NonNull VideoSize size) {
            videoSize = size;
        }

        @Override
        public void onTracksChanged(@NonNull Tracks tracks) {
            if (tracks.isEmpty()) return;
            effects.refreshAudioSetting();
            effects.refreshVideoSetting();
            if (initTrack) return;
            setTrack(Track.find(getKey()));
            callback.onTracksChanged();
            initTrack = true;
        }

        @Override
        public void onMediaChaptersChanged(@NonNull List<MediaChapter> chapters) {
            callback.onMediaOptionsChanged();
        }

        @Override
        public void onMediaEditionsChanged(@NonNull List<MediaEdition> editions) {
            callback.onMediaOptionsChanged();
        }

        @Override
        public void onPlayerError(@NonNull PlaybackException e) {
            if (spec == null) return;
            PlayerEngine.ErrorAction action = engine.handleError(e);
            if (action != PlayerEngine.ErrorAction.RECOVERED) App.removeCallbacks(runnable);
            switch (action) {
                case DECODE -> handleDecodeError(e);
                case RECOVERED -> setDanmakus(spec.getDanmakus());
                case FATAL -> {
                    if (liveMode && e.errorCode >= 2000 && e.errorCode < 3000) LineQualityStore.recordFailure(spec.getUrl());
                    if (!fallbackMpvToExo()) callback.onError(engine.getErrorMessage(e));
                }
            }
        }
    };

    public record SecondaryTrackOption(androidx.media3.common.Format format, TrackSelectionOverride selection, boolean selected) {
    }

    private record PendingPreload(PlaySpec spec, long startPositionMs) {
    }

}
