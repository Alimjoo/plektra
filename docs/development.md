# Development

## Requirements

- JDK 17
- Android SDK for compile SDK 36
- Android platform tools for device installation

## Common checks

```sh
./gradlew :app:testStableDebugUnitTest
./gradlew :app:testNightlyDebugUnitTest
./gradlew :app:assemblePlayStableDebug :app:assemblePlayNightlyDebug
```

Build and install a Nightly debug APK with the project wrapper:

```sh
./scripts/build-nightly-debug.sh 26.09 --install
```

The Kotlin namespace is `rocks.pkb.plektra`. The four app identities and their update behaviour are defined in `app/build.gradle.kts`.

## Uyghur layout

The bundled `uyghur` layout uses Arabic-script Uyghur (`ug`, `ug_CN`). It is selectable in Settings → Input Languages without a dictionary, and does not translate the app interface. Automatic capitalization is disabled for layouts with no cased letters so it cannot select an extra letter at the beginning of a sentence. Manual Shift remains available.

The normal letter positions follow the [Microsoft Uyghur computer layout](https://learn.microsoft.com/en-us/globalization/keyboards/kbdughr1.html):

```text
Q=چ W=ۋ E=ې R=ر T=ت Y=ي U=ۇ I=ڭ O=و P=پ
A=ھ S=س D=د F=ا G=ە H=ى J=ق K=ك L=ل
Z=ز X=ش C=غ V=ۈ B=ب N=ن M=م
```

| Key | Shift output |
| --- | --- |
| D | ژ |
| F | ف |
| G | گ |
| H | خ |
| J | ؛ |
| K | ۆ |
| L | لا (two Unicode letters) |
| X | ج |

Compact-keyboard additions use **Shift+M → ئ**, **Shift+V → ؟**, **Shift+N → ،**, **Shift+J → ؛**. The custom Shift+J shortcut moves `ج` from its standard PC position to **Shift+X**. Type `ئ` explicitly; this layout does not insert it automatically before vowels. Repeated presses enter repeated letters; there is no multi-tap cycling.

On a full keyboard, `/` produces `ئ`, Shift+`/` produces `؟`, comma produces `،`, and semicolon produces `؛` (Shift gives `:`). Period, hyphen and Shift+1 retain `.`, `-` and `!`.

Q25 Alt mappings remain device-specific: Alt+R → `3`, Alt+H → `:`, Alt+B → `!`, Alt+I → `-`, and Alt+M → `.`. Use Shift for the Uyghur additions above. Text is committed as ordinary Unicode in logical order, allowing the target editor to handle joining and right-to-left display.
