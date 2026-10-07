package com.fongmi.android.tv.utils;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fongmi.android.tv.App;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.Response;

public final class TmdbLogoHelper {

    private static final String DEFAULT_LOGO_SIZE = "w500";
    private static final String DEFAULT_POSTER_SIZE = "original";
    private static final String INCLUDE_IMAGE_LANGUAGE = "zh,en,null";
    private static final Pattern YEAR = Pattern.compile("(?:19|20)\\d{2}");

    private TmdbLogoHelper() {
    }

    public static void findLogo(@Nullable String apiKey, @Nullable String title, @Nullable String year, @Nullable String typeName, @NonNull LogoCallback callback) {
        findLogo(apiKey, title, year, typeName, DEFAULT_LOGO_SIZE, callback);
    }

    public static void findLogo(@Nullable String apiKey, @Nullable String title, @Nullable String year, @Nullable String typeName, @Nullable String imageSize, @NonNull LogoCallback callback) {
        String safeApiKey = normalize(apiKey);
        String safeTitle = normalize(title);
        String safeYear = normalizeYear(year);
        String safeImageSize = normalizeImageSize(imageSize);
        if (TextUtils.isEmpty(safeTitle)) {
            post(callback::onNotFound);
            return;
        }
        MediaType firstType = guessMediaType(typeName);
        MediaType secondType = firstType == MediaType.TV ? MediaType.MOVIE : MediaType.TV;
        search(safeApiKey, firstType, safeTitle, safeYear, new SearchCallback() {
            @Override
            public void onFound(@Nullable SearchResult result) {
                if (result != null) {
                    fetchLogo(safeApiKey, firstType, result.id, safeImageSize, callback);
                } else {
                    searchAlternativeLogo(safeApiKey, secondType, safeTitle, safeYear, safeImageSize, callback);
                }
            }

            @Override
            public void onError(@NonNull Exception error) {
                post(() -> callback.onError(error));
            }
        });
    }

    public static void findPoster(@Nullable String apiKey, @Nullable String title, @Nullable String year, @Nullable String typeName, @NonNull ImageCallback callback) {
        findPoster(apiKey, title, year, typeName, DEFAULT_POSTER_SIZE, callback);
    }

    public static void findPoster(@Nullable String apiKey, @Nullable String title, @Nullable String year, @Nullable String typeName, @Nullable String imageSize, @NonNull ImageCallback callback) {
        String safeApiKey = normalize(apiKey);
        String safeTitle = normalize(title);
        String safeYear = normalizeYear(year);
        String safeImageSize = normalizeImageSize(imageSize);
        if (TextUtils.isEmpty(safeTitle)) {
            post(callback::onNotFound);
            return;
        }
        MediaType firstType = guessMediaType(typeName);
        MediaType secondType = firstType == MediaType.TV ? MediaType.MOVIE : MediaType.TV;
        search(safeApiKey, firstType, safeTitle, safeYear, new SearchCallback() {
            @Override
            public void onFound(@Nullable SearchResult result) {
                if (dispatchPosterResult(result, safeImageSize, callback)) return;
                searchAlternativePoster(safeApiKey, secondType, safeTitle, safeYear, safeImageSize, callback);
            }

            @Override
            public void onError(@NonNull Exception error) {
                post(() -> callback.onError(error));
            }
        });
    }

    private static void searchAlternativeLogo(String apiKey, MediaType type, String title, String year, String imageSize, LogoCallback callback) {
        search(apiKey, type, title, year, new SearchCallback() {
            @Override
            public void onFound(@Nullable SearchResult result) {
                if (result != null) fetchLogo(apiKey, type, result.id, imageSize, callback);
                else post(callback::onNotFound);
            }

            @Override
            public void onError(@NonNull Exception error) {
                post(() -> callback.onError(error));
            }
        });
    }

    private static void searchAlternativePoster(String apiKey, MediaType type, String title, String year, String imageSize, ImageCallback callback) {
        search(apiKey, type, title, year, new SearchCallback() {
            @Override
            public void onFound(@Nullable SearchResult result) {
                if (!dispatchPosterResult(result, imageSize, callback)) post(callback::onNotFound);
            }

            @Override
            public void onError(@NonNull Exception error) {
                post(() -> callback.onError(error));
            }
        });
    }

    private static boolean dispatchPosterResult(@Nullable SearchResult result, String imageSize, ImageCallback callback) {
        String filePath = result == null ? "" : result.getFeaturedImagePath();
        if (TextUtils.isEmpty(filePath)) return false;
        post(() -> callback.onFound(buildImageUrl(imageSize, filePath)));
        return true;
    }

