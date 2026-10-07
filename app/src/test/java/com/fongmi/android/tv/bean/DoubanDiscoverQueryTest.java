package com.fongmi.android.tv.bean;

import org.junit.Test;
import okhttp3.HttpUrl;
import static org.junit.Assert.*;

public class DoubanDiscoverQueryTest {
    @Test public void combinesMovieTagsYearSortAndPageWithoutRatingThreshold() {
        HttpUrl url = new DoubanDiscoverQuery("movie", "科幻", "美国", "2010-01-01", "2019-12-31", DiscoverQuery.SORT_RATING, "", 2).buildUrl();
        assertEquals("电影,科幻,美国", url.queryParameter("tags"));
        assertEquals("2010,2019", url.queryParameter("year_range"));
        assertEquals("20", url.queryParameter("start"));
        assertEquals("S", url.queryParameter("sort"));
        assertEquals("0,10", url.queryParameter("range"));
        assertNull(url.queryParameter("vote_count.gte"));
    }

    @Test public void tvPlatformsAndShowFormsRemainActualDoubanTags() {
        DoubanDiscoverQuery tv = new DoubanDiscoverQuery("tv", "古装", "华语", "", "", DiscoverQuery.SORT_LATEST, "腾讯视频", 1);
        assertEquals("电视剧,古装,华语,腾讯视频", tv.buildUrl().queryParameter("tags"));
        assertEquals("R", tv.buildUrl().queryParameter("sort"));
        DoubanDiscoverQuery show = new DoubanDiscoverQuery("show", "真人秀", "华语", "", "", "", "", 1);
        assertEquals("综艺,真人秀,华语", show.buildUrl().queryParameter("tags"));
        assertEquals("tv", show.getMediaType());
    }

    @Test public void movieCannotRetainTvPlatformAndAllYearsHasNoYearRestriction() {
        HttpUrl url = new DoubanDiscoverQuery("movie", "", "", "", "", "", "Netflix", 1).buildUrl();
        assertEquals("电影", url.queryParameter("tags"));
        assertNull(url.queryParameter("year_range"));
    }

    @Test public void earliestYearsAndInvalidPageAreBounded() {
        HttpUrl url = new DoubanDiscoverQuery("movie", "", "", "", "1959-12-31", "", "", 0).buildUrl();
        assertEquals("1,1959", url.queryParameter("year_range"));
        assertEquals("0", url.queryParameter("start"));
    }

    @Test public void platformChangeAndLibraryChangeInvalidateRequestIdentity() {
        DoubanDiscoverQuery netflix = new DoubanDiscoverQuery("tv", "", "", "", "", "", "Netflix", 1);
        DoubanDiscoverQuery hbo = new DoubanDiscoverQuery("tv", "", "", "", "", "", "HBO", 1);
        assertNotEquals(netflix, hbo);
        assertNotEquals(netflix, DiscoverQuery.defaults());
        assertEquals(netflix, new DoubanDiscoverQuery("tv", "", "", "", "", "", "Netflix", 1));
    }

    @Test public void fullRawPageContinuesEvenWhenSomePostersAreMissing() {
        assertEquals(3, DoubanDiscoverQuery.totalPages(2, 20));
        assertEquals(2, DoubanDiscoverQuery.totalPages(2, 19));
        assertEquals(2, DoubanDiscoverQuery.totalPages(2, 0));
    }

    @Test public void switchingToMovieHidesExpandedTvPlatform() {
        DiscoverFilterPanel panel = new DiscoverFilterPanel();
        panel.setRowCount(7);
        panel.setExpandedRow(6);
        panel.setRowCount(6);
        assertEquals(-1, panel.getExpandedRow());
        assertEquals(6, panel.getRowCount());
    }
}
