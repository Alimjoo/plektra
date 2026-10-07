# Settings links

Plektra settings can be opened without changing a value. The canonical public form is:

```text
https://pkb.rocks/settings/<setting-id>
```

The app also accepts the internal URI form `plektra://setting/<setting-id>`.

For example, the app icon selector is available at
`https://pkb.rocks/settings/customization.app_icon` and
`plektra://setting/customization.app_icon`.

Android verifies the HTTPS host through `https://pkb.rocks/.well-known/assetlinks.json`. The association must list each published Plektra package and its release certificate before that package is distributed.

Useful checks:

```sh
adb shell pm verify-app-links --re-verify rocks.pkb.plektra
adb shell pm get-app-links rocks.pkb.plektra
adb shell am start -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d 'https://pkb.rocks/settings/main.about'
```

The complete setting ID registry is implemented in `SettingLinks.kt` and covered by its registry tests.
