# TV Debloat

A lightweight Android home launcher for TVs and phones. Its monochrome interface uses restrained red accents and adapts to phone portrait and landscape layouts.

## Features

- **Android TV:** Pinned app dock, selectable app library, remote-friendly focus states, and Android app widgets.
- **Android phones:** A compact debug dashboard with local device, battery, storage, and network diagnostics.
- **Termux tools:** An optional scan checks a fixed list of command names in Termux. It does not accept or run user-entered shell commands.
- **App widgets:** Add, configure, and remove widgets provided by installed Android apps.
- **App controls:** Launch apps, pin favorites, and choose which apps appear in the library.

## Requirements

- Android 8.0 (API 26) or newer
- JDK 17
- Android SDK Platform 34

## Build

The repository does not include a Gradle wrapper. With Gradle and the Android SDK installed and configured:

```sh
gradle assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

## Optional Termux setup

The phone dashboard can check for a predefined set of tools (`bash`, `curl`, `git`, `python`, `python3`, `ssh`, `tar`, `jq`, `top`, `pkg`, and `adb`). The scan uses Termux's `RUN_COMMAND` interface (Termux 0.109 or newer). To enable it:

1. Install Termux and open it once.
2. Set `allow-external-apps=true` in `~/.termux/termux.properties` and reload Termux.
3. In Android Settings, grant this launcher the **Run commands in Termux** permission.
4. Tap **Scan** in the Termux Tools section.

The scan runs only after the user taps the button. It executes a fixed, read-only `command -v` check on the device; there is no arbitrary command field. Without Termux or the required opt-ins, the rest of the launcher remains usable.

## Widgets

Tap **+ Widget** and choose a widget in Android's system picker. Some widgets open their own configuration screen before appearing in the launcher. Use **Remove** on a widget card to remove it.
