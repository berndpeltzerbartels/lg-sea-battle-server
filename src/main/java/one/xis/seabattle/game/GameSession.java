package one.xis.seabattle.game;

import java.util.*;

public final class GameSession {

    private static final double TORPEDO_BOAT_MODEL_SCALE = SeaBattleGameConfig.TORPEDO_BOAT_SCALE;
    private static final double SCOUT_PLANE_MODEL_SCALE = SeaBattleGameConfig.SCOUT_PLANE_SCALE;
    private static final double TORPEDO_BROAD_PHASE_RADIUS = 6.2;
    private static final double TORPEDO_HULL_MARGIN = 0.28;
    private static final double TORPEDO_SWEEP_STEP = 1.15;
    private static final double BOMB_HIT_RADIUS = 6.6;
    private static final double BOMB_HULL_MARGIN = 1.35;
    private static final int BOMBS_PER_DROP = 12;
    private static final double BOMB_RELEASE_INTERVAL_SECONDS = 0.12;
    private static final double BOT_SCOUT_PLANE_BOMB_COOLDOWN_SECONDS = 4.5;
    private static final double BOT_SCOUT_PLANE_TORPEDO_COOLDOWN_SECONDS = 12.0;
    private static final double BOT_SCOUT_PLANE_ATTACK_RANGE = 620.0;
    private static final double BOT_SCOUT_PLANE_BOMB_MIN_RANGE = 35.0;
    private static final double BOT_SCOUT_PLANE_BOMB_RANGE = 245.0;
    private static final double BOT_SCOUT_PLANE_BOMB_FORWARD_TOLERANCE = 24.0;
    private static final double BOT_SCOUT_PLANE_BOMB_LATERAL_TOLERANCE = 18.0;
    private static final double BOT_SCOUT_PLANE_TORPEDO_MIN_RANGE = 95.0;
    private static final double BOT_SCOUT_PLANE_TORPEDO_RELEASE_RANGE = 300.0;
    private static final double BOT_SCOUT_PLANE_TORPEDO_APPROACH_RANGE = 560.0;
    private static final double BOT_SCOUT_PLANE_ATTACK_ARC = Math.toRadians(32);
    private static final double BOT_SCOUT_PLANE_TORPEDO_ARC = Math.toRadians(14);
    private static final double BOT_SCOUT_PLANE_FLY_THROUGH_DISTANCE = 230.0;
    private static final double BOT_SCOUT_PLANE_FLY_THROUGH_COMPLETE_DISTANCE = 30.0;
    private static final double BOT_SCOUT_PLANE_FLY_THROUGH_SECONDS = 12.0;
    private static final double SCOUT_PLANE_ALTITUDE_SCALE = SeaBattleGameConfig.SCOUT_PLANE_ALTITUDE_SCALE;
    private static final double BOT_SCOUT_PLANE_CRUISE_Y = 220.0;
    private static final double BOT_SCOUT_PLANE_TORPEDO_ATTACK_Y = 70.0;
    private static final double BOT_SCOUT_PLANE_BOMB_ATTACK_Y = 110.0;
    private static final double BOT_SCOUT_PLANE_TORPEDO_RELEASE_MAX_Y = 118.25;
    private static final double BOT_SCOUT_PLANE_TERRAIN_CLEARANCE = 8.0 * SCOUT_PLANE_ALTITUDE_SCALE;
    private static final double BOT_SCOUT_PLANE_TORPEDO_LEAD_SECONDS = 2.2;
    private static final double BOT_SCOUT_PLANE_AIR_TORPEDO_RELEASE_OFFSET = 10.0;
    private static final double BOT_SCOUT_PLANE_AIR_TORPEDO_RELEASE_Y_OFFSET = 1.6 * SCOUT_PLANE_MODEL_SCALE;
    private static final double SHIP_TORPEDO_SPEED_SCALE = Math.sqrt(TORPEDO_BOAT_MODEL_SCALE);
    private static final double TORPEDO_SPEED_ADJUSTMENT = 0.8;
    private static final double SHIP_TORPEDO_BASE_SPEED = 24.0 * SHIP_TORPEDO_SPEED_SCALE * TORPEDO_SPEED_ADJUSTMENT;
    private static final double SHIP_TORPEDO_SPEED_GAIN = 0.35 * SHIP_TORPEDO_SPEED_SCALE * TORPEDO_SPEED_ADJUSTMENT;
    private static final double AIR_TORPEDO_SPEED_FACTOR = 0.75;
    private static final double BOT_SCOUT_PLANE_AIR_TORPEDO_BASE_SPEED = SHIP_TORPEDO_BASE_SPEED * AIR_TORPEDO_SPEED_FACTOR;
    private static final double BOT_SCOUT_PLANE_AIR_TORPEDO_SPEED_GAIN = SHIP_TORPEDO_SPEED_GAIN * AIR_TORPEDO_SPEED_FACTOR;
    private static final double BOT_SCOUT_PLANE_POST_TORPEDO_BOMB_DELAY_SECONDS = 1.1;
    private static final double PLAYER_SCOUT_PLANE_TORPEDO_COOLDOWN_SECONDS = 1.5;
    private static final int BOT_SCOUT_PLANE_FORCE_HUMAN_SINGLE_TARGET_STREAK = 5;
    private static final int BOT_SCOUT_PLANE_FORCE_HUMAN_TWO_TARGET_STREAK = 6;
    private static final int BOT_SCOUT_PLANE_FORCE_HUMAN_MANY_TARGET_STREAK = 7;
    private static final int BOT_SCOUT_PLANE_DESIRED_WITHOUT_HUMAN_TARGETS = 1;
    private static final int BOT_SCOUT_PLANE_DESIRED_WITH_ONE_OR_TWO_HUMAN_TARGETS = 2;
    private static final int BOT_SCOUT_PLANE_DESIRED_WITH_MANY_HUMAN_TARGETS = 3;
    private static final String VEHICLE_TORPEDO_BOAT = "torpedo-boat";
    private static final String VEHICLE_SCOUT_PLANE = "scout-plane";
    private static final double BOT_SCOUT_PLANE_TARGET_RESERVATION_PENALTY = 280.0;
    private static final double BOT_SCOUT_PLANE_TARGET_TIE_BREAKER = 16.0;
    private static final double BOMB_DROP_FORWARD_OFFSET = 0.6;
    private static final double BOMB_DROP_VERTICAL_OFFSET = 0.65;
    private static final double BOMB_PATTERN_LATERAL_SPACING = 0.18;
    private static final double BOMB_PATTERN_HEADING_JITTER = 0.008;
    private static final double BOMB_PATTERN_SPEED_JITTER = 1.1;
    private static final double BOMB_DROP_COOLDOWN_SECONDS = 2.8;
    private static final double SCOUT_PLANE_MIN_BOMB_ALTITUDE = 3 * SCOUT_PLANE_ALTITUDE_SCALE;
    private static final double SCOUT_PLANE_MAX_BOMB_ALTITUDE = 200 * SCOUT_PLANE_ALTITUDE_SCALE;
    private static final double SCOUT_PLANE_MAX_BOMB_HORIZONTAL_SPEED = 42;
    private static final double SCOUT_PLANE_MAX_BOMB_INITIAL_UP_SPEED = 20;
    private static final double SCOUT_PLANE_MAX_BOMB_INITIAL_DOWN_SPEED = 34;
    private static final double BOT_SCOUT_PLANE_STABLE_ATTACK_TURN_RATE = 0.035;
    private static final double FLAK_FIRE_COOLDOWN_SECONDS = 0.059;
    private static final double CANNON_FIRE_COOLDOWN_SECONDS = 1.0;
    private static final double FLAK_HIT_VISIBILITY_SECONDS = 2.4;
    private static final double AIRBORNE_TORPEDO_SWEEP_STEP = 1.0;
    private static final double FLAK_SWEEP_STEP = 1.5;
    private static final double CANNON_SWEEP_STEP = 0.45;
    private static final double CANNON_HULL_MARGIN = 0.42;
    private static final double CANNON_HULL_MIN_Y = -0.08;
    private static final double CANNON_HULL_DECK_MARGIN = 0.14;
    private static final double SCOUT_PLANE_FUSELAGE_HALF_WIDTH = 0.55;
    private static final double SCOUT_PLANE_HALF_LENGTH = 3.5;
    private static final double SCOUT_PLANE_WING_HALF_WIDTH = 4.15;
    private static final double SCOUT_PLANE_WING_FORWARD_MIN = -0.45;
    private static final double SCOUT_PLANE_WING_FORWARD_MAX = 0.95;
    private static final double SCOUT_PLANE_TAIL_HALF_WIDTH = 1.45;
    private static final double SCOUT_PLANE_TAIL_FORWARD_MIN = -2.85;
    private static final double SCOUT_PLANE_TAIL_FORWARD_MAX = -2.1;
    private static final double SCOUT_PLANE_VERTICAL_HALF_HEIGHT = 1.15;
    private static final double SCOUT_PLANE_HIT_MARGIN = 1.1;
    private static final double SCOUT_PLANE_VISUAL_BANK_PER_TURN_RATE = 2.35;
    private static final double SCOUT_PLANE_MAX_VISUAL_BANK = 0.72;
    private static final double FLAK_SHIP_HIT_MARGIN = 0.18;
    private static final double TORPEDO_BOAT_MODEL_WATERLINE_Y = -0.2;
    private static final double TORPEDO_BOAT_STERN_Z = -4.2;
    private static final double TORPEDO_BOAT_BOW_Z = 3.68;
    private static final double BOT_SHIP_TACTICAL_SCALE = TORPEDO_BOAT_MODEL_SCALE;
    private static final double RAM_HIT_RADIUS = 4.8;
    private static final double RAM_BOW_OFFSET = TORPEDO_BOAT_BOW_Z;
    private static final double RAM_STERN_LENGTH = TORPEDO_BOAT_STERN_Z;
    private static final double RAM_BOW_LENGTH = TORPEDO_BOAT_BOW_Z;
    private static final double RAM_SIDE_FORWARD_MIN = -2.85;
    private static final double RAM_SIDE_FORWARD_MAX = 2.95;
    private static final double RAM_SIDE_MARGIN = 0.42;
    private static final double RAM_SIDE_ANGLE_TOLERANCE = Math.toRadians(45);
    private static final double RAM_GLANCING_COLLISION_ANGLE = Math.toRadians(30);
    private static final double RAM_GLANCING_HEADING_IMPULSE = Math.toRadians(9);
    private static final double BOT_FIRE_ARC = 0.16;
    private static final double BOT_CLOSE_FIRE_ARC = 1.15;
    private static final double BOT_FIRE_MIN_RANGE = 65 * BOT_SHIP_TACTICAL_SCALE;
    private static final double BOT_FIRE_MAX_RANGE = 230 * BOT_SHIP_TACTICAL_SCALE;
    private static final double BOT_CLOSE_FIRE_RANGE = 145 * BOT_SHIP_TACTICAL_SCALE;
    private static final double BOT_CLOSE_MANEUVER_RANGE = 130;
    private static final double BOT_APPROACH_SLOW_RANGE = 230;
    private static final double BOT_AIM_ERROR = 0.055;
    private static final double BOT_RAM_RANGE = 34 * BOT_SHIP_TACTICAL_SCALE;
    private static final double BOT_TORPEDO_EVADE_RANGE = 115 * BOT_SHIP_TACTICAL_SCALE;
    private static final double BOT_TORPEDO_LOOKOUT_FORWARD_OFFSET = 4.5 * TORPEDO_BOAT_MODEL_SCALE;
    private static final double BOT_TORPEDO_LOOKOUT_ARC = 0.38;
    private static final double BOT_TORPEDO_INCOMING_ARC = 0.34;
    private static final double BOT_TORPEDO_THREAT_CORRIDOR = 8.0 * TORPEDO_BOAT_MODEL_SCALE;
    private static final double BOT_RADAR_INTERCEPT_RANGE = 360;
    private static final double BOT_HUMAN_TARGET_PRIORITY_CLEARANCE = 320;
    private static final double BOT_RETURN_TO_LAND_DISTANCE = 720;
    private static final double BOT_PATROL_LAND_DISTANCE = 470;
    private static final double BOT_ESCORT_JOIN_RANGE = 680;
    private static final double BOT_ESCORT_MIN_DISTANCE = 95;
    private static final double BOT_ESCORT_TARGET_DISTANCE = 150;
    private static final double BOT_FRIENDLY_HUMAN_COMBAT_RADIUS = 420;
    private static final double BOT_FRIENDLY_HUMAN_TARGET_DRIFT_WEIGHT = 0.8;
    private static final double BOT_GLANCING_RAM_BACKOFF_SECONDS = 2.85;
    private static final boolean SCOUT_PLANE_EXPERIMENT_PEACEFUL_BOTS = false;
    private static final double RESPAWN_DELAY_SECONDS = 8;
    private static final double RESPAWN_HUMAN_RADAR_MARGIN = 120;
    private static final double RESPAWN_MIN_SHIP_DISTANCE = 170;
    private static final double TORPEDO_IMPACT_VISIBILITY_SECONDS = 3.0;
    private static final int ENGINE_FULL_ASTERN = 0;
    private static final int ENGINE_ASTERN = 1;
    private static final int ENGINE_STOP = 2;
    private static final int ENGINE_SLOW = 3;
    private static final int ENGINE_ONE_THIRD = 4;
    private static final int ENGINE_HALF = 5;
    private static final int ENGINE_TWO_THIRDS = 6;
    private static final int ENGINE_FULL = 7;
    private static final int ENGINE_FLANK = 8;
    private static final int SCORE_ENEMY_SUNK = 1;
    private static final int SCORE_FRIENDLY_SUNK = -2;
    private static final int SCORE_PLAYER_SUNK = -3;

