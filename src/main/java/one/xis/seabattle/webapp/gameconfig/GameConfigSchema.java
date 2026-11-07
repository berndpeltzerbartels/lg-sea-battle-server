package one.xis.seabattle.webapp.gameconfig;

import one.xis.ddl.Change;
import one.xis.ddl.ChangeSet;
import one.xis.ddl.DDL;

import static one.xis.seabattle.webapp.gameconfig.GameConfigParameterType.*;

@ChangeSet(value = "sea-battle-game-config", previous = one.xis.seabattle.game.SeaBattleSchema.class)
class GameConfigSchema {
    @Change("001-create-game-config-parameters")
    void createParameters(DDL ddl) {
        var table = ddl.createTableIfNotExists("game_config_parameters");
        table.addColumn("id").varchar(160).notNull().primaryKey();
        table.addColumn("parameter_group").varchar(80).notNull();
        table.addColumn("parameter_type").varchar(40).notNull();
        table.addColumn("name").varchar(100).notNull();
        table.addColumn("title").varchar(255).notNull();
        table.addColumn("description").text().notNull();
        table.addColumn("parameter_value").varchar(255).notNull();
    }

    @Change("002-seed-torpedo-boat-bot")
    void seedTorpedoBoatBot(DDL ddl) {
        // Snapshot of the current constants. Migration defaults must not change with future tuning.
        // Loaded into the runtime configuration at startup and on refresh. Distances use world units.
        insertDefault(ddl, "AIM_RESPONSE", NUMBER, "0.40", "Einlenken beim Zielen",
                "Teiler der Winkelkorrektur beim Torpedozielen. Größer bedeutet schwächeres Einlenken; kleiner bedeutet schnelleres Ausrichten.");
        insertDefault(ddl, "AIM_TURN_DAMPING", NUMBER, "0.45", "Dämpfung beim Zielen",
                "Gewichtung der Differenz zwischen benötigter und tatsächlicher Drehrate. Bremst die Drehbewegung beim Ausrichten ab.");
        insertDefault(ddl, "AIM_MIN_CORRECTION_ANGLE", ANGLE, "0.0015", "Kleinste Zielkorrektur",
                "Restabweichung in Radiant, oberhalb derer auch bei gerundetem Ruderwert null noch eine kleine Korrektur erfolgt.");
        insertDefault(ddl, "FIRE_ARC", ANGLE, "0.16", "Feuerwinkel",
                "Maximale Winkelabweichung in Radiant links oder rechts vom Vorhaltewinkel für einen normalen Torpedoschuss. Die Trefferbahn wird zusätzlich geprüft.");
        insertDefault(ddl, "CLOSE_FIRE_ARC", ANGLE, "1.15", "Feuerwinkel im Nahbereich",
                "Maximale Winkelabweichung in Radiant im Nahbereich. Erlaubt nur einen Schussversuch; eine tatsächlich passende Torpedobahn bleibt erforderlich.");
        insertDefault(ddl, "FIRE_MIN_RANGE", DISTANCE, "195", "Beginn des normalen Schussbereichs",
                "Untere Distanzgrenze in Welteinheiten für normale Torpedoschüsse. Aktuell 65 mal Modellskalierung 3; Nahbereichsschüsse werden separat geprüft.");
        insertDefault(ddl, "FIRE_MAX_RANGE", DISTANCE, "690", "Maximale Torpedoschussweite",
                "Obere Distanzgrenze für Bot-Torpedoangriffe in Welteinheiten. Aktuell 230 mal Modellskalierung 3; nicht die gesamte Laufweite des Torpedos.");
        insertDefault(ddl, "CLOSE_FIRE_RANGE", DISTANCE, "435", "Schussbereich für Nahangriffe",
                "Bis zu dieser Distanz in Welteinheiten gilt der Nahbereichs-Feuerwinkel. Aktuell 145 mal Modellskalierung 3.");
        insertDefault(ddl, "TORPEDO_COOLDOWN_MIN", SECONDS, "10.5", "Minimale Torpedopause",
                "Grundpause in Sekunden nach einem abgefeuerten Torpedo. Dazu kommt der bootsabhängige Zusatz.");
        insertDefault(ddl, "TORPEDO_COOLDOWN_VARIATION", SECONDS, "3.0", "Zusätzliche Torpedopause",
                "Maximaler bootsabhängiger Zuschlag zur Torpedopause in Sekunden. Mit dem aktuellen Grundwert ergeben sich 10,5 bis 13,5 Sekunden.");
        insertDefault(ddl, "APPROACH_SLOW_RANGE", DISTANCE, "230", "Distanz zum langsameren Annähern",
                "Distanzgrenze in Welteinheiten für die langsamere Annäherung im Angriff. Die konkrete Fahrstufe hängt zusätzlich von Zieltyp und Nahkampfsituation ab.");
        insertDefault(ddl, "CLOSE_MANEUVER_RANGE", DISTANCE, "130", "Nahkampf-Manövrierbereich",
                "Innerhalb dieser Distanz in Welteinheiten entscheidet der Zielwinkel stärker über die Angriffsgeschwindigkeit.");
        insertDefault(ddl, "RADAR_INTERCEPT_RANGE", DISTANCE, "360", "Distanz für schnelle Annäherung",
                "Distanzgrenze in Welteinheiten für schnelle Annäherung an menschliche Oberflächenziele. Andere Regeln, etwa Vollgas ab einem Radarring, haben Vorrang.");
        insertDefault(ddl, "RAM_RANGE", DISTANCE, "102", "Rammangriffsbereich",
                "Nahbereich in Welteinheiten für die Geschwindigkeitsregel beim Rammen. Aktuell 34 mal Modellskalierung 3.");
        insertDefault(ddl, "HUMAN_TARGET_PRIORITY_CLEARANCE", DISTANCE, "320", "Spielerbevorzugung gegenüber Bot-Zielen",
                "Ist kein gegnerischer Bot innerhalb dieser Distanz in Welteinheiten, wird ein erkanntes menschliches Ziel bevorzugt ausgewählt.");
        insertDefault(ddl, "TARGET_PREFERRED_ATTACKERS", NUMBER, "2", "Bevorzugte Angreiferzahl je Ziel",
                "Ab dieser Zahl bereits zugeteilter Angreifer wird eine passende Alternative gesucht. Spielerbevorzugung und U-Boot-Jagd behalten ihre Regeln.");
        insertDefault(ddl, "TARGET_ALTERNATIVE_DISTANCE_FACTOR", NUMBER, "1.3", "Entfernungsspielraum für Alternativziele",
                "Ein alternatives Ziel darf höchstens diesen Faktor weiter entfernt sein als die erste Wahl. 1,3 entspricht 30 Prozent zusätzlicher Entfernung.");
    }

