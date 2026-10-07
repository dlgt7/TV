package com.fongmi.android.tv.source;

import com.fongmi.android.tv.bean.Vod;
import com.github.catvod.utils.Trans;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Bounded candidates keyed by source AND item, never by the source-local vod ID alone. */
public final class PosterSourceResults {
    private static final int LIMIT = 120;
    private static final Pattern MOVIE = Pattern.compile("(?i)(?<![a-z])(?:movies?|films?)(?![a-z])|电影");
    private static final Pattern SERIES = Pattern.compile("(?i)(?<![a-z])(?:tv|series)(?![a-z])|电视剧|连续剧");
    private static final Comparator<Candidate> TITLE_EVIDENCE = Comparator.comparingInt(it -> it.match.score());
    private static final Comparator<Candidate> TYPED_EVIDENCE = TITLE_EVIDENCE.thenComparingInt(it -> it.match.confident() ? 1 : 0);
    private final Map<String, Candidate> candidates = new LinkedHashMap<>();
    private final Map<String, Boolean> rejected = new LinkedHashMap<>();
    private final Set<String> conflictingKinds = new HashSet<>();
    private final SearchRelevance.Query query;
    private final String preferredSite;
    private final Kind expectedKind;
    private final Comparator<Candidate> evidence;
    private final Map<String, Integer> sourceOrder = new LinkedHashMap<>();

    public PosterSourceResults(SearchRelevance.Query query, String preferredSite) {
        this(query, preferredSite, List.of());
    }

    public PosterSourceResults(SearchRelevance.Query query, String preferredSite, List<String> orderedSites) {
        this(query, preferredSite, orderedSites, "");
    }

    public PosterSourceResults(SearchRelevance.Query query, String preferredSite, List<String> orderedSites, String expectedType) {
        for (String site : orderedSites) sourceOrder.putIfAbsent(site, sourceOrder.size());
        this.query = query;
        this.preferredSite = preferredSite == null ? "" : preferredSite;
        this.expectedKind = kind(expectedType);
        this.evidence = expectedKind == Kind.UNKNOWN ? TITLE_EVIDENCE : TYPED_EVIDENCE;
    }

    public void add(List<Vod> items) {
        if (items == null) return;
        for (Vod item : items) {
            if (item == null || item.getId().isEmpty() || item.getSiteKey().isEmpty()
                    || item.isFolder() || item.isAction()) continue;
            String id = key(item);
            SearchRelevance.Match match = SearchRelevance.evaluate(item, query);
            if (expectedKind != Kind.UNKNOWN) {
                // A same-name film and series are different works. Titles, remarks and generic anime
                // categories are not evidence of kind; unknown categories remain manual candidates.
                Kind actualKind = kind(item.getTypeName());
                if (actualKind != Kind.UNKNOWN && actualKind != expectedKind) {
                    if (conflictingKinds.size() < 2000) conflictingKinds.add(id);
                    Candidate previous = candidates.get(id);
                    if (previous != null && kind(previous.vod.getTypeName()) == Kind.UNKNOWN) candidates.remove(id);
                    match = new SearchRelevance.Match(0, false, false, false);
                }
                else if (actualKind == Kind.UNKNOWN) {
                    match = conflictingKinds.contains(id) ? new SearchRelevance.Match(0, false, false, false)
                            : new SearchRelevance.Match(match.score(), match.relevant(), match.strict(), false);
                } else conflictingKinds.remove(id);
            }
            if (!match.relevant()) {
                if (!candidates.containsKey(id) && rejected.size() < 2000) rejected.put(id, true);
                continue;
            }
            rejected.remove(id);
            Candidate value = new Candidate(item, match);
            Candidate old = candidates.get(id);
            if (old != null) {
                if (evidence.compare(value, old) > 0) candidates.put(id, value);
            } else if (candidates.size() < LIMIT) candidates.put(id, value);
            else {
                Candidate weakest = candidates.values().stream().min(evidence).orElse(null);
                if (weakest != null && evidence.compare(value, weakest) > 0) {
                    candidates.remove(key(weakest.vod));
                    candidates.put(id, value);
                }
            }
        }
    }

    public List<Candidate> snapshot(boolean smart) {
        List<Candidate> result = new ArrayList<>(candidates.values());
        if (smart) result.sort(evidence.reversed()
                .thenComparingInt(it -> preferredSite.equals(it.vod.getSiteKey()) ? 0 : 1));
        else result.sort(Comparator.comparingInt(it -> sourceOrder.getOrDefault(it.vod.getSiteKey(), Integer.MAX_VALUE)));
        return result;
    }

    public int hiddenCount() { return rejected.size(); }

    private static Kind kind(String value) {
        String text = Trans.t2s(false, value == null ? "" : value);
        boolean movie = MOVIE.matcher(text).find();
        boolean series = SERIES.matcher(text).find();
        if (movie == series) return Kind.UNKNOWN;
        return movie ? Kind.MOVIE : Kind.SERIES;
    }

    private enum Kind { UNKNOWN, MOVIE, SERIES }

    public static String key(Vod vod) { return vod.getSiteKey() + "\u0000" + vod.getId(); }
    public record Candidate(Vod vod, SearchRelevance.Match match) {}
}
