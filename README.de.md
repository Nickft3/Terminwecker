# Terminwecker

[English](README.md) | **Deutsch**

[![Build](https://github.com/Nickft3/Terminwecker/actions/workflows/build.yml/badge.svg)](https://github.com/Nickft3/Terminwecker/actions/workflows/build.yml)
[![Lizenz: GPL-3.0](https://img.shields.io/badge/Lizenz-GPL--3.0-blue.svg)](LICENSE)

Android-App, die für Termine aus deinem Kalender **echte Wecker** stellt – mit Vollbild, Ton,
Vibration und Schlummern, nicht bloß eine Benachrichtigung.

## Was die App kann

- **Wecker statt Erinnerung:** Klingelt wie ein Wecker, auch auf dem Sperrbildschirm,
  mit Schlummern und Aus.
- **Für einen Termin, eine Serie oder einen Titel:** z. B. „jeden letzten Freitag im Monat um 15:25 Uhr“,
  wenn der Termin als Serie im Kalender steht, oder alle Termine, die „Zahnarzt“ heißen.
- **Flexibler Zeitpunkt:** X Minuten vor Beginn oder zu einer festen Uhrzeit am Termintag, am Vortag
  oder zwei Tage vorher.
- **Kalender wie gewohnt:** Woche, 3 Tage, Tag oder durchsuchbare Terminliste; Termine mit Wecker
  sind markiert.
- **Plant selbst neu:** nach jedem Klingeln, nach einem Neustart, bei Zeitumstellung und wenn Termine
  verschoben werden.
- **Privat:** liest den auf dem Handy synchronisierten Kalender (z. B. Google-Kalender) lokal –
  kein Login, keine Internet-Berechtigung, keine Werbung, kein Tracking.
- **Deutsch und Englisch:** folgt automatisch der Sprache des Handys; ab Android 13 lässt sich die
  Sprache auch nur für Terminwecker umstellen.

Voraussetzung: Android 8.0 oder neuer und ein Kalender, der auf dem Handy synchronisiert wird.

## Download und Installation

1. Auf dem Handy die neueste APK herunterladen:
   **[Terminwecker.apk](https://github.com/Nickft3/Terminwecker/releases/latest/download/Terminwecker.apk)**
   (alle Versionen unter [Releases](https://github.com/Nickft3/Terminwecker/releases)).
2. Die heruntergeladene Datei antippen. Android fragt einmalig, ob der Browser bzw. „Dateien“
   Apps installieren darf: erlauben.
3. Play Protect warnt eventuell, weil die App nicht aus dem Play Store kommt: Hier steht je nach Smartphone
   und Android-Version entweder „Trotzdem installieren“ oder so etwas wie „Scannen“; nach abgeschlossenem
   Scan kann die App installiert werden.
4. App öffnen und **Kalender** sowie **Benachrichtigungen** erlauben.
5. Im Tab **Einstellungen** alle roten Punkte auf grün bringen, vor allem **Akku-Optimierung aus** und
   **Vollbild zulassen**. Bei Samsung, Xiaomi, Huawei & Co. zusätzlich in den App-Einstellungen unter Akku
   „Nicht eingeschränkt“ wählen (Details je Hersteller: [dontkillmyapp.com](https://dontkillmyapp.com)).
6. **Testwecker in 10 Sekunden** drücken, Bildschirm sperren und prüfen, dass er klingelt.

### Update

Die neue APK aus den Releases einfach über die alte installieren („Aktualisieren“), **nicht** vorher
deinstallieren. Wecker und Einstellungen bleiben erhalten und werden direkt nach dem Update neu geplant.

Das klappt nur mit APKs aus den offiziellen Releases, weil Android Updates nur mit derselben Signatur
annimmt. Eine selbst gebaute APK lässt sich erst nach dem Deinstallieren der Release-Version
installieren (dabei gehen die gestellten Wecker verloren) – und umgekehrt.

## Bedienung

- **Kalender:** Ansicht oben rechts umschalten zwischen **Woche**, **3 Tage**, **Tag** und **Terminliste**.
  - Zeitraster: Wischen blättert, Tippen auf den Monatsnamen springt zu einem Datum, das Kalender-Symbol
    mit der Zahl springt zu heute, Tippen auf einen Wochentag öffnet die Tagesansicht.
    Termine mit Wecker tragen ein ⏰ vor dem Titel.
  - Terminliste: alle kommenden Termine (400 Tage), Suche nach Titel oder Ort (Lupe oben rechts).
  - Tippen auf einen Termin öffnet „Wecker stellen“.
- **Gilt für**
  - *Nur diesen Termin:* folgt dem Termin auch, wenn er verschoben wird.
  - *Ganze Terminserie:* alle Wiederholungen, auch einzeln verschobene.
  - *Alle Termine mit diesem Titel:* auch neue Termine und neue Serien mit gleichem Namen
    (am robustesten, falls Google eine Serie beim Bearbeiten aufteilt).
- **Wann:** X Minuten vor Beginn oder feste Uhrzeit am Termintag / am Vortag / 2 Tage vorher.
- **Wecker-Tab:** alle Wecker mit nächstem Klingeltermin, an/aus, „Nächsten Termin überspringen“,
  „Stichwort-Wecker“ ohne konkreten Termin anlegen.
- **Weckerbildschirm:** Vollbild auch auf dem Sperrbildschirm, **Schlummern** oder **Aus**.
  Nach der eingestellten Klingeldauer hört er auf und hinterlässt „Verpasster Wecker“.
- **Sprache:** Die App erscheint auf Deutsch, wenn das Handy auf Deutsch eingestellt ist, sonst auf
  Englisch. Ab Android 13 unter *Einstellungen → Apps → Terminwecker → Sprache* separat wählbar.
  Datum und Uhrzeit folgen dem Format des Handys (auch 12-Stunden-Uhr).

## Berechtigungen

| Berechtigung | Wofür |
|---|---|
| Kalender lesen | Termine anzeigen und Wecker berechnen (nur lesen, nie ändern) |
| Benachrichtigungen | Weckerbenachrichtigung und „Verpasster Wecker“ |
| Wecker und Erinnerungen (exakte Alarme) | Auf die Minute pünktlich klingeln |
| Vollbild-Benachrichtigungen | Weckerbildschirm über dem Sperrbildschirm |
| Akku-Optimierung ignorieren | Zuverlässig im Hintergrund, auch nach Tagen ohne App-Start |
| Beim Gerätestart ausführen | Wecker nach einem Neustart neu planen |
| Vibration, Vordergrunddienst, Wakelock | Klingeln mit Ton und Vibration, Bildschirm einschalten |

Eine Internet-Berechtigung hat die App nicht.

## Mitmachen

Fehlerberichte, Ideen und Pull Requests sind willkommen. Wie das abläuft, steht in
[CONTRIBUTING.md](CONTRIBUTING.md).
Kurz: Repository forken, Änderung auf einem eigenen Branch machen,
Pull Request stellen – jeder Pull Request wird vor dem Übernehmen geprüft.

## Selbst bauen

Voraussetzungen: JDK 17 und Android SDK (Plattform 35). Am einfachsten den Ordner in
**Android Studio** öffnen und auf „Run“ klicken.

Auf der Kommandozeile:

```bash
./gradlew assembleDebug
```

Die APK liegt danach unter `app/build/outputs/apk/debug/app-debug.apk`.

Unter Windows kann der Build an zu langen Pfaden scheitern. Dann ein kurzes Build-Verzeichnis angeben:

```powershell
.\gradlew.bat assembleDebug "-Pterminwecker.buildRoot=C:/tmp/tw"
```

## Technik

- Liest den Kalender lokal über `CalendarContract`; Serien werden über `Instances` aufgelöst.
- Pro Wecker wird immer nur der nächste Termin als exakter Alarm (`setAlarmClock`) geplant;
  nach dem Klingeln, nach Neustart, Zeitumstellung und jeder Kalenderänderung wird neu geplant.
- Kotlin + Jetpack Compose, minSdk 26 (Android 8), targetSdk 35 (Android 15).
- Texte liegen in `app/src/main/res/values/strings.xml` (Englisch, Standard) und
  `values-de/strings.xml` (Deutsch). Weitere Sprachen: einfach einen Ordner `values-xx` ergänzen.

## Lizenz

Terminwecker ist freie Software unter der [GNU General Public License v3.0](LICENSE).
Du darfst die App nutzen, verändern und weitergeben; veränderte Versionen müssen ebenfalls unter
der GPL-3.0 mit Quellcode veröffentlicht werden.
