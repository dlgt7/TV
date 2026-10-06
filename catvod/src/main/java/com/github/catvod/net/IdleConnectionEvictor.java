package com.github.catvod.net;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Serializes idle socket cleanup without blocking settings changes or accumulating work. */
final class IdleConnectionEvictor {

    private final ThreadPoolExecutor executor;
    private final Runnable eviction;

    IdleConnectionEvictor(Runnable... pools) {
        executor = new ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1), runnable -> {
                    Thread thread = new Thread(runnable, "OkHttp-idle-evictor");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.DiscardPolicy());
        eviction = () -> {
            for (Runnable pool : pools) {
                try {
                    pool.run();
                } catch (RuntimeException e) {
                    // A failed socket close must not prevent the other pool from being cleared.
                    e.printStackTrace();
                }
            }
        };
    }

    void request() {
        // Every task reads the current pools. One queued pass covers any further changes
        // while an earlier pass is blocked in socket.close(); extra requests are coalesced.
        executor.execute(eviction);
    }
}
