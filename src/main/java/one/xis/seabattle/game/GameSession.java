package one.xis.seabattle.game;

import static one.xis.seabattle.game.GameRules.*;

import java.util.*;

public final class GameSession {

    private final String id;
    private final String instanceId = java.util.UUID.randomUUID().toString();
    private final WorldMap worldMap;
    private final PeriscopeWarnings periscopeWarnings = new PeriscopeWarnings();
    final AircraftAttackWarnings aircraftWarnings = new AircraftAttackWarnings();
    final WorldMap scoutPlaneObstacleMap;
    private final Map<String, Fleet> fleets;
    private final List<Vector2> respawnCandidates;
    final Vector2 patrolCenter;
    private final Map<String, Integer> destroyedShipsByTeam = new LinkedHashMap<>();
    private final Map<String, Integer> killsByPlayer = new LinkedHashMap<>();
    private final Map<String, Double> nextFlakFireTimeByShipId = new LinkedHashMap<>();
    private final Map<String, Double> nextCannonFireTimeByShipId = new LinkedHashMap<>();
    final List<Torpedo> torpedoes = new ArrayList<>();
    private final List<Wreck> wrecks = new ArrayList<>();
    private long nextWreckId;
    private final Map<String, HullPose> previousWreckTargets = new HashMap<>();
    private double previousWreckCheckTime;
    private record PendingDepthCharge(String shipId, String playerId, int lane, double at) {}
    private final List<PendingDepthCharge> pendingDepthCharges = new ArrayList<>();
    private final List<DepthChargeSnapshot> depthCharges = new ArrayList<>();
    private static class DepthChargeReload {
        final double[] readyAt = new double[4];
        String queuedPlayer;
    }
    private final Map<String, DepthChargeReload> depthChargeReloads = new HashMap<>();
    private int nextDepthChargeId;
    static final double DEPTH_CHARGE_RADIUS = 24;
    static final double DEPTH_CHARGE_RELOAD = 4.8;
    static final double BOT_DEPTH_CHARGE_DETECTION_RADIUS = 6 * BotNavigation.SHIP_LENGTH;
    private double nextBotDepthChargeCheck;
    private final List<TorpedoImpactSnapshot> torpedoImpacts = new ArrayList<>();
    private final List<Bomb> bombs = new ArrayList<>();
    private final List<PendingBombRelease> pendingBombReleases = new ArrayList<>();
    private final List<BombImpactSnapshot> bombImpacts = new ArrayList<>();
    private final List<FlakProjectile> flakProjectiles = new ArrayList<>();
    private final List<FlakProjectileSnapshot> weaponShots = new ArrayList<>();
    private final List<ProjectileHitSnapshot> projectileHits = new ArrayList<>();
    private final List<FlakImpactSnapshot> flakImpacts = new ArrayList<>();
    private final List<RamHitSnapshot> ramHits = new ArrayList<>();
    private final Map<String, Double> ramImpactStopUntilSecondsByShipId = new LinkedHashMap<>();
    final Map<String, Integer> botScoutPlaneNonHumanAttackStreakByHumanTarget = new LinkedHashMap<>();
    private final Map<String, Map<String, Integer>> botScoutPlaneNonHumanAttackStreakByPlane = new LinkedHashMap<>();
    final Map<String, Vector2> botScoutPlaneFlyThroughTargets = new LinkedHashMap<>();
    final Map<String, Double> botScoutPlaneFlyThroughUntil = new LinkedHashMap<>();
    private int nextTorpedoId = 1;
    private int nextBombId = 1;
    private int nextFlakProjectileId = 1;
    private int nextRespawnCandidateIndex;
    private int lastRespawnCandidateIndex = -1;
    double nowSeconds;
    double nextBotScoutPlaneTorpedoTime;
    private String state = "running";

    private final BotCalculation botCalculation;
    private final BotTargetSelection targetSelection;
    private final int simulationThreads;
    final boolean parallelBotNavigation;
    GameSession(GameSetup setup) {
        this(setup, Integer.getInteger("seaBattle.simulationThreads", 1),
                Boolean.getBoolean("seaBattle.parallelBotNavigation"));
    }

    GameSession(GameSetup setup, int simulationThreads) {
        this(setup, simulationThreads, false);
    }

    GameSession(GameSetup setup, int simulationThreads, boolean parallelBotNavigation) {
        this(setup, simulationThreads, parallelBotNavigation, BotAlgorithms.calculation());
    }

    GameSession(GameSetup setup, int simulationThreads, boolean parallelBotNavigation, BotCalculation calculation) {
        this(setup, simulationThreads, parallelBotNavigation, calculation, BotAlgorithms.targetSelection());
    }

    GameSession(GameSetup setup, int simulationThreads, boolean parallelBotNavigation, BotCalculation calculation,
                BotTargetSelection targetSelection) {
        this.botCalculation = Objects.requireNonNull(calculation, "calculation");
        this.targetSelection = Objects.requireNonNull(targetSelection, "targetSelection");
        if (simulationThreads != 1 && simulationThreads != 2) {
            throw new IllegalArgumentException("simulationThreads must be 1 or 2");
        }
        this.simulationThreads = simulationThreads;
        this.parallelBotNavigation = parallelBotNavigation;
        this.id = setup.id();
        this.worldMap = setup.worldMap();
        this.scoutPlaneObstacleMap = LandGeometry.obstacleMapForMinimumTerrainHeight(
                worldMap,
                Math.min(BOT_SCOUT_PLANE_TORPEDO_ATTACK_Y, BOT_SCOUT_PLANE_BOMB_ATTACK_Y) - BOT_SCOUT_PLANE_TERRAIN_CLEARANCE
        );
        this.fleets = createFleets(setup.fleets());
        this.respawnCandidates = List.copyOf(setup.respawnCandidates());
        this.patrolCenter = mapCenter(worldMap, respawnCandidates);
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
        weaponShots.removeIf(shot -> nowSeconds - shot.firedAt() > 2);
        return new GameSnapshot(
                "state",
                id,
                state,
                MathSupport.round(nowSeconds),
                allShips().stream()
                        .filter(Vehicle::isVisibleInSnapshot)
                        .map(Vehicle::snapshot)
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
                List.copyOf(weaponShots),
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
                Map.copyOf(killsByPlayer),
                instanceId,
                List.copyOf(depthCharges),
                depthChargeControls(),
                periscopeWarnings.snapshot(),
                aircraftWarnings.snapshot(),
                targetSelection.submarineDetections(nowSeconds),
                wrecks.stream().map(Wreck::snapshot).toList()
        );
    }

    public synchronized GameSnapshot updatePlayerState(PlayerStateUpdate update, NavigationService navigationService, WorldMap worldMap) {
        applyPlayerState(update, navigationService, worldMap);
        return snapshot();
    }

    public synchronized void dropDepthCharges(String controller, String actor, String shipId) {
        Vehicle ship = findShipById(shipId).filter(s -> controller.equals(s.controlledBy())
                && "active".equals(s.state()) && !s.isSubmarine() && !s.isScoutPlane()).orElseThrow(
                        () -> new IllegalArgumentException("Kein aktives Torpedoboot."));
        var reload = depthChargeReloads.computeIfAbsent(shipId, ignored -> new DepthChargeReload());
        if (reload.queuedPlayer != null)
            throw new IllegalArgumentException("Eine weitere Serie ist bereits vorgemerkt.");
        if (hasPendingDepthCharges(shipId) || nowSeconds < reload.readyAt[0]) {
            reload.queuedPlayer = actor;
        } else {
            scheduleDepthChargeSalvo(ship, actor);
        }
        updateDepthCharges();
    }

    private boolean hasPendingDepthCharges(String shipId) {
        return pendingDepthCharges.stream().anyMatch(c -> c.shipId().equals(shipId));
    }

    private Map<String, DepthChargeControlSnapshot> depthChargeControls() {
        var controls = new HashMap<String, DepthChargeControlSnapshot>();
        depthChargeReloads.forEach((id, reload) -> controls.put(id, new DepthChargeControlSnapshot(
                hasPendingDepthCharges(id), reload.queuedPlayer != null, reload.readyAt[0])));
        return Map.copyOf(controls);
    }

    private void scheduleDepthChargeSalvo(Vehicle ship, String actor) {
        // Aim for a slightly overlapping diamond at steady forward speed. Limit waiting
        // when stopped/reversing; the release positions still follow the actual ship.
        double speed = Math.max(0, ship.speed());
        double separation = 36;
        double launcherOffset = 4.45 * TORPEDO_BOAT_MODEL_SCALE + speed * 0.6;
        double sideDelay = speed > 0 ? MathSupport.clamp((separation - launcherOffset) / speed, 0.1, 3) : 3;
        double sternDelay = speed > 0 ? MathSupport.clamp((separation + launcherOffset) / speed, 0.1, 6) : 6;
        pendingDepthCharges.add(new PendingDepthCharge(ship.id(), actor, 0, nowSeconds));
        pendingDepthCharges.add(new PendingDepthCharge(ship.id(), actor, 2, nowSeconds + sideDelay));
        pendingDepthCharges.add(new PendingDepthCharge(ship.id(), actor, 3, nowSeconds + sideDelay));
        pendingDepthCharges.add(new PendingDepthCharge(ship.id(), actor, 1, nowSeconds + sideDelay + sternDelay));
    }

    private void updateDepthCharges() {
        depthChargeReloads.entrySet().removeIf(entry -> {
            var ship = findShipById(entry.getKey()).filter(s -> "active".equals(s.state()));
            if (ship.isEmpty()) {
                pendingDepthCharges.removeIf(c -> c.shipId().equals(entry.getKey()));
                return true;
            }
            var reload = entry.getValue();
            if (reload.queuedPlayer != null && !hasPendingDepthCharges(entry.getKey()) && nowSeconds >= reload.readyAt[0]) {
                scheduleDepthChargeSalvo(ship.get(), reload.queuedPlayer);
                reload.queuedPlayer = null;
            }
            return reload.queuedPlayer == null && !hasPendingDepthCharges(entry.getKey())
                    && java.util.Arrays.stream(reload.readyAt).allMatch(t -> t <= nowSeconds);
        });
        var releases = pendingDepthCharges.iterator();
        while (releases.hasNext()) {
            var release = releases.next();
            var reload = depthChargeReloads.get(release.shipId());
            if (release.at() > nowSeconds || nowSeconds < reload.readyAt[release.lane()]) continue;
            releases.remove();
            findShipById(release.shipId()).filter(s -> "active".equals(s.state())).ifPresent(ship -> {
                Vector2 forward = Vector2.fromHeading(ship.heading());
                Vector2 right = new Vector2(Math.cos(ship.heading()), -Math.sin(ship.heading()));
                // Short free flight retains ship velocity; drag stops horizontal travel in water.
                boolean sideThrower = release.lane() >= 2;
                double side = release.lane() % 2 == 0 ? -1 : 1;
                Vector2 position = ship.position().add(forward.scale(
                                (sideThrower ? 0.1 : -4.35) * TORPEDO_BOAT_MODEL_SCALE
                                        + ship.speed() * (sideThrower ? 1.3 : 0.7)))
                        .add(right.scale(side * (sideThrower ? 24 : 0.225 * TORPEDO_BOAT_MODEL_SCALE)));
                reload.readyAt[release.lane()] = nowSeconds + DEPTH_CHARGE_RELOAD;
                depthCharges.add(new DepthChargeSnapshot("depth-charge-" + ++nextDepthChargeId, ship.id(), release.playerId(),
                        release.lane(), nowSeconds, nowSeconds + 2.5, position.x(), position.z(), ship.heading(),
                        DEPTH_CHARGE_RADIUS, false, List.of(), reload.readyAt[release.lane()]));
            });
        }
        for (int i = 0; i < depthCharges.size(); i++) {
            var charge = depthCharges.get(i);
            if (charge.exploded() || charge.explodesAt() > nowSeconds) continue;
            Vector2 center = new Vector2(charge.x(), charge.z());
            var targets = new ArrayList<String>();
            for (Vehicle target : allShips()) {
                if (target.isSubmarine() && "active".equals(target.state())
                        && target.position().distanceTo(center) <= charge.radius()
                        && sinkShip(target, charge.playerId())) targets.add(target.id());
            }
            depthCharges.set(i, new DepthChargeSnapshot(charge.id(), charge.shipId(), charge.playerId(), charge.lane(),
                    charge.releasedAt(), charge.explodesAt(), charge.x(), charge.z(), charge.heading(), charge.radius(), true, List.copyOf(targets), charge.readyAt()));
        }
        depthCharges.removeIf(c -> nowSeconds - c.explodesAt() > 3);
    }