    private final String id;
    private final WorldMap worldMap;
    private final WorldMap scoutPlaneObstacleMap;
    private final Map<String, Fleet> fleets;
    private final List<Vector2> respawnCandidates;
    private final Map<String, Integer> destroyedShipsByTeam = new LinkedHashMap<>();
    private final Map<String, Integer> killsByPlayer = new LinkedHashMap<>();
    private final Map<String, Double> nextFlakFireTimeByShipId = new LinkedHashMap<>();
    private final Map<String, Double> nextCannonFireTimeByShipId = new LinkedHashMap<>();
    private final List<Torpedo> torpedoes = new ArrayList<>();
    private final List<TorpedoImpactSnapshot> torpedoImpacts = new ArrayList<>();
    private final List<Bomb> bombs = new ArrayList<>();
    private final List<PendingBombRelease> pendingBombReleases = new ArrayList<>();
    private final List<BombImpactSnapshot> bombImpacts = new ArrayList<>();
    private final List<FlakProjectile> flakProjectiles = new ArrayList<>();
    private final List<ProjectileHitSnapshot> projectileHits = new ArrayList<>();
    private final List<FlakImpactSnapshot> flakImpacts = new ArrayList<>();
    private final List<RamHitSnapshot> ramHits = new ArrayList<>();
    private final Map<String, Integer> botScoutPlaneNonHumanAttackStreakByHumanTarget = new LinkedHashMap<>();
    private final Map<String, Vector2> botScoutPlaneFlyThroughTargets = new LinkedHashMap<>();
    private final Map<String, Double> botScoutPlaneFlyThroughUntil = new LinkedHashMap<>();
    private int nextTorpedoId = 1;
    private int nextBombId = 1;
    private int nextFlakProjectileId = 1;
    private int nextRespawnCandidateIndex;
    private int lastRespawnCandidateIndex = -1;
    private double nowSeconds;
    private double nextBotScoutPlaneTorpedoTime;
    private String state = "running";

    GameSession(GameSetup setup) {
        this.id = setup.id();
        this.worldMap = setup.worldMap();
        this.scoutPlaneObstacleMap = LandGeometry.obstacleMapForMinimumTerrainHeight(
                worldMap,
                Math.min(BOT_SCOUT_PLANE_TORPEDO_ATTACK_Y, BOT_SCOUT_PLANE_BOMB_ATTACK_Y) - BOT_SCOUT_PLANE_TERRAIN_CLEARANCE
        );
        this.fleets = createFleets(setup.fleets());
        this.respawnCandidates = List.copyOf(setup.respawnCandidates());
        this.fleets.keySet().forEach(teamId -> destroyedShipsByTeam.put(teamId, 0));
    }

    WorldMap worldMap() {
        return worldMap;
    }

    List<Vector2> respawnCandidates() {
        return respawnCandidates;
    }

    boolean hasHumanControlledShip(Set<String> activePlayerIds) {
        if (activePlayerIds == null || activePlayerIds.isEmpty()) {
            return false;
        }
        return allShips().stream()
                .anyMatch(ship -> "active".equals(ship.state())
                        && ship.controlledBy() != null
                        && activePlayerIds.contains(ship.controlledBy()));
    }

    public synchronized GameSnapshot snapshot() {
        return new GameSnapshot(
                "state",
                id,
                state,
                MathSupport.round(nowSeconds),
                allShips().stream()
                        .filter(ship -> ship.isVisibleInSnapshot(nowSeconds))
                        .map(Ship::snapshot)
                        .toList(),
                torpedoes.stream()
                        .filter(torpedo -> "running".equals(torpedo.state()) || "airborne".equals(torpedo.state()))
                        .map(Torpedo::snapshot)
                        .toList(),
                torpedoImpacts.stream()
                        .filter(impact -> nowSeconds - impact.t() <= TORPEDO_IMPACT_VISIBILITY_SECONDS)
                        .toList(),
                bombs.stream()
                        .filter(bomb -> "falling".equals(bomb.state()))
                        .map(Bomb::snapshot)
                        .toList(),
                bombImpacts.stream()
                        .filter(impact -> nowSeconds - impact.t() <= TORPEDO_IMPACT_VISIBILITY_SECONDS)
                        .toList(),
                flakProjectiles.stream()
                        .filter(projectile -> "flying".equals(projectile.state()))
                        .map(FlakProjectile::snapshot)
                        .toList(),
                projectileHits.stream()
                        .filter(hit -> nowSeconds - hit.t() <= FLAK_HIT_VISIBILITY_SECONDS)
                        .toList(),
                flakImpacts.stream()
                        .filter(impact -> nowSeconds - impact.t() <= FLAK_HIT_VISIBILITY_SECONDS)
                        .toList(),
                ramHits.stream()
                        .filter(hit -> nowSeconds - hit.t() <= TORPEDO_IMPACT_VISIBILITY_SECONDS)
                        .toList(),
                Map.copyOf(destroyedShipsByTeam),
                Map.copyOf(killsByPlayer)
        );
    }

    public synchronized GameSnapshot updatePlayerState(PlayerStateUpdate update, NavigationService navigationService, WorldMap worldMap) {
        applyPlayerState(update, navigationService, worldMap);
        return snapshot();
    }

    public synchronized void applyPlayerState(PlayerStateUpdate update, NavigationService navigationService, WorldMap worldMap) {
        Fleet fleet = fleets.get(update.teamId());
        if (fleet == null) {
            return;
        }

        Optional<Ship> assignedShip = fleet.assignedShip(update.playerId());
        Ship ship = assignedShip
                .or(() -> fleet.assignNextShipToPlayer(update.playerId()))
                .orElseGet(() -> fleet.assignAdditionalShipToPlayer(
                        update.playerId(),
                        new Vector2(update.x(), update.z()),
                        update.heading(),
                        update.vehicleType(),
                        update.y()
                ));
        ship.applyPlayerState(update, navigationService, worldMap);
    }

    public synchronized GameSnapshot fireTorpedo(FireTorpedoRequest request) {
        applyFireTorpedo(request);
        return snapshot();
    }

    public synchronized void applyFireTorpedo(FireTorpedoRequest request) {
        Fleet fleet = fleets.get(request.teamId());
        if (fleet == null) {
            return;
        }

        Ship ship = fleet.assignedShip(request.playerId())
                .or(() -> fleet.assignNextShipToPlayer(request.playerId()))
                .orElseGet(() -> fleet.assignAdditionalShipToPlayer(
                        request.playerId(),
                        new Vector2(request.x(), request.z()),
                        request.heading(),
                        request.vehicleType(),
                        request.y()
                ));
        if ("scout-plane".equals(request.vehicleType())) {
            if (!ship.isScoutPlane()) {
                return;
            }
            if (request.includesPlayerState()) {
                ship.applyScoutPlaneWeaponState(
                        new Vector2(request.x(), request.z()),
                        request.y(),
                        request.heading(),
                        request.speed(),
                        request.verticalSpeed()
                );
            }
            firePlayerAirTorpedo(ship, PLAYER_SCOUT_PLANE_TORPEDO_COOLDOWN_SECONDS, request.heading());
            return;
        }
        if (ship.isScoutPlane()) {
            return;
        }
        fireTorpedo(ship, 2.4, 0, request.tubeSide());
    }

    public synchronized GameSnapshot dropBomb(BombDropRequest request) {
        applyDropBomb(request);
        return snapshot();
    }

    public synchronized void applyDropBomb(BombDropRequest request) {
        Fleet fleet = fleets.get(request.teamId());
        if (fleet == null) {
            return;
        }

        Ship ship = fleet.assignedShip(request.playerId())
                .or(() -> fleet.assignNextShipToPlayer(request.playerId()))
                .orElseThrow(() -> new IllegalStateException("No active ship available for team: " + request.teamId()));
        if (!ship.isScoutPlane() && !"scout-plane".equals(request.vehicleType())) {
            return;
        }
        if (!ship.canDropBomb(nowSeconds)) {
            return;
        }

        ship.applyScoutPlaneWeaponState(
                new Vector2(request.x(), request.z()),
                request.y(),
                request.heading(),
                request.speed(),
                request.verticalSpeed()
        );
        dropBombsFromScoutPlane(ship, BOMB_DROP_COOLDOWN_SECONDS);
        releasePendingBombs();
    }

    private void dropBombsFromScoutPlane(Ship ship, double cooldownSeconds) {
        ship.markFired(nowSeconds, cooldownSeconds);
        for (int index = 0; index < BOMBS_PER_DROP; index += 1) {
            pendingBombReleases.add(new PendingBombRelease(
                    ship.id(),
                    nowSeconds + index * BOMB_RELEASE_INTERVAL_SECONDS,
                    index
            ));
        }
    }

    public synchronized GameSnapshot fireFlak(FlakFireRequest request) {
        applyFireFlak(request);
        return snapshot();
    }

    public synchronized GameSnapshot fireCannon(FlakFireRequest request) {
        applyFireCannon(request);
        return snapshot();
    }

    public synchronized GameSnapshot reportClientPlaneHit(ClientPlaneHitRequest request) {
        applyClientPlaneHit(request);
        return snapshot();
    }

    public synchronized void applyFireFlak(FlakFireRequest request) {
        Fleet fleet = fleets.get(request.teamId());
        if (fleet == null) {
            return;
        }

        Ship ship = fleet.assignedShip(request.playerId())
                .or(() -> fleet.assignNextShipToPlayer(request.playerId()))
                .orElse(null);
        if (ship == null) {
            return;
        }
        if (ship.isScoutPlane() || !ship.id().equals(request.shipId()) || !canFireFlak(ship)) {
            return;
        }
        if (!hasShotVelocity(request)) {
            return;
        }

        ship.flakAim(request.weaponYaw(), request.weaponPitch());
        nextFlakFireTimeByShipId.put(ship.id(), nowSeconds + FLAK_FIRE_COOLDOWN_SECONDS);
        flakProjectiles.add(new FlakProjectile(
                "flak-" + nextFlakProjectileId++,
                ship.teamId(),
                ship.id(),
                request.x(),
                Math.max(0, request.y()),
                request.z(),
                request.vx(),
                request.vy(),
                request.vz(),
                nowSeconds
        ));
    }

    public synchronized void applyFireCannon(FlakFireRequest request) {
        Fleet fleet = fleets.get(request.teamId());
        if (fleet == null) {
            return;
        }

        Ship ship = fleet.assignedShip(request.playerId())
                .or(() -> fleet.assignNextShipToPlayer(request.playerId()))
                .orElse(null);
        if (ship == null) {
            return;
        }
        if (ship.isScoutPlane() || !ship.id().equals(request.shipId()) || !canFireCannon(ship)) {
            return;
        }
        if (!hasShotVelocity(request)) {
            return;
        }

        ship.cannonAim(request.weaponYaw(), request.weaponPitch());
        nextCannonFireTimeByShipId.put(ship.id(), nowSeconds + CANNON_FIRE_COOLDOWN_SECONDS);
        flakProjectiles.add(new FlakProjectile(
                "cannon-" + nextFlakProjectileId++,
                ship.teamId(),
                ship.id(),
                request.x(),
                Math.max(0, request.y()),
                request.z(),
                request.vx(),
                request.vy(),
                request.vz(),
                nowSeconds
        ));
    }

    public synchronized void applyClientPlaneHit(ClientPlaneHitRequest request) {
        Fleet fleet = fleets.get(request.teamId());
        if (fleet == null) {
            return;
        }

        Ship shooter = fleet.assignedShip(request.playerId())
                .orElseThrow(() -> new IllegalStateException("No active ship available for team: " + request.teamId()));
        if (shooter.isScoutPlane() || !shooter.id().equals(request.shipId())) {
            return;
        }

        Optional<Ship> target = allShips().stream()
                .filter(ship -> ship.id().equals(request.targetShipId()))
                .findFirst();
        if (target.isEmpty() || !target.get().isScoutPlane() || !"active".equals(target.get().state())) {
            return;
        }

        if (sinkShip(target.get(), shooterController(shooter.id()))) {
            recordClientPlaneHit(request, shooter, target.get());
        }
    }

    private boolean canFireFlak(Ship ship) {
        return nowSeconds >= nextFlakFireTimeByShipId.getOrDefault(ship.id(), 0.0);
    }

    private boolean canFireCannon(Ship ship) {
        return nowSeconds >= nextCannonFireTimeByShipId.getOrDefault(ship.id(), 0.0);
    }

    private boolean hasShotVelocity(FlakFireRequest request) {
        double squaredLength = request.vx() * request.vx() + request.vy() * request.vy() + request.vz() * request.vz();
        return squaredLength > 0.000001;
    }

    public synchronized void releasePlayer(String playerId) {
        fleets.values().forEach(fleet -> fleet.releasePlayer(playerId));
    }

    public synchronized void update(double deltaSeconds, RadarService radarService, NavigationService navigationService, WorldMap worldMap) {
        if (!"running".equals(state)) {
            return;
        }
        nowSeconds += deltaSeconds;
        commandBots(radarService, navigationService, worldMap);
        allShips().stream()
                .filter(Ship::isServerSimulated)
                .forEach(ship -> ship.update(deltaSeconds, navigationService, worldMap));
        updateTorpedoes(deltaSeconds, navigationService, worldMap);
        Set<String> releasedBombIds = releasePendingBombs();
        updateBombs(deltaSeconds, releasedBombIds);
        updateFlakProjectiles(deltaSeconds);
        updateFlakHits();
        updateFlakTerrainImpacts(worldMap);
        updateRamCollisions();
        respawnSunkShips(navigationService, worldMap, radarService);
        torpedoes.removeIf(torpedo -> !torpedoVisibleInWorld(torpedo));
        torpedoImpacts.removeIf(impact -> nowSeconds - impact.t() > TORPEDO_IMPACT_VISIBILITY_SECONDS);
        bombs.removeIf(bomb -> !"falling".equals(bomb.state()));
        bombImpacts.removeIf(impact -> nowSeconds - impact.t() > TORPEDO_IMPACT_VISIBILITY_SECONDS);
        flakProjectiles.removeIf(projectile -> !"flying".equals(projectile.state()));
        projectileHits.removeIf(hit -> nowSeconds - hit.t() > FLAK_HIT_VISIBILITY_SECONDS);
        flakImpacts.removeIf(impact -> nowSeconds - impact.t() > FLAK_HIT_VISIBILITY_SECONDS);
        ramHits.removeIf(hit -> nowSeconds - hit.t() > TORPEDO_IMPACT_VISIBILITY_SECONDS);
        checkGameOver();
    }

    public synchronized void updateIdle(double deltaSeconds, RadarService radarService, NavigationService navigationService, WorldMap worldMap) {
        if (!"running".equals(state)) {
            return;
        }
        nowSeconds += deltaSeconds;
        updateTorpedoes(deltaSeconds, navigationService, worldMap);
        Set<String> releasedBombIds = releasePendingBombs();
        updateBombs(deltaSeconds, releasedBombIds);
        updateFlakProjectiles(deltaSeconds);
        updateFlakHits();
        updateFlakTerrainImpacts(worldMap);
        respawnSunkShips(navigationService, worldMap, radarService);
        torpedoes.removeIf(torpedo -> !torpedoVisibleInWorld(torpedo));
        torpedoImpacts.removeIf(impact -> nowSeconds - impact.t() > TORPEDO_IMPACT_VISIBILITY_SECONDS);
        bombs.removeIf(bomb -> !"falling".equals(bomb.state()));
        bombImpacts.removeIf(impact -> nowSeconds - impact.t() > TORPEDO_IMPACT_VISIBILITY_SECONDS);
        flakProjectiles.removeIf(projectile -> !"flying".equals(projectile.state()));
        projectileHits.removeIf(hit -> nowSeconds - hit.t() > FLAK_HIT_VISIBILITY_SECONDS);
        flakImpacts.removeIf(impact -> nowSeconds - impact.t() > FLAK_HIT_VISIBILITY_SECONDS);
        ramHits.removeIf(hit -> nowSeconds - hit.t() > TORPEDO_IMPACT_VISIBILITY_SECONDS);
        checkGameOver();
    }

