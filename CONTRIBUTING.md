# Contributing

Frontier Hunts is source-available for review. FormaWorks is not currently accepting unsolicited code or asset contributions. Bug reports with clear reproduction steps are welcome through GitHub Issues.

## Reporting a bug

Include:

- Frontier Hunts, Minecraft, NeoForge and Java versions;
- whether the issue occurs in a new test world;
- the smallest reliable sequence that reproduces it;
- relevant logs with account names, server addresses and local paths removed;
- installed mods and shader pack when the problem may be compatibility-related.

Do not upload worlds, screenshots or logs containing another person's private information without their permission.

## Maintainer standards

- Preserve the `frontierhunts` and `frontierstructures` registry namespaces.
- Keep Minecraft 1.21.1, NeoForge 21.1.248 and Java 21 as the release target.
- Treat client rendering as cosmetic; gameplay outcomes remain server-authoritative.
- Keep optional integrations isolated and fail safely when their target mod is absent.
- Never commit build output, credentials, local configuration, game saves or third-party mod JARs.
- Run `python tools/release/verify_public_tree.py` before and after documentation-only maintenance.
- Rebuild and perform focused in-game tests whenever a build input changes.

The [license](LICENSE.md) does not grant permission to redistribute or reuse project code or assets.
