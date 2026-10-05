package com.fongmi.android.tv.player.audio;

import androidx.media3.common.C;
import androidx.media3.common.audio.BaseAudioProcessor;

import com.fongmi.android.tv.setting.AudioEffectSetting;

import java.nio.ByteBuffer;

public final class AudioEffectProcessor extends BaseAudioProcessor {
    private final float[] bands = AudioEffectSetting.bands();
    private final float center = AudioEffectSetting.centerDb();
    private final boolean normalize = AudioEffectSetting.normalize();
    private AudioDsp dsp;
    private float[] frame;

    @Override protected AudioFormat onConfigure(AudioFormat input) {
        if (input.encoding != C.ENCODING_PCM_16BIT && input.encoding != C.ENCODING_PCM_FLOAT) return AudioFormat.NOT_SET;
        dsp = new AudioDsp(input.sampleRate, input.channelCount, bands, center, normalize);
        frame = new float[input.channelCount];
        return input;
    }

    @Override public void queueInput(ByteBuffer input) {
        if (!input.hasRemaining()) return;
        if (input.remaining() % inputAudioFormat.bytesPerFrame != 0) throw new IllegalArgumentException("Incomplete PCM frame");
        ByteBuffer output = replaceOutputBuffer(input.remaining());
        boolean floating = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT;
        while (input.hasRemaining()) {
            for (int i = 0; i < frame.length; i++) frame[i] = floating ? input.getFloat() : input.getShort() / 32768f;
            dsp.process(frame);
            for (float value : frame) {
                if (floating) output.putFloat(value);
                else output.putShort((short) Math.round(Math.clamp(value, -1f, 1f) * 32767));
            }
        }
        output.flip();
    }

    @Override protected void onFlush(StreamMetadata metadata) { if (dsp != null) dsp.reset(); }
    @Override protected void onReset() { dsp = null; frame = null; }
}
