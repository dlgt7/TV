# Spider DEX compatibility fixtures

`legacy` deliberately bundles its own `bean.Result` and `bean.Vod` with `legacyMarker()` methods which do not exist in the host. It also bundles decoy `crawler.Spider` and `net.Net` classes: those core runtime types must still load from the host. Its `destroy()` throws to check that loader cleanup continues for all owners.

`modern` includes no shared SDK helpers. Its `init` deliberately omits `super.init` and immediately uses the injected Net and Local. Its home method uses host Result/Vod and real HTTP.

Rebuild the committed small DEX JARs with JDK 17+, Android build-tools d8 and the published CatVodSpider SDK:

```bash
python3 tools/testing/generate_spider_api_fixtures.py \
  --api /path/to/catvod-api.jar \
  --android-jar /path/to/android-sdk/platforms/android-37.0/android.jar \
  --okhttp /path/to/okhttp-jvm-5.5.0.jar \
  --d8 /path/to/android-sdk/build-tools/37.0.0/d8
```

The outputs live in `app/src/androidTest/assets/spider-api/`, with SHA-256/source lists in `manifest.json`. They contain only test classes and do not enter the application APK.

Run `com.fongmi.android.tv.test.SpiderDexCompatibilityTest` using the isolated `com.fongmi.android.tv.sourceprobe` target and its AndroidJUnitRunner. No external files or network endpoints are required. Results are written to the target's private `files/spider-dex-result.json`. This test must execute on Android; host compilation or source signature checks cannot prove DEX loader identity.
