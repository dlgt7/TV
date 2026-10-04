# Playback core integration

Base: `2dc4576fe` on `ui/apple-tv-redesign`.
Upstream reference: FongMi/TV `c616c0aa3613e87529791587a9f71b78c278c991`.
Media3 source: `release-1.11.0-fongmi`, `3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d`.

## Changes

- Proxy redirects retain the originating routing policy for CDN/IP hops, honor explicit target rules, drop stale proxy credentials and preserve no-redirect clients. Proxy authentication uses the route's policy. Adapted from the pinned public upstream; OkHttp and MockWebServer move together to 5.5.0.
- Chaquopy receives the ABI actually selected from the packaged Python libraries.
- Exo prepares one next episode in the last 45 seconds before the end/credit-skip boundary. Source lookup is cancellable and isolated from the active native extractor. Parsed, DRM and non-HTTP sources are excluded. Results expire after 60 seconds; failures have a 15-second retry cooldown. Media3 preloads up to 10 seconds with a 64 MiB target, yields while the current video buffers, and transfers the prepared source at handoff. Each item owns its request headers and embedded font set. Settings → Preload controls the feature.
- Exo renders original ASS styles/animation with real libass, including Matroska font attachments and external ASS files. Primary and secondary text streams are independently selected; turning off the primary leaves the secondary active. Advanced subtitle settings offer native Material controls and SAF font import. MPV uses its native secondary-sid, style and font controls. Existing primary track preferences, subtitle offsets and AI overlays remain in place.
- Audio track settings expose off/dialogue/night/music/custom profiles, five EQ bands, multichannel center gain, loudness normalization and a limiter. Exo processes PCM after the AI tap; enabling effects disables encoded passthrough for that sink, and disabling restores the configured behavior. MPV applies a named lavfi filter chain, preserving other filters and its previous passthrough setting. Settings changes preserve pause and position.

## Native runtime and bounds

`subtitle-ass` reproducibly resolves `io.github.peerless2012:ass-kt:0.5.1`, verifies SHA-256 and retains the existing MPV C++ runtime. JNI classes have explicit R8 keep rules. Attribution and source links are in `third_party/libass`.

libass runs on renderer worker threads, at most 30 fps and 1080p-equivalent area. UI frame delivery is coalesced. Font imports/attachments are capped at 24 MiB each and 32 MiB per font set, ASS sidecars at 16 MiB, queued/event data at 32 MiB, and retained ASS replay history at 16 MiB / 32,000 events per source. History evicts old packets without stopping later subtitles; native event storage is periodically rebuilt from that bounded history. Seeks and track changes invalidate stale frame work; pause/rebinding preserves the current frame.

## Verification

Verified on 2026-10-04:

- 267 app JUnit tests and 14 proxy tests passed.
- TV ARM64 and ARMv7 debug APKs built; mobile ARM64 Java/Kotlin compilation passed.
- TV lint completed with 0 errors (311 warnings remain).
- Device instrumentation: **all 12 scenarios passed**. Cases cover SUP/PGS, 1920×1080 VobSub, DVB paused forward/backward seeks and clear pages, complex ASS, WebVTT/TTML, plus two SRT tracks with DSP and pause/rebuild; external ASS animation and seek; embedded ASS plus independent secondary selection/disable and paused rebind; prepared-source handoff with header isolation and cancellation.
- Native logs confirm Matroska attachment selection (`DejaVu Sans → DejaVuSans`) and external-subtitle fallback (`DejaVu Sans → Roboto-Regular`). Both APKs contain exactly one libass, JNI bridge and C++ runtime per ABI.
- FFmpeg accepted the EQ/loudness/limiter chain and the center-gain filter retaining the `5.1(side)` layout.

The original generated fixtures contain SRT, embedded/external ASS, a Matroska font attachment, multi-region WebVTT and TTML. Complex ASS combines karaoke, motion, rotation/transforms, clipping, vector drawing, Unicode bidirectional shaping and overlapping events. Tests compare the exact paused frame before and after a backward seek and check that empty intervals are actually transparent.

`tools/player-core-sample-fixtures.py` downloads public FFmpeg regression samples with pinned SHA-256 hashes. It remuxes their subtitle streams onto generated video without re-encoding the captions:

| Input | Exercised behavior |
| --- | --- |
| `PGS/supsample.mkv` | Embedded PGS and exported external `.SUP`, clear display set, backwards seek, exchange PGS/ASS primary and secondary |
| `BluRay/title03_track2.sup` | Complete ~36 MiB Blu-ray SUP with 4,113 display sets; first/later cue, empty interval and backwards seek |
| `largeres_vobsub.mkv` | 1920×1080 VobSub plane embedded in MKV, palette/bitmap decoding, stop timing and seek |
| `dvbsub/dvbsubtest.ts` | DVB initial display, paused forward seek into an active page, two-region composition, clear interval and exact bitmap restoration after backward seek |

