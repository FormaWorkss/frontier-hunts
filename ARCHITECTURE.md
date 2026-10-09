# Architecture

Frontier Hunts ships two NeoForge mods in one JAR: `frontierhunts` and the bundled `frontierstructures`. Both target Minecraft 1.21.1 and NeoForge 21.1.248.

## Repository map

| Path | Role |
| --- | --- |
| `src/` | Authored Frontier Hunts Java sources compiled for the 1.5.0 release. |
| `fs/java/` | Bundled Frontier Structures Java sources. |
| `fs/resources/` | Frontier Structures data and resources. |
| `patch/` | Current Frontier Hunts assets, data and metadata merged over the base. |
| `release-base/` | Split first-party base archive used by the release builder. |
| `recovered-source/` | Readable review snapshot for older base classes; not compiled a second time. |
| `tools/release/` | Release builder, classpath inventory and public-tree verifier. |
| `docs/` | Build, release, performance and system documentation. |

## Runtime boundaries

- Server code owns hunting outcomes, progression, inventories, wildlife state and saved data.
- Client code owns rendering, screens, input presentation, sound playback and visual effects.
- Network packets cross that boundary through bounded, validated messages.
- Optional compatibility code checks for its target mod and remains outside the required dependency set.
- Game registry names stay stable across updates so existing worlds retain their blocks, items, entities and saved state.

## Release construction

`tools/release/BuildRelease.java` joins the split first-party base, compiles the authored release overlay and Frontier Structures, replaces superseded class families, removes retired paths and merges current resources. See [building instructions](docs/BUILDING.md).

The repository verifier hashes every release input, including every current patch audio file and all split base parts. Together those hashes protect the complete release input while making documentation-only maintenance reviewable without modifying the playable artifact.
