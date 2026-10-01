package com.fongmi.android.tv.bean;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HistoryRequestStateTest {

    @Test
    public void shouldRejectOlderQueriesThatFinishAfterTheLatestRefresh() {
        HistoryRequestState state = new HistoryRequestState();
        HistoryRequestState.Request first = state.begin(1, false);
        HistoryRequestState.Request latest = state.begin(1, false);

        assertTrue(state.isCurrent(latest, 1));
        assertFalse(state.isCurrent(first, 1));
    }

    @Test
    public void shouldRejectResultsFromThePreviousConfigEvenBeforeItsRefreshStarts() {
        HistoryRequestState state = new HistoryRequestState();
        HistoryRequestState.Request request = state.begin(1, false);

        assertFalse(state.isCurrent(request, 2));
    }

    @Test
    public void shouldNotRestoreDeletedHistoryFromAnInFlightQuery() {
        HistoryRequestState state = new HistoryRequestState();
        HistoryRequestState.Request beforeDelete = state.begin(1, false);
        state.invalidate();

        assertFalse(state.isCurrent(beforeDelete, 1));
        assertTrue(state.isCurrent(state.begin(1, false), 1));
    }

    @Test
    public void shouldIgnoreQueuedCallbacksAndNewRequestsAfterDestroy() {
        HistoryRequestState state = new HistoryRequestState();
        HistoryRequestState.Request request = state.begin(1, false);
        state.close();

        assertFalse(state.isCurrent(request, 1));
        assertFalse(state.isCurrent(state.begin(1, false), 1));
    }

    @Test
    public void shouldCarrySizeRenewalAcrossSupersededRefreshesUntilApplied() {
        HistoryRequestState state = new HistoryRequestState();
        HistoryRequestState.Request sizeChange = state.begin(1, true);
        HistoryRequestState.Request refresh = state.begin(1, false);
        state.applied(sizeChange, 1);
        HistoryRequestState.Request latest = state.begin(1, false);

        assertTrue(refresh.renew());
        assertTrue(latest.renew());
        state.applied(latest, 1);
        assertFalse(state.begin(1, false).renew());
    }

    @Test
    public void shouldKeepSizeRenewalWhenDeletionOrConfigChangeInvalidatesItsQuery() {
        HistoryRequestState state = new HistoryRequestState();
        HistoryRequestState.Request request = state.begin(1, true);
        state.invalidate();
        state.applied(request, 1);

        assertTrue(state.begin(2, false).renew());
    }
}