    @Change("003-seed-fleet-sizes")
    void seedFleetSizes(DDL ddl) {
        insertDefault(ddl, GameConfigParameterGroup.FLEET, "LIGHT_VEHICLES", NUMBER, "20", "Flotte Hell",
                "Sollzahl aller aktiven Fahrzeuge der hellen Partei, einschließlich Spielern und Flugzeugen.");
        insertDefault(ddl, GameConfigParameterGroup.FLEET, "DARK_VEHICLES", NUMBER, "20", "Flotte Dunkel",
                "Sollzahl aller aktiven Fahrzeuge der dunklen Partei, einschließlich Spielern und Flugzeugen.");
    }

    @Change("004-seed-fleet-vehicle-types")
    void seedFleetVehicleTypes(DDL ddl) {
        // Migrate the former total to independent counts without changing its sum.
        String total = "case when regexp_like(parameter_value, '^[0-9]{1,9}$') then cast(parameter_value as integer) else 0 end";
        for (String team : new String[]{"LIGHT", "DARK"}) {
            insertTypeDefault(ddl, team, "SUBMARINES", "U-Boote", "floor((" + total + ") * 0.15)");
            insertTypeDefault(ddl, team, "PLANES", "Flugzeuge", "floor((" + total + ") * 0.05)");
            insertTypeDefault(ddl, team, "TORPEDO_BOATS", "Torpedoboote",
                    "(" + total + ") - floor((" + total + ") * 0.15) - floor((" + total + ") * 0.05)");
        }
        ddl.sql("delete from game_config_parameters where id in ('FLEET.LIGHT_VEHICLES','FLEET.DARK_VEHICLES')");
    }

