package com.fongmi.android.tv.bean;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.utils.TmdbEndpoint;

import java.util.Objects;

import okhttp3.HttpUrl;

public final class DiscoverQuery implements DiscoverListQuery {

    public static final String DEFAULT_TMDB_BASE = TmdbEndpoint.DEFAULT_API_BASE;

    public static final String SORT_POPULAR = "popular";
    public static final String SORT_RATING = "rating";
    public static final String SORT_LATEST = "latest";

    private final String mediaType;
    private final String genreId;
    private final String originCountry;
    private final String dateStart;
    private final String dateEnd;
    private final String sort;
    private final int page;

    public DiscoverQuery(String mediaType, String genreId, String originCountry, String dateStart, String dateEnd, String sort, int page) {
        this.mediaType = DiscoverMediaKey.TV.equals(mediaType) ? DiscoverMediaKey.TV : DiscoverMediaKey.MOVIE;
        this.genreId = value(genreId);
        this.originCountry = value(originCountry);
        this.dateStart = value(dateStart);
        this.dateEnd = value(dateEnd);
        this.sort = SORT_RATING.equals(sort) || SORT_LATEST.equals(sort) ? sort : SORT_POPULAR;
        this.page = Math.max(1, page);
    }

    public static DiscoverQuery defaults() {
        return new DiscoverQuery(DiscoverMediaKey.MOVIE, "", "", "", "", SORT_POPULAR, 1);
    }

    public String getMediaType() {
        return mediaType;
    }

    public String getGenreId() {
        return genreId;
    }

    public String getOriginCountry() {
        return originCountry;
    }

    public String getDateStart() {
        return dateStart;
    }

    public String getDateEnd() {
        return dateEnd;
    }

    public String getSort() {
        return sort;
    }

    public int getPage() {
        return page;
    }

    public HttpUrl buildUrl(String baseUrl, @Nullable String apiKey) {
        HttpUrl base = HttpUrl.parse(baseUrl + "discover/" + mediaType);
        if (base == null || apiKey == null || apiKey.trim().isEmpty()) return null;
        HttpUrl.Builder builder = base.newBuilder()
                .addQueryParameter("api_key", apiKey.trim())
                .addQueryParameter("language", "zh-CN")
                .addQueryParameter("include_adult", "false")
                .addQueryParameter("include_video", "false")
                .addQueryParameter("page", String.valueOf(page))
                .addQueryParameter("sort_by", sortParameter());
        if (!genreId.isEmpty()) builder.addQueryParameter("with_genres", genreId);
        if (!originCountry.isEmpty()) builder.addQueryParameter("with_origin_country", originCountry);
        String dateField = DiscoverMediaKey.MOVIE.equals(mediaType) ? "primary_release_date" : "first_air_date";
        if (!dateStart.isEmpty()) builder.addQueryParameter(dateField + ".gte", dateStart);
        if (!dateEnd.isEmpty()) builder.addQueryParameter(dateField + ".lte", dateEnd);
        if (SORT_RATING.equals(sort)) builder.addQueryParameter("vote_count.gte", "100");
        return builder.build();
    }

    @Nullable
    public HttpUrl buildUrl(@Nullable String apiKey) {
        return buildUrl(DEFAULT_TMDB_BASE, apiKey);
    }

    private String sortParameter() {
        if (SORT_RATING.equals(sort)) return "vote_average.desc";
        if (SORT_LATEST.equals(sort)) return DiscoverMediaKey.MOVIE.equals(mediaType) ? "primary_release_date.desc" : "first_air_date.desc";
        return "popularity.desc";
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }

    @Override
    public boolean equals(@Nullable Object object) {
        if (this == object) return true;
        if (!(object instanceof DiscoverQuery other)) return false;
        return page == other.page && mediaType.equals(other.mediaType) && genreId.equals(other.genreId)
                && originCountry.equals(other.originCountry) && dateStart.equals(other.dateStart)
                && dateEnd.equals(other.dateEnd) && sort.equals(other.sort);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mediaType, genreId, originCountry, dateStart, dateEnd, sort, page);
    }
}
