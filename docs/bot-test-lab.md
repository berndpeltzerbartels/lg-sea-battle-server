# Bot-Testlabor

Das Testlabor führt echte Backend-Runden ohne HTTP-Server aus. Derselbe Rastertext kann mit dem bisherigen Bot-Algorithmus oder einer eigenen Implementierung ausgeführt werden. Es verändert weder den laufenden Server noch die Auswahl des Produktionsalgorithmus.

## Schnell ausprobieren

Im Verzeichnis `server`:

```sh
./gradlew runBotScenario
```

Danach `build/bot-scenario/index.html` im Browser öffnen. Die Draufsicht läuft auch offline: Abspielen, Pause, Zeitschieber und Fahrwege. Daneben stehen Geschwindigkeit, Gasstellung, Ruder, Tauchtiefe und Zustand jedes Schiffs. Die Rasterkennung bleibt am Schiff sichtbar. Die Grafik ist eine schematische Draufsicht, keine Babylon-Ansicht.

Die Ausgangslage steht in `scenarios/island-corner.txt`. Zum Ändern diese Datei bearbeiten und erneut ausführen. Der Bericht enthält außerdem eine Kopie als `scenario.txt` und `trace.csv` mit jeder Runde, inklusive Zeitpunkt null.

```sh
./gradlew runBotScenario -Pscenario=scenarios/island-corner.txt -Pticks=600 -Poutput=build/mein-test
```

600 Takte sind 60 Spielsekunden. Die Simulation läuft ohne Wartezeiten, nicht in Echtzeit. Die lokale Spiel-App wird dabei nicht gestartet.

## Raster-Eingabe

Beliebig großes rechteckiges Raster, beispielsweise 20 × 20, in einer Schrift mit fester Breite. Eine Zelle enthält genau ein Zeichen:

- `.`: Wasser
- `+`: Land; das bisherige `*` funktioniert ebenfalls
- `1` bis `9`, `0`, danach Buchstaben: eindeutige Schiffserkennung
- `#`: Kommentar bis zum Zeilenende

Jede Kennung muss genau einmal auf der Karte stehen und genau eine Objektzeile besitzen. Unvollständige oder mehrdeutige Eingaben werden abgewiesen.

```text
scenario: zwei-boote
cell: 25
map:
..........
.1..++....
....++..2.
..........
objects:
1: boat light bot [heading: 90, speed: 0]
2: submarine dark human [heading: 270, speed: 6, depth: periscope]
```

`cell` ist der Abstand benachbarter Zellmittelpunkte in Welt-Einheiten. 25 entspricht ungefähr einer Torpedobootlänge (23,64). Die Karte liegt um den Weltursprung; rechts ist +x, unten +z. **0° fährt im Raster nach unten**, 90° nach rechts, 180° nach oben, 270° nach links. Intern verwendet die API Radiant. Es gibt keine unsichtbare Kartenrandmauer; Schiffe können das Raster verlassen.

Jedes Landzeichen erzeugt eine kleine runde Insel mit Radius 0,42 Zellbreiten. Zusammenhängende Pluszeichen sind keine durchgehenden Rechteckflächen. Eine Wasserzelle garantiert nicht, dass ein Schiffsrumpf hineinpasst. Überlappungen des Startrumpfs mit Land werden abgewiesen.

Objekte:

- Typ: `boat`, `submarine`, `plane` (bestehende deutsche Aliasse funktionieren ebenfalls).
- Team/Farbe: `light` oder `dark`.
- Kontrolle: `bot`, `human`, `scenario`.
- `heading`: Richtung in Grad; Standard 0.
- `speed`: tatsächliche Startgeschwindigkeit, wie die Knotenanzeige des Spiels; Standard 0. Danach gelten die normale Beschleunigung und die aus dem Wert abgeleitete Gasstufe des bestehenden Parsers.
- `depth`: `surface`, `periscope`, `submerged`; Standard `surface`. Im Testlabor beginnt ein U-Boot bereits auf dieser Tiefe.
- `height`: Flughöhe für Flugzeuge.

`human` markiert einen menschlichen Gegner für die Zielsuche, es wird kein Benutzer angemeldet. Das Schiff fährt mit seiner anfänglichen Gasstellung weiter. Ein U-Boot dieses Typs bleibt ohne weitere Eingabe auf seiner vorgegebenen Tiefe. Ein `bot` darf dagegen selbst Ziele und Tauchtiefe ändern. `scenario` fährt ohne eigene Bot-Entscheidungen weiter. Die Startgeschwindigkeit gilt exakt zu Zeitpunkt null; anschließend greifen die normalen Geschwindigkeitsgrenzen des Vehikels.