    public synchronized void update(double deltaSeconds) {
        throw new IllegalStateException("World navigation is required for server simulation");
    }

    private void commandBots(RadarService radarService, NavigationService navigationService, WorldMap worldMap) {
        if ("side-view-sandbox".equals(id)) {
            return;
        }
        if (radarService == null || worldMap == null) {
            return;
        }

        List<Ship> activeShips = allShips().stream()
                .filter(ship -> "active".equals(ship.state()))
                .toList();
        List<Ship> surfaceShips = activeShips.stream()
                .filter(ship -> !ship.isScoutPlane())
                .toList();
        RadarService.VisibilityCache visibilityCache = radarService.visibilityCache(worldMap, surfaceShips);
        Map<String, Integer> scoutPlaneTargetReservations = new LinkedHashMap<>();
        activeShips.stream()
                .filter(ship -> "bot".equals(ship.controlledBy()))
                .forEach(ship -> {
                    if (ship.isScoutPlane()) {
                        commandScoutPlaneBot(ship, activeShips, worldMap, scoutPlaneTargetReservations);
                    } else {
                        commandBot(ship, visibilityCache, navigationService, worldMap);
                    }
                });
    }

    private void commandScoutPlaneBot(Ship plane, List<Ship> activeShips, WorldMap worldMap,
                                      Map<String, Integer> targetReservations) {
        Optional<Ship> target = selectBotScoutPlaneTarget(plane, activeShips, targetReservations);
        if (target.isEmpty()) {
            clearBotScoutPlaneFlyThrough(plane);
            patrolScoutPlane(plane);
            return;
        }

        Ship ship = target.get();
        targetReservations.merge(ship.id(), 1, Integer::sum);
        BotTorpedoSolution torpedoSolution = botScoutPlaneTorpedoSolution(plane, ship);
        Vector2 torpedoTarget = torpedoSolution.targetPosition();
        BotBombingSolution bombSolution = botScoutPlaneBombingSolution(plane, ship);
        Vector2 bombTarget = bombSolution.targetPosition();
        double distance = plane.position().distanceTo(ship.position());
        boolean inBombWindow = distance >= BOT_SCOUT_PLANE_BOMB_MIN_RANGE && distance <= BOT_SCOUT_PLANE_BOMB_RANGE;
        boolean inTorpedoReleaseWindow = distance >= BOT_SCOUT_PLANE_TORPEDO_MIN_RANGE
                && distance <= BOT_SCOUT_PLANE_TORPEDO_RELEASE_RANGE;
        boolean preferTorpedoAttack = distance >= BOT_SCOUT_PLANE_TORPEDO_MIN_RANGE
                && distance <= BOT_SCOUT_PLANE_TORPEDO_APPROACH_RANGE;
        Optional<Vector2> flyThroughTarget = botScoutPlaneFlyThroughTarget(plane, distance);
        if (flyThroughTarget.isPresent()) {
            plane.botScoutPlaneTargetY(BOT_SCOUT_PLANE_BOMB_ATTACK_Y);
            applyScoutPlaneBotCommand(plane, angleTo(flyThroughTarget.get(), plane.position()));
            return;
        }
        if (distance < BOT_SCOUT_PLANE_TORPEDO_MIN_RANGE && !inBombWindow) {
            Vector2 flyThrough = startBotScoutPlaneFlyThrough(plane);
            plane.botScoutPlaneTargetY(BOT_SCOUT_PLANE_BOMB_ATTACK_Y);
            applyScoutPlaneBotCommand(plane, angleTo(flyThrough, plane.position()));
            return;
        }
        Vector2 aimTarget = preferTorpedoAttack ? torpedoTarget : bombTarget;
        double targetBearing = relativeBearing(plane, aimTarget);
        if (distance > BOT_SCOUT_PLANE_ATTACK_RANGE) {
            plane.botScoutPlaneTargetY(BOT_SCOUT_PLANE_CRUISE_Y);
            applyScoutPlaneBotCommand(plane, angleTo(torpedoTarget, plane.position()));
            return;
        }

        plane.botScoutPlaneTargetY(preferTorpedoAttack ? BOT_SCOUT_PLANE_TORPEDO_ATTACK_Y : BOT_SCOUT_PLANE_BOMB_ATTACK_Y);
        applyScoutPlaneBotCommand(plane, MathSupport.normalizeAngle(plane.heading() + targetBearing));
        if (Math.abs(targetBearing) > BOT_SCOUT_PLANE_ATTACK_ARC) {
            return;
        }

        if (preferTorpedoAttack
                && inTorpedoReleaseWindow
                && plane.y() <= BOT_SCOUT_PLANE_TORPEDO_RELEASE_MAX_Y
                && torpedoSolution.canRelease()
                && nowSeconds >= nextBotScoutPlaneTorpedoTime
                && plane.canDropBomb(nowSeconds)) {
            fireAirTorpedo(plane, BOT_SCOUT_PLANE_TORPEDO_COOLDOWN_SECONDS, torpedoSolution.heading());
            recordBotScoutPlaneAttack(plane, ship);
            return;
        }
        if (inBombWindow
                && (!preferTorpedoAttack || distance < BOT_SCOUT_PLANE_TORPEDO_MIN_RANGE)
                && bombSolution.canRelease()
                && plane.canDropBomb(nowSeconds)) {
            dropBombsFromScoutPlane(plane, BOT_SCOUT_PLANE_BOMB_COOLDOWN_SECONDS);
            recordBotScoutPlaneAttack(plane, ship);
            return;
        }
    }

    private Vector2 predictedShipPosition(Ship ship, double leadSeconds) {
        return ship.position().add(Vector2.fromHeading(ship.heading()).scale(Math.max(0, ship.speed()) * leadSeconds));
    }

    private BotTorpedoSolution botScoutPlaneTorpedoSolution(Ship plane, Ship target) {
        double torpedoSpeed = airTorpedoSpeed(plane);
        Vector2 releasePosition = plane.position().add(Vector2.fromHeading(plane.heading()).scale(BOT_SCOUT_PLANE_AIR_TORPEDO_RELEASE_OFFSET));
        Vector2 targetPosition = predictedShipPosition(target, BOT_SCOUT_PLANE_TORPEDO_LEAD_SECONDS);
        for (int iteration = 0; iteration < 3; iteration += 1) {
            double runSeconds = releasePosition.distanceTo(targetPosition) / Math.max(1, torpedoSpeed);
            targetPosition = predictedShipPosition(target, runSeconds);
        }
        double heading = angleTo(targetPosition, releasePosition);
        boolean canRelease = Math.abs(relativeBearing(plane, targetPosition)) <= BOT_SCOUT_PLANE_TORPEDO_ARC
                && angularDistance(plane.heading(), heading) <= BOT_SCOUT_PLANE_TORPEDO_ARC
                && Math.abs(plane.turnVelocity()) <= BOT_SCOUT_PLANE_STABLE_ATTACK_TURN_RATE;
        return new BotTorpedoSolution(targetPosition, heading, canRelease);
    }

    private BotBombingSolution botScoutPlaneBombingSolution(Ship plane, Ship target) {
        double fallSeconds = estimatedBombFallSeconds(plane);
        double centerReleaseDelaySeconds = (BOMBS_PER_DROP - 1) * BOMB_RELEASE_INTERVAL_SECONDS * 0.5;
        double leadSeconds = fallSeconds + centerReleaseDelaySeconds;
        Vector2 targetPosition = predictedShipPosition(target, leadSeconds);
        Vector2 toTarget = targetPosition.subtract(plane.position());
        Vector2 forward = Vector2.fromHeading(plane.heading());
        Vector2 right = rightFromHeading(plane.heading());
        double forwardDistance = dot(toTarget, forward);
        double lateralDistance = Math.abs(dot(toTarget, right));
        double bombSpeed = Math.min(SCOUT_PLANE_MAX_BOMB_HORIZONTAL_SPEED, Math.max(4, plane.speed() * 0.92));
        double patternCenterForwardDistance = bombSpeed * fallSeconds + Math.max(0, plane.speed()) * centerReleaseDelaySeconds;
        double forwardError = forwardDistance - patternCenterForwardDistance;
        boolean canRelease = forwardDistance > 0
                && forwardError <= BOT_SCOUT_PLANE_BOMB_FORWARD_TOLERANCE * 0.35
                && forwardError >= -BOT_SCOUT_PLANE_BOMB_FORWARD_TOLERANCE
                && lateralDistance <= BOT_SCOUT_PLANE_BOMB_LATERAL_TOLERANCE
                && Math.abs(plane.turnVelocity()) <= BOT_SCOUT_PLANE_STABLE_ATTACK_TURN_RATE;
        return new BotBombingSolution(targetPosition, canRelease);
    }

    private static double estimatedBombFallSeconds(Ship plane) {
        double altitude = Math.max(SCOUT_PLANE_MIN_BOMB_ALTITUDE, plane.y() - BOMB_DROP_VERTICAL_OFFSET);
        double initialVerticalSpeed = MathSupport.clamp(
                -plane.verticalSpeed(),
                -SCOUT_PLANE_MAX_BOMB_INITIAL_UP_SPEED,
                SCOUT_PLANE_MAX_BOMB_INITIAL_DOWN_SPEED
        );
        double gravity = 14.0;
        double discriminant = initialVerticalSpeed * initialVerticalSpeed + 2.0 * gravity * altitude;
        return Math.max(0.2, (-initialVerticalSpeed + Math.sqrt(Math.max(0, discriminant))) / gravity);
    }

    private static double dot(Vector2 left, Vector2 right) {
        return left.x() * right.x() + left.z() * right.z();
    }

    private Optional<Vector2> botScoutPlaneFlyThroughTarget(Ship plane, double distanceToTarget) {
        Vector2 target = botScoutPlaneFlyThroughTargets.get(plane.id());
        if (target == null) {
            return Optional.empty();
        }
        if (nowSeconds >= botScoutPlaneFlyThroughUntil.getOrDefault(plane.id(), 0.0)
                || plane.position().distanceTo(target) <= BOT_SCOUT_PLANE_FLY_THROUGH_COMPLETE_DISTANCE
                || distanceToTarget >= BOT_SCOUT_PLANE_TORPEDO_APPROACH_RANGE + 40.0) {
            clearBotScoutPlaneFlyThrough(plane);
            return Optional.empty();
        }
        return Optional.of(target);
    }

    private Vector2 startBotScoutPlaneFlyThrough(Ship plane) {
        Vector2 target = plane.position().add(Vector2.fromHeading(plane.heading()).scale(BOT_SCOUT_PLANE_FLY_THROUGH_DISTANCE));
        botScoutPlaneFlyThroughTargets.put(plane.id(), target);
        botScoutPlaneFlyThroughUntil.put(plane.id(), nowSeconds + BOT_SCOUT_PLANE_FLY_THROUGH_SECONDS);
        return target;
    }

    private void clearBotScoutPlaneFlyThrough(Ship plane) {
        botScoutPlaneFlyThroughTargets.remove(plane.id());
        botScoutPlaneFlyThroughUntil.remove(plane.id());
    }

    private void applyScoutPlaneBotCommand(Ship plane, double desiredHeading) {
        double heading = scoutPlaneTerrainSafeHeading(plane, desiredHeading);
        plane.applyCommand(7, rudderTowardHeading(plane, heading));
    }

