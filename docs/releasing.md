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

## Nightly APK variants

The `nightly` and `nightlyNoAccessibility` flavors share the canonical Nightly
package ID, version and PIV signing configuration. They replace each other when
installed; they do not create separate apps. Build both with the same timestamp:

```sh
scripts/build-nightly-release-unsigned.sh 26.10 /path/to/Plektra-VERSION-full.apk --timestamp 20261008.100000
scripts/build-nightly-release-unsigned.sh 26.10 /path/to/Plektra-VERSION-no-accessibility.apk --variant no-accessibility --timestamp 20261008.100000
```

Replace `VERSION` with the generated `full_version`. The release assets must be
named exactly `Plektra-VERSION-full.apk` and
`Plektra-VERSION-no-accessibility.apk`. Both use
`scripts/sign-plektra-nightly-apk-piv.sh` and the same public signing lineage.
The build helper verifies the unsigned APK's package, version and service
presence before copying it to the requested output.

The reduced APK omits the Clicks accessibility service from its merged manifest
and hides its setup and launcher-button remapping controls. Normal keyboard
features and TalkBack support remain available. Clicks launcher-button
interception requires the full APK.

The installed build selects its own variant during GitHub update checks. Missing,
ambiguous or insecure matching assets leave the release-page link available
without a direct APK download. The full build also accepts the previous exact
`Plektra-VERSION-github.apk` filename; the reduced build never accepts that file.

For the transition, first publish a full-only Nightly containing the new selector,
then offer both variants. Older installed builds choose the first APK in GitHub's
asset list: keep the full APK first in every two-APK release and verify the asset
order returned by the GitHub API before publication. Never publish both the
legacy and the new full filename together; that is an ambiguous full download.
