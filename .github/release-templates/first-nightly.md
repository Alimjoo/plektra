# First Plektra Nightly · 26.10

Plektra is a physical-keyboard IME with its own app identity, release channels and customization settings. This first Nightly brings together the current work for the Titan 2 and Titan 2 Elite.

- **Keyboard bar:** choose suggestions, variations/menu controls and modifier indicators independently. Configure Normal and Slim profiles, reorder the content rows or combine them into one row. Empty content collapses; explicitly opened SYM, emoji and clipboard panels remain available.
- **Consistent outer surfaces:** display corners and LED contours use shared geometry across the bar, expanded panels and software keyboard. The actual outer key owns its contour, including the SYM Close slot and secondary key labels.
- **Rendering:** prepared shapes, LED paths, hit regions and content placements are reused. Color and modifier changes avoid rebuilding unchanged geometry; pixel-by-pixel placement searches are removed.
- **Themes and settings:** the selected theme and previews reflect the effective system theme. Modifier indicators and display geometry have their own settings destinations.
- **Typing fixes:** accent-only dictionary variants take priority for correction; replacement safety checks actual edit distance. Snippet suggestions update IME visibility when shown or cleared.
- **Titan 2 Elite gestures:** the default flick threshold is 250px, with an explanation of the native Scroll assistant prerequisite. Saved thresholds remain in effect.
- **Main screen:** selectable flowing strings and physical-key backgrounds react to typing. The launcher-home visibility setting controls the bar when Niagara exposes an invisible input field.

## Installation

The canonical package is `rocks.pkb.plektra.nightly`. Plektra Nightly can coexist with Plektra Stable and Pastiera. Configure its keyboard profile and languages after installation; automatic migration from Pastiera is planned for the stable release.

The published Nightly APK uses the separate hardware-backed Nightly signing key. See [release channels and signing](https://github.com/pkb-rocks/plektra/blob/main/docs/releasing.md) and [getting started](https://github.com/pkb-rocks/plektra/blob/main/docs/getting-started.md).
