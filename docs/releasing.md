# Releasing

Plektra uses calendar versions.

- Stable: `YY.MM`, for example `26.10`
- Stable correction: `YY.MM.N`, for example `26.10.1`
- Nightly: `YY.MM-nightly.DD.HHmmssZ`
- Nightly tag: `nightly/vYY.MM-nightly.DD.HHmmssZ`

Canonical Stable and Nightly APKs are signed with separate hardware-backed PIV keys. Play builds use separate app IDs and an upload key; Google Play applies its own app-signing key to delivered packages.

Release work must verify the package name, version, channel icon, update source, signature, SBOM, source archive, checksums, and release notes before publication. Signing and publication remain explicit maintainer actions.

## Preparing an unsigned Nightly

Build the canonical release APK locally with a single UTC timestamp:

```sh
timestamp=$(date -u +'%Y%m%d.%H%M%S')
scripts/build-nightly-release-unsigned.sh 26.10 \
  ".local-work/releases/26.10/plektra-nightly-unsigned.apk" \
  --timestamp "$timestamp"
```

The script checks the application ID, CalVer version, version code, non-debuggable build and absence of an APK signature. Release APKs include a CycloneDX SBOM generated from that variant's runtime dependencies.

When the relevant unit checks have already passed, `--skip-unit-tests` builds without repeating the complete release unit suite. Device acceptance remains a separate check.

The first Nightly's user-facing notes are in [first-nightly.md](../.github/release-templates/first-nightly.md); the shorter highlights remain within F-Droid's 500-character limit. Prepare a source archive from the exact release commit, extract the embedded SBOM and record SHA-256 checksums alongside the unsigned APK.

Sign the reviewed APK with `scripts/sign-plektra-nightly-apk-piv.sh` in the maintainer's interactive terminal. Final verification and publication use the signed APK and its checksum. Record its exact source commit and CalVer tag in the release metadata.
