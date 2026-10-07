package com.fongmi.android.tv.player.exo;

/** One speculative network task per player. Accessed on the player's application thread. */
final class PreloadBudget {
    enum Owner { CURRENT, NEXT }

    static final long MAX_TASK_MS = 20_000;
    private static final long WAITING_TIMEOUT_MS = 10_000;

    static final class Lease {
        private final Owner owner;
        private final long deadlineMs;
        private final Runnable cancel;

        private Lease(Owner owner, long deadlineMs, Runnable cancel) {
            this.owner = owner;
            this.deadlineMs = deadlineMs;
            this.cancel = cancel;
        }
    }

    private Lease active;
    private Owner waiting;
    private long waitingUntilMs;
    private boolean allowed;
    private boolean revoking;

    void update(boolean allowed, long nowMs) {
        this.allowed = allowed;
        if (waiting != null && nowMs >= waitingUntilMs) waiting = null;
        if (!revoking && active != null && (!allowed || nowMs >= active.deadlineMs)) revoke();
    }

    Lease acquire(Owner owner, long nowMs, Runnable cancel) {
        update(allowed, nowMs);
        if (!allowed || revoking) return null;
        if (active != null) {
            if (active.owner != owner) {
                waiting = owner;
                waitingUntilMs = nowMs + WAITING_TIMEOUT_MS;
            }
            return null;
        }
        // A task that timed out cannot immediately reclaim the connection ahead of its peer.
        if (waiting != null && waiting != owner) return null;
        waiting = null;
        return active = new Lease(owner, nowMs + MAX_TASK_MS, cancel);
    }

    void release(Lease lease) {
        // A late callback from a previous helper/session must not release the new task's slot.
        if (lease != null && active == lease) active = null;
    }

    void cancelWaiting(Owner owner) {
        if (waiting == owner) waiting = null;
    }

    void reset() {
        allowed = false;
        waiting = null;
        if (active != null) revoke();
    }

    private void revoke() {
        Lease previous = active;
        revoking = true;
        try {
            previous.cancel.run();
        } finally {
            if (active == previous) active = null;
            revoking = false;
        }
    }
}
