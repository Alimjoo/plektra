# Pastiera backup import device verification

Verified on Pixel 7a on 2026-10-08 with Plektra `26.10-nightly.08.204300Z`.

`PastieraBackupDeviceTest` ran two UI tests in 67.207 seconds: both passed. The tests open the production Advanced settings import screen, select a ZIP through Android DocumentsUI, import it twice and then reject a corrupt ZIP. The 0.86 test also selects adaptation to the Pixel in the device-change dialog.

The 0.85 fixture follows the export contract at `9a946b24150e4a2aa08ce16edee4404d7246def6`: typed preferences, `pastiera_prefs`, `force_minimal`, and no source-device metadata. The 0.86 fixture follows `02af786`: Pastierina names and device metadata. Test data is synthetic.

Assertions cover layout and mode settings, user dictionary files, layouts with umlauts, merged variations, repeated import, malformed-data rejection, retained speech configuration and credentials, and target-device adaptation. The tests compare the real application's preferences, encrypted credential storage, managed user files and selected IME before and after each case.

The UI uses isolated preferences and files. Dictionary broadcasts are captured and system subtype registration is suppressed to protect the running IME. Production post-restore triggers are covered separately by the backup integration tests.

Run the device suite after installing the Nightly and its Android test APK:

```sh
adb -s 37081JEHN11514 shell am instrument -w \
  -e class rocks.pkb.plektra.PastieraBackupDeviceTest \
  rocks.pkb.plektra.nightly.test/androidx.test.runner.AndroidJUnitRunner
```

Screenshots are saved under the application's external files directory in `backup-e2e/`. Test ZIPs, isolated preferences and temporary files are removed after each test. An actual user export and target-device verification on a Titan remain separate acceptance checks.
