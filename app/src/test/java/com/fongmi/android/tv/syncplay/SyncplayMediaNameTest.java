package com.fongmi.android.tv.syncplay;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;

public class SyncplayMediaNameTest {
    @Test public void keepsRealTitlesAndEpisodeLabels() {
        assertEquals("药屋少女的呢喃 第 01 集", SyncplayMediaName.from("药屋少女的呢喃", "第 01 集"));
        assertEquals("Fate/stay night", SyncplayMediaName.from("Fate/stay night", "Fate/stay night"));
        assertEquals("电影：第二章 第01集", SyncplayMediaName.from(" 电影：第二章\n", "第01集"));
    }

    @Test public void discardsFullAddressesRatherThanPotentiallySecretBasenames() {
        for (String address : new String[]{
                "file:///data/user/0/private/files/secret-token.mp4",
                "https://user:password@example.test/secret-token.mp4?signature=secret#private",
                "content://provider/private-document-id", "magnet:?xt=private-token",
                "/storage/emulated/0/Download/private-name.mkv", "C:\\Users\\private\\movie.mp4",
                "\\\\server\\private\\movie.mkv", "relative/private/secret.mp4", "//example.test/private",
                "example.test/secret.mp4?key=secret", "www.example.test/private", "user:password@example.test",
                "secret.mp4?signature=secret", "secret.mp4#secret", "https%3A%2F%2Fprivate.example%2Fsecret",
                "%2568%2574%2574%2570%2573%253A%252F%252Fprivate.example", "电影 https://private.example/secret",
                "https\u200b://private.example/secret", "https:\u200b//private.example/secret"
        }) {
            String result = SyncplayMediaName.from(address, address);
            assertEquals("[Unknown video]", result);
        }
    }

    @Test public void neverAppendsAnAddressFromTheArtistField() {
        assertEquals("电影", SyncplayMediaName.from("电影", "https://user:secret@example.test/episode?token=secret"));
        assertEquals("[Unknown video] 第02集", SyncplayMediaName.from("file:///private/secret.mkv", "第02集"));
    }

    @Test public void unknownTitlesRequireConfirmationWhenAnotherMemberIsPresent() {
        String unknown = SyncplayMediaName.from("file:///private/local.mkv", null);
        var members = List.of(new SyncplayProtocol.Member("Other", "room", unknown, 100));
        assertEquals(SyncplaySynchronizer.Gate.DIFFERENT_MEDIA,
                SyncplaySynchronizer.gate(true, true, "TV", unknown, 100, members, false));
        assertEquals(SyncplaySynchronizer.Gate.READY,
                SyncplaySynchronizer.gate(true, true, "TV", unknown, 100, members, true));
        assertEquals(SyncplaySynchronizer.Gate.READY,
                SyncplaySynchronizer.gate(true, true, "TV", unknown, 100, List.of(), false));
    }

    @Test public void truncatesLongTitlesWithoutSplittingUnicodeCharacters() {
        String result = SyncplayMediaName.from("🎬".repeat(300), null);
        assertEquals(250, result.codePointCount(0, result.length()));
        assertEquals("🎬".repeat(250), result);
    }
}
