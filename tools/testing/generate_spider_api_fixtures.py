#!/usr/bin/env python3
"""Rebuild the small Android DEX fixtures without Gradle; requires JDK 17+ and Android d8."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--api", type=Path, required=True, help="Published CatVodSpider catvod-api.jar")
    parser.add_argument("--android-jar", type=Path, required=True)
    parser.add_argument("--okhttp", type=Path, required=True, help="OkHttp JVM or Android classes JAR")
    parser.add_argument("--d8", type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    sources = root / "app/src/androidTest/fixtures/spider-api"
    assets = root / "app/src/androidTest/assets/spider-api"
    assets.mkdir(parents=True, exist_ok=True)
    manifest = {"apiSha256": hashlib.sha256(args.api.read_bytes()).hexdigest(), "fixtures": {}}
    with tempfile.TemporaryDirectory(prefix="spider-api-dex-") as temporary:
        temporary = Path(temporary)
        for kind in ("legacy", "modern"):
            classes, dex = temporary / kind / "classes", temporary / kind / "dex"
            classes.mkdir(parents=True)
            dex.mkdir()
            java = sorted((sources / kind).rglob("*.java"))
            classpath = ":".join(str(path.resolve()) for path in (args.api, args.android_jar, args.okhttp))
            subprocess.run(["javac", "-source", "8", "-target", "8", "-bootclasspath", str(args.android_jar.resolve()),
                            "-encoding", "UTF-8", "-cp", classpath,
                            "-d", str(classes), *map(str, java)], check=True)
            subprocess.run([str(args.d8.resolve()), "--min-api", "24", "--lib", str(args.android_jar.resolve()),
                            "--classpath", str(args.api.resolve()), "--classpath", str(args.okhttp.resolve()),
                            "--output", str(dex), *map(str, sorted(classes.rglob("*.class")))], check=True)
            output = assets / (kind + ".jar")
            with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
                for file in sorted(dex.glob("*.dex")):
                    entry = zipfile.ZipInfo(file.name, date_time=(1980, 1, 1, 0, 0, 0))
                    entry.compress_type = zipfile.ZIP_DEFLATED
                    archive.writestr(entry, file.read_bytes())
            manifest["fixtures"][kind] = {"sha256": hashlib.sha256(output.read_bytes()).hexdigest(),
                                          "bytes": output.stat().st_size,
                                          "sources": [str(path.relative_to(root)) for path in java]}
    (assets / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(json.dumps(manifest, indent=2))


if __name__ == "__main__":
    main()
