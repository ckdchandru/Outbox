package com.ob.support;

import com.ob.worker.EventProcessor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

public class TestWorkers {

    // runs until done
    public static void runUntil(EventProcessor processor, int threads, BooleanSupplier done, long timeoutMs)
            throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        AtomicBoolean stop = new AtomicBoolean(false);

        for (int i = 0; i < threads; i++) {
            String name = "test-worker-" + i;
            executor.submit(() -> {
                while (!stop.get()) {
                    try {
                        processor.processOne(name);
                    } catch (Exception e) {
                        System.err.println(name + " failed: " + e.getMessage());
                    }
                }
            });
        }

        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!done.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }

        stop.set(true);
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
    }
}
