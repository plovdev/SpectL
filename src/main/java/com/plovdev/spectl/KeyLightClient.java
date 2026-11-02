package com.plovdev.spectl;

import org.plovdev.keylight.KeyLight;
import org.plovdev.keylight.KeyLightImpl;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

public final class KeyLightClient {
    private static final AtomicReference<CompletableFuture<Void>> currentFade = new AtomicReference<>();

    private KeyLightClient() {
    }

    public static final KeyLight KEY_LIGHT = new KeyLightImpl();

    static {
        KEY_LIGHT.initKeyLight();
        Runtime.getRuntime().addShutdownHook(new Thread(KEY_LIGHT::close));
    }

    private static final AtomicReference<Long> currentAnimationId = new AtomicReference<>(0L);

    public static void setBrightness(double value) {
        long nextId = System.nanoTime();
        currentAnimationId.set(nextId);
        fadeToWithId(value, nextId);
    }

    private static void fadeToWithId(double from, long animId) {
        Duration duration = Duration.ofMillis(1000);
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration < 0");
        }

        CompletableFuture.runAsync(() -> {
            double to = 0.0;
            long totalNanos = duration.toNanos();
            long frameNanos = 32_000_000L;
            long start = System.nanoTime();

            KEY_LIGHT.brightness(from);
            while (true) {
                if (!currentAnimationId.get().equals(animId)) {
                    return;
                }

                long elapsed = System.nanoTime() - start;
                if (elapsed >= totalNanos) break;
                if (Thread.currentThread().isInterrupted()) return;
                double t = elapsed / (double) totalNanos;
                KEY_LIGHT.brightness(from + (to - from) * t);
                LockSupport.parkNanos(frameNanos);
            }

            if (currentAnimationId.get().equals(animId)) {
                KEY_LIGHT.brightness(to);
            }
        });
    }
}