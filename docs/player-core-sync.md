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

libass runs on renderer worker threads, at most 30 fps and 1080p-equivalent area. UI frame delivery is coalesced. Font imports/attachments are capped at 24 MiB each and 32 MiB per font set, ASS sidecars at 16 MiB, and queued/event data at 32 MiB. Seeks and track changes invalidate stale frame work; pause/rebinding preserves the current frame.

## Verification

Verified on 2026-10-04:

- 240 app JUnit tests and 14 proxy tests passed.
- TV ARM64 and ARMv7 debug APKs built; mobile ARM64 Java/Kotlin compilation passed.
- TV lint completed with 0 errors (311 warnings remain).
- Four device instrumentation scenarios passed: two SRT tracks with DSP and pause/rebuild; external ASS animation and seek; embedded ASS plus independent secondary selection/disable and paused rebind; prepared-source handoff with header isolation and cancellation.
- Native logs confirm Matroska attachment selection (`DejaVu Sans → DejaVuSans`) and external-subtitle fallback (`DejaVu Sans → Roboto-Regular`). Both APKs contain exactly one libass, JNI bridge and C++ runtime per ABI.
- FFmpeg accepted the EQ/loudness/limiter chain and the center-gain filter retaining the `5.1(side)` layout.

The reproducible fixtures contain two SRT languages, two animated ASS tracks, a Matroska font attachment and an external ASS sidecar.

```bash
bash tools/player-core-fixtures.sh /tmp/tv-core-fixtures
python3 tools/player-core-fixture-server.py /tmp/tv-core-fixtures
./gradlew :catvod:testDebugUnitTest :app:testLeanbackArm64_v8aDebugUnitTest
./gradlew :app:assembleLeanbackArm64_v8aDebug :app:assembleLeanbackArmeabi_v7aDebug
./gradlew :app:compileMobileArm64_v8aDebugJavaWithJavac :app:lintLeanbackArm64_v8aDebug
./gradlew :app:assembleLeanbackArm64_v8aDebugAndroidTest
adb shell am instrument -w -r -e core_base http://10.0.2.2:9980/ \
  -e class com.fongmi.android.tv.test.PlayerCoreTest,com.fongmi.android.tv.player.exo.NextMediaPreloadTest \
  com.fongmi.android.tv.test/androidx.test.runner.AndroidJUnitRunner
```

The fixture server rejects media requests whose per-item header does not match the requested file, including range requests. The debug-only test activity is absent from release builds.

## Test environment and limits

The dedicated Codespace uses an Android 7.1 x86_64 emulator. Its disposable test APK contains the published x86_64 libass and UI dependencies, with the unused Python loader bypassed; ARM deliverables remain unchanged. Exo/ASS/audio/preload results are actual emulator playback results. They do not establish ARM-device MPV playback, HDMI passthrough, Python execution or end-to-end AI recognition quality. Those require suitable ARM hardware/models. The supplied MPV binaries contain equalizer, pan, dynaudnorm, alimiter and secondary ASS support; the filter syntax is also exercised with FFmpeg. Next-media buffering is an Exo feature; MPV retains its existing current-stream demux cache.
