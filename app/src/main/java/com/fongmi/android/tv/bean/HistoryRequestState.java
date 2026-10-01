package com.fongmi.android.tv.bean;

/** Main-thread state for history refreshes; cancelled queries may still finish. */
public final class HistoryRequestState {

    private long generation;
    private boolean renewPending;
    private boolean closed;

    public Request begin(int cid, boolean renew) {
        renewPending |= renew;
        return new Request(++generation, cid, renewPending);
    }

    public boolean isCurrent(Request request, int cid) {
        return !closed && request.generation() == generation && request.cid() == cid;
    }

    public void applied(Request request, int cid) {
        if (isCurrent(request, cid)) renewPending = false;
    }

    public void invalidate() {
        generation++;
    }

    public void close() {
        closed = true;
        invalidate();
    }

    public record Request(long generation, int cid, boolean renew) {
    }
}
