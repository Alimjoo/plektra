# Migration from Pastiera

In Plektra, open **Settings → Advanced → Restore from file** and select a Pastiera backup ZIP. Create the backup in Pastiera before importing it. Keep the ZIP until you have checked the imported settings.

Plektra translates `pastiera_prefs` to `plektra_prefs` and Pastierina settings to Slim settings. Supported backup data includes settings, user dictionary entries, custom input styles, keyboard layouts, key mappings, variations and typing sound packs. Settings and files absent from the backup remain unchanged. Current speech provider settings and encrypted API keys remain on the device.

Imported values replace matching settings. If an archive contains both legacy and Plektra names, the Plektra value takes precedence. Importing the same backup again applies the same values. Plektra refreshes the user dictionary and registers imported input styles.

Pastiera 0.85 values `force_minimal` and `force_full` become Slim and Normal mode. The old `follow_system` value becomes Normal mode.

Runtime state is recalculated. When a backup identifies a different source device, the import dialog offers adaptation to the current device. Adaptation preserves the target keyboard profile and device-specific display settings. Older backups without device metadata use the normal import; check the device profile afterwards.

Plektra validates supported backup data before changing settings or files. A write failure triggers restoration of the previous state. If recovery also fails, Plektra reports the failure and retains recovery data in its private cache.

The import has integration tests and Pixel 7a UI tests with synthetic backup data based on the historical Pastiera 0.85 and 0.86 export contracts. The UI tests use Android DocumentsUI, repeat each import, reject corrupt backups and check that existing application data and the active IME remain unchanged. Verification with an actual user export on a Titan 2 or Titan 2 Elite remains pending.

An assisted migration flow with an import preview is planned before the first Plektra stable release. Plektra has a separate package name and does not automatically read another application's private data.
