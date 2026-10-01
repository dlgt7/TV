package com.fongmi.android.tv.source;

import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MediaMatcherTest {

    @Test
    public void mergesArabicAndChineseSeasonTitles() {
        assertTrue(MediaMatcher.sameMedia(vod("庆余年2", "", "电视剧"), vod("慶餘年 第二季", "", "电视剧")));
    }

    @Test
    public void normalizesTraditionalFullWidthAndRomanSeason() {
        assertEquals(MediaMatcher.identity("庆余年2"), MediaMatcher.identity("慶餘年Ⅱ"));
    }

    @Test
    public void doesNotMergeDifferentYears() {
        assertFalse(MediaMatcher.sameMedia(vod("流浪地球", "2019", "电影"), vod("流浪地球", "2023", "电影")));
    }

    @Test
    public void doesNotMergeMovieAndSeries() {
        assertFalse(MediaMatcher.sameMedia(vod("三体", "2023", "电视剧"), vod("三体", "2023", "电影")));
    }

    @Test
    public void doesNotMergeDifferentSeasons() {
        assertFalse(MediaMatcher.sameMedia(vod("庆余年1", "", "电视剧"), vod("庆余年2", "", "电视剧")));
    }

    @Test
    public void queryMatchesFuzzySeasonAndNoise() {
        assertTrue(MediaMatcher.queryMatches("庆余年2", vod("庆余年 第二季 4K 完结", "", "电视剧")));
    }

    @Test
    public void keepsChineseLettersDuringNormalization() {
        assertEquals("庆余年", MediaMatcher.identity("庆余年2").getTitle());
        assertEquals("庆余年12", MediaMatcher.normalizedEpisodeName("庆余年 第12集"));
    }

    @Test
    public void normalizesEpisodeMarkersWithArabicAndChineseNumbers() {
        assertEquals("12", MediaMatcher.normalizedEpisodeName("第１２集"));
        assertEquals("12", MediaMatcher.normalizedEpisodeName("12 集"));
        assertEquals("十二", MediaMatcher.normalizedEpisodeName("第十二集"));
        assertEquals("十二", MediaMatcher.normalizedEpisodeName("十二集"));
        assertEquals("12", MediaMatcher.normalizedEpisodeName("Episode 12"));
        assertEquals("12", MediaMatcher.normalizedEpisodeName("EP12"));
    }

    @Test
    public void keepsWordsThatContainEpisodeMarkerLetters() {
        assertEquals("shepherd", MediaMatcher.normalizedEpisodeName("Shepherd"));
        assertEquals("specialepisode", MediaMatcher.normalizedEpisodeName("Special Episode"));
        assertEquals("第一现场", MediaMatcher.normalizedEpisodeName("第一现场"));
        assertEquals("集结号", MediaMatcher.normalizedEpisodeName("集结号"));
        assertEquals("第一集训队", MediaMatcher.normalizedEpisodeName("第一集训队"));
    }

    @Test
    public void keepsSplitEpisodeAndPreviewModifiers() {
        assertEquals("十二上", MediaMatcher.normalizedEpisodeName("第十二集上"));
        assertEquals("十二下", MediaMatcher.normalizedEpisodeName("十二集下"));
        assertEquals("十二预告", MediaMatcher.normalizedEpisodeName("第十二集预告"));
        assertEquals("十二上", MediaMatcher.normalizedEpisodeName("第十二话（上）"));
    }

    @Test
    public void keepsUnicodeLettersAndDigitsWithoutEpisodeMarkers() {
        assertEquals("𠮷野12", MediaMatcher.normalizedEpisodeName("𠮷野-１２"));
        assertEquals("", MediaMatcher.normalizedEpisodeName(null));
    }

    private static Vod vod(String name, String year, String type) {
        Vod vod = new Vod();
        vod.setName(name);
        vod.setYear(year);
        vod.setTypeName(type);
        vod.setSite(Site.get(name + year + type, "test"));
        return vod;
    }
}
