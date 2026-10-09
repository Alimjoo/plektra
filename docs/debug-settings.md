# Debug settings over ADB

Debug builds include a shell-only settings receiver. Release builds do not include it. The receiver requires Android's `DUMP` permission, so ordinary apps cannot use it.

List known preference keys:

```sh
python3 scripts/debug-settings.py --device <serial> list
```

Apply a JSON file, optionally selecting a built-in theme for hardware and software:

```sh
python3 scripts/debug-settings.py --device <serial> set --file /absolute/settings.json --theme 'Nord'
```

Example settings file:

```json
{
  "suggestions_enabled": true,
  "status_bar_variations_visible": false,
  "modifier_indicator_mode": "bottom_strip,menu_bar",
  "keyboard_bar_slim_suggestions": false,
  "keyboard_bar_slim_actions": false,
  "keyboard_bar_slim_bottom": true
}
```

Boolean, integer and string values use normal JSON types. `null` removes an override. Floats, longs and string sets use an explicit type:

```json
{
  "accessibility_suggestions_announcement_delay_ms": {"type": "long", "value": 250}
}
```

Use `list` to find the actual key for the setting you want. Existing preferences require their existing type. A failed request is returned as an error before preference changes are committed. The adapter checks names and storage types; Android preference values should still follow the ranges and options supported by the settings UI.

The first change to each key saves its original value or absence in the app's private `files/showcase-preferences.json`. Further changes retain that original value. Theme selection also saves the theme and assignment keys. Restore the touched keys after a capture or test:

```sh
python3 scripts/debug-settings.py --device <serial> restore
```

Restore removes the completed backup. An app migration can recreate an initially absent default after preferences reload; explicit original values remain preserved. Finish one configuration session before starting another; an interrupted session can be restored with the same command. Saved backups may contain user snippets and custom settings, so they remain in private app storage.

The optional `--package` selects another installed Plektra debug variant. The receiver and the synthetic native Notes activity live exclusively in `app/src/debug`. The Notes activity is a capture host; the visible keyboard, suggestions, symbol pages, emoji and clipboard are the actual Plektra input method.

## Import speech credentials

Fill in `OPENROUTER_API_KEY` and `OPENAI_API_KEY` in the gitignored root `.env`. Keep the file readable only by your user. The remaining fields select the device, Nightly package, provider, models and pause length.

```sh
python3 scripts/setup-speech.py
```

The script reads `.env` without executing it. It sends the keys over standard input into the debug app's private cache, then invokes `SpeechSetupReceiver`. The receiver encrypts both keys with Android Keystore, checks that it can read them back, and applies the selected speech settings. Neither command arguments nor output contain the keys. The receiver and script remove the transfer file after the attempt.

This replaces the saved keys for both providers. The receiver exists only in debug builds and requires Android's `DUMP` permission. It does not validate account access or send audio to either provider.

OpenRouter speech requests always require ZDR. Opening the model selector reloads the model catalog and active ZDR endpoints. Before uploading a recording, Plektra checks again, retains an eligible selected model or selects an available replacement. If discovery fails or no eligible model remains, it does not upload the recording. OpenRouter also enforces ZDR on the inference request.

## Live transcription

Select OpenAI and GPT Live Transcribe in Voice input settings. Plektra streams 24 kHz mono PCM through a dedicated Realtime transcription session and sends the keyboard language as a hint. Partial text appears in the active editor's composing span. The final transcript replaces that span once.

The default silence interval is three seconds. Tap the microphone to finish earlier. Typing, moving the cursor or switching fields ends live input and preserves the text already visible. Each recording owns its connection; cancelled audio is not replayed. Audio and transcripts are not saved. Usage totals use the recording duration and provider-reported token counts; missing prices remain unknown.
