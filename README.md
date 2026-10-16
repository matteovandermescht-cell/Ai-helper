# AI Helper — Android-Prototyp

Lokaler visueller Spielassistent für Android (min. Android 8 / API 26). Die App verwendet Android MediaProjection und benötigt zusätzlich die vom Nutzer erteilte Berechtigung „Über anderen Apps einblenden“ für die schwebende Anzeige.

## Tatsächlicher Funktionsstand

- Bildschirmfreigabe wird ausdrücklich über den Android-Systemdialog angefragt.
- Vor dem Erstellen der Overlay-Fenster wird die vom Nutzer erteilte Systemberechtigung für schwebende Fenster geprüft.
- Transparente Vollbild-Zeichenfläche mit `TYPE_APPLICATION_OVERLAY` und `FLAG_NOT_TOUCHABLE`; sie soll Touch-Eingaben nicht abfangen. Die separate Bedienleiste ist verschiebbar, einklappbar und bedienbar. Das Schließen der Leiste beendet jetzt die Aufnahme vollständig.
- Bedienleiste zeigt Aufnahme-/Modell-/Steuerungsstatus, Erkennung AN/AUS, Auto-Play AUS, Debug und roten STOPP-Knopf. Auto-Play ist nicht implementiert und kann nicht eingeschaltet werden.
- Aufnahme verarbeitet aktuelle `ImageReader.acquireLatestImage()`-Frames, maximal fünf pro Sekunde; die Detector-Eingabe wird auf maximal 480 Pixel Kantenlänge skaliert.
- Detector-Aufrufdauer wird mit `elapsedRealtimeNanos()` gemessen; FPS basiert auf tatsächlich verarbeiteten Frames. Ergebnisalter wird aktualisiert; nach 1000 ms werden Boxen ausgeblendet. `DecisionEngine` bestätigt Zustandswechsel erst nach drei konsistenten Beobachtungen; veraltete Beobachtungen liefern keine Aktionsvorschläge.
- Die Detector-Schnittstelle definiert Koordinaten als Pixelkoordinaten des an `detect()` übergebenen Bildes. `CoordinateTransform` bildet Boxen mit zentriertem Fit und Letterboxing ab, statt unterschiedliche Seitenverhältnisse zu verzerren. Unit-Tests prüfen diese Mathematik; die reale Ausrichtung mit Referenzpunkten auf dem Galaxy A56 ist noch nicht verifiziert.
- Keine Audioaufnahme und keine absichtliche Bilddatei-Speicherung. Netzwerkzugriff ist ausschließlich für den ausdrücklich manuell gestarteten einmaligen Roboflow-URL-Test ergänzt; die Bildschirmframes werden dabei nicht übertragen.
- STOPP beendet MediaProjection, ImageReader, VirtualDisplay, Worker-Thread und Overlay-Fenster.

## Blocker: echtes Modell fehlt

Im Repository ist **kein trainiertes Modell** enthalten. `NoModelDetector` meldet „Kein Modell geladen“ und gibt immer eine leere Liste zurück. Daher gibt es keine echten Erkennungen und keine Bounding Boxes. Die Overlay-Schicht ist implementiert, aber derzeit keine KI-Erkennung.

Für die lokale Integration wird das tatsächlich exportierte Modell benötigt, beispielsweise als konkretes TFLite- oder ONNX-Artefakt, einschließlich Eingabegröße, Farb-/Normalisierungsregeln, Klassenreihenfolge, Ausgabetensorformat und NMS-Regeln. Die angegebenen Roboflow-Workspace-/Projekt-/Workflow-IDs allein bestätigen keinen Modellzugriff. Ein manueller Roboflow-Cloud-Diagnosetest ist jetzt in der App integriert: API-Schlüssel und eine öffentlich erreichbare Bild-URL eingeben und genau einmal „Roboflow-Test starten“ drücken. Der Schlüssel wird nicht in der App gespeichert oder von ihr protokolliert und nach dem Versuch aus dem Eingabefeld gelöscht. Die App zeigt HTTP-Status, Antwortfelder und geparste Boxkoordinaten an, ohne einen möglichen `output_image`-Base64-Block in die Oberfläche zu kopieren. Der Client nutzt den bereitgestellten Workflow-Slug `brawl-stars-entscheidungsprototyp-1792181498461`. Es gibt keine automatische Wiederholung, keine periodischen Anfragen und keinen Upload des aktuellen Bildschirmbilds. Der Test verwendet nur die eingegebene Bild-URL. Die Integration ist erst bestätigt, wenn sie auf dem Gerät mit einem gültigen Schlüssel und einer erreichbaren Bild-URL erfolgreich antwortet. Die zuvor beobachtete Cloud-Laufzeit von etwa 11,8 Sekunden wäre nicht echtzeitfähig.

Vorgesehene Klassen: `player`, `enemy`, `bush`, `poison_zone`, `play_button`, `exit_button`, `play_again_button`, `defeated_indicator`. Scores sollen später unverändert aus dem Modell stammen und sind keine kalibrierten Wahrscheinlichkeiten. Unsichtbare Gegner sind nicht zuverlässig erkennbar; eine `poison_zone`-Box ist keine exakte Giftzonengrenze.

## Overlay-Rückkopplung, Skalierung und Rotation

