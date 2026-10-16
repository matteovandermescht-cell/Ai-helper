# AI Helper — Android-Prototyp

Lokaler visueller Spielassistent für Android (min. Android 8 / API 26). Die App verwendet Android MediaProjection und benötigt zusätzlich die vom Nutzer erteilte Berechtigung „Über anderen Apps einblenden“ für die schwebende Anzeige.

## Tatsächlicher Funktionsstand

- Bildschirmfreigabe wird ausdrücklich über den Android-Systemdialog angefragt.
- Vor dem Erstellen der Overlay-Fenster wird die vom Nutzer erteilte Systemberechtigung für schwebende Fenster geprüft.
- Transparente Vollbild-Zeichenfläche mit `TYPE_APPLICATION_OVERLAY` und `FLAG_NOT_TOUCHABLE`; sie soll Touch-Eingaben nicht abfangen. Die separate Bedienleiste ist verschiebbar, einklappbar und bedienbar.
- Bedienleiste zeigt Aufnahme-/Modell-/Steuerungsstatus, Erkennung AN/AUS, Auto-Play AUS, Debug und roten STOPP-Knopf. Auto-Play ist nicht implementiert und kann nicht eingeschaltet werden.
- Aufnahme verarbeitet aktuelle `ImageReader.acquireLatestImage()`-Frames, maximal fünf pro Sekunde; die Detector-Eingabe wird auf maximal 480 Pixel Kantenlänge skaliert.
- Detector-Aufrufdauer wird mit `elapsedRealtimeNanos()` gemessen; FPS basiert auf tatsächlich verarbeiteten Frames. Ergebnisalter wird aktualisiert; nach 1000 ms werden Boxen ausgeblendet.
- Die Detector-Schnittstelle definiert Koordinaten als Pixelkoordinaten des an `detect()` übergebenen Bildes. Da aktuell kein Modell geladen ist, gibt es keine Boxen und die Transformation kann nicht mit echten Modellreferenzpunkten auf dem Galaxy A56 verifiziert werden.
- Keine Audioaufnahme, keine absichtliche Bilddatei-Speicherung und keine Netzwerkübertragung.
- STOPP beendet MediaProjection, ImageReader, VirtualDisplay, Worker-Thread und Overlay-Fenster.

## Blocker: echtes Modell fehlt

Im Repository ist **kein trainiertes Modell** enthalten. `NoModelDetector` meldet „Kein Modell geladen“ und gibt immer eine leere Liste zurück. Daher gibt es keine echten Erkennungen und keine Bounding Boxes. Die Overlay-Schicht ist implementiert, aber derzeit keine KI-Erkennung.

Für die lokale Integration wird das tatsächlich exportierte Modell benötigt, beispielsweise als konkretes TFLite- oder ONNX-Artefakt, einschließlich Eingabegröße, Farb-/Normalisierungsregeln, Klassenreihenfolge, Ausgabetensorformat und NMS-Regeln. Die angegebenen Roboflow-Workspace-/Projekt-/Workflow-IDs allein bestätigen keinen Modellzugriff. In dieser Änderung wurde kein Roboflow-Endpunkt aufgerufen, keine API-Zugangsdaten hinterlegt und kein Cloud-Job gestartet. Ein optionaler Cloud-Diagnosetest ist nicht integriert. Die zuvor erwähnte Cloud-Laufzeit von etwa 11,8 Sekunden wäre nicht echtzeitfähig.

Vorgesehene Klassen: `player`, `enemy`, `bush`, `poison_zone`, `play_button`, `exit_button`, `play_again_button`, `defeated_indicator`. Scores sollen später unverändert aus dem Modell stammen und sind keine kalibrierten Wahrscheinlichkeiten. Unsichtbare Gegner sind nicht zuverlässig erkennbar; eine `poison_zone`-Box ist keine exakte Giftzonengrenze.

## Overlay-Rückkopplung, Skalierung und Rotation

Die Zeichenfläche nutzt ein separates Android-Overlay-Fenster mit `FLAG_NOT_TOUCHABLE`. Ob Overlay-/Systemfenster in einem konkreten MediaProjection-Modus mit aufgenommen werden, kann je nach Android-Version, Freigabeart und Gerät variieren; ein Ausschluss ist nicht garantiert. Vor einem Modelltest muss mit einem sichtbaren Testmuster geprüft werden, ob das Overlay im aufgenommenen Bild erscheint. Falls ja, muss der entsprechende Bereich maskiert oder die Aufnahmeart angepasst werden.

Die virtuelle Anzeige wird bei Displaygrößen-/Rotationsänderungen neu konfiguriert. Die Bildvorschau und die spätere Modellkoordinatenabbildung verwenden die jeweilige Aufnahmegröße. Eine Prüfung anhand sichtbarer Referenzpunkte auf echter Hardware ist noch nicht erfolgt. Schwarze Ränder, die bereits im aufgenommenen Vollbild enthalten sind, bleiben Teil des Koordinatensystems; ein zugeschnittenes Modellbild benötigt zusätzliche Crop-Offsets, die derzeit nicht implementiert sind.

## Touch-/Accessibility-Status

Es gibt keinen AccessibilityService und keine automatische Touch-Steuerung. Android stellt `AccessibilityService.dispatchGesture()` für einen ausdrücklich eingerichteten AccessibilityService mit vom Nutzer erteilter Freigabe bereit. Ein erfolgreicher API-Aufruf würde nicht beweisen, dass das Spiel die Geste akzeptiert. Isolierter Gestentest, manuelle Kalibrierung, Vordergrund-App-Prüfung, Abbruchlogik und gleichzeitige Gesten sind nicht implementiert. Nach Neustart bleibt Auto-Play AUS.

## Build und Geräteprüfung

Workflow: [Android Debug APK](../../actions/workflows/android-debug.yml)

Nach erfolgreichem GitHub-Actions-Lauf: Workflow öffnen → **Artifacts** → `AI-Helper-debug-apk` herunterladen → ZIP entpacken → `app-debug.apk` installieren.

Ein CI-Build beweist nicht, dass die App auf dem Samsung Galaxy A56 5G (SM-A566B/DS) getestet wurde. Gerätetest, Overlay-Touch-Passthrough, Rotations-/Referenzpunkttest und Prüfung der Overlay-Rückkopplung stehen noch aus.