Source base: https://samples.ffmpeg.org/sub/. Downloaded media is not committed. The older IDX/SUB pair `VOB/X.The.Movie.DVDivX-SChiZO` was inspected but lacks its palette without DVD context; the short Channel 4 DVB cut lacks an acquisition page. These incomplete samples were replaced with the complete inputs above. VobSub coverage is for the remuxed MKV track; it does not establish direct loading of external IDX+SUB pairs. `BluRay/Subpictures_20.sup` was also inspected, but its `SP` header identifies an older DVD SUP format, not the `PG` PGS format supported by the `.sup` MIME mapping.

The expanded tests exposed and fixed two seek regressions beyond overlap replay: external ASS advertised an unseekable map, causing a merged-period seek exception; the default lazy sidecar path lost the last cue's end time and could return EOS when seeking into that cue. The custom sidecar extractor now publishes its own seek map and exact final dialogue/cue end. Full SUP stress testing also exposed whole-file decoding stalls on repeated seeking. SUP now streams bounded display sets through Media3’s PGS parser and indexes acquisition/epoch boundaries, retaining palette/object state across normal display sets and resetting it on seek. A display set is capped at 4 MiB and the seek index at 64,000 boundaries.

```bash
CORE_REAL_SAMPLES=1 bash tools/player-core-fixtures.sh /tmp/tv-core-fixtures
python3 tools/player-core-fixture-server.py /tmp/tv-core-fixtures
./gradlew :catvod:testDebugUnitTest :app:testLeanbackArm64_v8aDebugUnitTest
./gradlew :app:assembleLeanbackArm64_v8aDebug :app:assembleLeanbackArmeabi_v7aDebug
./gradlew :app:compileMobileArm64_v8aDebugJavaWithJavac :app:lintLeanbackArm64_v8aDebug
./gradlew :app:assembleLeanbackArm64_v8aDebugAndroidTest
adb shell am instrument -w -r -e core_base http://10.0.2.2:9980/ \
  -e class com.fongmi.android.tv.test.PlayerCoreTest,com.fongmi.android.tv.player.exo.NextMediaPreloadTest \
  com.fongmi.android.tv.test/androidx.test.runner.AndroidJUnitRunner
```

The DVB regression is fixed in `PlayerCoreTest.broadcastDvbBitmapRendersAndSurvivesSeek`. Instrumented tracing established the precise failure: a seek to 16.734 seconds made the TS binary seeker resume at the next subtitle update (16.940 seconds), skipping the page still active at the requested time. The renderer correctly remained empty while paused; this was not evidence that the subsequent packet failed decoding. The TS subtitle extractor now indexes DVB acquisition pages and seeks back far enough to rebuild the active page, including preceding display-definition and ancillary CLUT/object dependencies. It resets subtitle parsers and incomplete PES data on seek and bypasses the TS binary seek that would otherwise skip the indexed acquisition again. Complete PES delivery also avoids nonzero byte offsets into Media3's DVB parser.

The original failing forward-seek assertion remains. The extended test checks that the paused page stays visible, a later page has two regions, real clear intervals are transparent, and the backward-seek bitmap exactly matches its reference. Eight unit tests cover acquisition versus normal pages, ancillary dependencies, multiple tracks, incomplete segments, format changes, unvisited targets and index thinning. The index holds at most 4,096 entries per track, and one DVB PES is capped at 4 MiB. An unvisited target reuses the latest known acquisition; before any known acquisition it starts at the beginning. Long unindexed intervals can therefore require additional preroll. TS streams without DVB retain their normal seek map and binary seeking.

Final validation used the pinned Media3 sources with the temporary diagnostic logging removed. The complete device run finished with `OK (12 tests)` in 71.248 seconds; its output is retained in [instrumentation.txt](player-core-validation/instrumentation.txt).

Screenshots from playback validation:

- [ASS at 5 and 14 seconds](player-core-validation/ass-validation.png): karaoke, rotation, clipping, drawing and bidi text.
- [WebVTT and TTML regions](player-core-validation/region-validation.png): independently positioned overlapping text.
- [DVB after paused seeks](player-core-validation/dvb-validation.png): the restored page at 16.5 seconds and the two-region page at 20 seconds (public FFmpeg regression sample).

The fixture server rejects media requests whose per-item header does not match the requested file, including range requests. The debug-only test activity is absent from release builds.

## Test environment and limits

The dedicated Codespace uses an Android 7.0 (API 24) x86_64 emulator, confirmed from the running device's system properties. Its disposable test APK contains the published x86_64 libass and UI dependencies, with the unused Python loader bypassed; ARM deliverables remain unchanged. Exo/ASS/audio/preload results are actual emulator playback results. They do not establish ARM-device MPV playback, HDMI passthrough, Python execution or end-to-end AI recognition quality. Those require suitable ARM hardware/models. The supplied MPV binaries contain equalizer, pan, dynaudnorm, alimiter and secondary ASS support; the filter syntax is also exercised with FFmpeg. Next-media buffering is an Exo feature; MPV retains its existing current-stream demux cache.
