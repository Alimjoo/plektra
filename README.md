<p align="center"><img src="docs/branding/plektra-open-color.svg" alt="Plektra" width="160"></p>

# Plektra

Plektra is an Android input method for phones with physical keyboards. It is free software under the GNU General Public License v3.0.

## Highlights

- Configurable physical keyboard layouts, modifiers, SYM pages, and navigation shortcuts
- Suggestions, dictionaries, snippets, emoji, clipboard history, and voice input
- A compact **Slim mode** and a full status bar
- Device-specific layouts and display geometry for keyboard phones
- Local backup and restore for settings and user data

See [Features](docs/features.md) for details.

## Install

Plektra is currently prepared for four independent channels:

- **Plektra** — canonical stable build, signed with the published hardware-backed stable key
- **Plektra Nightly** — canonical development build, signed with the published hardware-backed nightly key
- **Google Play stable** — planned Play build under a separate app identity
- **Google Play Nightly** — planned Play test build under a separate app identity

The first stable release is planned for **26.10**. Current Nightlies use the `26.10-nightly.DD.HHmmssZ` format. Installation instructions are in [Getting started](docs/getting-started.md).

## Devices

Plektra is developed and release-tested on the Unihertz Titan 2 and Titan 2 Elite. Community reports cover further physical-keyboard devices. See the short [device support ranking](docs/devices.md); hardware notes live in separate [device support documents](docs/device-support/).

## Moving from Pastiera

Plektra has its own app identity. Pastiera backup ZIPs can be imported in Advanced settings. An assisted migration flow is planned before the first Plektra stable release. See [Migration from Pastiera](docs/migration-from-pastiera.md).

## Development and contributions

Build instructions and project structure are in [Development](docs/development.md). Release maintainers use [Releasing](docs/releasing.md).

Plektra is licensed under [GPLv3](LICENSE). Distributed forks must use their own name, application ID, update endpoints, and branding, and must not present themselves as official Plektra releases.

## Project links

- [GitHub](https://github.com/pkb-rocks/plektra)
- [Website](https://pkb.rocks)
- [Open Collective](https://opencollective.com/plektra)
- [Security policy](SECURITY.md)
- [Imprint](https://pastiera.eu/imprint)
