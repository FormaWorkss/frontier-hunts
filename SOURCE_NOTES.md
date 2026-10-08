# Source snapshot notes

This repository contains the complete Java and resource snapshot corresponding to the Frontier Hunts 1.5.0 release.

`src/`, `fs/`, `patch/` and `tools/release/` are the verified release build inputs. Some older development source records were previously retained only as compiled classes in the project's first-party release base. Those missing Java files were mechanically recovered from the project's own 1.5.0 bytecode and placed in `recovered-source/` so every shipped class remains readable. Names were normalized to the public `FormaWorks` identity. Game registry identifiers remain unchanged for world compatibility.

The official release JAR remains the distribution artifact. Build and verification status is recorded in `VERIFICATION.md`.
