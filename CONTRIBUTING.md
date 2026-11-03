# Mitmachen bei Terminwecker

Danke, dass du helfen möchtest! Beiträge auf Deutsch oder Englisch sind gleichermaßen willkommen.
*Contributions in English are welcome too.*

## Fehler melden und Ideen vorschlagen

Lege ein [Issue](../../issues) an. Bei Fehlern rund ums Klingeln helfen diese Angaben sehr:
Handy-Modell, Android-Version, ob die Akku-Optimierung für die App aus ist, und wie der Wecker
eingestellt war (einzelner Termin, Serie oder Titel; Minuten vorher oder feste Uhrzeit).

## Code beitragen

1. Repository forken und einen Branch anlegen, z. B. `fix/schlummern-doppelt` oder `feature/monatsansicht`.
2. Änderung machen und testen (siehe unten).
3. Pull Request gegen `main` stellen und kurz beschreiben, was sich ändert und wie du getestet hast.
4. Der Maintainer prüft den Pull Request, stellt ggf. Rückfragen und übernimmt ihn dann.

Für größere Änderungen (neue Ansichten, andere Weck-Logik) am besten vorher ein Issue aufmachen,
damit wir die Richtung abstimmen können, bevor du viel Zeit investierst.

## Bauen und testen

- JDK 17 und Android SDK (Plattform 35); am einfachsten mit Android Studio.
- `./gradlew assembleDebug` muss ohne Fehler durchlaufen. Das prüft GitHub Actions bei jedem
  Pull Request automatisch; die gebaute Debug-APK hängt als Artefakt am Lauf.
- Wenn du an der Weck-Logik (`alarm/`) etwas änderst: auf einem Gerät oder Emulator mit
  **gesperrtem Bildschirm** und **beendeter App** prüfen, dass der Wecker pünktlich klingelt.
  Der Knopf „Testwecker in 10 Sekunden“ in den Einstellungen hilft dabei.

## Stil

- Kotlin im offiziellen Code-Stil (`kotlin.code.style=official`), UI mit Jetpack Compose.
- Texte in der App sind auf Deutsch.
- Kommentare kurz und auf Deutsch, wie im übrigen Code.
- Neue Bibliotheken nur, wenn es wirklich nötig ist. Die App soll ohne Internet-Berechtigung,
  Werbung und Tracking auskommen.

## Lizenz

Mit einem Pull Request erklärst du dich einverstanden, dass dein Beitrag unter der
[GPL-3.0](LICENSE) veröffentlicht wird, wie der Rest des Projekts.
