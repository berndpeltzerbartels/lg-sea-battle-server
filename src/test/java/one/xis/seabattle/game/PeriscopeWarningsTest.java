package one.xis.seabattle.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PeriscopeWarningsTest {
    private final RadarService radar = new RadarService();
    private final WorldMap sea = new WorldMap(1, List.of());
    private final PeriscopeWarnings warnings = new PeriscopeWarnings();
    private final Ship observer = ship("boat", "light", "captain", 0, 0, 0, 0, false, "surface");

    private Ship ship(String id, String team, String controller, double x, double z, double y,
                      double speed, boolean sub, String depth) {
        Ship ship = new Ship(id, team, new Vector2(x, z), 0, controller);
        ship.applyPlayerState(new PlayerStateUpdate(controller, team, x, z, 0, speed, 0, 2, 0, 0,
                true, sub ? "submarine" : "torpedo-boat", y, 0, null, null, null, null, depth),
                new NavigationService(), sea);
        return ship;
    }

    private Ship sub(String controller, double x, double z, double y, double speed, String depth) {
        return ship("sub", "dark", controller, x, z, y, speed, true, depth);
    }

    private void check(double now, Ship target) { check(now, target, sea); }
    private void check(double now, Ship target, WorldMap world) {
        warnings.update(now, List.of(observer), radar.visibilityCache(world, List.of(observer, target)), world);
    }

    @Test void humanAndBotSubmarinesHaveIdenticalSightingsIncludingBehindTheShip() {
        for (String controller : List.of("bot", "enemy-captain")) {
            PeriscopeWarnings detector = new PeriscopeWarnings();
            Ship target = sub(controller, -5, -5, -5.58, 0, "periscope");
            detector.update(0, List.of(observer), radar.visibilityCache(sea, List.of(observer, target)), sea);
            assertEquals("Backbord achtern", detector.snapshot().get("boat").direction());
        }
    }

    @Test void actualDepthControlsSightingsRatherThanSelectedDepthState() {
        check(0, sub("enemy", 0, 10, -19.26, 0, "periscope"));
        assertTrue(warnings.snapshot().isEmpty());
        check(1, sub("enemy", 0, 10, -.78, 0, "periscope"));
        assertTrue(warnings.snapshot().isEmpty());
        check(2, sub("enemy", 0, 10, -5.58, 0, "submerged"));
        assertEquals("voraus", warnings.snapshot().get("boat").direction());
    }

    @Test void noWarningsForFriendlyInactiveOrHiddenContacts() {
        Ship friend = ship("friend", "light", "enemy", 0, 10, -5.58, 0, true, "periscope");
        check(0, friend);
        assertTrue(warnings.snapshot().isEmpty());
        Ship sunk = sub("enemy", 0, 10, -5.58, 0, "periscope");
        sunk.sink(0);
        check(1, sunk);
        assertTrue(warnings.snapshot().isEmpty());
        Ship hidden = sub("enemy", 0, 10, -6.5, 0, "periscope");
        check(2, hidden);
        assertTrue(warnings.snapshot().isEmpty());
    }

    @Test void anIslandBlocksTheSighting() {
        WorldMap island = new WorldMap(2, List.of(new Landmass("island", "rock", 0, 8,
                4, 4, 4, 4, 4, 4, null, 1, null, null, null, List.of(), List.of(), List.of())));
        check(0, sub("enemy", 0, 16, -5.58, 0, "periscope"), island);
        assertTrue(warnings.snapshot().isEmpty());
    }

    @Test void quietSubmarinesAreOnlyDetectedAtShortRange() {
        check(0, sub("enemy", 0, 60, -5.58, 0, "periscope"));
        assertTrue(warnings.snapshot().isEmpty());
        check(1, sub("enemy", 0, 60, -5.58, EngineOrders.speedFor(5), "periscope"));
        assertFalse(warnings.snapshot().isEmpty());
    }

    @Test void oncePerSecondNoContinuousSpamAndRearmAfterTenSecondsWithoutContact() {
        Ship target = sub("enemy", 0, 10, -5.58, 0, "periscope");
        Ship far = sub("enemy", 0, 1000, -5.58, 0, "periscope");
        check(0, far);
        check(.99, target);
        assertTrue(warnings.snapshot().isEmpty());
        check(1, target);
        String id = warnings.snapshot().get("boat").id();
        for (int t = 2; t <= 6; t++) {
            check(t, target);
            assertEquals(id, warnings.snapshot().get("boat").id());
        }
        check(7, target);
        assertTrue(warnings.snapshot().isEmpty());
        check(8, far);
        check(9, target);
        assertTrue(warnings.snapshot().isEmpty());
        for (int t = 10; t <= 19; t++) check(t, far);
        check(20, target);
        assertNotEquals(id, warnings.snapshot().get("boat").id());
    }

    @Test void multipleContactsAreCombinedAndNewContactsAreNotLostDuringTheToast() {
        Ship first = sub("bot", 0, 10, -5.58, 0, "periscope");
        Ship second = ship("second", "dark", "human", 10, 0, -5.58, 0, true, "periscope");
        var cache = radar.visibilityCache(sea, List.of(observer, first, second));
        warnings.update(0, List.of(observer), cache, sea);
        assertEquals(2, warnings.snapshot().get("boat").count());
        Ship third = ship("third", "dark", "human", -10, 0, -5.58, 0, true, "periscope");
        cache = radar.visibilityCache(sea, List.of(observer, first, second, third));
        warnings.update(1, List.of(observer), cache, sea);
        warnings.update(6, List.of(observer), cache, sea);
        assertEquals(1, warnings.snapshot().get("boat").count());
        assertEquals("Backbord", warnings.snapshot().get("boat").direction());
    }

    @Test void deepObserversAndReleasedShipsDoNotReceiveWarnings() {
        Ship deep = ship("deep", "light", "captain", 0, 0, -19, 0, true, "submerged");
        Ship target = sub("enemy", 0, 10, -5.58, 0, "periscope");
        var cache = radar.visibilityCache(sea, List.of(deep, target));
        warnings.update(0, List.of(deep), cache, sea);
        assertTrue(warnings.snapshot().isEmpty());
        warnings.update(1, List.of(observer), radar.visibilityCache(sea, List.of(observer, target)), sea);
        assertFalse(warnings.snapshot().isEmpty());
        warnings.update(2, List.of(), cache, sea);
        assertTrue(warnings.snapshot().isEmpty());
    }

    @Test void humanSubmarineReceivesSightingsOnlyWhenPhysicallySurfaced() {
        for (double y : new double[] { -.78, -3, -5.58, -19.26 }) {
            var detector = new PeriscopeWarnings();
            Ship own = ship("own-sub", "light", "captain", 0, 0, y, 0, true,
                    y < -1 ? "periscope" : "surface");
            Ship target = sub("enemy", 0, 10, -5.58, 0, "periscope");
            detector.update(0, List.of(own), radar.visibilityCache(sea, List.of(own, target)), sea);
            if (y == -.78) assertEquals("voraus", detector.snapshot().get("own-sub").direction());
            else assertTrue(detector.snapshot().isEmpty());
        }
    }

    @Test void surfacedHumanSubmarineReceivesEnemyPeriscopeWarningThroughGameSession() {
        ShipSetup own = new ShipSetup("own-sub", "light", new Vector2(0, 0), 0, "captain", 2, 0, 99,
                "submarine", 0);
        ShipSetup enemy = new ShipSetup("enemy-sub", "dark", new Vector2(0, 10), 0, "enemy", 2, 0, 99,
                "submarine", 0);
        ShipSetup friendly = new ShipSetup("friendly-sub", "light", new Vector2(10, 0), 0, "friend", 2, 0, 99,
                "submarine", 0);
        GameSession session = new GameSession(new GameSetup("surfaced-sub-warning", sea,
                List.of(new FleetSetup("light", List.of(own, friendly)), new FleetSetup("dark", List.of(enemy))),
                List.of(new Vector2(1000, 1000))));
        NavigationService navigation = new NavigationService();
        session.applyPlayerState(new PlayerStateUpdate("captain", "light", 0, 0, 0, 0, 0, 2, 0, 0,
                true, "submarine", -.78, 0, null, null, null, null, "surface"), navigation, sea);
        session.applyPlayerState(new PlayerStateUpdate("enemy", "dark", 0, 10, 0, 0, 0, 2, 0, 0,
                true, "submarine", -5.58, 0, null, null, null, null, "periscope"), navigation, sea);
        session.applyPlayerState(new PlayerStateUpdate("friend", "light", 10, 0, 0, 0, 0, 2, 0, 0,
                true, "submarine", -5.58, 0, null, null, null, null, "periscope"), navigation, sea);
        session.update(.1, radar, navigation, sea);
        var warning = session.snapshot().periscopeWarnings().get("own-sub");
        assertNotNull(warning);
        assertEquals("voraus", warning.direction());
        assertEquals(1, warning.count(), "The friendly periscope must not be included");
    }

    @ParameterizedTest
    @ValueSource(doubles = { -5.58, -19.26 })
    void surfacingReannouncesAnEnemyAlreadyPresentWhileDiving(double initialY) {
        ShipSetup own = new ShipSetup("own-sub", "light", new Vector2(0, 0), 0, "captain", 2, 0, 99,
                "submarine", 0);
        ShipSetup enemy = new ShipSetup("enemy-sub", "dark", new Vector2(0, 15), 0, "enemy", 2, 0, 99,
                "submarine", 0);
        GameSession session = new GameSession(new GameSetup("surfacing-warning", sea,
                List.of(new FleetSetup("light", List.of(own)), new FleetSetup("dark", List.of(enemy))),
                List.of(new Vector2(1000, 1000))));
        NavigationService navigation = new NavigationService();
        session.applyPlayerState(new PlayerStateUpdate("enemy", "dark", 0, 15, 0, 0, 0, 2, 0, 0,
                true, "submarine", -5.58, 0, null, null, null, null, "periscope"), navigation, sea);
        setObserverDepth(session, navigation, initialY, initialY < -10 ? "submerged" : "periscope");
        session.update(1.1, radar, navigation, sea);
        assertFalse(session.snapshot().periscopeWarnings().containsKey("own-sub"));
        // Coming from deep water, ascend through periscope depth. The enemy never leaves.
        setObserverDepth(session, navigation, -5.58, "periscope");
        session.update(1.1, radar, navigation, sea);
        assertFalse(session.snapshot().periscopeWarnings().containsKey("own-sub"));
        for (int i = 0; i < 8; i++) session.update(1.1, radar, navigation, sea);
        assertFalse(session.snapshot().periscopeWarnings().containsKey("own-sub"));
        // Selecting Surface alone must not reset sightings before actually reaching the surface.
        setObserverDepth(session, navigation, -5.58, "surface");
        session.update(1.1, radar, navigation, sea);
        assertFalse(session.snapshot().periscopeWarnings().containsKey("own-sub"));
        setObserverDepth(session, navigation, -3, "surface");
        session.update(1.1, radar, navigation, sea);
        assertFalse(session.snapshot().periscopeWarnings().containsKey("own-sub"));
        setObserverDepth(session, navigation, -.78, "surface");
        session.update(1.1, radar, navigation, sea);
        var warning = session.snapshot().periscopeWarnings().get("own-sub");
        assertNotNull(warning, "Surfacing must warn again about the nearby enemy periscope");
        assertEquals("voraus", warning.direction());
        session.update(1.1, radar, navigation, sea);
        assertEquals(warning.id(), session.snapshot().periscopeWarnings().get("own-sub").id(),
                "Remaining surfaced must not repeat the notification every second");
        // Even a brief dive must discard the old delivered-contact history.
        setObserverDepth(session, navigation, initialY, initialY < -10 ? "submerged" : "periscope");
        session.update(1.1, radar, navigation, sea);
        assertFalse(session.snapshot().periscopeWarnings().containsKey("own-sub"));
        setObserverDepth(session, navigation, -.78, "surface");
        session.update(1.1, radar, navigation, sea);
        var resurfaced = session.snapshot().periscopeWarnings().get("own-sub");
        assertNotNull(resurfaced);
        assertNotEquals(warning.id(), resurfaced.id());
    }

    private void setObserverDepth(GameSession session, NavigationService navigation, double y, String selectedDepth) {
        session.applyPlayerState(new PlayerStateUpdate("captain", "light", 0, 0, 0, 0, 0, 2, 0, 0,
                true, "submarine", y, 0, null, null, null, null, selectedDepth), navigation, sea);
    }

    @Test void noHumanObserversRequireNoContactSearch() {
        warnings.update(0, List.of(), null, sea);
        assertTrue(warnings.snapshot().isEmpty());
    }

    @Test void gameSessionOnlyEvaluatesHumanObserversAndPublishesIntoTheNormalSnapshot() {
        ShipSetup boat = new ShipSetup("boat", "light", new Vector2(0, 0), 0, "captain", 2, 0, 99,
                "torpedo-boat", 0);
        ShipSetup target = new ShipSetup("sub", "dark", new Vector2(0, 10), 0, "enemy", 2, 0, 99,
                "submarine", -5.58);
        ShipSetup bot = new ShipSetup("bot", "light", new Vector2(10, 0), 0, "bot", 2, 0, 99,
                "torpedo-boat", 0);
        GameSession session = new GameSession(new GameSetup("sighting-test", sea,
                List.of(new FleetSetup("light", List.of(boat, bot)), new FleetSetup("dark", List.of(target))),
                List.of(new Vector2(1000, 1000))));
        session.applyPlayerState(new PlayerStateUpdate("enemy", "dark", 0, 10, 0, 0, 0, 2, 0, 0,
                true, "submarine", -5.58, 0, null, null, null, null, "periscope"), new NavigationService(), sea);
        session.update(.1, radar, new NavigationService(), sea);
        assertEquals(java.util.Set.of("boat"), session.snapshot().periscopeWarnings().keySet());
    }
}
