package com.fongmi.android.tv.utils;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ImageRetryPolicyTest {

    @Test
    public void shouldRetrySameImageAfterTemporaryFailureExpires() {
        AtomicLong clock = new AtomicLong(1_000);
        ImageRetryPolicy policy = new ImageRetryPolicy(256, 30_000, clock::get);

        assertTrue(policy.canLoad("poster"));
        policy.onFailure("poster");
        assertFalse(policy.canLoad("poster"));
        clock.addAndGet(29_999);
        assertFalse(policy.canLoad("poster"));
        clock.incrementAndGet();
        assertTrue(policy.canLoad("poster"));
        policy.onSuccess("poster");
        assertTrue(policy.canLoad("poster"));
    }

    @Test
    public void shouldNotExtendCooldownWhenRebindingFailedImage() {
        AtomicLong clock = new AtomicLong();
        ImageRetryPolicy policy = new ImageRetryPolicy(256, 30_000, clock::get);
        policy.onFailure("poster");

        for (int i = 0; i < 5; i++) {
            clock.addAndGet(5_000);
            assertFalse(policy.canLoad("poster"));
        }
        clock.addAndGet(5_000);
        assertTrue(policy.canLoad("poster"));
    }

    @Test
    public void shouldClearCooldownWhenAnAlreadyRunningRequestSucceeds() {
        ImageRetryPolicy policy = new ImageRetryPolicy(256, 30_000, () -> 0);
        policy.onFailure("poster");
        assertFalse(policy.canLoad("poster"));

        policy.onSuccess("poster");

        assertTrue(policy.canLoad("poster"));
    }

    @Test
    public void shouldEvictOldestFailureAtCapacity() {
        ImageRetryPolicy policy = new ImageRetryPolicy(2, 30_000, () -> 0);
        policy.onFailure("first");
        policy.onFailure("second");
        policy.onFailure("third");

        assertTrue(policy.canLoad("first"));
        assertFalse(policy.canLoad("second"));
        assertFalse(policy.canLoad("third"));
    }

    @Test
    public void shouldRetainMostRecentlyFailedImageWhenEvicting() {
        ImageRetryPolicy policy = new ImageRetryPolicy(2, 30_000, () -> 0);
        policy.onFailure("first");
        policy.onFailure("second");
        policy.onFailure("first");
        policy.onFailure("third");

        assertFalse(policy.canLoad("first"));
        assertTrue(policy.canLoad("second"));
        assertFalse(policy.canLoad("third"));
    }

    @Test
    public void shouldRestartCooldownWhenRetryFailsAgain() {
        AtomicLong clock = new AtomicLong();
        ImageRetryPolicy policy = new ImageRetryPolicy(256, 30_000, clock::get);
        policy.onFailure("poster");
        clock.addAndGet(30_000);
        assertTrue(policy.canLoad("poster"));

        policy.onFailure("poster");
        clock.addAndGet(29_999);
        assertFalse(policy.canLoad("poster"));
        clock.incrementAndGet();
        assertTrue(policy.canLoad("poster"));
    }

    @Test
    public void shouldForgetFailuresWhenFeaturedItemsChange() {
        ImageRetryPolicy policy = new ImageRetryPolicy(256, 30_000, () -> 0);
        policy.onFailure("first");
        policy.onFailure("second");

        policy.clear();

        assertTrue(policy.canLoad("first"));
        assertTrue(policy.canLoad("second"));
    }
}
