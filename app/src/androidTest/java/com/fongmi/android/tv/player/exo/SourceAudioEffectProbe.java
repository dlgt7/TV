package com.fongmi.android.tv.player.exo;

import android.os.Looper;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessingPipeline;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.AudioProcessorChain;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;

import com.fongmi.android.tv.player.audio.AudioDsp;
import com.fongmi.android.tv.player.audio.AudioEffectProcessor;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Read-only inspection of the production audio chain, serialized with actual PCM processing. */
@UnstableApi
public final class SourceAudioEffectProbe {
    private SourceAudioEffectProbe() {}

    /**
     * Requests a snapshot on the player's playback thread. Call on the main thread after audio
     * rendering starts; await the future with a timeout on the test thread, never on the main thread.
     * Missing renderers or unsupported reflection layouts fail the future instead of reporting that
     * effects are absent. The probe never feeds synthetic PCM or changes the processor state.
     */
    public static CompletableFuture<Snapshot> snapshot(ExoPlayer player) {
        if (Looper.myLooper() != Looper.getMainLooper()
                || Looper.myLooper() != player.getApplicationLooper()) {
            throw new IllegalStateException("Request audio snapshots on the player's main thread");
        }
        List<Renderer> renderers = new ArrayList<>();
        for (int i = 0; i < player.getRendererCount(); i++) {
            if (player.getRendererType(i) != C.TRACK_TYPE_AUDIO) continue;
            renderers.add(player.getRenderer(i));
            Renderer secondary = player.getSecondaryRenderer(i);
            if (secondary != null) renderers.add(secondary);
        }
        CompletableFuture<Snapshot> future = new CompletableFuture<>();
        try {
            player.createMessage((type, payload) -> {
                try {
                    future.complete(inspect(renderers));
                } catch (Throwable failure) {
                    future.completeExceptionally(failure);
                }
            }).setLooper(player.getPlaybackLooper()).send();
        } catch (RuntimeException failure) {
            future.completeExceptionally(failure);
        }
        return future;
    }

    /**
     * processed means an active production processor has nonzero DSP history since its last reset.
     * Silence can leave this false even when processing runs, so callers may resample during audio.
     */
    public record Snapshot(boolean installed, boolean active, boolean processed,
                           int enabledAudioRenderers, int inspectedSinks,
                           double power, double filterStateMagnitude) {}

    private static Snapshot inspect(List<Renderer> renderers) throws ReflectiveOperationException {
        Set<DefaultAudioSink> sinks = Collections.newSetFromMap(new IdentityHashMap<>());
        int enabled = 0;
        for (Renderer renderer : renderers) {
            if (renderer.getState() == Renderer.STATE_DISABLED) continue;
            enabled++;
            Object sink = read(renderer, "audioSink");
            if (!(sink instanceof AudioSink audioSink)) {
                throw new IllegalStateException("Enabled audio renderer has no inspectable AudioSink");
            }
            sinks.add(unwrap(audioSink));
        }
        if (enabled == 0 || sinks.isEmpty()) {
            throw new IllegalStateException("No enabled audio renderer; wait for source audio playback");
        }
        boolean installed = false;
        boolean active = false;
        double power = 0;
        double filterState = 0;
        for (DefaultAudioSink sink : sinks) {
            AudioProcessorChain chain = (AudioProcessorChain) read(sink, "audioProcessorChain");
            AudioProcessingPipeline pipeline = (AudioProcessingPipeline) read(sink, "audioProcessingPipeline");
            List<?> activeProcessors = pipeline == null ? Collections.emptyList()
                    : (List<?>) read(pipeline, "activeAudioProcessors");
            for (AudioProcessor processor : chain.getAudioProcessors()) {
                if (!(processor instanceof AudioEffectProcessor effect)) continue;
                installed = true;
                if (!activeProcessors.contains(effect) || !effect.isActive()) continue;
                active = true;
                AudioDsp dsp = (AudioDsp) read(effect, "dsp");
                if (dsp == null) continue;
                double observedPower = (Double) read(dsp, "power");
                if (!Double.isFinite(observedPower) || observedPower < 0) {
                    throw new IllegalStateException("Audio DSP has invalid power state");
                }
                power = Math.max(power, observedPower);
                filterState = Math.max(filterState, magnitude((double[][]) read(dsp, "z1")));
                filterState = Math.max(filterState, magnitude((double[][]) read(dsp, "z2")));
            }
        }
        return new Snapshot(installed, active, active && (power > 0 || filterState > 0),
                enabled, sinks.size(), power, filterState);
    }

    private static double magnitude(double[][] state) {
        double maximum = 0;
        for (double[] channel : state) {
            for (double value : channel) {
                if (!Double.isFinite(value)) throw new IllegalStateException("Audio DSP has invalid filter state");
                maximum = Math.max(maximum, Math.abs(value));
            }
        }
        return maximum;
    }

    private static DefaultAudioSink unwrap(AudioSink initial) throws IllegalAccessException {
        Set<AudioSink> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        AudioSink current = initial;
        while (visited.add(current)) {
            if (current instanceof DefaultAudioSink sink) return sink;
            AudioSink delegate = null;
            for (Class<?> owner = current.getClass(); owner != null; owner = owner.getSuperclass()) {
                for (Field field : owner.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                            || !AudioSink.class.isAssignableFrom(field.getType())) continue;
                    field.setAccessible(true);
                    AudioSink nested = (AudioSink) field.get(current);
                    if (nested == null || nested == current) continue;
                    if (delegate != null && delegate != nested) {
                        throw new IllegalStateException("Ambiguous audio sink wrapper");
                    }
                    delegate = nested;
                }
            }
            if (delegate == null) throw new IllegalStateException("Unsupported audio sink wrapper");
            current = delegate;
        }
        throw new IllegalStateException("Cyclic audio sink wrapper");
    }

    private static Object read(Object target, String name) throws ReflectiveOperationException {
        for (Class<?> owner = target.getClass(); owner != null; owner = owner.getSuperclass()) {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                // Both platform and extension audio renderers inherit their sink field.
            }
        }
        throw new NoSuchFieldException("Audio inspection field is unavailable: " + name);
    }
}
