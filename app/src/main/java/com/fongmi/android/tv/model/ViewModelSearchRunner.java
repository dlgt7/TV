package com.fongmi.android.tv.model;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.source.health.SourceHealthManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

final class ViewModelSearchRunner {
    private final BoundedSearchBatch<Site, Result> batch = new BoundedSearchBatch<>();
    private final AtomicLong epoch = new AtomicLong();

    void start(List<Site> sites, Function<Site, Callable<Result>> taskFactory, Consumer<Result> onResult) {
        final long current = epoch.incrementAndGet();
        final String scope = SourceHealthManager.currentScope();
        final Map<Site, SourceHealthManager.Attempt> attempts = new ConcurrentHashMap<>();
        batch.start(sites, site -> () -> {
            SourceHealthManager.Attempt attempt = SourceHealthManager.attempt(scope, site.getKey(), SourceHealthManager.Phase.SEARCH);
            attempts.put(site, attempt);
            attempt.start();
            try { return taskFactory.apply(site).call(); }
            finally { attempt.finish(); }
        }, new BoundedSearchBatch.Observer<>() {
            @Override public void success(Site site, Result result, long elapsedMs) {
                if (epoch.get() != current) return;
                SourceHealthManager.Attempt attempt = attempts.remove(site);
                if (attempt != null) attempt.complete(result, null);
                App.post(() -> { if (epoch.get() == current) onResult.accept(result); });
            }
            @Override public void failure(Site site, Throwable error, long elapsedMs) {
                SourceHealthManager.Attempt attempt = attempts.remove(site);
                if (epoch.get() == current && attempt != null) attempt.complete(null, error);
            }
        }, Constant.TIMEOUT_SEARCH);
    }

    void stop() { epoch.incrementAndGet(); batch.stop(); }
}
