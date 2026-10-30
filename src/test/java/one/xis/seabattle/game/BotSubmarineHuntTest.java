package one.xis.seabattle.game;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class BotSubmarineHuntTest {
    private final NavigationService navigation = new NavigationService();
    private final WorldMap water = new WorldMap(84001, List.of());
    private final RadarService radar = new RadarService();

    private Ship boat(String id, String team, String controller, String type, double x, double z) {
        Ship s = new Ship(id, team, new Vector2(x, z), 0, controller);
        s.applyPlayerState(new PlayerStateUpdate(controller, team, x, z, 0, 0, 0, 2, 0, 0, true,
                type, 0, 0, null, null, null, null, "surface"), navigation, water);
        return s;
    }

    @Test void recruitsByRadiusWithoutNumberLimitAndRetainsIndependentMinuteLongSearchPoints() {
        var hunt = new BotSubmarineHunt();
        Ship observer = boat("observer", "light", "bot", "torpedo-boat", 0, -40);
        Ship target = boat("target", "dark", "player-human", "submarine", 0, 0);
        List<Ship> neighbors = new ArrayList<>();
        for (int i = 0; i < 8; i++) neighbors.add(boat("friend-" + i, "light", "bot", "torpedo-boat", i * 80, -100));
        Ship outside = boat("outside", "light", "bot", "torpedo-boat", BotSubmarineHunt.RECRUIT_RADIUS + 1, 0);
        Ship human = boat("human", "light", "player-captain", "torpedo-boat", 0, 100);
        Ship sub = boat("friend-sub", "light", "bot", "submarine", 0, 100);
        Ship plane = boat("plane", "light", "bot", "scout-plane", 0, 100);
        Ship enemy = boat("enemy", "dark", "bot", "torpedo-boat", 0, 100);
        neighbors.addAll(List.of(observer, outside, human, sub, plane, enemy));
        AtomicInteger scans = new AtomicInteger();
        hunt.sighting(observer, target, 5, () -> { scans.incrementAndGet(); return neighbors; });
        var original = observer.botNavigation.submarineSearch;
        assertNotNull(original);
        assertEquals(65, original.area().until());
        assertEquals(new Vector2(0, 0), original.area().center());
        assertEquals(9, neighbors.stream().filter(s -> s.botNavigation.submarineSearch != null).count());
        for (Ship ignored : List.of(outside, human, sub, plane, enemy)) assertNull(ignored.botNavigation.submarineSearch);
        target.respawn(new Vector2(500, 500), 0, 0); // A replacement is a new contact, but cannot replace active assignments.
        hunt.sighting(observer, target, 10, () -> { scans.incrementAndGet(); return neighbors; });
        assertSame(original, observer.botNavigation.submarineSearch);
        assertEquals(new Vector2(0, 0), original.area().center());
        assertTrue(original.active(64.999)); assertFalse(original.active(65));
        hunt.sighting(observer, target, 71, () -> { scans.incrementAndGet(); return neighbors; });
        assertNotSame(original, observer.botNavigation.submarineSearch);
        assertEquals(131, observer.botNavigation.submarineSearch.area().until());
        assertEquals(3, scans.get());
    }

    @Test void repeatedObservationsUseOneNeighborhoodQueryAndNeverTrackHiddenMotion() {
        var hunt = new BotSubmarineHunt();
        Ship observer = boat("observer", "light", "bot", "torpedo-boat", 0, -40);
        Ship target = boat("target", "dark", "player-human", "submarine", 0, 0);
        AtomicInteger scans = new AtomicInteger();
        hunt.sighting(observer, target, 0, () -> { scans.incrementAndGet(); return List.of(observer); });
        var original = observer.botNavigation.submarineSearch;
        target.applyPlayerState(new PlayerStateUpdate("player-human", "dark", 800, 800, 1, 10, 0, 8, 0, 0, true,
                "submarine", SeaBattleGameConfig.SUBMARINE_DEEP_Y, 0, null, null, null, null, "submerged"), navigation, water);
        for (int t = 1; t < 60; t++) hunt.sighting(observer, target, t, () -> { scans.incrementAndGet(); return List.of(observer); });
        assertEquals(1, scans.get());
        assertEquals(new Vector2(0, 0), original.area().center());
        assertEquals(60, original.area().until());
        Ship other = boat("other", "dark", "other-human", "submarine", 0, 20);
        hunt.sighting(observer, other, 10, () -> List.of(observer));
        assertSame(original, observer.botNavigation.submarineSearch, "Another report cannot steal this boat's minute");
        observer.respawn(new Vector2(0, -40), 0, 0);
        assertNull(observer.botNavigation.submarineSearch, "A replacement does not inherit the lost boat's search");
    }

    @SuppressWarnings("unchecked")
    private List<Ship> ships(GameSession session) throws Exception {
        Method m = GameSession.class.getDeclaredMethod("allShips"); m.setAccessible(true);
        return (List<Ship>) m.invoke(session);
    }
    private void depth(Ship sub, double x, double z, String depth, double speed) {
        sub.applyPlayerState(new PlayerStateUpdate(sub.controlledBy(), sub.teamId(), x, z, 0, speed, 0, speed > 0 ? 8 : 2,
                0, 0, true, "submarine", depth.equals("surface") ? 0 : depth.equals("periscope") ? -5.58 : SeaBattleGameConfig.SUBMARINE_DEEP_Y,
                0, null, null, null, null, depth), navigation, water);
    }
    private GameSession scenario(boolean parallel, boolean visible) throws Exception {
        List<ShipSetup> bots = new ArrayList<>();
        bots.add(new ShipSetup("detector", "light", new Vector2(0, -60), 0, "bot", 2, 0, 999, "torpedo-boat", 0));
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4, radius = 400 + i * 80;
            Vector2 p = Vector2.fromHeading(angle).scale(radius);
            bots.add(new ShipSetup("hunter-" + i, "light", p, MathSupport.normalizeAngle(angle + Math.PI), "bot", 2, 0, 999, "torpedo-boat", 0));
        }
        bots.add(new ShipSetup("outside", "light", new Vector2(BotSubmarineHunt.RECRUIT_RADIUS + 20, 0), Math.PI, "bot", 2, 0, 999, "torpedo-boat", 0));
        var session = new GameSession(new GameSetup("hunt-integration", water, List.of(new FleetSetup("light", bots),
                new FleetSetup("dark", List.of(new ShipSetup("sub", "dark", new Vector2(0, 0), 0, "player-sub", 2, 0, 999, "submarine", 0)))),
                List.of(new Vector2(3000, 3000))), 1, parallel);
        for (Ship s : ships(session)) {
            s.nextFireTime(9999);
            if (s.id().equals("sub")) depth(s, 0, 0, visible ? "periscope" : "submerged", 10);
        }
        return session;
    }

    @Test void realSightingRecruitsMoreThanFourBoatsAndTheyActuallyReachAndSearchTheArea() throws Exception {
        for (boolean parallel : List.of(false, true)) {
            GameSession session = scenario(parallel, true);
            session.update(0, radar, navigation, water);
            List<Ship> all = ships(session);
            List<Ship> recruited = all.stream().filter(s -> s.botNavigation.submarineSearch != null).toList();
            assertEquals(9, recruited.size());
            assertNull(all.stream().filter(s -> s.id().equals("outside")).findFirst().orElseThrow().botNavigation.submarineSearch);
            var area = recruited.get(0).botNavigation.submarineSearch.area();
            Ship target = all.stream().filter(s -> s.id().equals("sub")).findFirst().orElseThrow();
            depth(target, 3000, 3000, "submerged", 0);
            Set<String> arrived = new HashSet<>(), crossed = new HashSet<>();
            int fullSpeedApproachSamples = 0;
            for (int t = 0; t < 590; t++) {
                session.update(.1, radar, navigation, water);
                for (Ship hunter : recruited) {
                    var search = hunter.botNavigation.submarineSearch;
                    if (search != null && search.waypoint() > 0) arrived.add(hunter.id());
                    if (search != null && search.waypoint() > 1) crossed.add(hunter.id());
                    if (hunter.position().distanceTo(area.center()) > 10 * BotNavigation.SHIP_LENGTH && hunter.snapshot().engineOrder() == 8)
                        fullSpeedApproachSamples++;
                }
            }
            assertTrue(arrived.size() > 4, "Not merely theoretical: arrivals=" + arrived + ", parallel=" + parallel);
            assertTrue(crossed.size() >= 2, "Boats must cross the search area instead of stopping at the first point: " + crossed);
            assertTrue(fullSpeedApproachSamples > 300, "Travel uses top gear, not the slow map-centre patrol");
            assertEquals(new Vector2(0, 0), area.center(), "Never follow the teleported hidden position");
            session.update(1.2, radar, navigation, water);
            assertTrue(recruited.stream().allMatch(s -> s.botNavigation.submarineSearch == null));
            System.out.println("Hunt " + (parallel ? "parallel" : "serial") + ": " + arrived.size() + "/9 arrived; " + crossed.size() + " crossed; full-speed approach samples=" + fullSpeedApproachSamples);
        }
    }

    @Test void surfacedHumanSubmarineSightingAlsoStartsTheSameMinuteLongSearch() throws Exception {
        GameSession session = scenario(false, false);
        Ship sub = ships(session).stream().filter(s -> s.id().equals("sub")).findFirst().orElseThrow();
        depth(sub, 0, 0, "surface", 0);
        session.update(0, radar, navigation, water);
        List<Ship> recruited = ships(session).stream().filter(s -> s.botNavigation.submarineSearch != null).toList();
        assertEquals(9, recruited.size());
        var area = recruited.get(0).botNavigation.submarineSearch.area();
        depth(sub, 3000, 3000, "submerged", 0);
        for (int t = 0; t < 120; t++) session.update(.1, radar, navigation, water);
        for (Ship boat : recruited) {
            assertSame(area, boat.botNavigation.submarineSearch.area());
            assertEquals(new Vector2(0, 0), area.center());
            assertEquals(60, area.until());
        }
    }

    @Test void distantStrategicSurfaceTargetIsNotMistakenForALocalSighting() throws Exception {
        GameSession session = scenario(false, false);
        Ship sub = ships(session).stream().filter(s -> s.id().equals("sub")).findFirst().orElseThrow();
        depth(sub, 3000, 3000, "surface", 0);
        session.update(0, radar, navigation, water);
        assertTrue(ships(session).stream().allMatch(s -> s.botNavigation.submarineSearch == null));
    }

    @Test void unseenDeepSubmarineDoesNotCreateSearchOrders() throws Exception {
        GameSession session = scenario(false, false);
        for (int t = 0; t < 40; t++) session.update(.1, radar, navigation, water);
        assertTrue(ships(session).stream().allMatch(s -> s.botNavigation.submarineSearch == null));
    }

    @Test void localCombatAndEscapeOverrideTheSearchWithoutCancellingOrExtendingIt() throws Exception {
        GameSession session = scenario(false, true);
        session.update(0, radar, navigation, water);
        List<Ship> all = ships(session);
        Ship hunter = all.stream().filter(s -> s.id().equals("hunter-0")).findFirst().orElseThrow();
        var search = hunter.botNavigation.submarineSearch;
        Ship closeEnemy = boat("close", "dark", "bot", "torpedo-boat", hunter.position().x() + 30, hunter.position().z());
        Method course = GameSession.class.getDeclaredMethod("desiredBotCourse", Ship.class, Ship.class, WorldMap.class);
        course.setAccessible(true);
        var local = (BotNavigation.Course) course.invoke(session, hunter, closeEnemy, water);
        assertEquals(Math.PI / 2, local.heading(), 1e-8);
        assertSame(search, hunter.botNavigation.submarineSearch);
        Ship distantEnemy = boat("distant", "dark", "player-other", "torpedo-boat", hunter.position().x() + 1000, hunter.position().z());
        var searchCourse = (BotNavigation.Course) course.invoke(session, hunter, distantEnemy, water);
        assertEquals(8, searchCourse.engineOrder(), "A routine distant target must not slow or steal the search");
        assertNotEquals(Math.PI / 2, searchCourse.heading(), 1e-8);
        hunter.botReverseUntil = 5;
        Ship sub = all.stream().filter(s -> s.id().equals("sub")).findFirst().orElseThrow();
        depth(sub, 3000, 3000, "submerged", 0);
        session.update(.1, radar, navigation, water);
        assertEquals(0, hunter.snapshot().engineOrder(), "Existing escape is higher priority than top-speed search travel");
        assertSame(search, hunter.botNavigation.submarineSearch);
        assertEquals(60, search.area().until());
    }
}