Die Spielinitialisierung über die vorhandene administrative Szenario-Seite behält ihr bisheriges Verhalten. Exakte Startgeschwindigkeiten und `depth` werden durch die neue **BotScenario**-Test-API angewendet. Skript-Waffenaktionen sind im Bot-Testlabor nicht erlaubt.

## Eigener Algorithmus

Die Schnittstelle lautet:

```java
public interface BotCalculation {
    void calculate(BotSituation situation, long tickId);
}
```

Eine Implementierung wird einmal je Spielsitzung erzeugt und einmal je aktiver Simulationsrunde aufgerufen. Die erste normale Runde hat Tick-ID 1; eine Runde dauert 0,1 Sekunden. Zustände und Zwischenergebnisse dürfen als Felder der Implementierung gehalten werden. Für jeden unabhängigen Durchlauf eine **neue Instanz** liefern.

`BotSituation` bietet:

- `ships()`: unveränderliche Schiff-Momentaufnahmen; erst bei Bedarf erzeugt. Enthält auch menschliche Schiffe und versteckte Gegner. Eigene Algorithmen sind selbst für ihre Wahrnehmungsregeln verantwortlich.
- `timeSeconds()`: aktuelle Simulationszeit.
- `isLand(x, z)` und `isHullBlocked(x, z, heading)`: vorhandene Land-/Rumpfprüfung verwenden.
- `steer(shipId, engineOrder, rudderDegrees)`: Gas und Ruder eines aktiven Bots setzen; die bisherigen Grenzwerte bleiben wirksam.
- `dive(shipId, depth)`: Tauchtiefe eines Bot-U-Boots setzen.
- `runStandard()`: bisherige Bot-Berechnung einmal ausführen, zum Beispiel vor eigenen gezielten Korrekturen.

`ships()` hält den Stand seines ersten Aufrufs in dieser Runde fest. Den Kontext nur innerhalb von `calculate()` verwenden und nicht für spätere Runden speichern. Befehle wirken direkt auf die Spielsituation; separate Ergebnisobjekte sind nicht nötig. Bewegung und Treffer werden anschließend vom bestehenden Backend berechnet. Eine reine Ersatzimplementierung mit `steer()` schießt nicht automatisch; für Versuche mit dem vorhandenen Kampfverhalten `runStandard()` verwenden.

Die Standardimplementierung verwendet weiter dieselben Wahrnehmungs-, Waffen- und Navigationsregeln. Ihre Einbindung kopiert weder Welt noch Flotten; die zusätzliche Kontextinstanz pro Runde enthält nur Referenzen. Momentaufnahmen entstehen nur, wenn ein eigener Algorithmus `ships()` verwendet.

Ein minimales, absichtlich dummes Beispiel liegt in `src/test/java/one/xis/seabattle/game/StraightAheadCalculation.java`. Es fährt alle Bots geradeaus mit voller Kraft:

```java
public final class StraightAheadCalculation implements BotCalculation {
    public void calculate(BotSituation situation, long tickId) {
        for (ShipSnapshot ship : situation.ships()) {
            if ("bot".equals(ship.controlledBy()) && "active".equals(ship.state())) {
                situation.steer(ship.id(), 8, 0);
            }
        }
    }
}
```

Eigene Klassen können auch in einem anderen Package stehen. Für den Aufruf über die Kommandozeile muss die Klasse öffentlich sein und einen öffentlichen parameterlosen Konstruktor haben:

```sh
./gradlew runBotScenario \
  -Palgorithm=one.xis.seabattle.game.StraightAheadCalculation \
  -Poutput=build/geradeaus
```

Standardalgorithmus und eigene Implementierung erhalten jeweils eine neu erzeugte Spielsitzung aus derselben Datei. Das Testlabor wählt die Implementierung explizit; die laufende Anwendung bleibt beim Standard.

## Ergebnisse automatisch prüfen

Die API lässt sich direkt in JUnit verwenden:

```java
BotScenario scenario = BotScenario.parse(Files.readString(Path.of("scenarios/island-corner.txt")));
BotTestRun run = scenario.run(BotCalculation::standard, 600);

assertTrue(run.displacement('1') > 50);
assertEquals("active", run.last().ship('1').state());
assertTrue(run.minimumSeparation('1', '2') > 5);

// Jede einzelne Runde ist zugänglich:
for (BotTestRun.Frame frame : run.frames()) {
    ShipSnapshot ship = frame.ship('1');
    // Beispielsweise unerwünschtes Rückwärtsfahren oder Ruderausschläge prüfen.
}
run.writeReport(Path.of("build/mein-test"));
```

`displacement` misst den Abstand vom Start zum Ende, nicht die zurückgelegte Strecke. `minimumSeparation` misst Rumpfmittelpunkte in den gespeicherten Taktzuständen, keine Kollisionsflächen. Die Momentaufnahmen enthalten gerundete Werte wie die bestehende Backend-Ausgabe. Bei Tod/Respawn zusätzlich `state` und `lifeId` prüfen; ein am Ende wieder aktives Schiff kann zwischenzeitlich versenkt worden sein. Eine im Verlauf fehlende Kennung führt bei `ship()` zu einem Fehler statt unbemerkt ein anderes Schiff auszuwählen.

Sinnvolle Tests sind: Engstelle verlassen, trotz Insel das Ziel erreichen, keine hektischen Gangwechsel, Mindestfortschritt in gegebener Zeit, Freund nicht rammen, Ziel nach Tauchen korrekt verlieren. Die sichtbare Fahrspur hilft bei der Diagnose, die Bedingungen im Test schützen anschließend vor Rückfällen.

## Performance vergleichen

```sh
./gradlew runBotScenario -Prounds=5 -Pticks=2000 -Poutput=build/standard
./gradlew runBotScenario -Prounds=5 -Pticks=2000 \
  -Palgorithm=one.xis.seabattle.game.StraightAheadCalculation -Poutput=build/variante
```

Zusätzlich zur Vorschau entstehen pro Algorithmus `benchmark.csv`-Dateien. Vor jeder Messreihe laufen zwei Aufwärmdurchläufe. Jede Runde startet mit frischer Spielsituation und frischer Algorithmusinstanz. Gemessen werden separat:

1. Bot-Entscheidung (`calculate`), einschließlich eigener Abfragen und Befehle.
2. Gesamte Backend-Runde, einschließlich Bot-Entscheidung, Bewegung und Kampfauflösung.

Ausgegeben werden Mittelwert sowie p95/p99 je Durchlauf; die Java-API enthält auch Median und Maximum. Vorschau, Ausgabe und Momentaufnahmen liegen außerhalb der gemessenen Zeit. Die Java-API unterstützt zudem `scenario.withThreads(2, true)` für die vorhandenen Parallelisierungsoptionen; nicht parallel im Sinn mehrerer Benchmark-Prozesse starten.

Mehrere Durchläufe vergleichen und die Reihenfolge wechseln. Änderungen an Verhalten und Weg verändern auch den Arbeitsaufwand. Ein Algorithmus, der stehen bleibt oder nicht schießt, kann schneller sein, ohne besser zu sein. Kampfszenarien mit zufälligem Respawn sind nicht vollständig deterministisch; gleiche Eingaben garantieren dann keinen identischen Verlauf. Für isolierte Planungstests zunächst einfache Szenarien ohne frühe Treffer verwenden. Diese Werte messen keine Browsergrafik, Netzwerkverzögerung oder Produktionslast.

## Zielauswahl unabhängig ersetzen

Zielauswahl und Bot-Berechnungsrunde sind zwei getrennte Schnittstellen. Du kannst nur eine davon ersetzen oder beide kombinieren. Solange deine Berechnungsrunde `runStandard()` verwendet, greift die konfigurierte Zielauswahl an den bisherigen Auswahlstellen.

```java
public interface BotTargetSelection {
    String select(BotTargetSituation situation, long tickId);
}
```

Eingaben:

- `observer()` / `observerId()`: das auswählende Schiff oder Flugzeug.
- `candidates()`: unveränderliche Momentaufnahmen der nach den **bestehenden** Wahrnehmungsregeln zulässigen Gegner. Anders als `BotSituation.ships()` ist das keine Liste aller Schiffe. Darin können auch die bisherigen strategisch erkannten menschlichen Überwassergegner außerhalb der normalen Sichtweite stehen.
- `currentTargetId()`: bisher gespeichertes Navigationsziel von Boot/U-Boot, eventuell `null`. Flugzeuge speichern dieses Navigationsziel nicht und liefern hier `null`. Die ID allein garantiert keinen aktuellen Kontakt.
- `timeSeconds()` und die übergebene `tickId`.
- `standardTargetId()`: auf Wunsch die bisherige Zielgewichtung verwenden. Das Ergebnis wird pro Auswahlaufruf nur einmal berechnet. Bei Booten bleibt die menschliche Zielpriorität erhalten; bei Flugzeugen deren Verteilung und Angriffshistorie.

