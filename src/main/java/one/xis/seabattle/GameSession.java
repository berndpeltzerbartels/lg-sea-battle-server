package one.xis.seabattle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class GameSession {

    private static final double TORPEDO_BROAD_PHASE_RADIUS = 6.2;
    private static final double TORPEDO_HULL_MARGIN = 0.28;
    private static final double TORPEDO_SWEEP_STEP = 1.15;
    private static final double BOMB_HIT_RADIUS = 5.0;
    private static final double BOMB_HULL_MARGIN = 0.18;
    private static final int BOMBS_PER_DROP = 5;
    private static final double BOMB_RELEASE_INTERVAL_SECONDS = 0.5;
    private static final double BOMB_DROP_COOLDOWN_SECONDS = 2.8;
    private static final double RAM_HIT_RADIUS = 4.8;
    private static final double RAM_BOW_OFFSET = 4.45;
    private static final double RAM_STERN_LENGTH = -4.05;
    private static final double RAM_BOW_LENGTH = 4.45;
    private static final double RAM_SIDE_FORWARD_MIN = -2.85;
    private static final double RAM_SIDE_FORWARD_MAX = 2.95;
    private static final double RAM_SIDE_MARGIN = 0.42;
    private static final double RAM_SIDE_ANGLE_TOLERANCE = Math.toRadians(45);
    private static final double RAM_GLANCING_COLLISION_ANGLE = Math.toRadians(30);
    private static final double RAM_GLANCING_HEADING_IMPULSE = Math.toRadians(9);
    private static final double BOT_FIRE_ARC = 0.16;
    private static final double BOT_CLOSE_FIRE_ARC = 1.15;
    private static final double BOT_CLOSE_FIRE_RANGE = 145;
    private static final double BOT_AIM_ERROR = 0.055;
    private static final double BOT_RAM_RANGE = 34;
    private static final double BOT_TORPEDO_EVADE_RANGE = 115;
    private static final double BOT_TORPEDO_LOOKOUT_FORWARD_OFFSET = 4.5;
    private static final double BOT_TORPEDO_LOOKOUT_ARC = 0.38;
    private static final double BOT_TORPEDO_INCOMING_ARC = 0.34;
    private static final double BOT_TORPEDO_THREAT_CORRIDOR = 8.0;
    private static final double BOT_RADAR_INTERCEPT_RANGE = 360;
    private static final double HUMAN_RADAR_RANGE = 945;
    private static final double BOT_HUMAN_TARGET_PRIORITY_CLEARANCE = HUMAN_RADAR_RANGE * 0.5;
    private static final double BOT_RETURN_TO_LAND_DISTANCE = 720;
    private static final double BOT_PATROL_LAND_DISTANCE = 470;
    private static final double BOT_ESCORT_JOIN_RANGE = 680;
    private static final double BOT_ESCORT_MIN_DISTANCE = 95;
    private static final double BOT_ESCORT_TARGET_DISTANCE = 150;
    private static final double BOT_GLANCING_RAM_BACKOFF_SECONDS = 2.85;
    private static final boolean SCOUT_PLANE_EXPERIMENT_PEACEFUL_BOTS = true;
    private static final double RESPAWN_DELAY_SECONDS = 8;
    private static final double RESPAWN_HUMAN_RADAR_MARGIN = 120;
    private static final double RESPAWN_MIN_SHIP_DISTANCE = 170;
    private static final double TORPEDO_IMPACT_VISIBILITY_SECONDS = 3.0;
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
    private final Map<String, Fleet> fleets;
    private final List<Vector2> respawnCandidates;
    private final Map<String, Integer> destroyedShipsByTeam = new LinkedHashMap<>();
    private final Map<String, Integer> killsByPlayer = new LinkedHashMap<>();
    private final List<Torpedo> torpedoes = new ArrayList<>();
    private final List<TorpedoImpactSnapshot> torpedoImpacts = new ArrayList<>();
    private final List<Bomb> bombs = new ArrayList<>();
    private final List<BombImpactSnapshot> bombImpacts = new ArrayList<>();
    private int nextTorpedoId = 1;
    private int nextBombId = 1;
    private int nextRespawnCandidateIndex;
    private int lastRespawnCandidateIndex = -1;
    private double nowSeconds;
    private String state = "running";

    GameSession(GameSetup setup) {
        this.id = setup.id();
        this.worldMap = setup.worldMap();
        this.fleets = createFleets(setup.fleets());
        this.respawnCandidates = List.copyOf(setup.respawnCandidates());
        this.fleets.keySet().forEach(teamId -> destroyedShipsByTeam.put(teamId, 0));
        LandGeometry.prepareRadarBlockingGrid(worldMap);
    }

    WorldMap worldMap() {
        return worldMap;
    }

    List<Vector2> respawnCandidates() {
        return respawnCandidates;
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
                        .filter(torpedo -> "running".equals(torpedo.state()))
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
            throw new IllegalArgumentException("Unknown team: " + update.teamId());
        }

        Optional<Ship> assignedShip = fleet.assignedShip(update.playerId());
        Ship ship = assignedShip
                .or(() -> fleet.assignNextShipToPlayer(update.playerId()))
                .orElseThrow(() -> new IllegalStateException("No active ship available for team: " + update.teamId()));
        ship.applyPlayerState(update, navigationService, worldMap);
    }

    public synchronized GameSnapshot fireTorpedo(FireTorpedoRequest request) {
        applyFireTorpedo(request);
        return snapshot();
    }

    public synchronized void applyFireTorpedo(FireTorpedoRequest request) {
        Fleet fleet = fleets.get(request.teamId());
        if (fleet == null) {
            throw new IllegalArgumentException("Unknown team: " + request.teamId());
        }

        Ship ship = fleet.assignedShip(request.playerId())
                .or(() -> fleet.assignNextShipToPlayer(request.playerId()))
                .orElseThrow(() -> new IllegalStateException("No active ship available for team: " + request.teamId()));
        if (ship.isScoutPlane() || "scout-plane".equals(request.vehicleType())) {
            return;
        }
        fireTorpedo(ship, 2.4, 0);
    }

    public synchronized GameSnapshot dropBomb(BombDropRequest request) {
        applyDropBomb(request);
        return snapshot();
    }

    public synchronized void applyDropBomb(BombDropRequest request) {
        Fleet fleet = fleets.get(request.teamId());
        if (fleet == null) {
            throw new IllegalArgumentException("Unknown team: " + request.teamId());
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

        ship.markFired(nowSeconds, BOMB_DROP_COOLDOWN_SECONDS);
        double heading = MathSupport.normalizeAngle(request.heading());
        double horizontalSpeed = Math.min(22, Math.max(4, request.speed() * 0.92));
        Vector2 forward = Vector2.fromHeading(heading);
        Vector2 baseDropPosition = new Vector2(request.x(), request.z()).add(forward.scale(2.6));
        for (int index = 0; index < BOMBS_PER_DROP; index += 1) {
            double releaseDelay = index * BOMB_RELEASE_INTERVAL_SECONDS;
            bombs.add(new Bomb(
                    "bomb-" + nextBombId++,
                    ship.teamId(),
                    ship.id(),
                    baseDropPosition.add(forward.scale(horizontalSpeed * releaseDelay)),
                    Math.min(120, Math.max(1, request.y())),
                    heading,
                    horizontalSpeed,
                    nowSeconds + releaseDelay,
                    releaseDelay
            ));
        }
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
        updateBombs(deltaSeconds);
        updateRamCollisions();
        respawnSunkShips(navigationService, worldMap, radarService);
        torpedoes.removeIf(torpedo -> !"running".equals(torpedo.state()));
        torpedoImpacts.removeIf(impact -> nowSeconds - impact.t() > TORPEDO_IMPACT_VISIBILITY_SECONDS);
        bombs.removeIf(bomb -> !"falling".equals(bomb.state()) && !"pending".equals(bomb.state()));
        bombImpacts.removeIf(impact -> nowSeconds - impact.t() > TORPEDO_IMPACT_VISIBILITY_SECONDS);
        checkGameOver();
    }

    public synchronized void update(double deltaSeconds) {
        throw new IllegalStateException("World navigation is required for server simulation");
    }

    private void commandBots(RadarService radarService, NavigationService navigationService, WorldMap worldMap) {
        if (radarService == null || worldMap == null) {
            return;
        }

        List<Ship> activeShips = allShips().stream()
                .filter(ship -> "active".equals(ship.state()))
                .filter(ship -> !ship.isScoutPlane())
                .toList();
        RadarService.VisibilityCache visibilityCache = radarService.visibilityCache(worldMap, activeShips);
        activeShips.stream()
                .filter(ship -> "bot".equals(ship.controlledBy()))
                .forEach(ship -> commandBot(ship, visibilityCache, navigationService, worldMap));
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
            ship.applyCommand(ENGINE_STOP, 0);
            return true;
        }

        if (blockedAhead) {
            double safeHeading = chooseSafeEscapeHeading(ship, navigationService, worldMap);
            int rudder = rudderTowardHeading(ship, safeHeading);
            ship.applyCommand(ENGINE_SLOW, rudder);
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
                .filter(target -> visibilityCache.isVisible(ship, target, targetRangeForBot(target)))
                .toList();
    }

    private double targetRangeForBot(Ship target) {
        return "bot".equals(target.controlledBy())
                ? RadarService.RADAR_RANGE
                : RadarService.HUMAN_TARGET_RANGE;
    }

    private Optional<Ship> chooseBotTarget(Ship ship, List<Ship> targets) {
        if (targets.isEmpty()) {
            return Optional.empty();
        }

        ShipDistance nearestHumanTarget = null;
        ShipDistance nearestBotTarget = null;
        ShipDistance nearestTarget = null;
        for (Ship target : targets) {
            ShipDistance candidate = new ShipDistance(target, ship.position().distanceTo(target.position()));
            if (nearestTarget == null || candidate.distance() < nearestTarget.distance()) {
                nearestTarget = candidate;
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

        return nearestTarget == null ? Optional.empty() : Optional.of(nearestTarget.ship());
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
        Optional<Ship> leader = activeTeamShips(ship.teamId()).stream()
                .filter(candidate -> !"bot".equals(candidate.controlledBy()))
                .min((left, right) -> Double.compare(
                        ship.position().distanceTo(left.position()),
                        ship.position().distanceTo(right.position())
                ));
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
        int engineOrder = botAttackEngineOrder(distance, targetBearing);
        applyBotCommand(ship, engineOrder, rudder, navigationService, worldMap);

        boolean closeInFront = distance <= BOT_CLOSE_FIRE_RANGE && Math.abs(targetBearing) <= BOT_CLOSE_FIRE_ARC;
        boolean aimedShot = distance >= 65 && distance <= 230 && Math.abs(steerError) <= BOT_FIRE_ARC;
        if (!SCOUT_PLANE_EXPERIMENT_PEACEFUL_BOTS && (closeInFront || aimedShot)) {
            fireTorpedo(ship, 10.5 + Math.abs(Math.sin(stablePhase(ship.id()))) * 3.0, aimError * 0.65);
        }
    }

    private int botAttackEngineOrder(double distance, double targetBearing) {
        double absoluteBearing = Math.abs(targetBearing);
        if (distance < BOT_RAM_RANGE) {
            return absoluteBearing <= Math.toRadians(30) ? ENGINE_ONE_THIRD : ENGINE_FULL;
        }
        if (distance < 130) {
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
        if (distance > 230) {
            return ENGINE_TWO_THIRDS;
        }
        return ENGINE_HALF;
    }

    private void updateRamCollisions() {
        List<Ship> activeShips = allShips().stream()
                .filter(ship -> "active".equals(ship.state()))
                .toList();
        for (int i = 0; i < activeShips.size(); i += 1) {
            Ship left = activeShips.get(i);
            for (int j = i + 1; j < activeShips.size(); j += 1) {
                Ship right = activeShips.get(j);

                RamImpact leftImpact = ramImpact(left, right);
                RamImpact rightImpact = ramImpact(right, left);
                if (!leftImpact.hits() && !rightImpact.hits()
                        && left.position().distanceTo(right.position()) > RAM_HIT_RADIUS) {
                    continue;
                }

                boolean headOnCollision = leftImpact.isBowHit() && rightImpact.isBowHit()
                        && angularDistance(left.heading(), right.heading()) > Math.toRadians(135);
                boolean glancingCollision = isGlancingCollision(left, right);
                if (headOnCollision) {
                    sinkShip(left, right.controlledBy());
                    sinkShip(right, left.controlledBy());
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
            sinkShip(right, left.controlledBy());
            left.stopAfterRamImpact();
        } else {
            sinkShip(left, right.controlledBy());
            right.stopAfterRamImpact();
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
                .add(Vector2.fromHeading(attacker.heading()).scale(bowOffset));
        LocalHullPoint hit = localHullPoint(attackerBow, target);
        if (hit.forward() < RAM_STERN_LENGTH - RAM_SIDE_MARGIN || hit.forward() > RAM_BOW_LENGTH + RAM_SIDE_MARGIN) {
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
                dx * Math.cos(ship.heading()) - dz * Math.sin(ship.heading()),
                dx * Math.sin(ship.heading()) + dz * Math.cos(ship.heading())
        );
    }

    private double enemyHullHalfWidthAt(double forward) {
        double[][] sections = {
                {-4.05, 0.39},
                {-2.3, 0.61},
                {1.55, 0.66},
                {3.25, 0.31},
                {4.45, 0.04}
        };

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

    private void updateBombs(double deltaSeconds) {
        for (Bomb bomb : bombs) {
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

    private boolean bombHitsShip(Bomb bomb, Ship ship) {
        if (ship.position().distanceTo(bomb.position()) > BOMB_HIT_RADIUS) {
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
        if (ship.position().distanceTo(torpedo.position()) > TORPEDO_BROAD_PHASE_RADIUS
                && ship.position().distanceTo(torpedo.previousPosition()) > TORPEDO_BROAD_PHASE_RADIUS) {
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
        if (hit.forward() < RAM_STERN_LENGTH - margin || hit.forward() > RAM_BOW_LENGTH + margin) {
            return false;
        }

        return Math.abs(hit.right()) <= enemyHullHalfWidthAt(hit.forward()) + margin;
    }

    private void sinkShip(Ship ship) {
        sinkShip(ship, null);
    }

    private void sinkShip(Ship ship, String creditedPlayerId) {
        String sunkController = ship.controlledBy();
        Integer scoreDelta = isHumanController(creditedPlayerId)
                ? scoreDeltaFor(creditedPlayerId, ship.teamId())
                : null;
        if (!ship.sink(nowSeconds + RESPAWN_DELAY_SECONDS)) {
            return;
        }
        destroyedShipsByTeam.merge(ship.teamId(), 1, Integer::sum);
        if (scoreDelta != null) {
            killsByPlayer.merge(creditedPlayerId, scoreDelta, Integer::sum);
        }
        if (isHumanController(sunkController)) {
            killsByPlayer.merge(sunkController, SCORE_PLAYER_SUNK, Integer::sum);
        }
        Optional.ofNullable(fleets.get(ship.teamId())).ifPresent(fleet -> fleet.releaseShip(ship.id()));
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
                    Vector2 position = findRespawnPosition(ship, navigationService, worldMap, radarService);
                    double heading = MathSupport.normalizeAngle(angleTo(nearestLandCenter(position, worldMap).orElse(new Vector2(0, 0)), position) + Math.PI);
                    ship.respawn(position, heading, nowSeconds);
                });
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

    private boolean fireTorpedo(Ship ship, double cooldownSeconds, double headingOffsetRadians) {
        if (!ship.canFire(nowSeconds)) {
            return false;
        }

        ship.markFired(nowSeconds, cooldownSeconds);
        double heading = MathSupport.normalizeAngle(ship.heading() + headingOffsetRadians);
        Vector2 muzzlePosition = ship.position().add(Vector2.fromHeading(heading).scale(5.0));
        torpedoes.add(new Torpedo(
                "torpedo-" + nextTorpedoId++,
                ship.teamId(),
                ship.id(),
                muzzlePosition,
                heading,
                24 + Math.max(0, ship.speed()) * 0.35,
                nowSeconds,
                RadarService.TORPEDO_RANGE
        ));
        return true;
    }

    private void checkGameOver() {
        state = "running";
    }

    private List<Ship> allShips() {
        return fleets.values().stream()
                .flatMap(fleet -> fleet.ships().stream())
                .toList();
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
}
