package com.fongmi.android.tv.utils;

import static org.junit.Assert.assertEquals;

import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Vod;

import org.junit.Test;

import java.util.List;

public class SearchResultFilterTest {

    @Test
    public void keepsTitlesContainingNormalizedKeyword() {
        Result result = Result.list(List.of(vod("斗破苍穹 年番"), vod("完美世界"), vod("The Matrix")));

        SearchResultFilter.apply(result, "斗破  苍穹");

        assertEquals(1, result.getList().size());
        assertEquals("斗破苍穹 年番", result.getList().get(0).getName());
    }

    @Test
    public void ignoresCaseAndPunctuation() {
        Result result = Result.list(List.of(vod("The.Matrix Reloaded"), vod("The Lord of the Rings")));

        SearchResultFilter.apply(result, "the matrix");

        assertEquals(1, result.getList().size());
        assertEquals("The.Matrix Reloaded", result.getList().get(0).getName());
    }

    @Test
    public void keepsAllResultsForEmptyKeyword() {
        Result result = Result.list(List.of(vod("任意影片")));

        SearchResultFilter.apply(result, "  ");

        assertEquals(1, result.getList().size());
    }

    @Test
    public void matchesChineseSeasonNotation() {
        Result result = Result.list(List.of(vod("庆余年 第二季"), vod("庆余年 第一季")));

        SearchResultFilter.apply(result, "庆余年2");

        assertEquals(1, result.getList().size());
        assertEquals("庆余年 第二季", result.getList().get(0).getName());
    }

    @Test
    public void originalModeRetainsPreviouslyShippedReverseContainsBehavior() {
        Result result = Result.list(List.of(vod("庆"), vod("庆余年 第二季"), vod("完美世界")));
        SearchResultFilter.apply(result, "庆余年", 0);
        assertEquals(2, result.getList().size());
    }

    @Test
    public void relatedModeRemovesPartialFalsePositivesWithoutHidingRelatedEditions() {
        Result result = Result.list(List.of(vod("庆"), vod("庆余年 第二季"), vod("庆余年 幕后特辑")));
        SearchResultFilter.apply(result, "庆余年", 1);
        assertEquals(2, result.getList().size());
    }

    @Test
    public void strictModeOnlyKeepsTheCompleteTitle() {
        Result result = Result.list(List.of(vod("庆"), vod("庆余年"), vod("庆余年 第二季"), vod("庆余年 幕后特辑")));
        SearchResultFilter.apply(result, "庆余年", 2);
        assertEquals(1, result.getList().size());
    }

    @Test
    public void fullRelatedSearchStillOffersReviewsExcludedFromPosterSources() {
        Result result = Result.list(List.of(vod("蜘蛛侠：崭新之日"), vod("线上真实影评《蜘蛛侠崭新之日》"), vod("完美世界")));
        SearchResultFilter.apply(result, "蜘蛛侠：崭新之日", 1);
        assertEquals(2, result.getList().size());
    }

    private static Vod vod(String name) {
        Vod vod = new Vod();
        vod.setName(name);
        return vod;
    }
}