    public synchronized void assignPlayerVehicle(String playerId, String teamId, String vehicleType) {
        Fleet fleet = fleets.get(teamId);
        if (fleet == null) {
            return;
        }
        var assigned = fleet.assignNextShipToPlayer(playerId, vehicleType);
        if (isTwoShipDuel() && assigned.isEmpty()) throw duelSlotUnavailable();
    }

    private boolean isTwoShipDuel() {
        return "two-ship-duel".equals(id);
    }

    private DuelSlotUnavailableException duelSlotUnavailable() {
        return new DuelSlotUnavailableException();
    }

    private Vehicle additionalPlayerShip(Fleet fleet, String player, Vector2 position, double heading, String vehicle, double y) {
        if (isTwoShipDuel()) throw duelSlotUnavailable();
        return fleet.assignAdditionalShipToPlayer(player, position, heading, vehicle, y);
    }

    public synchronized void addTeams(GameSetup setup) {
        for (FleetSetup fleet : setup.fleets()) {
            fleets.computeIfAbsent(fleet.teamId(), id -> new Fleet(id, createShips(fleet.ships())));
            destroyedShipsByTeam.putIfAbsent(fleet.teamId(), 0);
        }
    }

    public synchronized void restorePlayer(String player, String team, String vehicle, VehicleSnapshot previous, int score) {
        Fleet fleet = fleets.get(team);
        if (fleet == null) throw new IllegalArgumentException("Unknown team");
        if (isTwoShipDuel()) {
            fleet.assignNextShipToPlayer(player, vehicle).orElseThrow(this::duelSlotUnavailable);
        } else if (previous != null) {
            fleets.values().forEach(f -> f.removeUnoccupiedShipAt(previous));
            Vector2 position = new Vector2(previous.x(), previous.z());
            // A remaining crew keeps its hull; place a departing member alongside, never inside it.
            if (allShips().stream().anyMatch(s -> "active".equals(s.state()) && s.position().distanceTo(new Vector2(previous.x(), previous.z())) < 20)) {
                position = nearbyFreeReturnPosition(previous);
            }
            Vehicle ship = fleet.assignAdditionalShipToPlayer(player, position,
                    previous.heading(), vehicle, "submarine".equals(vehicle) ? previous.y() : 0);
            ship.applyCommand(previous.engineOrder(), previous.rudderDegrees());
        } else {
            fleet.assignNextShipToPlayer(player, vehicle);
        }
        killsByPlayer.put(player, score);
    }

    private Vector2 nearbyFreeReturnPosition(VehicleSnapshot previous) {
        var navigation = new NavigationService();
        for (int radius : new int[]{40, 60, 80}) {
            for (int direction = 0; direction < 8; direction++) {
                double angle = previous.heading() + Math.PI / 2 + direction * Math.PI / 4;
                var point = new Vector2(previous.x() + Math.sin(angle) * radius, previous.z() + Math.cos(angle) * radius);
                if (!navigation.isShipBlocked(point, previous.heading(), worldMap)
                        && allShips().stream().noneMatch(s -> "active".equals(s.state()) && s.position().distanceTo(point) < 35)) return point;
            }
        }
        throw new IllegalArgumentException("Am bisherigen Ort ist kein freier Platz fuer ein weiteres Schiff.");
    }

    public synchronized void restorePersonalScore(String player, int score) {
        killsByPlayer.put(player, score);
    }