    private static void search(String apiKey, MediaType type, String title, String year, SearchCallback callback) {
        HttpUrl url = buildSearchUrl(apiKey, type, title, year);
        TmdbNetwork.newCall(new okhttp3.Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                post(() -> callback.onError(e));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response resp = response) {
                    if (!resp.isSuccessful() || resp.body() == null) throw new IOException("TMDB search failed: HTTP " + resp.code());
                    callback.onFound(parseFirstResult(resp.body().string()));
                } catch (Exception e) {
                    post(() -> callback.onError(e));
                }
            }
        });
    }

    private static void fetchLogo(String apiKey, MediaType type, int id, String imageSize, LogoCallback callback) {
        HttpUrl url = buildImagesUrl(apiKey, type, id);
        TmdbNetwork.newCall(new okhttp3.Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                post(() -> callback.onError(e));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response resp = response) {
                    if (!resp.isSuccessful() || resp.body() == null) throw new IOException("TMDB images failed: HTTP " + resp.code());
                    String filePath = selectBestLogo(new JSONObject(resp.body().string()).optJSONArray("logos"));
                    if (TextUtils.isEmpty(filePath)) post(callback::onNotFound);
                    else post(() -> callback.onFound(buildImageUrl(imageSize, filePath)));
                } catch (Exception e) {
                    post(() -> callback.onError(e));
                }
            }
        });
    }

    private static HttpUrl buildSearchUrl(String apiKey, MediaType type, String title, String year) {
        HttpUrl url = HttpUrl.parse(TmdbEndpoint.getApiBase() + type.searchPath);
        if (url == null) throw new IllegalArgumentException("Invalid TMDB search URL");
        HttpUrl.Builder builder = url.newBuilder()
                .addQueryParameter("query", title)
                .addQueryParameter("language", "zh-CN")
                .addQueryParameter("include_adult", "false");
        if (!TextUtils.isEmpty(apiKey)) builder.addQueryParameter("api_key", apiKey);
        if (!TextUtils.isEmpty(year)) builder.addQueryParameter(type.yearParam, year);
        return builder.build();
    }

    private static HttpUrl buildImagesUrl(String apiKey, MediaType type, int id) {
        HttpUrl url = HttpUrl.parse(TmdbEndpoint.getApiBase() + type.detailPath + "/" + id + "/images");
        if (url == null) throw new IllegalArgumentException("Invalid TMDB images URL");
        HttpUrl.Builder builder = url.newBuilder()
                .addQueryParameter("include_image_language", INCLUDE_IMAGE_LANGUAGE);
        if (!TextUtils.isEmpty(apiKey)) builder.addQueryParameter("api_key", apiKey);
        return builder.build();
    }

    @Nullable
    private static SearchResult parseFirstResult(String body) throws Exception {
        JSONArray results = new JSONObject(body).optJSONArray("results");
        if (results == null) return null;
        for (int i = 0; i < results.length(); i++) {
            JSONObject result = results.optJSONObject(i);
            int id = result == null ? 0 : result.optInt("id");
            if (id > 0) return new SearchResult(id, result.optString("backdrop_path"), result.optString("poster_path"));
        }
        return null;
    }

    @Nullable
    private static String selectBestLogo(@Nullable JSONArray logos) {
        if (logos == null || logos.length() == 0) return null;
        String[] paths = new String[3];
        int[] widths = new int[]{0, 0, 0};
        for (int i = 0; i < logos.length(); i++) {
            JSONObject logo = logos.optJSONObject(i);
            if (logo == null) continue;
            String path = logo.optString("file_path");
            int width = logo.optInt("width");
            if (TextUtils.isEmpty(path) || "null".equalsIgnoreCase(path) || width <= 0) continue;
            int rank = getLanguageRank(logo.optString("iso_639_1"));
            if (width > widths[rank]) {
                paths[rank] = path;
                widths[rank] = width;
            }
        }
        for (String path : paths) if (!TextUtils.isEmpty(path)) return path;
        return null;
    }

    private static int getLanguageRank(String language) {
        if ("zh".equalsIgnoreCase(language)) return 0;
        if ("en".equalsIgnoreCase(language)) return 1;
        return 2;
    }

    private static String buildImageUrl(String imageSize, String filePath) {
        return TmdbEndpoint.getImageBase() + imageSize + (filePath.startsWith("/") ? filePath : "/" + filePath);
    }

    private static MediaType guessMediaType(@Nullable String typeName) {
        String lower = normalize(typeName).toLowerCase(Locale.ROOT);
        if (lower.contains("剧") || lower.contains("电视") || lower.contains("动漫") || lower.contains("动画") || lower.contains("综艺") || lower.contains("纪录") || lower.contains("tv")) {
            return MediaType.TV;
        }
        return MediaType.MOVIE;
    }

    private static String normalize(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeImagePath(@Nullable String value) {
        String path = normalize(value);
        return TextUtils.isEmpty(path) || "null".equalsIgnoreCase(path) ? "" : path;
    }

    private static String normalizeYear(@Nullable String year) {
        Matcher matcher = YEAR.matcher(normalize(year));
        return matcher.find() ? matcher.group() : "";
    }

    private static String normalizeImageSize(@Nullable String imageSize) {
        String value = normalize(imageSize);
        if (TextUtils.isEmpty(value)) return DEFAULT_LOGO_SIZE;
        while (value.startsWith("/")) value = value.substring(1);
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return TextUtils.isEmpty(value) ? DEFAULT_LOGO_SIZE : value;
    }

    private static void post(Runnable runnable) {
        if (App.get() == null) runnable.run();
        else App.post(runnable);
    }

    public interface ImageCallback {

        void onFound(@NonNull String imageUrl);

        void onNotFound();

        void onError(@NonNull Exception error);
    }

    public interface LogoCallback extends ImageCallback {
    }

    private interface SearchCallback {

        void onFound(@Nullable SearchResult result);

        void onError(@NonNull Exception error);
    }

    private static class SearchResult {

        private final int id;
        private final String backdropPath;
        private final String posterPath;

        private SearchResult(int id, String backdropPath, String posterPath) {
            this.id = id;
            this.backdropPath = normalizeImagePath(backdropPath);
            this.posterPath = normalizeImagePath(posterPath);
        }

        private String getFeaturedImagePath() {
            return TextUtils.isEmpty(backdropPath) ? posterPath : backdropPath;
        }
    }

    private enum MediaType {
        MOVIE("search/movie", "movie", "primary_release_year"),
        TV("search/tv", "tv", "first_air_date_year");

        private final String searchPath;
        private final String detailPath;
        private final String yearParam;

        MediaType(String searchPath, String detailPath, String yearParam) {
            this.searchPath = searchPath;
            this.detailPath = detailPath;
            this.yearParam = yearParam;
        }
    }
}
