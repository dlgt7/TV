package com.fongmi.android.tv.source;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class EpisodeTargetTest {

    @Test
    public void shouldFindSameChineseNumberedEpisodeAcrossSources() {
        EpisodeTarget target = EpisodeTarget.of(episode("第十二集"));
        Episode same = episode("十二");
        Flag flag = new Flag("other-source");
        flag.getEpisodes().add(episode("十三"));
        flag.getEpisodes().add(same);

        assertNull(target.getNumber());
        assertSame(same, SmartSourceSelector.findEpisode(flag, target));
    }

    @Test
    public void shouldKeepNumericMatchingAcrossDifferentEpisodeFormats() {
        EpisodeTarget target = EpisodeTarget.of(episode("第12集"));

        assertTrue(target.matches(episode("Episode 12")));
    }

    @Test
    public void shouldMatchChineseSplitEpisodesWithoutConfusingParts() {
        EpisodeTarget target = EpisodeTarget.of(episode("第十二集上"));
        Episode same = episode("十二集上");
        Flag flag = new Flag("other-source");
        flag.getEpisodes().add(episode("十二集下"));
        flag.getEpisodes().add(same);

        assertSame(same, SmartSourceSelector.findEpisode(flag, target));
        assertFalse(target.matches(episode("十二集下")));
        assertFalse(target.matches(episode("十二")));
    }

    private Episode episode(String name) {
        Episode episode = new Episode();
        episode.setName(name);
        return episode;
    }
}