    private double scoutPlaneTerrainSafeHeading(Ship plane, double desiredHeading) {
        if (isScoutPlaneCourseTerrainSafe(plane, desiredHeading)) {
            return desiredHeading;
        }

        double bestHeading = MathSupport.normalizeAngle(plane.heading() + Math.PI);
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int step = -8; step <= 8; step += 1) {
            double heading = MathSupport.normalizeAngle(plane.heading() + step * (Math.PI / 10.0));
            double score = scoutPlaneTerrainCourseScore(plane, heading, desiredHeading);
            if (score > bestScore) {
                bestScore = score;
                bestHeading = heading;
            }
        }
        return bestHeading;
    }

    private double scoutPlaneTerrainCourseScore(Ship plane, double heading, double desiredHeading) {
        double score = 0;
        Vector2 forward = Vector2.fromHeading(heading);
        double[] distances = {32, 64, 104, 150, 205};
        for (int index = 0; index < distances.length; index += 1) {
            Vector2 sample = plane.position().add(forward.scale(distances[index]));
            if (isScoutPlaneTerrainObstacle(sample)) {
                score -= 4000 - index * 300;
                continue;
            }
            score += distances[index] * (1.0 + index * 0.12);
        }
        score -= angularDistance(heading, desiredHeading) * 45;
        score -= angularDistance(heading, plane.heading()) * 12;
        return score;
    }

    private boolean isScoutPlaneCourseTerrainSafe(Ship plane, double heading) {
        Vector2 forward = Vector2.fromHeading(heading);
        double[] distances = {34, 72, 118, 170};
        for (double distance : distances) {
            if (isScoutPlaneTerrainObstacle(plane.position().add(forward.scale(distance)))) {
                return false;
            }
        }
        return true;
    }

    private boolean isScoutPlaneTerrainObstacle(Vector2 position) {
        return LandGeometry.isBlocked(position, scoutPlaneObstacleMap);
    }

    private Optional<Ship> selectBotScoutPlaneTarget(Ship plane, List<Ship> activeShips,
                                                     Map<String, Integer> targetReservations) {
        List<Ship> candidates = activeShips.stream()
                .filter(ship -> !ship.teamId().equals(plane.teamId()))
                .filter(ship -> !ship.isScoutPlane())
                .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        List<Ship> preferredCandidates = candidates.stream()
                .filter(ship -> !isHumanControlled(ship))
                .toList();
        if (preferredCandidates.isEmpty()) {
            return candidates.stream()
                    .min((left, right) -> Double.compare(
                            botScoutPlaneTargetScore(plane, left, targetReservations),
                            botScoutPlaneTargetScore(plane, right, targetReservations)
                    ));
        }

        Optional<Ship> attackHuman = scoutPlaneAttackHuman(plane);
        if (shouldForceHumanScoutPlaneTarget(plane, attackHuman)
                && candidates.stream().anyMatch(candidate -> candidate.id().equals(attackHuman.get().id()))) {
            return attackHuman;
        }

        return preferredCandidates.stream()
                .min((left, right) -> Double.compare(
                        botScoutPlaneTargetScore(plane, left, targetReservations),
                        botScoutPlaneTargetScore(plane, right, targetReservations)
                ));
    }

    private void patrolScoutPlane(Ship plane) {
        double patrolHeading = MathSupport.normalizeAngle(stablePhase(plane.id()) + Math.sin(nowSeconds * 0.08 + stablePhase(plane.id())) * 0.9);
        plane.applyCommand(7, rudderTowardHeading(plane, patrolHeading));
    }

    private double botScoutPlaneTargetScore(Ship plane, Ship target, Map<String, Integer> targetReservations) {
        return plane.position().distanceTo(target.position())
                + targetReservations.getOrDefault(target.id(), 0) * BOT_SCOUT_PLANE_TARGET_RESERVATION_PENALTY
                + stablePlaneTargetBias(plane, target);
    }

    private double stablePlaneTargetBias(Ship plane, Ship target) {
        double phase = stablePhase(plane.id() + ":" + target.id());
        return Math.sin(phase * 2.3) * BOT_SCOUT_PLANE_TARGET_TIE_BREAKER;
    }

    private boolean isHumanControlled(Ship ship) {
        return isHumanController(ship.controlledBy());
    }

    private boolean shouldForceHumanScoutPlaneTarget(Ship plane, Optional<Ship> attackHuman) {
        if (attackHuman.isEmpty()) {
            return false;
        }
        int threshold = botScoutPlaneForceHumanAttackThreshold(plane.teamId());
        if (threshold == Integer.MAX_VALUE) {
            return false;
        }
        return botScoutPlaneNonHumanAttackStreakByHumanTarget.getOrDefault(
                scoutPlaneHumanAttackStreakKey(plane.teamId(), attackHuman.get().id()),
                0
        )
                >= threshold;
    }

    private int botScoutPlaneForceHumanAttackThreshold(String attackingTeamId) {
        int humanTargetCount = activeHumanTargetsForScoutPlaneTeam(attackingTeamId).size();
        if (humanTargetCount <= 0) {
            return Integer.MAX_VALUE;
        }
        if (humanTargetCount == 1) {
            return BOT_SCOUT_PLANE_FORCE_HUMAN_SINGLE_TARGET_STREAK;
        }
        if (humanTargetCount == 2) {
            return BOT_SCOUT_PLANE_FORCE_HUMAN_TWO_TARGET_STREAK;
        }
        return BOT_SCOUT_PLANE_FORCE_HUMAN_MANY_TARGET_STREAK;
    }

    private Optional<Ship> scoutPlaneAttackHuman(Ship plane) {
        if (!plane.isScoutPlane() || !"bot".equals(plane.controlledBy())) {
            return Optional.empty();
        }

        List<Ship> availablePlanes = activeTeamShips(plane.teamId()).stream()
                .filter(candidate -> candidate.isScoutPlane() && "bot".equals(candidate.controlledBy()))
                .toList();
        List<Ship> humans = allShips().stream()
                .filter(candidate -> "active".equals(candidate.state()))
                .filter(candidate -> !candidate.teamId().equals(plane.teamId()))
                .filter(candidate -> !candidate.isScoutPlane())
                .filter(this::isHumanControlled)
                .sorted((left, right) -> left.id().compareTo(right.id()))
                .toList();
        List<String> assignedPlaneIds = new ArrayList<>();
        for (Ship human : humans) {
            Optional<Ship> attacker = availablePlanes.stream()
                    .filter(candidate -> !assignedPlaneIds.contains(candidate.id()))
                    .min((left, right) -> Double.compare(
                            left.position().distanceTo(human.position()),
                            right.position().distanceTo(human.position())
                    ));
            if (attacker.isEmpty()) {
                return Optional.empty();
            }
            assignedPlaneIds.add(attacker.get().id());
            if (attacker.get().id().equals(plane.id())) {
                return Optional.of(human);
            }
        }
        return Optional.empty();
    }

    private void recordBotScoutPlaneAttack(Ship plane, Ship target) {
        if (isHumanControlled(target)) {
            botScoutPlaneNonHumanAttackStreakByHumanTarget.remove(scoutPlaneHumanAttackStreakKey(plane.teamId(), target.id()));
            return;
        }
        activeHumanTargetsForScoutPlaneTeam(plane.teamId()).forEach(human ->
                botScoutPlaneNonHumanAttackStreakByHumanTarget.merge(
                        scoutPlaneHumanAttackStreakKey(plane.teamId(), human.id()),
                        1,
                        Integer::sum
                )
        );
    }

    private List<Ship> activeHumanTargetsForScoutPlaneTeam(String teamId) {
        return allShips().stream()
                .filter(candidate -> "active".equals(candidate.state()))
                .filter(candidate -> !candidate.teamId().equals(teamId))
                .filter(candidate -> !candidate.isScoutPlane())
                .filter(this::isHumanControlled)
                .toList();
    }

    private String scoutPlaneHumanAttackStreakKey(String attackingTeamId, String humanShipId) {
        return attackingTeamId + ">" + humanShipId;
    }

    private void commandBot(Ship ship, RadarService.VisibilityCache visibilityCache, NavigationService navigationService, WorldMap worldMap) {
        if (escapeBlockedWater(ship, navigationService, worldMap)) {
            return;
        }

        Optional<Torpedo> threat = visibleIncomingTorpedo(ship);
        if (threat.isPresent()) {
            evadeTorpedo(ship, threat.get(), navigationService, worldMap);
            return;
        }
        if (ship.applyGlancingRamBackoff(nowSeconds)) {
            return;
        }

        Optional<Ship> target = chooseBotTarget(ship, visibleTargets(ship, visibilityCache));
        if (target.isEmpty()) {
            moveWithoutTarget(ship, navigationService, worldMap);
            return;
        }

        aimAtTarget(ship, target.get(), navigationService, worldMap);
    }

    private void moveWithoutTarget(Ship ship, NavigationService navigationService, WorldMap worldMap) {
        if (escortHumanLeader(ship, navigationService, worldMap)) {
            return;
        }

        Optional<Vector2> nearestLandCenter = nearestLandCenter(ship.position(), worldMap);
        double nearestLandDistance = nearestLandCenter
                .map(center -> ship.position().distanceTo(center))
                .orElse(0.0);
        if (nearestLandDistance > BOT_RETURN_TO_LAND_DISTANCE && nearestLandCenter.isPresent()) {
            steerToward(ship, nearestLandCenter.get(), ENGINE_TWO_THIRDS, navigationService, worldMap);
            return;
        }
        if (nearestLandDistance > BOT_PATROL_LAND_DISTANCE && nearestLandCenter.isPresent()) {
            steerToward(ship, nearestLandCenter.get(), ENGINE_HALF, navigationService, worldMap);
            return;
        }
        patrol(ship, navigationService, worldMap);
    }

    private boolean escapeBlockedWater(Ship ship, NavigationService navigationService, WorldMap worldMap) {
        Vector2 forward = Vector2.fromHeading(ship.heading());
        double lookAheadDistance = MathSupport.clamp(Math.abs(ship.speed()) * 6.0 + 22.0, 24.0, 78.0);
        Vector2 nearLookAhead = ship.position().add(forward.scale(14));
        Vector2 farLookAhead = ship.position().add(forward.scale(lookAheadDistance));
        boolean blockedHere = navigationService.isShipBlocked(ship.position(), ship.heading(), worldMap);
        boolean blockedAhead = navigationService.isShipBlocked(nearLookAhead, ship.heading(), worldMap)
                || navigationService.isShipBlocked(farLookAhead, ship.heading(), worldMap);

        if (blockedHere) {
            boolean forwardClear = !navigationService.isShipMovementBlocked(
                    ship.position(),
                    ship.heading(),
                    EngineOrders.speedFor(ENGINE_SLOW),
                    worldMap
            );
            boolean asternClear = !navigationService.isShipMovementBlocked(
                    ship.position(),
                    ship.heading(),
                    EngineOrders.speedFor(ENGINE_FULL_ASTERN),
                    worldMap
            );
            if (ship.speed() >= 0 && forwardClear) {
                ship.applyCommand(ENGINE_SLOW, 0);
                return true;
            }
            if (asternClear) {
                ship.applyCommand(ENGINE_FULL_ASTERN, 0);
                return true;
            }
            if (forwardClear) {
                ship.applyCommand(ENGINE_SLOW, 0);
                return true;
            }
            ship.applyCommand(ENGINE_STOP, 0);
            return true;
        }

        if (blockedAhead) {
            double safeHeading = chooseSafeEscapeHeading(ship, navigationService, worldMap);
            int rudder = rudderTowardHeading(ship, safeHeading);
            ship.applyCommand(ENGINE_ONE_THIRD, rudder);
            return true;
        }

        return false;
    }

    private double chooseSafeEscapeHeading(Ship ship, NavigationService navigationService, WorldMap worldMap) {
        double bestHeading = MathSupport.normalizeAngle(ship.heading() + Math.PI);
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int step = -12; step <= 12; step += 1) {
            double offset = step * (Math.PI / 12.0);
            double heading = MathSupport.normalizeAngle(ship.heading() + offset);
            double score = escapeCourseScore(ship, heading, navigationService, worldMap);
            if (score > bestScore) {
                bestScore = score;
                bestHeading = heading;
            }
        }
        return bestHeading;
    }

    private double escapeCourseScore(Ship ship, double heading, NavigationService navigationService, WorldMap worldMap) {
        double score = 0;
        Vector2 forward = Vector2.fromHeading(heading);
        double[] distances = {18, 34, 58, 88, 128, 170};
        for (int index = 0; index < distances.length; index += 1) {
            Vector2 sample = ship.position().add(forward.scale(distances[index]));
            if (navigationService.isShipBlocked(sample, heading, worldMap)) {
                score -= 5000 - index * 250;
                continue;
            }
            score += distances[index] * (1.0 + index * 0.18);
        }

        double turn = Math.abs(MathSupport.normalizeAngle(heading - ship.heading()));
        score -= turn * 18;
        if (turn > Math.PI * 0.82) {
            score -= 35;
        }
        score += Math.sin(stablePhase(ship.id()) + heading * 1.7) * 2.5;
        return score;
    }

    private int rudderTowardHeading(Ship ship, double heading) {
        double targetBearing = MathSupport.normalizeAngle(heading - ship.heading());
        return (int) Math.round(MathSupport.clamp(targetBearing / 0.58, -1, 1) * 35);
    }

    private void applyBotCommand(Ship ship, int engineOrder, int rudder, NavigationService navigationService, WorldMap worldMap) {
        if (EngineOrders.speedFor(engineOrder) <= 0) {
            ship.applyCommand(engineOrder, rudder);
            return;
        }

        int cruisingEngineOrder = Math.max(engineOrder, ENGINE_HALF);
        double plannedHeading = plannedBotCourseHeading(ship, rudder);
        if (isBotCourseSafe(ship, plannedHeading, navigationService, worldMap)) {
            ship.applyCommand(cruisingEngineOrder, rudder);
            return;
        }

        double escapeHeading = chooseSafeEscapeHeading(ship, navigationService, worldMap);
        ship.applyCommand(Math.min(engineOrder, ENGINE_SLOW), rudderTowardHeading(ship, escapeHeading));
    }

    private double plannedBotCourseHeading(Ship ship, int rudder) {
        double turn = MathSupport.clamp(rudder / 35.0, -1, 1);
        return MathSupport.normalizeAngle(ship.heading() + turn * 0.48);
    }

    private boolean isBotCourseSafe(Ship ship, double heading, NavigationService navigationService, WorldMap worldMap) {
        Vector2 forward = Vector2.fromHeading(heading);
        double[] distances = {28, 55, 92, 138};
        for (double distance : distances) {
            Vector2 sample = ship.position().add(forward.scale(distance));
            if (navigationService.isShipBlocked(sample, heading, worldMap)) {
                return false;
            }
        }
        return true;
    }

    private List<Ship> visibleTargets(Ship ship, RadarService.VisibilityCache visibilityCache) {
        return visibilityCache.candidates(ship, RadarService.HUMAN_TARGET_RANGE).stream()
                .filter(target -> !target.teamId().equals(ship.teamId()))
                .filter(target -> !target.isScoutPlane())
                .filter(target -> isStrategicHumanContact(ship, target)
                        || visibilityCache.isVisible(ship, target, RadarService.RADAR_RANGE))
                .toList();
    }

    private boolean isStrategicHumanContact(Ship observer, Ship target) {
        return isHumanControlled(target)
                && observer.position().distanceTo(target.position()) > RadarService.RADAR_RANGE;
    }

    private Optional<Ship> chooseBotTarget(Ship ship, List<Ship> targets) {
        if (targets.isEmpty()) {
            return Optional.empty();
        }

        ShipDistance nearestHumanTarget = null;
        ShipDistance nearestBotTarget = null;
        Ship bestTarget = null;
        double bestTargetScore = Double.POSITIVE_INFINITY;
        for (Ship target : targets) {
            ShipDistance candidate = new ShipDistance(target, ship.position().distanceTo(target.position()));
            double targetScore = botTargetScore(ship, target);
            if (bestTarget == null || targetScore < bestTargetScore) {
                bestTarget = target;
                bestTargetScore = targetScore;
            }
            if ("bot".equals(target.controlledBy())) {
                if (nearestBotTarget == null || candidate.distance() < nearestBotTarget.distance()) {
                    nearestBotTarget = candidate;
                }
            } else if (nearestHumanTarget == null || candidate.distance() < nearestHumanTarget.distance()) {
                nearestHumanTarget = candidate;
            }
        }

        if (nearestHumanTarget != null
                && (nearestBotTarget == null
                || nearestBotTarget.distance() > BOT_HUMAN_TARGET_PRIORITY_CLEARANCE)) {
            return Optional.of(nearestHumanTarget.ship());
        }

        return Optional.ofNullable(bestTarget);
    }

    private double botTargetScore(Ship ship, Ship target) {
        return ship.position().distanceTo(target.position())
                + friendlyHumanDriftPenalty(nearestHumanControlledTeamShip(ship), target.position(), BOT_FRIENDLY_HUMAN_TARGET_DRIFT_WEIGHT);
    }

    private double friendlyHumanDriftPenalty(Optional<Ship> human, Vector2 targetPosition, double weight) {
        if (human.isEmpty()) {
            return 0;
        }
        double distanceToHumanArea = targetPosition.distanceTo(human.get().position());
        return Math.max(0, distanceToHumanArea - BOT_FRIENDLY_HUMAN_COMBAT_RADIUS) * weight;
    }

    private Optional<Torpedo> visibleIncomingTorpedo(Ship ship) {
        return torpedoes.stream()
                .filter(torpedo -> "running".equals(torpedo.state()))
                .filter(torpedo -> botCanSeeIncomingTorpedo(ship, torpedo))
                .min((left, right) -> Double.compare(
                        ship.position().distanceTo(left.position()),
                        ship.position().distanceTo(right.position())
                ));
    }

    private boolean botCanSeeIncomingTorpedo(Ship ship, Torpedo torpedo) {
        Vector2 lookoutPosition = ship.position()
                .add(Vector2.fromHeading(ship.heading()).scale(BOT_TORPEDO_LOOKOUT_FORWARD_OFFSET));
        if (lookoutPosition.distanceTo(torpedo.position()) > BOT_TORPEDO_EVADE_RANGE) {
            return false;
        }

        double bearingFromLookout = MathSupport.normalizeAngle(angleTo(torpedo.position(), lookoutPosition) - ship.heading());
        if (Math.abs(bearingFromLookout) > BOT_TORPEDO_LOOKOUT_ARC) {
            return false;
        }

        double incomingAngle = angularDistance(angleTo(ship.position(), torpedo.position()), torpedo.heading());
        if (incomingAngle > BOT_TORPEDO_INCOMING_ARC) {
            return false;
        }

        return torpedoTrackDistanceToShip(torpedo, ship.position()) <= BOT_TORPEDO_THREAT_CORRIDOR;
    }

    private double torpedoTrackDistanceToShip(Torpedo torpedo, Vector2 shipPosition) {
        Vector2 forward = Vector2.fromHeading(torpedo.heading());
        Vector2 toShip = shipPosition.subtract(torpedo.position());
        double alongTrack = toShip.x() * forward.x() + toShip.z() * forward.z();
        if (alongTrack < 0) {
            return Double.POSITIVE_INFINITY;
        }
        Vector2 closestPoint = torpedo.position().add(forward.scale(alongTrack));
        return closestPoint.distanceTo(shipPosition);
    }

    private void evadeTorpedo(Ship ship, Torpedo torpedo, NavigationService navigationService, WorldMap worldMap) {
        double side = relativeBearing(ship, torpedo.position()) >= 0 ? -1 : 1;
        int rudder = (int) (side * 35);
        applyBotCommand(ship, ENGINE_FULL, rudder, navigationService, worldMap);
    }

    private void patrol(Ship ship, NavigationService navigationService, WorldMap worldMap) {
        double wander = Math.sin(nowSeconds * 0.13 + stablePhase(ship.id())) * 18;
        applyBotCommand(ship, ENGINE_HALF, (int) Math.round(wander), navigationService, worldMap);
    }

    private Optional<Vector2> nearestLandCenter(Vector2 position, WorldMap worldMap) {
        return worldMap.landmasses().stream()
                .map(landmass -> new Vector2(landmass.x(), landmass.z()))
                .min((left, right) -> Double.compare(
                        position.distanceTo(left),
                        position.distanceTo(right)
                ));
    }

    private void steerToward(Ship ship, Vector2 target, int engineOrder, NavigationService navigationService, WorldMap worldMap) {
        double targetBearing = relativeBearing(ship, target);
        int rudder = (int) Math.round(MathSupport.clamp(targetBearing / 0.7, -1, 1) * 35);
        applyBotCommand(ship, engineOrder, rudder, navigationService, worldMap);
    }

    private boolean escortHumanLeader(Ship ship, NavigationService navigationService, WorldMap worldMap) {
        Optional<Ship> leader = nearestHumanControlledTeamShip(ship);
        if (leader.isEmpty()) {
            return false;
        }

        Ship human = leader.get();
        double distanceToHuman = ship.position().distanceTo(human.position());
        if (distanceToHuman > BOT_ESCORT_JOIN_RANGE) {
            return false;
        }
        if (distanceToHuman < BOT_ESCORT_MIN_DISTANCE) {
            steerAwayFrom(ship, human.position(), Math.abs(human.speed()) < 0.8 ? ENGINE_STOP : ENGINE_SLOW, navigationService, worldMap);
            return true;
        }

        Vector2 escortPoint = escortPointFor(ship, human);
        double distanceToEscortPoint = ship.position().distanceTo(escortPoint);
        int engineOrder = escortEngineOrder(distanceToEscortPoint, human.speed());
        steerToward(ship, escortPoint, engineOrder, navigationService, worldMap);
        return true;
    }

    private Optional<Ship> nearestHumanControlledTeamShip(Ship ship) {
        return activeTeamShips(ship.teamId()).stream()
                .filter(this::isHumanControlled)
                .min((left, right) -> Double.compare(
                        ship.position().distanceTo(left.position()),
                        ship.position().distanceTo(right.position())
                ));
    }

    private List<Ship> activeTeamShips(String teamId) {
        return allShips().stream()
                .filter(ship -> teamId.equals(ship.teamId()))
                .filter(ship -> "active".equals(ship.state()))
                .toList();
    }

    private Vector2 escortPointFor(Ship ship, Ship leader) {
        double side = stablePhase(ship.id()) % 2.0 >= 1.0 ? 1.0 : -1.0;
        double lane = 70 + (stablePhase(ship.id()) % 0.8) * 36;
        double sternOffset = BOT_ESCORT_TARGET_DISTANCE + (stablePhase(ship.id()) % 0.6) * 45;
        Vector2 forward = Vector2.fromHeading(leader.heading());
        Vector2 right = new Vector2(Math.cos(leader.heading()), -Math.sin(leader.heading()));
        return leader.position()
                .add(forward.scale(-sternOffset))
                .add(right.scale(side * lane));
    }

    private int escortEngineOrder(double distanceToEscortPoint, double leaderSpeed) {
        if (distanceToEscortPoint < 45 && Math.abs(leaderSpeed) < 0.8) {
            return ENGINE_STOP;
        }
        if (distanceToEscortPoint < 90) {
            return Math.abs(leaderSpeed) < 2.5 ? ENGINE_SLOW : ENGINE_HALF;
        }
        if (distanceToEscortPoint < 180) {
            return ENGINE_HALF;
        }
        return ENGINE_FLANK;
    }

    private void steerAwayFrom(Ship ship, Vector2 point, int engineOrder, NavigationService navigationService, WorldMap worldMap) {
        Vector2 away = ship.position().add(ship.position().subtract(point).normalized().scale(130));
        steerToward(ship, away, engineOrder, navigationService, worldMap);
    }

    private void aimAtTarget(Ship ship, Ship target, NavigationService navigationService, WorldMap worldMap) {
        double distance = ship.position().distanceTo(target.position());
        double targetBearing = relativeBearing(ship, target.position());
        double aimError = Math.sin(nowSeconds * 0.31 + stablePhase(ship.id())) * BOT_AIM_ERROR;
        double steerError = MathSupport.normalizeAngle(targetBearing + aimError);
        int rudder = (int) Math.round(MathSupport.clamp(steerError / 0.58, -1, 1) * 35);
        int engineOrder = botAttackEngineOrder(ship, target, distance, targetBearing);
        applyBotCommand(ship, engineOrder, rudder, navigationService, worldMap);

        boolean closeInFront = distance <= BOT_CLOSE_FIRE_RANGE && Math.abs(targetBearing) <= BOT_CLOSE_FIRE_ARC;
        boolean aimedShot = distance >= BOT_FIRE_MIN_RANGE && distance <= BOT_FIRE_MAX_RANGE && Math.abs(steerError) <= BOT_FIRE_ARC;
        if (!SCOUT_PLANE_EXPERIMENT_PEACEFUL_BOTS && (closeInFront || aimedShot)) {
            fireTorpedo(ship, 10.5 + Math.abs(Math.sin(stablePhase(ship.id()))) * 3.0, aimError * 0.65);
        }
    }

    private int botAttackEngineOrder(Ship ship, Ship target, double distance, double targetBearing) {
        double absoluteBearing = Math.abs(targetBearing);
        if (distance < BOT_RAM_RANGE) {
            return absoluteBearing <= Math.toRadians(30) ? ENGINE_ONE_THIRD : ENGINE_FULL;
        }
        if (isHumanControlled(target) && Math.abs(ship.speed()) < 0.8 && distance <= BOT_APPROACH_SLOW_RANGE) {
            return ENGINE_FULL;
        }
        if (distance < BOT_CLOSE_MANEUVER_RANGE) {
            if (absoluteBearing <= Math.toRadians(30)) {
                return ENGINE_ONE_THIRD;
            }
            if (absoluteBearing <= BOT_CLOSE_FIRE_ARC) {
                return ENGINE_HALF;
            }
            return ENGINE_FULL;
        }
        if (distance > BOT_RADAR_INTERCEPT_RANGE) {
            return ENGINE_FULL;
        }
        if (distance > BOT_APPROACH_SLOW_RANGE) {
            return ENGINE_TWO_THIRDS;
        }
        return ENGINE_HALF;
    }

    private void updateRamCollisions() {
        List<Ship> activeShips = allShips().stream()
                .filter(ship -> "active".equals(ship.state()))
                .filter(ship -> !ship.isScoutPlane())
                .toList();
        for (int i = 0; i < activeShips.size(); i += 1) {
            Ship left = activeShips.get(i);
            for (int j = i + 1; j < activeShips.size(); j += 1) {
                Ship right = activeShips.get(j);
                if (left.teamId().equals(right.teamId())) {
                    continue;
                }

                RamImpact leftImpact = ramImpact(left, right);
                RamImpact rightImpact = ramImpact(right, left);
                if (!leftImpact.hits() && !rightImpact.hits()
                        && left.position().distanceTo(right.position()) > RAM_HIT_RADIUS * TORPEDO_BOAT_MODEL_SCALE) {
                    continue;
                }

                if (left.isBotControlled() || right.isBotControlled()) {
                    resolveGlancingRam(left, right);
                    continue;
                }

                boolean headOnCollision = leftImpact.isBowHit() && rightImpact.isBowHit()
                        && angularDistance(left.heading(), right.heading()) > Math.toRadians(135);
                boolean glancingCollision = isGlancingCollision(left, right);
                if (headOnCollision) {
                    sinkShipByRam(left, right);
                    sinkShipByRam(right, left);
                } else if (glancingCollision) {
                    resolveGlancingRam(left, right);
                } else if (leftImpact.hits() || rightImpact.hits()) {
                    resolveSideRamBySpeed(left, right);
                } else {
                    resolveSideRamBySpeed(left, right);
                }
            }
        }
    }

    private void resolveGlancingRam(Ship left, Ship right) {
        double side = Math.signum(MathSupport.normalizeAngle(right.heading() - left.heading()));
        if (side == 0) {
            side = 1;
        }
        resolveGlancingShip(left, right.position(), -side * RAM_GLANCING_HEADING_IMPULSE);
        resolveGlancingShip(right, left.position(), side * RAM_GLANCING_HEADING_IMPULSE);
    }

    private void resolveGlancingShip(Ship ship, Vector2 targetPosition, double headingImpulse) {
        if (ship.isBotControlled()) {
            ship.backOffAfterGlancingRam(nowSeconds, BOT_GLANCING_RAM_BACKOFF_SECONDS, targetPosition);
        } else {
            ship.glanceOff(headingImpulse, 0.55);
        }
    }

    private void resolveSideRamBySpeed(Ship left, Ship right) {
        double leftSpeed = Math.max(0, left.speed());
        double rightSpeed = Math.max(0, right.speed());
        if (leftSpeed >= rightSpeed) {
            sinkShipByRam(right, left);
            left.stopAfterRamImpact();
        } else {
            sinkShipByRam(left, right);
            right.stopAfterRamImpact();
        }
    }

    private void sinkShipByRam(Ship target, Ship attacker) {
        boolean sunk = sinkShip(target, attacker.controlledBy());
        if (sunk) {
            recordRamHit(attacker, target);
        }
    }

    private boolean isGlancingCollision(Ship left, Ship right) {
        double relativeHeading = angularDistance(left.heading(), right.heading());
        double acuteHeading = Math.min(relativeHeading, Math.PI - relativeHeading);
        return acuteHeading < RAM_GLANCING_COLLISION_ANGLE;
    }

    private RamImpact ramImpact(Ship attacker, Ship target) {
        if (attacker.speed() < 1.8) {
            return RamImpact.miss();
        }

        RamImpact bestImpact = RamImpact.miss();
        for (double bowOffset : List.of(RAM_BOW_OFFSET, RAM_BOW_OFFSET - 1.15, RAM_BOW_OFFSET - 2.3)) {
            RamImpact impact = ramImpactAt(attacker, target, bowOffset);
            if (impact.sideScore() > bestImpact.sideScore() || (impact.hits() && !bestImpact.hits())) {
                bestImpact = impact;
            }
        }
        return bestImpact;
    }

    private RamImpact ramImpactAt(Ship attacker, Ship target, double bowOffset) {
        Vector2 attackerBow = attacker.position()
                .add(Vector2.fromHeading(attacker.heading()).scale(bowOffset * TORPEDO_BOAT_MODEL_SCALE));
        LocalHullPoint hit = localHullPoint(attackerBow, target);
        if (!forwardInsideTorpedoBoatHull(hit.forward(), RAM_SIDE_MARGIN)) {
            return RamImpact.miss();
        }

        double halfWidth = enemyHullHalfWidthAt(hit.forward());
        if (Math.abs(hit.right()) > halfWidth + RAM_SIDE_MARGIN) {
            return RamImpact.miss();
        }

        double relativeHeading = angularDistance(attacker.heading(), target.heading());
        double sideAngleError = Math.abs((Math.PI / 2) - Math.min(relativeHeading, Math.PI - relativeHeading));
        boolean sideAngle = sideAngleError <= RAM_SIDE_ANGLE_TOLERANCE;
        boolean sideHit = hit.forward() >= RAM_SIDE_FORWARD_MIN
                && hit.forward() <= RAM_SIDE_FORWARD_MAX
                && sideAngle;
        boolean bowHit = hit.forward() > RAM_BOW_LENGTH - 1.15;
        double sideScore = sideHit
                ? (1 - sideAngleError / RAM_SIDE_ANGLE_TOLERANCE)
                + MathSupport.clamp((halfWidth + RAM_SIDE_MARGIN - Math.abs(hit.right())) / (halfWidth + RAM_SIDE_MARGIN), 0, 1) * 0.35
                + MathSupport.clamp(attacker.speed() / 8.0, 0, 1) * 0.25
                : 0;
        return new RamImpact(true, sideHit, bowHit, sideScore);
    }

    private LocalHullPoint localHullPoint(Vector2 point, Ship ship) {
        double dx = point.x() - ship.position().x();
        double dz = point.z() - ship.position().z();
        return new LocalHullPoint(
                (dx * Math.cos(ship.heading()) - dz * Math.sin(ship.heading())) / TORPEDO_BOAT_MODEL_SCALE,
                (dx * Math.sin(ship.heading()) + dz * Math.cos(ship.heading())) / TORPEDO_BOAT_MODEL_SCALE
        );
    }

    private double localShipY(double y, Ship ship, double scale) {
        return (y - ship.y()) / scale - TORPEDO_BOAT_MODEL_WATERLINE_Y;
    }

    private double enemyHullHalfWidthAt(double forward) {
        return torpedoBoatHullTopHalfWidthAt(forward);
    }

    private boolean forwardInsideTorpedoBoatHull(double forward, double margin) {
        return forward >= TORPEDO_BOAT_STERN_Z - margin && forward <= TORPEDO_BOAT_BOW_Z + margin;
    }

    private double torpedoBoatHullTopHalfWidthAt(double forward) {
        double[][] sections = {
                {-4.2, 0.31},
                {-3.45, 0.53},
                {-2.25, 0.73},
                {-1.1, 0.79},
                {-0.22, 0.79},
                {-0.04, 0.78},
                {1.25, 0.67},
                {2.42, 0.436},
                {2.452, 0.4295},
                {2.469, 0.4245},
                {2.72, 0.35},
                {3.18, 0.21},
                {3.68, 0.01}
        };

        return interpolateSectionValue(sections, forward);
    }

    private record LocalHullPoint(double right, double forward) {
    }

    private record RamImpact(boolean hits, boolean isCleanSideHit, boolean isBowHit, double sideScore) {
        static RamImpact miss() {
            return new RamImpact(false, false, false, 0);
        }
    }

    private void updateTorpedoes(double deltaSeconds, NavigationService navigationService, WorldMap worldMap) {
        for (Torpedo torpedo : torpedoes) {
            torpedo.update(deltaSeconds);
            if ("expired".equals(torpedo.state())) {
                recordTorpedoImpact(torpedo, "expired", null);
                continue;
            }
            if ("airborne".equals(torpedo.state())) {
                airborneTorpedoHitsScoutPlane(torpedo)
                        .ifPresent(plane -> {
                            sinkShip(plane, shooterController(torpedo.shipId()));
                            torpedo.hit();
                            recordTorpedoImpact(torpedo, "plane-hit", plane.id());
                        });
                continue;
            }
            if (!"running".equals(torpedo.state())) {
                continue;
            }

            if (torpedoHitsLand(torpedo, navigationService, worldMap)) {
                torpedo.hit();
                recordTorpedoImpact(torpedo, "land-hit", null);
                continue;
            }

            allShips().stream()
                    .filter(ship -> "active".equals(ship.state()))
                    .filter(ship -> !ship.isScoutPlane())
                    .filter(ship -> !ship.id().equals(torpedo.shipId()))
                    .filter(ship -> torpedoHitsShip(torpedo, ship))
                    .findFirst()
                    .ifPresent(ship -> {
                        sinkShip(ship, shooterController(torpedo.shipId()));
                        torpedo.hit();
                        recordTorpedoImpact(torpedo, "ship-hit", ship.id());
                    });
        }
    }

    private Optional<Ship> airborneTorpedoHitsScoutPlane(Torpedo torpedo) {
        return allShips().stream()
                .filter(ship -> "active".equals(ship.state()))
                .filter(Ship::isScoutPlane)
                .filter(ship -> !ship.id().equals(torpedo.shipId()))
                .filter(ship -> torpedoAirSegmentHitsScoutPlane(torpedo, ship))
                .findFirst();
    }

    private boolean torpedoAirSegmentHitsScoutPlane(Torpedo torpedo, Ship plane) {
        double dx = torpedo.position().x() - torpedo.previousPosition().x();
        double dy = torpedo.y() - torpedo.previousY();
        double dz = torpedo.position().z() - torpedo.previousPosition().z();
        double segmentLength = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int samples = Math.max(1, (int) Math.ceil(segmentLength / AIRBORNE_TORPEDO_SWEEP_STEP));
        for (int index = 0; index <= samples; index += 1) {
            double t = (double) index / samples;
            double x = torpedo.previousPosition().x() + dx * t;
            double y = torpedo.previousY() + dy * t;
            double z = torpedo.previousPosition().z() + dz * t;
            if (pointHitsScoutPlane(x, y, z, plane)) {
                return true;
            }
        }
        return false;
    }

    private boolean torpedoVisibleInWorld(Torpedo torpedo) {
        return "running".equals(torpedo.state()) || "airborne".equals(torpedo.state());
    }

    private void recordTorpedoImpact(Torpedo torpedo, String reason, String targetShipId) {
        torpedoImpacts.add(new TorpedoImpactSnapshot(
                torpedo.id(),
                torpedo.teamId(),
                torpedo.shipId(),
                targetShipId,
                reason,
                MathSupport.round(torpedo.position().x()),
                MathSupport.round(torpedo.position().z()),
                MathSupport.round(torpedo.heading()),
                MathSupport.round(nowSeconds)
        ));
    }

    private Set<String> releasePendingBombs() {
        List<PendingBombRelease> dueReleases = pendingBombReleases.stream()
                .filter(release -> release.releaseAtSeconds() <= nowSeconds)
                .toList();
        if (dueReleases.isEmpty()) {
            return Set.of();
        }
        Set<String> releasedBombIds = new HashSet<>();
        pendingBombReleases.removeAll(dueReleases);
        dueReleases.forEach(release ->
                findShipById(release.shipId()).ifPresent(ship -> {
                    if (!"active".equals(ship.state()) || !ship.isScoutPlane()) {
                        return;
                    }
                    double planeHeading = ship.heading();
                    double planeSpeed = Math.min(SCOUT_PLANE_MAX_BOMB_HORIZONTAL_SPEED, Math.max(4, ship.speed() * 0.92));
                    double initialBombVerticalSpeed = MathSupport.clamp(
                            -ship.verticalSpeed(),
                            -SCOUT_PLANE_MAX_BOMB_INITIAL_UP_SPEED,
                            SCOUT_PLANE_MAX_BOMB_INITIAL_DOWN_SPEED
                    );
                    double heading = MathSupport.normalizeAngle(planeHeading + bombPatternHeadingJitter(release.index()));
                    double horizontalSpeed = Math.max(0, planeSpeed + bombPatternSpeedJitter(release.index()));
                    Vector2 forward = Vector2.fromHeading(planeHeading);
                    Vector2 right = rightFromHeading(planeHeading);
                    Vector2 position = ship.position()
                            .add(forward.scale(BOMB_DROP_FORWARD_OFFSET))
                            .add(right.scale(bombPatternOffset(release.index())));
                    double altitude = Math.max(0, ship.y() - BOMB_DROP_VERTICAL_OFFSET);
                    String bombId = "bomb-" + nextBombId++;
                    bombs.add(new Bomb(
                            bombId,
                            ship.teamId(),
                            ship.id(),
                            position,
                            altitude,
                            heading,
                            horizontalSpeed,
                            initialBombVerticalSpeed,
                            nowSeconds,
                            0
                    ));
                    releasedBombIds.add(bombId);
                })
        );
        return releasedBombIds;
    }

    private void updateBombs(double deltaSeconds) {
        updateBombs(deltaSeconds, Set.of());
    }

    private void updateBombs(double deltaSeconds, Set<String> releasedBombIds) {
        for (Bomb bomb : bombs) {
            if (releasedBombIds.contains(bomb.id())) {
                continue;
            }
            bomb.update(deltaSeconds);
            if (!"detonated".equals(bomb.state())) {
                continue;
            }

            allShips().stream()
                    .filter(ship -> "active".equals(ship.state()))
                    .filter(ship -> !ship.isScoutPlane())
                    .filter(ship -> !ship.id().equals(bomb.shipId()))
                    .filter(ship -> bombHitsShip(bomb, ship))
                    .findFirst()
                    .ifPresentOrElse(ship -> {
                        sinkShip(ship, shooterController(bomb.shipId()));
                        recordBombImpact(bomb, "ship-hit", ship.id());
                    }, () -> recordBombImpact(bomb, "sea-hit", null));
        }
    }

    private void updateFlakProjectiles(double deltaSeconds) {
        flakProjectiles.forEach(projectile -> {
            projectile.update(deltaSeconds);
        });
    }

    private void updateFlakTerrainImpacts(WorldMap worldMap) {
        double maxTerrainHeight = LandGeometry.maxTerrainHeight(worldMap) + 1.5;
        flakProjectiles.stream()
                .filter(projectile -> !projectile.hitResolved())
                .filter(projectile -> "flying".equals(projectile.state()) || (projectile.previousY() > 0 && projectile.y() <= 0))
                .forEach(projectile -> recordFlakTerrainImpact(projectile, worldMap, maxTerrainHeight)
                        .ifPresent(impact -> projectile.hit()));
    }

    private Optional<FlakImpactSnapshot> recordFlakTerrainImpact(FlakProjectile projectile, WorldMap worldMap, double maxTerrainHeight) {
        double dx = projectile.x() - projectile.previousX();
        double dy = projectile.y() - projectile.previousY();
        double dz = projectile.z() - projectile.previousZ();
        if (Math.min(projectile.previousY(), projectile.y()) <= maxTerrainHeight) {
            double segmentLength = Math.sqrt(dx * dx + dy * dy + dz * dz);
            int samples = Math.max(1, (int) Math.ceil(segmentLength / FLAK_SWEEP_STEP));
            for (int index = 1; index <= samples; index += 1) {
                double t = (double) index / samples;
                double x = projectile.previousX() + dx * t;
                double y = projectile.previousY() + dy * t;
                double z = projectile.previousZ() + dz * t;
                double terrainHeight = LandGeometry.terrainHeightAt(new Vector2(x, z), worldMap);
                if (terrainHeight > 0.02 && y <= terrainHeight) {
                    return Optional.of(recordFlakImpact(projectile, "land-hit", x, terrainHeight, z));
                }
            }
        }

        if (projectile.previousY() > 0 && projectile.y() <= 0) {
            double t = projectile.previousY() / (projectile.previousY() - projectile.y());
            double x = projectile.previousX() + dx * t;
            double z = projectile.previousZ() + dz * t;
            return Optional.of(recordFlakImpact(projectile, "water-hit", x, 0, z));
        }
        return Optional.empty();
    }

    private void updateFlakHits() {
        List<Ship> activeTargets = allShips().stream()
                .filter(ship -> "active".equals(ship.state()))
                .toList();

        flakProjectiles.stream()
                .filter(projectile -> !projectile.hitResolved())
                .filter(this::hasActiveFlakCollisionSegment)
                .forEach(projectile -> activeTargets.stream()
                        .filter(ship -> !ship.id().equals(projectile.shipId()))
                        .map(ship -> flakProjectileHitsTarget(projectile, ship))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .findFirst()
                        .ifPresent(hit -> {
                            if (hit.sinks()) {
                                sinkShip(hit.ship(), shooterController(projectile.shipId()));
                                recordProjectileHit(projectile, hit);
                            }
                            projectile.hit();
                            if (!hit.ship().isScoutPlane()) {
                                recordFlakImpact(projectile, hit.reason(), hit.x(), hit.y(), hit.z());
                            }
                        }));
    }

    private boolean hasActiveFlakCollisionSegment(FlakProjectile projectile) {
        return "flying".equals(projectile.state()) || (projectile.previousY() > 0 && projectile.y() <= 0);
    }

    private Optional<FlakTargetHit> flakProjectileHitsTarget(FlakProjectile projectile, Ship ship) {
        if (distanceToFlakSegment2D(projectile, ship.position()) > (ship.isScoutPlane() ? 9.0 * SCOUT_PLANE_MODEL_SCALE : 7.2 * TORPEDO_BOAT_MODEL_SCALE)) {
            return Optional.empty();
        }
        return ship.isScoutPlane()
                ? flakProjectileHitsScoutPlane(projectile, ship)
                : flakProjectileHitsShip(projectile, ship);
    }

    private double distanceToFlakSegment2D(FlakProjectile projectile, Vector2 point) {
        double ax = projectile.previousX();
        double az = projectile.previousZ();
        double bx = projectile.x();
        double bz = projectile.z();
        double dx = bx - ax;
        double dz = bz - az;
        double lengthSquared = dx * dx + dz * dz;
        double t = lengthSquared <= 0.0001
                ? 0
                : MathSupport.clamp(((point.x() - ax) * dx + (point.z() - az) * dz) / lengthSquared, 0, 1);
        double nearestX = ax + dx * t;
        double nearestZ = az + dz * t;
        double ox = point.x() - nearestX;
        double oz = point.z() - nearestZ;
        return Math.sqrt(ox * ox + oz * oz);
    }

    private Optional<FlakTargetHit> flakProjectileHitsScoutPlane(FlakProjectile projectile, Ship plane) {
        double dx = projectile.x() - projectile.previousX();
        double dy = projectile.y() - projectile.previousY();
        double dz = projectile.z() - projectile.previousZ();
        double segmentLength = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double sweepStep = isCannonProjectile(projectile) ? CANNON_SWEEP_STEP : FLAK_SWEEP_STEP;
        int samples = Math.max(1, (int) Math.ceil(segmentLength / sweepStep));
        for (int index = 0; index <= samples; index += 1) {
            double t = samples == 0 ? 1 : (double) index / samples;
            double x = projectile.previousX() + dx * t;
            double y = projectile.previousY() + dy * t;
            double z = projectile.previousZ() + dz * t;
            if (pointHitsScoutPlane(x, y, z, plane)) {
                return Optional.of(new FlakTargetHit(plane, "plane-hit", true, x, y, z));
            }
        }
        return Optional.empty();
    }

    private Optional<FlakTargetHit> flakProjectileHitsShip(FlakProjectile projectile, Ship ship) {
        double dx = projectile.x() - projectile.previousX();
        double dy = projectile.y() - projectile.previousY();
        double dz = projectile.z() - projectile.previousZ();
        double segmentLength = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double sweepStep = isCannonProjectile(projectile) ? CANNON_SWEEP_STEP : FLAK_SWEEP_STEP;
        int samples = Math.max(1, (int) Math.ceil(segmentLength / sweepStep));
        for (int index = 0; index <= samples; index += 1) {
            double t = samples == 0 ? 1 : (double) index / samples;
            double x = projectile.previousX() + dx * t;
            double y = projectile.previousY() + dy * t;
            double z = projectile.previousZ() + dz * t;
            if (isCannonProjectile(projectile) && pointHitsShipHullAtCannonHeight(x, y, z, ship)) {
                return Optional.of(new FlakTargetHit(ship, FlakShipHitArea.CRITICAL.reason(), true, x, y, z));
            }
            FlakShipHitArea area = shipFlakHitArea(x, y, z, ship);
            if (area != FlakShipHitArea.MISS) {
                if (isCannonProjectile(projectile)) {
                    return Optional.of(new FlakTargetHit(ship, FlakShipHitArea.CRITICAL.reason(), true, x, y, z));
                }
                return Optional.of(new FlakTargetHit(ship, area.reason(), area.sinks(), x, y, z));
            }
        }
        return Optional.empty();
    }

    private boolean isCannonProjectile(FlakProjectile projectile) {
        return projectile.id().startsWith("cannon-");
    }

    private boolean pointHitsShipHullAtCannonHeight(double x, double y, double z, Ship ship) {
        LocalHullPoint hit = localHullPoint(new Vector2(x, z), ship);
        if (!forwardInsideTorpedoBoatHull(hit.forward(), CANNON_HULL_MARGIN)) {
            return false;
        }
        double localY = localShipY(y, ship, TORPEDO_BOAT_MODEL_SCALE);
        double deckHeight = torpedoBoatDeckY(hit.forward()) + CANNON_HULL_DECK_MARGIN;
        return localY >= CANNON_HULL_MIN_Y
                && localY <= deckHeight
                && Math.abs(hit.right()) <= enemyHullHalfWidthAt(hit.forward()) + CANNON_HULL_MARGIN;
    }

    private FlakShipHitArea shipFlakHitArea(double x, double y, double z, Ship ship) {
        LocalHullPoint hit = localHullPoint(new Vector2(x, z), ship);
        double localY = localShipY(y, ship, TORPEDO_BOAT_MODEL_SCALE);
        double absRight = Math.abs(hit.right());
        if (localY >= 0.72 && localY <= 1.72 && hit.forward() >= 0.32 && hit.forward() <= 1.24 && absRight <= 0.62) {
            return FlakShipHitArea.CRITICAL;
        }
        if (localY >= 0.78 && localY <= 1.92 && hit.forward() >= -1.5 && hit.forward() <= 0.24 && absRight <= 0.26) {
            return FlakShipHitArea.CRITICAL;
        }
        double deckClearance = torpedoBoatDeckY(hit.forward()) + 0.025;
        if (localY >= 0.1 && localY <= deckClearance
                && forwardInsideTorpedoBoatHull(hit.forward(), FLAK_SHIP_HIT_MARGIN)
                && absRight <= enemyHullHalfWidthAt(hit.forward()) + FLAK_SHIP_HIT_MARGIN) {
            return FlakShipHitArea.SURFACE;
        }
        return FlakShipHitArea.MISS;
    }

    private double torpedoBoatDeckY(double forward) {
        double[][] sections = {
                {-4.2, 0.52},
                {-3.45, 0.54},
                {-2.25, 0.56},
                {-1.1, 0.57},
                {-0.22, 0.57},
                {-0.04, 0.74},
                {1.25, 0.74},
                {2.42, 0.74},
                {2.452, 0.74},
                {2.469, 0.74},
                {2.72, 0.736},
                {3.18, 0.73},
                {3.68, 0.73}
        };

        return interpolateSectionValue(sections, forward);
    }

    private double interpolateSectionValue(double[][] sections, double forward) {
        if (forward <= sections[0][0]) {
            return sections[0][1];
        }
        for (int i = 0; i < sections.length - 1; i += 1) {
            double currentForward = sections[i][0];
            double nextForward = sections[i + 1][0];
            if (forward <= nextForward) {
                double t = (forward - currentForward) / (nextForward - currentForward);
                return sections[i][1] + (sections[i + 1][1] - sections[i][1]) * t;
            }
        }
        return sections[sections.length - 1][1];
    }

    private boolean pointHitsScoutPlane(double x, double y, double z, Ship plane) {
        double dx = x - plane.position().x();
        double dz = z - plane.position().z();
        double right = (dx * Math.cos(plane.heading()) - dz * Math.sin(plane.heading())) / SCOUT_PLANE_MODEL_SCALE;
        double forward = (dx * Math.sin(plane.heading()) + dz * Math.cos(plane.heading())) / SCOUT_PLANE_MODEL_SCALE;
        double vertical = (y - plane.y()) / SCOUT_PLANE_MODEL_SCALE;
        double bank = MathSupport.clamp(
                -plane.turnVelocity() * SCOUT_PLANE_VISUAL_BANK_PER_TURN_RATE,
                -SCOUT_PLANE_MAX_VISUAL_BANK,
                SCOUT_PLANE_MAX_VISUAL_BANK
        );
        double cosBank = Math.cos(bank);
        double sinBank = Math.sin(bank);
        double modelRight = right * cosBank + vertical * sinBank;
        double modelVertical = -right * sinBank + vertical * cosBank;
        if (pointHitsScoutPlanePart(
                forward,
                modelRight,
                modelVertical,
                -SCOUT_PLANE_HALF_LENGTH,
                SCOUT_PLANE_HALF_LENGTH,
                SCOUT_PLANE_FUSELAGE_HALF_WIDTH
        )) {
            return true;
        }
        if (pointHitsScoutPlanePart(
                forward,
                modelRight,
                modelVertical,
                SCOUT_PLANE_WING_FORWARD_MIN,
                SCOUT_PLANE_WING_FORWARD_MAX,
                SCOUT_PLANE_WING_HALF_WIDTH
        )) {
            return true;
        }
        return pointHitsScoutPlanePart(
                forward,
                modelRight,
                modelVertical,
                SCOUT_PLANE_TAIL_FORWARD_MIN,
                SCOUT_PLANE_TAIL_FORWARD_MAX,
                SCOUT_PLANE_TAIL_HALF_WIDTH
        );
    }

    private boolean pointHitsScoutPlanePart(double forward, double right, double vertical,
                                            double forwardMin, double forwardMax, double halfWidth) {
        return forward >= forwardMin - SCOUT_PLANE_HIT_MARGIN
                && forward <= forwardMax + SCOUT_PLANE_HIT_MARGIN
                && Math.abs(right) <= halfWidth + SCOUT_PLANE_HIT_MARGIN
                && Math.abs(vertical) <= SCOUT_PLANE_VERTICAL_HALF_HEIGHT + SCOUT_PLANE_HIT_MARGIN;
    }

    private void recordProjectileHit(FlakProjectile projectile, FlakTargetHit hit) {
        projectileHits.add(new ProjectileHitSnapshot(
                projectile.id(),
                projectile.teamId(),
                projectile.shipId(),
                hit.ship().id(),
                MathSupport.round(hit.x()),
                MathSupport.round(hit.y()),
                MathSupport.round(hit.z()),
                MathSupport.round(nowSeconds)
        ));
    }

    private void recordClientPlaneHit(ClientPlaneHitRequest request, Ship shooter, Ship target) {
        String weaponPrefix = "cannon".equals(request.weaponType()) ? "cannon" : "flak";
        projectileHits.add(new ProjectileHitSnapshot(
                weaponPrefix + "-client-" + nextFlakProjectileId++,
                shooter.teamId(),
                shooter.id(),
                target.id(),
                MathSupport.round(request.x()),
                MathSupport.round(request.y()),
                MathSupport.round(request.z()),
                MathSupport.round(nowSeconds)
        ));
    }

    private FlakImpactSnapshot recordFlakImpact(FlakProjectile projectile, String reason, double x, double y, double z) {
        FlakImpactSnapshot impact = new FlakImpactSnapshot(
                projectile.id(),
                projectile.teamId(),
                projectile.shipId(),
                reason,
                MathSupport.round(x),
                MathSupport.round(y),
                MathSupport.round(z),
                MathSupport.round(nowSeconds)
        );
        flakImpacts.add(impact);
        return impact;
    }

    private record FlakTargetHit(Ship ship, String reason, boolean sinks, double x, double y, double z) {
    }

    private enum FlakShipHitArea {
        MISS("miss", false),
        SURFACE("ship-hit", false),
        CRITICAL("ship-critical-hit", true);

        private final String reason;
        private final boolean sinks;

        FlakShipHitArea(String reason, boolean sinks) {
            this.reason = reason;
            this.sinks = sinks;
        }

        String reason() {
            return reason;
        }

        boolean sinks() {
            return sinks;
        }
    }

    private boolean bombHitsShip(Bomb bomb, Ship ship) {
        if (ship.position().distanceTo(bomb.position()) > BOMB_HIT_RADIUS * TORPEDO_BOAT_MODEL_SCALE) {
            return false;
        }
        return pointHitsShipHull(bomb.position(), ship, BOMB_HULL_MARGIN);
    }

    private void recordBombImpact(Bomb bomb, String reason, String targetShipId) {
        bombImpacts.add(new BombImpactSnapshot(
                bomb.id(),
                bomb.teamId(),
                bomb.shipId(),
                targetShipId,
                reason,
                MathSupport.round(bomb.position().x()),
                MathSupport.round(bomb.position().z()),
                MathSupport.round(nowSeconds)
        ));
    }

    private boolean torpedoHitsLand(Torpedo torpedo, NavigationService navigationService, WorldMap worldMap) {
        double segmentLength = torpedo.previousPosition().distanceTo(torpedo.position());
        int samples = Math.max(1, (int) Math.ceil(segmentLength / TORPEDO_SWEEP_STEP));
        for (int index = 0; index <= samples; index += 1) {
            double t = samples == 0 ? 1 : (double) index / samples;
            Vector2 sample = new Vector2(
                    torpedo.previousPosition().x() + (torpedo.position().x() - torpedo.previousPosition().x()) * t,
                    torpedo.previousPosition().z() + (torpedo.position().z() - torpedo.previousPosition().z()) * t
            );
            if (navigationService.isTorpedoBlocked(sample, worldMap)) {
                return true;
            }
        }
        return false;
    }

    private boolean torpedoHitsShip(Torpedo torpedo, Ship ship) {
        if (ship.position().distanceTo(torpedo.position()) > TORPEDO_BROAD_PHASE_RADIUS * TORPEDO_BOAT_MODEL_SCALE
                && ship.position().distanceTo(torpedo.previousPosition()) > TORPEDO_BROAD_PHASE_RADIUS * TORPEDO_BOAT_MODEL_SCALE) {
            return false;
        }

        double segmentLength = torpedo.previousPosition().distanceTo(torpedo.position());
        int samples = Math.max(1, (int) Math.ceil(segmentLength / TORPEDO_SWEEP_STEP));
        for (int index = 0; index <= samples; index += 1) {
            double t = samples == 0 ? 1 : (double) index / samples;
            Vector2 sample = new Vector2(
                    torpedo.previousPosition().x() + (torpedo.position().x() - torpedo.previousPosition().x()) * t,
                    torpedo.previousPosition().z() + (torpedo.position().z() - torpedo.previousPosition().z()) * t
            );
            if (pointHitsShipHull(sample, ship, TORPEDO_HULL_MARGIN)) {
                return true;
            }
        }
        return false;
    }

    private boolean pointHitsShipHull(Vector2 point, Ship ship, double margin) {
        LocalHullPoint hit = localHullPoint(point, ship);
        if (!forwardInsideTorpedoBoatHull(hit.forward(), margin)) {
            return false;
        }

        return Math.abs(hit.right()) <= enemyHullHalfWidthAt(hit.forward()) + margin;
    }

    private void sinkShip(Ship ship) {
        sinkShip(ship, null);
    }

    private boolean sinkShip(Ship ship, String creditedPlayerId) {
        String sunkController = ship.controlledBy();
        Integer scoreDelta = isHumanController(creditedPlayerId)
                ? scoreDeltaFor(creditedPlayerId, ship.teamId())
                : null;
        if (!ship.sink(nowSeconds + RESPAWN_DELAY_SECONDS)) {
            return false;
        }
        if (ship.isScoutPlane()) {
            clearBotScoutPlaneAttackState(ship);
        }
        destroyedShipsByTeam.merge(ship.teamId(), 1, Integer::sum);
        if (scoreDelta != null) {
            killsByPlayer.merge(creditedPlayerId, scoreDelta, Integer::sum);
        }
        if (isHumanController(sunkController)) {
            killsByPlayer.merge(sunkController, SCORE_PLAYER_SUNK, Integer::sum);
        }
        Optional.ofNullable(fleets.get(ship.teamId())).ifPresent(fleet -> fleet.releaseShip(ship.id()));
        return true;
    }

    private void recordRamHit(Ship attacker, Ship target) {
        ramHits.add(new RamHitSnapshot(
                "ram-" + attacker.id() + "-" + target.id() + "-" + Math.round(nowSeconds * 1000),
                attacker.teamId(),
                attacker.id(),
                target.id(),
                MathSupport.round(target.position().x()),
                MathSupport.round(target.position().z()),
                MathSupport.round(nowSeconds)
        ));
    }

    private void clearBotScoutPlaneAttackState(Ship ship) {
        clearBotScoutPlaneFlyThrough(ship);
    }

    private int scoreDeltaFor(String creditedPlayerId, String sunkTeamId) {
        return teamIdForController(creditedPlayerId)
                .map(teamId -> teamId.equals(sunkTeamId) ? SCORE_FRIENDLY_SUNK : SCORE_ENEMY_SUNK)
                .orElse(SCORE_ENEMY_SUNK);
    }

    private Optional<String> teamIdForController(String controller) {
        return allShips().stream()
                .filter(ship -> controller.equals(ship.controlledBy()))
                .map(Ship::teamId)
                .findFirst();
    }

    private String shooterController(String shipId) {
        return allShips().stream()
                .filter(ship -> ship.id().equals(shipId))
                .map(Ship::controlledBy)
                .findFirst()
                .orElse(null);
    }

    private boolean isHumanController(String controller) {
        return controller != null && !controller.isBlank() && !"bot".equals(controller) && !"scenario".equals(controller);
    }

    private void respawnSunkShips(NavigationService navigationService, WorldMap worldMap, RadarService radarService) {
        allShips().stream()
                .filter(ship -> ship.isReadyToRespawn(nowSeconds))
                .forEach(ship -> {
                    prepareBotVehicleTypeForRespawn(ship);
                    Vector2 position = findRespawnPosition(ship, navigationService, worldMap, radarService);
                    double heading = MathSupport.normalizeAngle(angleTo(nearestLandCenter(position, worldMap).orElse(new Vector2(0, 0)), position) + Math.PI);
                    ship.respawn(position, heading, nowSeconds);
                });
    }

    private void prepareBotVehicleTypeForRespawn(Ship ship) {
        if (!ship.isBotControlled()) {
            return;
        }
        int desiredScoutPlanes = desiredBotScoutPlaneCount(ship.teamId());
        long currentScoutPlanesWithoutRespawningShip = teamShips(ship.teamId()).stream()
                .filter(candidate -> !candidate.id().equals(ship.id()))
                .filter(Ship::isScoutPlane)
                .filter(Ship::isBotControlled)
                .count();
        ship.vehicleType(currentScoutPlanesWithoutRespawningShip < desiredScoutPlanes
                ? VEHICLE_SCOUT_PLANE
                : VEHICLE_TORPEDO_BOAT);
    }

    private int desiredBotScoutPlaneCount(String teamId) {
        int humanTargets = activeHumanTargetsForScoutPlaneTeam(teamId).size();
        int desired = desiredBotScoutPlaneCountForHumanTargets(humanTargets);
        long humanControlledTeamShips = teamShips(teamId).stream()
                .filter(this::isHumanControlled)
                .count();
        int availableBotSlots = Math.max(0, teamShips(teamId).size() - (int) humanControlledTeamShips);
        return Math.min(desired, availableBotSlots);
    }

    private int desiredBotScoutPlaneCountForHumanTargets(int humanTargets) {
        if (humanTargets <= 0) {
            return BOT_SCOUT_PLANE_DESIRED_WITHOUT_HUMAN_TARGETS;
        }
        if (humanTargets <= 2) {
            return BOT_SCOUT_PLANE_DESIRED_WITH_ONE_OR_TWO_HUMAN_TARGETS;
        }
        return BOT_SCOUT_PLANE_DESIRED_WITH_MANY_HUMAN_TARGETS;
    }

    private List<Ship> teamShips(String teamId) {
        Fleet fleet = fleets.get(teamId);
        return fleet == null ? List.of() : fleet.ships();
    }

    Vector2 findRespawnPosition(Ship ship, NavigationService navigationService, WorldMap worldMap, RadarService radarService) {
        List<Vector2> candidates = respawnCandidates;
        if (candidates.isEmpty()) {
            return ship.position();
        }
        RespawnChoice firstDifferentCandidate = null;
        RespawnChoice firstNavigableCandidate = null;
        RespawnChoice bestFallbackCandidate = null;
        double bestFallbackScore = Double.NEGATIVE_INFINITY;
        RespawnChoice firstCandidate = null;
        int startIndex = nextRespawnCandidateIndex;
        for (int offset = 0; offset < candidates.size(); offset += 1) {
            int index = Math.floorMod(startIndex + offset, candidates.size());
            Vector2 candidate = candidates.get(index);
            RespawnChoice choice = new RespawnChoice(index, candidate);
            boolean sameAsLast = index == lastRespawnCandidateIndex;
            boolean blocked = navigationService.isShipBlocked(candidate, ship.heading(), worldMap);
            boolean activeShipsTooClose = activeShipsTooClose(candidate);
            double distanceToHumans = distanceToNearestHumanShip(candidate);
            boolean tooCloseToHuman = distanceToHumans <= radarService.range() + RESPAWN_HUMAN_RADAR_MARGIN;

            if (!sameAsLast && !blocked && !activeShipsTooClose && !tooCloseToHuman) {
                return selectRespawnChoice(choice, candidates.size());
            }
            if (!sameAsLast && !blocked) {
                double fallbackScore = distanceToHumans - (activeShipsTooClose ? radarService.range() * 2 : 0);
                if (fallbackScore > bestFallbackScore) {
                    bestFallbackScore = fallbackScore;
                    bestFallbackCandidate = choice;
                }
            }
            if (!sameAsLast && !blocked && firstNavigableCandidate == null) {
                firstNavigableCandidate = choice;
            }
            if (!sameAsLast && firstDifferentCandidate == null) {
                firstDifferentCandidate = choice;
            }
            if (firstCandidate == null) {
                firstCandidate = choice;
            }
        }
        RespawnChoice fallback = bestFallbackCandidate != null
                ? bestFallbackCandidate
                : firstNavigableCandidate != null
                ? firstNavigableCandidate
                : firstDifferentCandidate != null
                ? firstDifferentCandidate
                : firstCandidate;
        return selectRespawnChoice(fallback, candidates.size());
    }

    private Vector2 selectRespawnChoice(RespawnChoice choice, int candidateCount) {
        lastRespawnCandidateIndex = choice.index();
        nextRespawnCandidateIndex = Math.floorMod(choice.index() + 1, candidateCount);
        return choice.position();
    }

    private boolean activeShipsTooClose(Vector2 candidate) {
        return allShips().stream()
                .filter(ship -> "active".equals(ship.state()))
                .anyMatch(ship -> ship.position().distanceTo(candidate) < RESPAWN_MIN_SHIP_DISTANCE);
    }

    private double distanceToNearestHumanShip(Vector2 candidate) {
        return allShips().stream()
                .filter(ship -> "active".equals(ship.state()))
                .filter(ship -> !"bot".equals(ship.controlledBy()))
                .mapToDouble(ship -> ship.position().distanceTo(candidate))
                .min()
                .orElse(Double.POSITIVE_INFINITY);
    }

    private record RespawnChoice(int index, Vector2 position) {
    }

    private record ShipDistance(Ship ship, double distance) {
    }

    private record BotTorpedoSolution(Vector2 targetPosition, double heading, boolean canRelease) {
    }

    private boolean fireTorpedo(Ship ship, double cooldownSeconds, double headingOffsetRadians) {
        return fireTorpedo(ship, cooldownSeconds, headingOffsetRadians, null);
    }

    private boolean fireTorpedo(Ship ship, double cooldownSeconds, double headingOffsetRadians, Integer requestedTubeSide) {
        if (!ship.canFire(nowSeconds)) {
            return false;
        }

        ship.markFired(nowSeconds, cooldownSeconds);
        double heading = MathSupport.normalizeAngle(ship.heading() + headingOffsetRadians);
        int tubeSide = normalizeTubeSide(requestedTubeSide);
        Vector2 forward = Vector2.fromHeading(heading);
        Vector2 right = new Vector2(Math.cos(heading), -Math.sin(heading));
        Vector2 muzzlePosition = ship.position()
                .add(forward.scale(5.0 * TORPEDO_BOAT_MODEL_SCALE))
                .add(right.scale(tubeSide * 0.56 * TORPEDO_BOAT_MODEL_SCALE));
        if (torpedoLaunchWouldHitFriendlyShip(ship, muzzlePosition, heading)) {
            return false;
        }
        torpedoes.add(new Torpedo(
                "torpedo-" + nextTorpedoId++,
                ship.teamId(),
                ship.id(),
                muzzlePosition,
                heading,
                SHIP_TORPEDO_BASE_SPEED + Math.max(0, ship.speed()) * SHIP_TORPEDO_SPEED_GAIN,
                nowSeconds,
                RadarService.TORPEDO_RANGE,
                tubeSide
        ));
        return true;
    }

    private boolean torpedoLaunchWouldHitFriendlyShip(Ship shooter, Vector2 muzzlePosition, double heading) {
        Vector2 forward = Vector2.fromHeading(heading);
        Vector2 right = new Vector2(Math.cos(heading), -Math.sin(heading));
        double broadRadius = (TORPEDO_BROAD_PHASE_RADIUS + TORPEDO_HULL_MARGIN) * TORPEDO_BOAT_MODEL_SCALE;
        return allShips().stream()
                .filter(ship -> "active".equals(ship.state()))
                .filter(ship -> !ship.isScoutPlane())
                .filter(ship -> !ship.id().equals(shooter.id()))
                .filter(ship -> ship.teamId().equals(shooter.teamId()))
                .anyMatch(ship -> torpedoLaunchWouldHitFriendlyShip(shooter, muzzlePosition, forward, right, ship, broadRadius));
    }

    private boolean torpedoLaunchWouldHitFriendlyShip(Ship shooter, Vector2 muzzlePosition, Vector2 forward,
                                                     Vector2 right, Ship friendly, double broadRadius) {
        Vector2 toFriendly = friendly.position().subtract(muzzlePosition);
        double along = toFriendly.x() * forward.x() + toFriendly.z() * forward.z();
        if (along < 0 || along > BOT_FIRE_MAX_RANGE) {
            return false;
        }
        double lateral = Math.abs(toFriendly.x() * right.x() + toFriendly.z() * right.z());
        if (lateral > broadRadius) {
            return false;
        }

        double sweepStart = Math.max(0, along - broadRadius);
        double sweepEnd = Math.min(BOT_FIRE_MAX_RANGE, along + broadRadius);
        double sweepStep = Math.max(TORPEDO_SWEEP_STEP, TORPEDO_BOAT_MODEL_SCALE);
        for (double distance = sweepStart; distance <= sweepEnd; distance += sweepStep) {
            if (pointHitsShipHull(muzzlePosition.add(forward.scale(distance)), friendly, TORPEDO_HULL_MARGIN)) {
                return true;
            }
        }
        Vector2 endPoint = muzzlePosition.add(forward.scale(sweepEnd));
        return pointHitsShipHull(endPoint, friendly, TORPEDO_HULL_MARGIN);
    }

    private int normalizeTubeSide(Integer value) {
        if (value == null) {
            return 0;
        }
        if (value < 0) {
            return -1;
        }
        if (value > 0) {
            return 1;
        }
        return 0;
    }

    private boolean fireAirTorpedo(Ship plane, double cooldownSeconds, double heading) {
        heading = MathSupport.normalizeAngle(heading);
        Vector2 releasePosition = plane.position().add(Vector2.fromHeading(heading).scale(BOT_SCOUT_PLANE_AIR_TORPEDO_RELEASE_OFFSET));
        torpedoes.add(Torpedo.airDropped(
                "torpedo-" + nextTorpedoId++,
                plane.teamId(),
                plane.id(),
                releasePosition,
                heading,
                Math.max(0, plane.speed()),
                airTorpedoSpeed(plane),
                plane.y() - BOT_SCOUT_PLANE_AIR_TORPEDO_RELEASE_Y_OFFSET,
                plane.verticalSpeed(),
                nowSeconds,
                RadarService.TORPEDO_RANGE
        ));
        plane.markFired(nowSeconds, BOT_SCOUT_PLANE_POST_TORPEDO_BOMB_DELAY_SECONDS);
        nextBotScoutPlaneTorpedoTime = nowSeconds + cooldownSeconds;
        return true;
    }

    private boolean firePlayerAirTorpedo(Ship plane, double cooldownSeconds, double heading) {
        if (!plane.canDropBomb(nowSeconds)) {
            return false;
        }
        heading = MathSupport.normalizeAngle(heading);
        Vector2 releasePosition = plane.position().add(Vector2.fromHeading(heading).scale(BOT_SCOUT_PLANE_AIR_TORPEDO_RELEASE_OFFSET));
        torpedoes.add(Torpedo.airDropped(
                "torpedo-" + nextTorpedoId++,
                plane.teamId(),
                plane.id(),
                releasePosition,
                heading,
                Math.max(0, plane.speed()),
                airTorpedoSpeed(plane),
                plane.y() - BOT_SCOUT_PLANE_AIR_TORPEDO_RELEASE_Y_OFFSET,
                plane.verticalSpeed(),
                nowSeconds,
                RadarService.TORPEDO_RANGE
        ));
        plane.markFired(nowSeconds, cooldownSeconds);
        return true;
    }

    private double airTorpedoSpeed(Ship plane) {
        return BOT_SCOUT_PLANE_AIR_TORPEDO_BASE_SPEED + Math.max(0, plane.speed()) * BOT_SCOUT_PLANE_AIR_TORPEDO_SPEED_GAIN;
    }

    private void checkGameOver() {
        state = "running";
    }

    private List<Ship> allShips() {
        return fleets.values().stream()
                .flatMap(fleet -> fleet.ships().stream())
                .toList();
    }

    private Optional<Ship> findShipById(String shipId) {
        return allShips().stream()
                .filter(ship -> ship.id().equals(shipId))
                .findFirst();
    }

    private static Map<String, Fleet> createFleets(List<FleetSetup> fleetSetups) {
        Map<String, Fleet> fleets = new LinkedHashMap<>();
        fleetSetups.forEach(setup -> fleets.put(setup.teamId(), new Fleet(setup.teamId(), createShips(setup.ships()))));
        return fleets;
    }

    private static List<Ship> createShips(List<ShipSetup> shipSetups) {
        return shipSetups.stream()
                .map(GameSession::createShip)
                .toList();
    }

    private static Ship createShip(ShipSetup setup) {
        Ship ship = new Ship(
                setup.id(),
                setup.teamId(),
                setup.position(),
                MathSupport.normalizeAngle(setup.heading()),
                setup.controlledBy()
        );
        ship.applyCommand(setup.engineOrder(), setup.rudderDegrees());
        ship.vehicleType(setup.vehicleType());
        ship.y(setup.y());
        ship.nextFireTime(setup.nextFireDelaySeconds());
        return ship;
    }

    private double relativeBearing(Ship ship, Vector2 target) {
        return MathSupport.normalizeAngle(angleTo(target, ship.position()) - ship.heading());
    }

    private double angleTo(Vector2 target, Vector2 from) {
        return Math.atan2(target.x() - from.x(), target.z() - from.z());
    }

    private double angularDistance(double left, double right) {
        return Math.abs(MathSupport.normalizeAngle(left - right));
    }

    private double stablePhase(String value) {
        int hash = Math.abs(value.hashCode());
        return (hash % 6283) / 1000.0;
    }

    private static Vector2 rightFromHeading(double heading) {
        return new Vector2(Math.cos(heading), -Math.sin(heading));
    }

    private static double bombPatternOffset(int index) {
        double side = index % 2 == 0 ? -1.0 : 1.0;
        return side * BOMB_PATTERN_LATERAL_SPACING;
    }

    private static double bombPatternHeadingJitter(int index) {
        return (unitNoise(index * 97 + 31) - 0.5) * BOMB_PATTERN_HEADING_JITTER;
    }

    private static double bombPatternSpeedJitter(int index) {
        return (unitNoise(index * 83 + 47) - 0.5) * BOMB_PATTERN_SPEED_JITTER;
    }

    private static double unitNoise(int seed) {
        double value = Math.sin(seed * 12.9898) * 43758.5453;
        return value - Math.floor(value);
    }

    private record PendingBombRelease(String shipId, double releaseAtSeconds, int index) {
    }

    private record BotBombingSolution(Vector2 targetPosition, boolean canRelease) {
    }

}
