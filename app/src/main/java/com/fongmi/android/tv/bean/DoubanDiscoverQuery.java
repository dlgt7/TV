package com.fongmi.android.tv.bean;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import okhttp3.HttpUrl;

/** Combines the same tags offered by the Douban source without approximating them as TMDB genres. */
public final class DoubanDiscoverQuery implements DiscoverListQuery {
    public static final String SHOW = "show";
    public static final int PAGE_SIZE = 20;
    private final String type, genre, region, start, end, sort, platform;
    private final int page;

    public DoubanDiscoverQuery(String type, String genre, String region, String start, String end, String sort, String platform, int page) {
        this.type = SHOW.equals(type) ? SHOW : DiscoverMediaKey.TV.equals(type) ? DiscoverMediaKey.TV : DiscoverMediaKey.MOVIE;
        this.genre = value(genre);
        this.region = value(region);
        this.start = value(start);
        this.end = value(end);
        this.sort = DiscoverQuery.SORT_RATING.equals(sort) ? "S" : DiscoverQuery.SORT_LATEST.equals(sort) ? "R" : "T";
        this.platform = DiscoverMediaKey.MOVIE.equals(this.type) ? "" : value(platform);
        this.page = Math.max(1, page);
    }

    public int getPage() { return page; }
    public String getMediaType() { return DiscoverMediaKey.MOVIE.equals(type) ? DiscoverMediaKey.MOVIE : DiscoverMediaKey.TV; }

    public HttpUrl buildUrl() {
        List<String> tags = new ArrayList<>();
        tags.add(SHOW.equals(type) ? "综艺" : DiscoverMediaKey.TV.equals(type) ? "电视剧" : "电影");
        for (String tag : List.of(genre, region, platform)) if (!tag.isEmpty()) tags.add(tag);
        HttpUrl.Builder builder = HttpUrl.get("https://movie.douban.com/j/new_search_subjects").newBuilder()
                .addQueryParameter("sort", sort)
                .addQueryParameter("range", "0,10")
                .addQueryParameter("tags", String.join(",", tags))
                .addQueryParameter("start", String.valueOf((long) (page - 1) * PAGE_SIZE));
        if (!start.isEmpty() || !end.isEmpty()) builder.addQueryParameter("year_range", year(start, "1") + "," + year(end, "9999"));
        return builder.build();
    }

    public static int totalPages(int page, int rawResultCount) {
        return rawResultCount >= PAGE_SIZE ? page + 1 : page;
    }

    private static String year(String date, String fallback) {
        return date.matches("\\d{4}-\\d{2}-\\d{2}") ? date.substring(0, 4) : fallback;
    }

    private static String value(String value) { return value == null ? "" : value.trim(); }

    @Override public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof DoubanDiscoverQuery other)) return false;
        return page == other.page && type.equals(other.type) && genre.equals(other.genre) && region.equals(other.region)
                && start.equals(other.start) && end.equals(other.end) && sort.equals(other.sort) && platform.equals(other.platform);
    }

    @Override public int hashCode() { return Objects.hash(type, genre, region, start, end, sort, platform, page); }
}
