# AI Helper — Android-Prototyp

Ein minimaler visueller Spielassistent für Android (min. Android 8 / API 26). Bildschirmaufnahme läuft lokal über Android MediaProjection. **Es ist kein Modell geladen.** Es gibt keine automatischen Eingaben, keine Accessibility-Steuerung und keine Roboflow-/Netzwerkübertragung.

## Funktionen und Grenzen

- Start/Stopp über die App und Stopp-Aktion in der laufenden Benachrichtigung.
- Android zeigt vor jeder Bildschirmaufnahme einen ausdrücklichen Systemdialog.
- Keine Audioaufnahme; Frames werden nicht auf Datenträger geschrieben und nicht über das Netzwerk übertragen.
- Eine verkleinerte Live-Vorschau wird lokal in der App angezeigt. Frames werden gedrosselt und alte Frames verworfen.
- Bei Displaygrößen-/Rotationsänderungen wird die virtuelle Anzeige angepasst und die ImageReader-Oberfläche erneuert. Während eines Rotationswechsels kann ein Frame verworfen werden.
- `Detector` und `DecisionEngine` sind getrennt; der Detector meldet ausdrücklich „Kein Modell geladen“ und die Entscheidungslogik darf nur Hinweise ausgeben.
- Die Erkennungsvisualisierung bleibt leer, solange kein echtes Modell eingebunden ist.
- MediaProjection muss vom Nutzer freigegeben werden. Android/Samsung können Hintergrunddienste zusätzlich einschränken.

## APK über GitHub Actions bauen

1. Öffne den Tab **Actions** im Repository.
2. Wähle **Android Debug APK**.
3. Tippe **Run workflow** und bestätige.
4. Nach erfolgreichem Lauf öffne den Workflow-Lauf und lade unter **Artifacts** die Datei **AI-Helper-debug-apk** herunter.
5. Entpacke das ZIP; darin liegt `app-debug.apk`.

Workflow: [Android Debug APK](../../actions/workflows/android-debug.yml)

## Installation am Galaxy A56 5G

1. Lade das Artifact-ZIP auf das Handy und entpacke es, oder lade es am PC herunter und übertrage die APK.
2. Öffne `app-debug.apk`. Falls Android fragt, erlaube deinem Datei-/Browserprogramm die Installation unbekannter Apps.
3. Starte **AI Helper**.
4. Tippe **Bildschirmfreigabe starten** und bestätige den Android-Systemdialog.
5. Die Vorschau wird in der App gezeigt. Über **Aufnahme stoppen** in der App oder **Stopp** in der Benachrichtigung beendest du die Aufnahme.

## Reproduzierbarer Build

- Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Gradle 8.9, JDK 17.
- Das offizielle Gradle Wrapper-JAR wird vom offiziellen Gradle-Wrapper-Task während des CI-Laufs erzeugt, falls es im Repository fehlt. Die Wrapper-Distribution wird über die festgelegte Gradle-8.9-URL geladen.
- GitHub Actions führt `./gradlew --no-daemon assembleDebug` aus und lädt die Debug-APK 14 Tage als Artifact hoch.
- Lokal: JDK 17 installieren, Gradle 8.9 einmal ausführen, dann `gradle wrapper --gradle-version 8.9` und `./gradlew assembleDebug`.

## Noch nicht verifiziert

- Der CI-Build muss tatsächlich laufen, bevor eine APK oder ein erfolgreiches Build-Ergebnis gemeldet werden kann.
- Es wurde kein Test auf einem physischen Galaxy A56 durchgeführt; ein CI-Build beweist weder Gerätekompatibilität noch Echtzeitfähigkeit.
