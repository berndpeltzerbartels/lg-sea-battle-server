# Mehrere Menschen auf einem Torpedoboot

## Aktualisierte Vereinbarung und erster Umsetzungsschritt

Die spaetere Diskussion ersetzt den Kapitaen als exklusiven Fahrer durch
gleichwertige Besatzungsmitglieder: Jeder kann einen freien Bedienplatz
uebernehmen, der Server reserviert ihn exklusiv. Ein freigegebener Platz ist
fuer alle frei. Ohne Steuermann bleibt die letzte Fahrtanweisung bestehen.
Aufnahmeentscheidungen bleiben davon getrennt.

Umgesetzt ist zunaechst der XIS-Anmeldeweg mit Anheuern-Auswahl und einer
XIS-Seite /crew.html. Antragsteller erhalten noch kein eigenes Schiff.
Einmalige Anfragen pro Account/Schiff/Partie und eine offene Anfrage pro
Account werden serverseitig verwaltet. Anfragen laufen nach zwei Minuten
oder bei Wegfall des Ansprechpartners ab. Die Seite aktualisiert sich ueber
RefreshOnUpdateEvents und RefreshEventPublisher (XIS SSE); ein XIS-Scheduler
prueft einmal pro Sekunde die relevanten Listen und sendet nur bei Aenderungen.
Positionen und Fahrbewegungen loesen kein Neuladen der Liste aus.

Noch offen: Annahme/Ablehnung beim Ansprechpartner, Aufnahmefreigabe,
Besatzungsplatzreservierung, tatsaechlicher Einstieg und Bedienberechtigungen.
Aktuell erscheinen aktive menschlich gesteuerte Torpedoboote desselben Teams.
Nicht auf Produktion ausgerollt. Lokaler Browser-Smoke-Test:
node scripts/test-crew-page.mjs (Standardport 9092, CREW_TEST_URL optional).

Die folgende Erstanalyse dokumentiert den damaligen Ausgangspunkt;
abweichende Rollenvorschlaege sind durch die Vereinbarung oben ersetzt.

## Ausgangspunkt

- Server: main b437eaa, Branch codex/multi-person-crew.
- Client: master dd40f4f, Branch codex/multi-person-crew.
- Separate Arbeitsverzeichnisse unter ../lg-sea-battle-crew; bestehende Experimente bleiben unveraendert.
- Analyse des Codes, noch keine Implementierung oder Laufzeittests der Besatzungsfunktion.

## Befund

Die Bedienplaetze und Waffen sind bereits vorhanden. Die zentrale Einschraenkung
ist die Gleichsetzung von Spieler, Bewegungssteuerung und Schiffseigentuemer.
Eine reine Liste weiterer Spieler am Schiff reicht deshalb nicht aus.

### Server

- Fleet.activeShipIdByPlayerId kann grundsaetzlich mehrere Spieler demselben
  Schiff zuordnen. assignNextShipToPlayer setzt jedoch den einzigen
  Ship.controlledBy, und releasePlayer gibt das Schiff sofort an einen Bot
  zurueck oder entfernt ein zusaetzlich angelegtes Schiff.
- Ship.controlledBy entscheidet auch ueber Bot-/Serversimulation. Es sollte
  nicht unbesehen durch eine Liste ersetzt werden: Fahrverantwortung und
  Besatzungsmitgliedschaft brauchen getrennte Bedeutungen.
- GameSession.applyPlayerState und Ship.applyPlayerState uebernehmen Bewegung
  und beide Geschuetzausrichtungen aus einem gemeinsamen PlayerStateUpdate.
  Updates eines Schuetzen duerfen die Fahrt oder die andere Waffe nicht aendern.
- GameSession.applyFireFlak/applyFireCannon pruefen die Schiffszuteilung und
  Schiff-ID; Platz-/Waffenberechtigungen fehlen. Torpedos brauchen dieselbe
  Berechtigungspruefung. Munition und Cooldowns bleiben pro Schiff/Waffe.
- Mehrere Befehle weisen fehlenden Spielern automatisch ein neues Schiff zu.
  Besatzungsbefehle duerfen bei veralteter Zuordnung kein Ersatzschiff erzeugen.
- SeaBattleClientController und SeaBattleLoginPage weisen beim Einstieg ein
  eigenes Fahrzeug zu. Beitreten muss eine explizite Alternative werden,
  einschliesslich Teampruefung und atomarer Platzreservierung.
- ShipSnapshot bzw. ein separates Besatzungsfeld im GameSnapshot muessen
  Mitgliedschaft, Platzbelegung und Fahrverantwortung transportieren.