    private void insertTypeDefault(DDL ddl, String team, String type, String title, String expression) {
        String name = team + "_" + type, id = "FLEET." + name;
        ddl.sql("""
                insert into game_config_parameters
                    (id, parameter_group, parameter_type, name, title, description, parameter_value)
                select '%s', 'FLEET', 'NUMBER', '%s', '%s', 'Sollzahl dieses Fahrzeugtyps in dieser Partei.', cast(cast((%s) as integer) as varchar)
                from game_config_parameters where id = 'FLEET.%s_VEHICLES'
                and not exists (select 1 from game_config_parameters where id = '%s')
                """.formatted(id, name, title, expression, team, id));
    }

    @Change("005-create-game-config-profiles")
    void createProfiles(DDL ddl) {
        var table = ddl.createTableIfNotExists("game_config_profiles");
        table.addColumn("id").varchar(100).notNull().primaryKey();
        table.addColumn("name").varchar(100).notNull();
        table.addColumn("parameter_values").text().notNull();
        table.addColumn("setup_id").varchar(100).notNull();
        table.addColumn("game_mode").varchar(40).notNull();
    }

    @Change("006-unique-profile-names")
    void uniqueProfileNames(DDL ddl) {
        ddl.alterTable("game_config_profiles").addColumn("name_key").varchar(100);
        ddl.sql("update game_config_profiles set name_key=lower(trim(name))");
        ddl.sql("alter table game_config_profiles alter column name_key set not null");
        ddl.sql("create unique index game_config_profiles_name_uq on game_config_profiles(name_key)");
    }

    @Change("007-seed-bot-aim-error")
    void seedAimError(DDL ddl) {
        insertDefault(ddl, "AIM_ERROR_ANGLE", ANGLE, "0.017453292519943295", "Zielungenauigkeit",
                "Maximale absichtliche Zielabweichung in Radiant je Zielvorgang. Bis zum nächsten Schuss stabil. Null bedeutet präzises Zielen. Insel- und Freundschiffprüfung bleiben aktiv.");
    }

    @Change("008-seed-submarine-bot")
    void seedSubmarineBot(DDL ddl) {
        // Historical submarine defaults, not live torpedo-boat settings.
        // Reuse the seed definitions only to create independent rows in the other group.
        seedSubmarineCombat(ddl);
        insertDefault(ddl, GameConfigParameterGroup.SUBMARINE_BOT, "DIVE_RANGE", DISTANCE, "500", "Sehrohrtiefe ab Gegnernähe", "Unabhängige Einstellung für die U-Boot-Bots.");
        insertDefault(ddl, GameConfigParameterGroup.SUBMARINE_BOT, "DEEP_RANGE", DISTANCE, "130", "Tieftauchen und Flucht bei Gegnernähe", "Unabhängige Einstellung für die U-Boot-Bots.");
        insertDefault(ddl, GameConfigParameterGroup.SUBMARINE_BOT, "SAFE_RANGE", DISTANCE, "220", "Sicherheitsabstand beim Tieftauchen", "Unabhängige Einstellung für die U-Boot-Bots.");
        insertDefault(ddl, GameConfigParameterGroup.SUBMARINE_BOT, "SHOT_ASCENT_RANGE", DISTANCE, "180", "Mindestabstand für Auftauchen zum Schuss", "Unabhängige Einstellung für die U-Boot-Bots.");
        insertDefault(ddl, GameConfigParameterGroup.SUBMARINE_BOT, "SURFACE_RANGE", DISTANCE, "650", "Auftauchen ohne nahen Gegner", "Unabhängige Einstellung für die U-Boot-Bots.");
        insertDefault(ddl, GameConfigParameterGroup.SUBMARINE_BOT, "ATTACK_MIN_RANGE", DISTANCE, "250", "Unterer Angriffsabstand", "Unabhängige Einstellung für die U-Boot-Bots.");
        insertDefault(ddl, GameConfigParameterGroup.SUBMARINE_BOT, "ATTACK_MAX_RANGE", DISTANCE, "400", "Oberer Angriffsabstand", "Unabhängige Einstellung für die U-Boot-Bots.");
        insertDefault(ddl, GameConfigParameterGroup.SUBMARINE_BOT, "AIM_TOLERANCE", ANGLE, "0.002617993877991494", "Zielabweichung für Fahrtstopp", "Unabhängige Einstellung für die U-Boot-Bots.");
        insertDefault(ddl, GameConfigParameterGroup.SUBMARINE_BOT, "AIRCRAFT_MEMORY_SECONDS", SECONDS, "10", "Erinnerung an Flugzeuggefahr", "Unabhängige Einstellung für die U-Boot-Bots.");
        insertDefault(ddl, GameConfigParameterGroup.SUBMARINE_BOT, "UNDERWATER_CONTACT_RANGE", DISTANCE, "849.555", "Kontaktweite unter Wasser", "Unabhängige Einstellung für die U-Boot-Bots.");
    }