Die Zeichenfläche nutzt ein separates Android-Overlay-Fenster mit `FLAG_NOT_TOUCHABLE`. Ob Overlay-/Systemfenster in einem konkreten MediaProjection-Modus mit aufgenommen werden, kann je nach Android-Version, Freigabeart und Gerät variieren; ein Ausschluss ist nicht garantiert. Vor einem Modelltest muss mit einem sichtbaren Testmuster geprüft werden, ob das Overlay im aufgenommenen Bild erscheint. Falls ja, muss der entsprechende Bereich maskiert oder die Aufnahmeart angepasst werden.

Die virtuelle Anzeige wird bei Displaygrößen-/Rotationsänderungen neu konfiguriert. Die Bildvorschau und die spätere Modellkoordinatenabbildung verwenden die jeweilige Aufnahmegröße. Eine Prüfung anhand sichtbarer Referenzpunkte auf echter Hardware ist noch nicht erfolgt. Schwarze Ränder, die bereits im aufgenommenen Vollbild enthalten sind, bleiben Teil des Koordinatensystems; ein zugeschnittenes Modellbild benötigt zusätzliche Crop-Offsets, die derzeit nicht implementiert sind.

## Touch-/Accessibility-Status

Es gibt keinen AccessibilityService und keine automatische Touch-Steuerung. Android stellt `AccessibilityService.dispatchGesture()` für einen ausdrücklich eingerichteten AccessibilityService mit vom Nutzer erteilter Freigabe bereit. Ein erfolgreicher API-Aufruf würde nicht beweisen, dass das Spiel die Geste akzeptiert. Isolierter Gestentest, manuelle Kalibrierung, Vordergrund-App-Prüfung, Abbruchlogik und gleichzeitige Gesten sind nicht implementiert. Nach Neustart bleibt Auto-Play AUS.

## Manueller Roboflow-Cloud-Test

1. App öffnen und zum Abschnitt „Roboflow-Cloud-Test“ scrollen.
2. Einen gültigen Test-API-Schlüssel direkt in das temporäre Passwortfeld eingeben (nicht in GitHub oder Chat posten).
3. Eine öffentlich erreichbare Bild-URL eingeben.
4. Einmal auf „Roboflow-Test starten (1 Anfrage)“ tippen.
5. HTTP-Status, erkannte Ausgabefelder und geparste Boxkoordinaten prüfen. `401`/`403` deutet auf Authentifizierung/Berechtigung hin. Die App zeigt eine sichere Zusammenfassung statt der kompletten Antwort, damit große Bilddaten aus `output_image` nicht die Oberfläche füllen. Eine leere Vorhersageliste wird nicht als Erkennung ausgegeben.

Die lokale Bildschirmaufnahme und der Cloud-Test sind getrennt. Der URL-Test ist keine Live-Erkennung und zeichnet seine Cloud-Ergebnisse nicht auf das Spiel-Overlay.

## Build und Geräteprüfung

Workflow: [Android Debug APK](../../actions/workflows/android-debug.yml)

Nach erfolgreichem GitHub-Actions-Lauf: Workflow öffnen → **Artifacts** → `AI-Helper-debug-apk` herunterladen → ZIP entpacken → `app-debug.apk` installieren.

Ein CI-Build beweist nicht, dass die App auf dem Samsung Galaxy A56 5G (SM-A566B/DS) getestet wurde. Gerätetest, Overlay-Touch-Passthrough, Rotations-/Referenzpunkttest und Prüfung der Overlay-Rückkopplung stehen noch aus.


## Zusätzliche Tests und genaue Grenzen

- Unit-Tests: `CoordinateTransformTest` testet Seitenverhältnis/Letterboxing und ungültige Dimensionen. `DecisionEngineTest` testet drei aufeinanderfolgende Beobachtungen, dass `player` allein keine laufende Runde beweist, dass `exit_button` allein keine Niederlage beweist und dass veraltete Beobachtungen den Vorschlag sperren.
- Der Roboflow-Parser akzeptiert übliche JSON-Verschachtelungen mit einem `predictions`-Array und behandelt `x`/`y` als Boxmitte. **Die tatsächliche Antwort dieses veröffentlichten Workflows wurde in dieser Umgebung noch nicht live abgerufen**, weil dafür ein gültiger Schlüssel und ein erreichbares Testbild benötigt werden. Daher ist der Parser auf echter Workflow-Antwort noch unbestätigt; ein HTTP-200 allein wäre noch kein Beweis, dass die Vorhersagen korrekt geparst wurden.
- Cloud-Boxen stammen aus dem manuell angegebenen Bild und werden deshalb nur als Koordinaten im Diagnosetext angezeigt. Sie werden nicht über ein anderes Live-Spielbild gelegt. Live-Cloud-Inferenz, automatische Cloud-Anfragen und Echtzeitnutzung sind nicht implementiert.
- Auto-Play ist sichtbar gesperrt und standardmäßig aus. Es gibt keinen `AccessibilityService`, keine Gestenausführung und keine verifizierte Vordergrund-App-Prüfung. Eine eigene Gestentestfläche und eine vom Nutzer explizit freigegebene Accessibility-Implementierung bleiben offen.
- `SEARCHING` und `LOADING` sind als Zustände vorgesehen, werden ohne geeignete Modellklassen/Beobachtungen aber nicht geraten. Die aktuelle Zustandslogik gibt bei unklarer Evidenz `UNCLEAR` zurück.