- GameSession.releasePlayer, Versenken/Respawn, verbundene Spieler,
  Abschusszuordnung und Spielerlisten sind auf den einzelnen controlledBy
  zugeschnitten. Beim Austritt eines Mitglieds muss die Restbesatzung bleiben.

### Client

- src/main.js:createPlayerSpawn und applyServerGameSnapshot suchen das eigene
  Schiff anhand controlledBy === playerId. Kuenftig Mitgliedschaft verwenden.
- Das eigene Boot wird lokal bewegt; regulaere Server-Snapshots korrigieren
  seine Fahrt derzeit bewusst nicht. Nur der Fahrverantwortliche darf diesen
  Pfad nutzen. Schuetzen folgen der gemeinsamen Schiffsbewegung aus Snapshots,
  mit geglaetteter Darstellung und lokaler, reaktionsschneller Waffensteuerung.
- createPlayerStatePayload sendet Bewegung, Flak- und Kanonenausrichtung
  gemeinsam. Die Zustaendigkeiten muessen beim Senden und auf dem Server
  getrennt werden, auch fuer sendFinalPlayerState beim Schliessen der Seite.
- setBattleStation kennt bereits bridge, flak, cannon und torpedo. Die Kamera
  umzuschalten ist bisher rein lokal; einen Bedienplatz zu beanspruchen muss
  dagegen vom Server bestaetigt werden. Ansicht und Bedienrecht trennen.
- Spielerlisten, Namensanzeigen, eigene Projektile, Trefferberichte und
  Respawn-Anzeige auf mehrere Spieler desselben Schiffes pruefen. Insbesondere
  darf derselbe Treffer nicht von mehreren Besatzungsclients doppelt gelten.

## Vorschlag fuer einen kleinen ersten Schritt

1. Torpedoboot: Ein Schiffsverantwortlicher behaelt Fahrt und unbesetzte Waffen.
   Ein weiterer Mensch kann Flak oder Kanone exklusiv uebernehmen. Solo bleibt
   wie bisher bedienbar. Das ist ein Vorschlag, keine festgelegte Produktregel.
2. Server verwaltet Beitritt, Austritt und Platzwechsel atomar. Ein Spieler hat
   hoechstens ein Schiff und einen belegten Platz; kein Platz zwei Bediener.
   Zustaendigkeit bzw. Zuordnungsversion verhindert verspätete Updates nach
   einem Wechsel. Fremde Teams und nicht aktive Schiffe werden abgewiesen.
3. Schuetzen senden nur ihren Waffenanteil und Feuerbefehle. Der Server prueft
   jeden Befehl gegen die aktuelle Belegung; Fahrzeugtyp und Position werden
   durch Schuetzen nicht ueberschrieben.
4. Client folgt als Schuetze dem zugewiesenen Schiff, nutzt bestehende Kameras
   und stellt fremdbediente Waffen anhand des gemeinsamen Zustands dar.
5. Erst danach mehrere Schuetzen, Torpedoplatz und Steueruebergabe erweitern.

Vor Umsetzung festlegen: Wer darf beitreten (Einladung/offen)? Was geschieht
bei Austritt des Fahrers? Bleibt die Besatzung beim Respawn zusammen? Wem
werden Abschuesse gutgeschrieben? Empfehlung: Besatzung beim Respawn erhalten
und den ausloesenden Spieler beim Schuss speichern, nicht erst beim Treffer
aus dem dann aktuellen Fahrer ableiten.

## Testschwerpunkte

- Zwei Clients, ein Schiff: Fahrt plus gleichzeitiges Zielen/Feuern.
- Ein Schuetze kann weder Fahrt noch fremde Waffen ueberschreiben.
- Gleichzeitiger Beitritt zum selben Platz hat genau einen Gewinner.
- Platzwechsel, verspaetete Updates, Wiederverbindung und Abmelden.
- Versenken/Respawn betrifft alle Mitglieder konsistent und erzeugt nicht
  pro Mitglied ein neues Schiff. Keine doppelten Treffer oder Munition.
- Solospiel, Bots, Flugzeuge und U-Boote bleiben unveraendert.

## Aufwand und Performance

Kein neues 3D-Modell, keine neue Landschaftsberechnung und kein eigener
Netzwerkkanal erforderlich. Besatzungsdaten sind klein; ein bemanntes Schiff
bleibt ein einziges simuliertes Schiff. Jeder weitere Browser ist allerdings
eine weitere Netzwerkverbindung. Die nichttriviale Arbeit liegt in der
Zustaendigkeit fuer Bewegung/Waffen und in den Lebenszyklusfaellen. Daher
ueberschaubar als erste Zweierbesatzung, aber kein reiner Zuordnungsfix.
