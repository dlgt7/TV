package com.fongmi.android.tv.player.audio;

import org.junit.Test;
import static org.junit.Assert.*;

public class AudioDspTest {
    @Test public void flatSettingsPreserveQuietPcm() {
        AudioDsp dsp = new AudioDsp(48000, 2, new float[5], 0, false);
        float[] frame = { .125f, -.25f };
        dsp.process(frame);
        assertArrayEquals(new float[]{.125f, -.25f}, frame, .000001f);
    }
    @Test public void dialogueBoostOnlyTouchesCenterChannel() {
        AudioDsp dsp = new AudioDsp(48000, 6, new float[5], 6, false);
        float[] frame = { .1f, .1f, .1f, .1f, .1f, .1f };
        dsp.process(frame);
        for (int c = 0; c < 6; c++) assertEquals(c == 2 ? .199526f : .1f, frame[c], .00001f);
    }
    @Test public void eqHasExpectedGainAtCenterFrequency() {
        double flat = rms(new float[5]);
        double boosted = rms(new float[]{0,0,6,0,0});
        assertEquals(6, 20 * Math.log10(boosted / flat), .05);
    }
    private double rms(float[] bands) {
        AudioDsp dsp = new AudioDsp(48000, 1, bands, 0, false);
        double power = 0;
        float[] frame = new float[1];
        for (int i = 0; i < 96000; i++) {
            frame[0] = (float) (.05 * Math.sin(2 * Math.PI * 1000 * i / 48000));
            dsp.process(frame);
            if (i >= 48000) power += frame[0] * frame[0];
        }
        return Math.sqrt(power / 48000);
    }
    @Test public void limiterKeepsLoudAndNonFiniteInputBounded() {
        AudioDsp dsp = new AudioDsp(48000, 2, new float[]{12,12,12,12,12}, 12, true);
        float[] frame = new float[2];
        for (int i = 0; i < 48000; i++) {
            frame[0] = i == 0 ? Float.NaN : 4;
            frame[1] = i == 0 ? Float.POSITIVE_INFINITY : -4;
            dsp.process(frame);
            for (float value : frame) { assertTrue(Float.isFinite(value)); assertTrue(Math.abs(value) <= .970001); }
        }
    }
    @Test public void resetRemovesPreviousFilterAndLoudnessHistory() {
        AudioDsp dsp = new AudioDsp(48000, 2, new float[]{4,0,0,0,0}, 0, true);
        for (int i=0; i<100; i++) dsp.process(new float[]{1, -1});
        dsp.reset();
        float[] zero = {0,0}; dsp.process(zero);
        assertArrayEquals(new float[]{0,0}, zero, 0);
    }
    @Test public void invalidPreferencesDoNotPoisonOutput() {
        AudioDsp dsp = new AudioDsp(48000, 3, new float[]{Float.NaN,0,0,0,Float.POSITIVE_INFINITY}, Float.NaN, false);
        float[] frame = {.1f,.1f,.1f}; dsp.process(frame);
        assertArrayEquals(new float[]{.1f,.1f,.1f}, frame, .000001f);
    }
    @Test public void normalizerDoesNotAmplifySilence() {
        AudioDsp dsp = new AudioDsp(48000, 1, new float[5], 0, true);
        float[] frame = {0};
        for (int i=0;i<96000;i++) dsp.process(frame);
        assertEquals(0, frame[0], 0);
    }
}