    private void seedSubmarineCombat(DDL ddl) {
        insertDefault(ddl, GameConfigParameterGroup.SUBMARINE_BOT, "AIM_RESPONSE", NUMBER, "0.40", "Einlenken beim Zielen",
                "Teiler der Winkelkorrektur beim Torpedozielen. Größer bedeutet schwächeres Einlenken; kleiner bedeutet schnelleres Ausrichten.");
        insertDefault(ddl, GameConfigParameterGroup.SUBMARINE_BOT, "AIM_TURN_DAMPING", NUMBER, "0.45", "Dämpfung beim Zielen",
                "Gewichtung der Differenz zwischen benötigter und tatsächlicher Drehrate. Bremst die Drehbewegung beim Ausrichten ab.");
        insertDefault(ddl, GameConfigParameterGroup.SUBMARINE_BOT, "AIM_MIN_CORRECTION_ANGLE", ANGLE, "0.0015", "Kleinste Zielkorrektur",
                "Restabweichung in Radiant, oberhalb derer auch bei gerundetem Ruderwert null noch eine kleine Korrektur erfolgt.");
        insertDefault(ddl, GameConfigParameterGroup.SUBMARINE_BOT, "FIRE_ARC", ANGLE, "0.16", "Feuerwinkel",
                "Maximale Winkelabweichung in Radiant links oder rechts vom Vorhaltewinkel für einen normalen Torpedoschuss. Die Trefferbahn wird zusätzlich geprüft.");
        insertDefault(ddl, GameConfigParameterGroup.SUBMARINE_BOT, "CLOSE_FIRE_ARC", ANGLE, "1.15", "Feuerwinkel im Nahbereich",
                "Maximale Winkelabweichung in Radiant im Nahbereich. Erlaubt nur einen Schussversuch; eine tatsächlich passende Torpedobahn bleibt erforderlich.");
        insertDefault(ddl, GameConfigParameterGroup.SUBMARINE_BOT, "FIRE_MIN_RANGE", DISTANCE, "195", "Beginn des normalen Schussbereichs",
                "Untere Distanzgrenze in Welteinheiten für normale Torpedoschüsse. Aktuell 65 mal Modellskalierung 3; Nahbereichsschüsse werden separat geprüft.");
        insertDefault(ddl, GameConfigParameterGroup.SUBMARINE_BOT, "FIRE_MAX_RANGE", DISTANCE, "690", "Maximale Torpedoschussweite",
                "Obere Distanzgrenze für Bot-Torpedoangriffe in Welteinheiten. Aktuell 230 mal Modellskalierung 3; nicht die gesamte Laufweite des Torpedos.");
        insertDefault(ddl, GameConfigParameterGroup.SUBMARINE_BOT, "CLOSE_FIRE_RANGE", DISTANCE, "435", "Schussbereich für Nahangriffe",
                "Bis zu dieser Distanz in Welteinheiten gilt der Nahbereichs-Feuerwinkel. Aktuell 145 mal Modellskalierung 3.");
        insertDefault(ddl, GameConfigParameterGroup.SUBMARINE_BOT, "TORPEDO_COOLDOWN_MIN", SECONDS, "10.5", "Minimale Torpedopause",
                "Grundpause in Sekunden nach einem abgefeuerten Torpedo. Dazu kommt der bootsabhängige Zusatz.");
        insertDefault(ddl, GameConfigParameterGroup.SUBMARINE_BOT, "TORPEDO_COOLDOWN_VARIATION", SECONDS, "3.0", "Zusätzliche Torpedopause",
                "Maximaler bootsabhängiger Zuschlag zur Torpedopause in Sekunden. Mit dem aktuellen Grundwert ergeben sich 10,5 bis 13,5 Sekunden.");
        insertDefault(ddl, GameConfigParameterGroup.SUBMARINE_BOT, "APPROACH_SLOW_RANGE", DISTANCE, "230", "Distanz zum langsameren Annähern",
                "Distanzgrenze in Welteinheiten für die langsamere Annäherung im Angriff. Die konkrete Fahrstufe hängt zusätzlich von Zieltyp und Nahkampfsituation ab.");
        insertDefault(ddl, GameConfigParameterGroup.SUBMARINE_BOT, "CLOSE_MANEUVER_RANGE", DISTANCE, "130", "Nahkampf-Manövrierbereich",
                "Innerhalb dieser Distanz in Welteinheiten entscheidet der Zielwinkel stärker über die Angriffsgeschwindigkeit.");
        insertDefault(ddl, GameConfigParameterGroup.SUBMARINE_BOT, "RADAR_INTERCEPT_RANGE", DISTANCE, "360", "Distanz für schnelle Annäherung",
                "Distanzgrenze in Welteinheiten für schnelle Annäherung an menschliche Oberflächenziele. Andere Regeln, etwa Vollgas ab einem Radarring, haben Vorrang.");
        insertDefault(ddl, GameConfigParameterGroup.SUBMARINE_BOT, "RAM_RANGE", DISTANCE, "102", "Rammangriffsbereich",
                "Nahbereich in Welteinheiten für die Geschwindigkeitsregel beim Rammen. Aktuell 34 mal Modellskalierung 3.");
        insertDefault(ddl, GameConfigParameterGroup.SUBMARINE_BOT, "HUMAN_TARGET_PRIORITY_CLEARANCE", DISTANCE, "320", "Spielerbevorzugung gegenüber Bot-Zielen",
                "Ist kein gegnerischer Bot innerhalb dieser Distanz in Welteinheiten, wird ein erkanntes menschliches Ziel bevorzugt ausgewählt.");
        insertDefault(ddl, GameConfigParameterGroup.SUBMARINE_BOT, "TARGET_PREFERRED_ATTACKERS", NUMBER, "2", "Bevorzugte Angreiferzahl je Ziel",
                "Ab dieser Zahl bereits zugeteilter Angreifer wird eine passende Alternative gesucht. Spielerbevorzugung und U-Boot-Jagd behalten ihre Regeln.");
        insertDefault(ddl, GameConfigParameterGroup.SUBMARINE_BOT, "TARGET_ALTERNATIVE_DISTANCE_FACTOR", NUMBER, "1.3", "Entfernungsspielraum für Alternativziele",
                "Ein alternatives Ziel darf höchstens diesen Faktor weiter entfernt sein als die erste Wahl. 1,3 entspricht 30 Prozent zusätzlicher Entfernung.");
        insertDefault(ddl, GameConfigParameterGroup.SUBMARINE_BOT, "AIM_ERROR_ANGLE", ANGLE, "0", "Zielungenauigkeit",
                "Maximale Zielabweichung in Radiant, stabil pro Zielvorgang. Null bedeutet präzises Zielen.");
    }

    private void insertDefault(DDL ddl, String name, GameConfigParameterType type, String value,
                               String title, String description) {
        insertDefault(ddl, GameConfigParameterGroup.TORPEDO_BOAT_BOT, name, type, value, title, description);
    }

    private void insertDefault(DDL ddl, GameConfigParameterGroup parameterGroup, String name,
                               GameConfigParameterType type, String value, String title, String description) {
        String group = parameterGroup.name();
        String id = group + "." + name;
        ddl.sql("""
                insert into game_config_parameters
                    (id, parameter_group, parameter_type, name, title, description, parameter_value)
                select '%s', '%s', '%s', '%s', '%s', '%s', '%s'
                where not exists (select 1 from game_config_parameters where id = '%s')
                """.formatted(quote(id), quote(group), quote(type.name()), quote(name),
                quote(title), quote(description), quote(value), quote(id)));
    }

    private String quote(String value) {
        return value.replace("'", "''");
    }
}
