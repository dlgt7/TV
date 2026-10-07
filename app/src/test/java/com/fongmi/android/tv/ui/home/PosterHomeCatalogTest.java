package com.fongmi.android.tv.ui.home;

import com.fongmi.android.tv.api.DiscoverApi;
import com.fongmi.android.tv.bean.Vod;

import org.junit.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PosterHomeCatalogTest {

    @Test
    public void retainsDoubanPostersWhenTmdbIsUnavailable() {
        Map<DiscoverApi.Row, List<Vod>> content = new EnumMap<>(DiscoverApi.Row.class);
        content.put(DiscoverApi.Row.DOUBAN_HOT_MOVIE, List.of(vod("douban:1", "电影", "movie", "2026", false)));
        content.put(DiscoverApi.Row.DOUBAN_HOT_TV, List.of(vod("douban:2", "剧集", "tv", "2026", false)));

        assertEquals(List.of("电影", "剧集"), PosterHomeCatalog.hero(content, PosterHomeCatalog.ALL).stream().map(Vod::getName).toList());
        assertEquals(List.of("剧集"), PosterHomeCatalog.hero(content, PosterHomeCatalog.TV).stream().map(Vod::getName).toList());
    }

    @Test
    public void prefersLandscapeAndPreservesDistinctRemakes() {
        Map<DiscoverApi.Row, List<Vod>> content = new EnumMap<>(DiscoverApi.Row.class);
        content.put(DiscoverApi.Row.TMDB_DAY, List.of(vod("tmdb:movie:1", "同名电影", "movie", "2026", true)));
        content.put(DiscoverApi.Row.DOUBAN_HOT_MOVIE, List.of(
                vod("douban:1", "同 名 电 影", "movie", "2026", false),
                vod("douban:2", "同名电影", "movie", "1990", false)));

        List<Vod> hero = PosterHomeCatalog.hero(content, PosterHomeCatalog.ALL);

        assertEquals(2, hero.size());
        assertEquals("tmdb:movie:1", hero.get(0).getId());
        assertEquals("1990", hero.get(1).getYear());
    }

    @Test
    public void keepsMoviesAndSeriesOutOfEachOthersCategory() {
        List<Vod> items = List.of(vod("tmdb:movie:1", "电影", "movie", "2026", true),
                vod("tmdb:tv:2", "剧集", "tv", "2026", true));

        assertEquals(List.of("电影"), PosterHomeCatalog.filter(items, PosterHomeCatalog.MOVIES).stream().map(Vod::getName).toList());
        assertEquals(List.of("剧集"), PosterHomeCatalog.filter(items, PosterHomeCatalog.TV).stream().map(Vod::getName).toList());
        assertFalse(PosterHomeCatalog.visible(DiscoverApi.Row.DOUBAN_HOT_MOVIE, PosterHomeCatalog.TOP));
        assertTrue(PosterHomeCatalog.visible(DiscoverApi.Row.TMDB_TOP_TV, PosterHomeCatalog.TOP));
    }

    @Test
    public void boundsShelfAndHeroWorkForOversizedLists() {
        List<Vod> items = new ArrayList<>();
        for (int i = 0; i < 100; i++) items.add(vod("tmdb:movie:" + i, "电影" + i, "movie", "2026", true));
        Map<DiscoverApi.Row, List<Vod>> content = Map.of(DiscoverApi.Row.TMDB_DAY, items);

        assertEquals(20, PosterHomeCatalog.filter(items, PosterHomeCatalog.ALL).size());
        assertEquals(5, PosterHomeCatalog.hero(content, PosterHomeCatalog.ALL).size());
        assertTrue(PosterHomeCatalog.hero(Map.of(), PosterHomeCatalog.ALL).isEmpty());
    }

    private static Vod vod(String id, String title, String type, String year, boolean landscape) {
        Vod item = new Vod();
        item.setId(id);
        item.setName(title);
        item.setTypeName(type);
        item.setYear(year);
        item.setPic("poster");
        item.setBackdrop(landscape ? "backdrop" : "poster");
        return item;
    }
}
