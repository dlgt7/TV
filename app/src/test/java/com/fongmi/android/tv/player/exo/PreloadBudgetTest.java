package com.fongmi.android.tv.player.exo;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class PreloadBudgetTest {
    @Test public void currentAndNextCannotDownloadTogetherAndCompletionHandsOver() {
        PreloadBudget budget = ready();
        PreloadBudget.Lease current = budget.acquire(PreloadBudget.Owner.CURRENT, 0, () -> {});
        assertNotNull(current);
        assertNull(budget.acquire(PreloadBudget.Owner.NEXT, 1, () -> {}));
        budget.release(current);
        assertNotNull(budget.acquire(PreloadBudget.Owner.NEXT, 2, () -> {}));
    }

    @Test public void foregroundPressureCancelsBeforeAnyOtherTaskCanAcquire() {
        PreloadBudget budget = ready();
        AtomicInteger cancels = new AtomicInteger();
        budget.acquire(PreloadBudget.Owner.NEXT, 0, () -> {
            assertNull(budget.acquire(PreloadBudget.Owner.CURRENT, 1, () -> {}));
            cancels.incrementAndGet();
        });
        budget.update(false, 1);
        assertEquals(1, cancels.get());
        assertNull(budget.acquire(PreloadBudget.Owner.CURRENT, 2, () -> {}));
        budget.update(true, 3);
        assertNotNull(budget.acquire(PreloadBudget.Owner.CURRENT, 3, () -> {}));
    }

    @Test public void stalledCurrentTaskYieldsToWaitingNextEpisode() {
        PreloadBudget budget = ready();
        AtomicInteger cancels = new AtomicInteger();
        budget.acquire(PreloadBudget.Owner.CURRENT, 0, cancels::incrementAndGet);
        assertNull(budget.acquire(PreloadBudget.Owner.NEXT, 15_000, () -> {}));
        budget.update(true, PreloadBudget.MAX_TASK_MS);
        assertEquals(1, cancels.get());
        assertNull(budget.acquire(PreloadBudget.Owner.CURRENT, PreloadBudget.MAX_TASK_MS, () -> {}));
        assertNotNull(budget.acquire(PreloadBudget.Owner.NEXT, PreloadBudget.MAX_TASK_MS, () -> {}));
    }

    @Test public void lateCompletionFromOldSessionCannotReleaseNewLease() {
        PreloadBudget budget = ready();
        AtomicInteger cancels = new AtomicInteger();
        PreloadBudget.Lease old = budget.acquire(PreloadBudget.Owner.CURRENT, 0, cancels::incrementAndGet);
        budget.reset();
        assertEquals(1, cancels.get());
        budget.update(true, 1);
        assertNotNull(budget.acquire(PreloadBudget.Owner.NEXT, 1, () -> {}));
        budget.release(old);
        assertNull(budget.acquire(PreloadBudget.Owner.CURRENT, 2, () -> {}));
    }

    @Test public void disabledOrAbandonedWaiterCannotStarveCurrent() {
        PreloadBudget budget = ready();
        PreloadBudget.Lease current = budget.acquire(PreloadBudget.Owner.CURRENT, 0, () -> {});
        budget.acquire(PreloadBudget.Owner.NEXT, 1, () -> {});
        budget.cancelWaiting(PreloadBudget.Owner.NEXT);
        budget.release(current);
        current = budget.acquire(PreloadBudget.Owner.CURRENT, 2, () -> {});
        assertNotNull(current);
        budget.acquire(PreloadBudget.Owner.NEXT, 3, () -> {});
        budget.release(current);
        assertNotNull(budget.acquire(PreloadBudget.Owner.CURRENT, 15_000, () -> {}));
    }

    private PreloadBudget ready() {
        PreloadBudget budget = new PreloadBudget();
        budget.update(true, 0);
        return budget;
    }
}
