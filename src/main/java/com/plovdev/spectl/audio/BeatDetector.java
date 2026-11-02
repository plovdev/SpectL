package com.plovdev.spectl.audio;

import edu.emory.mathcs.jtransforms.fft.FloatFFT_1D;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class BeatDetector {
    private static final double MULTIPLIER = 2;
    private static final int FFT_SIZE = 512;
    private static final int CHANNELS = 2;
    private static final int BYTES_PER_SAMPLE = Float.BYTES;

    private static final float SENSITIVITY = 1.7f;
    private static final float RELEASE = 0.90f;
    private static final float HISTORY_ALPHA = 0.08f;

    private final FloatFFT_1D fft = new FloatFFT_1D(FFT_SIZE);
    private final float[] samples = new float[FFT_SIZE];
    private final float[] previousMagnitude = new float[FFT_SIZE / 2];
    private final float[] history = new float[FFT_SIZE / 2];

    private float envelope;
    private float averageFlux;
    private float averageEnergy;

    /**
     * @param buffer interleaved stereo Float32 PCM, 48 kHz
     * @return beat intensity in range [0, 1]
     */
    public double process(@NotNull ByteBuffer buffer) {
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        int frames = Math.min(buffer.remaining() / (CHANNELS * BYTES_PER_SAMPLE), FFT_SIZE);

        if (frames < 256) {
            return envelope;
        }

        int start = buffer.position();

        // Convert stereo to mono.
        for (int i = 0; i < FFT_SIZE; i++) {
            if (i < frames) {
                float left = buffer.getFloat(
                        start + i * CHANNELS * BYTES_PER_SAMPLE
                );
                float right = buffer.getFloat(
                        start + (i * CHANNELS + 1) * BYTES_PER_SAMPLE
                );

                samples[i] = (left + right) * 0.5f;
            } else {
                samples[i] = 0;
            }
        }

        // Hann window reduces spectral leakage.
        for (int i = 0; i < FFT_SIZE; i++) {
            float window = 0.5f - 0.5f *
                    (float) Math.cos(2.0 * Math.PI * i / (FFT_SIZE - 1));

            samples[i] *= window;
        }

        fft.realForward(samples);

        float flux = 0;
        float bassFlux = 0;
        float totalEnergy = 0;

        // At 48 kHz, each FFT bin is 46.875 Hz.
        // Bins 0..4 roughly cover bass frequencies below 235 Hz.
        for (int bin = 1; bin < FFT_SIZE / 2; bin++) {
            float re;
            float im;

            re = samples[2 * bin];
            im = samples[2 * bin + 1];

            float magnitude = (float) Math.sqrt(re * re + im * im);
            float increase = Math.max(0, magnitude - previousMagnitude[bin]);

            flux += increase;
            if (bin <= 4) {
                bassFlux += increase;
            }

            totalEnergy += magnitude;
            previousMagnitude[bin] = magnitude;
        }

        // Ignore near-silence.
        float energy = totalEnergy / ((float) FFT_SIZE / 2);

        averageEnergy += HISTORY_ALPHA * (energy - averageEnergy);
        averageFlux += HISTORY_ALPHA * (flux - averageFlux);

        float raw = getRaw(flux, bassFlux, energy);
        if (raw > envelope) {
            envelope += (raw - envelope) * 0.85f;
        } else {
            envelope *= RELEASE;
        }

        return Math.min(1, Math.max(0, envelope)) * MULTIPLIER;
    }

    private float getRaw(float flux, float bassFlux, float energy) {
        float threshold = Math.max(averageFlux * SENSITIVITY, 1e-6f);
        float strength = Math.max(0, flux - threshold);

        // Bass transients contribute extra weight to kick detection.
        float bassThreshold = Math.max(averageFlux * 0.15f, 1e-6f);
        float bassStrength = Math.max(0, bassFlux - bassThreshold);

        float raw = (strength + bassStrength * 0.8f) / (averageFlux * 4.0f + 1e-6f);

        if (energy < averageEnergy * 0.15f) {
            raw = 0;
        }

        raw = Math.min(1, Math.max(0, raw));
        return raw;
    }
}