# Release checks

Both 1.3.1 and 1.4.0 were rebuilt from their versioned source with Java 21 and the existing first-party binary base. Author credits and internal package names now use FormaWorks. Game registry identifiers and release versions are retained.

Each release has passed archive-integrity and name-removal checks, correct-password extraction, incorrect-password rejection and a SHA-256 comparison of the extracted JAR. Checksums accompany each release.

These checks do not establish full gameplay, shader or multiplayer coverage. Existing [known issues](KNOWN_ISSUES.md) still apply. No independent security audit has been performed.
