# Building Frontier Hunts 1.5.0

Frontier Hunts targets Minecraft 1.21.1, NeoForge 21.1.248 and Java 21. The release builder is `tools/release/BuildRelease.java`; it does not require a globally installed Gradle command.

## Inputs

| Path | Purpose |
| --- | --- |
| `VERSION` | Version written into metadata and the output filename. |
| `release-base/base-formaworks.jar.part1` through `.part6` | Split first-party release base. The builder joins the parts in numeric order. |
| `src/` | Authored Frontier Hunts source changed after the base. |
| `fs/java` and `fs/resources` | Bundled Frontier Structures source and resources. |
| `patch/` | Current resources and data merged into the release. |
| `recovered-source/` | Complete readable source snapshot of older base classes; retained for review, not compiled twice. |
| `tools/release/classpath.txt` | Compile-time libraries resolved from the supplied Gradle cache. |

## Windows

```powershell
.\BUILD-SOURCE.ps1 -NeoForgeJar "C:\path\to\neoforge-21.1.248.jar"
```

Use `-Java` or `-Libraries` if Java 21 or the Gradle cache is in a nonstandard location.

## macOS or Linux

```sh
java tools/release/BuildRelease.java --repo . \
  --base release-base/base-formaworks.jar.part1 \
  --libs ~/.gradle/caches \
  --neoforge /path/to/neoforge-21.1.248.jar \
  --out build-release
```

The output is `build-release/FrontierHunts-1.5.0.jar`. The official GitHub release JAR is the distribution artifact. A locally built archive may differ in compiled class bytes or ZIP metadata even when the source behavior and entry layout match.

## Optional integrations

The release includes optional compatibility code for Sodium, Distant Horizons and Xaero. Sodium and Distant Horizons expose APIs used by the integration. The Xaero bridge targets exact implementation classes because the required functionality does not have a stable public API; future Xaero versions may need a compatibility update.
