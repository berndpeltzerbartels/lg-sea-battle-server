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
    void calculate(BotSituation situation, long tickId, Map<String, BotGoal> goals);
}
```

Eine Bewegungsimplementierung wird einmal je Spielsitzung erzeugt und einmal je aktiver Simulationsrunde aufgerufen, nachdem die Ziele aller Bots vorbereitet sind. Die erste normale Runde hat Tick-ID 1; eine Runde dauert 0,1 Sekunden. Zustände und Zwischenergebnisse dürfen als Felder der Implementierung gehalten werden. Für jeden unabhängigen Durchlauf eine **neue Instanz** liefern.

`BotSituation` bietet:

- `vehicles()`: direkte Referenzen auf die aktiven `Vehicle`-Instanzen mit ihren echten Unterklassen; keine Kopien und keine Rundung. Enthält auch menschliche Schiffe und versteckte Gegner. Eigene Algorithmen sind selbst für ihre Wahrnehmungsregeln verantwortlich.
- `timeSeconds()`: aktuelle Simulationszeit.
- `isLand(x, z)` und `isHullBlocked(x, z, heading)`: vorhandene Land-/Rumpfprüfung verwenden.
- `steer(shipId, engineOrder, rudderDegrees)`: Gas und Ruder eines aktiven Bots setzen; die bisherigen Grenzwerte bleiben wirksam.
- `dive(shipId, depth)`: Tauchtiefe eines Bot-U-Boots setzen.
- `runStandard(goals)`: vorhandene Navigation und Kampfsteuerung mit den übergebenen Zielen ausführen. Dabei wird die Zielauswahl nicht erneut aufgerufen. `runStandard()` verwendet dieselbe vorbereitete Sammlung als Kurzform.

`vehicles()` liefert die echten, veränderlichen Serverobjekte. Nur eigene Fahrzeuge über die Steuerbefehle verändern; fremde Fahrzeuge lediglich lesen. Den Kontext nur innerhalb von `calculate()` verwenden und nicht für spätere Runden speichern. Befehle wirken direkt auf die Spielsituation; separate Ergebnisobjekte sind nicht nötig. Bewegung und Treffer werden anschließend vom bestehenden Backend berechnet. Eine reine Ersatzimplementierung mit `steer()` schießt nicht automatisch; für Versuche mit dem vorhandenen Kampfverhalten `runStandard(goals)` verwenden.

Die Standardimplementierung verwendet weiter dieselben Wahrnehmungs-, Waffen- und Navigationsregeln. Ihre Einbindung kopiert weder Welt noch Flotten; die zusätzliche Kontextinstanz pro Runde enthält nur Referenzen. Fahrzeug-Snapshots entstehen ausschließlich für die Ausgabe; auch eigene Algorithmen rechnen direkt mit `Vehicle`.

Ein minimales, absichtlich dummes Beispiel liegt in `src/main/java/one/xis/seabattle/game/StraightAheadCalculation.java`. Es fährt alle Bots geradeaus mit voller Kraft:

```java
public final class StraightAheadCalculation implements BotCalculation {
    public void calculate(BotSituation situation, long tickId, Map<String, BotGoal> goals) {
        for (Vehicle vehicle : situation.vehicles()) {
            if ("bot".equals(vehicle.controlledBy()) && "active".equals(vehicle.state())) {
                situation.steer(vehicle, 8, 0);
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
    VehicleSnapshot ship = frame.ship('1');
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

1. Bewegungs-/Steuerberechnung (`calculate`), einschließlich eigener Abfragen und Befehle.
2. Zielauswahl und Zielpunkte (`select` / `goal`), getrennt von der Bewegungsberechnung.
3. Gesamte Backend-Runde, einschließlich Zielvorbereitung, Steuerung, Bewegung und Kampfauflösung.

Ausgegeben werden Mittelwert sowie p95/p99 je Durchlauf; die Java-API enthält auch Median und Maximum. Vorschau, Ausgabe und Momentaufnahmen liegen außerhalb der gemessenen Zeit. Die Java-API unterstützt zudem `scenario.withThreads(2, true)` für die vorhandenen Parallelisierungsoptionen; nicht parallel im Sinn mehrerer Benchmark-Prozesse starten.

Mehrere Durchläufe vergleichen und die Reihenfolge wechseln. Änderungen an Verhalten und Weg verändern auch den Arbeitsaufwand. Ein Algorithmus, der stehen bleibt oder nicht schießt, kann schneller sein, ohne besser zu sein. Kampfszenarien mit zufälligem Respawn sind nicht vollständig deterministisch; gleiche Eingaben garantieren dann keinen identischen Verlauf. Für isolierte Planungstests zunächst einfache Szenarien ohne frühe Treffer verwenden. Diese Werte messen keine Browsergrafik, Netzwerkverzögerung oder Produktionslast.

## Zielauswahl unabhängig ersetzen

Zielauswahl und Bot-Berechnungsrunde sind zwei getrennte Schnittstellen. Du kannst nur eine davon ersetzen oder beide kombinieren. Die Zielvorbereitung läuft zuerst, unabhängig davon, welche Bewegungsimplementierung verwendet wird. Sie führt die konfigurierte Zielauswahl an den bisherigen Auswahlstellen aus und stellt eine vollständige Zielsammlung für den folgenden Bewegungsaufruf her.

```java
public interface BotTargetSelection {
    Vehicle select(BotTargetSituation situation, long tickId);
    // Zusätzlich überschreibbar: goal(situation, contact, advanceSearch).
}
```

Eingaben:

- `observer()` / `observerId()`: das auswählende Schiff oder Flugzeug.
- `candidates()`: direkte Referenzen auf die tatsächlichen Fahrzeuginstanzen der nach den **bestehenden** Wahrnehmungsregeln zulässigen Gegner. Anders als `BotSituation.vehicles()` ist das keine Liste aller Schiffe. Darin können auch die bisherigen strategisch erkannten menschlichen Überwassergegner außerhalb der normalen Sichtweite stehen.
- `currentTargetId()`: bisher gespeichertes Navigationsziel von Boot/U-Boot, eventuell `null`. Flugzeuge speichern dieses Navigationsziel nicht und liefern hier `null`. Die ID allein garantiert keinen aktuellen Kontakt.
- `timeSeconds()` und die übergebene `tickId`.
- `standardTarget()`: auf Wunsch die bisherige Zielgewichtung verwenden. Das Ergebnis wird pro Auswahlaufruf nur einmal berechnet. Bei Booten bleibt die menschliche Zielpriorität erhalten; bei Flugzeugen deren Verteilung und Angriffshistorie.

Rückgabe: eine Fahrzeuginstanz aus `candidates()` oder `null`, wenn kein Ziel gewählt werden soll. Ein fremdes, befreundetes oder gar nicht geliefertes Fahrzeug wird mit einem Fehler abgewiesen. Während einer noch gültigen Angriffsfortsetzung ist auch das bisherige gemerkte Ziel zulässig. Damit kann eine versehentlich falsche Auswahl nicht stillschweigend zu einem Angriff führen. Der Kontext ist nur während `select()` gültig. Eine Instanz darf eigene Zustände halten; die Factory erzeugt sie für jede neue Spielsitzung erneut.

Die Auswahl ändert die Gewichtung **innerhalb** der Kandidatenliste. Sie verändert weder Erkennungsreichweiten noch den bisherigen Takt: Boote/U-Boote wählen weiterhin versetzt alle 20 Takte, Flugzeuge derzeit jede Runde. Die Standardimplementierung kann bei der Auswahl die noch gültige Erinnerung an einen verlorenen U-Boot-Kontakt beibehalten; eine eigene Implementierung wird trotzdem aufgerufen. Kollisionsvermeidung, Tauchtaktik und Steuerung gehören zur Bewegungsimplementierung. Der Standard behält ihre Prioritäten; eine eigene Bewegungsimplementierung ersetzt diese Entscheidungen vollständig.

Ein bewusst einfaches Beispiel findet sich in `src/main/java/one/xis/seabattle/game/NearestTargetSelection.java`: Es nimmt immer den nächstgelegenen zulässigen Gegner, ohne zusätzliche menschliche Zielpriorität. Du kannst dort eine eigene Bewertung einsetzen.

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

`-PtargetSelection` wählt die Zielauswahl, `-Palgorithm` die Bewegungs-/Steuerberechnung. Beide sind standardmäßig `standard`. Auch eine vollständig eigene Bewegungsberechnung bekommt die vorher berechnete Zielsammlung. Sie muss weder `runStandard()` noch die Zielauswahl selbst aufrufen.

Im Bericht zeigen gelbe gestrichelte Linien und die Spalte **Letzte Zielwahl** die zuletzt ausgewählte Ziel-ID. Das ist ein Auswahlprotokoll, kein garantierter aktueller Sichtkontakt. `targets.csv` protokolliert jeden tatsächlichen Aufruf mit Tick, Zeit, Beobachter, vorherigem Ziel, gewähltem Ziel, dessen Lebens-ID und Kandidatenzahl. `run.targetDecisions()` macht dieselben Entscheidungen für eigene JUnit-Prüfungen zugänglich. Ein fehlendes Ziel steht dort als `null` und in der CSV als leeres Feld.

`benchmark.csv` und `run.timing().targetSelection()` messen zusätzlich die Zeit **innerhalb der Auswahlimplementierung (`select` und `goal`)**. Kandidatensuche, Erkennungsprüfung und nachfolgende Navigation liegen außerhalb dieser Teilmessung und bleiben in der Gesamtrundenmessung enthalten; Navigation liegt zusätzlich in der Bewegungs-/Steuerzeit. Die Zielzeit wird je Simulationstakt summiert; `goal` läuft auch zwischen den Auswahl-Takten, damit Zielpunkte aktuell bleiben. `targetSelectionCalls` gibt zusätzlich die Anzahl tatsächlicher Auswahlaufrufe an. Die Zielgewichtung läuft vor `calculate()` und ist daher nicht in dessen Bewegungs-/Steuerzeit enthalten. Die Gesamtrundenmessung enthält beide Phasen sowie Vorbereitung und Physik.  Das Auswahlprotokoll wird nur für den sichtbaren Probelauf gesammelt, nicht während der anschließenden Benchmark-Durchläufe.


## Explizite Übergabe der Zielsammlung an die Bewegung

Der Ablauf jeder Runde ist:

1. Wahrnehmung/Zielvorbereitung für alle Bots ausführen. Neue Gegner werden nur in den bisherigen Auswahl-Takten gewählt.
2. Die Zielsammlung für diesen Tick unveränderlich bereitstellen.
3. `calculate(situation, tickId, goals)` aufrufen.
4. Steuerbefehle, Bewegung und Treffer mit dem bestehenden Backend verarbeiten.

`goals` hat den Typ `Map<String, BotGoal>`. Der Schlüssel ist die **eigene Schiffskennung**, der Wert ihr Navigationsziel. Die Map enthält alle aktiven Bots (auch Flugzeuge), keine menschlich gesteuerten Schiffe. Die Werte bleiben unveränderlich; zwischen den Auswahl-Takten folgen gültige sichtbare Ziele ihren aktuellen Positionen. Die Gegenstände werden nicht erneut gesucht, nur die bereits gehaltenen Kontakte geprüft. Außerhalb des normalen Simulationsbetriebs, beispielsweise im bisherigen reinen Modell-Sandbox-Modus, kann die Sammlung leer sein.

`BotGoal` enthält:

- `kind()`: `ENEMY`, `LAST_KNOWN_COURSE`, `SEARCH`, `RETAINED_COURSE`, `PATROL` oder `NONE`.
- `target()`: direkte Fahrzeugreferenz für einen beobachteten Gegner, sonst `null`. `BotGoal.enemy(vehicle)` erstellt dieses Ziel ohne DTO-Mapping.
- `targetShipId()` und `targetLifeId()`: Kennung/Lebens-ID des Gegners, soweit vorhanden. Bei reinen Punkten ist die ID `null` und die Lebens-ID `-1`.
- `x()` / `z()`: konkreter Zielpunkt für die Fahrtrichtung. Bei verlorenem Kontakt sind das ausschließlich erlaubte Erinnerungs-/Suchpunkte, keine heimlich weitergereichten Live-Koordinaten.

`RETAINED_COURSE` beschreibt den kurz beibehaltenen bisherigen Kurs durch einen vorausliegenden Punkt. `PATROL` ist derzeit die Kartenmitte. `NONE` bedeutet beispielsweise ein Flugzeug ohne Angriffsziel: Der eigene Algorithmus kann dann stoppen oder patrouillieren. Diese Ziele enthalten keine vorgeschriebenen Ruderausschläge oder Geschwindigkeiten; diese berechnet die Bewegungsimplementierung selbst.

Das Beispiel `GoalFollowingCalculation.java` verwendet genau diese Übergabe: Es berechnet für jedes eigene Schiff aus seiner Position und dem zugeordneten Zielpunkt eine Richtung und setzt Gas/Ruder. Es verwendet weder die Standardnavigation noch eine eigene Zielsuche. Es lässt bewusst Hindernisvermeidung und Waffen weg und eignet sich als kleine Vorlage zum Weiterbearbeiten.

```java
public void calculate(BotSituation situation, long tickId, Map<String, BotGoal> goals) {
    for (Vehicle vehicle : situation.vehicles()) {
        BotGoal goal = goals.get(vehicle.id());
        if (goal == null || goal.kind() == BotGoal.Kind.NONE) continue;
        double heading = Math.atan2(goal.x() - vehicle.position().x(), goal.z() - vehicle.position().z());
        // Eigene Navigation: Hindernisse prüfen, Kursfehler bestimmen, Gas/Ruder setzen.
    }
}
```

```sh
./gradlew runBotScenario \
  -PtargetSelection=one.xis.seabattle.game.NearestTargetSelection \
  -Palgorithm=one.xis.seabattle.game.GoalFollowingCalculation \
  -Pscenario=scenarios/target-choice.txt -Poutput=build/eigene-fahrt
```


## Fahrzeugmodell

`Vehicle` enthält die gemeinsame Identität, Position, Bewegung und den Lebenszyklus.
Die konkreten Typen sind `TorpedoBoat`, `Submarine` und `ScoutPlane`.
`SurfaceVehicle` bündelt die Navigation und Flak der beiden Wasserfahrzeuge.
Nur das Torpedoboot enthält Kanonenzustand, nur das U-Boot Tauchtiefe und
U-Boot-Botzustand, nur das Flugzeug vertikale Geschwindigkeit und Flughöhenziel.

Ein Typwechsel ersetzt das Objekt in der Flotte; Kennung, Lebens-ID und
Spielerzuordnung bleiben erhalten. Alte Zielreferenzen werden ungültig.
Die Bot-Schnittstellen erhalten echte `Vehicle`-Instanzen. `VehicleSnapshot` ist ausschließlich ein Ausgabedatensatz.
Dieser flache Transportdatensatz behält die bisherigen Netzwerkfelder bei,
damit der Browser unverändert funktioniert. Er ist kein veränderliches Fahrzeugmodell.


## Austausch im normalen Spiel

`StandardBotTargetSelection` enthält die bisherige Zielgewichtung und die Entscheidung
zwischen direktem Kontakt, U-Boot-Jagd, gemerktem Angriffskurs und Patrouille.
`StandardBotCalculation` enthält Navigation, taktisches Tauchen und die bisherigen
Angriffsentscheidungen. Sie rufen keine versteckte Standardberechnung in `GameSession` auf.
Die Sitzung stellt Wahrnehmung, Zeitplanung, Physik und Waffeneffekte bereit.

Ohne Konfiguration werden diese beiden Standardklassen verwendet. Eigene Klassen gehören
unter `src/main/java` (oder in eine Bibliothek auf dem Laufzeit-Classpath) und benötigen
einen öffentlichen parameterlosen Konstruktor. Beim normalen Start oder Neustart eines
Spiels wird für jedes Interface eine neue Instanz erstellt. Die Auswahl erfolgt nur beim
Erzeugen der Sitzung, niemals per Reflection innerhalb eines Ticks.

Als JVM-Optionen, beispielsweise in der IDE:

```text
-DseaBattle.botTargetSelection=one.xis.seabattle.game.NearestTargetSelection
-DseaBattle.botCalculation=one.xis.seabattle.game.GoalFollowingCalculation
```

Oder beim selbst gestarteten lokalen Spiel:

```sh
./gradlew runSeaBattleXis \
  -DseaBattle.botTargetSelection=one.xis.seabattle.game.NearestTargetSelection \
  -DseaBattle.botCalculation=one.xis.seabattle.game.GoalFollowingCalculation
```

Beide Einstellungen sind unabhängig. Eine ungültige Klasse erzeugt einen klaren Fehler;
es gibt keinen stillen Rückfall auf den Standard. Die Beispielklassen sind auch im
normalen Anwendungs-Classpath enthalten; sie sind bewusst einfache Versuchsalgorithmen.

## U-Boot-Jagd und Wahrnehmung

`BotTargetSituation` bietet ohne Fahrzeug-Mapping:

- `observer()` und `candidates()`: konkrete Fahrzeuge, etwa `candidate instanceof Submarine`.
- `vehicles()` / `nearbyVehicles(range)`: dieselben aktiven Serverfahrzeuge bzw. Nachbarn über den vorhandenen räumlichen Index, ohne Kopien oder DTO-Mapping. Hier muss die eigene Strategie Wahrnehmung und Team prüfen.
- `isVisible(vehicle)`: Sichtkontakt aus Sicht dieses Beobachters, einschließlich erworbener Sehrohrverfolgung.
- `hasSonarContact(submarine)`: Sonarkontakt getrennt von optischer Sichtbarkeit.
- `isStrategicContact(vehicle)`: der bisherige weiter entfernte menschliche Überwasserkontakt.
- `currentTarget()`: bisheriges Ziel; die Referenz allein bedeutet keinen aktuellen Sichtkontakt.
- `submarineSearch()`: laufende Jagd, Gebietszentrum, Ablaufzeit und aktueller Suchpunkt.
- `lastSubmarineCourse()`: gemerkte Position/Richtung und Ablaufzeit der Angriffsfortsetzung.
- `assignedAircraftCount(vehicle)`: bisherige Zielreservierung anderer Flugzeuge.

`select` wird nach der bisherigen gestaffelten Zeitplanung aufgerufen.
`goal` berechnet anschließend pro Tick den Navigationspunkt. Der Standard berücksichtigt
hier die Jagd und lokale Kontakte. Wer eine vollständig eigene Strategie möchte,
kann beide Methoden ersetzen. Wer nur anders gewichten möchte, kann in `goal` ausdrücklich
an eine eigene Instanz von `StandardBotTargetSelection` delegieren.

Sichtbarkeit ist beobachterabhängig. Tiefe und Geschwindigkeit sind Eigenschaften des
U-Boots; ob ein bestimmter Gegner es sieht, ist eine Wahrnehmungsabfrage. Bei Kontaktverlust
verwendet der Standard gemerkte Koordinaten statt der aktuellen Position eines versteckten
Fahrzeugs. Ein eigener Algorithmus darf die verfügbare globale Fahrzeugliste nicht als
allwissende Zielerkennung behandeln.

Auch Wrackkollisionen rechnen jetzt mit dem Fahrzeugmodell und ungerundeten geometrischen
Vorpositionen (`HullPose`), nicht mit Frontend-Snapshots. Ein Wrack hält ein einmal beim
Abschuss abgetrenntes Fahrzeug seines konkreten Typs, damit eine Wiedergeburt den Wrackzustand
nicht verändert. Erst die Ausgabe erstellt `VehicleSnapshot` bzw. `ShipWreckSnapshot`.


## Prüfung der Auslagerung (4. November 2026)

- Gesamte JVM-Testsuite: 624 Tests, davon 622 bestanden und 2 übersprungen.
- Integration über den normalen `GameSession`-Konstruktor: konfigurierte Zielauswahl und
  Bewegung werden tatsächlich aufgerufen, auch nach Erzeugen einer neuen Sitzung.
- Originalinstanzen, Unterklassen, ungerundete Koordinaten, Jagdgebiet und getrennte
  Sicht-/Sonarkontakte sind geprüft. Ein eigener Controller bekommt keine vorab
  erzwungene Standard-Tauchtaktik.
- Eine frische JVM simuliert 40 Fahrzeuge über 7 × 600 Takte ohne Ausgabe.
  Die Initialisierungsprotokolle belegen: `VehicleSnapshot` und `ShipWreckSnapshot`
  werden erst nach Ende dieser Berechnung beim ausdrücklichen Ausgabeaufruf initialisiert.
  Damit können diese DTOs vorher auch nicht instanziiert worden sein.
- Vergleich mit dem vorherigen Commit `3f2dbc9`: zweimal fünf gemessene Durchläufe nach
  jeweils zwei Aufwärmdurchläufen, Reihenfolge Alt/Neu/Neu/Alt. Im synthetischen
  Freiwasser-Szenario mit 40 Fahrzeugen lagen die mittleren Rundenzeiten bei rund
  0,227 ms vorher und 0,217 ms danach. Das zeigt hier keinen zusätzlichen Laufzeitbedarf;
  es ist keine Garantie für Produktionslast oder komplexe Landschaften. Zusätzlich
  bestehen die vorhandenen Lasttests mit Landschaft und größeren Flotten.


## Standardberechnung nach Fahrzeugtyp

`StandardBotCalculation` koordiniert nur die Runde und reicht jedes Fahrzeug an seine
konkrete Implementierung weiter:

- `TorpedoBoatBotCalculation`: Verfolgung und Angriffsgeschwindigkeit des Torpedoboots.
- `SubmarineBotCalculation`: Tauchen, Rückzug und Angriffsgeschwindigkeit des U-Boots.
- `ScoutPlaneBotCalculation`: Anflug, Flughöhe, Geländeprüfung, Torpedos und Bomben.

Die Klassen implementieren `VehicleBotCalculation<TorpedoBoat>`,
`VehicleBotCalculation<Submarine>` bzw. `VehicleBotCalculation<ScoutPlane>`.
Die jeweilige Methode bekommt direkt das konkrete Fahrzeug, den Rundenkontext, die
Tick-ID und sein vorbereitetes `BotGoal`. Flugzeuge heißen im Code `plane`, U-Boote
`submarine`, Torpedoboote `boat` und typübergreifende Objekte `vehicle` oder `target`.

`WaterBotNavigation` bündelt ausschließlich die gemeinsamen Wasserfahrzeug-Regeln:
Kursplanung, Kollisionsvermeidung, Ausweichmanöver, Ruder und Torpedofeuer. Eine eigene
Vererbungshierarchie für die Berechnung gibt es nicht. Die Standardrunde behält ihre
bisherige optionale parallele Vorausberechnung und wendet Befehle in derselben Reihenfolge an.

Einzelne Fahrzeugtypen können unabhängig ersetzt werden:

```java
BotCalculation calculation = new StandardBotCalculation(
    new TorpedoBoatBotCalculation(),
    (submarine, situation, tickId, goal) -> situation.steer(submarine, 5, 0),
    new ScoutPlaneBotCalculation());
```

Der Konstruktor mit eigenen Implementierungen lässt deren Planung vollständig bei diesen
Implementierungen; er führt keine gemeinsame parallele Vorausberechnung darüber aus.
`new StandardBotCalculation()` verwendet weiterhin die bisherige optionale Bündelung.

`BotSituation.vehicles()` ist der passende Name für die aktive Fahrzeugliste.
`ships()` bleibt als kompatibler Alias für bestehende Experimente erhalten.


Die Aufteilung nach Fahrzeugtypen ist zusätzlich durch einen Integrationstest abgesichert:
Jede eigene Implementierung erhält genau die ursprüngliche Instanz ihres Fahrzeugtyps;
menschlich gesteuerte Fahrzeuge werden dabei übersprungen. Die Rudergrenzen bleiben erhalten.
Stand nach der Aufteilung: 626 Tests, davon 624 bestanden und 2 übersprungen. Der erneute
Freiwasser-Vergleich mit 40 Fahrzeugen liegt vor und nach der Aufteilung bei rund 0,22 ms
pro Runde; innerhalb dieser Messung ist kein relevanter Mehrbedarf erkennbar.
