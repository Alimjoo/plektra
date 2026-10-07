# Releasing

Plektra uses calendar versions.

- Stable: `YY.MM`, for example `26.10`
- Stable correction: `YY.MM.N`, for example `26.10.1`
- Nightly: `YY.MM-nightly.DD.HHmmssZ`
- Nightly tag: `nightly/vYY.MM-nightly.DD.HHmmssZ`

Canonical Stable and Nightly APKs are signed with separate hardware-backed PIV keys. Play builds use separate app IDs and an upload key; Google Play applies its own app-signing key to delivered packages.

Release work must verify the package name, version, channel icon, update source, signature, SBOM, source archive, checksums, and release notes before publication. Signing and publication remain explicit maintainer actions.
