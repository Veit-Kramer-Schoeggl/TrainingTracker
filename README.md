# Pull-ups – Klimmzug Tracker (Android)

Android-App zum Erfassen von Klimmzügen mit Auswertung. Design und Verhalten folgen dem
HTML-Prototyp `klimmzug-tracker-7.html` (externe Vorgabe, liegt nicht im Repo).

## Funktionen

- Sätze eintragen (Datum, Wiederholungen, Notiz); Tage bearbeiten und löschen (mit „Rückgängig“)
- Statistiken wie im Prototyp: Gesamt, Bestleistung, Ø/Woche, Ø/Tag, Streaks, Pausen, Monate, Top 3
- Verlauf als Balken- oder Liniendiagramm (Alle / 3M / 1M / 2W), Tages- und Wochenansicht
- Excel-Export (Blätter „Tage“, „Wochen“ und „Sätze“) und Excel-Import (auch Exporte des Prototyps)
- Drei Farbthemen
- Selbst-Update über GitHub Releases, ohne Play Store

## Technik

| | |
|---|---|
| Sprache / UI | Kotlin, Jetpack Compose (Material 3) |
| Trainingsdaten | Room (SQLite), Datei `training.db` im privaten App-Speicher |
| Einstellungen | DataStore (Farbthema) |
| Android | minSdk 34 (Android 14), targetSdk 37 |

```
app/src/main/java/io/github/veitkramerschoeggl/trainingtracker/
  data/     Room-Datenbank, Repositories, Einstellungen
  domain/   Statistik (1:1 aus dem Prototyp), deutsche Datums-/Zahlenformate
  excel/    XLSX lesen/schreiben (ohne externe Bibliothek), Import/Export
  update/   Update-Prüfung, Download, Installation
  ui/       Compose-Oberfläche, ViewModel
```

## Warum die Daten erhalten bleiben

- Die Daten liegen in einer SQLite-Datenbank im privaten Speicher der App. Android behält sie bei
  jedem Update, solange **Paketname, Signaturschlüssel** gleich bleiben und der **versionCode steigt**.
  Nur Deinstallieren löscht sie.
- Zusätzliche Sicherung: Android Auto Backup (Google-Konto, auch beim Gerätewechsel) und der
  Excel-Export, der sich wieder importieren lässt.
- Debug-Builds heißen `…trainingtracker.debug` und sind eine eigene App. Ein Debug-Install aus
  Android Studio kann die echte App und ihre Daten daher nie überschreiben.
- **Schemaänderungen** an der Datenbank: `version` in `TrainingDatabase` erhöhen, eine `Migration`
  in `TrainingDatabase.MIGRATIONS` ergänzen und `MigrationTest` erweitern. Die Schema-Dateien in
  `app/schemas/` gehören ins Repo. Niemals `fallbackToDestructiveMigration()` verwenden, das würde
  alle Trainingsdaten löschen.

## Updates ohne Play Store

1. Bei jedem Start liest die App `update.json` aus dem neuesten GitHub Release
   (`releases/latest/download/update.json`).
2. Ist der `versionCode` höher, erscheint oben „Update verfügbar“ mit den Release-Notes.
3. „Aktualisieren“ lädt das APK, prüft SHA-256, Paketname und Signaturschlüssel und übergibt es an
   den Android-Paketinstaller. Die App wird dabei beendet. Beim nächsten Öffnen meldet sie
   „Aktualisiert auf Version …“.

Auf dem Emulator getestet (1.0.0 → 1.0.1 → 1.0.2 → 1.0.3, Daten jeweils erhalten):

- **Erstes Update:** Android fragt einmalig nach „Apps aus dieser Quelle zulassen“ (die App erklärt
  das vorher und macht danach automatisch weiter) und dann „Diese App aktualisieren?“.
- **Weitere Updates:** Die App ist jetzt selbst als Installationsquelle eingetragen, die
  Android-Rückfrage entfällt (`UPDATE_PACKAGES_WITHOUT_USER_ACTION`).
- **Google Play Protect** kann bei jedem neuen APK „App-Scan empfohlen“ anzeigen, weil es die App
  nicht aus dem Play Store kennt. Dann „App scannen“ und danach „Installieren“ tippen.

**Erstinstallation:** auf dem Handy die Release-Seite des Repos öffnen, das APK herunterladen und
installieren.

## Einmalig: Signaturschlüssel einrichten

```bash
scripts/setup-signing.sh
```

Das Skript erzeugt den Release-Schlüssel (`~/.android/trainingtracker/release.jks`) und
`keystore.properties` (beide per `.gitignore` ausgeschlossen) und hinterlegt beides als
GitHub-Secrets für den Release-Workflow.
**Schlüssel und Passwort sicher aufbewahren** (Passwortmanager und eine Offline-Kopie). Ohne sie
lassen sich keine Updates mehr für bereits installierte Apps veröffentlichen.

## Neue Version veröffentlichen

```bash
scripts/release.sh 1.1.0 "Was ist neu (erscheint in der App)"
```

Das Skript setzt `appVersionName` in `gradle.properties`, führt die Tests aus, committet, taggt
`v1.1.0` und pusht. GitHub Actions (`.github/workflows/release.yml`) baut dann das signierte APK und
veröffentlicht den Release mit APK und `update.json`. Den `versionCode` leitet der Build aus der
Version ab (1.2.3 → 10203), deshalb ist jede neue Version automatisch höher.

## Entwicklung

Voraussetzung ist JDK 25 für den Gradle-Daemon (z. B. die JBR von Android Studio).

```bash
./gradlew assembleDebug               # Debug-APK
./gradlew testDebugUnitTest           # Unit-Tests (Statistik gegen den Prototyp, Excel, Updates)
./gradlew connectedDebugAndroidTest   # Datenbank- und Migrationstests auf Gerät/Emulator
```

**Self-Update lokal testen** (Emulator; Debug-Builds dürfen dafür http zu `10.0.2.2` verwenden):

```bash
URL=http://10.0.2.2:8765/update.json
./gradlew assembleDebug -PappVersionName=1.0.1 -PdebugUpdateManifestUrl=$URL
# APK + passende update.json (versionCode 10001, apkUrl, sha256) in einen Ordner legen und dort:
python3 -m http.server 8765
./gradlew installDebug -PdebugUpdateManifestUrl=$URL   # Version 1.0.0 installieren und öffnen
```

## Abweichungen vom Prototyp

- „Längster Streak“ zeigt den Zeitraum als „14.09.–20.09.“, Top 3 zeigt das volle Datum. Der
  Prototyp schneidet dort den Text ab („Mo., 14.–So., 20.“ bzw. nur „Mo.“).
- Wird beim Bearbeiten das Datum auf einen Tag mit vorhandenen Einträgen gelegt, wird der Wert dort
  addiert. Der Prototyp überschreibt diesen Tag ohne Nachfrage.
- Löschen lässt sich rückgängig machen.
- Neu hinzugekommen sind der Excel-Import, der Update-Hinweis und die Fußzeile mit Version und
  Update-Suche.

## Lizenzhinweise

App-Icon: 💪 aus Noto Color Emoji (SIL Open Font License 1.1). Kalender-Symbol: Material Icons
(Apache License 2.0).
