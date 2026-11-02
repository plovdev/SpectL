package com.plovdev.spectl;

import com.plovdev.spectl.audio.BeatDetector;
import org.plovdev.audioengine.AudioEngine;
import org.plovdev.audioengine.NativeAudioEngine;
import org.plovdev.audioengine.devices.AudioDeviceInfo;
import org.plovdev.audioengine.devices.AudioDeviceManager;
import org.plovdev.audioengine.devices.InputAudioDevice;
import org.plovdev.audioengine.devices.NativeInputAudioDevice;
import org.plovdev.audioengine.format.TrackFormat;
import org.plovdev.audioengine.format.factories.WavTrackFormatFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    static void main() {
        KeyLightClient.KEY_LIGHT.disable();
        BeatDetector detector = new BeatDetector();
        try (AudioEngine engine = new NativeAudioEngine()) {
            AudioDeviceManager manager = AudioDeviceManager.getInstance();
            AudioDeviceInfo inputDevice = manager.getDefaultInputAudioDevice(); // If user specified BlackHole, this device will be detecting automaticly

            TrackFormat format = WavTrackFormatFactory.wav32bitFloatStereo48kHz();
            try (InputAudioDevice input = new NativeInputAudioDevice(inputDevice)) {
                input.open(format);

                log.info("Starting read input for SpectL");
                while (!Thread.currentThread().isInterrupted()) {
                    ByteBuffer soundBuffer = ByteBuffer.allocateDirect(4096);
                    int read = input.read(soundBuffer);

                    double brightness = detector.process(soundBuffer);
                    if (brightness > 0) {
                        KeyLightClient.setBrightness(brightness);
                    }
                }
            } catch (Exception e) {
                log.error("AudioSND error: ", e);
            }
        } finally {
            KeyLightClient.KEY_LIGHT.disable();
        }
    }
}