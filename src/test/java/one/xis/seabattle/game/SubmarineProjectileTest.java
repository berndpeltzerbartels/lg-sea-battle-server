package one.xis.seabattle.game;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class SubmarineProjectileTest {
    private static final double SCALE = SeaBattleGameConfig.TORPEDO_BOAT_SCALE;

    @Test
    void waterTravelContinuesBeyondHullLengthButStillExpires() {
        for (String id : List.of("flak-test", "cannon-test")) {
            FlakProjectile projectile = new FlakProjectile(id, "light", "shooter", 0, 0, 0, 30, -40, 0, 0);
            projectile.update(1);
            assertEquals("flying", projectile.state());
            assertEquals(50, Math.hypot(projectile.x(), projectile.y()), 1e-9);
            projectile.update(20);
            assertEquals("expired", projectile.state());
            assertEquals(50 * (id.startsWith("cannon-") ? 18 : 8), Math.hypot(projectile.x(), projectile.y()), 1e-9);
            assertTrue(projectile.hasCollisionSegment(), "The last clipped segment must still be tested");
            double end = projectile.x();
            projectile.update(.1);
            assertFalse(projectile.hasCollisionSegment());
            assertEquals(end, projectile.x());
        }
    }

    @Test
    void waterRangeDoesNotDependOnTickSize() {
        FlakProjectile smallSteps = new FlakProjectile("cannon-a", "light", "shooter", 0, 2, 0, 30, -40, 0, 0);
        FlakProjectile largeStep = new FlakProjectile("cannon-b", "light", "shooter", 0, 2, 0, 30, -40, 0, 0);
        for (int i = 0; i < 100; i++) smallSteps.update(.01);
        largeStep.update(1);
        assertEquals(largeStep.x(), smallSteps.x(), 1e-9);
        assertEquals(largeStep.y(), smallSteps.y(), 1e-9);
    }

    @Test
    void hullAndBothThinPeriscopesAreHitButEmptySpaceIsNot() {
        Ship submarine = submarine(0, 0, 0, 0);
        assertTrue(Double.isFinite(cross(submarine, .2, 0)));
        assertTrue(Double.isFinite(cross(submarine, 1.9, .022)));
        assertTrue(Double.isFinite(cross(submarine, 1.9, .184)));
        assertFalse(Double.isFinite(cross(submarine, 1.9, .10)), "Gap between periscopes");
        assertFalse(Double.isFinite(cross(submarine, 1.9, 3)), "No torpedo boat superstructure");
        assertFalse(Double.isFinite(cross(submarine, -.4, 0)), "Below the hull");
        assertFalse(Double.isFinite(cross(submarine, .2, 5.2)), "Past the bow");
    }

    @Test
    void collisionFollowsPositionHeadingAndActualDepth() {
        Ship submarine = submarine(100, 200, Math.PI / 2, -9);
        double t = SubmarineProjectileGeometry.hitFraction(100, -9, 210, 100, -9, 190, submarine);
        assertTrue(Double.isFinite(t));
        assertFalse(Double.isFinite(SubmarineProjectileGeometry.hitFraction(100, 0, 210, 100, 0, 190, submarine)));
    }

    @Test
    void sessionUsesSubmarineGeometryForBothWeaponsAlongTravelledSegment() throws Exception {
        GameSession session = new GameSession(new GameSetup("sub-hit", new WorldMap(99, List.of()), List.of(), List.of()));
        Method targetHit = GameSession.class.getDeclaredMethod("flakProjectileHitsTarget", FlakProjectile.class, Ship.class);
        targetHit.setAccessible(true);
        for (String id : List.of("flak-test", "cannon-test")) {
            FlakProjectile projectile = new FlakProjectile(id, "light", "shooter", -8, 0, 0, 30, -10, 0, 0);
            projectile.update(1);
            assertTrue(((Optional<?>) targetHit.invoke(session, projectile, submarine(0, 0, 0, -2.5))).isPresent());
            assertTrue(((Optional<?>) targetHit.invoke(session, projectile, submarine(14, 0, 0, -7.3))).isPresent(),
                    "Hull beyond the former half-length limit must now be reachable");
            assertTrue(((Optional<?>) targetHit.invoke(session, projectile, submarine(30, 0, 0, -12))).isEmpty());
        }
    }

    private static double cross(Ship ship, double y, double z) {
        return SubmarineProjectileGeometry.hitFraction(-10, y * SCALE, z * SCALE, 10, y * SCALE, z * SCALE, ship);
    }

    @Test
    void underwaterHitAndPeriscopeHitsReachTheKillFeed() throws Exception {
        for (boolean cannon : List.of(false, true)) {
            for (double mastZ : new double[]{.022, .184, Double.NaN}) {
                GameSession session = new GameSession(new GameSetup("sub-kill", new WorldMap(99, List.of()),
                        List.of(new FleetSetup("light", List.of(new ShipSetup("shooter", "light", new Vector2(-100, 0),
                                0, "captain", 2, 0, 1000, "torpedo-boat", 0))),
                                new FleetSetup("dark", List.of(new ShipSetup("sub", "dark", new Vector2(0, 0),
                                        0, "diver", 2, 0, 1000, "submarine", 0)))), List.of()));
                Method allShips = GameSession.class.getDeclaredMethod("allShips");
                allShips.setAccessible(true);
                @SuppressWarnings("unchecked") List<Ship> ships = (List<Ship>) allShips.invoke(session);
                Ship target = ships.stream().filter(s -> s.id().equals("sub")).findFirst().orElseThrow();
                boolean underwater = Double.isNaN(mastZ);
                double depth = underwater ? -2.5 : -1.86 * SCALE;
                target.applyPlayerState(new PlayerStateUpdate("diver", "dark", 0, 0, 0, 0, 0, 2, 0, 0,
                        false, "submarine", depth), new NavigationService(), session.worldMap());
                FlakFireRequest shot = new FlakFireRequest("captain", "light", "shooter",
                        -8, underwater ? .1 : depth + 1.92 * SCALE, underwater ? 0 : mastZ * SCALE,
                        1000, underwater ? -300 : 0, 0);
                if (cannon) session.applyFireCannon(shot, "gunner");
                else session.applyFireFlak(shot, "gunner");
                session.update(.02, new RadarService(), new NavigationService(), session.worldMap());
                assertNotEquals("active", target.state());
                assertEquals(1, session.snapshot().flakHits().size());
                assertEquals("sub", session.snapshot().flakHits().get(0).targetShipId());
                assertEquals("ship-critical-hit", session.snapshot().flakImpacts().get(0).reason());
            }
        }
    }

    private static Ship submarine(double x, double z, double heading, double y) {
        Ship ship = new Ship("sub", "dark", new Vector2(x, z), heading, "human");
        ship.applyPlayerState(new PlayerStateUpdate("player", "dark", x, z, heading, 0, 0, 2, 0, 0, false, "submarine", y),
                new NavigationService(), new WorldMap(99, List.of()));
        return ship;
    }
}
