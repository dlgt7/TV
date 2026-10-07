package com.fongmi.android.tv.source;

import com.fongmi.android.tv.bean.Vod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded candidates keyed by source AND item, never by the source-local vod ID alone. */
public final class PosterSourceResults {
    private static final int LIMIT = 120;
    private final Map<String, Candidate> candidates = new LinkedHashMap<>();
    private final Map<String, Boolean> rejected = new LinkedHashMap<>();
    private final SearchRelevance.Query query;
    private final String preferredSite;
    private final Map<String, Integer> sourceOrder = new LinkedHashMap<>();

    public PosterSourceResults(SearchRelevance.Query query, String preferredSite) {
        this(query, preferredSite, List.of());
    }

    public PosterSourceResults(SearchRelevance.Query query, String preferredSite, List<String> orderedSites) {
        for (String site : orderedSites) sourceOrder.putIfAbsent(site, sourceOrder.size());
        this.query = query;
        this.preferredSite = preferredSite == null ? "" : preferredSite;
    }

    public void add(List<Vod> items) {
        if (items == null) return;
        for (Vod item : items) {
            if (item == null || item.getId().isEmpty() || item.getSiteKey().isEmpty()
                    || item.isFolder() || item.isAction()) continue;
            String id = key(item);
            SearchRelevance.Match match = SearchRelevance.evaluate(item, query);
            if (!match.relevant()) {
                if (!candidates.containsKey(id) && rejected.size() < 2000) rejected.put(id, true);
                continue;
            }
            rejected.remove(id);
            Candidate value = new Candidate(item, match);
            Candidate old = candidates.get(id);
            if (old != null) {
                if (value.match.score() > old.match.score()) candidates.put(id, value);
            } else if (candidates.size() < LIMIT) candidates.put(id, value);
            else {
                Candidate weakest = candidates.values().stream().min(Comparator.comparingInt(it -> it.match.score())).orElse(null);
                if (weakest != null && match.score() > weakest.match.score()) {
                    candidates.remove(key(weakest.vod));
                    candidates.put(id, value);
                }
            }
        }
    }

    public List<Candidate> snapshot(boolean smart) {
        List<Candidate> result = new ArrayList<>(candidates.values());
        if (smart) result.sort(Comparator.<Candidate>comparingInt(it -> it.match.score()).reversed()
                .thenComparingInt(it -> preferredSite.equals(it.vod.getSiteKey()) ? 0 : 1));
        else result.sort(Comparator.comparingInt(it -> sourceOrder.getOrDefault(it.vod.getSiteKey(), Integer.MAX_VALUE)));
        return result;
    }

    public int hiddenCount() { return rejected.size(); }
    public static String key(Vod vod) { return vod.getSiteKey() + "\u0000" + vod.getId(); }
    public record Candidate(Vod vod, SearchRelevance.Match match) {}
}
