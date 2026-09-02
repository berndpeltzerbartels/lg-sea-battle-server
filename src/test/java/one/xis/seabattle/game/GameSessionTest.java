package one.xis.seabattle.game;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GameSessionTest {

    private static final double TORPEDO_BOAT_MODEL_SCALE = SeaBattleGameConfig.TORPEDO_BOAT_SCALE;
    private static final double SCOUT_PLANE_START_Y = 220;
    private static final double TORPEDO_BOAT_MODEL_WATERLINE_Y = -0.2;
    private static final int ENGINE_FULL_ASTERN = 0;
    private static final int ENGINE_STOP = 2;
    private static final int ENGINE_SLOW = 3;
    private static final int ENGINE_ONE_THIRD = 4;
    private static final int ENGINE_HALF = 5;
    private static final int ENGINE_TWO_THIRDS = 6;
    private static final int ENGINE_FULL = 7;
    private static final int ENGINE_FLANK = 8;

    private final RadarService radarService = new RadarService();
    private final NavigationService navigationService = new NavigationService();

    @Test
    void usesInjectedWorldAndFleets() {
        GameSetup setup = new GameSetup(
                "tiny-test",
                new WorldMap(9001, List.of()),
                List.of(
                        new FleetSetup("red", List.of(ship("red-1", "red", -10, 0, 0, 2, 0))),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 10, 0, Math.PI, 2, 0)))
                ),
                List.of(new Vector2(-10, 0), new Vector2(10, 0))
        );

        GameSession session = new GameSession(setup);
        GameSnapshot snapshot = session.snapshot();

        assertEquals("tiny-test", snapshot.sessionId());
        assertEquals(9001, session.worldMap().version());
        assertEquals(2, snapshot.ships().size());
        assertEquals("red-1", snapshot.ships().get(0).id());
        assertEquals("blue-1", snapshot.ships().get(1).id());
    }

    @Test
    void firstPlayerCommandMayFireBeforePositionSyncAssignedShip() {
        GameSession session = new GameSession(new GameSetup(
                "first-fire-test",
                new WorldMap(9025, List.of()),
                List.of(new FleetSetup("light", List.of(
                        ship("light-1", "light", 0, 0, 0, "bot", 5, 0, 0)
                ))),
                List.of(new Vector2(0, 0))
        ));

        GameSnapshot snapshot = session.fireTorpedo(new FireTorpedoRequest("player-BP-test", "light"));

        ShipSnapshot ship = findShip(snapshot, "light-1");
        assertNotNull(ship);
        assertEquals("player-BP-test", ship.controlledBy());
        assertEquals(12, ship.torpedoesRemaining());
        assertEquals(1, snapshot.torpedoes().size());
    }

    @Test
    void playerAssignmentSkipsScoutPlanes() {
        GameSession session = new GameSession(new GameSetup(
                "player-assignment-skips-scout-planes-test",
                new WorldMap(90255, List.of()),
                List.of(new FleetSetup("light", List.of(
                        ship("light-F1", "light", -20, 0, 0, "bot", 7, 0, 0, "scout-plane"),
                        ship("light-S1", "light", 20, 0, 0, "bot", 5, 0, 0)
                ))),
                List.of(new Vector2(20, 0))
        ));

        GameSnapshot snapshot = session.fireTorpedo(new FireTorpedoRequest("player-BP-test", "light"));

        ShipSnapshot assignedShip = snapshot.ships().stream()
                .filter(ship -> "player-BP-test".equals(ship.controlledBy()))
                .findFirst()
                .orElseThrow();
        assertEquals("light-S1", assignedShip.id());
        assertEquals("torpedo-boat", assignedShip.vehicleType());
        assertEquals(1, snapshot.torpedoes().size());
    }

    @Test
    void playerCanJoinAsSubmarineWithBoatMovementRules() {
        GameSession session = new GameSession(new GameSetup(
                "player-submarine-assignment-test",
                new WorldMap(90256, List.of()),
                List.of(new FleetSetup("light", List.of(
                        ship("light-S1", "light", 0, 0, 0, "bot", ENGINE_STOP, 0, 99)
                ))),
                List.of(new Vector2(0, 0))
        ));

        GameSnapshot snapshot = session.updatePlayerState(new PlayerStateUpdate(
                "player-BP-test", "light", 12, 0, 0, 6, 0, ENGINE_FULL, 0, 0, false, "submarine", 80
        ), navigationService, session.worldMap());

        ShipSnapshot ship = findShip(snapshot, "light-S1");
        assertEquals("player-BP-test", ship.controlledBy());
        assertEquals("submarine", ship.vehicleType());
        assertEquals(0, ship.y(), 0.001);
    }

    @Test
    void playerVehicleAssignmentImmediatelyConvertsAvailableBoatSlotToSubmarine() {
        GameSession session = new GameSession(new GameSetup(
                "player-submarine-start-assignment-test",
                new WorldMap(90257, List.of()),
                List.of(new FleetSetup("light", List.of(
                        ship("light-S1", "light", 0, 0, 0, "bot", ENGINE_STOP, 0, 99)
                ))),
                List.of(new Vector2(0, 0))
        ));

        session.assignPlayerVehicle("player-BP-test", "light", "submarine");

        ShipSnapshot ship = findShip(session.snapshot(), "light-S1");
        assertEquals("player-BP-test", ship.controlledBy());
        assertEquals("submarine", ship.vehicleType());
        assertEquals(0, ship.y(), 0.001);
    }

    @Test
    void scoutPlanePlayerCanFireAirTorpedo() {
        GameSession session = new GameSession(new GameSetup(
                "scout-plane-fire-test",
                new WorldMap(9026, List.of()),
                List.of(new FleetSetup("light", List.of(
                        ship("light-1", "light", 0, 0, 0, "bot", 7, 0, 0, "scout-plane")
                ))),
                List.of(new Vector2(0, 0))
        ));

        session.updatePlayerState(
                new PlayerStateUpdate("player-BP-test", "light", 0, 0, 0, 8, 0, 7, 0, 0, false, "scout-plane", 80),
                navigationService,
                session.worldMap()
        );
        GameSnapshot snapshot = session.fireTorpedo(new FireTorpedoRequest("player-BP-test", "light", "scout-plane"));

        ShipSnapshot ship = findShip(snapshot, "light-1");
        assertNotNull(ship);
        assertEquals("scout-plane", ship.vehicleType());
        assertEquals(1, snapshot.torpedoes().size());
        assertEquals("light-1", snapshot.torpedoes().get(0).shipId());
        assertEquals("airborne", snapshot.torpedoes().get(0).state());
        assertTrue(snapshot.torpedoes().get(0).y() > 70);
    }

    @Test
    void scoutPlanePlayerAirTorpedoUsesOneAndHalfSecondCooldown() {
        GameSession session = new GameSession(new GameSetup(
                "scout-plane-torpedo-cooldown-test",
                new WorldMap(9028, List.of()),
                List.of(new FleetSetup("light", List.of(
                        ship("light-1", "light", 0, 0, 0, "bot", 7, 0, 0, "scout-plane")
                ))),
                List.of(new Vector2(0, 0))
        ));

        session.updatePlayerState(
                new PlayerStateUpdate("player-BP-test", "light", 0, 0, 0, 8, 0, 7, 0, 0, false, "scout-plane", 80),
                navigationService,
                session.worldMap()
        );

        GameSnapshot first = session.fireTorpedo(new FireTorpedoRequest("player-BP-test", "light", "scout-plane"));
        GameSnapshot blocked = session.fireTorpedo(new FireTorpedoRequest("player-BP-test", "light", "scout-plane"));
        session.update(1.49, radarService, navigationService, session.worldMap());
        GameSnapshot stillBlocked = session.fireTorpedo(new FireTorpedoRequest("player-BP-test", "light", "scout-plane"));
        session.update(0.02, radarService, navigationService, session.worldMap());
        GameSnapshot released = session.fireTorpedo(new FireTorpedoRequest("player-BP-test", "light", "scout-plane"));

        assertEquals(1, first.torpedoes().size());
        assertEquals(1, blocked.torpedoes().size());
        assertEquals(1, stillBlocked.torpedoes().size());
        assertEquals(2, released.torpedoes().size());
    }

    @Test
    void airborneTorpedoCanHitScoutPlaneBeforeReachingWater() {
        GameSession session = new GameSession(new GameSetup(
                "airborne-torpedo-plane-hit-test",
                new WorldMap(9029, List.of()),
                List.of(
                        new FleetSetup("light", List.of(
                                new ShipSetup("light-F1", "light", new Vector2(0, 0), Math.PI / 2,
                                        "bot", 7, 0, 0, "scout-plane", 80)
                        )),
                        new FleetSetup("dark", List.of(
                                new ShipSetup("dark-F1", "dark", new Vector2(11, 0), Math.PI,
                                        "scenario", 7, 0, 99, "scout-plane", 77.6)
                        ))
                ),
                List.of(new Vector2(0, 0), new Vector2(25, 0))
        ));

        session.updatePlayerState(
                new PlayerStateUpdate("player-BP-test", "light", 0, 0, Math.PI / 2, 30, 0, 7, 0, 0,
                        false, "scout-plane", 80),
                navigationService,
                session.worldMap()
        );
        session.fireTorpedo(new FireTorpedoRequest(
                "player-BP-test", "light", "scout-plane", 0, 0, Math.PI / 2, 30, 0, 7, 0, 80, 0, null, 1
        ));

        GameSnapshot snapshot = session.snapshot();
        for (int i = 0; i < 20 && "active".equals(findShip(snapshot, "dark-F1").state()); i += 1) {
            session.update(0.05, radarService, navigationService, session.worldMap());
            snapshot = session.snapshot();
        }

        assertEquals("sunk", findShip(snapshot, "dark-F1").state());
        assertTrue(snapshot.torpedoImpacts().stream()
                .anyMatch(impact -> "plane-hit".equals(impact.reason()) && "dark-F1".equals(impact.targetShipId())));
    }

    @Test
    void scoutPlaneBombsStartBelowPlaneAndNearReleaseBay() throws Exception {
        double planeHeading = Math.PI / 2;
        double planeY = 140;
        Vector2 planePosition = new Vector2(12, -18);
        GameSession session = new GameSession(new GameSetup(
                "bomb-release-geometry-test",
                new WorldMap(9030, List.of()),
                List.of(new FleetSetup("dark", List.of(
                        new ShipSetup("dark-F1", "dark", planePosition, planeHeading,
                                "bot", 7, 0, 0, "scout-plane", planeY)
                ))),
                List.of(planePosition)
        ));

        Ship plane = shipEntity(session, "dark-F1");
        dropBombsFromScoutPlane(session, plane, 4.5);
        session.updateIdle(0, radarService, navigationService, session.worldMap());

        GameSnapshot snapshot = session.snapshot();
        assertEquals(1, snapshot.bombs().size());
        BombSnapshot bomb = snapshot.bombs().get(0);
        double dx = bomb.launchX() - planePosition.x();
        double dz = bomb.launchZ() - planePosition.z();
        double forward = dx * Math.sin(planeHeading) + dz * Math.cos(planeHeading);
        double right = dx * Math.cos(planeHeading) - dz * Math.sin(planeHeading);

        assertTrue(bomb.launchY() < planeY, "bomb must be released below the aircraft, not above it");
        assertTrue(bomb.launchY() > planeY - 2.0, "bomb release should stay close to the bay");
        assertEquals(bomb.launchY(), bomb.y(), 0.001, "fresh bomb visual state should start at its launch height");
        assertTrue(forward >= 0.0 && forward <= 1.0, "bomb release should be only slightly ahead of the aircraft");
        assertTrue(Math.abs(right) <= 0.4, "bomb release should stay near the aircraft centerline");
    }

    @Test
    void humanScoutPlaneCanDropBombsForScenarioObservation() {
        GameSession session = new GameSession(new GameSetup(
                "human-bomb-release-test",
                new WorldMap(9032, List.of()),
                List.of(new FleetSetup("light", List.of(
                        new ShipSetup("light-F1", "light", new Vector2(0, 0), 0,
                                "player-human-bomb", 7, 0, 0, "scout-plane", 150)
                ))),
                List.of(new Vector2(0, 0))
        ));

        GameSnapshot snapshot = session.dropBomb(new BombDropRequest(
                "player-human-bomb",
                "light",
                0,
                150,
                0,
                0,
                30,
                0,
                0,
                "scout-plane"
        ));

        assertEquals(1, snapshot.bombs().size());
        assertEquals("light-F1", snapshot.bombs().get(0).shipId());
    }

    @Test
    void sleepingBotShipAcceleratesHardWhenEnemyAppearsNearby() {
        GameSession session = new GameSession(new GameSetup(
                "sleeping-bot-threat-response-test",
                new WorldMap(9031, List.of()),
                List.of(
                        new FleetSetup("red", List.of(
                                ship("red-1", "red", 0, 0, 0, "bot", 2, 0, 0)
                        )),
                        new FleetSetup("blue", List.of(
                                ship("blue-1", "blue", 0, 180, Math.PI, "player-BP-test", 2, 0, 99)
                        ))
                ),
                List.of(new Vector2(0, 0), new Vector2(0, 180))
        ));

        session.update(0.1, radarService, navigationService, session.worldMap());

        ShipSnapshot bot = findShip(session.snapshot(), "red-1");
        assertNotNull(bot);
        assertEquals(ENGINE_FULL, bot.engineOrder());
    }

    @Test
    void restingEscortBotShipDoesNotSleepForever() {
        GameSession session = new GameSession(new GameSetup(
                "resting-escort-wake-test",
                new WorldMap(90311, List.of()),
                List.of(new FleetSetup("red", List.of(
                        ship("red-escort", "red", 0, 30, 0, "bot", ENGINE_STOP, 0, 99),
                        ship("red-human", "red", 0, 0, 0, "player-BPB", ENGINE_STOP, 0, 99)
                ))),
                List.of(new Vector2(0, 30), new Vector2(0, 0))
        ));

        boolean woke = false;
        for (double elapsed = 0; elapsed < 70; elapsed += 0.25) {
            session.update(0.25, radarService, navigationService, session.worldMap());
            ShipSnapshot escort = findShip(session.snapshot(), "red-escort");
            if (escort != null && escort.engineOrder() > ENGINE_STOP) {
                woke = true;
                break;
            }
        }

        assertTrue(woke, "resting escort bot should occasionally move instead of sleeping forever");
    }

    @Test
    void idleBotShipPeriodicallyHuntsDistantHumanPlayer() {
        GameSession session = new GameSession(new GameSetup(
                "idle-bot-human-hunt-wake-test",
                new WorldMap(90312, List.of()),
                List.of(
                        new FleetSetup("red", List.of(
                                ship("red-1", "red", 0, 0, 0, "bot", ENGINE_STOP, 0, 99)
                        )),
                        new FleetSetup("blue", List.of(
                                ship("blue-human", "blue", 0, 13000, Math.PI, "player-BPB", ENGINE_STOP, 0, 99)
                        ))
                ),
                List.of(new Vector2(0, 0), new Vector2(0, 13000))
        ));

        boolean hunted = false;
        for (double elapsed = 0; elapsed < 70; elapsed += 0.25) {
            session.update(0.25, radarService, navigationService, session.worldMap());
            ShipSnapshot bot = findShip(session.snapshot(), "red-1");
            if (bot != null && bot.engineOrder() == ENGINE_FULL) {
                hunted = true;
                break;
            }
        }

        assertTrue(hunted, "idle bot should periodically head toward a distant human player");
    }

    @Test
    void idleBotShipPeriodicallyPatrolsWhenNoHumanPlayerExists() {
        GameSession session = new GameSession(new GameSetup(
                "idle-bot-patrol-wake-test",
                new WorldMap(90313, List.of()),
                List.of(new FleetSetup("red", List.of(
                        ship("red-1", "red", 0, 0, 0, "bot", ENGINE_STOP, 0, 99)
                ))),
                List.of(new Vector2(0, 0))
        ));

        boolean patrolled = false;
        for (double elapsed = 0; elapsed < 70; elapsed += 0.25) {
            session.update(0.25, radarService, navigationService, session.worldMap());
            ShipSnapshot bot = findShip(session.snapshot(), "red-1");
            if (bot != null && bot.engineOrder() >= ENGINE_HALF) {
                patrolled = true;
                break;
            }
        }

        assertTrue(patrolled, "idle bot without human targets should periodically patrol");
    }

    @Test
    void botDoesNotFireTorpedoThroughFriendlyShipOnStaticFiringLine() {
        GameSession session = new GameSession(new GameSetup(
                "bot-friendly-fire-line-test",
                new WorldMap(9033, List.of()),
                List.of(
                        new FleetSetup("red", List.of(
                                ship("red-shooter", "red", 0, 0, 0, "bot", ENGINE_HALF, 0, 0),
                                ship("red-screen", "red", 0, 82, 0, "bot", ENGINE_STOP, 0, 99)
                        )),
                        new FleetSetup("blue", List.of(
                                ship("blue-target", "blue", 0, 260, Math.PI, "player-BP-test", ENGINE_STOP, 0, 99)
                        ))
                ),
                List.of(new Vector2(0, 0), new Vector2(0, 82), new Vector2(0, 260))
        ));

        session.update(0.1, radarService, navigationService, session.worldMap());

        assertTrue(session.snapshot().torpedoes().isEmpty());
    }

    @Test
    void botDoesNotFireTorpedoWhenEnemyIsOutsideStaticFiringLine() {
        GameSession session = new GameSession(new GameSetup(
                "bot-empty-torpedo-line-test",
                new WorldMap(9034, List.of()),
                List.of(
                        new FleetSetup("red", List.of(
                                ship("red-shooter", "red", 0, 0, 0, "bot", ENGINE_HALF, 0, 0)
                        )),
                        new FleetSetup("blue", List.of(
                                ship("blue-target", "blue", 95, 95, Math.PI, "player-BP-test", ENGINE_STOP, 0, 99)
                        ))
                ),
                List.of(new Vector2(0, 0), new Vector2(95, 95))
        ));

        session.update(0.1, radarService, navigationService, session.worldMap());

        assertTrue(session.snapshot().torpedoes().isEmpty());
    }

    @Test
    void torpedoBoatPlayerCanFireVisibleFlakProjectile() {
        GameSession session = new GameSession(new GameSetup(
                "flak-fire-test",
                new WorldMap(9027, List.of()),
                List.of(new FleetSetup("light", List.of(
                        ship("light-1", "light", 0, 0, 0, "bot", 5, 0, 0)
                ))),
                List.of(new Vector2(0, 0))
        ));

        session.updatePlayerState(
                new PlayerStateUpdate("player-BP-test", "light", 0, 0, 0, 4, 0, 5, 0, 0, false, "torpedo-boat"),
                navigationService,
                session.worldMap()
        );
        GameSnapshot snapshot = session.fireFlak(new FlakFireRequest(
                "player-BP-test", "light", "light-1", 0, 1.5 * TORPEDO_BOAT_MODEL_SCALE, -3 * TORPEDO_BOAT_MODEL_SCALE, 0, 60, 95, 0.7, 0.4
        ));

        assertEquals(1, snapshot.flakProjectiles().size());
        FlakProjectileSnapshot projectile = snapshot.flakProjectiles().get(0);
        assertEquals("light-1", projectile.shipId());
        assertEquals(1.5 * TORPEDO_BOAT_MODEL_SCALE, projectile.y(), 0.01);
        ShipSnapshot ship = findShip(snapshot, "light-1");
        assertEquals(0.7, ship.flakYaw(), 0.01);
        assertEquals(0.4, ship.flakPitch(), 0.01);
    }

    @Test
    void cannonFirePublishesWeaponAim() {
        GameSession session = new GameSession(new GameSetup(
                "cannon-aim-test",
                new WorldMap(9045, List.of()),
                List.of(new FleetSetup("light", List.of(
                        ship("light-1", "light", 0, 0, 0, "bot", 5, 0, 0)
                ))),
                List.of(new Vector2(0, 0))
        ));

        session.updatePlayerState(
                new PlayerStateUpdate("player-BP-test", "light", 0, 0, 0, 4, 0, 5, 0, 0, false, "torpedo-boat"),
                navigationService,
                session.worldMap()
        );
        GameSnapshot snapshot = session.fireCannon(new FlakFireRequest(
                "player-BP-test", "light", "light-1", 0, 1.5 * TORPEDO_BOAT_MODEL_SCALE, -3 * TORPEDO_BOAT_MODEL_SCALE, 0, 72, 120, -0.5, 0.32
        ));

        assertEquals(1, snapshot.flakProjectiles().size());
        ShipSnapshot ship = findShip(snapshot, "light-1");
        assertEquals(-0.5, ship.cannonYaw(), 0.01);
        assertEquals(0.32, ship.cannonPitch(), 0.01);
    }

    @Test
    void flakHitScoutPlaneIsReportedAndSinksIt() {
        GameSession session = new GameSession(new GameSetup(
                "flak-hit-plane-test",
                new WorldMap(9028, List.of()),
                List.of(
                        new FleetSetup("light", List.of(
                                ship("light-1", "light", 0, 0, 0, "bot", 5, 0, 0)
                        )),
                        new FleetSetup("dark", List.of(
                                ship("dark-1", "dark", 3, 47, 0, "bot", 5, 0, 0)
                        ))
                ),
                List.of(new Vector2(0, 0), new Vector2(3, 47))
        ));

        session.updatePlayerState(
                new PlayerStateUpdate("player-gunner", "light", 0, 0, 0, 4, 0, 5, 0, 0, false, "torpedo-boat"),
                navigationService,
                session.worldMap()
        );
        session.updatePlayerState(
                new PlayerStateUpdate("player-plane", "dark", 3, 47, 0, 14, 0, 7, 0, 0, false, "scout-plane", 30),
                navigationService,
                session.worldMap()
        );
        session.fireFlak(new FlakFireRequest(
                "player-gunner", "light", "light-1", 3, 1.5, 0, 0, 60, 95
        ));

        GameSnapshot snapshot = session.snapshot();
        for (int i = 0; i < 20 && snapshot.flakHits().isEmpty(); i += 1) {
            session.update(0.05, radarService, navigationService, session.worldMap());
            snapshot = session.snapshot();
        }

        assertFalse(snapshot.flakHits().isEmpty());
        assertEquals("dark-1", snapshot.flakHits().get(0).targetShipId());
        ShipSnapshot plane = findShip(snapshot, "dark-1");
        assertNotNull(plane);
        assertEquals("sunk", plane.state());
        assertEquals("scout-plane", plane.vehicleType());
    }

    @Test
    void clientReportedCannonPlaneHitIsAuthoritative() {
        GameSession session = new GameSession(new GameSetup(
                "client-plane-hit-test",
                new WorldMap(9044, List.of()),
                List.of(
                        new FleetSetup("light", List.of(
                                ship("light-1", "light", 0, 0, 0, "bot", 5, 0, 0)
                        )),
                        new FleetSetup("dark", List.of(
                                ship("dark-1", "dark", 0, 80, 0, "bot", 7, 0, 0, "scout-plane")
                        ))
                ),
                List.of(new Vector2(0, 0), new Vector2(0, 80))
        ));

        session.updatePlayerState(
                new PlayerStateUpdate("player-gunner", "light", 0, 0, 0, 4, 0, 5, 0, 0, false, "torpedo-boat"),
                navigationService,
                session.worldMap()
        );
        GameSnapshot snapshot = session.reportClientPlaneHit(new ClientPlaneHitRequest(
                "player-gunner", "light", "light-1", "dark-1", "cannon", 0, 32, 80
        ));

        assertEquals("sunk", findShip(snapshot, "dark-1").state());
        assertEquals(1, snapshot.flakHits().size());
        assertTrue(snapshot.flakHits().get(0).id().startsWith("cannon-client-"));
        assertEquals("light-1", snapshot.flakHits().get(0).shipId());
        assertEquals("dark-1", snapshot.flakHits().get(0).targetShipId());
    }

    @Test
    void cannonHitUsesScoutPlaneBankWhenResolvingWingHits() {
        GameSession session = new GameSession(new GameSetup(
                "cannon-hit-banked-plane-test",
                new WorldMap(9043, List.of()),
                List.of(
                        new FleetSetup("light", List.of(
                                ship("light-1", "light", 0, 0, 0, "bot", 5, 0, 0)
                        )),
                        new FleetSetup("dark", List.of(
                                ship("dark-1", "dark", 0, 47, 0, "bot", 7, 35, 0, "scout-plane")
                        ))
                ),
                List.of(new Vector2(0, 0), new Vector2(0, 47))
        ));

        session.updatePlayerState(
                new PlayerStateUpdate("player-gunner", "light", 0, 0, 0, 4, 0, 5, 0, 0, false, "torpedo-boat"),
                navigationService,
                session.worldMap()
        );
        session.updatePlayerState(
                new PlayerStateUpdate("player-plane", "dark", 0, 47, 0, 14, -0.3, 7, 0, 0, false, "scout-plane", 30),
                navigationService,
                session.worldMap()
        );
        session.fireCannon(new FlakFireRequest(
                "player-gunner", "light", "light-1", 3, 1.5, 0, 0, 65, 95
        ));

        GameSnapshot snapshot = session.snapshot();
        for (int i = 0; i < 20 && snapshot.flakHits().isEmpty(); i += 1) {
            session.update(0.05, radarService, navigationService, session.worldMap());
            snapshot = session.snapshot();
        }

        assertFalse(snapshot.flakHits().isEmpty());
        assertEquals("dark-1", snapshot.flakHits().get(0).targetShipId());
        assertEquals("sunk", findShip(snapshot, "dark-1").state());
    }

    @Test
    void scoutPlaneRespawnsAsPlaneAboveWater() {
        GameSession session = new GameSession(new GameSetup(
                "scout-plane-respawn-test",
                new WorldMap(9038, List.of()),
                List.of(
                        new FleetSetup("light", List.of(
                                ship("light-1", "light", 0, 0, 0, "bot", 5, 0, 0)
                        )),
                        new FleetSetup("dark", List.of(
                                ship("dark-1", "dark", 3, 47, 0, "bot", 5, 0, 0)
                        ))
                ),
                List.of(new Vector2(0, 0), new Vector2(3, 47), new Vector2(1400, 1400))
        ));

        session.updatePlayerState(
                new PlayerStateUpdate("player-gunner", "light", 0, 0, 0, 4, 0, 5, 0, 0, false, "torpedo-boat"),
                navigationService,
                session.worldMap()
        );
        session.updatePlayerState(
                new PlayerStateUpdate("player-plane", "dark", 3, 47, 0, 14, 0, 7, 0, 0, false, "scout-plane", 30),
                navigationService,
                session.worldMap()
        );
        session.fireFlak(new FlakFireRequest(
                "player-gunner", "light", "light-1", 3, 1.5, 0, 0, 60, 95
        ));

        GameSnapshot snapshot = tickUntilShipState(session, "dark-1", "sunk", 2);
        assertEquals("scout-plane", findShip(snapshot, "dark-1").vehicleType());

        for (int i = 0; i < 180; i += 1) {
            session.update(0.05, radarService, navigationService, session.worldMap());
        }
        ShipSnapshot respawned = findShip(session.snapshot(), "dark-1");
        assertEquals("active", respawned.state());
        assertEquals("scout-plane", respawned.vehicleType());
        assertTrue(respawned.y() > 100);
    }

    @Test
    void flakProjectileCreatesWaterImpactWhenItHitsSeaLevel() {
        GameSession session = new GameSession(new GameSetup(
                "flak-water-impact-test",
                new WorldMap(9035, List.of()),
                List.of(new FleetSetup("light", List.of(
                        ship("light-1", "light", 0, 0, 0, "bot", 5, 0, 0)
                ))),
                List.of(new Vector2(0, 0))
        ));

        session.updatePlayerState(
                new PlayerStateUpdate("player-gunner", "light", 0, 0, 0, 4, 0, 5, 0, 0, false, "torpedo-boat"),
                navigationService,
                session.worldMap()
        );
        session.fireFlak(new FlakFireRequest(
                "player-gunner", "light", "light-1", 3, 2.0, 0, 0, -40, 0
        ));

        session.update(0.1, radarService, navigationService, session.worldMap());
        GameSnapshot snapshot = session.snapshot();

        assertEquals(0, snapshot.flakProjectiles().size());
        assertEquals(1, snapshot.flakImpacts().size());
        assertEquals("water-hit", snapshot.flakImpacts().get(0).reason());
        assertEquals(0, snapshot.flakImpacts().get(0).y(), 0.001);
    }

    @Test
    void flakProjectileCreatesLandImpactWhenItHitsTerrainHeight() {
        WorldMap worldMap = new WorldMap(9036, List.of(testIsland("impact-island", 0, 0, 60, 60)));
        GameSession session = new GameSession(new GameSetup(
                "flak-land-impact-test",
                worldMap,
                List.of(new FleetSetup("light", List.of(
                        ship("light-1", "light", 0, -90, 0, "bot", 5, 0, 0)
                ))),
                List.of(new Vector2(0, -90))
        ));

        session.updatePlayerState(
                new PlayerStateUpdate("player-gunner", "light", 0, -90, 0, 4, 0, 5, 0, 0, false, "torpedo-boat"),
                navigationService,
                session.worldMap()
        );
        session.fireFlak(new FlakFireRequest(
                "player-gunner", "light", "light-1", 0, 6.0, 0, 0, -24, 0
        ));

        session.update(0.1, radarService, navigationService, session.worldMap());
        GameSnapshot snapshot = session.snapshot();

        assertEquals(0, snapshot.flakProjectiles().size());
        assertEquals(1, snapshot.flakImpacts().size());
        assertEquals("land-hit", snapshot.flakImpacts().get(0).reason());
        assertTrue(snapshot.flakImpacts().get(0).y() > 0);
    }

    @Test
    void flakProjectileSinksShipWhenItHitsHull() {
        GameSession session = new GameSession(new GameSetup(
                "flak-ship-surface-hit-test",
                new WorldMap(9037, List.of()),
                List.of(
                        new FleetSetup("light", List.of(
                                ship("light-1", "light", 0, -40, 0, "bot", 5, 0, 0)
                        )),
                        new FleetSetup("dark", List.of(
                                ship("dark-1", "dark", 3, 0, 0, "target", 2, 0, 0)
                        ))
                ),
                List.of(new Vector2(0, -40), new Vector2(3, 0))
        ));

        session.updatePlayerState(
                new PlayerStateUpdate("player-gunner", "light", 0, -40, 0, 4, 0, 5, 0, 0, false, "torpedo-boat"),
                navigationService,
                session.worldMap()
        );
        session.fireFlak(new FlakFireRequest(
                "player-gunner", "light", "light-1", 3, 0.5, -40, 0, 2.25, 95
        ));

        session.update(0.5, radarService, navigationService, session.worldMap());
        GameSnapshot snapshot = session.snapshot();

        assertEquals("active", findShip(snapshot, "dark-1").state());
        assertEquals(0, snapshot.flakHits().size());
        assertEquals(1, snapshot.flakImpacts().size());
        assertEquals("ship-hit", snapshot.flakImpacts().get(0).reason());
    }

    @Test
    void flakProjectileSinksShipWhenItHitsBridgeOrFunnel() {
        GameSession session = new GameSession(new GameSetup(
                "flak-ship-critical-hit-test",
                new WorldMap(9038, List.of()),
                List.of(
                        new FleetSetup("light", List.of(
                                ship("light-1", "light", 0, -40, 0, "bot", 5, 0, 0)
                        )),
                        new FleetSetup("dark", List.of(
                                ship("dark-1", "dark", 3, 0, 0, "target", 2, 0, 0)
                        ))
                ),
                List.of(new Vector2(0, -40), new Vector2(3, 0))
        ));

        session.updatePlayerState(
                new PlayerStateUpdate("player-gunner", "light", 0, -40, 0, 4, 0, 5, 0, 0, false, "torpedo-boat"),
                navigationService,
                session.worldMap()
        );
        session.fireFlak(new FlakFireRequest(
                "player-gunner", "light", "light-1", 3, 1.14, -40, 3, 6.2, 95
        ));

        session.update(0.5, radarService, navigationService, session.worldMap());
        GameSnapshot snapshot = session.snapshot();

        assertEquals("sunk", findShip(snapshot, "dark-1").state());
        assertEquals(1, snapshot.flakHits().size());
        assertEquals(1, snapshot.flakImpacts().size());
        assertEquals("ship-critical-hit", snapshot.flakImpacts().get(0).reason());
    }

    @Test
    void flakShotCrossingOwnShipDoesNotHitShooter() {
        GameSession session = new GameSession(new GameSetup(
                "flak-own-ship-hit-test",
                new WorldMap(9039, List.of()),
                List.of(new FleetSetup("light", List.of(
                        ship("light-1", "light", 0, 0, 0, "bot", 5, 0, 0)
                ))),
                List.of(new Vector2(0, 0))
        ));

        session.updatePlayerState(
                new PlayerStateUpdate("player-gunner", "light", 0, 0, 0, 4, 0, 5, 0, 0, false, "torpedo-boat"),
                navigationService,
                session.worldMap()
        );
        session.fireFlak(new FlakFireRequest(
                "player-gunner", "light", "light-1", 0, 1.14, -2, 0, 0, 95
        ));

        session.update(0.1, radarService, navigationService, session.worldMap());
        GameSnapshot snapshot = session.snapshot();

        assertEquals("active", findShip(snapshot, "light-1").state());
        assertEquals(1, snapshot.flakProjectiles().size());
        assertEquals(0, snapshot.flakHits().size());
        assertTrue(snapshot.flakImpacts().stream()
                .noneMatch(impact -> "ship-hit".equals(impact.reason()) || "ship-critical-hit".equals(impact.reason())));
    }

    @Test
    void flakShotOverOwnDeckCanLeaveShipBackwardAndForwardOffTheSide() {
        GameSession session = new GameSession(new GameSetup(
                "flak-own-deck-clearance-test",
                new WorldMap(9145, List.of()),
                List.of(new FleetSetup("light", List.of(
                        ship("light-1", "light", 0, 0, 0, "bot", 5, 0, 0)
                ))),
                List.of(new Vector2(0, 0))
        ));

        session.updatePlayerState(
                new PlayerStateUpdate("player-gunner", "light", 0, 0, 0, 4, 0, 5, 0, 0, false, "torpedo-boat"),
                navigationService,
                session.worldMap()
        );
        session.fireFlak(new FlakFireRequest(
                "player-gunner", "light", "light-1", 0, 0.64 * TORPEDO_BOAT_MODEL_SCALE, -2.92 * TORPEDO_BOAT_MODEL_SCALE, 0, 0, -95
        ));
        session.update(0.08, radarService, navigationService, session.worldMap());
        GameSnapshot backwardSnapshot = session.snapshot();

        assertEquals(1, backwardSnapshot.flakProjectiles().size());
        assertEquals(0, backwardSnapshot.flakImpacts().size());

        session.update(1.0, radarService, navigationService, session.worldMap());
        session.fireFlak(new FlakFireRequest(
                "player-gunner", "light", "light-1", 0, 0.64 * TORPEDO_BOAT_MODEL_SCALE, -2.92 * TORPEDO_BOAT_MODEL_SCALE, 70, 0, 70
        ));
        session.update(0.08, radarService, navigationService, session.worldMap());
        GameSnapshot forwardSnapshot = session.snapshot();

        assertTrue(forwardSnapshot.flakProjectiles().stream()
                .anyMatch(projectile -> projectile.z() > -2.92 * TORPEDO_BOAT_MODEL_SCALE && projectile.x() > 0));
        assertTrue(forwardSnapshot.flakImpacts().stream()
                .noneMatch(impact -> "ship-hit".equals(impact.reason()) || "ship-critical-hit".equals(impact.reason())));
    }

    @Test
    void flakShotFromSternMountCanLeaveShipDirectlyAstern() {
        GameSession session = new GameSession(new GameSetup(
                "flak-astern-clearance-test",
                new WorldMap(9146, List.of()),
                List.of(new FleetSetup("light", List.of(
                        ship("light-1", "light", 0, 15, 0, "bot", 5, 0, 0)
                ))),
                List.of(new Vector2(0, 15))
        ));

        session.updatePlayerState(
                new PlayerStateUpdate("player-gunner", "light", 0, 15, 0, 4, 0, 5, 0, 0,
                        false, "torpedo-boat", 0, 0, Math.PI, -0.07, null, null),
                navigationService,
                session.worldMap()
        );
        session.fireFlak(new FlakFireRequest(
                "player-gunner", "light", "light-1", 0, 1.68, 4.25, 0, -82, -1250, Math.PI, -0.07
        ));

        GameSnapshot snapshot = session.snapshot();

        assertEquals(1, snapshot.flakProjectiles().size());
        assertEquals(0, snapshot.flakImpacts().size());
        assertEquals("active", findShip(snapshot, "light-1").state());
    }

    @Test
    void flakProjectileCanSinkFriendlyShip() {
        GameSession session = new GameSession(new GameSetup(
                "flak-friendly-ship-hit-test",
                new WorldMap(9040, List.of()),
                List.of(new FleetSetup("light", List.of(
                        ship("light-1", "light", 0, -40, 0, "bot", 5, 0, 0),
                        ship("light-2", "light", 3, 0, 0, "friendly-target", 2, 0, 0)
                ))),
                List.of(new Vector2(0, -40), new Vector2(3, 0))
        ));

        session.updatePlayerState(
                new PlayerStateUpdate("player-gunner", "light", 0, -40, 0, 4, 0, 5, 0, 0, false, "torpedo-boat"),
                navigationService,
                session.worldMap()
        );
        session.fireFlak(new FlakFireRequest(
                "player-gunner", "light", "light-1", 3, 1.14, -40, 0, 2.25, 95
        ));

        session.update(0.5, radarService, navigationService, session.worldMap());
        GameSnapshot snapshot = session.snapshot();

        assertEquals("active", findShip(snapshot, "light-1").state());
        assertEquals("sunk", findShip(snapshot, "light-2").state());
        assertEquals(1, snapshot.flakHits().size());
        assertEquals(1, snapshot.flakImpacts().size());
        assertEquals("ship-hit", snapshot.flakImpacts().get(0).reason());
    }

    @Test
    void cannonProjectileSinksShipWhenItHitsHullHeight() {
        GameSession session = cannonShipHitSession("cannon-hull-hit-test");

        session.fireCannon(new FlakFireRequest(
                "player-gunner", "light", "light-1", 3, 0.32, -40, 0, 2.8, 125
        ));

        GameSnapshot snapshot = tickUntilShipState(session, "dark-1", "sunk", 1);
        assertEquals("sunk", findShip(snapshot, "dark-1").state());
        assertEquals(1, snapshot.flakImpacts().size());
        assertEquals("ship-critical-hit", snapshot.flakImpacts().get(0).reason());
    }

    @Test
    void cannonCloseSideShotHitGridCoversVisibleHullProfile() throws Exception {
        GameSession session = new GameSession(new GameSetup(
                "cannon-side-grid-hit-test",
                new WorldMap(9051, List.of()),
                List.of(
                        new FleetSetup("light", List.of(
                                ship("light-1", "light", -18, 0, Math.PI / 2, "player-gunner", 2, 0, 99)
                        )),
                        new FleetSetup("dark", List.of(
                                ship("dark-1", "dark", 0, 0, 0, "bot", 2, 0, 99)
                        ))
                ),
                List.of(new Vector2(-18, 0), new Vector2(0, 0))
        ));
        List<String> misses = new java.util.ArrayList<>();

        for (int forwardIndex = 0; forwardIndex < 10; forwardIndex += 1) {
            double forward = -3.75 + forwardIndex * (7.5 / 9.0);
            for (int heightIndex = 0; heightIndex < 10; heightIndex += 1) {
                double y = 0.02 + heightIndex * (0.62 / 9.0);
                FlakProjectile projectile = cannonSideShotProjectile(-0.7, 0.7, y, y, forward);
                Optional<Object> hit = projectileHit(session, "dark-1", projectile);
                if (hit.isEmpty() || !"ship-critical-hit".equals(flakTargetHitReason(hit.get()))) {
                    misses.add("forward=" + MathSupport.round(forward) + " y=" + MathSupport.round(y));
                }
            }
        }

        assertTrue(misses.isEmpty(), String.join("\n", misses));
    }

    @Test
    void cannonCloseSideShotDoesNotSinkWhenItPassesAboveDeck() throws Exception {
        GameSession session = new GameSession(new GameSetup(
                "cannon-side-grid-air-test",
                new WorldMap(9052, List.of()),
                List.of(
                        new FleetSetup("light", List.of(
                                ship("light-1", "light", -18, 0, Math.PI / 2, "player-gunner", 2, 0, 99)
                        )),
                        new FleetSetup("dark", List.of(
                                ship("dark-1", "dark", 0, 0, 0, "bot", 2, 0, 99)
                        ))
                ),
                List.of(new Vector2(-18, 0), new Vector2(0, 0))
        ));
        List<String> falseHits = new java.util.ArrayList<>();

        for (double forward : List.of(-3.75, -2.917, 2.083, 2.917, 3.75)) {
            FlakProjectile projectile = cannonSideShotProjectile(-0.7, 0.7, 0.96, 0.96, forward);
            Optional<Object> hit = projectileHit(session, "dark-1", projectile);
            if (hit.isPresent() && "ship-critical-hit".equals(flakTargetHitReason(hit.get()))) {
                falseHits.add("forward=" + MathSupport.round(forward));
            }
        }

        assertTrue(falseHits.isEmpty(), String.join("\n", falseHits));
    }

    @Test
    void cannonProjectileDoesNotHitShipFarAboveHullAndSuperstructure() {
        GameSession session = cannonShipHitSession("cannon-high-miss-test");

        session.fireCannon(new FlakFireRequest(
                "player-gunner", "light", "light-1", 3, 3.8 * TORPEDO_BOAT_MODEL_SCALE, -40, 0, 0, 125
        ));

        session.update(0.5, radarService, navigationService, session.worldMap());
        GameSnapshot snapshot = session.snapshot();
        assertEquals("active", findShip(snapshot, "dark-1").state());
        assertTrue(snapshot.flakHits().isEmpty());
        assertTrue(snapshot.flakImpacts().stream().noneMatch(impact -> "ship-critical-hit".equals(impact.reason())));
    }

    @Test
    void flakProjectileHullHeightShipHitDoesNotSink() throws Exception {
        GameSession session = cannonShipHitSession("flak-hull-hit-test");
        FlakProjectile projectile = new FlakProjectile("flak-test", "light", "light-1", 3, 0.5 * TORPEDO_BOAT_MODEL_SCALE, 0, 0, 0, 0, 0);

        Object hit = projectileHit(session, "dark-1", projectile).orElseThrow();

        assertEquals("ship-hit", flakTargetHitReason(hit));
        assertFalse(flakTargetHitSinks(hit));
    }

    @Test
    void flakProjectileHitsScoutPlaneAtDifferentHeadings() {
        assertProjectileHitsScoutPlaneAtHeading(0);
        assertProjectileHitsScoutPlaneAtHeading(Math.PI / 2);
        assertProjectileHitsScoutPlaneAtHeading(Math.PI);
        assertProjectileHitsScoutPlaneAtHeading(-Math.PI / 2);
    }

    @Test
    void cannonProjectileHitsBankedScoutPlane() {
        GameSession session = planeHitSession(
                "cannon-plane-banked-hit-test",
                Math.PI / 2,
                -0.42
        );

        session.fireCannon(new FlakFireRequest(
                "player-gunner", "light", "light-1", 0, 28, 0, 0, 0, 125
        ));

        GameSnapshot snapshot = tickUntilShipState(session, "dark-1", "sunk", 1);
        assertEquals("sunk", findShip(snapshot, "dark-1").state());
        assertFalse(snapshot.flakHits().isEmpty());
        assertEquals("dark-1", snapshot.flakHits().get(0).targetShipId());
    }

    @Test
    void scoutPlanePlayerCanDropBomb() {
        GameSession session = new GameSession(new GameSetup(
                "scout-plane-bomb-test",
                new WorldMap(9031, List.of()),
                List.of(new FleetSetup("light", List.of(
                        ship("light-1", "light", 0, 0, 0, "bot", 5, 0, 0)
                ))),
                List.of(new Vector2(0, 0))
        ));

        session.updatePlayerState(
                new PlayerStateUpdate("player-BP-test", "light", 0, 0, 0, 8, 0, 7, 0, 0, false, "scout-plane", 80),
                navigationService,
                session.worldMap()
        );
        GameSnapshot snapshot = session.dropBomb(new BombDropRequest(
                "player-BP-test", "light", 0, 80, 0, 0, 14.5, 0, "scout-plane"
        ));

        ShipSnapshot ship = findShip(snapshot, "light-1");
        assertNotNull(ship);
        assertEquals("scout-plane", ship.vehicleType());
        assertEquals(1, snapshot.bombs().size());
        assertEquals("light-1", snapshot.bombs().get(0).shipId());
    }

    @Test
    void pendingScoutPlaneBombsUseCurrentPlanePosition() throws Exception {
        GameSession session = new GameSession(new GameSetup(
                "scout-plane-moving-bomb-test",
                new WorldMap(9035, List.of()),
                List.of(new FleetSetup("light", List.of(
                        ship("light-1", "light", 0, 0, 0, "bot", 5, 0, 0, "scout-plane")
                ))),
                List.of(new Vector2(0, 0))
        ));

        Ship plane = shipEntity(session, "light-1");
        plane.applyScoutPlaneWeaponState(new Vector2(0, 0), 80, 0, 14.5, 0);
        dropBombsFromScoutPlane(session, plane, 2.8);

        plane.applyScoutPlaneWeaponState(new Vector2(0, 40), 80, 0, 14.5, 0);
        session.update(0.13, radarService, navigationService, session.worldMap());

        List<BombSnapshot> bombs = session.snapshot().bombs();
        assertTrue(bombs.size() >= 2);
        assertTrue(bombs.get(1).launchZ() > 35,
                "Delayed bombs must leave the current plane position, not the original release point");
        assertEquals(bombs.get(1).launchZ(), bombs.get(1).z(), 0.001,
                "Delayed bombs must not jump forward in the same server tick that releases them");

        session.update(0.2, radarService, navigationService, session.worldMap());
        BombSnapshot movingBomb = session.snapshot().bombs().stream()
                .filter(bomb -> bomb.id().equals(bombs.get(1).id()))
                .findFirst()
                .orElseThrow();
        assertEquals(bombs.get(1).launchZ(), movingBomb.launchZ(),
                "Launch position must stay the backend release point");
    }

    @Test
    void botScoutPlaneDropsBombsDuringStraightAttackRun() {
        GameSession session = new GameSession(new GameSetup(
                "bot-scout-plane-bomb-test",
                new WorldMap(9041, List.of()),
                List.of(
                        new FleetSetup("light", List.of(
                                new ShipSetup(
                                        "light-plane-1",
                                        "light",
                                        new Vector2(0, -80),
                                        0,
                                        "bot",
                                        ENGINE_FULL,
                                        0,
                                        0,
                                        "scout-plane",
                                        50
                                )
                        )),
                        new FleetSetup("dark", List.of(
                                ship("dark-human-1", "dark", 0, 0, Math.PI, "player-dark", ENGINE_HALF, 0, 99, "torpedo-boat")
                        ))
                ),
                List.of(new Vector2(0, -140), new Vector2(0, 140))
        ));

        for (int i = 0; i < 80 && session.snapshot().bombs().isEmpty(); i += 1) {
            session.update(0.1, radarService, navigationService, session.worldMap());
        }

        assertFalse(session.snapshot().bombs().isEmpty(), "Bot scout plane should drop bombs in a straight attack window");
    }

    @Test
    void botScoutPlaneStartsTorpedoApproachBeforeCloseRange() {
        GameSession session = new GameSession(new GameSetup(
                "bot-scout-plane-early-torpedo-approach-test",
                new WorldMap(9044, List.of()),
                List.of(
                        new FleetSetup("light", List.of(
                                ship("light-plane-1", "light", 0, -520, 0, "bot", ENGINE_FULL, 0, 0, "scout-plane")
                        )),
                        new FleetSetup("dark", List.of(
                                ship("dark-human-1", "dark", 0, 0, Math.PI, "player-dark", ENGINE_HALF, 0, 99, "torpedo-boat")
                        ))
                ),
                List.of(new Vector2(0, -360), new Vector2(0, 160))
        ));

        session.update(0.5, radarService, navigationService, session.worldMap());

        ShipSnapshot plane = findShip(session.snapshot(), "light-plane-1");
        assertTrue(plane.y() < SCOUT_PLANE_START_Y,
                "Bot scout plane should already descend into its torpedo approach before the old close release range");
        assertTrue(session.snapshot().torpedoes().isEmpty(),
                "The early approach range must not immediately become a maximum-distance torpedo release");
        assertTrue(session.snapshot().bombs().isEmpty(),
                "The early torpedo approach must not be treated as a bombing run");
    }

    @Test
    void botScoutPlaneDoesNotReleaseTorpedoBeforeDescendingToAttackHeight() {
        GameSession session = new GameSession(new GameSetup(
                "bot-scout-plane-high-torpedo-release-test",
                new WorldMap(9046, List.of()),
                List.of(
                        new FleetSetup("light", List.of(
                                ship("light-plane-1", "light", 0, -260, 0, "bot", ENGINE_FULL, 0, 0, "scout-plane")
                        )),
                        new FleetSetup("dark", List.of(
                                ship("dark-human-1", "dark", 0, 0, Math.PI, "player-dark", ENGINE_HALF, 0, 99, "torpedo-boat")
                        ))
                ),
                List.of(new Vector2(0, -300), new Vector2(0, 140))
        ));

        session.update(0.1, radarService, navigationService, session.worldMap());

        ShipSnapshot plane = findShip(session.snapshot(), "light-plane-1");
        assertTrue(plane.y() > 118,
                "Test setup should still have the plane above a credible torpedo-drop height");
        assertTrue(session.snapshot().torpedoes().isEmpty(),
                "Bot scout plane must not release a torpedo while still high above torpedo-attack height");
    }

    @Test
    void botScoutPlanePrefersTorpedoOverBombsInSharedAttackRange() {
        GameSession session = new GameSession(new GameSetup(
                "bot-scout-plane-prefers-torpedo-test",
                new WorldMap(9045, List.of()),
                List.of(
                        new FleetSetup("light", List.of(
                                new ShipSetup(
                                        "light-plane-1",
                                        "light",
                                        new Vector2(0, -220),
                                        0,
                                        "bot",
                                        ENGINE_FULL,
                                        0,
                                        0,
                                        "scout-plane",
                                        105
                                )
                        )),
                        new FleetSetup("dark", List.of(
                                ship("dark-human-1", "dark", 0, 0, Math.PI, "player-dark", ENGINE_HALF, 0, 99, "torpedo-boat")
                        ))
                ),
                List.of(new Vector2(0, -260), new Vector2(0, 140))
        ));

        for (int i = 0; i < 20 && session.snapshot().torpedoes().isEmpty(); i += 1) {
            session.update(0.1, radarService, navigationService, session.worldMap());
        }

        assertFalse(session.snapshot().torpedoes().isEmpty(),
                "Bot scout plane should use its torpedo in the shared bomb/torpedo attack range");
        assertTrue(session.snapshot().bombs().isEmpty(),
                "Bombing should remain the fallback, not the preferred attack in torpedo range");
    }

    @Test
    void botScoutPlaneThreatensMovingHumanTarget() {
        GameSession session = new GameSession(new GameSetup(
                "bot-scout-plane-moving-target-test",
                new WorldMap(9042, List.of()),
                List.of(
                        new FleetSetup("light", List.of(
                                ship("light-plane-1", "light", 0, -520, 0, "bot", ENGINE_FULL, 0, 0, "scout-plane")
                        )),
                        new FleetSetup("dark", List.of(
                                ship("dark-human-1", "dark", 0, 0, Math.PI / 2, "player-dark", ENGINE_HALF, 0, 99, "torpedo-boat")
                        ))
                ),
                List.of(new Vector2(0, -540), new Vector2(0, 180))
        ));

        GameSnapshot snapshot = session.snapshot();
        for (int i = 0; i < 700 && "active".equals(findShip(snapshot, "dark-human-1").state()); i += 1) {
            session.update(0.1, radarService, navigationService, session.worldMap());
            snapshot = session.snapshot();
        }

        assertEquals("sunk", findShip(snapshot, "dark-human-1").state(), "Moving human target should be sinkable by bot scout-plane attacks");
    }

    @Test
    void botScoutPlaneAvoidsLowAttackCourseAcrossLand() {
        WorldMap worldMap = new WorldMap(9043, List.of(testCoastline("blocking-ridge", 0, -80, 36, 36, 2.0, 80.0)));
        GameSession session = new GameSession(new GameSetup(
                "bot-scout-plane-land-avoidance-test",
                worldMap,
                List.of(
                        new FleetSetup("light", List.of(
                                ship("light-plane-1", "light", 0, -170, 0, "bot", ENGINE_FULL, 0, 0, "scout-plane")
                        )),
                        new FleetSetup("dark", List.of(
                                ship("dark-human-1", "dark", 0, 0, Math.PI, "player-dark", ENGINE_HALF, 0, 99, "torpedo-boat")
                        ))
                ),
                List.of(new Vector2(0, -190), new Vector2(0, 190))
        ));

        session.update(0.1, radarService, navigationService, worldMap);

        ShipSnapshot plane = findShip(session.snapshot(), "light-plane-1");
        assertNotNull(plane);
        assertTrue(Math.abs(plane.rudderDegrees()) > 0, "Bot scout plane should turn away from a land-blocked low attack course");
    }

    @Test
    void botScoutPlaneTargetsCloserBotUntilHumanAttackIsForced() {
        GameSession session = new GameSession(new GameSetup(
                "bot-scout-plane-human-target-test",
                new WorldMap(9037, List.of()),
                List.of(
                        new FleetSetup("light", List.of(
                                ship("light-plane", "light", 0, 0, 0, "bot", 7, 0, 99, "scout-plane")
                        )),
                        new FleetSetup("dark", List.of(
                                ship("dark-player", "dark", 0, 80, Math.PI, "player-BPB", 5, 0, 99),
                                ship("dark-bot", "dark", 45, 40, Math.PI, "bot", 5, 0, 99)
                        ))
                ),
                List.of(new Vector2(0, 0))
        ));

        session.update(0.05, radarService, navigationService, session.worldMap());

        assertTrue(findShip(session.snapshot(), "light-plane").rudderDegrees() > 0,
                "Bot scout plane should target the closer bot until the forced human attack rule applies");
    }

    @Test
    void botScoutPlaneAvoidsBackToBackHumanAttacksWhenBotTargetsExist() {
        GameSession session = new GameSession(new GameSetup(
                "bot-scout-plane-human-cooldown-target-test",
                new WorldMap(9041, List.of()),
                List.of(
                        new FleetSetup("light", List.of(
                                ship("light-plane", "light", 0, 0, 0, "bot", ENGINE_FULL, 0, 99, "scout-plane")
                        )),
                        new FleetSetup("dark", List.of(
                                ship("dark-player", "dark", 0, 80, Math.PI, "player-BPB", ENGINE_HALF, 0, 99),
                                ship("dark-bot", "dark", 95, 40, Math.PI, "bot", ENGINE_HALF, 0, 99)
                        ))
                ),
                List.of(new Vector2(0, 0))
        ));

        session.update(0.05, radarService, navigationService, session.worldMap());

        assertTrue(findShip(session.snapshot(), "light-plane").rudderDegrees() > 0,
                "Bot scout plane should not choose a human again before enough non-human attacks happened");
    }

    @Test
    void botScoutPlaneTargetsNearestHumanWhenNoBotTargetsRemain() throws Exception {
        GameSession session = new GameSession(new GameSetup(
                "bot-scout-plane-human-only-target-test",
                new WorldMap(9054, List.of()),
                List.of(
                        new FleetSetup("light", List.of(
                                ship("light-plane", "light", 0, 0, 0, "bot", ENGINE_FULL, 0, 99, "scout-plane")
                        )),
                        new FleetSetup("dark", List.of(
                                ship("dark-human-a", "dark", 620, 0, Math.PI, "player-A", ENGINE_HALF, 0, 99),
                                ship("dark-human-z", "dark", 0, 90, Math.PI, "player-Z", ENGINE_HALF, 0, 99)
                        ))
                ),
                List.of(new Vector2(0, 0))
        ));
        setBotScoutPlaneNonHumanAttackStreak(session, "light", "dark-human-a", 5);

        assertEquals("dark-human-z", selectBotScoutPlaneTargetId(session, "light-plane").orElse(null));
    }

    @Test
    void botScoutPlanePrefersCleanApproachOverCloserSideTarget() throws Exception {
        GameSession session = new GameSession(new GameSetup(
                "bot-scout-plane-clean-approach-target-test",
                new WorldMap(9055, List.of()),
                List.of(
                        new FleetSetup("light", List.of(
                                ship("light-plane", "light", 0, 0, 0, "bot", ENGINE_FULL, 0, 99, "scout-plane")
                        )),
                        new FleetSetup("dark", List.of(
                                ship("dark-side", "dark", 80, 55, Math.PI, "bot", ENGINE_HALF, 0, 99),
                                ship("dark-ahead", "dark", 0, 165, Math.PI, "bot", ENGINE_HALF, 0, 99)
                        ))
                ),
                List.of(new Vector2(0, 0))
        ));

        assertEquals("dark-ahead", selectBotScoutPlaneTargetId(session, "light-plane").orElse(null),
                "A bot scout plane should prefer a target already in a useful attack lane over a closer side target");
    }

    @Test
    void botScoutPlaneFliesThroughBeforeTurningOntoCloseSideTarget() {
        GameSession session = new GameSession(new GameSetup(
                "bot-scout-plane-close-side-realign-test",
                new WorldMap(9056, List.of()),
                List.of(
                        new FleetSetup("light", List.of(
                                ship("light-plane", "light", 0, 0, 0, "bot", ENGINE_FULL, 0, 99, "scout-plane")
                        )),
                        new FleetSetup("dark", List.of(
                                ship("dark-side", "dark", 135, 70, Math.PI, "bot", ENGINE_HALF, 0, 99)
                        ))
                ),
                List.of(new Vector2(0, 0))
        ));

        session.update(0.1, radarService, navigationService, session.worldMap());

        assertEquals(0, findShip(session.snapshot(), "light-plane").rudderDegrees(),
                "A close side target should trigger a straight fly-through instead of a tight circling turn");
    }

    @Test
    void onlyOneEnemyBotScoutPlaneAttacksOneHumanPlayer() throws Exception {
        GameSession session = new GameSession(new GameSetup(
                "bot-scout-plane-single-attacker-test",
                new WorldMap(9048, List.of()),
                List.of(
                        new FleetSetup("light", List.of(
                                ship("light-plane-a", "light", 0, -500, 0, "bot", ENGINE_FULL, 0, 99, "scout-plane"),
                                ship("light-plane-b", "light", 90, -520, 0, "bot", ENGINE_FULL, 0, 99, "scout-plane")
                        )),
                        new FleetSetup("dark", List.of(
                                ship("dark-player", "dark", 0, 0, 0, "player-BPB", ENGINE_HALF, 0, 99)
                        ))
                ),
                List.of(new Vector2(0, 0))
        ));

        assertEquals("dark-player", scoutPlaneAttackHumanId(session, "light-plane-a").orElse(null));
        assertTrue(scoutPlaneAttackHumanId(session, "light-plane-b").isEmpty());
    }

    @Test
    void botScoutPlaneAttackerIsReassignedWhenAttackerIsSunk() throws Exception {
        GameSession session = new GameSession(new GameSetup(
                "bot-scout-plane-attacker-reassigned-test",
                new WorldMap(9049, List.of()),
                List.of(
                        new FleetSetup("light", List.of(
                                ship("light-plane-a", "light", 0, -500, 0, "bot", ENGINE_FULL, 0, 99, "scout-plane"),
                                ship("light-plane-b", "light", 90, -520, 0, "bot", ENGINE_FULL, 0, 99, "scout-plane")
                        )),
                        new FleetSetup("dark", List.of(
                                ship("dark-player", "dark", 0, 0, 0, "player-BPB", ENGINE_HALF, 0, 99)
                        ))
                ),
                List.of(new Vector2(0, 0))
        ));

        sinkShip(session, "light-plane-a");

        assertTrue(scoutPlaneAttackHumanId(session, "light-plane-a").isEmpty());
        assertEquals("dark-player", scoutPlaneAttackHumanId(session, "light-plane-b").orElse(null));
    }

    @Test
    void onlyAssignedBotScoutPlaneCanForceHumanAttack() throws Exception {
        GameSession session = new GameSession(new GameSetup(
                "bot-scout-plane-attacker-forced-human-test",
                new WorldMap(9050, List.of()),
                List.of(
                        new FleetSetup("light", List.of(
                                ship("light-plane-a", "light", 0, -500, 0, "bot", ENGINE_FULL, 0, 99, "scout-plane"),
                                ship("light-plane-b", "light", 90, -520, 0, "bot", ENGINE_FULL, 0, 99, "scout-plane")
                        )),
                        new FleetSetup("dark", List.of(
                                ship("dark-player", "dark", 0, 0, 0, "player-BPB", ENGINE_HALF, 0, 99),
                                ship("dark-bot", "dark", 20, 0, 0, "bot", ENGINE_HALF, 0, 99)
                        ))
                ),
                List.of(new Vector2(0, 0))
        ));
        setBotScoutPlaneNonHumanAttackStreak(session, "light", "dark-player", 5);

        assertTrue(shouldForceHumanScoutPlaneTarget(session, "light-plane-a"));
        assertFalse(shouldForceHumanScoutPlaneTarget(session, "light-plane-b"));
    }

    @Test
    void botScoutPlaneDoesNotForceHumanAttackAfterOnlyFourNonHumanAttacks() throws Exception {
        GameSession session = new GameSession(new GameSetup(
                "bot-scout-plane-two-attacks-no-human-force-test",
                new WorldMap(9052, List.of()),
                List.of(
                        new FleetSetup("light", List.of(
                                ship("light-plane", "light", 0, -500, 0, "bot", ENGINE_FULL, 0, 99, "scout-plane")
                        )),
                        new FleetSetup("dark", List.of(
                                ship("dark-player", "dark", 0, 0, 0, "player-BPB", ENGINE_HALF, 0, 99)
                        ))
                ),
                List.of(new Vector2(0, 0))
        ));
        setBotScoutPlaneNonHumanAttackStreak(session, "light", "dark-player", 4);

        assertFalse(shouldForceHumanScoutPlaneTarget(session, "light-plane"));
    }

    @Test
    void botScoutPlaneForcedHumanTargetIsNotLimitedToAttackRange() throws Exception {
        GameSession session = new GameSession(new GameSetup(
                "bot-scout-plane-forced-human-target-test",
                new WorldMap(9038, List.of()),
                List.of(
                        new FleetSetup("light", List.of(
                                ship("light-plane", "light", 0, 0, 0, "bot", ENGINE_FULL, 0, 99, "scout-plane")
                        )),
                        new FleetSetup("dark", List.of(
                                ship("dark-bot", "dark", 0, 90, Math.PI, "bot", ENGINE_HALF, 0, 99),
                                ship("dark-player", "dark", 620, 0, Math.PI, "player-BPB", ENGINE_HALF, 0, 99)
                        ))
                ),
                List.of(new Vector2(0, 0))
        ));
        setBotScoutPlaneNonHumanAttackStreak(session, "light", "dark-player", 5);

        session.update(0.05, radarService, navigationService, session.worldMap());

        assertTrue(findShip(session.snapshot(), "light-plane").rudderDegrees() > 0,
                "Forced human targeting should turn toward a distant human instead of the closer bot");
    }

    @Test
    void nonHumanAttacksByOtherScoutPlanesCountTowardAssignedHumanAttack() throws Exception {
        GameSession session = new GameSession(new GameSetup(
                "bot-scout-plane-team-human-attack-counter-test",
                new WorldMap(9053, List.of()),
                List.of(
                        new FleetSetup("light", List.of(
                                ship("light-plane-a", "light", 0, -500, 0, "bot", ENGINE_FULL, 0, 99, "scout-plane"),
                                ship("light-plane-b", "light", 90, -520, 0, "bot", ENGINE_FULL, 0, 99, "scout-plane"),
                                ship("light-plane-c", "light", 180, -540, 0, "bot", ENGINE_FULL, 0, 99, "scout-plane")
                        )),
                        new FleetSetup("dark", List.of(
                                ship("dark-player", "dark", 0, 0, 0, "player-BPB", ENGINE_HALF, 0, 99),
                                ship("dark-bot-1", "dark", 200, 0, 0, "bot", ENGINE_HALF, 0, 99),
                                ship("dark-bot-2", "dark", 260, 0, 0, "bot", ENGINE_HALF, 0, 99),
                                ship("dark-bot-3", "dark", 320, 0, 0, "bot", ENGINE_HALF, 0, 99),
                                ship("dark-bot-4", "dark", 380, 0, 0, "bot", ENGINE_HALF, 0, 99),
                                ship("dark-bot-5", "dark", 440, 0, 0, "bot", ENGINE_HALF, 0, 99)
                        ))
                ),
                List.of(new Vector2(0, 0))
        ));

        recordBotScoutPlaneAttack(session, "light-plane-b", "dark-bot-1");
        recordBotScoutPlaneAttack(session, "light-plane-c", "dark-bot-2");
        recordBotScoutPlaneAttack(session, "light-plane-b", "dark-bot-3");
        recordBotScoutPlaneAttack(session, "light-plane-c", "dark-bot-4");
        recordBotScoutPlaneAttack(session, "light-plane-b", "dark-bot-5");

        assertTrue(shouldForceHumanScoutPlaneTarget(session, "light-plane-a"));
    }

    @Test
    void sinkingAssignedScoutPlaneRevertsItsNonHumanAttackCounterContribution() throws Exception {
        GameSession session = new GameSession(new GameSetup(
                "bot-scout-plane-sunk-reverts-counter-test",
                new WorldMap(9064, List.of()),
                List.of(
                        new FleetSetup("light", List.of(
                                ship("light-plane-a", "light", 0, -500, 0, "bot", ENGINE_FULL, 0, 99, "scout-plane"),
                                ship("light-plane-b", "light", 90, -520, 0, "bot", ENGINE_FULL, 0, 99, "scout-plane"),
                                ship("light-plane-c", "light", 180, -540, 0, "bot", ENGINE_FULL, 0, 99, "scout-plane")
                        )),
                        new FleetSetup("dark", List.of(
                                ship("dark-player", "dark", 0, 0, 0, "player-BPB", ENGINE_HALF, 0, 99),
                                ship("dark-bot-1", "dark", 200, 0, 0, "bot", ENGINE_HALF, 0, 99),
                                ship("dark-bot-2", "dark", 260, 0, 0, "bot", ENGINE_HALF, 0, 99),
                                ship("dark-bot-3", "dark", 320, 0, 0, "bot", ENGINE_HALF, 0, 99),
                                ship("dark-bot-4", "dark", 380, 0, 0, "bot", ENGINE_HALF, 0, 99),
                                ship("dark-bot-5", "dark", 440, 0, 0, "bot", ENGINE_HALF, 0, 99)
                        ))
                ),
                List.of(new Vector2(0, 0))
        ));

        recordBotScoutPlaneAttack(session, "light-plane-a", "dark-bot-1");
        recordBotScoutPlaneAttack(session, "light-plane-b", "dark-bot-2");
        recordBotScoutPlaneAttack(session, "light-plane-b", "dark-bot-3");
        recordBotScoutPlaneAttack(session, "light-plane-b", "dark-bot-4");
        recordBotScoutPlaneAttack(session, "light-plane-b", "dark-bot-5");

        assertTrue(shouldForceHumanScoutPlaneTarget(session, "light-plane-a"));

        sinkShip(session, "light-plane-a");

        assertFalse(shouldForceHumanScoutPlaneTarget(session, "light-plane-b"),
                "Destroying the assigned attacker should also undo its non-human attack counter contribution");
    }

    @Test
    void forcedHumanScoutPlaneAttackThresholdGrowsWithHumanTargets() throws Exception {
        GameSession session = new GameSession(new GameSetup(
                "bot-scout-plane-many-human-target-counter-test",
                new WorldMap(9063, List.of()),
                List.of(
                        new FleetSetup("light", List.of(
                                ship("light-plane-a", "light", 0, -500, 0, "bot", ENGINE_FULL, 0, 99, "scout-plane"),
                                ship("light-plane-b", "light", 90, -520, 0, "bot", ENGINE_FULL, 0, 99, "scout-plane"),
                                ship("light-plane-c", "light", 180, -540, 0, "bot", ENGINE_FULL, 0, 99, "scout-plane")
                        )),
                        new FleetSetup("dark", List.of(
                                ship("dark-human-a", "dark", 0, 0, 0, "player-A", ENGINE_HALF, 0, 99),
                                ship("dark-human-b", "dark", 70, 0, 0, "player-B", ENGINE_HALF, 0, 99),
                                ship("dark-human-c", "dark", 140, 0, 0, "player-C", ENGINE_HALF, 0, 99),
                                ship("dark-bot", "dark", 240, 0, 0, "bot", ENGINE_HALF, 0, 99)
                        ))
                ),
                List.of(new Vector2(0, 0))
        ));
        setBotScoutPlaneNonHumanAttackStreak(session, "light", "dark-human-a", 6);

        assertFalse(shouldForceHumanScoutPlaneTarget(session, "light-plane-a"));

        setBotScoutPlaneNonHumanAttackStreak(session, "light", "dark-human-a", 7);

        assertTrue(shouldForceHumanScoutPlaneTarget(session, "light-plane-a"));
    }

    @Test
    void humanScoutPlaneAttackClearsForcedHumanAttackState() throws Exception {
        GameSession session = new GameSession(new GameSetup(
                "bot-scout-plane-human-attack-clears-state-test",
                new WorldMap(9039, List.of()),
                List.of(
                        new FleetSetup("light", List.of(
                                ship("light-human", "light", 0, 0, 0, "player-BPB", ENGINE_HALF, 0, 99)
                        )),
                        new FleetSetup("dark", List.of(
                                ship("dark-plane", "dark", 3, 47, 0, "bot", ENGINE_FULL, 0, 99, "scout-plane")
                        ))
                ),
                List.of(new Vector2(0, 0), new Vector2(3, 47))
        ));
        setBotScoutPlaneNonHumanAttackStreak(session, "dark", "light-human", 5);

        recordBotScoutPlaneAttack(session, "dark-plane", "light-human");

        assertFalse(botScoutPlaneNonHumanAttackStreak(session).containsKey("dark>light-human"));
    }

    @Test
    void torpedoBoatCannotDropBomb() {
        GameSession session = new GameSession(new GameSetup(
                "boat-bomb-test",
                new WorldMap(9032, List.of()),
                List.of(new FleetSetup("light", List.of(
                        ship("light-1", "light", 0, 0, 0, "bot", 5, 0, 0)
                ))),
                List.of(new Vector2(0, 0))
        ));

        GameSnapshot snapshot = session.dropBomb(new BombDropRequest(
                "player-BP-test", "light", 0, 22, 0, 0, 14.5, 0, "torpedo-boat"
        ));

        assertEquals(0, snapshot.bombs().size());
    }

    @Test
    void bombCanSinkShipAtSeaLevelImpact() {
        GameSession session = new GameSession(new GameSetup(
                "bomb-hit-test",
                new WorldMap(9033, List.of()),
                List.of(
                        new FleetSetup("light", List.of(ship("light-1", "light", 0, 0, 0, "bot", 5, 0, 0))),
                        new FleetSetup("dark", List.of(ship("dark-1", "dark", 0, 2.6, 0, "bot", 2, 0)))
                ),
                List.of(new Vector2(0, 0), new Vector2(20, 0))
        ));

        session.updatePlayerState(
                new PlayerStateUpdate("player-BP-test", "light", 0, 0, 0, 8, 0, 7, 0, 0, false, "scout-plane"),
                navigationService,
                session.worldMap()
        );
        session.dropBomb(new BombDropRequest(
                "player-BP-test", "light", 0, 1, 0, 0, 0, 0, "scout-plane"
        ));
        GameSnapshot snapshot = session.snapshot();
        for (int index = 0; index < 12 && snapshot.bombImpacts().isEmpty(); index += 1) {
            session.update(0.1, radarService, navigationService, session.worldMap());
            snapshot = session.snapshot();
        }

        assertEquals("sunk", findShip(snapshot, "dark-1").state());
        assertEquals("ship-hit", snapshot.bombImpacts().get(0).reason());
    }

    @Test
    void bombNearMissDoesNotSinkShip() {
        GameSession session = new GameSession(new GameSetup(
                "bomb-near-miss-test",
                new WorldMap(9034, List.of()),
                List.of(
                        new FleetSetup("light", List.of(ship("light-1", "light", 0, 0, 0, "bot", 5, 0, 0))),
                        new FleetSetup("dark", List.of(ship("dark-1", "dark", 8.0, 8.0, 0, "bot", 2, 0)))
                ),
                List.of(new Vector2(0, 0), new Vector2(20, 0))
        ));

        session.updatePlayerState(
                new PlayerStateUpdate("player-BP-test", "light", 0, 0, 0, 8, 0, 7, 0, 0, false, "scout-plane"),
                navigationService,
                session.worldMap()
        );
        session.dropBomb(new BombDropRequest(
                "player-BP-test", "light", 0, 1, 0, 0, 0, 0, "scout-plane"
        ));
        GameSnapshot snapshot = session.snapshot();
        for (int index = 0; index < 12 && snapshot.bombImpacts().isEmpty(); index += 1) {
            session.update(0.1, radarService, navigationService, session.worldMap());
            snapshot = session.snapshot();
        }

        assertEquals("active", findShip(snapshot, "dark-1").state());
        assertEquals("sea-hit", snapshot.bombImpacts().get(0).reason());
    }

    @Test
    void torpedoesDoNotHitScoutPlanes() {
        GameSession session = new GameSession(new GameSetup(
                "scout-plane-torpedo-ignore-test",
                new WorldMap(9027, List.of()),
                List.of(
                        new FleetSetup("red", List.of(ship("red-1", "red", 0, 0, 0, "bot", 5, 0, 0))),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 0, 90, Math.PI, "bot", 2, 0)))
                ),
                List.of(new Vector2(0, 0), new Vector2(0, 90))
        ));

        session.updatePlayerState(
                new PlayerStateUpdate("player-BP-test", "blue", 0, 90, Math.PI, 8, 0, 7, 0, 0, false, "scout-plane"),
                navigationService,
                session.worldMap()
        );
        GameSnapshot snapshot = session.fireTorpedo(new FireTorpedoRequest("player-red-test", "red"));
        for (int index = 0; index < 90; index += 1) {
            session.update(0.05, radarService, navigationService, session.worldMap());
            snapshot = session.snapshot();
        }

        assertEquals("active", findShip(snapshot, "blue-1").state());
        assertEquals("scout-plane", findShip(snapshot, "blue-1").vehicleType());
        assertEquals(0, snapshot.destroyedShipsByTeam().get("blue"));
    }

    @Test
    void unknownClientTeamRequestsAreIgnored() {
        GameSession session = new GameSession(new GameSetup(
                "unknown-client-team-test",
                new WorldMap(9030, List.of()),
                List.of(new FleetSetup("light", List.of(
                        ship("light-1", "light", 0, 0, 0, "bot", ENGINE_HALF, 0, 0)
                ))),
                List.of(new Vector2(0, 0))
        ));

        assertDoesNotThrow(() -> session.updatePlayerState(
                new PlayerStateUpdate("player-dark-test", "dark", 0, 0, 0, 0, 0, ENGINE_HALF, 0, 0, false, "torpedo-boat"),
                navigationService,
                session.worldMap()
        ));
        assertDoesNotThrow(() -> session.fireTorpedo(new FireTorpedoRequest("player-dark-test", "dark")));
        assertDoesNotThrow(() -> session.fireFlak(new FlakFireRequest("player-dark-test", "dark", "dark-1", 0, 0, 0, 0, 0, 1)));
        assertDoesNotThrow(() -> session.fireCannon(new FlakFireRequest("player-dark-test", "dark", "dark-1", 0, 0, 0, 0, 0, 1)));
        assertDoesNotThrow(() -> session.reportClientPlaneHit(new ClientPlaneHitRequest(
                "player-dark-test", "dark", "dark-1", "light-1", "flak", 0, 0, 0
        )));
        assertDoesNotThrow(() -> session.dropBomb(new BombDropRequest(
                "player-dark-test", "dark", 0, 80, 0, 0, 0, 0, "scout-plane"
        )));

        GameSnapshot snapshot = session.snapshot();
        assertEquals(1, snapshot.ships().size());
        assertEquals("active", findShip(snapshot, "light-1").state());
        assertTrue(snapshot.torpedoes().isEmpty());
        assertTrue(snapshot.flakProjectiles().isEmpty());
        assertTrue(snapshot.bombs().isEmpty());
    }

    @Test
    void shipGunRequestsWithoutAvailableShipAreIgnored() {
        GameSession session = new GameSession(new GameSetup(
                "no-ship-for-gun-request-test",
                new WorldMap(90301, List.of()),
                List.of(new FleetSetup("light", List.of(
                        ship("light-1", "light", 0, 0, 0, "player-other-1", ENGINE_HALF, 0, 0),
                        ship("light-2", "light", 30, 0, 0, "player-other-2", ENGINE_HALF, 0, 0)
                ))),
                List.of(new Vector2(0, 0))
        ));

        assertDoesNotThrow(() -> session.fireFlak(new FlakFireRequest(
                "player-new", "light", "light-1", 0, 5, 0, 0, 0, 180, 0, 0
        )));
        assertDoesNotThrow(() -> session.fireCannon(new FlakFireRequest(
                "player-new", "light", "light-1", 0, 5, 0, 0, 0, 260, 0, 0
        )));

        GameSnapshot snapshot = session.snapshot();
        assertTrue(snapshot.flakProjectiles().isEmpty());
        assertEquals("player-other-1", findShip(snapshot, "light-1").controlledBy());
        assertEquals("player-other-2", findShip(snapshot, "light-2").controlledBy());
    }

    @Test
    void escortBotUsesFlankWhenFallingBehindHumanLeader() {
        GameSession session = new GameSession(new GameSetup(
                "escort-speed-test",
                new WorldMap(9013, List.of()),
                List.of(
                        new FleetSetup("light", List.of(
                                ship("light-1", "light", 0, 0, 0, "player-BP", 5, 0),
                                ship("light-2", "light", -560, -80, 0, "bot", 2, 0)
                        ))
                ),
                List.of(new Vector2(0, 0), new Vector2(-560, -80))
        ));

        session.update(0.05, radarService, navigationService, session.worldMap());

        assertEquals(ENGINE_FLANK, findShip(session.snapshot(), "light-2").engineOrder());
    }

    @Test
    void escortBotFollowsHumanBeforeReturningToLand() {
        GameSession session = new GameSession(new GameSetup(
                "escort-priority-test",
                new WorldMap(9014, List.of(testIsland("far-land", 0, 0, 90, 90))),
                List.of(
                        new FleetSetup("light", List.of(
                                ship("light-1", "light", 1000, 1000, 0, "player-BP", 5, 0),
                                ship("light-2", "light", 600, 940, 0, "bot", 2, 0)
                        ))
                ),
                List.of(new Vector2(1000, 1000), new Vector2(600, 940))
        ));

        session.update(0.05, radarService, navigationService, session.worldMap());

        assertEquals(ENGINE_FLANK, findShip(session.snapshot(), "light-2").engineOrder());
    }

    @Test
    void botTargetsCloserHumanPlayerEvenWhenAnotherEnemyIsVisible() {
        GameSession session = new GameSession(new GameSetup(
                "bot-human-target-priority-test",
                new WorldMap(9017, List.of()),
                List.of(
                        new FleetSetup("red", List.of(
                                ship("red-1", "red", 0, 0, 0, "bot", 2, 0)
                        )),
                        new FleetSetup("blue", List.of(
                                ship("blue-human", "blue", -80, 0, 0, "player-BP-test", 5, 0),
                                ship("blue-bot", "blue", 300, 0, 0, "bot", 5, 0)
                        ))
                ),
                List.of(new Vector2(0, 0), new Vector2(-80, 0), new Vector2(300, 0))
        ));

        session.update(0.05, radarService, navigationService, session.worldMap());

        ShipSnapshot attacker = findShip(session.snapshot(), "red-1");
        assertEquals(ENGINE_FULL, attacker.engineOrder());
        assertTrue(attacker.rudderDegrees() < 0);
    }

    @Test
    void botPrefersHumanTargetUnlessEnemyBotIsInsideRadarRing() {
        GameSession session = new GameSession(new GameSetup(
                "bot-human-wide-radar-priority-test",
                new WorldMap(9022, List.of()),
                List.of(
                        new FleetSetup("red", List.of(
                                ship("red-1", "red", 0, 0, 0, "bot", 2, 0)
                        )),
                        new FleetSetup("blue", List.of(
                                ship("blue-human", "blue", -900, 0, 0, "player-BP-test", 5, 0),
                                ship("blue-bot", "blue", 1000, 0, 0, "bot", 5, 0)
                        ))
                ),
                List.of(new Vector2(0, 0), new Vector2(-900, 0), new Vector2(1000, 0))
        ));

        session.update(0.05, radarService, navigationService, session.worldMap());

        ShipSnapshot attacker = findShip(session.snapshot(), "red-1");
        assertEquals(ENGINE_FULL, attacker.engineOrder());
        assertTrue(attacker.rudderDegrees() < 0);
    }

    @Test
    void botPrefersHumanTargetWhenEnemyBotIsOnlyMidDistance() {
        GameSession session = new GameSession(new GameSetup(
                "bot-mid-distance-enemy-human-priority-test",
                new WorldMap(9023, List.of()),
                List.of(
                        new FleetSetup("red", List.of(
                                ship("red-1", "red", 0, 0, 0, "bot", 2, 0)
                        )),
                        new FleetSetup("blue", List.of(
                                ship("blue-human", "blue", -900, 0, 0, "player-BP-test", 5, 0),
                                ship("blue-bot", "blue", 400, 0, 0, "bot", 5, 0)
                        ))
                ),
                List.of(new Vector2(0, 0), new Vector2(-900, 0), new Vector2(400, 0))
        ));

        session.update(0.05, radarService, navigationService, session.worldMap());

        ShipSnapshot attacker = findShip(session.snapshot(), "red-1");
        assertEquals(ENGINE_FULL, attacker.engineOrder());
        assertTrue(attacker.rudderDegrees() < 0);
    }

    @Test
    void botAttacksVeryCloseEnemyBotBeforeDistantHumanTarget() {
        GameSession session = new GameSession(new GameSetup(
                "bot-very-near-enemy-priority-test",
                new WorldMap(9046, List.of()),
                List.of(
                        new FleetSetup("red", List.of(
                                ship("red-1", "red", 0, 0, 0, "bot", 2, 0)
                        )),
                        new FleetSetup("blue", List.of(
                                ship("blue-human", "blue", -900, 0, 0, "player-BP-test", 5, 0),
                                ship("blue-bot", "blue", 240, 0, 0, "bot", 5, 0)
                        ))
                ),
                List.of(new Vector2(0, 0), new Vector2(-900, 0), new Vector2(240, 0))
        ));

        session.update(0.05, radarService, navigationService, session.worldMap());

        ShipSnapshot attacker = findShip(session.snapshot(), "red-1");
        assertEquals(ENGINE_TWO_THIRDS, attacker.engineOrder());
        assertTrue(attacker.rudderDegrees() > 0);
    }

    @Test
    void botBoatFiresTorpedoesAcrossScaledCombatDistance() {
        GameSession session = new GameSession(new GameSetup(
                "bot-scaled-torpedo-fire-test",
                new WorldMap(9061, List.of()),
                List.of(
                        new FleetSetup("red", List.of(
                                ship("red-1", "red", 0, 0, 0, "bot", 5, 0, 0)
                        )),
                        new FleetSetup("blue", List.of(
                                ship("blue-1", "blue", 0, 520, Math.PI, "bot", 5, 0, 0)
                        ))
                ),
                List.of(new Vector2(0, 0), new Vector2(0, 520))
        ));

        GameSnapshot snapshot = session.snapshot();
        for (int index = 0; index < 30 && snapshot.torpedoes().isEmpty(); index += 1) {
            session.update(0.1, radarService, navigationService, session.worldMap());
            snapshot = session.snapshot();
        }

        assertFalse(snapshot.torpedoes().isEmpty());
        assertTrue(findShip(snapshot, "red-1").engineOrder() >= ENGINE_HALF);
        assertTrue(findShip(snapshot, "blue-1").engineOrder() >= ENGINE_HALF);
    }

    @Test
    void botBoatFavorsTargetsNearFriendlyHumanArea() {
        GameSession session = new GameSession(new GameSetup(
                "bot-friendly-human-area-target-test",
                new WorldMap(9045, List.of()),
                List.of(
                        new FleetSetup("red", List.of(
                                ship("red-1", "red", 0, 0, 0, "bot", 2, 0),
                                ship("red-human", "red", -500, 0, 0, "player-BP-test", 5, 0)
                        )),
                        new FleetSetup("blue", List.of(
                                ship("blue-close-bot", "blue", 250, 0, 0, "bot", 5, 0),
                                ship("blue-human-area-bot", "blue", -350, 0, 0, "bot", 5, 0)
                        ))
                ),
                List.of(new Vector2(0, 0), new Vector2(-500, 0), new Vector2(250, 0), new Vector2(-350, 0))
        ));

        session.update(0.05, radarService, navigationService, session.worldMap());

        ShipSnapshot attacker = findShip(session.snapshot(), "red-1");
        assertTrue(attacker.engineOrder() >= ENGINE_HALF);
        assertTrue(attacker.rudderDegrees() < 0);
    }

    @Test
    void botIgnoresEnemyBotOutsideRadarRingWhenHumanTargetIsVisible() {
        GameSession session = new GameSession(new GameSetup(
                "bot-human-priority-over-far-bot-test",
                new WorldMap(9044, List.of()),
                List.of(
                        new FleetSetup("red", List.of(
                                ship("red-1", "red", 0, 0, 0, "bot", 2, 0)
                        )),
                        new FleetSetup("blue", List.of(
                                ship("blue-human", "blue", -1200, 0, 0, "player-BP-test", 5, 0),
                                ship("blue-bot", "blue", 1040, 0, 0, "bot", 5, 0)
                        ))
                ),
                List.of(new Vector2(0, 0), new Vector2(-1200, 0), new Vector2(1040, 0))
        ));

        session.update(0.05, radarService, navigationService, session.worldMap());

        ShipSnapshot attacker = findShip(session.snapshot(), "red-1");
        assertEquals(ENGINE_FULL, attacker.engineOrder());
        assertTrue(attacker.rudderDegrees() < 0);
    }

    @Test
    void botTargetsHumanPlayerApproachingFromBehind() {
        GameSession session = new GameSession(new GameSetup(
                "bot-human-behind-test",
                new WorldMap(9018, List.of()),
                List.of(
                        new FleetSetup("red", List.of(
                                ship("red-1", "red", 0, 0, 0, "bot", 2, 0)
                        )),
                        new FleetSetup("blue", List.of(
                                ship("blue-human", "blue", -80, 0, 0, "player-BP-test", 5, 0)
                        ))
                ),
                List.of(new Vector2(0, 0), new Vector2(-80, 0))
        ));

        session.update(0.05, radarService, navigationService, session.worldMap());

        ShipSnapshot attacker = findShip(session.snapshot(), "red-1");
        assertEquals(ENGINE_FULL, attacker.engineOrder());
        assertTrue(attacker.rudderDegrees() < 0);
    }

    @Test
    void botTargetsHumanPlayerApproachingFromSide() {
        GameSession session = new GameSession(new GameSetup(
                "bot-human-side-test",
                new WorldMap(9019, List.of()),
                List.of(
                        new FleetSetup("red", List.of(
                                ship("red-1", "red", 0, 0, 0, "bot", 2, 0)
                        )),
                        new FleetSetup("blue", List.of(
                                ship("blue-human", "blue", 80, 0, 0, "player-BP-test", 5, 0)
                        ))
                ),
                List.of(new Vector2(0, 0), new Vector2(80, 0))
        ));

        session.update(0.05, radarService, navigationService, session.worldMap());

        ShipSnapshot attacker = findShip(session.snapshot(), "red-1");
        assertEquals(ENGINE_FULL, attacker.engineOrder());
        assertTrue(attacker.rudderDegrees() > 0);
    }

    @Test
    void botEvadesFriendlyTorpedoOnCollisionCourse() {
        GameSession session = new GameSession(new GameSetup(
                "bot-friendly-torpedo-evade-test",
                new WorldMap(9021, List.of()),
                List.of(
                        new FleetSetup("red", List.of(
                                ship("red-shooter", "red", 0, 0, 0, "bot", 5, 0, 0),
                                ship("red-friendly", "red", 0, 50, Math.PI, "bot", 5, 0)
                        )),
                        new FleetSetup("blue", List.of(
                                ship("blue-target", "blue", 0, 100, Math.PI, "bot", 2, 0)
                        ))
                ),
                List.of(new Vector2(0, 0), new Vector2(0, 50), new Vector2(0, 100))
        ));

        session.update(0.05, radarService, navigationService, session.worldMap());

        ShipSnapshot friendly = findShip(session.snapshot(), "red-friendly");
        assertEquals(ENGINE_FULL, friendly.engineOrder());
        assertTrue(Math.abs(friendly.rudderDegrees()) > 0);
    }

    @Test
    void botInterceptsHumanPlayerDetectedByRadarOutsideCloseView() {
        GameSession session = new GameSession(new GameSetup(
                "bot-human-radar-intercept-test",
                new WorldMap(9020, List.of()),
                List.of(
                        new FleetSetup("red", List.of(
                                ship("red-1", "red", 0, 0, 0, "bot", 2, 0)
                        )),
                        new FleetSetup("blue", List.of(
                                ship("blue-human", "blue", 650, 0, 0, "player-BP-test", 5, 0)
                        ))
                ),
                List.of(new Vector2(0, 0), new Vector2(650, 0))
        ));

        session.update(0.05, radarService, navigationService, session.worldMap());

        ShipSnapshot attacker = findShip(session.snapshot(), "red-1");
        assertEquals(7, attacker.engineOrder());
        assertTrue(attacker.rudderDegrees() > 0);
    }

    @Test
    void sideRamSinksTargetAndStopsAttacker() {
        GameSession session = new GameSession(new GameSetup(
                "ram-test",
                new WorldMap(9002, List.of()),
                List.of(
                        new FleetSetup("red", List.of(ship("red-1", "red", 0, 0, Math.PI / 2, "scenario", 5, 0))),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 56, 0, 0, "scenario", 2, 0)))
                ),
                List.of(new Vector2(0, 0), new Vector2(56, 0))
        ));

        GameSnapshot snapshot = tickUntilShipState(session, "blue-1", "sunk", 24);

        ShipSnapshot attacker = findShip(snapshot, "red-1");
        ShipSnapshot target = findShip(snapshot, "blue-1");
        assertNotNull(attacker);
        assertNotNull(target);
        assertEquals("active", attacker.state());
        assertEquals(0.0, attacker.speed(), 0.001);
        assertEquals(2, attacker.engineOrder());
        assertEquals("sunk", target.state());
        assertEquals(1, snapshot.destroyedShipsByTeam().get("blue"));
    }

    @Test
    void ramCollisionsIgnoreScoutPlanes() {
        GameSession session = new GameSession(new GameSetup(
                "scout-plane-ram-ignore-test",
                new WorldMap(9028, List.of()),
                List.of(
                        new FleetSetup("red", List.of(ship("red-1", "red", 0, 0, Math.PI / 2, 5, 0))),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 56, 0, 0, "bot", 2, 0)))
                ),
                List.of(new Vector2(0, 0), new Vector2(56, 0))
        ));

        session.updatePlayerState(
                new PlayerStateUpdate("player-BP-test", "blue", 56, 0, 0, 8, 0, 7, 0, 0, false, "scout-plane"),
                navigationService,
                session.worldMap()
        );
        GameSnapshot snapshot = tickUntilShipsTouch(session, 24);

        assertEquals("active", findShip(snapshot, "red-1").state());
        assertEquals("active", findShip(snapshot, "blue-1").state());
        assertEquals("scout-plane", findShip(snapshot, "blue-1").vehicleType());
        assertEquals(0, snapshot.destroyedShipsByTeam().get("blue"));
    }

    @Test
    void scoutPlaneCannotRamBoatFromAbove() {
        GameSession session = new GameSession(new GameSetup(
                "scout-plane-over-boat-ram-ignore-test",
                new WorldMap(9033, List.of()),
                List.of(
                        new FleetSetup("red", List.of(ship("red-1", "red", 0, 0, Math.PI / 2, "bot", 2, 0))),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 0, 0, 0, "bot", 2, 0)))
                ),
                List.of(new Vector2(0, 0))
        ));

        session.updatePlayerState(
                new PlayerStateUpdate("player-BP-test", "red", 0, 0, Math.PI / 2, 8, 0, 7, 0, 0, false, "scout-plane", 40),
                navigationService,
                session.worldMap()
        );
        session.update(0.05, radarService, navigationService, session.worldMap());
        GameSnapshot snapshot = session.snapshot();

        assertEquals("active", findShip(snapshot, "red-1").state());
        assertEquals("active", findShip(snapshot, "blue-1").state());
        assertEquals("scout-plane", findShip(snapshot, "red-1").vehicleType());
        assertEquals(0, snapshot.destroyedShipsByTeam().get("red"));
        assertEquals(0, snapshot.destroyedShipsByTeam().get("blue"));
    }

    @Test
    void surfaceShipCanRamPeriscopeDepthSubmarineOnlyAtPeriscope() {
        GameSession session = new GameSession(new GameSetup(
                "periscope-ram-test",
                new WorldMap(9075, List.of()),
                List.of(
                        new FleetSetup("red", List.of(ship("red-1", "red", 0, -11.04, 0, "player-red", ENGINE_FULL, 0, 99))),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 0, 0, 0, "player-blue", ENGINE_STOP, 0, 99, "submarine")))
                ),
                List.of(new Vector2(0, -11.04), new Vector2(0, 0))
        ));
        session.updatePlayerState(
                new PlayerStateUpdate("player-red", "red", 0, -11.04, 0, 8, 0, ENGINE_FULL, 0, 0, true,
                        "torpedo-boat"),
                navigationService,
                session.worldMap()
        );
        session.updatePlayerState(
                new PlayerStateUpdate("player-blue", "blue", 0, 0, 0, 0, 0, ENGINE_STOP, 0, 0, true,
                        "submarine", 0, 0, null, null, null, null, "periscope"),
                navigationService,
                session.worldMap()
        );

        session.update(0, radarService, navigationService, session.worldMap());

        GameSnapshot snapshot = session.snapshot();
        assertEquals("active", findShip(snapshot, "red-1").state());
        assertEquals("sunk", findShip(snapshot, "blue-1").state());
    }

    @Test
    void slowSurfaceShipCanRamPeriscopeDepthSubmarine() {
        GameSession session = new GameSession(new GameSetup(
                "slow-periscope-ram-test",
                new WorldMap(9079, List.of()),
                List.of(
                        new FleetSetup("red", List.of(ship("red-1", "red", 0, -11.04, 0, "player-red", ENGINE_SLOW, 0, 99))),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 0, 0, 0, "player-blue", ENGINE_STOP, 0, 99, "submarine")))
                ),
                List.of(new Vector2(0, -11.04), new Vector2(0, 0))
        ));
        session.updatePlayerState(
                new PlayerStateUpdate("player-red", "red", 0, -11.04, 0, 1.0, 0, ENGINE_SLOW, 0, 0, true,
                        "torpedo-boat"),
                navigationService,
                session.worldMap()
        );
        session.updatePlayerState(
                new PlayerStateUpdate("player-blue", "blue", 0, 0, 0, 0, 0, ENGINE_STOP, 0, 0, true,
                        "submarine", 0, 0, null, null, null, null, "periscope"),
                navigationService,
                session.worldMap()
        );

        session.update(0, radarService, navigationService, session.worldMap());

        GameSnapshot snapshot = session.snapshot();
        assertEquals("active", findShip(snapshot, "red-1").state());
        assertEquals("sunk", findShip(snapshot, "blue-1").state());
    }

    @Test
    void surfaceShipCanPassOverPeriscopeDepthSubmarineHullAwayFromPeriscope() {
        GameSession session = new GameSession(new GameSetup(
                "periscope-hull-pass-over-test",
                new WorldMap(9076, List.of()),
                List.of(
                        new FleetSetup("red", List.of(ship("red-1", "red", 0, -11.04, 0, "player-red", ENGINE_FULL, 0, 99))),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 1.2, 0, 0, "player-blue", ENGINE_STOP, 0, 99, "submarine")))
                ),
                List.of(new Vector2(0, -11.04), new Vector2(1.2, 0))
        ));
        session.updatePlayerState(
                new PlayerStateUpdate("player-red", "red", 0, -11.04, 0, 8, 0, ENGINE_FULL, 0, 0, true,
                        "torpedo-boat"),
                navigationService,
                session.worldMap()
        );
        session.updatePlayerState(
                new PlayerStateUpdate("player-blue", "blue", 1.2, 0, 0, 0, 0, ENGINE_STOP, 0, 0, true,
                        "submarine", 0, 0, null, null, null, null, "periscope"),
                navigationService,
                session.worldMap()
        );

        session.update(0, radarService, navigationService, session.worldMap());

        GameSnapshot snapshot = session.snapshot();
        assertEquals("active", findShip(snapshot, "red-1").state());
        assertEquals("active", findShip(snapshot, "blue-1").state());
    }

    @Test
    void surfaceShipDoesNotRamFullySubmergedSubmarine() {
        GameSession session = new GameSession(new GameSetup(
                "submerged-ram-ignore-test",
                new WorldMap(9077, List.of()),
                List.of(
                        new FleetSetup("red", List.of(ship("red-1", "red", 0, -11.04, 0, "player-red", ENGINE_FULL, 0, 99))),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 0, 0, 0, "player-blue", ENGINE_STOP, 0, 99, "submarine")))
                ),
                List.of(new Vector2(0, -11.04), new Vector2(0, 0))
        ));
        session.updatePlayerState(
                new PlayerStateUpdate("player-red", "red", 0, -11.04, 0, 8, 0, ENGINE_FULL, 0, 0, true,
                        "torpedo-boat"),
                navigationService,
                session.worldMap()
        );
        session.updatePlayerState(
                new PlayerStateUpdate("player-blue", "blue", 0, 0, 0, 0, 0, ENGINE_STOP, 0, 0, true,
                        "submarine", 0, 0, null, null, null, null, "submerged"),
                navigationService,
                session.worldMap()
        );

        session.update(0, radarService, navigationService, session.worldMap());

        GameSnapshot snapshot = session.snapshot();
        assertEquals("active", findShip(snapshot, "red-1").state());
        assertEquals("active", findShip(snapshot, "blue-1").state());
    }

    @Test
    void fullySubmergedSubmarinesCanRamEachOther() {
        GameSession session = new GameSession(new GameSetup(
                "submerged-submarine-ram-test",
                new WorldMap(9078, List.of()),
                List.of(
                        new FleetSetup("red", List.of(ship("red-1", "red", 0, -11.04, 0, "player-red", ENGINE_FULL, 0, 99, "submarine"))),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 0, 0, Math.PI, "player-blue", ENGINE_STOP, 0, 99, "submarine")))
                ),
                List.of(new Vector2(0, -11.04), new Vector2(0, 0))
        ));
        session.updatePlayerState(
                new PlayerStateUpdate("player-red", "red", 0, -11.04, 0, 8, 0, ENGINE_FULL, 0, 0, true,
                        "submarine", 0, 0, null, null, null, null, "submerged"),
                navigationService,
                session.worldMap()
        );
        session.updatePlayerState(
                new PlayerStateUpdate("player-blue", "blue", 0, 0, Math.PI, 0, 0, ENGINE_STOP, 0, 0, true,
                        "submarine", 0, 0, null, null, null, null, "submerged"),
                navigationService,
                session.worldMap()
        );

        session.update(0, radarService, navigationService, session.worldMap());

        GameSnapshot snapshot = session.snapshot();
        assertEquals("active", findShip(snapshot, "red-1").state());
        assertEquals("sunk", findShip(snapshot, "blue-1").state());
    }

    @Test
    void diagonalSideRamSinksTargetOnly() {
        GameSession session = new GameSession(new GameSetup(
                "diagonal-ram-test",
                new WorldMap(9003, List.of()),
                List.of(
                        new FleetSetup("red", List.of(ship("red-1", "red", 0, 0, Math.PI / 4, "scenario", 5, 0))),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 41, 41, 0, "scenario", 2, 0)))
                ),
                List.of(new Vector2(0, 0), new Vector2(41, 41))
        ));

        GameSnapshot snapshot = tickUntilShipState(session, "blue-1", "sunk", 30);

        assertEquals("active", findShip(snapshot, "red-1").state());
        assertEquals("sunk", findShip(snapshot, "blue-1").state());
        assertEquals(1, snapshot.destroyedShipsByTeam().get("blue"));
        assertEquals(0, snapshot.destroyedShipsByTeam().get("red"));
    }

    @Test
    void steepSideRamAtEightyDegreesSinksTargetOnly() {
        double attackerHeading = Math.toRadians(80);
        Vector2 targetPosition = Vector2.fromHeading(attackerHeading).scale(56);
        GameSession session = new GameSession(new GameSetup(
                "steep-side-ram-test",
                new WorldMap(9011, List.of()),
                List.of(
                        new FleetSetup("red", List.of(ship("red-1", "red", 0, 0, attackerHeading, "scenario", 5, 0))),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", targetPosition.x(), targetPosition.z(), 0, "scenario", 2, 0)))
                ),
                List.of(new Vector2(0, 0), targetPosition)
        ));

        GameSnapshot snapshot = tickUntilShipState(session, "blue-1", "sunk", 30);

        assertEquals("active", findShip(snapshot, "red-1").state());
        assertEquals("sunk", findShip(snapshot, "blue-1").state());
        assertEquals(1, snapshot.destroyedShipsByTeam().get("blue"));
        assertEquals(0, snapshot.destroyedShipsByTeam().get("red"));
    }

    @Test
    void shallowAngleRamChangesCourseWithoutSinkingShips() {
        double attackerHeading = Math.toRadians(8);
        Vector2 targetPosition = Vector2.fromHeading(attackerHeading).scale(56);
        GameSession session = new GameSession(new GameSetup(
                "shallow-angle-ram-test",
                new WorldMap(9012, List.of()),
                List.of(
                        new FleetSetup("red", List.of(ship("red-1", "red", 0, 0, attackerHeading, "scenario", 5, 0))),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", targetPosition.x(), targetPosition.z(), 0, "scenario", 2, 0)))
                ),
                List.of(new Vector2(0, 0), targetPosition)
        ));

        GameSnapshot snapshot = tickUntilShipsTouch(session, 30);

        ShipSnapshot attacker = findShip(snapshot, "red-1");
        ShipSnapshot target = findShip(snapshot, "blue-1");
        assertEquals("active", attacker.state());
        assertEquals("active", target.state());
        assertEquals(0, snapshot.destroyedShipsByTeam().get("red"));
        assertEquals(0, snapshot.destroyedShipsByTeam().get("blue"));
        assertTrue(Math.abs(MathSupport.normalizeAngle(attacker.heading() - attackerHeading)) > Math.toRadians(2));
        assertTrue(Math.abs(MathSupport.normalizeAngle(target.heading())) > Math.toRadians(2));
    }

    @Test
    void botGlancingRamBacksBothShipsAway() {
        GameSession session = new GameSession(new GameSetup(
                "bot-glancing-ram-test",
                new WorldMap(9020, List.of()),
                List.of(
                        new FleetSetup("red", List.of(ship("red-1", "red", 0, 0, 0, "bot", 5, 0))),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 3, 0, Math.PI, "bot", 5, 0)))
                ),
                List.of(new Vector2(0, 0), new Vector2(3, 0))
        ));

        GameSnapshot snapshot = tickUntilShipsUseEngineOrder(session, ENGINE_FULL_ASTERN, 1);

        ShipSnapshot attacker = findShip(snapshot, "red-1");
        ShipSnapshot target = findShip(snapshot, "blue-1");
        assertEquals("active", attacker.state());
        assertEquals("active", target.state());
        assertEquals(ENGINE_FULL_ASTERN, attacker.engineOrder());
        assertEquals(ENGINE_FULL_ASTERN, target.engineOrder());
    }

    @Test
    void botGlancingRamBackoffSteersAndOpensDistance() {
        GameSession session = new GameSession(new GameSetup(
                "bot-glancing-ram-steer-test",
                new WorldMap(9021, List.of()),
                List.of(
                        new FleetSetup("red", List.of(ship("red-1", "red", 0, 0, 0, "bot", 5, 0))),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 3, 0, Math.PI, "bot", 5, 0)))
                ),
                List.of(new Vector2(0, 0), new Vector2(3, 0))
        ));

        double initialDistance = distanceBetween(session.snapshot(), "red-1", "blue-1");
        GameSnapshot snapshot = session.snapshot();
        for (double elapsed = 0; elapsed < 2.7; elapsed += 0.05) {
            session.update(0.05, radarService, navigationService, session.worldMap());
            snapshot = session.snapshot();
        }

        ShipSnapshot attacker = findShip(snapshot, "red-1");
        ShipSnapshot target = findShip(snapshot, "blue-1");
        assertEquals("active", attacker.state());
        assertEquals("active", target.state());
        assertEquals(ENGINE_FULL_ASTERN, attacker.engineOrder());
        assertEquals(ENGINE_FULL_ASTERN, target.engineOrder());
        assertTrue(Math.abs(attacker.rudderDegrees()) > 0);
        assertTrue(Math.abs(target.rudderDegrees()) > 0);
        double finalDistance = distanceBetween(snapshot, "red-1", "blue-1");
        assertTrue(
                finalDistance > initialDistance + 6.0,
                "expected distance to grow from " + initialDistance + " to more than " + (initialDistance + 6.0)
                        + " but was " + finalDistance
        );
    }

    @Test
    void botRamCollisionDoesNotSinkEnemyShip() {
        GameSession session = new GameSession(new GameSetup(
                "bot-ram-no-sink-test",
                new WorldMap(9069, List.of()),
                List.of(
                        new FleetSetup("red", List.of(ship("red-1", "red", 0, 0, Math.PI / 2, "bot", ENGINE_FULL, 0))),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 56, 0, 0, "scenario", ENGINE_STOP, 0)))
                ),
                List.of(new Vector2(0, 0), new Vector2(56, 0))
        ));

        GameSnapshot snapshot = tickUntilShipsTouch(session, 24);

        assertEquals("active", findShip(snapshot, "red-1").state());
        assertEquals("active", findShip(snapshot, "blue-1").state());
        assertEquals(0, snapshot.destroyedShipsByTeam().get("blue"));
    }

    @Test
    void playerSideRamEnemyScoresKillWithoutSinkingAttacker() {
        String playerId = "player-BP-test";
        GameSession session = new GameSession(new GameSetup(
                "player-ram-score-test",
                new WorldMap(9007, List.of()),
                List.of(
                        new FleetSetup("red", List.of(ship("red-1", "red", 0, 0, Math.PI / 2, "bot", 5, 0))),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 56, 0, 0, "scenario", 2, 0)))
                ),
                List.of(new Vector2(0, 0), new Vector2(56, 0))
        ));

        GameSnapshot snapshot = tickPlayerForwardUntilShipState(
                session, playerId, "red", "blue-1", "sunk", new Vector2(0, 0), Math.PI / 2, 24
        );

        assertEquals("active", findShip(snapshot, "red-1").state());
        assertEquals("sunk", findShip(snapshot, "blue-1").state());
        assertEquals(1, snapshot.destroyedShipsByTeam().get("blue"));
        assertEquals(0, snapshot.destroyedShipsByTeam().get("red"));
        assertEquals(1, snapshot.killsByPlayer().get(playerId));
        assertEquals(1, snapshot.ramHits().size());
        assertEquals("red-1", snapshot.ramHits().get(0).shipId());
        assertEquals("blue-1", snapshot.ramHits().get(0).targetShipId());
    }

    @Test
    void playerSideRamFriendlyShipDoesNotSinkOrScore() {
        String playerId = "player-BP-test";
        GameSession session = new GameSession(new GameSetup(
                "player-friendly-ram-score-test",
                new WorldMap(9008, List.of()),
                List.of(
                        new FleetSetup("red", List.of(
                                ship("red-1", "red", 0, 0, Math.PI / 2, "bot", 5, 0),
                                ship("red-2", "red", 56, 0, 0, "scenario", 2, 0)
                        )),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 240, 240, Math.PI, 2, 0)))
                ),
                List.of(new Vector2(0, 0), new Vector2(56, 0), new Vector2(240, 240))
        ));

        GameSnapshot snapshot = tickPlayerForwardUntilShipState(session, playerId, "red", "red-2", "sunk",
                new Vector2(0, 0), Math.PI / 2, 2);

        assertEquals("active", findShip(snapshot, "red-1").state());
        assertEquals("active", findShip(snapshot, "red-2").state());
        assertEquals(0, snapshot.destroyedShipsByTeam().get("red"));
        assertFalse(snapshot.killsByPlayer().containsKey(playerId));
    }

    @Test
    void playerLosesThreePointsWhenSunk() {
        String playerId = "player-BP-test";
        GameSession session = new GameSession(new GameSetup(
                "player-sunk-score-test",
                new WorldMap(9014, List.of()),
                List.of(
                        new FleetSetup("red", List.of(ship("red-1", "red", 0, 0, Math.PI / 2, "scenario", 5, 0))),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 56, 0, 0, playerId, 2, 0)))
                ),
                List.of(new Vector2(0, 0), new Vector2(56, 0))
        ));

        GameSnapshot snapshot = tickUntilShipState(session, "blue-1", "sunk", 24);

        assertEquals("active", findShip(snapshot, "red-1").state());
        assertEquals("sunk", findShip(snapshot, "blue-1").state());
        assertEquals(-3, snapshot.killsByPlayer().get(playerId));
    }

    @Test
    void diagonalPlayerSideRamEnemyScoresKillWithoutSinkingAttacker() {
        String playerId = "player-BP-test";
        GameSession session = new GameSession(new GameSetup(
                "player-diagonal-ram-score-test",
                new WorldMap(9009, List.of()),
                List.of(
                        new FleetSetup("red", List.of(ship("red-1", "red", 0, 0, Math.PI / 4, playerId, 5, 0))),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 41, 41, 0, 2, 0)))
                ),
                List.of(new Vector2(0, 0), new Vector2(41, 41))
        ));

        GameSnapshot snapshot = tickPlayerForwardUntilShipState(
                session, playerId, "red", "blue-1", "sunk", new Vector2(0, 0), Math.PI / 4, 30
        );

        assertEquals("active", findShip(snapshot, "red-1").state());
        assertEquals("sunk", findShip(snapshot, "blue-1").state());
        assertEquals(1, snapshot.killsByPlayer().get(playerId));
    }

    @Test
    void releasePlayerReturnsAssignedShipToBotControl() {
        GameSession session = new GameSession(new GameSetup(
                "release-test",
                new WorldMap(9005, List.of()),
                List.of(
                        new FleetSetup("red", List.of(ship("red-1", "red", 0, 0, 0, 2, 0))),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 40, 0, Math.PI, 2, 0)))
                ),
                List.of(new Vector2(0, 0), new Vector2(40, 0))
        ));

        session.updatePlayerState(new PlayerStateUpdate("player-BP-test", "red", 0, 0, 0, 0, 0, 2, 0, 0, false), navigationService, session.worldMap());
        assertEquals("player-BP-test", findShip(session.snapshot(), "red-1").controlledBy());

        session.releasePlayer("player-BP-test");

        assertEquals("bot", findShip(session.snapshot(), "red-1").controlledBy());
    }

    @Test
    void joiningPlayerTakesOneOfTheAvailableFleetShips() {
        GameSession session = new GameSession(new GameSetup(
                "player-start-choice-test",
                new WorldMap(9017, List.of()),
                List.of(
                        new FleetSetup("red", List.of(
                                ship("red-1", "red", -100, 0, 0, "bot", 2, 0),
                                ship("red-2", "red", 0, 0, 0, "bot", 2, 0),
                                ship("red-3", "red", 100, 0, 0, "bot", 2, 0)
                        )),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 400, 0, Math.PI, 2, 0)))
                ),
                List.of(new Vector2(-100, 0), new Vector2(0, 0), new Vector2(100, 0), new Vector2(400, 0))
        ));

        session.updatePlayerState(new PlayerStateUpdate("player-BP-test", "red", 0, 0, 0, 0, 0, 2, 0, 0, false),
                navigationService, session.worldMap());

        List<String> controlledShips = session.snapshot().ships().stream()
                .filter(ship -> "player-BP-test".equals(ship.controlledBy()))
                .map(ShipSnapshot::id)
                .toList();
        assertEquals(1, controlledShips.size());
        assertTrue(List.of("red-1", "red-2", "red-3").contains(controlledShips.get(0)));
    }

    @Test
    void joiningPlayerCreatesAdditionalShipWhenAllBoatsAreHumanControlled() {
        GameSession session = new GameSession(new GameSetup(
                "player-overflow-test",
                new WorldMap(9029, List.of()),
                List.of(
                        new FleetSetup("red", List.of(
                                ship("red-1", "red", -100, 0, 0, "player-AA-test", 2, 0),
                                ship("red-2", "red", 0, 0, 0, "player-BB-test", 2, 0),
                                ship("red-3", "red", 100, 0, 0, "player-CC-test", 2, 0)
                        )),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 400, 0, Math.PI, 2, 0)))
                ),
                List.of(new Vector2(-100, 0), new Vector2(0, 0), new Vector2(100, 0), new Vector2(400, 0))
        ));

        GameSnapshot snapshot = session.updatePlayerState(new PlayerStateUpdate(
                "player-DD-test", "red", 180, 0, 0, 0, 0, 2, 0, 0, false, "torpedo-boat"
        ), navigationService, session.worldMap());

        List<ShipSnapshot> redShips = snapshot.ships().stream()
                .filter(ship -> "red".equals(ship.teamId()))
                .toList();
        assertEquals(4, redShips.size());
        ShipSnapshot joinedShip = redShips.stream()
                .filter(ship -> "player-DD-test".equals(ship.controlledBy()))
                .findFirst()
                .orElseThrow();
        assertEquals("torpedo-boat", joinedShip.vehicleType());
        assertEquals(180, joinedShip.x(), 0.001);
        assertEquals(0, joinedShip.z(), 0.001);
    }

    @Test
    void releasingAdditionalHumanShipRemovesItInsteadOfCreatingExtraBot() {
        GameSession session = new GameSession(new GameSetup(
                "player-overflow-release-test",
                new WorldMap(9030, List.of()),
                List.of(
                        new FleetSetup("red", List.of(
                                ship("red-1", "red", -100, 0, 0, "player-AA-test", 2, 0),
                                ship("red-2", "red", 0, 0, 0, "player-BB-test", 2, 0)
                        )),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 400, 0, Math.PI, 2, 0)))
                ),
                List.of(new Vector2(-100, 0), new Vector2(0, 0), new Vector2(400, 0))
        ));
        session.updatePlayerState(new PlayerStateUpdate(
                "player-CC-test", "red", 180, 0, 0, 0, 0, 2, 0, 0, false, "torpedo-boat"
        ), navigationService, session.worldMap());

        session.releasePlayer("player-CC-test");

        List<ShipSnapshot> redShips = session.snapshot().ships().stream()
                .filter(ship -> "red".equals(ship.teamId()))
                .toList();
        assertEquals(2, redShips.size());
        assertTrue(redShips.stream().noneMatch(ship -> "player-CC-test".equals(ship.controlledBy())));
    }

    @Test
    void sinkingAdditionalHumanShipRemovesItInsteadOfRespawningExtraBot() throws Exception {
        GameSession session = new GameSession(new GameSetup(
                "player-overflow-sink-test",
                new WorldMap(9030, List.of()),
                List.of(
                        new FleetSetup("red", List.of(
                                ship("red-1", "red", -100, 0, 0, "player-AA-test", 2, 0),
                                ship("red-2", "red", 0, 0, 0, "player-BB-test", 2, 0)
                        )),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 400, 0, Math.PI, 2, 0)))
                ),
                List.of(new Vector2(-100, 0), new Vector2(0, 0), new Vector2(400, 0))
        ));
        GameSnapshot joined = session.updatePlayerState(new PlayerStateUpdate(
                "player-CC-test", "red", 180, 0, 0, 0, 0, 2, 0, 0, false, "torpedo-boat"
        ), navigationService, session.worldMap());
        String extraShipId = joined.ships().stream()
                .filter(ship -> "player-CC-test".equals(ship.controlledBy()))
                .findFirst()
                .orElseThrow()
                .id();

        sinkShip(session, extraShipId);
        session.update(10, radarService, navigationService, session.worldMap());

        List<ShipSnapshot> redShips = session.snapshot().ships().stream()
                .filter(ship -> "red".equals(ship.teamId()))
                .toList();
        assertEquals(2, redShips.size());
        assertTrue(redShips.stream().noneMatch(ship -> extraShipId.equals(ship.id())));
    }

    @Test
    void playerStateUsesClientPositionWithoutServerAdvancingHumanShip() {
        GameSession session = new GameSession(new GameSetup(
                "client-authority-test",
                new WorldMap(9006, List.of()),
                List.of(
                        new FleetSetup("red", List.of(ship("red-1", "red", 0, 0, 0, 2, 0))),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 200, 0, Math.PI, 2, 0)))
                ),
                List.of(new Vector2(0, 0), new Vector2(200, 0))
        ));

        session.updatePlayerState(
                new PlayerStateUpdate("player-BP-test", "red", 15, 25, 0.7, 9.6, 0.12, 7, 14, 123.45, false),
                navigationService,
                session.worldMap()
        );
        ShipSnapshot afterClientUpdate = findShip(session.snapshot(), "red-1");
        assertEquals(15, afterClientUpdate.x(), 0.001);
        assertEquals(25, afterClientUpdate.z(), 0.001);
        assertEquals(0.7, afterClientUpdate.heading(), 0.001);
        assertEquals(9.6, afterClientUpdate.speed(), 0.001);
        assertEquals(0.12, afterClientUpdate.turnVelocity(), 0.001);

        session.update(0.5, new RadarService(), navigationService, session.worldMap());

        ShipSnapshot afterServerTick = findShip(session.snapshot(), "red-1");
        assertEquals(15, afterServerTick.x(), 0.001);
        assertEquals(25, afterServerTick.z(), 0.001);
        assertEquals(0.7, afterServerTick.heading(), 0.001);
    }

    @Test
    void playerStateUpdatesWeaponAimForExternalBoatView() {
        GameSession session = new GameSession(new GameSetup(
                "weapon-aim-state-test",
                new WorldMap(9025, List.of()),
                List.of(
                        new FleetSetup("red", List.of(ship("red-1", "red", 0, 0, 0, 2, 0))),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 200, 0, Math.PI, 2, 0)))
                ),
                List.of(new Vector2(0, 0), new Vector2(200, 0))
        ));

        session.updatePlayerState(
                new PlayerStateUpdate(
                        "player-BP-test",
                        "red",
                        0,
                        0,
                        0,
                        0,
                        0,
                        ENGINE_FULL,
                        0,
                        123.45,
                        false,
                        "torpedo-boat",
                        0,
                        0,
                        Math.PI,
                        0.33,
                        -0.72,
                        0.11
                ),
                navigationService,
                session.worldMap()
        );

        ShipSnapshot snapshot = findShip(session.snapshot(), "red-1");
        assertEquals(MathSupport.round(Math.PI), snapshot.flakYaw(), 0.001);
        assertEquals(0.33, snapshot.flakPitch(), 0.001);
        assertEquals(-0.72, snapshot.cannonYaw(), 0.001);
        assertEquals(0.11, snapshot.cannonPitch(), 0.001);
    }

    @Test
    void scoutPlaneSnapshotIncludesClientAltitude() {
        GameSession session = new GameSession(new GameSetup(
                "scout-plane-altitude-test",
                new WorldMap(9035, List.of()),
                List.of(new FleetSetup("light", List.of(
                        ship("light-1", "light", 0, 0, 0, "bot", 5, 0, 0)
                ))),
                List.of(new Vector2(0, 0))
        ));

        session.updatePlayerState(
                new PlayerStateUpdate("player-BP-test", "light", 0, 0, 0, 8, 0, 7, 0, 0, false, "scout-plane", 64),
                navigationService,
                session.worldMap()
        );

        ShipSnapshot ship = findShip(session.snapshot(), "light-1");
        assertEquals("scout-plane", ship.vehicleType());
        assertEquals(64, ship.y(), 0.001);

        session.updatePlayerState(
                new PlayerStateUpdate("player-BP-test", "light", 0, 0, 0, 8, 0, 7, 0, 0, false, "scout-plane", 2),
                navigationService,
                session.worldMap()
        );
        assertEquals(5.25, findShip(session.snapshot(), "light-1").y(), 0.001);

        session.updatePlayerState(
                new PlayerStateUpdate("player-BP-test", "light", 0, 0, 0, 8, 0, 7, 0, 0, false, "scout-plane", 501),
                navigationService,
                session.worldMap()
        );
        assertEquals(350, findShip(session.snapshot(), "light-1").y(), 0.001);
    }

    @Test
    void defaultFactoryCanSelectSmallTestSetups() {
        DefaultGameSetupFactory factory = new DefaultGameSetupFactory(new WorldMapService());

        GameSetup singleIsland = factory.setup("single-island");
        assertEquals("single-island", singleIsland.id());
        assertEquals(1001, singleIsland.worldMap().version());
        assertEquals(1, singleIsland.worldMap().landmasses().size());
        assertEquals(2, singleIsland.fleets().stream().mapToInt(fleet -> fleet.ships().size()).sum());

        GameSetup ramSide = factory.setup("ram-side");
        assertEquals("ram-side", ramSide.id());
        assertEquals(1002, ramSide.worldMap().version());
        assertEquals(2, ramSide.fleets().stream().mapToInt(fleet -> fleet.ships().size()).sum());

        GameSetup explosionDemo = factory.setup("explosion-demo");
        assertEquals("explosion-demo", explosionDemo.id());
        assertEquals(30, explosionDemo.fleets().stream().mapToInt(fleet -> fleet.ships().size()).sum());

        GameSetup escortDebug = factory.setup("escort-debug");
        assertEquals("escort-debug", escortDebug.id());
        assertEquals(12, escortDebug.fleets().stream().mapToInt(fleet -> fleet.ships().size()).sum());
        assertEquals(6, countScoutPlanes(escortDebug));

        GameSetup landmarkTour = factory.setup("landmark-tour");
        assertEquals("landmark-tour", landmarkTour.id());
        assertEquals(16, landmarkTour.worldMap().version());
        assertEquals(7, landmarkTour.fleets().stream().mapToInt(fleet -> fleet.ships().size()).sum());
        assertEquals(List.of("light", "dark"), landmarkTour.fleets().stream().map(FleetSetup::teamId).toList());
        assertEquals(6, countScoutPlanes(landmarkTour));

        GameSetup denseLand = factory.setup("dense-land");
        assertEquals("dense-land", denseLand.id());
        assertEquals(16, denseLand.worldMap().version());
        assertEquals(30, denseLand.fleets().stream().mapToInt(fleet -> fleet.ships().size()).sum());
        assertEquals(List.of("dark", "light"),
                denseLand.fleets().stream().map(FleetSetup::teamId).toList());
        assertEquals(List.of(15, 15), denseLand.fleets().stream().map(fleet -> fleet.ships().size()).toList());
        assertEquals(2, countScoutPlanes(denseLand));

        GameSetup crowded = factory.setup("dense-land-crowded");
        assertEquals(2, countScoutPlanes(crowded));

        GameSetup crowdedReverse = factory.setup("dense-land-crowded-reverse");
        assertEquals(2, countScoutPlanes(crowdedReverse));

        GameSetup bombDropScenario = factory.setup("scenario-bomb-drop");
        assertEquals("scenario-bomb-drop", bombDropScenario.id());
        assertEquals(2, bombDropScenario.fleets().stream().mapToInt(fleet -> fleet.ships().size()).sum());
        assertEquals(1, countScoutPlanes(bombDropScenario));

        GameSetup twoShipDuel = factory.setup("two-ship-duel");
        assertEquals("two-ship-duel", twoShipDuel.id());
        assertEquals(16, twoShipDuel.worldMap().version());
        assertFalse(twoShipDuel.worldMap().landmasses().isEmpty());
        assertEquals(List.of("light", "dark"), twoShipDuel.fleets().stream().map(FleetSetup::teamId).toList());
        assertEquals(List.of(1, 1), twoShipDuel.fleets().stream().map(fleet -> fleet.ships().size()).toList());
        assertEquals(List.of("scenario", "scenario"), twoShipDuel.fleets().stream()
                .flatMap(fleet -> fleet.ships().stream())
                .map(ShipSetup::controlledBy)
                .toList());
        assertEquals(0, countScoutPlanes(twoShipDuel));
    }

    @Test
    void manualSpecialMenuSetupsExceptSideViewContainScoutPlanes() {
        DefaultGameSetupFactory factory = new DefaultGameSetupFactory(new WorldMapService());

        List<String> setupIds = List.of(
                "dense-land",
                "islands",
                "escort-debug",
                "landmark-tour",
                "dense-land-crowded",
                "dense-land-crowded-reverse",
                "scout-plane"
        );

        for (String setupId : setupIds) {
            assertTrue(countScoutPlanes(factory.setup(setupId)) > 0, setupId);
        }
        assertEquals(0, countScoutPlanes(factory.setup("side-view-sandbox")));
    }

    @Test
    void denseLandAddsAdditionalPartiesOnlyForRequestedHumanTeams() {
        DefaultGameSetupFactory factory = new DefaultGameSetupFactory(new WorldMapService());

        GameSetup threeTeams = factory.setup("dense-land", List.of("green"));
        assertEquals(List.of("dark", "light", "green"),
                threeTeams.fleets().stream().map(FleetSetup::teamId).toList());
        assertEquals(List.of(10, 10, 10), threeTeams.fleets().stream().map(fleet -> fleet.ships().size()).toList());

        GameSetup fourTeams = factory.setup("dense-land", List.of("green", "sand"));
        assertEquals(List.of("dark", "light", "green", "sand"),
                fourTeams.fleets().stream().map(FleetSetup::teamId).toList());
        assertEquals(List.of(8, 8, 7, 7), fourTeams.fleets().stream().map(fleet -> fleet.ships().size()).toList());
    }

    @Test
    void defaultSetupPlacesShipsAndRespawnsInNavigableWater() {
        assertSetupPlacesShipsAndRespawnsInNavigableWater(new DefaultGameSetupFactory(new WorldMapService()).defaultSetup());
    }

    @Test
    void denseLandBotBoatsKeepMovingAndFiringAfterScaleChange() {
        GameSession session = new GameSession(new DefaultGameSetupFactory(new WorldMapService()).setup("dense-land"));

        GameSnapshot snapshot = session.snapshot();
        Map<String, Vector2> startPositions = snapshot.ships().stream()
                .filter(ship -> "bot".equals(ship.controlledBy()))
                .filter(ship -> !"scout-plane".equals(ship.vehicleType()))
                .collect(java.util.stream.Collectors.toMap(
                        ShipSnapshot::id,
                        ship -> new Vector2(ship.x(), ship.z())
                ));

        for (int index = 0; index < 120; index += 1) {
            session.update(0.1, radarService, navigationService, session.worldMap());
        }
        int warmupTorpedoes = session.snapshot().torpedoes().size();

        for (int index = 0; index < 280; index += 1) {
            session.update(0.1, radarService, navigationService, session.worldMap());
            snapshot = session.snapshot();
        }

        GameSnapshot finalSnapshot = snapshot;
        long movingBotBoats = finalSnapshot.ships().stream()
                .filter(ship -> "active".equals(ship.state()))
                .filter(ship -> "bot".equals(ship.controlledBy()))
                .filter(ship -> !"scout-plane".equals(ship.vehicleType()))
                .filter(ship -> Math.abs(ship.speed()) >= 2.0)
                .count();
        long botBoatsThatMadeWay = finalSnapshot.ships().stream()
                .filter(ship -> "bot".equals(ship.controlledBy()))
                .filter(ship -> !"scout-plane".equals(ship.vehicleType()))
                .filter(ship -> {
                    Vector2 start = startPositions.get(ship.id());
                    return start != null && start.distanceTo(new Vector2(ship.x(), ship.z())) >= 55;
                })
                .count();
        long shipTorpedoes = finalSnapshot.torpedoes().stream()
                .filter(torpedo -> {
                    ShipSnapshot shooter = findShip(finalSnapshot, torpedo.shipId());
                    return shooter != null && !"scout-plane".equals(shooter.vehicleType());
                })
                .count();

        assertTrue(movingBotBoats >= 6);
        assertTrue(botBoatsThatMadeWay >= 6);
        assertTrue(shipTorpedoes > warmupTorpedoes);
    }

    @Test
    void explosionDemoPlacesShipsAndRespawnsInNavigableWater() {
        assertSetupPlacesShipsAndRespawnsInNavigableWater(new DefaultGameSetupFactory(new WorldMapService()).setup("explosion-demo"));
    }

    @Test
    void denseLandPlacesShipsAndRespawnsInNavigableWater() {
        assertSetupPlacesShipsAndRespawnsInNavigableWater(new DefaultGameSetupFactory(new WorldMapService()).setup("dense-land"));
    }

    @Test
    void escortDebugPlacesShipsAndRespawnsInNavigableWater() {
        assertSetupPlacesShipsAndRespawnsInNavigableWater(new DefaultGameSetupFactory(new WorldMapService()).setup("escort-debug"));
    }

    @Test
    void denseLandKeepsActiveShipsInNavigableWaterDuringSimulation() {
        GameSetup setup = new DefaultGameSetupFactory(new WorldMapService()).setup("dense-land");
        GameSession session = new GameSession(setup);

        for (int tick = 0; tick < 400; tick += 1) {
            session.update(0.05, radarService, navigationService, session.worldMap());
            assertActiveShipsNavigable("tick " + tick, session.snapshot(), session.worldMap());
        }
    }

    @Test
    void denseLandHasCalculatedBeachBandThatBlocksNavigationOnly() {
        WorldMap worldMap = new DefaultGameSetupFactory(new WorldMapService()).setup("dense-land").worldMap();
        List<String> navigationMismatches = new java.util.ArrayList<>();

        for (Landmass landmass : worldMap.landmasses()) {
            sampleBoundaryPoints(landmass).stream()
                    .forEach(point -> {
                        double distance = LandGeometry.shapeDistance(point, landmass);
                        boolean landWater = LandGeometry.isInLandWater(point, landmass);
                        boolean navigationBlocked = LandGeometry.isBlockedByLandmass(point, landmass);
                        boolean expectedNavigation = distance < LandGeometry.navigationBlockDistance(landmass) && !landWater;
                        if (navigationBlocked != expectedNavigation) {
                            navigationMismatches.add(landmass.name() + " has wrong navigation boundary at " + point
                                    + " distance=" + distance + " expected=" + expectedNavigation
                                    + " actual=" + navigationBlocked);
                        }
                    });
        }

        assertTrue(navigationMismatches.isEmpty(), String.join("\n", navigationMismatches.stream().limit(40).toList()));

        Landmass coastline = worldMap.landmasses().stream()
                .filter(landmass -> "coastline".equals(landmass.kind()))
                .findFirst()
                .orElseThrow();
        double beachBandDistance = LandGeometry.navigationBlockDistance(coastline) - 0.02;
        Vector2 beachBandPoint = new Vector2(coastline.x() + coastline.rx() * beachBandDistance, coastline.z());
        assertTrue(LandGeometry.isBlockedByLandmass(beachBandPoint, coastline));
    }

    @Test
    void denseLandVolcanoCenterIsNotNavigableWater() {
        WorldMap worldMap = new DefaultGameSetupFactory(new WorldMapService()).setup("dense-land").worldMap();

        assertTrue(navigationService.isShipBlocked(new Vector2(310, -201), 2.55, worldMap));
    }

    @Test
    void botUsesControlledPowerInsteadOfCrawlingWhenLandBlocksCourseAhead() {
        GameSession session = new GameSession(new GameSetup(
                "bot-blocked-course-power-test",
                new WorldMap(9051, List.of(testIsland("tight-channel-rock", 0, 18, 5, 5))),
                List.of(
                        new FleetSetup("red", List.of(
                                ship("red-1", "red", 0, 0, 0, "bot", 2, 0)
                        ))
                ),
                List.of(new Vector2(0, 0))
        ));

        session.update(0.05, radarService, navigationService, session.worldMap());

        ShipSnapshot bot = findShip(session.snapshot(), "red-1");
        assertEquals(ENGINE_ONE_THIRD, bot.engineOrder());
        assertTrue(bot.rudderDegrees() != 0);
    }

    @Test
    void botBacksOutInsteadOfStoppingWhenAlreadyBowBlockedByLand() {
        GameSession session = new GameSession(new GameSetup(
                "bot-bow-blocked-escape-test",
                new WorldMap(9067, List.of(testIsland("bow-rock", 0, 5.2, 2.5, 2.5))),
                List.of(
                        new FleetSetup("red", List.of(
                                ship("red-1", "red", 0, 0, 0, "bot", ENGINE_FULL, 0, 99)
                        ))
                ),
                List.of(new Vector2(0, 0))
        ));

        assertTrue(navigationService.isShipBlocked(new Vector2(0, 0), 0, session.worldMap()));

        session.update(0.05, radarService, navigationService, session.worldMap());

        ShipSnapshot bot = findShip(session.snapshot(), "red-1");
        assertEquals(ENGINE_FULL_ASTERN, bot.engineOrder());
    }

    @Test
    void botMovesForwardWhenOnlyAsternSideIsBlockedAtShore() {
        GameSession session = new GameSession(new GameSetup(
                "bot-parallel-shore-forward-escape-test",
                new WorldMap(9069, List.of(testIsland("astern-side-bank", 1.86, -1, 2.0, 2.0))),
                List.of(
                        new FleetSetup("red", List.of(
                                ship("red-1", "red", 0, 0, 0, "bot", ENGINE_FULL, 0, 99)
                        ))
                ),
                List.of(new Vector2(0, 0))
        ));

        assertTrue(navigationService.isShipBlocked(new Vector2(0, 0), 0, session.worldMap()));
        assertFalse(navigationService.isShipMovementBlocked(new Vector2(0, 0), 0, EngineOrders.speedFor(ENGINE_SLOW), session.worldMap()));
        assertTrue(navigationService.isShipMovementBlocked(new Vector2(0, 0), 0, EngineOrders.speedFor(ENGINE_FULL_ASTERN), session.worldMap()));

        session.update(0.05, radarService, navigationService, session.worldMap());

        ShipSnapshot bot = findShip(session.snapshot(), "red-1");
        assertEquals(ENGINE_SLOW, bot.engineOrder());
    }

    @Test
    void friendlyShipsDoNotSinkEachOtherByRamCollision() {
        GameSession session = new GameSession(new GameSetup(
                "friendly-ram-ignore-test",
                new WorldMap(9068, List.of()),
                List.of(new FleetSetup("red", List.of(
                        ship("red-1", "red", 0, -10, 0, "bot", ENGINE_FULL, 0, 99),
                        ship("red-2", "red", 0, 10, Math.PI, "bot", ENGINE_FULL, 0, 99)
                ))),
                List.of(new Vector2(0, -10), new Vector2(0, 10))
        ));

        for (int index = 0; index < 30; index += 1) {
            session.update(0.05, radarService, navigationService, session.worldMap());
        }

        GameSnapshot snapshot = session.snapshot();
        assertEquals("active", findShip(snapshot, "red-1").state());
        assertEquals("active", findShip(snapshot, "red-2").state());
        assertEquals(0, snapshot.destroyedShipsByTeam().get("red"));
        assertEquals(ENGINE_FULL_ASTERN, findShip(snapshot, "red-1").engineOrder());
        assertEquals(ENGINE_FULL_ASTERN, findShip(snapshot, "red-2").engineOrder());
    }

    @Test
    void botAvoidsFriendlyHumanShipAhead() {
        GameSession session = new GameSession(new GameSetup(
                "friendly-human-blocking-course-test",
                new WorldMap(9070, List.of()),
                List.of(
                        new FleetSetup("red", List.of(
                                ship("red-bot", "red", 0, -55, 0, "bot", ENGINE_FULL, 0, 99),
                                ship("red-human", "red", 0, 0, 0, "player-BP-test", ENGINE_STOP, 0, 99)
                        )),
                        new FleetSetup("blue", List.of(
                                ship("blue-target", "blue", 0, 260, Math.PI, "scenario", ENGINE_STOP, 0, 99)
                        ))
                ),
                List.of(new Vector2(0, -55), new Vector2(0, 0), new Vector2(0, 260))
        ));

        session.update(0.05, radarService, navigationService, session.worldMap());

        ShipSnapshot bot = findShip(session.snapshot(), "red-bot");
        assertEquals("active", bot.state());
        assertTrue(Math.abs(bot.rudderDegrees()) > 0);
        assertTrue(bot.engineOrder() <= ENGINE_HALF);
    }

    @Test
    void playerCanDamageHumanFleetMateByRamCollision() {
        GameSession session = new GameSession(new GameSetup(
                "player-fleet-mate-ram-damage-test",
                new WorldMap(9071, List.of()),
                List.of(new FleetSetup("red", List.of(
                        ship("red-1", "red", 0, 11, 0, "bot", ENGINE_STOP, 0, 99),
                        ship("red-2", "red", 0, -11, 0, "bot", ENGINE_STOP, 0, 99)
                ))),
                List.of(new Vector2(0, 11), new Vector2(0, -11))
        ));

        session.updatePlayerState(
                new PlayerStateUpdate("player-red-front", "red", 0, 11, 0, 5, 0, ENGINE_HALF, 0, 0, false),
                navigationService,
                session.worldMap()
        );
        session.updatePlayerState(
                new PlayerStateUpdate("player-red-rear", "red", 0, -11, 0, 10.3, 0, ENGINE_FULL, 0, 0, false),
                navigationService,
                session.worldMap()
        );
        session.update(0, radarService, navigationService, session.worldMap());

        GameSnapshot snapshot = session.snapshot();
        assertEquals(2, snapshot.destroyedShipsByTeam().get("red"));
    }

    @Test
    void slowRamCollisionDoesNotSinkShips() {
        GameSession session = new GameSession(new GameSetup(
                "slow-ram-no-damage-test",
                new WorldMap(9072, List.of()),
                List.of(
                        new FleetSetup("red", List.of(ship("red-1", "red", 0, 0, Math.PI / 2, "scenario", ENGINE_SLOW, 0, 99))),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 18, 0, 0, "scenario", ENGINE_STOP, 0, 99)))
                ),
                List.of(new Vector2(0, 0), new Vector2(18, 0))
        ));

        for (int index = 0; index < 50; index += 1) {
            session.update(0.05, radarService, navigationService, session.worldMap());
        }

        GameSnapshot snapshot = session.snapshot();
        assertEquals("active", findShip(snapshot, "red-1").state());
        assertEquals("active", findShip(snapshot, "blue-1").state());
        assertEquals(0, snapshot.destroyedShipsByTeam().get("red"));
        assertEquals(0, snapshot.destroyedShipsByTeam().get("blue"));
    }

    @Test
    void rearEndRamCollisionSinksBothShips() {
        String playerId = "player-BP-test";
        GameSession session = new GameSession(new GameSetup(
                "rear-end-ram-test",
                new WorldMap(9072, List.of()),
                List.of(
                        new FleetSetup("red", List.of(ship("red-1", "red", 0, -40, 0, playerId, ENGINE_FULL, 0, 99))),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 0, -8, 0, "scenario", ENGINE_STOP, 0, 99)))
                ),
                List.of(new Vector2(0, -40), new Vector2(0, -8))
        ));

        GameSnapshot snapshot = tickPlayerForwardUntilShipState(
                session, playerId, "red", "red-1", "sunk", new Vector2(0, -40), 0, 4
        );

        assertEquals("sunk", findShip(snapshot, "red-1").state());
        assertEquals("sunk", findShip(snapshot, "blue-1").state());
        assertEquals(1, snapshot.destroyedShipsByTeam().get("red"));
        assertEquals(1, snapshot.destroyedShipsByTeam().get("blue"));
    }

    @Test
    void rearEndRamCollisionUsesRelativeImpactSpeedForDamage() {
        GameSession session = playerRearEndRamSession("rear-end-relative-impact-damage-test");

        setPlayerShipState(session, "player-red", "red", 0, -11, 0, 10.3);
        setPlayerShipState(session, "player-blue", "blue", 0, 11, 0, 5.0);
        session.update(0, radarService, navigationService, session.worldMap());

        GameSnapshot snapshot = session.snapshot();
        assertEquals("sunk", findShip(snapshot, "red-1").state());
        assertEquals("sunk", findShip(snapshot, "blue-1").state());
    }

    @Test
    void rearEndRamCollisionBelowFiveKnotsRelativeImpactDoesNotSinkShips() {
        GameSession session = playerRearEndRamSession("rear-end-relative-impact-no-damage-test");

        setPlayerShipState(session, "player-red", "red", 0, -11, 0, 10.0);
        setPlayerShipState(session, "player-blue", "blue", 0, 11, 0, 5.2);
        session.update(0, radarService, navigationService, session.worldMap());

        GameSnapshot snapshot = session.snapshot();
        assertEquals("active", findShip(snapshot, "red-1").state());
        assertEquals("active", findShip(snapshot, "blue-1").state());
    }

    @Test
    void cleanHeadOnRamCollisionSinksBothShips() {
        GameSession session = new GameSession(new GameSetup(
                "clean-head-on-ram-test",
                new WorldMap(9073, List.of()),
                List.of(
                        new FleetSetup("red", List.of(ship("red-1", "red", 0, -40, 0, "scenario", ENGINE_FULL, 0, 99))),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 0, 40, Math.PI, "scenario", ENGINE_FULL, 0, 99)))
                ),
                List.of(new Vector2(0, -40), new Vector2(0, 40))
        ));

        GameSnapshot snapshot = tickUntilShipState(session, "red-1", "sunk", 6);

        assertEquals("sunk", findShip(snapshot, "red-1").state());
        assertEquals("sunk", findShip(snapshot, "blue-1").state());
        assertEquals(1, snapshot.destroyedShipsByTeam().get("red"));
        assertEquals(1, snapshot.destroyedShipsByTeam().get("blue"));
    }

    @Test
    void offsetHeadOnRamCollisionGlancesWithoutSinkingShips() {
        GameSession session = new GameSession(new GameSetup(
                "offset-head-on-ram-test",
                new WorldMap(9074, List.of()),
                List.of(
                        new FleetSetup("red", List.of(ship("red-1", "red", -4.2, -12, 0, "scenario", ENGINE_FULL, 0, 99))),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 4.2, 12, Math.PI, "scenario", ENGINE_FULL, 0, 99)))
                ),
                List.of(new Vector2(-4.2, -12), new Vector2(4.2, 12))
        ));

        GameSnapshot snapshot = tickUntilShipsTouch(session, 4);

        assertEquals("active", findShip(snapshot, "red-1").state());
        assertEquals("active", findShip(snapshot, "blue-1").state());
        assertEquals(0, snapshot.destroyedShipsByTeam().get("red"));
        assertEquals(0, snapshot.destroyedShipsByTeam().get("blue"));
    }

    @Test
    void asternMovementCanLeaveBowGroundingWhenSternIsClear() {
        WorldMap worldMap = new WorldMap(9003, List.of(testIsland("bow-rock", 0, 5.2, 2.5, 2.5)));
        Ship ship = new Ship("red-1", "red", new Vector2(0, 0), 0, "player-test");

        assertTrue(navigationService.isShipBlocked(ship.position(), ship.heading(), worldMap));
        assertFalse(navigationService.isShipMovementBlocked(ship.position(), ship.heading(), -1, worldMap));

        ship.applyCommand(0, 0);
        for (int i = 0; i < 40; i += 1) {
            ship.update(0.05, navigationService, worldMap);
        }

        assertTrue(ship.position().z() < -0.3);
    }

    @Test
    void asternMovementStaysBlockedWhenSternIsGrounded() {
        WorldMap worldMap = new WorldMap(9004, List.of(testIsland("stern-rock", 0, -1.05, 1.9, 1.9)));
        Ship ship = new Ship("red-1", "red", new Vector2(0, 0), 0, "player-test");

        assertTrue(navigationService.isShipBlocked(ship.position(), ship.heading(), worldMap));
        assertTrue(navigationService.isShipMovementBlocked(ship.position(), ship.heading(), -1, worldMap));
    }

    @Test
    void respawnSelectionUsesCandidatesAsRoundRobin() {
        List<Vector2> candidates = List.of(
                new Vector2(-200, 0),
                new Vector2(0, 0),
                new Vector2(200, 0)
        );
        GameSession session = new GameSession(new GameSetup(
                "respawn-round-robin-test",
                new WorldMap(9012, List.of()),
                List.of(),
                candidates
        ));
        Ship sunkShip = new Ship("red-1", "red", new Vector2(0, -500), 0, "bot");

        assertEquals(candidates.get(0), session.findRespawnPosition(sunkShip, navigationService, session.worldMap(), radarService));
        assertEquals(candidates.get(1), session.findRespawnPosition(sunkShip, navigationService, session.worldMap(), radarService));
        assertEquals(candidates.get(2), session.findRespawnPosition(sunkShip, navigationService, session.worldMap(), radarService));
        assertEquals(candidates.get(0), session.findRespawnPosition(sunkShip, navigationService, session.worldMap(), radarService));
    }

    @Test
    void respawnSelectionDoesNotRepeatLastCandidateWhenAllCandidatesAreNegative() {
        List<Vector2> candidates = List.of(
                new Vector2(-500, 0),
                new Vector2(0, 0),
                new Vector2(500, 0)
        );
        GameSession session = new GameSession(new GameSetup(
                "respawn-negative-round-robin-test",
                new WorldMap(9013, List.of()),
                List.of(new FleetSetup("red", List.of(
                        ship("red-a", "red", -500, 0, 0, 2, 0),
                        ship("red-b", "red", 0, 0, 0, 2, 0),
                        ship("red-c", "red", 500, 0, 0, 2, 0)
                ))),
                candidates
        ));
        Ship sunkShip = new Ship("red-1", "red", new Vector2(0, -500), 0, "bot");

        Vector2 first = session.findRespawnPosition(sunkShip, navigationService, session.worldMap(), radarService);
        Vector2 second = session.findRespawnPosition(sunkShip, navigationService, session.worldMap(), radarService);

        assertEquals(candidates.get(0), first);
        assertEquals(candidates.get(1), second);
    }

    @Test
    void simultaneousRespawnsPreferHumanProximityOverStackingShips() throws Exception {
        GameSession session = new GameSession(new GameSetup(
                "simultaneous-respawn-spacing-test",
                new WorldMap(9071, List.of()),
                List.of(
                        new FleetSetup("red", List.of(
                                ship("red-a", "red", -20, 0, 0, "bot", ENGINE_STOP, 0, 0),
                                ship("red-b", "red", 20, 0, 0, "bot", ENGINE_STOP, 0, 0)
                        )),
                        new FleetSetup("blue", List.of(
                                ship("blue-human", "blue", 5300, 0, Math.PI, "player-BPB", ENGINE_STOP, 0, 99)
                        ))
                ),
                List.of(new Vector2(0, 0), new Vector2(5000, 0))
        ));
        sinkShip(session, "red-a");
        sinkShip(session, "red-b");

        session.update(8.1, radarService, navigationService, session.worldMap());
        GameSnapshot snapshot = session.snapshot();

        assertEquals("active", findShip(snapshot, "red-a").state());
        assertEquals("active", findShip(snapshot, "red-b").state());
        assertTrue(distanceBetween(snapshot, "red-a", "red-b") >= 170);
        assertEquals(0, findShip(snapshot, "red-a").x(), 0.001);
        assertEquals(5000, findShip(snapshot, "red-b").x(), 0.001);
    }

    @Test
    void respawnWaitsWhenEveryCandidateIsOccupied() throws Exception {
        GameSession session = new GameSession(new GameSetup(
                "occupied-respawn-waits-test",
                new WorldMap(90711, List.of()),
                List.of(new FleetSetup("red", List.of(
                        ship("red-sunk", "red", 40, 0, 0, "bot", ENGINE_STOP, 0, 0),
                        ship("red-active", "red", 0, 0, 0, "bot", ENGINE_STOP, 0, 99)
                ))),
                List.of(new Vector2(0, 0))
        ));
        sinkShip(session, "red-sunk");

        session.update(8.1, radarService, navigationService, session.worldMap());
        GameSnapshot snapshot = session.snapshot();

        assertNull(findShip(snapshot, "red-sunk"));
        assertEquals("active", findShip(snapshot, "red-active").state());
    }

    @Test
    void botBoatRespawnsAsScoutPlaneWhenHumanTargetsNeedMorePlanes() throws Exception {
        GameSession session = new GameSession(new GameSetup(
                "dynamic-scout-plane-respawn-test",
                new WorldMap(9064, List.of()),
                List.of(
                        new FleetSetup("light", List.of(
                                ship("light-plane", "light", -300, 0, 0, "bot", ENGINE_FULL, 0, 99, "scout-plane"),
                                ship("light-boat", "light", 0, 0, 0, "bot", ENGINE_HALF, 0, 99, "torpedo-boat")
                        )),
                        new FleetSetup("dark", List.of(
                                ship("dark-human", "dark", 320, 0, Math.PI, "player-BPB", ENGINE_HALF, 0, 99)
                        ))
                ),
                List.of(new Vector2(0, 360))
        ));

        sinkShip(session, "light-boat");
        session.update(8.05, radarService, navigationService, session.worldMap());

        ShipSnapshot respawned = findShip(session.snapshot(), "light-boat");
        assertEquals("active", respawned.state());
        assertEquals("scout-plane", respawned.vehicleType());
        assertTrue(respawned.y() > 0);
    }

    @Test
    void botBoatRespawnsAsBoatWhenScoutPlaneTargetCountIsAlreadyMet() throws Exception {
        GameSession session = new GameSession(new GameSetup(
                "dynamic-scout-plane-keeps-boat-respawn-test",
                new WorldMap(9065, List.of()),
                List.of(
                        new FleetSetup("light", List.of(
                                ship("light-plane-a", "light", -300, 0, 0, "bot", ENGINE_FULL, 0, 99, "scout-plane"),
                                ship("light-plane-b", "light", -340, 0, 0, "bot", ENGINE_FULL, 0, 99, "scout-plane"),
                                ship("light-boat", "light", 0, 0, 0, "bot", ENGINE_HALF, 0, 99, "torpedo-boat")
                        )),
                        new FleetSetup("dark", List.of(
                                ship("dark-human", "dark", 320, 0, Math.PI, "player-BPB", ENGINE_HALF, 0, 99)
                        ))
                ),
                List.of(new Vector2(0, 360))
        ));

        sinkShip(session, "light-boat");
        session.update(8.05, radarService, navigationService, session.worldMap());

        ShipSnapshot respawned = findShip(session.snapshot(), "light-boat");
        assertEquals("active", respawned.state());
        assertEquals("torpedo-boat", respawned.vehicleType());
        assertEquals(0, respawned.y());
    }

    @Test
    void respawnFallbackPrefersCandidateFarthestFromHumanShips() {
        String playerId = "player-BP-test";
        List<Vector2> candidates = List.of(
                new Vector2(120, 0),
                new Vector2(420, 0),
                new Vector2(860, 0)
        );
        GameSession session = new GameSession(new GameSetup(
                "respawn-human-distance-fallback-test",
                new WorldMap(9016, List.of()),
                List.of(new FleetSetup("red", List.of(
                        ship("red-human", "red", 0, 0, 0, playerId, 2, 0)
                ))),
                candidates
        ));
        Ship sunkShip = new Ship("blue-1", "blue", new Vector2(0, -500), 0, "bot");

        assertEquals(candidates.get(2), session.findRespawnPosition(sunkShip, navigationService, session.worldMap(), radarService));
    }

    @Test
    void defaultSetupHasEnoughRespawnCandidates() {
        GameSetup setup = new DefaultGameSetupFactory(new WorldMapService()).setup("default");

        assertTrue(setup.respawnCandidates().size() >= 10);
    }

    private void assertSetupPlacesShipsAndRespawnsInNavigableWater(GameSetup setup) {
        List<String> blockedPositions = new java.util.ArrayList<>();
        setup.fleets().stream()
                .flatMap(fleet -> fleet.ships().stream())
                .filter(ship -> navigationService.isShipBlocked(ship.position(), ship.heading(), setup.worldMap()))
                .map(ship -> ship.id() + " starts blocked at " + ship.position()
                        + " nearest " + nearestNavigable(setup.worldMap(), ship.position(), ship.heading()))
                .forEach(blockedPositions::add);

        setup.respawnCandidates().stream()
                .filter(candidate -> navigationService.isShipBlocked(candidate, 0, setup.worldMap()))
                .map(candidate -> "Respawn candidate is blocked at " + candidate
                        + " nearest " + nearestNavigable(setup.worldMap(), candidate, 0))
                .forEach(blockedPositions::add);

        assertTrue(blockedPositions.isEmpty(), String.join("\n", blockedPositions));
    }

    private void assertActiveShipsNavigable(String label, GameSnapshot snapshot, WorldMap worldMap) {
        List<String> blockedPositions = new java.util.ArrayList<>();
        snapshot.ships().stream()
                .filter(ship -> "active".equals(ship.state()))
                .filter(ship -> !"scout-plane".equals(ship.vehicleType()))
                .filter(ship -> navigationService.isShipBlocked(new Vector2(ship.x(), ship.z()), ship.heading(), worldMap))
                .map(ship -> label + " " + ship.id() + " is blocked at "
                        + new Vector2(ship.x(), ship.z()) + " nearest "
                        + nearestNavigable(worldMap, new Vector2(ship.x(), ship.z()), ship.heading()))
                .forEach(blockedPositions::add);

        assertTrue(blockedPositions.isEmpty(), String.join("\n", blockedPositions));
    }

    private List<Vector2> sampleBoundaryPoints(Landmass landmass) {
        List<Vector2> points = new java.util.ArrayList<>();
        double minX = landmass.x() - landmass.rx() * 1.18;
        double maxX = landmass.x() + landmass.rx() * 1.18;
        double minZ = landmass.z() - landmass.rz() * 1.18;
        double maxZ = landmass.z() + landmass.rz() * 1.18;
        double step = Math.max(4, Math.min(18, Math.min(landmass.rx(), landmass.rz()) / 5));

        for (double x = minX; x <= maxX; x += step) {
            for (double z = minZ; z <= maxZ; z += step) {
                points.add(new Vector2(x, z));
            }
        }
        return points;
    }

    private Vector2 nearestNavigable(WorldMap worldMap, Vector2 origin, double heading) {
        Vector2 best = origin;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int radius = 20; radius <= 360; radius += 20) {
            for (int step = 0; step < 32; step += 1) {
                double angle = step * Math.PI * 2 / 32.0;
                Vector2 candidate = origin.add(new Vector2(Math.cos(angle) * radius, Math.sin(angle) * radius));
                if (!navigationService.isShipBlocked(candidate, heading, worldMap)) {
                    double distance = origin.distanceTo(candidate);
                    if (distance < bestDistance) {
                        best = candidate;
                        bestDistance = distance;
                    }
                }
            }
            if (bestDistance < Double.POSITIVE_INFINITY) {
                return best;
            }
        }
        return best;
    }

    private Landmass testIsland(String name, double x, double z, double rx, double rz) {
        return testIsland(name, x, z, rx, rz, 1);
    }

    private Landmass testIsland(String name, double x, double z, double rx, double rz, double heightScale) {
        return new Landmass(
                "island",
                name,
                x,
                z,
                rx,
                rz,
                rx,
                rz,
                rx * 1.2,
                rz * 1.2,
                null,
                heightScale,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of()
        );
    }

    private Landmass testCoastline(String name, double x, double z, double rx, double rz, double heightScale, Double peakBoost) {
        return new Landmass(
                "coastline",
                name,
                x,
                z,
                rx,
                rz,
                rx,
                rz,
                rx,
                rz,
                null,
                heightScale,
                peakBoost,
                0.2,
                null,
                List.of(),
                List.of(),
                List.of()
        );
    }

    private GameSnapshot tickUntilShipState(GameSession session, String shipId, String state, double maxSeconds) {
        GameSnapshot snapshot = session.snapshot();
        for (double elapsed = 0; elapsed < maxSeconds; elapsed += 0.05) {
            session.update(0.05, radarService, navigationService, session.worldMap());
            snapshot = session.snapshot();
            ShipSnapshot ship = findShip(snapshot, shipId);
            if (ship != null && state.equals(ship.state())) {
                return snapshot;
            }
        }
        return snapshot;
    }

    private GameSession cannonShipHitSession(String id) {
        GameSession session = new GameSession(new GameSetup(
                id,
                new WorldMap(9050, List.of()),
                List.of(
                        new FleetSetup("light", List.of(
                                ship("light-1", "light", 0, -40, 0, "bot", 5, 0, 0)
                        )),
                        new FleetSetup("dark", List.of(
                                ship("dark-1", "dark", 3, 0, 0, "bot", 2, 0, 0)
                        ))
                ),
                List.of(new Vector2(0, -40), new Vector2(3, 0))
        ));

        session.updatePlayerState(
                new PlayerStateUpdate("player-gunner", "light", 0, -40, 0, 4, 0, 5, 0, 0, false, "torpedo-boat"),
                navigationService,
                session.worldMap()
        );
        return session;
    }

    private GameSession planeHitSession(String id, double planeHeading, double planeTurnVelocity) {
        GameSession session = new GameSession(new GameSetup(
                id,
                new WorldMap(9051, List.of()),
                List.of(
                        new FleetSetup("light", List.of(
                                ship("light-1", "light", 0, 0, 0, "bot", 5, 0, 0)
                        )),
                        new FleetSetup("dark", List.of(
                                ship("dark-1", "dark", 0, 42, planeHeading, "bot", 7, 0, 0, "scout-plane")
                        ))
                ),
                List.of(new Vector2(0, 0), new Vector2(0, 42))
        ));

        session.updatePlayerState(
                new PlayerStateUpdate("player-gunner", "light", 0, 0, 0, 4, 0, 5, 0, 0, false, "torpedo-boat"),
                navigationService,
                session.worldMap()
        );
        session.updatePlayerState(
                new PlayerStateUpdate("player-plane", "dark", 0, 42, planeHeading, 14, planeTurnVelocity, 7, 0, 0, false, "scout-plane", 28),
                navigationService,
                session.worldMap()
        );
        return session;
    }

    private void assertProjectileHitsScoutPlaneAtHeading(double planeHeading) {
        GameSession session = planeHitSession("flak-plane-heading-" + Math.round(planeHeading * 100), planeHeading, 0);
        session.fireFlak(new FlakFireRequest(
                "player-gunner", "light", "light-1", 0, 28, 0, 0, 0, 95
        ));

        GameSnapshot snapshot = tickUntilShipState(session, "dark-1", "sunk", 1);
        assertEquals("sunk", findShip(snapshot, "dark-1").state());
        assertFalse(snapshot.flakHits().isEmpty());
        assertEquals("dark-1", snapshot.flakHits().get(0).targetShipId());
    }

    @SuppressWarnings("unchecked")
    private Optional<Object> projectileHit(GameSession session, String shipId, FlakProjectile projectile) throws Exception {
        Method allShips = GameSession.class.getDeclaredMethod("allShips");
        allShips.setAccessible(true);
        Ship ship = ((List<Ship>) allShips.invoke(session)).stream()
                .filter(candidate -> shipId.equals(candidate.id()))
                .findFirst()
                .orElseThrow();
        Method flakProjectileHitsTarget = GameSession.class.getDeclaredMethod(
                "flakProjectileHitsTarget",
                FlakProjectile.class,
                Ship.class
        );
        flakProjectileHitsTarget.setAccessible(true);
        return (Optional<Object>) flakProjectileHitsTarget.invoke(session, projectile, ship);
    }

    private String flakTargetHitReason(Object hit) throws Exception {
        Method reason = hit.getClass().getDeclaredMethod("reason");
        reason.setAccessible(true);
        return (String) reason.invoke(hit);
    }

    private boolean flakTargetHitSinks(Object hit) throws Exception {
        Method sinks = hit.getClass().getDeclaredMethod("sinks");
        sinks.setAccessible(true);
        return (boolean) sinks.invoke(hit);
    }

    private FlakProjectile cannonSideShotProjectile(double fromRight, double toRight, double fromY, double toY, double forward) {
        double deltaSeconds = 0.1;
        FlakProjectile projectile = new FlakProjectile(
                "cannon-side-shot-" + fromRight + "-" + toRight + "-" + fromY + "-" + forward,
                "light",
                "light-1",
                fromRight * TORPEDO_BOAT_MODEL_SCALE,
                torpedoBoatModelYToWorldY(fromY),
                forward * TORPEDO_BOAT_MODEL_SCALE,
                (toRight - fromRight) * TORPEDO_BOAT_MODEL_SCALE / deltaSeconds,
                (torpedoBoatModelYToWorldY(toY) - torpedoBoatModelYToWorldY(fromY)) / deltaSeconds + 9.0 * deltaSeconds,
                0,
                0
        );
        projectile.update(deltaSeconds);
        return projectile;
    }

    private double torpedoBoatModelYToWorldY(double modelY) {
        return (modelY + TORPEDO_BOAT_MODEL_WATERLINE_Y) * TORPEDO_BOAT_MODEL_SCALE;
    }

    @SuppressWarnings("unchecked")
    private Optional<String> scoutPlaneAttackHumanId(GameSession session, String planeId) throws Exception {
        Ship plane = shipEntity(session, planeId);
        Method scoutPlaneAttackHuman = GameSession.class.getDeclaredMethod("scoutPlaneAttackHuman", Ship.class);
        scoutPlaneAttackHuman.setAccessible(true);
        return ((Optional<Ship>) scoutPlaneAttackHuman.invoke(session, plane)).map(Ship::id);
    }

    private boolean shouldForceHumanScoutPlaneTarget(GameSession session, String planeId) throws Exception {
        Ship plane = shipEntity(session, planeId);
        Optional<Ship> attackHuman = scoutPlaneAttackHumanId(session, planeId)
                .map(humanId -> {
                    try {
                        return shipEntity(session, humanId);
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                });
        Method shouldForceHumanScoutPlaneTarget = GameSession.class.getDeclaredMethod(
                "shouldForceHumanScoutPlaneTarget",
                Ship.class,
                Optional.class
        );
        shouldForceHumanScoutPlaneTarget.setAccessible(true);
        return (boolean) shouldForceHumanScoutPlaneTarget.invoke(session, plane, attackHuman);
    }

    @SuppressWarnings("unchecked")
    private Ship shipEntity(GameSession session, String shipId) throws Exception {
        Method allShips = GameSession.class.getDeclaredMethod("allShips");
        allShips.setAccessible(true);
        return ((List<Ship>) allShips.invoke(session)).stream()
                .filter(candidate -> shipId.equals(candidate.id()))
                .findFirst()
                .orElseThrow();
    }

    private void dropBombsFromScoutPlane(GameSession session, Ship plane, double cooldownSeconds) throws Exception {
        Method dropBombsFromScoutPlane = GameSession.class.getDeclaredMethod("dropBombsFromScoutPlane", Ship.class, double.class);
        dropBombsFromScoutPlane.setAccessible(true);
        dropBombsFromScoutPlane.invoke(session, plane, cooldownSeconds);
    }

    private GameSnapshot tickUntilShipsTouch(GameSession session, double maxSeconds) {
        GameSnapshot snapshot = session.snapshot();
        for (double elapsed = 0; elapsed < maxSeconds; elapsed += 0.05) {
            session.update(0.05, radarService, navigationService, session.worldMap());
            snapshot = session.snapshot();
            ShipSnapshot red = findShip(snapshot, "red-1");
            ShipSnapshot blue = findShip(snapshot, "blue-1");
            if (red != null && blue != null && new Vector2(red.x(), red.z()).distanceTo(new Vector2(blue.x(), blue.z())) < 6 * TORPEDO_BOAT_MODEL_SCALE) {
                return snapshot;
            }
        }
        return snapshot;
    }

    private GameSnapshot tickUntilShipsUseEngineOrder(GameSession session, int engineOrder, double maxSeconds) {
        GameSnapshot snapshot = session.snapshot();
        for (double elapsed = 0; elapsed < maxSeconds; elapsed += 0.05) {
            session.update(0.05, radarService, navigationService, session.worldMap());
            snapshot = session.snapshot();
            ShipSnapshot red = findShip(snapshot, "red-1");
            ShipSnapshot blue = findShip(snapshot, "blue-1");
            if (red != null && blue != null
                    && red.engineOrder() == engineOrder
                    && blue.engineOrder() == engineOrder) {
                return snapshot;
            }
        }
        return snapshot;
    }

    private GameSnapshot tickPlayerForwardUntilShipState(GameSession session, String playerId, String teamId,
                                                         String shipId, String state, Vector2 startPosition,
                                                         double heading, double maxSeconds) {
        GameSnapshot snapshot = session.snapshot();
        double speed = 9.6;
        for (double elapsed = 0; elapsed < maxSeconds; elapsed += 0.05) {
            Vector2 position = startPosition.add(Vector2.fromHeading(heading).scale(speed * elapsed));
            session.updatePlayerState(
                    new PlayerStateUpdate(playerId, teamId, position.x(), position.z(), heading, speed, 0, 7, 0, elapsed, false),
                    navigationService,
                    session.worldMap()
            );
            session.update(0.05, radarService, navigationService, session.worldMap());
            snapshot = session.snapshot();
            ShipSnapshot ship = findShip(snapshot, shipId);
            if (ship != null && state.equals(ship.state())) {
                return snapshot;
            }
        }
        return snapshot;
    }

    private GameSession playerRearEndRamSession(String setupId) {
        return new GameSession(new GameSetup(
                setupId,
                new WorldMap(9075, List.of()),
                List.of(
                        new FleetSetup("red", List.of(ship("red-1", "red", 0, -11, 0, "player-red", ENGINE_STOP, 0, 99))),
                        new FleetSetup("blue", List.of(ship("blue-1", "blue", 0, 11, 0, "player-blue", ENGINE_STOP, 0, 99)))
                ),
                List.of(new Vector2(0, -11), new Vector2(0, 11))
        ));
    }

    private void setPlayerShipState(GameSession session, String playerId, String teamId,
                                    double x, double z, double heading, double speed) {
        session.updatePlayerState(
                new PlayerStateUpdate(playerId, teamId, x, z, heading, speed, 0, ENGINE_FULL, 0, 0, false),
                navigationService,
                session.worldMap()
        );
    }

    private ShipSnapshot findShip(GameSnapshot snapshot, String shipId) {
        return snapshot.ships().stream()
                .filter(ship -> shipId.equals(ship.id()))
                .findFirst()
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private void setBotScoutPlaneNonHumanAttackStreak(GameSession session, String attackingTeamId,
                                                      String humanShipId, int value) throws Exception {
        Field field = GameSession.class.getDeclaredField("botScoutPlaneNonHumanAttackStreakByHumanTarget");
        field.setAccessible(true);
        ((Map<String, Integer>) field.get(session)).put(attackingTeamId + ">" + humanShipId, value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> botScoutPlaneNonHumanAttackStreak(GameSession session) throws Exception {
        Field field = GameSession.class.getDeclaredField("botScoutPlaneNonHumanAttackStreakByHumanTarget");
        field.setAccessible(true);
        return (Map<String, Integer>) field.get(session);
    }

    private void recordBotScoutPlaneAttack(GameSession session, String planeId, String targetId) throws Exception {
        Method recordBotScoutPlaneAttack = GameSession.class.getDeclaredMethod("recordBotScoutPlaneAttack", Ship.class, Ship.class);
        recordBotScoutPlaneAttack.setAccessible(true);
        recordBotScoutPlaneAttack.invoke(session, shipEntity(session, planeId), shipEntity(session, targetId));
    }

    @SuppressWarnings("unchecked")
    private Optional<String> selectBotScoutPlaneTargetId(GameSession session, String planeId) throws Exception {
        Method allShips = GameSession.class.getDeclaredMethod("allShips");
        allShips.setAccessible(true);
        List<Ship> activeShips = ((List<Ship>) allShips.invoke(session)).stream()
                .filter(ship -> "active".equals(ship.state()))
                .toList();
        Method selectBotScoutPlaneTarget = GameSession.class.getDeclaredMethod(
                "selectBotScoutPlaneTarget",
                Ship.class,
                List.class,
                Map.class
        );
        selectBotScoutPlaneTarget.setAccessible(true);
        return ((Optional<Ship>) selectBotScoutPlaneTarget.invoke(session, shipEntity(session, planeId), activeShips, Map.of()))
                .map(Ship::id);
    }

    @SuppressWarnings("unchecked")
    private void sinkShip(GameSession session, String shipId) throws Exception {
        Method allShips = GameSession.class.getDeclaredMethod("allShips");
        allShips.setAccessible(true);
        Ship ship = ((List<Ship>) allShips.invoke(session)).stream()
                .filter(candidate -> shipId.equals(candidate.id()))
                .findFirst()
                .orElseThrow();
        Method sinkShip = GameSession.class.getDeclaredMethod("sinkShip", Ship.class, String.class);
        sinkShip.setAccessible(true);
        sinkShip.invoke(session, ship, null);
    }

    private double distanceBetween(GameSnapshot snapshot, String leftShipId, String rightShipId) {
        ShipSnapshot left = findShip(snapshot, leftShipId);
        ShipSnapshot right = findShip(snapshot, rightShipId);
        return new Vector2(left.x(), left.z()).distanceTo(new Vector2(right.x(), right.z()));
    }

    private ShipSetup ship(String id, String teamId, double x, double z, double heading, int engineOrder, int rudderDegrees) {
        return ship(id, teamId, x, z, heading, "scenario", engineOrder, rudderDegrees);
    }

    private ShipSetup ship(String id, String teamId, double x, double z, double heading, String controlledBy,
                           int engineOrder, int rudderDegrees) {
        return ship(id, teamId, x, z, heading, controlledBy, engineOrder, rudderDegrees, 99);
    }

    private ShipSetup ship(String id, String teamId, double x, double z, double heading, String controlledBy,
                           int engineOrder, int rudderDegrees, double nextFireDelaySeconds) {
        return ship(id, teamId, x, z, heading, controlledBy, engineOrder, rudderDegrees, nextFireDelaySeconds, "torpedo-boat");
    }

    private ShipSetup ship(String id, String teamId, double x, double z, double heading, String controlledBy,
                           int engineOrder, int rudderDegrees, double nextFireDelaySeconds, String vehicleType) {
        return new ShipSetup(
                id,
                teamId,
                new Vector2(x, z),
                MathSupport.normalizeAngle(heading),
                controlledBy,
                engineOrder,
                rudderDegrees,
                nextFireDelaySeconds,
                vehicleType,
                "scout-plane".equals(vehicleType) ? SCOUT_PLANE_START_Y : 0
        );
    }

    private long countScoutPlanes(GameSetup setup) {
        return setup.fleets().stream()
                .flatMap(fleet -> fleet.ships().stream())
                .filter(ship -> "scout-plane".equals(ship.vehicleType()))
                .count();
    }
}
