# Frontier Hunts 1.5.0

Complete source snapshot for Frontier Hunts 1.5.0, targeting **Minecraft 1.21.1**, **NeoForge 21.1.248** and **Java 21**. Frontier Structures 1.2.0 is bundled as a second mod in the same release.

The repository is organized for review: [architecture](ARCHITECTURE.md), [source layout](SOURCE_NOTES.md), [build instructions](docs/BUILDING.md), [verification](VERIFICATION.md), [license](LICENSE.md) and [contribution guidance](CONTRIBUTING.md).

## Build

Install a Java 21 JDK. Point the included build script at a NeoForge 21.1.248 development artifact and your Gradle dependency cache:

```powershell
.\BUILD-SOURCE.ps1 -NeoForgeJar "C:\path\to\neoforge-21.1.248.jar"
```

On macOS or Linux:

```sh
java tools/release/BuildRelease.java --repo . \
  --base release-base/base-formaworks.jar.part1 \
  --libs ~/.gradle/caches \
  --neoforge /path/to/neoforge-21.1.248.jar \
  --out build-release
```

The output is written to `build-release/FrontierHunts-1.5.0.jar`. See [docs/BUILDING.md](docs/BUILDING.md) and [SOURCE_NOTES.md](SOURCE_NOTES.md) for the source layout. The release builder compiles the authored 1.5.0 overlay and bundled Frontier Structures source, then merges them with the split, first-party base. `recovered-source/` contains readable source for every older base class and is included for complete review; it is not a second build input.

For a fast integrity check that does not launch Minecraft:

```powershell
python tools/release/verify_public_tree.py
```

## Install

Download the official 1.5.0 release, close Minecraft, and place `FrontierHunts-1.5.0.jar` in the instance's `mods` folder. Use Minecraft 1.21.1, NeoForge 21.1.248 and Java 21. Clients and servers must use the same version. Do not install a separate Frontier Structures JAR because it is already bundled.

## Support

- [Official release](https://github.com/FormaWorkss/frontier-hunts/releases/tag/v1.5.0)
- [Discord](https://discord.gg/qgF5u973ue)
- [Issues](https://github.com/FormaWorkss/frontier-hunts/issues)
- [License](LICENSE.md)

This is source-available software under an All Rights Reserved license. Reading the source does not grant permission to redistribute or reuse it.
