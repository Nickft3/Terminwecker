# Terminwecker

**English** | [Deutsch](README.de.md)

[![Build](https://github.com/NicklasThiele/Terminwecker/actions/workflows/build.yml/badge.svg)](https://github.com/NicklasThiele/Terminwecker/actions/workflows/build.yml)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)

Android app that sets **real alarms** for events in your calendar – full screen, sound, vibration
and snooze, not just a notification.

## Features

- **An alarm, not a reminder:** rings like an alarm clock, even on the lock screen, with snooze and dismiss.
- **For one event, a series or a title:** e.g. “every last Friday of the month at 3:25 PM” when the event
  is a recurring series in your calendar, or every event called “Dentist”.
- **Flexible timing:** X minutes before the start, or at a fixed time on the day of the event, the day
  before or two days before.
- **A familiar calendar:** week, 3 days, day or a searchable event list; events with an alarm are marked.
- **Reschedules itself:** after every alarm, after a reboot, on time changes and when events are moved.
- **Private:** reads the calendar synced to your phone (e.g. Google Calendar) locally – no login,
  no internet permission, no ads, no tracking.
- **English and German:** follows your phone’s language; on Android 13+ you can also pick a language
  just for Terminwecker.

Requirements: Android 8.0 or newer and a calendar that is synced to the phone.

## Download and installation

1. On your phone, download the latest APK:
   **[Terminwecker.apk](https://github.com/NicklasThiele/Terminwecker/releases/latest/download/Terminwecker.apk)**
   (all versions under [Releases](https://github.com/NicklasThiele/Terminwecker/releases)).
2. Tap the downloaded file. Android asks once whether your browser or “Files” may install apps: allow it.
3. Play Protect may warn you because the app doesn’t come from the Play Store. Depending on your phone
   and Android version, you’ll see either “Install anyway” or something like “Scan”; once the scan is
   finished, the app can be installed.
4. Open the app and allow **Calendar** and **Notifications**.
5. In the **Settings** tab, turn all red items green, especially **Battery optimization off** and
   **Full-screen alarm**. On Samsung, Xiaomi, Huawei & co., also set battery usage to “Unrestricted” in
   the app settings (details per manufacturer: [dontkillmyapp.com](https://dontkillmyapp.com)).
6. Tap **Test alarm in 10 seconds**, lock the screen and check that it rings.

### Updating

Simply install the new APK from the releases over the old one (“Update”) – **don’t** uninstall first.
Your alarms and settings are kept and rescheduled right after the update.

This only works with APKs from the official releases, because Android accepts updates only with the
same signature. A self-built APK can only be installed after uninstalling the release version (which
deletes your alarms) – and vice versa.

## Usage

- **Calendar:** switch the view at the top right between **Week**, **3 days**, **Day** and **Event list**.
  - Time grid: swipe to change pages, tap the month name to jump to a date, tap the calendar icon with
    the number to go to today, tap a weekday to open the day view. Events with an alarm show ⏰ before
    the title.
  - Event list: all upcoming events (400 days), search by title or location (magnifier at the top right).
  - Tap an event to open “Set alarm”.
- **Applies to**
  - *Only this event:* follows the event even if it is moved.
  - *Whole series:* all occurrences, including individually moved ones.
  - *All events with this title:* also new events and new series with the same name
    (the most robust option if Google splits a series while you edit it).
- **When:** X minutes before the start, or a fixed time on the day / the day before / 2 days before.
- **Alarms tab:** all alarms with their next ring time, on/off, “Skip next event”, and
  “Keyword alarm” to create an alarm without picking a specific event.
- **Alarm screen:** full screen even on the lock screen, **Snooze** or **Dismiss**.
  After the configured ringing time it stops and leaves a “Missed alarm” notification.
- **Language:** the app appears in German if your phone is set to German, otherwise in English.
  On Android 13+ you can choose it separately under *Settings → Apps → Terminwecker → Language*.
  Dates and times follow your phone’s format (including the 12-hour clock).

## Permissions

| Permission | What for |
|---|---|
| Read calendar | Show events and calculate alarms (read only, never changes anything) |
| Notifications | Alarm notification and “Missed alarm” |
| Alarms & reminders (exact alarms) | Ring right on the minute |
| Full-screen notifications | Alarm screen on top of the lock screen |
| Ignore battery optimization | Reliable in the background, even after days without opening the app |
| Run at startup | Reschedule alarms after a reboot |
| Vibration, foreground service, wake lock | Ring with sound and vibration, turn the screen on |

The app has no internet permission.

## Contributing

Bug reports, ideas and pull requests are welcome, in English or German. In short: fork the repository,
make your change on a separate branch and open a pull request – every pull request is reviewed before
it is merged. GitHub Actions builds every pull request automatically, and the debug APK is attached to
the run. Details (in German) are in [CONTRIBUTING.md](CONTRIBUTING.md).

New texts go into both `app/src/main/res/values/strings.xml` (English) and `values-de/strings.xml`
(German). Want to add a language? Add a `values-xx/strings.xml` folder – translations are very welcome.

## Building

Requirements: JDK 17 and the Android SDK (platform 35). The easiest way is to open the folder in
**Android Studio** and click “Run”.

On the command line:

```bash
./gradlew assembleDebug
```

The APK ends up in `app/build/outputs/apk/debug/app-debug.apk`.

On Windows the build can fail because of long paths. In that case, use a short build directory:

```powershell
.\gradlew.bat assembleDebug "-Pterminwecker.buildRoot=C:/tmp/tw"
```

## How it works

- Reads the calendar locally via `CalendarContract`; series are expanded via `Instances`.
- For each alarm only the next occurrence is scheduled as an exact alarm (`setAlarmClock`);
  it is rescheduled after ringing, after a reboot, on time changes and on every calendar change.
- Kotlin + Jetpack Compose, minSdk 26 (Android 8), targetSdk 35 (Android 15).

## License

Terminwecker is free software under the [GNU General Public License v3.0](LICENSE).
You may use, modify and share the app; modified versions must also be published with their source
code under the GPL-3.0.