Rückgabe: eine ID aus `candidates()` oder `null`, wenn kein Ziel gewählt werden soll. Eine fremde, befreundete oder gar nicht gelieferte Ziel-ID wird mit einem Fehler abgewiesen. Damit kann eine versehentlich falsche Auswahl nicht stillschweigend zu einem Angriff führen. Der Kontext ist nur während `select()` gültig. Eine Instanz darf eigene Zustände halten; die Factory erzeugt sie für jede neue Spielsitzung erneut.

Die Auswahl ändert die Gewichtung **innerhalb** der Kandidatenliste. Sie verändert weder Erkennungsreichweiten noch den bisherigen Takt: Boote/U-Boote wählen weiterhin versetzt alle 20 Takte, Flugzeuge derzeit jede Runde. Die bestehende kurze Verfolgung eines gerade verlorenen U-Boot-Kontakts kann eine Neuauswahl vorübergehend aussetzen. Kollisionsvermeidung, Tauchtaktik, Steuerung und unmittelbare Gefahren behalten ihren Vorrang. Das vermeidet, dass die neue Schnittstelle unbeabsichtigt die Spielregeln ändert.

Ein bewusst einfaches Beispiel findet sich in `src/test/java/one/xis/seabattle/game/NearestTargetSelection.java`: Es nimmt immer den nächstgelegenen zulässigen Gegner, ohne zusätzliche menschliche Zielpriorität. Du kannst dort eine eigene Bewertung einsetzen.

```java
BotScenario scenario = BotScenario.parse(script)
        .withTargetSelection(NearestTargetSelection::new);

// Vorhandene Navigation und Kampfentscheidungen, eigene Zielgewichtung:
BotTestRun run = scenario.run(BotCalculation::standard, 600);

assertTrue(run.targetDecisions().stream()
        .filter(d -> d.observerId().equals("light-S1"))
        .allMatch(d -> "dark-S2".equals(d.targetId())));
```

Über die Kommandozeile:

```sh
./gradlew runBotScenario \
  -PtargetSelection=one.xis.seabattle.game.NearestTargetSelection \
  -Poutput=build/naechstes-ziel -Prounds=5
```

`-PtargetSelection` wählt die Zielauswahl, `-Palgorithm` weiterhin die gesamte Bot-Berechnungsrunde. Beide sind standardmäßig `standard`. Ersetzt eine eigene Berechnungsrunde die Standardlogik vollständig, ruft sie die eingebaute Zielauswahl nicht automatisch auf.

Im Bericht zeigen gelbe gestrichelte Linien und die Spalte **Letzte Zielwahl** die zuletzt ausgewählte Ziel-ID. Das ist ein Auswahlprotokoll, kein garantierter aktueller Sichtkontakt. `targets.csv` protokolliert jeden tatsächlichen Aufruf mit Tick, Zeit, Beobachter, vorherigem Ziel, gewähltem Ziel, dessen Lebens-ID und Kandidatenzahl. `run.targetDecisions()` macht dieselben Entscheidungen für eigene JUnit-Prüfungen zugänglich. Ein fehlendes Ziel steht dort als `null` und in der CSV als leeres Feld.

`benchmark.csv` und `run.timing().targetSelection()` messen zusätzlich die Zeit **innerhalb der Auswahlimplementierung**. Kandidatensuche, Erkennungsprüfung und nachfolgende Navigation liegen außerhalb dieser Teilmessung und bleiben in der Bot-/Gesamtrundenmessung enthalten. Die Zielzeit wird je Simulationstakt summiert; Takte ohne Auswahl zählen mit null. `targetSelectionCalls` gibt zusätzlich die Anzahl tatsächlicher Auswahlaufrufe an. Die Teilmessung ist in der Bot-Zeit enthalten und darf nicht nochmals addiert werden. Bei vielen Takten ohne Auswahl können p95/p99 null sein. Das Auswahlprotokoll wird nur für den sichtbaren Probelauf gesammelt, nicht während der anschließenden Benchmark-Durchläufe.