    public synchronized void applyPlayerState(PlayerStateUpdate update, NavigationService navigationService, WorldMap worldMap) {
        Fleet fleet = fleets.get(update.teamId());
        if (fleet == null) {
            return;
        }

        Optional<Vehicle> assignedShip = fleet.assignedShip(update.playerId())
                .map(current -> fleet.changeVehicleType(current, update.vehicleType()));
        Vehicle ship = assignedShip
                .or(() -> fleet.assignNextShipToPlayer(update.playerId(), update.vehicleType()))
                .orElseGet(() -> additionalPlayerShip(fleet,
                        update.playerId(),
                        new Vector2(update.x(), update.z()),
                        update.heading(),
                        update.vehicleType(),
                        update.y()
                ));
        ship.applyPlayerState(update, navigationService, worldMap);
        applyPendingRamImpactStop(ship);
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

        Vehicle ship = fleet.assignedShip(request.playerId())
                .or(() -> fleet.assignNextShipToPlayer(request.playerId(), request.vehicleType()))
                .orElseGet(() -> additionalPlayerShip(fleet,
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

        Vehicle ship = fleet.assignedShip(request.playerId())
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

    void dropBombsFromScoutPlane(Vehicle ship, double cooldownSeconds) {
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
        applyFireFlak(request, request.playerId());
    }

    public synchronized void applyFireFlak(FlakFireRequest request, String shooterPlayerId) {
        Fleet fleet = fleets.get(request.teamId());
        if (fleet == null) {
            return;
        }

        Vehicle ship = fleet.assignedShip(request.playerId())
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
                nowSeconds, shooterPlayerId
        ));
        weaponShots.add(flakProjectiles.get(flakProjectiles.size() - 1).snapshot());
    }

    public synchronized void applyFireCannon(FlakFireRequest request) {
        applyFireCannon(request, request.playerId());
    }

    public synchronized void applyFireCannon(FlakFireRequest request, String shooterPlayerId) {
        Fleet fleet = fleets.get(request.teamId());
        if (fleet == null) {
            return;
        }

        Vehicle ship = fleet.assignedShip(request.playerId())
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
                nowSeconds, shooterPlayerId
        ));
        weaponShots.add(flakProjectiles.get(flakProjectiles.size() - 1).snapshot());
    }

    public synchronized void applyClientPlaneHit(ClientPlaneHitRequest request) {
        Fleet fleet = fleets.get(request.teamId());
        if (fleet == null) {
            return;
        }

        Vehicle shooter = fleet.assignedShip(request.playerId())
                .orElseThrow(() -> new IllegalStateException("No active ship available for team: " + request.teamId()));
        if (shooter.isScoutPlane() || !shooter.id().equals(request.shipId())) {
            return;
        }

        Optional<Vehicle> target = allShips().stream()
                .filter(ship -> ship.id().equals(request.targetShipId()))
                .findFirst();
        if (target.isEmpty() || !target.get().isScoutPlane() || !"active".equals(target.get().state())) {
            return;
        }

        if (sinkShip(target.get(), shooterController(shooter.id()))) {
            recordClientPlaneHit(request, shooter, target.get());
        }
    }

    private boolean canFireFlak(Vehicle ship) {
        return ship.isOnSurface() && nowSeconds >= nextFlakFireTimeByShipId.getOrDefault(ship.id(), 0.0);
    }

    private boolean canFireCannon(Vehicle ship) {
        return nowSeconds >= nextCannonFireTimeByShipId.getOrDefault(ship.id(), 0.0);
    }

    private boolean hasShotVelocity(FlakFireRequest request) {
        double squaredLength = request.vx() * request.vx() + request.vy() * request.vy() + request.vz() * request.vz();
        return squaredLength > 0.000001;
    }

    public synchronized void releasePlayer(String playerId) {
        fleets.values().forEach(fleet -> fleet.releasePlayer(playerId));
    }

    public synchronized void transferCrewController(String previous, String next, String team) {
        Fleet fleet = fleets.get(team);
        if (fleet != null) fleet.transferController(previous, next);
    }

    public synchronized void aimCrewWeapon(String player, String team, String station, double yaw, double pitch) {
        Fleet fleet = fleets.get(team);
        if (fleet == null) return;
        fleet.assignedShip(player).ifPresent(ship -> {
            if (station.equals("flak")) ship.flakAim(yaw, pitch);
            else ship.cannonAim(yaw, pitch);
        });
    }

    public synchronized void update(double deltaSeconds, RadarService radarService, NavigationService navigationService, WorldMap worldMap) {
        if (!"running".equals(state)) {
            return;
        }
        nowSeconds += deltaSeconds;
        // Refill fleet slots before combat. Deaths during this tick are replaced
        // on the next tick, without waiting for their visual wrecks to disappear.
        respawnSunkShips(navigationService, worldMap, radarService);
        wrecks.removeIf(wreck -> nowSeconds - wreck.sunkAt() >= WRECK_VISIBILITY_SECONDS);
        updateDepthCharges();
        if (!TEMPORARILY_DISABLE_BOTS) {
            long botTickId = (long) Math.floor(nowSeconds * 10 + 1e-6);
            BotRound round = prepareBotRound(radarService, navigationService, worldMap);
            botCalculation.calculate(new BotSituation(this, radarService, navigationService, worldMap, round),
                    botTickId, round.goals());
            if (simulationThreads == 1) {
                allShips().stream()
                        .filter(this::isMotionIntegratedOnServer)
                        .forEach(ship -> {
                            applyPendingRamImpactStop(ship);
                            ship.update(deltaSeconds, navigationService, worldMap);
                        });
            } else {
                List<Vehicle> movingShips = allShips().stream().filter(this::isMotionIntegratedOnServer).toList();
                movingShips.forEach(this::applyPendingRamImpactStop);
                // Only independent movement runs concurrently; combat reads the completed round.
                TwoThreadSimulation.forEach(movingShips, ship -> ship.update(deltaSeconds, navigationService, worldMap));
            }
        }
        updateTorpedoes(deltaSeconds, navigationService, worldMap);
        Set<String> releasedBombIds = releasePendingBombs();
        updateBombs(deltaSeconds, releasedBombIds);
        updateFlakProjectiles(deltaSeconds);
        updateFlakHits();
        updateFlakTerrainImpacts(worldMap);
        updateRamCollisions(navigationService);
        updateWreckCollisions();
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

    public synchronized void finish() {
        state = "finished";
    }

    public synchronized void updateIdle(double deltaSeconds, RadarService radarService, NavigationService navigationService, WorldMap worldMap) {
        if (!"running".equals(state)) {
            return;
        }
        nowSeconds += deltaSeconds;
        respawnSunkShips(navigationService, worldMap, radarService);
        wrecks.removeIf(wreck -> nowSeconds - wreck.sunkAt() >= WRECK_VISIBILITY_SECONDS);
        updateDepthCharges();
        updateTorpedoes(deltaSeconds, navigationService, worldMap);
        Set<String> releasedBombIds = releasePendingBombs();
        updateBombs(deltaSeconds, releasedBombIds);
        updateFlakProjectiles(deltaSeconds);
        updateFlakHits();
        updateFlakTerrainImpacts(worldMap);
        updateWreckCollisions();
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

    private boolean isMotionIntegratedOnServer(Vehicle ship) {
        return ship.isServerSimulated() || (!ship.isScoutPlane() && isHumanController(ship.controlledBy()));
    }

    double calculationTime() { return nowSeconds; }

    List<Vehicle> calculationShips() { return allShips(); }

    record BotIntent(Vehicle contact, boolean routeUpdated, BotTargetSituation situation) {}
    record BotRound(List<Vehicle> activeVehicles, List<Vehicle> waterVehicles, RadarService.VisibilityCache cache,
                    Map<Vehicle, BotIntent> boats, Map<Vehicle, Vehicle> planes, Map<String, BotGoal> goals) {}

    private BotRound prepareBotRound(RadarService radarService, NavigationService navigationService, WorldMap worldMap) {
        if ("side-view-sandbox".equals(id)) {
            return new BotRound(List.of(), List.of(), null, Map.of(), Map.of(), Map.of());
        }
        if (radarService == null || worldMap == null) {
            return new BotRound(List.of(), List.of(), null, Map.of(), Map.of(), Map.of());
        }

        List<Vehicle> activeShips = allShips().stream()
                .filter(ship -> "active".equals(ship.state()))
                .toList();
        List<Vehicle> surfaceShips = activeShips.stream()
                .filter(ship -> !ship.isScoutPlane())
                .toList();
        List<Vehicle> humanSurfaceShips = surfaceShips.stream()
                .filter(this::isHumanControlled)
                .toList();
        RadarService.VisibilityCache visibilityCache = radarService.visibilityCache(worldMap, activeShips);
        periscopeWarnings.update(nowSeconds, humanSurfaceShips, visibilityCache, worldMap);
        aircraftWarnings.expire(nowSeconds);
        boolean checkDepthCharges = nowSeconds >= nextBotDepthChargeCheck;
        if (checkDepthCharges) nextBotDepthChargeCheck = nowSeconds + .5;
        // One fleet pass per round, not another fleet scan for each selecting bot.
        Map<String, Map<String, Integer>> waterTargetReservations = new HashMap<>();
        for (Vehicle vehicle : surfaceShips) {
            SurfaceVehicle boat = (SurfaceVehicle) vehicle;
            if (!boat.isBotControlled()) continue;
            Map<String, Integer> assigned = waterTargetReservations.computeIfAbsent(boat.teamId(), ignored -> new HashMap<>());
            if (hasAssignedWaterTarget(boat)) assigned.merge(boat.botNavigation.target.id(), 1, Integer::sum);
        }
        Map<String, Integer> scoutPlaneTargetReservations = new LinkedHashMap<>();
        Map<Vehicle, BotIntent> boats = new LinkedHashMap<>();
        Map<Vehicle, Vehicle> planes = new LinkedHashMap<>();
        for (Vehicle ship : activeShips) {
            if (!ship.isBotControlled()) continue;
            if (ship.isScoutPlane()) {
                Vehicle target = selectBotScoutPlaneTarget(ship, activeShips, scoutPlaneTargetReservations, visibilityCache).orElse(null);
                planes.put(ship, target);
                if (target != null) scoutPlaneTargetReservations.merge(target.id(), 1, Integer::sum);
            } else {
                if (checkDepthCharges) tryBotDepthCharges(ship, visibilityCache);
                boats.put(ship, prepareBotIntent(ship, visibilityCache, waterTargetReservations.get(ship.teamId())));
            }
        }
        Map<String, BotGoal> goals = new LinkedHashMap<>();
        for (var entry : boats.entrySet()) {
            Vehicle ship = entry.getKey();
            BotIntent intent = entry.getValue();
            goals.put(ship.id(), targetSelection.goal(intent.situation(), intent.contact(), intent.routeUpdated()));
        }
        for (var entry : planes.entrySet()) {
            Vehicle target = entry.getValue(), plane = entry.getKey();
            goals.put(plane.id(), targetSelection.goal(new BotTargetSituation(this, plane, List.of(), visibilityCache,
                    scoutPlaneTargetReservations), target, true));
        }
        return new BotRound(activeShips, surfaceShips, visibilityCache, boats, planes, Map.copyOf(goals));
    }

    private void tryBotDepthCharges(Vehicle ship, RadarService.VisibilityCache visibilityCache) {
        if (ship.isSubmarine()) return;
        double strength = 0;
        Vehicle sonarContact = null;
        for (Vehicle target : visibilityCache.candidates(ship, BOT_DEPTH_CHARGE_DETECTION_RADIUS)) {
            if (!"active".equals(target.state()) || target.teamId().equals(ship.teamId())
                    || !target.isSubmarine() || target.isOnSurface() || target.y() >= -1) continue;
            double distance = ship.position().distanceTo(target.position());
            double echo = 1 - distance / BOT_DEPTH_CHARGE_DETECTION_RADIUS;
            if (echo > strength) { strength = echo; sonarContact = target; }
        }
        double previous = ship.surface().botNavigation.previousSubmarineEcho;
        ship.surface().botNavigation.previousSubmarineEcho = strength;
        // Like the visible echo meter: strongest at the closest pass, independent of speed.
        // No signal, first contact, steady or growing signal: do not drop.
        if (strength <= 0 || previous <= 0 || strength >= previous - 1e-6) return;
        if (hasPendingDepthCharges(ship.id())) return;
        var reload = depthChargeReloads.get(ship.id());
        if (reload != null && (reload.queuedPlayer != null
                || java.util.Arrays.stream(reload.readyAt).anyMatch(t -> nowSeconds < t))) return;
        dropDepthCharges(ship.controlledBy(), ship.controlledBy(), ship.id());
        if (sonarContact instanceof Submarine submarine) {
            targetSelection.depthChargeContact(new BotTargetSituation(this, ship, List.of(), visibilityCache, Map.of()),
                    submarine, (long) Math.floor(nowSeconds * 10 + 1e-6));
        }
    }

    private Optional<Vehicle> selectBotScoutPlaneTarget(Vehicle plane, List<Vehicle> activeShips,
                                                     Map<String, Integer> targetReservations, RadarService.VisibilityCache visibilityCache) {
        List<Vehicle> candidates = activeShips.stream()
                .filter(ship -> !ship.teamId().equals(plane.teamId()))
                .filter(ship -> !ship.isScoutPlane())
                .filter(ship -> !ship.isFullySubmerged())
                .filter(ship -> !ship.isAtPeriscopeDepth())
                .toList();
        long tick = (long) Math.floor(nowSeconds * 10 + 1e-6);
        BotTargetSituation situation = new BotTargetSituation(this, plane, candidates, visibilityCache, targetReservations);
        targetSelection.observe(situation, tick);
        return Optional.ofNullable(situation.validate(targetSelection.select(situation, tick)));
    }

    boolean isHumanControlled(Vehicle ship) {
        return isHumanController(ship.controlledBy());
    }

    void recordBotScoutPlaneAttack(Vehicle plane, Vehicle target) {
        if (isHumanControlled(target)) {
            clearBotScoutPlaneNonHumanAttackStreakForHuman(plane.teamId(), target.id());
            return;
        }
        activeHumanTargetsForScoutPlaneTeam(plane.teamId()).forEach(human -> {
            String key = scoutPlaneHumanAttackStreakKey(plane.teamId(), human.id());
            botScoutPlaneNonHumanAttackStreakByHumanTarget.merge(key, 1, Integer::sum);
            botScoutPlaneNonHumanAttackStreakByPlane
                    .computeIfAbsent(plane.id(), ignored -> new LinkedHashMap<>())
                    .merge(key, 1, Integer::sum);
        });
    }

    private void clearBotScoutPlaneNonHumanAttackStreakForHuman(String attackingTeamId, String humanShipId) {
        String key = scoutPlaneHumanAttackStreakKey(attackingTeamId, humanShipId);
        botScoutPlaneNonHumanAttackStreakByHumanTarget.remove(key);
        botScoutPlaneNonHumanAttackStreakByPlane.values()
                .forEach(streaks -> streaks.remove(key));
        botScoutPlaneNonHumanAttackStreakByPlane.entrySet()
                .removeIf(entry -> entry.getValue().isEmpty());
    }

    List<Vehicle> activeHumanTargetsForScoutPlaneTeam(String teamId) {
        return allShips().stream()
                .filter(candidate -> "active".equals(candidate.state()))
                .filter(candidate -> !candidate.teamId().equals(teamId))
                .filter(candidate -> !candidate.isScoutPlane())
                .filter(this::isHumanControlled)
                .toList();
    }

    String scoutPlaneHumanAttackStreakKey(String attackingTeamId, String humanShipId) {
        return attackingTeamId + ">" + humanShipId;
    }

    private BotIntent prepareBotIntent(Vehicle ship, RadarService.VisibilityCache visibilityCache, Map<String, Integer> assignedTargets) {
        BotNavigation plan = ship.surface().botNavigation;
        long tick = (long) Math.floor(nowSeconds * 10 + 1e-6);
        boolean targetSelectionDue = plan.targetDue(tick);
        List<Vehicle> targets = targetSelectionDue ? visibleTargets(ship, visibilityCache) : List.of();
        BotTargetSituation situation = new BotTargetSituation(this, ship, targets, visibilityCache, Map.of(), assignedTargets);
        if (targetSelectionDue) {
            targetSelection.observe(situation, tick);
            Vehicle nextTarget = situation.validate(targetSelection.select(situation, tick));
            if (nextTarget != plan.target || nextTarget != null && nextTarget.lifeId() != plan.targetLife) {
                plan.trackingPeriscope = false;
            }
            if (hasAssignedWaterTarget(ship.surface())) {
                String previousId = plan.target.id();
                int count = assignedTargets.getOrDefault(previousId, 0);
                if (count <= 1) assignedTargets.remove(previousId);
                else assignedTargets.put(previousId, count - 1);
            }
            plan.target = nextTarget;
            plan.targetLife = plan.target == null ? -1 : plan.target.lifeId();
            if (hasAssignedWaterTarget(ship.surface())) assignedTargets.merge(plan.target.id(), 1, Integer::sum);
        }
        Vehicle contact = observedBotTarget(ship, plan, visibilityCache);
        return new BotIntent(contact, plan.routeDue(tick), situation);
    }

    private static boolean hasAssignedWaterTarget(SurfaceVehicle vehicle) {
        Vehicle target = vehicle.botNavigation.target;
        return target != null && target.lifeId() == vehicle.botNavigation.targetLife
                && "active".equals(target.state()) && !target.teamId().equals(vehicle.teamId());
    }

    private Vehicle observedBotTarget(Vehicle ship, BotNavigation plan, RadarService.VisibilityCache cache) {
        Vehicle target = plan.target;
        if (target == null || target.lifeId() != plan.targetLife || !"active".equals(target.state())) {
            plan.trackingPeriscope = false;
            return null;
        }
        if (ship.isFullySubmerged()) {
            plan.trackingPeriscope = false;
            return isUnderwaterBotContact(ship, target) && target.isFullySubmerged() ? target : null;
        }
        // Forget tracking as soon as the mast submerges or leaves range, not when the search timer expires.
        plan.trackingPeriscope = isVisiblePeriscopeRamContact(ship, target, plan.trackingPeriscope);
        return isStrategicHumanContact(ship, target) || cache.isVisible(ship, target, RadarService.RADAR_RANGE)
                || plan.trackingPeriscope ? target : null;
    }

    private static Vector2 mapCenter(WorldMap map, List<Vector2> spawns) {
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (Landmass land : map.landmasses()) {
            minX = Math.min(minX, land.x() - Math.abs(land.rx()));
            maxX = Math.max(maxX, land.x() + Math.abs(land.rx()));
            minZ = Math.min(minZ, land.z() - Math.abs(land.rz()));
            maxZ = Math.max(maxZ, land.z() + Math.abs(land.rz()));
        }
        if (map.landmasses().isEmpty()) {
            for (Vector2 spawn : spawns) {
                minX = Math.min(minX, spawn.x()); maxX = Math.max(maxX, spawn.x());
                minZ = Math.min(minZ, spawn.z()); maxZ = Math.max(maxZ, spawn.z());
            }
        }
        return Double.isFinite(minX) ? new Vector2((minX + maxX) / 2, (minZ + maxZ) / 2) : new Vector2(0, 0);
    }

    boolean submarineHasAscentShot(Vehicle ship, Vehicle target) {
        if (Math.abs(relativeBearing(ship, target.position())) > BOT_FIRE_ARC) return false;
        Vector2 muzzle = torpedoMuzzlePosition(ship, ship.heading(), 1);
        return torpedoLineHitsShip(muzzle, Vector2.fromHeading(ship.heading()), target,
                BOT_FIRE_MAX_RANGE, TORPEDO_HULL_MARGIN)
                && !torpedoLaunchWouldHitFriendlyShip(ship, muzzle, ship.heading());
    }

    static double botPeriscopeRamDetectionRange(Vehicle target) {
        double speed=Math.abs(target.speed());
        double lengths=PERISCOPE_SIGHT_LENGTHS[0];
        if (speed <= PERISCOPE_SIGHT_SPEEDS[0]) return BOT_PERISCOPE_RAM_SHIP_LENGTH*lengths/BOT_PERISCOPE_AHEAD_FACTOR;
        for(int i=1;i<PERISCOPE_SIGHT_SPEEDS.length;i++) {
            lengths=mix(PERISCOPE_SIGHT_LENGTHS[i-1],PERISCOPE_SIGHT_LENGTHS[i],
                    (speed-PERISCOPE_SIGHT_SPEEDS[i-1])/(PERISCOPE_SIGHT_SPEEDS[i]-PERISCOPE_SIGHT_SPEEDS[i-1]));
            if(speed<=PERISCOPE_SIGHT_SPEEDS[i])break;
        }
        // This base range is multiplied by the bearing factor at the observer.
        return BOT_PERISCOPE_RAM_SHIP_LENGTH*lengths/BOT_PERISCOPE_AHEAD_FACTOR;
    }

    private static double mix(double start, double end, double ratio) {
        return start + (end - start) * MathSupport.clamp(ratio, 0, 1);
    }

    private List<Vehicle> visibleTargets(Vehicle ship, RadarService.VisibilityCache visibilityCache) {
        if (ship.isFullySubmerged()) {
            return visibilityCache.candidates(ship, GameRules.BOT_SUBMARINE_UNDERWATER_RANGE).stream()
                    .filter(target -> !target.teamId().equals(ship.teamId()))
                    .filter(target -> isUnderwaterBotContact(ship, target) && target.isFullySubmerged())
                    .toList();
        }
        return visibilityCache.candidates(ship, RadarService.HUMAN_TARGET_RANGE).stream()
                .filter(target -> !target.teamId().equals(ship.teamId()))
                .filter(target -> !target.isScoutPlane())
                .filter(target -> isStrategicHumanContact(ship, target)
                        || visibilityCache.isVisible(ship, target, RadarService.RADAR_RANGE)
                        || isVisiblePeriscopeRamContact(ship, target,
                            ship.surface().botNavigation.trackingPeriscope && ship.surface().botNavigation.target == target
                                    && ship.surface().botNavigation.targetLife == target.lifeId()))
                .toList();
    }

    boolean isUnderwaterBotContact(Vehicle observer, Vehicle target) {
        return observer.isSubmarine() && target.isSubmarine() && !target.isOnSurface()
                && !observer.id().equals(target.id()) && "active".equals(target.state())
                && observer.position().distanceTo(target.position()) <= GameRules.BOT_SUBMARINE_UNDERWATER_RANGE;
    }

    boolean isVisiblePeriscopeRamContact(Vehicle observer, Vehicle target) {
        return isVisiblePeriscopeRamContact(observer, target, false);
    }

    boolean isVisiblePeriscopeRamContact(Vehicle observer, Vehicle target, boolean tracking) {
        // The depth order changes immediately; visibility changes only when the mast actually submerges.
        if (!target.isSubmarine() || target.isOnSurface()
                || target.y() + SubmarinePeriscopeGeometry.topAboveRoot(target.y()) <= 0) return false;
        double dx = target.position().x() - observer.position().x();
        double dz = target.position().z() - observer.position().z();
        double distance = Math.hypot(dx, dz);
        if (distance < 1e-9) return true;
        if (tracking) return distance <= botPeriscopeRamDetectionRange(target) * BOT_PERISCOPE_AHEAD_FACTOR;
        double forward = (dx * Math.sin(observer.heading()) + dz * Math.cos(observer.heading())) / distance;
        if (forward < -1e-9) return false;
        double directionFactor = .3 + (BOT_PERISCOPE_AHEAD_FACTOR-.3) * MathSupport.clamp(forward, 0, 1);
        return distance <= botPeriscopeRamDetectionRange(target) * directionFactor;
    }

    boolean isStrategicHumanContact(Vehicle observer, Vehicle target) {
        return isHumanControlled(target)
                && target.isOnSurface()
                && observer.position().distanceTo(target.position()) > RadarService.RADAR_RANGE;
    }

    List<Vehicle> activeTeamShips(String teamId) {
        return allShips().stream()
                .filter(ship -> teamId.equals(ship.teamId()))
                .filter(ship -> "active".equals(ship.state()))
                .toList();
    }

    private void updateRamCollisions(NavigationService navigationService) {
        List<Vehicle> activeShips = allShips().stream()
                .filter(ship -> "active".equals(ship.state()))
                .filter(ship -> !ship.isScoutPlane())
                .toList();
        for (int i = 0; i < activeShips.size(); i += 1) {
            Vehicle left = activeShips.get(i);
            for (int j = i + 1; j < activeShips.size(); j += 1) {
                if (!"active".equals(left.state())) break;
                Vehicle right = activeShips.get(j);
                if (!"active".equals(right.state())) continue;
                if (left.position().distanceTo(right.position()) > RAM_COLLISION_BROAD_PHASE_RADIUS) {
                    continue;
                }

                RamDepthMode ramDepthMode = ramDepthMode(left, right);
                if (ramDepthMode == RamDepthMode.NONE) {
                    continue;
                }
                if (ramDepthMode == RamDepthMode.PERISCOPE) {
                    resolvePeriscopeRamCollision(left, right);
                    continue;
                }
                if (ramDepthMode == RamDepthMode.SUBMERGED_SUBMARINES) {
                    resolveSubmergedSubmarineRamCollision(left, right);
                    continue;
                }

                RamImpact leftImpact = ramImpact(left, right);
                RamImpact rightImpact = ramImpact(right, left);
                boolean hullsOverlap = hullsOverlap(left, right);
                if (!leftImpact.hits() && !rightImpact.hits() && !hullsOverlap) {
                    continue;
                }

                if (hullsOverlap && left.isBotControlled() && right.isBotControlled()) {
                    preventNewBotHullOverlap(left.surface(), right.surface(), navigationService);
                }
                if (left.teamId().equals(right.teamId()) && left.isBotControlled() && right.isBotControlled()) {
                    backOffYieldingFriend(left, right);
                    backOffYieldingFriend(right, left);
                    continue;
                }

                if (left.isBotControlled() && right.isBotControlled()) {
                    resolveGlancingRam(left, right);
                    continue;
                }

                double collisionSpeed = ramCollisionSpeed(left, right);
                if (left.isSubmarine() || right.isSubmarine()) {
                    resolveSubmarineHullRamCollision(left, right, collisionSpeed);
                    continue;
                }

                if (collisionSpeed < ramDamageMinClosingSpeed(left, right)) {
                    resolveGlancingRam(left, right);
                    continue;
                }

                boolean headOnCollision = isCleanHeadOnCollision(left, right, leftImpact, rightImpact);
                boolean rearEndCollision = isRearEndCollision(left, right, leftImpact, rightImpact);
                boolean glancingCollision = isGlancingCollision(left, right);
                if (headOnCollision || rearEndCollision) {
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

    private RamDepthMode ramDepthMode(Vehicle left, Vehicle right) {
        if (ramBelowSurface(left) || ramBelowSurface(right)) {
            return ramBelowSurface(left) && ramBelowSurface(right)
                    && Math.abs(left.y() - right.y()) <= .89 * TORPEDO_BOAT_MODEL_SCALE
                    ? RamDepthMode.SUBMERGED_SUBMARINES
                    : RamDepthMode.NONE;
        }
        if (ramAtPeriscopeDepth(left) || ramAtPeriscopeDepth(right)) {
            return RamDepthMode.PERISCOPE;
        }
        return RamDepthMode.HULL;
    }

    // Compare actual model heights with the surface hull's keel, never the dive command.
    private boolean ramBelowSurface(Vehicle ship) {
        return ship.isSubmarine() && ship.y() + SubmarinePeriscopeGeometry.topAboveRoot(ship.y()) < RAM_SURFACE_KEEL_Y;
    }

    private boolean ramAtPeriscopeDepth(Vehicle ship) {
        return ship.isSubmarine() && !ramBelowSurface(ship)
                && ship.y() + RAM_SUBMARINE_HULL_TOP < RAM_SURFACE_KEEL_Y;
    }

    private boolean ramOnSurface(Vehicle ship) {
        return !ramBelowSurface(ship) && !ramAtPeriscopeDepth(ship);
    }

    private void resolveSubmergedSubmarineRamCollision(Vehicle left, Vehicle right) {
        if (left.isBotControlled() && right.isBotControlled()) {
            return;
        }
        boolean leftHits = submergedSubmarineRamHit(left, right);
        boolean rightHits = submergedSubmarineRamHit(right, left);
        if (!leftHits && !rightHits) {
            return;
        }
        if (ramCollisionSpeed(left, right) < SUBMARINE_RAM_DAMAGE_MIN_CLOSING_SPEED) {
            stopAfterRamImpact(left);
            stopAfterRamImpact(right);
            return;
        }
        sinkShipByRam(left, right);
        sinkShipByRam(right, left);
    }

    private void resolvePeriscopeRamCollision(Vehicle left, Vehicle right) {
        if (left.isBotControlled() && right.isBotControlled()) {
            return;
        }
        boolean leftSubmarineSinks = ramAtPeriscopeDepth(left)
                && (periscopeRamHit(right, left) || periscopeHitsSurfaceHull(left, right));
        boolean rightSubmarineSinks = ramAtPeriscopeDepth(right)
                && (periscopeRamHit(left, right) || periscopeHitsSurfaceHull(right, left));
        if (!leftSubmarineSinks && !rightSubmarineSinks) {
            return;
        }
        if (leftSubmarineSinks) {
            sinkShipByRam(left, right);
            stopAfterRamImpact(right);
        }
        if (rightSubmarineSinks) {
            sinkShipByRam(right, left);
            stopAfterRamImpact(left);
        }
    }

    private boolean periscopeRamHit(Vehicle attacker, Vehicle target) {
        if (!ramAtPeriscopeDepth(target) || !ramOnSurface(attacker)) {
            return false;
        }
        for (double bowOffset : List.of(RAM_BOW_OFFSET, RAM_BOW_OFFSET - 1.15, RAM_BOW_OFFSET - 2.3)) {
            Vector2 attackerBow = attacker.position()
                    .add(Vector2.fromHeading(attacker.heading()).scale(bowOffset * TORPEDO_BOAT_MODEL_SCALE));
            if (attackerBow.distanceTo(target.position()) <= SUBMARINE_PERISCOPE_RAM_RADIUS) {
                return true;
            }
        }
        return false;
    }

    private boolean periscopeHitsSurfaceHull(Vehicle submarine, Vehicle surfaceShip) {
        if (!ramAtPeriscopeDepth(submarine)
                || !ramOnSurface(surfaceShip)) {
            return false;
        }
        return pointInsideShipHull(
                submarine.position(),
                surfaceShip,
                SUBMARINE_PERISCOPE_RAM_RADIUS / TORPEDO_BOAT_MODEL_SCALE
        );
    }

    private boolean submergedSubmarineRamHit(Vehicle attacker, Vehicle target) {
        if (!ramBelowSurface(attacker) || !ramBelowSurface(target)) {
            return false;
        }
        for (double bowOffset : List.of(RAM_BOW_OFFSET, RAM_BOW_OFFSET - 1.15, RAM_BOW_OFFSET - 2.3)) {
            Vector2 attackerBow = attacker.position()
                    .add(Vector2.fromHeading(attacker.heading()).scale(bowOffset * TORPEDO_BOAT_MODEL_SCALE));
            if (attackerBow.distanceTo(target.position()) <= SUBMERGED_SUBMARINE_RAM_RADIUS) {
                return true;
            }
        }
        return false;
    }

    private void resolveSubmarineHullRamCollision(Vehicle left, Vehicle right, double collisionSpeed) {
        if (collisionSpeed < SUBMARINE_RAM_DAMAGE_MIN_CLOSING_SPEED) {
            stopAfterRamImpact(left);
            stopAfterRamImpact(right);
            return;
        }
        if (left.isSubmarine() && right.isSubmarine()) {
            sinkShipByRam(left, right);
            sinkShipByRam(right, left);
            return;
        }
        if (left.isSubmarine()) {
            sinkShipByRam(left, right);
            stopAfterRamImpact(right);
            return;
        }
        sinkShipByRam(right, left);
        stopAfterRamImpact(left);
    }

    private void preventNewBotHullOverlap(SurfaceVehicle left, SurfaceVehicle right, NavigationService navigation) {
        // A setup already containing overlapping hulls must still be able to move apart.
        if (hullsOverlapAt(left.motionStartPosition, left.motionStartHeading,
                right.motionStartPosition, right.motionStartHeading)) return;
        Vector2 leftPosition = left.position(), rightPosition = right.position();
        Vector2 normal = leftPosition.subtract(rightPosition).normalized();
        double leftTravel = leftPosition.distanceTo(left.motionStartPosition);
        double rightTravel = rightPosition.distanceTo(right.motionStartPosition);
        // Only bodies moving into the contact are corrected; stationary or escaping boats stay put.
        double leftInward = Math.max(0, -dot(leftPosition.subtract(left.motionStartPosition), normal));
        double rightInward = Math.max(0, dot(rightPosition.subtract(right.motionStartPosition), normal));
        double sum = leftInward + rightInward;
        double leftShare = sum > .000001 ? leftInward / sum : .5;
        double rightShare = 1 - leftShare;
        double rotationTravel = (Math.abs(MathSupport.normalizeAngle(left.heading() - left.motionStartHeading))
                + Math.abs(MathSupport.normalizeAngle(right.heading() - right.motionStartHeading))) * BotNavigation.SHIP_LENGTH;
        double high = Math.min(BotNavigation.SHIP_LENGTH * .25, Math.max(BotNavigation.SHIP_LENGTH * .05, leftTravel + rightTravel + rotationTravel + .1));
        placeContactBodies(left, right, leftPosition, rightPosition, normal, high, leftShare, rightShare);
        if (!hullsOverlap(left, right)) {
            double low = 0;
            // Rare contact correction only: bounded search, no additional fleet loop.
            for (int step = 0; step < 5; step++) {
                double middle = (low + high) * .5;
                placeContactBodies(left, right, leftPosition, rightPosition, normal, middle, leftShare, rightShare);
                if (hullsOverlap(left, right)) low = middle;
                else high = middle;
            }
            placeContactBodies(left, right, leftPosition, rightPosition, normal, high, leftShare, rightShare);
            if (!navigation.isShipBlocked(left.position(), left.heading(), worldMap)
                    && !navigation.isShipBlocked(right.position(), right.heading(), worldMap)) return;
        }
        left.position = leftPosition;
        right.position = rightPosition;
        if (restoreMotionIfClear(left, right) || restoreMotionIfClear(right, left)) return;
        left.position = left.motionStartPosition;
        left.heading = left.motionStartHeading;
        right.position = right.motionStartPosition;
        right.heading = right.motionStartHeading;
        stopPenetratingMotion(left);
        stopPenetratingMotion(right);
    }

    private static double dot(Vector2 a, Vector2 b) { return a.x() * b.x() + a.z() * b.z(); }

    private void placeContactBodies(SurfaceVehicle left, SurfaceVehicle right, Vector2 leftPosition,
                                    Vector2 rightPosition, Vector2 normal, double distance, double leftShare, double rightShare) {
        left.position = leftShare == 0 ? leftPosition : leftPosition.add(normal.scale(distance * leftShare));
        right.position = rightShare == 0 ? rightPosition : rightPosition.subtract(normal.scale(distance * rightShare));
    }

    private void stopPenetratingMotion(SurfaceVehicle vehicle) {
        vehicle.speed = 0;
        vehicle.applyCommand(ENGINE_STOP, vehicle.rudderDegrees());
        // Retain angular momentum: a glancing hull can keep turning along its neighbour.
    }

    private boolean restoreMotionIfClear(SurfaceVehicle vehicle, SurfaceVehicle other) {
        if (vehicle.position().equals(vehicle.motionStartPosition) && vehicle.heading() == vehicle.motionStartHeading) return false;
        Vector2 currentPosition = vehicle.position();
        double currentHeading = vehicle.heading();
        vehicle.position = vehicle.motionStartPosition;
        if (!hullsOverlap(vehicle, other)) {
            stopPenetratingMotion(vehicle);
            return true;
        }
        vehicle.heading = vehicle.motionStartHeading;
        if (!hullsOverlap(vehicle, other)) {
            stopPenetratingMotion(vehicle);
            return true;
        }
        vehicle.position = currentPosition;
        vehicle.heading = currentHeading;
        return false;
    }

    private void backOffYieldingFriend(Vehicle vehicle, Vehicle other) {
        if (!vehicle.isBotControlled() || !BotNavigation.mustYieldTo(vehicle, other)) return;
        Vector2 offset = other.position().subtract(vehicle.position());
        Vector2 motion = Vector2.fromHeading(vehicle.heading()).scale(vehicle.speed());
        // A boat already leaving a close group must not be pinned by the contact response.
        if (offset.x() * motion.x() + offset.z() * motion.z() < 0) return;
        vehicle.backOffAfterGlancingRam(nowSeconds, BOT_GLANCING_RAM_BACKOFF_SECONDS, other);
    }

    private void resolveGlancingRam(Vehicle left, Vehicle right) {
        double side = Math.signum(MathSupport.normalizeAngle(right.heading() - left.heading()));
        if (side == 0) {
            side = 1;
        }
        resolveGlancingShip(left, right, -side * RAM_GLANCING_HEADING_IMPULSE);
        resolveGlancingShip(right, left, side * RAM_GLANCING_HEADING_IMPULSE);
    }

    private void resolveGlancingShip(Vehicle ship, Vehicle other, double headingImpulse) {
        if (ship.isBotControlled()) {
            ship.backOffAfterGlancingRam(nowSeconds, BOT_GLANCING_RAM_BACKOFF_SECONDS, other);
        } else {
            ship.glanceOff(headingImpulse, 0.55);
        }
    }

    private void resolveSideRamBySpeed(Vehicle left, Vehicle right) {
        double leftSpeed = Math.max(0, left.speed());
        double rightSpeed = Math.max(0, right.speed());
        if (leftSpeed >= rightSpeed) {
            sinkShipByRam(right, left);
            stopAfterRamImpact(left);
        } else {
            sinkShipByRam(left, right);
            stopAfterRamImpact(right);
        }
    }

    private void stopAfterRamImpact(Vehicle ship) {
        ship.stopAfterRamImpact();
        if ("active".equals(ship.state())) {
            ramImpactStopUntilSecondsByShipId.put(ship.id(), nowSeconds + RAM_IMPACT_STOP_HOLD_SECONDS);
        } else {
            ramImpactStopUntilSecondsByShipId.remove(ship.id());
        }
    }

    private void applyPendingRamImpactStop(Vehicle ship) {
        Double stopUntilSeconds = ramImpactStopUntilSecondsByShipId.get(ship.id());
        if (stopUntilSeconds == null) {
            return;
        }
        if (!"active".equals(ship.state()) || nowSeconds > stopUntilSeconds) {
            ramImpactStopUntilSecondsByShipId.remove(ship.id());
            return;
        }
        ship.stopAfterRamImpact();
    }

    private void sinkShipByRam(Vehicle target, Vehicle attacker) {
        boolean sunk = sinkShip(target, attacker.controlledBy());
        if (sunk) {
            recordRamHit(attacker, target);
        }
    }

    private boolean isGlancingCollision(Vehicle left, Vehicle right) {
        double relativeHeading = angularDistance(left.heading(), right.heading());
        return relativeHeading < RAM_GLANCING_COLLISION_ANGLE;
    }

    private boolean isCleanHeadOnCollision(Vehicle left, Vehicle right, RamImpact leftImpact, RamImpact rightImpact) {
        if (angularDistance(left.heading(), right.heading()) <= Math.toRadians(135)) {
            return false;
        }
        if (leftImpact.isCleanBowHit() && rightImpact.isCleanBowHit()) {
            return true;
        }

        LocalHullPoint rightCenterFromLeft = localHullPoint(right.position(), left);
        LocalHullPoint leftCenterFromRight = localHullPoint(left.position(), right);
        return rightCenterFromLeft.forward() > 0
                && leftCenterFromRight.forward() > 0
                && Math.abs(rightCenterFromLeft.right()) <= 0.55
                && Math.abs(leftCenterFromRight.right()) <= 0.55;
    }

    private boolean isRearEndCollision(Vehicle left, Vehicle right, RamImpact leftImpact, RamImpact rightImpact) {
        return angularDistance(left.heading(), right.heading()) < RAM_GLANCING_COLLISION_ANGLE
                && (leftImpact.isSternHit() || rightImpact.isSternHit());
    }

    private double ramCollisionSpeed(Vehicle left, Vehicle right) {
        Vector2 separation = right.position().subtract(left.position()).normalized();
        Vector2 leftVelocity = Vector2.fromHeading(left.heading()).scale(left.speed());
        Vector2 rightVelocity = Vector2.fromHeading(right.heading()).scale(right.speed());
        Vector2 relativeVelocity = leftVelocity.subtract(rightVelocity);
        return Math.max(0, relativeVelocity.x() * separation.x() + relativeVelocity.z() * separation.z());
    }

    private double ramDamageMinClosingSpeed(Vehicle left, Vehicle right) {
        return left.isSubmarine() || right.isSubmarine()
                ? SUBMARINE_RAM_DAMAGE_MIN_CLOSING_SPEED
                : RAM_DAMAGE_MIN_CLOSING_SPEED;
    }

    private RamImpact ramImpact(Vehicle attacker, Vehicle target) {
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

    private RamImpact ramImpactAt(Vehicle attacker, Vehicle target, double bowOffset) {
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
        boolean cleanBowHit = hit.forward() > RAM_BOW_LENGTH - 0.78
                && Math.abs(hit.right()) <= Math.max(0.16, halfWidth + RAM_SIDE_MARGIN * 0.22);
        boolean sternHit = hit.forward() < RAM_STERN_LENGTH + 1.15
                && Math.abs(hit.right()) <= Math.max(0.18, halfWidth * 0.65);
        double sideScore = sideHit
                ? (1 - sideAngleError / RAM_SIDE_ANGLE_TOLERANCE)
                + MathSupport.clamp((halfWidth + RAM_SIDE_MARGIN - Math.abs(hit.right())) / (halfWidth + RAM_SIDE_MARGIN), 0, 1) * 0.35
                + MathSupport.clamp(attacker.speed() / 8.0, 0, 1) * 0.25
                : 0;
        return new RamImpact(true, sideHit, bowHit, cleanBowHit, sternHit, sideScore);
    }

    private boolean hullsOverlap(Vehicle left, Vehicle right) {
        return hullSamplePoints(left).stream().anyMatch(point -> pointInsideShipHull(point, right, RAM_SIDE_MARGIN))
                || hullSamplePoints(right).stream().anyMatch(point -> pointInsideShipHull(point, left, RAM_SIDE_MARGIN));
    }

    private boolean hullsOverlap(Vector2 position, double heading, Vehicle other) {
        return hullSamplePoints(position, heading).stream().anyMatch(point -> pointInsideShipHull(point, other, RAM_SIDE_MARGIN))
                || hullSamplePoints(other).stream().anyMatch(point -> pointInsideShipHull(point, position, heading, RAM_SIDE_MARGIN));
    }

    private boolean hullsOverlapAt(Vector2 left, double leftHeading, Vector2 right, double rightHeading) {
        return hullSamplePoints(left, leftHeading).stream().anyMatch(point -> pointInsideShipHull(point, right, rightHeading, RAM_SIDE_MARGIN))
                || hullSamplePoints(right, rightHeading).stream().anyMatch(point -> pointInsideShipHull(point, left, leftHeading, RAM_SIDE_MARGIN));
    }

    private List<Vector2> hullSamplePoints(Vehicle ship) {
        return hullSamplePoints(ship.position(), ship.heading());
    }

    private List<Vector2> hullSamplePoints(Vector2 position, double heading) {
        double[] forwards = {
                TORPEDO_BOAT_STERN_Z + 0.25,
                -3.0,
                -1.45,
                0.2,
                1.7,
                TORPEDO_BOAT_BOW_Z - 0.25
        };
        List<Vector2> points = new ArrayList<>(forwards.length * 3);
        Vector2 forwardVector = Vector2.fromHeading(heading);
        Vector2 rightVector = new Vector2(Math.cos(heading), -Math.sin(heading));
        for (double forward : forwards) {
            double halfWidth = enemyHullHalfWidthAt(forward);
            points.add(position
                    .add(forwardVector.scale(forward * TORPEDO_BOAT_MODEL_SCALE)));
            points.add(position
                    .add(forwardVector.scale(forward * TORPEDO_BOAT_MODEL_SCALE))
                    .add(rightVector.scale(halfWidth * TORPEDO_BOAT_MODEL_SCALE)));
            points.add(position
                    .add(forwardVector.scale(forward * TORPEDO_BOAT_MODEL_SCALE))
                    .add(rightVector.scale(-halfWidth * TORPEDO_BOAT_MODEL_SCALE)));
        }
        return points;
    }

    private boolean pointInsideShipHull(Vector2 point, Vehicle ship, double margin) {
        LocalHullPoint hit = localHullPoint(point, ship);
        return forwardInsideTorpedoBoatHull(hit.forward(), margin)
                && Math.abs(hit.right()) <= enemyHullHalfWidthAt(hit.forward()) + margin;
    }

    private boolean pointInsideShipHull(Vector2 point, Vector2 position, double heading, double margin) {
        LocalHullPoint hit = localHullPoint(point, position, heading);
        return forwardInsideTorpedoBoatHull(hit.forward(), margin)
                && Math.abs(hit.right()) <= enemyHullHalfWidthAt(hit.forward()) + margin;
    }

    private LocalHullPoint localHullPoint(Vector2 point, Vehicle ship) {
        return localHullPoint(point, ship.position(), ship.heading());
    }

    private LocalHullPoint localHullPoint(Vector2 point, Vector2 position, double heading) {
        double dx = point.x() - position.x();
        double dz = point.z() - position.z();
        return new LocalHullPoint(
                (dx * Math.cos(heading) - dz * Math.sin(heading)) / TORPEDO_BOAT_MODEL_SCALE,
                (dx * Math.sin(heading) + dz * Math.cos(heading)) / TORPEDO_BOAT_MODEL_SCALE
        );
    }

    private double localShipY(double y, Vehicle ship, double scale) {
        return (y - ship.y()) / scale - TORPEDO_BOAT_MODEL_WATERLINE_Y;
    }

    private double enemyHullHalfWidthAt(double forward) {
        return torpedoBoatHullTopHalfWidthAt(forward);
    }

    private boolean forwardInsideTorpedoBoatHull(double forward, double margin) {
        return forward >= TORPEDO_BOAT_STERN_Z - margin && forward <= TORPEDO_BOAT_BOW_Z + margin;
    }

    private static final double[][] TORPEDO_BOAT_HULL_TOP_WIDTHS = {
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

    private double torpedoBoatHullTopHalfWidthAt(double forward) {
        return interpolateSectionValue(TORPEDO_BOAT_HULL_TOP_WIDTHS, forward);
    }

    private record LocalHullPoint(double right, double forward) {
    }

    private record RamImpact(boolean hits, boolean isCleanSideHit, boolean isBowHit, boolean isCleanBowHit,
                             boolean isSternHit, double sideScore) {
        static RamImpact miss() {
            return new RamImpact(false, false, false, false, false, 0);
        }
    }

    private enum RamDepthMode {
        NONE,
        HULL,
        PERISCOPE,
        SUBMERGED_SUBMARINES
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

            Vector2 landImpact = torpedoLandImpact(torpedo, navigationService, worldMap);
            if (landImpact != null) {
                torpedo.hit();
                sinkPointBlankSubmarineShooter(torpedo);
                recordTorpedoImpact(torpedo, "land-hit", null, landImpact);
                continue;
            }

            allShips().stream()
                    .filter(ship -> "active".equals(ship.state()))
                    .filter(ship -> !ship.isScoutPlane())
                    .filter(ship -> !ship.id().equals(torpedo.shipId()))
                    .filter(ship -> torpedoCanDamageShip(torpedo, ship))
                    .filter(ship -> torpedoHitsShip(torpedo, ship))
                    .findFirst()
                    .ifPresent(ship -> {
                        sinkShip(ship, shooterController(torpedo.shipId()));
                        sinkPointBlankSubmarineShooter(torpedo);
                        torpedo.hit();
                        recordTorpedoImpact(torpedo, "ship-hit", ship.id());
                    });
        }
    }

    boolean torpedoCanDamageShip(Torpedo torpedo, Vehicle target) {
        return torpedoDepthCanDamageShip(torpedo.y(), target);
    }

    private boolean torpedoDepthCanDamageShip(double y, Vehicle target) {
        if (y < -1) {
            return target.isSubmarine()
                    && target.y() < -1.87 * TORPEDO_BOAT_MODEL_SCALE
                    && Math.abs(y - (target.y() + .2 * TORPEDO_BOAT_MODEL_SCALE)) <= .5 * TORPEDO_BOAT_MODEL_SCALE;
        }
        if (!target.isSubmarine()) return true;
        // Broad vertical envelope only. The exact hull/mast intersection below decides the hit.
        // A selected dive state must neither expose an underwater hull nor hide an emerging one.
        return y >= target.y() - .27 * TORPEDO_BOAT_MODEL_SCALE
                && y <= target.y() + SubmarinePeriscopeGeometry.topAboveRoot(target.y());
    }

    private void sinkPointBlankSubmarineShooter(Torpedo torpedo) {
        shooterShip(torpedo.shipId())
                .filter(Vehicle::isSubmarine)
                .filter(ship -> "active".equals(ship.state()))
                .filter(ship -> distanceFromTorpedoToSubmarineHull(torpedo, ship) <= SUBMARINE_POINT_BLANK_TORPEDO_DANGER_RANGE)
                .ifPresent(this::sinkShip);
    }

    private double distanceFromTorpedoToSubmarineHull(Torpedo torpedo, Vehicle ship) {
        LocalHullPoint p=localHullPoint(torpedo.position(),ship);
        double dx=Math.max(0,Math.abs(p.right())-.62)*TORPEDO_BOAT_MODEL_SCALE;
        double dz=Math.max(0,Math.abs(p.forward())-5.02)*TORPEDO_BOAT_MODEL_SCALE;
        double dy=Math.max(ship.y()-.3*TORPEDO_BOAT_MODEL_SCALE-torpedo.y(),
                torpedo.y()-(ship.y()+.51*TORPEDO_BOAT_MODEL_SCALE));
        return Math.sqrt(dx*dx+dz*dz+Math.max(0,dy)*Math.max(0,dy));
    }

    private Optional<Vehicle> shooterShip(String shipId) {
        return allShips().stream()
                .filter(ship -> ship.id().equals(shipId))
                .findFirst();
    }

    private Optional<Vehicle> airborneTorpedoHitsScoutPlane(Torpedo torpedo) {
        return allShips().stream()
                .filter(ship -> "active".equals(ship.state()))
                .filter(Vehicle::isScoutPlane)
                .filter(ship -> !ship.id().equals(torpedo.shipId()))
                .filter(ship -> torpedoAirSegmentHitsScoutPlane(torpedo, ship))
                .findFirst();
    }

    private boolean torpedoAirSegmentHitsScoutPlane(Torpedo torpedo, Vehicle plane) {
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
        recordTorpedoImpact(torpedo, reason, targetShipId, torpedo.position());
    }

    private void recordTorpedoImpact(Torpedo torpedo, String reason, String targetShipId, Vector2 position) {
        torpedoImpacts.add(new TorpedoImpactSnapshot(
                torpedo.id(),
                torpedo.teamId(),
                torpedo.shipId(),
                targetShipId,
                reason,
                MathSupport.round(position.x()),
                MathSupport.round(position.z()),
                MathSupport.round(torpedo.heading()),
                MathSupport.round(nowSeconds),
                MathSupport.round(torpedo.y())
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
        double maxTerrainHeight = LandGeometry.maxTerrainHeight(worldMap);
        flakProjectiles.stream()
                .filter(projectile -> !projectile.hitResolved())
                .filter(FlakProjectile::hasCollisionSegment)
                .forEach(projectile -> recordFlakTerrainImpact(projectile, worldMap, maxTerrainHeight)
                        .ifPresent(impact -> {
                            if (!"water-hit".equals(impact.reason())) {
                                projectile.hit();
                            }
                        }));
    }

    private Optional<FlakImpactSnapshot> recordFlakTerrainImpact(FlakProjectile projectile, WorldMap worldMap, double maxTerrainHeight) {
        double dx = projectile.x() - projectile.previousX();
        double dy = projectile.y() - projectile.previousY();
        double dz = projectile.z() - projectile.previousZ();
        if (maxTerrainHeight > 0.02 && Math.min(projectile.previousY(), projectile.y()) <= maxTerrainHeight + 1.5) {
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
        List<Vehicle> activeTargets = allShips().stream()
                .filter(ship -> "active".equals(ship.state()))
                .toList();

        // Conservative vertical bounds avoid searching the fleet for deep, spent trajectories.
        double lowestTargetY = activeTargets.stream()
                .mapToDouble(ship -> ship.y() - (ship.isScoutPlane() ? 9.0 * SCOUT_PLANE_MODEL_SCALE : 7.2 * TORPEDO_BOAT_MODEL_SCALE))
                .min().orElse(Double.POSITIVE_INFINITY);

        flakProjectiles.stream()
                .filter(projectile -> !projectile.hitResolved())
                .filter(this::hasActiveFlakCollisionSegment)
                .filter(projectile -> Math.max(projectile.previousY(), projectile.y()) >= lowestTargetY)
                .forEach(projectile -> activeTargets.stream()
                        .filter(ship -> !ship.id().equals(projectile.shipId()))
                        .map(ship -> flakProjectileHitsTarget(projectile, ship))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .min(java.util.Comparator.comparingDouble(hit -> {
                            double dx = hit.x() - projectile.previousX();
                            double dy = hit.y() - projectile.previousY();
                            double dz = hit.z() - projectile.previousZ();
                            return dx * dx + dy * dy + dz * dz;
                        }))
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
        return projectile.hasCollisionSegment();
    }

    private Optional<FlakTargetHit> flakProjectileHitsTarget(FlakProjectile projectile, Vehicle ship) {
        if (distanceToFlakSegment2D(projectile, ship.position()) > (ship.isScoutPlane() ? 9.0 * SCOUT_PLANE_MODEL_SCALE : 7.2 * TORPEDO_BOAT_MODEL_SCALE)) {
            return Optional.empty();
        }
        if (ship.isSubmarine()) {
            double t = SubmarineProjectileGeometry.hitFraction(projectile.previousX(), projectile.previousY(), projectile.previousZ(),
                    projectile.x(), projectile.y(), projectile.z(), ship);
            if (!Double.isFinite(t)) {
                return Optional.empty();
            }
            return Optional.of(new FlakTargetHit(ship, FlakShipHitArea.CRITICAL.reason(), true,
                    projectile.previousX() + (projectile.x()-projectile.previousX())*t,
                    projectile.previousY() + (projectile.y()-projectile.previousY())*t,
                    projectile.previousZ() + (projectile.z()-projectile.previousZ())*t));
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

    private Optional<FlakTargetHit> flakProjectileHitsScoutPlane(FlakProjectile projectile, Vehicle plane) {
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

    private Optional<FlakTargetHit> flakProjectileHitsShip(FlakProjectile projectile, Vehicle ship) {
        if (ship.isSubmarine()) {
            double t = SubmarinePeriscopeGeometry.hitParameter(ship, projectile.previousX(), projectile.previousY(),
                    projectile.previousZ(), projectile.x(), projectile.y(), projectile.z());
            if (Double.isFinite(t)) return Optional.of(new FlakTargetHit(ship, FlakShipHitArea.CRITICAL.reason(), true,
                    projectile.previousX() + (projectile.x() - projectile.previousX()) * t,
                    projectile.previousY() + (projectile.y() - projectile.previousY()) * t,
                    projectile.previousZ() + (projectile.z() - projectile.previousZ()) * t));
        }
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

    private boolean pointHitsShipHullAtCannonHeight(double x, double y, double z, Vehicle ship) {
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

    private FlakShipHitArea shipFlakHitArea(double x, double y, double z, Vehicle ship) {
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

    private boolean pointHitsScoutPlane(double x, double y, double z, Vehicle plane) {
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
                MathSupport.round(nowSeconds),
                MathSupport.round(hit.ship().y())
        ));
    }

    private void recordClientPlaneHit(ClientPlaneHitRequest request, Vehicle shooter, Vehicle target) {
        String weaponPrefix = "cannon".equals(request.weaponType()) ? "cannon" : "flak";
        projectileHits.add(new ProjectileHitSnapshot(
                weaponPrefix + "-client-" + nextFlakProjectileId++,
                shooter.teamId(),
                shooter.id(),
                target.id(),
                MathSupport.round(request.x()),
                MathSupport.round(request.y()),
                MathSupport.round(request.z()),
                MathSupport.round(nowSeconds),
                MathSupport.round(target.y())
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

    private record FlakTargetHit(Vehicle ship, String reason, boolean sinks, double x, double y, double z) {
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

    private boolean bombHitsShip(Bomb bomb, Vehicle ship) {
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

    private Vector2 torpedoLandImpact(Torpedo torpedo, NavigationService navigationService, WorldMap worldMap) {
        double segmentLength = torpedo.previousPosition().distanceTo(torpedo.position());
        int samples = Math.max(1, (int) Math.ceil(segmentLength / TORPEDO_SWEEP_STEP));
        Vector2 waterSide = torpedo.previousPosition();
        for (int index = 0; index <= samples; index += 1) {
            double t = samples == 0 ? 1 : (double) index / samples;
            Vector2 sample = new Vector2(
                    torpedo.previousPosition().x() + (torpedo.position().x() - torpedo.previousPosition().x()) * t,
                    torpedo.previousPosition().z() + (torpedo.position().z() - torpedo.previousPosition().z()) * t
            );
            if (navigationService.isTorpedoBlocked(sample, worldMap)) {
                // Refine only actual hits; keep the explosion on the water side of the wall.
                Vector2 landSide = sample;
                for (int refinement = 0; refinement < 8 && index > 0; refinement++) {
                    Vector2 middle = waterSide.add(landSide).scale(0.5);
                    if (navigationService.isTorpedoBlocked(middle, worldMap)) {
                        landSide = middle;
                    } else {
                        waterSide = middle;
                    }
                }
                return waterSide;
            }
            waterSide = sample;
        }
        return null;
    }

    private boolean torpedoHitsShip(Torpedo torpedo, Vehicle ship) {
        if (ship.position().distanceTo(torpedo.position()) > TORPEDO_BROAD_PHASE_RADIUS * TORPEDO_BOAT_MODEL_SCALE
                && ship.position().distanceTo(torpedo.previousPosition()) > TORPEDO_BROAD_PHASE_RADIUS * TORPEDO_BOAT_MODEL_SCALE) {
            return false;
        }
        if (ship.isSubmarine()) {
            return Double.isFinite(SubmarineProjectileGeometry.hitFraction(
                    torpedo.previousPosition().x(), torpedo.y(), torpedo.previousPosition().z(),
                    torpedo.position().x(), torpedo.y(), torpedo.position().z(), ship));
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

    private boolean pointHitsShipHull(Vector2 point, Vehicle ship, double margin) {
        LocalHullPoint hit = localHullPoint(point, ship);
        if (!forwardInsideTorpedoBoatHull(hit.forward(), margin)) {
            return false;
        }

        return Math.abs(hit.right()) <= enemyHullHalfWidthAt(hit.forward()) + margin;
    }

    private void sinkShip(Vehicle ship) {
        sinkShip(ship, null);
    }

    private boolean sinkShip(Vehicle ship, String creditedPlayerId) {
        String sunkController = ship.controlledBy();
        Vehicle wreckShip = ship.copyForWreck();
        Integer scoreDelta = isHumanController(creditedPlayerId)
                ? scoreDeltaFor(creditedPlayerId, ship.teamId())
                : null;
        if (!ship.sink(nowSeconds + (ship.isBotControlled() ? 0 : RESPAWN_DELAY_SECONDS))) {
            return false;
        }
        wrecks.add(new Wreck("wreck-" + nextWreckId++, nowSeconds, wreckShip));
        // A hard bound also covers unusual mass-destruction scenarios.
        if (wrecks.size() > 256) wrecks.remove(0);
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

    List<BotWreckObstacle> botWreckObstacles() {
        if (wrecks.isEmpty()) return List.of();
        List<BotWreckObstacle> obstacles = new ArrayList<>();
        for (Wreck wreck : wrecks) {
            double age = nowSeconds - wreck.sunkAt();
            if (!(wreck.ship() instanceof TorpedoBoat) || age < 0 || age >= wreck.sinkSeconds()) continue;
            obstacles.add(BotWreckObstacle.of(wreck, age));
        }
        return obstacles;
    }

    private void updateWreckCollisions() {
        List<Vehicle> targets = allShips().stream().filter(ship -> "active".equals(ship.state()))
                .filter(ship -> !ship.isScoutPlane()).toList();
        List<Wreck> hazards = List.copyOf(wrecks);
        double elapsed = Math.max(0, nowSeconds - previousWreckCheckTime);
        for (Vehicle target : targets) {
            HullPose current = new HullPose(target);
            HullPose previous = previousWreckTargets.get(target.id());
            if (previous == null || previous.lifeId() != current.lifeId()) previous = current;
            for (Wreck wreck : hazards) {
                if (!"active".equals(target.state())) break;
                if (!"torpedo-boat".equals(wreck.ship().vehicleType())) continue;
                double age = nowSeconds - wreck.sunkAt();
                if (age < 0 || age-elapsed >= wreck.sinkSeconds()) continue;
                double startAge=Math.max(0,age-elapsed);
                double endAge=Math.min(age,wreck.sinkSeconds());
                double span=endAge-startAge;
                double travel = Math.hypot(current.x()-previous.x(),current.z()-previous.z())
                        + Math.abs(current.y()-previous.y()) + 12*span
                        + Math.abs(MathSupport.normalizeAngle(current.heading()-previous.heading()))*16;
                int steps = Math.max(1, (int)Math.ceil(travel/.6));
                // Cheap swept broad phase before any hull samples.
                WreckPhysics.Pose end = WreckPhysics.pose(wreck, endAge);
                if (Math.hypot(end.x()-current.x(),end.z()-current.z()) > 38+travel) continue;
                for (int i=0;i<=steps;i++) {
                    double f=i/(double)steps;
                    double sampleAge=startAge+span*f;
                    double targetF=elapsed>0 ? MathSupport.clamp((wreck.sunkAt()+sampleAge-previousWreckCheckTime)/elapsed,0,1) : 1;
                    HullPose sample = previous.interpolate(current, targetF);
                    if (!WreckPhysics.hits(wreck,sampleAge,sample)) continue;
                    if (sinkShip(target, null)) {
                        ramHits.add(new RamHitSnapshot("wreck-hit-"+wreck.id()+"-"+target.id()+"-"+current.lifeId(),
                                wreck.ship().teamId(),wreck.ship().id(),target.id(),sample.x(),sample.z(),nowSeconds,"wreck-collision"));
                    }
                    break;
                }
            }
        }
        previousWreckTargets.clear();
        allShips().stream().filter(ship -> "active".equals(ship.state()))
                .forEach(ship -> previousWreckTargets.put(ship.id(), new HullPose(ship)));
        previousWreckCheckTime=nowSeconds;
    }



    private void recordRamHit(Vehicle attacker, Vehicle target) {
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

    private void clearBotScoutPlaneAttackState(Vehicle ship) {
        botScoutPlaneFlyThroughTargets.remove(ship.id());
        botScoutPlaneFlyThroughUntil.remove(ship.id());
        Map<String, Integer> countedAttacks = botScoutPlaneNonHumanAttackStreakByPlane.remove(ship.id());
        if (countedAttacks == null) {
            return;
        }
        countedAttacks.forEach((key, count) ->
                botScoutPlaneNonHumanAttackStreakByHumanTarget.computeIfPresent(key, (ignored, value) -> {
                    int nextValue = value - count;
                    return nextValue > 0 ? nextValue : null;
                })
        );
    }

    private int scoreDeltaFor(String creditedPlayerId, String sunkTeamId) {
        return teamIdForController(creditedPlayerId)
                .map(teamId -> teamId.equals(sunkTeamId) ? SCORE_FRIENDLY_SUNK : SCORE_ENEMY_SUNK)
                .orElse(SCORE_ENEMY_SUNK);
    }

    private Optional<String> teamIdForController(String controller) {
        return allShips().stream()
                .filter(ship -> controller.equals(ship.controlledBy()))
                .map(Vehicle::teamId)
                .findFirst();
    }

    private String shooterController(String shipId) {
        return allShips().stream()
                .filter(ship -> ship.id().equals(shipId))
                .map(Vehicle::controlledBy)
                .findFirst()
                .orElse(null);
    }

    private boolean isHumanController(String controller) {
        return controller != null && !controller.isBlank() && !"bot".equals(controller) && !"scenario".equals(controller);
    }

    private void respawnSunkShips(NavigationService navigationService, WorldMap worldMap, RadarService radarService) {
        Set<Vector2> reservedRespawnPositions = new HashSet<>();
        allShips().stream()
                .filter(ship -> ship.isReadyToRespawn(nowSeconds))
                .forEach(ship -> {
                    Vector2 position = findRespawnPosition(ship, navigationService, worldMap, radarService, reservedRespawnPositions);
                    if (navigationService.isShipBlocked(position, ship.heading(), worldMap)
                            || activeShipsTooClose(position, reservedRespawnPositions)
                            || spawnOverlapsWreck(ship, position, worldMap)) {
                        return;
                    }
                    ship = prepareBotVehicleTypeForRespawn(ship);
                    reservedRespawnPositions.add(position);
                    double heading = respawnHeading(ship, position, worldMap);
                    ship.respawn(position, heading, nowSeconds);
                });
    }

    private boolean spawnOverlapsWreck(Vehicle ship, Vector2 position, WorldMap worldMap) {
        HullPose spawn = new HullPose(ship, ship.lifeId() + 1, position.x(), 0, position.z(),
                respawnHeading(ship, position, worldMap));
        return wrecks.stream().filter(w -> "torpedo-boat".equals(w.ship().vehicleType()))
                .anyMatch(w -> WreckPhysics.hits(w,nowSeconds-w.sunkAt(),spawn));
    }

    private Optional<Vector2> nearestLandCenter(Vector2 position, WorldMap worldMap) {
        return worldMap.landmasses().stream()
                .map(landmass -> new Vector2(landmass.x(), landmass.z()))
                .min((left, right) -> Double.compare(
                        position.distanceTo(left),
                        position.distanceTo(right)
                ));
    }

    private double respawnHeading(Vehicle ship, Vector2 position, WorldMap worldMap) {
        double heading = MathSupport.normalizeAngle(angleTo(nearestLandCenter(position, worldMap).orElse(new Vector2(0, 0)), position) + Math.PI);
        double headingVariation = Math.sin(stablePhase(ship.id())) * Math.toRadians(8);
        return MathSupport.normalizeAngle(heading + headingVariation);
    }

    private Vehicle prepareBotVehicleTypeForRespawn(Vehicle ship) {
        if (!ship.isBotControlled()) return ship;
        // Count every fleet slot, including destroyed boats awaiting their replacement.
        // Otherwise overlapping deaths can refill the same submarine quota repeatedly.
        Fleet fleet = fleets.get(ship.teamId());
        long otherSubmarines = teamShips(ship.teamId()).stream()
                .filter(other -> other != ship).filter(Vehicle::isSubmarine).count();
        if (otherSubmarines < fleet.intendedSubmarineCount()) {
            return fleet.changeVehicleType(ship, "submarine");
        }
        if (!allowsDynamicScoutPlanes()) {
            return fleet.changeVehicleType(ship, VEHICLE_TORPEDO_BOAT);
        }
        int desiredScoutPlanes = desiredBotScoutPlaneCount(ship.teamId());
        long currentScoutPlanesWithoutRespawningShip = teamShips(ship.teamId()).stream()
                .filter(candidate -> !candidate.id().equals(ship.id()))
                .filter(Vehicle::isScoutPlane)
                .filter(Vehicle::isBotControlled)
                .count();
        return fleet.changeVehicleType(ship, currentScoutPlanesWithoutRespawningShip < desiredScoutPlanes
                ? VEHICLE_SCOUT_PLANE
                : VEHICLE_TORPEDO_BOAT);
    }

    private boolean allowsDynamicScoutPlanes() {
        return !"side-view-sandbox".equals(id) && !"two-ship-duel".equals(id);
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

    private List<Vehicle> teamShips(String teamId) {
        Fleet fleet = fleets.get(teamId);
        return fleet == null ? List.of() : fleet.ships();
    }

    Vector2 findRespawnPosition(Vehicle ship, NavigationService navigationService, WorldMap worldMap, RadarService radarService) {
        return findRespawnPosition(ship, navigationService, worldMap, radarService, Set.of());
    }

    private Vector2 findRespawnPosition(Vehicle ship, NavigationService navigationService, WorldMap worldMap, RadarService radarService,
                                        Set<Vector2> reservedRespawnPositions) {
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
            boolean blocked = navigationService.isShipBlocked(candidate, ship.heading(), worldMap)
                    || spawnOverlapsWreck(ship,candidate,worldMap);
            boolean activeShipsTooClose = activeShipsTooClose(candidate, reservedRespawnPositions);
            double distanceToHumans = distanceToNearestHumanShip(candidate);
            boolean tooCloseToHuman = distanceToHumans <= radarService.range() + RESPAWN_HUMAN_RADAR_MARGIN;

            if (!sameAsLast && !blocked && !activeShipsTooClose && !tooCloseToHuman) {
                return selectRespawnChoice(choice, candidates.size());
            }
            if (!sameAsLast && !blocked && !activeShipsTooClose) {
                double fallbackScore = distanceToHumans;
                if (fallbackScore > bestFallbackScore) {
                    bestFallbackScore = fallbackScore;
                    bestFallbackCandidate = choice;
                }
            }
            if (!sameAsLast && !blocked && !activeShipsTooClose && firstNavigableCandidate == null) {
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

    private boolean activeShipsTooClose(Vector2 candidate, Set<Vector2> reservedRespawnPositions) {
        boolean reservedTooClose = reservedRespawnPositions.stream()
                .anyMatch(position -> position.distanceTo(candidate) < RESPAWN_MIN_SHIP_DISTANCE);
        if (reservedTooClose) {
            return true;
        }
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





    private boolean fireTorpedo(Vehicle ship, double cooldownSeconds, double headingOffsetRadians) {
        return fireTorpedo(ship, cooldownSeconds, headingOffsetRadians, null);
    }

    boolean fireTorpedoAtTarget(Vehicle ship, Vehicle target, double cooldownSeconds, double headingOffsetRadians) {
        return fireTorpedo(ship, cooldownSeconds, headingOffsetRadians, null, target);
    }

    private boolean fireTorpedo(Vehicle ship, double cooldownSeconds, double headingOffsetRadians, Integer requestedTubeSide) {
        return fireTorpedo(ship, cooldownSeconds, headingOffsetRadians, requestedTubeSide, null);
    }

    private boolean fireTorpedo(Vehicle ship, double cooldownSeconds, double headingOffsetRadians, Integer requestedTubeSide, Vehicle target) {
        if (!ship.canFire(nowSeconds)) {
            return false;
        }

        double heading = MathSupport.normalizeAngle(ship.heading() + headingOffsetRadians);
        int tubeSide = normalizeTubeSide(requestedTubeSide);
        Vector2 forward = Vector2.fromHeading(heading);
        Vector2 muzzlePosition = torpedoMuzzlePosition(ship, heading, tubeSide);
        double launchY = ship.isSubmarine() && ship.y() < -1.87 * TORPEDO_BOAT_MODEL_SCALE
                ? ship.y() + .2 * TORPEDO_BOAT_MODEL_SCALE : .05;
        if (target != null && !torpedoDepthCanDamageShip(launchY, target)) return false;
        if (target != null && !botTorpedoIntercepts(ship, muzzlePosition, forward, target, launchY)) {
            return false;
        }
        if (target != null) {
            BotTorpedoAim aim = BotTorpedoAim.forTarget(ship, target);
            if (aim == null || botTorpedoPathBlockedByLand(muzzlePosition, aim.targetPosition())) return false;
        }
        if (target != null && torpedoLaunchWouldHitFriendlyShip(ship, muzzlePosition, heading, launchY)) {
            return false;
        }
        ship.markFired(nowSeconds, cooldownSeconds);
        boolean underwater = ship.isSubmarine() && ship.y() < -1.87 * TORPEDO_BOAT_MODEL_SCALE;
        Torpedo torpedo = new Torpedo(
                "torpedo-" + nextTorpedoId++,
                ship.teamId(),
                ship.id(),
                muzzlePosition,
                heading,
                BotTorpedoAim.speed(ship),
                nowSeconds,
                RadarService.TORPEDO_RANGE,
                tubeSide
        );
        if (underwater) {
            torpedo = Torpedo.submerged(torpedo.id(), ship.teamId(), ship.id(), muzzlePosition, heading,
                    torpedo.speed(), nowSeconds, RadarService.TORPEDO_RANGE, tubeSide,
                    ship.y() + .2 * TORPEDO_BOAT_MODEL_SCALE);
        }
        torpedoes.add(torpedo);
        return true;
    }

    // Test the real fixed-tube course in the target's moving frame. No target mutation/mapping.
    private boolean botTorpedoIntercepts(Vehicle shooter, Vector2 start, Vector2 forward,
                                        Vehicle target, double y) {
        double speed = BotTorpedoAim.speed(shooter);
        Vector2 relativeVelocity = forward.scale(speed)
                .subtract(Vector2.fromHeading(target.heading()).scale(target.speed()));
        double duration = BOT_FIRE_MAX_RANGE / speed;
        Vector2 end = start.add(relativeVelocity.scale(duration));
        if (target.isSubmarine()) {
            return Double.isFinite(SubmarineProjectileGeometry.hitFraction(
                    start.x(), y, start.z(), end.x(), y, end.z(), target));
        }
        return torpedoLineHitsShip(start, relativeVelocity.normalized(), target,
                start.distanceTo(end), TORPEDO_HULL_MARGIN);
    }

    private double torpedoMuzzleForwardOffset(Vehicle ship) {
        return ship.isSubmarine() ? 5.45 : 5.0;
    }

    private double torpedoMuzzleSideOffset(Vehicle ship) {
        return ship.isSubmarine() ? 0.24 : 0.56;
    }

    Vector2 torpedoMuzzlePosition(Vehicle ship, double heading, int tubeSide) {
        Vector2 forward = Vector2.fromHeading(heading);
        Vector2 right = new Vector2(Math.cos(heading), -Math.sin(heading));
        return ship.position()
                .add(forward.scale(torpedoMuzzleForwardOffset(ship) * TORPEDO_BOAT_MODEL_SCALE))
                .add(right.scale(tubeSide * torpedoMuzzleSideOffset(ship) * TORPEDO_BOAT_MODEL_SCALE));
    }

    boolean torpedoLaunchWouldHitFriendlyShip(Vehicle shooter, Vector2 muzzlePosition, double heading) {
        return torpedoLaunchWouldHitFriendlyShip(shooter, muzzlePosition, heading, .05);
    }

    private boolean botTorpedoPathBlockedByLand(Vector2 start, Vector2 end) {
        // Only just before a possible shot. Most islands can be rejected with four comparisons.
        double minX = Math.min(start.x(), end.x()), maxX = Math.max(start.x(), end.x());
        double minZ = Math.min(start.z(), end.z()), maxZ = Math.max(start.z(), end.z());
        var obstacles = new ArrayList<Landmass>();
        for (Landmass land : worldMap.landmasses()) {
            if (land.x() + Math.abs(land.rx()) * 1.51 < minX || land.x() - Math.abs(land.rx()) * 1.51 > maxX
                    || land.z() + Math.abs(land.rz()) * 1.51 < minZ || land.z() - Math.abs(land.rz()) * 1.51 > maxZ) continue;
            obstacles.add(land);
        }
        return !obstacles.isEmpty() && LandGeometry.lineIntersectsBlockedLand(start, end,
                new WorldMap(worldMap.version(), obstacles));
    }

    boolean torpedoLaunchWouldHitFriendlyShip(Vehicle shooter, Vector2 muzzlePosition, double heading, double launchY) {
        Vector2 forward = Vector2.fromHeading(heading);
        double torpedoSpeed = SHIP_TORPEDO_BASE_SPEED + Math.max(0, shooter.speed()) * SHIP_TORPEDO_SPEED_GAIN;
        double nearbyRange = 3 * BotNavigation.SHIP_LENGTH;
        double clearance = BotNavigation.SHIP_LENGTH * .65;
        for (Vehicle friend : allShips()) {
            if (!"active".equals(friend.state()) || friend.isScoutPlane() || friend == shooter
                    || !friend.teamId().equals(shooter.teamId()) || !torpedoDepthCanDamageShip(launchY, friend)) continue;
            if (torpedoLineHitsShipAtDepth(muzzlePosition, forward, friend, launchY)) return true;
            double dx = friend.position().x() - muzzlePosition.x(), dz = friend.position().z() - muzzlePosition.z();
            if (dx * dx + dz * dz > nearbyRange * nearbyRange) continue;
            // One relative-motion calculation, not a trajectory simulation or a second fleet scan.
            double vx = Math.sin(friend.heading()) * friend.speed() - forward.x() * torpedoSpeed;
            double vz = Math.cos(friend.heading()) * friend.speed() - forward.z() * torpedoSpeed;
            double closing = dx * vx + dz * vz;
            if (closing >= 0) continue;
            double t = MathSupport.clamp(-closing / Math.max(.001, vx * vx + vz * vz), 0, 2);
            double x = dx + vx * t, z = dz + vz * t;
            if (x * x + z * z < clearance * clearance) return true;
        }
        return false;
    }

    private boolean torpedoLineHitsShipAtDepth(Vector2 start, Vector2 forward, Vehicle target, double y) {
        if (target.isSubmarine()) {
            Vector2 end = start.add(forward.scale(BOT_FIRE_MAX_RANGE));
            return Double.isFinite(SubmarineProjectileGeometry.hitFraction(
                    start.x(), y, start.z(), end.x(), y, end.z(), target));
        }
        return torpedoLineHitsShip(start, forward, target, BOT_FIRE_MAX_RANGE, TORPEDO_HULL_MARGIN);
    }

    boolean torpedoLineHitsShip(Vector2 muzzlePosition, Vector2 forward, Vehicle target, double maxRange, double margin) {
        Vector2 right = new Vector2(forward.z(), -forward.x());
        double broadRadius = (TORPEDO_BROAD_PHASE_RADIUS + margin) * TORPEDO_BOAT_MODEL_SCALE;
        Vector2 toFriendly = target.position().subtract(muzzlePosition);
        double along = toFriendly.x() * forward.x() + toFriendly.z() * forward.z();
        // A centre behind the muzzle can still have its bow around/in front of the tube.
        // Reject only when the entire bounding hull lies outside the forward firing segment.
        if (along < -broadRadius || along > maxRange + broadRadius) {
            return false;
        }
        double lateral = Math.abs(toFriendly.x() * right.x() + toFriendly.z() * right.z());
        if (lateral > broadRadius) {
            return false;
        }

        double sweepStart = Math.max(0, along - broadRadius);
        double sweepEnd = Math.min(maxRange, along + broadRadius);
        double sweepStep = Math.max(TORPEDO_SWEEP_STEP, TORPEDO_BOAT_MODEL_SCALE);
        for (double distance = sweepStart; distance <= sweepEnd; distance += sweepStep) {
            if (pointHitsShipHull(muzzlePosition.add(forward.scale(distance)), target, margin)) {
                return true;
            }
        }
        Vector2 endPoint = muzzlePosition.add(forward.scale(sweepEnd));
        return pointHitsShipHull(endPoint, target, margin);
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

    boolean fireAirTorpedo(Vehicle plane, double cooldownSeconds, double heading) {
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

    private boolean firePlayerAirTorpedo(Vehicle plane, double cooldownSeconds, double heading) {
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

    double airTorpedoSpeed(Vehicle plane) {
        return BOT_SCOUT_PLANE_AIR_TORPEDO_BASE_SPEED + Math.max(0, plane.speed()) * BOT_SCOUT_PLANE_AIR_TORPEDO_SPEED_GAIN;
    }

    private void checkGameOver() {
        state = "running";
    }

    private List<Vehicle> allShips() {
        return fleets.values().stream()
                .flatMap(fleet -> fleet.ships().stream())
                .toList();
    }

    private Optional<Vehicle> findShipById(String shipId) {
        return allShips().stream()
                .filter(ship -> ship.id().equals(shipId))
                .findFirst();
    }

    private static Map<String, Fleet> createFleets(List<FleetSetup> fleetSetups) {
        Map<String, Fleet> fleets = new LinkedHashMap<>();
        fleetSetups.forEach(setup -> fleets.put(setup.teamId(), new Fleet(setup.teamId(), createShips(setup.ships()))));
        return fleets;
    }

    private static List<Vehicle> createShips(List<VehicleSetup> shipSetups) {
        return shipSetups.stream()
                .map(GameSession::createShip)
                .toList();
    }

    private static Vehicle createShip(VehicleSetup setup) {
        Vehicle ship = Vehicle.create(setup.vehicleType(), setup.id(), setup.teamId(), setup.position(),
                MathSupport.normalizeAngle(setup.heading()), setup.controlledBy());
        ship.applyCommand(ship.isScoutPlane() ? 7 : setup.engineOrder(), setup.rudderDegrees());
        ship.y(setup.y());
        ship.nextFireTime(setup.nextFireDelaySeconds());
        return ship;
    }

    double relativeBearing(Vehicle ship, Vector2 target) {
        return MathSupport.normalizeAngle(angleTo(target, ship.position()) - ship.heading());
    }

    double angleTo(Vector2 target, Vector2 from) {
        return Math.atan2(target.x() - from.x(), target.z() - from.z());
    }

    double angularDistance(double left, double right) {
        return Math.abs(MathSupport.normalizeAngle(left - right));
    }

    double stablePhase(String value) {
        int hash = Math.abs(value.hashCode());
        return (hash % 6283) / 1000.0;
    }

    static Vector2 rightFromHeading(double heading) {
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



}
