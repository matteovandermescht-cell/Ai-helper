# AI Helper — Android-Prototyp

Ein minimaler visueller Spielassistent für Android (min. Android 8 / API 26). Der erste Prototyp enthält Bildschirmaufnahme über Android MediaProjection und eine austauschbare Detector-Schnittstelle. **Es ist kein Modell geladen.** Es gibt keine automatischen Eingaben, keine Accessibility-Steuerung und keine Roboflow-/Netzwerkübertragung.

## Funktionen und Grenzen

- Start/Stopp über die App und Stopp-Aktion in der laufenden Benachrichtigung.
- Android zeigt vor jeder Bildschirmaufnahme einen ausdrücklichen Systemdialog.
- Keine Audioaufnahme; Bildframes werden nicht gespeichert und nicht übertragen.
- Die Capture-Pipeline holt das neueste verfügbare Frame und schließt es sofort. Erkennung ist noch nicht angeschlossen.
- `Detector` und `DecisionEngine` sind getrennt; die Entscheidungslogik darf nur Hinweise ausgeben.
- Der aktuelle Bildschirmstatus ist lokal. Es gibt noch keine Vorschau/Erkennungsvisualisierung und keine Rotation-Neuinitialisierung; diese Teile sind offene Prototyp-Aufgaben.
- MediaProjection muss vom Nutzer freigegeben werden. Android/Samsung können Hintergrunddienste zusätzlich einschränken.

## APK über GitHub Actions bauen

1. Öffne den Tab **Actions** im Repository.
2. Wähle **Android Debug APK**.
3. Tippe **Run workflow** und bestätige.
4. Nach erfolgreichem Lauf öffne den Workflow-Lauf und lade unter **Artifacts** die Datei **AI-Helper-debug-apk** herunter.
5. Entpacke das ZIP; darin liegt `app-debug.apk`.

Workflow: [Android Debug APK](../../actions/workflows/android-debug.yml)

## Installation am Galaxy A56 5G

1. Lade das Artifact-ZIP direkt auf das Handy und entpacke es, oder lade es am PC herunter und übertrage die APK.
2. Öffne `app-debug.apk`. Falls Android fragt, erlaube deinem verwendeten Datei-/Browserprogramm die Installation unbekannter Apps. Erlaube das nur für die Quelle, der du vertraust.
3. Starte **AI Helper**.
4. Tippe **Bildschirmfreigabe starten** und bestätige den Android-Systemdialog.
5. Android zeigt eine laufende Benachrichtigung. Über **Aufnahme stoppen** in der App oder **Stopp** in der Benachrichtigung beendest du die Aufnahme.

## Lokal bauen

Voraussetzungen: JDK 17 und Gradle 8.9.

Im Repository sind Wrapper-Skripte und Wrapper-Properties enthalten. Das binäre `gradle-wrapper.jar` ist in diesem ersten Commit noch nicht eingecheckt. Mit installiertem Gradle 8.9 einmal im Projektordner ausführen:

```sh
gradle wrapper --gradle-version 8.9
./gradlew assembleDebug
```

Unter Windows PowerShell anschließend `./gradlew.bat assembleDebug` verwenden. GitHub Actions generiert den Wrapper bei Bedarf automatisch und baut dann die Debug-APK.

## Offene Punkte

- Bildvorschau und optionales Overlay für Erkennungsrahmen
- Rotation/Größenänderung während einer laufenden Aufnahme robust neu initialisieren
- Modell laden und echte Detektionen erst dann aktivieren
- Tests auf realem Galaxy A56; erfolgreicher CI-Build wäre kein Gerätetest oder Echtzeitnachweis
