package one.xis.seabattle.game;

import one.xis.context.Service;
import one.xis.seabattle.webapp.account.Account;
import java.util.*;

/** Membership and station changes share a lock with command authorization. */
@Service
public class CrewService {
    private static final List<String> STATIONS = List.of("bridge", "flak", "cannon");
    private final GameStateService game;
    private final SeaBattlePlayerRegistry players;
    private final Map<String, Crew> byPlayer = new HashMap<>();
    private final Map<String, Long> awaitingConnection = new HashMap<>();
    private Object round;
    private long revision;

    public CrewService(GameStateService game, SeaBattlePlayerRegistry players) {
        this.game = game;
        this.players = players;
    }

    private static class Crew {
        final String id = UUID.randomUUID().toString();
        final String team;
        String controller;
        String shipId;
        long respawnAt;
        final LinkedHashMap<String, Member> members = new LinkedHashMap<>();
        Crew(String controller, ShipSnapshot ship) {
            this.controller = controller;
            this.shipId = ship.id();
            this.team = ship.teamId();
        }
    }
    public record Member(String playerId, String name, String station, long revision) {}
    public record View(String id, String shipId, String controller, String station, long revision, List<Member> members) {}
    public record Command(String playerId, String shipId, long revision, String station,
                          PlayerStateUpdate motion, FlakFireRequest shot, Integer tubeSide) {}
    public record HitCommand(String playerId, String shipId, long revision, ClientPlaneHitRequest hit) {}
    public record AlignCommand(String playerId, String shipId, long revision, String mode) {}

    public synchronized GameSnapshot alignUnoccupiedWeapons(AlignCommand command) {
        Crew c = authorized(new Command(command.playerId(), command.shipId(), command.revision(), "bridge", null, null, null), "bridge");
        requireActive(c);
        if (!List.of("flat", "air-defense").contains(command.mode())) throw new IllegalArgumentException("Unbekannte Ausrichtung.");
        boolean air = command.mode().equals("air-defense");
        for (String station : List.of("flak", "cannon")) {
            if (c.members.values().stream().anyMatch(m -> m.station().equals(station))) continue;
            game.aimCrewWeapon(c.controller, c.team, station, station.equals("flak") ? Math.PI : 0,
                    air ? Math.toRadians(station.equals("flak") ? 18 : 20) : 0);
        }
        return game.snapshot();
    }

    private void refreshRound() {
        Object current = game.recruitmentRoundIdentity();
        if (round == current) return;
        var surviving = new HashSet<>(byPlayer.values());
        for (Crew c : surviving) {
            c.members.keySet().removeIf(p -> !players.isRegisteredPlayer(p));
            if (!c.members.isEmpty() && !c.members.containsKey(c.controller)) c.controller = c.members.keySet().iterator().next();
        }
        byPlayer.entrySet().removeIf(e -> !e.getValue().members.containsKey(e.getKey()));
        surviving.removeIf(c -> c.members.isEmpty());
        // Team activation can rebuild the world, so activate all teams before assigning ships.
        for (Crew c : surviving) game.activateTeam(c.team);
        for (Crew c : surviving) {
            game.assignPlayerVehicle(c.controller, c.team, "torpedo-boat");
            c.members.replaceAll((id, m) -> new Member(id, m.name(), m.station(), ++revision));
        }
        round = game.recruitmentRoundIdentity();
    }

    synchronized void synchronizeWorld() { refreshRound(); }

    private Crew crew(String player) {
        refreshRound();
        if (!players.isRegisteredPlayer(player)) return null;
        Crew crew = byPlayer.get(player);
        if (crew == null) {
            var ship = game.snapshot().ships().stream().filter(s -> player.equals(s.controlledBy())
                    && "torpedo-boat".equals(s.vehicleType()) && "active".equals(s.state())).findFirst().orElse(null);
            if (ship == null) return null;
            crew = new Crew(player, ship);
            crew.members.put(player, new Member(player, players.playerName(players.aliasForPlayer(player)), "bridge", ++revision));
            byPlayer.put(player, crew);
        }
        return crew;
    }

    public synchronized boolean managed(String player) { return crew(player) != null; }
    public synchronized String identity(String player) { var c = crew(player); return c == null ? player : c.id; }
    public synchronized boolean exists(String identity) {
        refreshRound();
        return byPlayer.values().stream().anyMatch(c -> c.id.equals(identity)
                && c.members.keySet().stream().anyMatch(players::isRegisteredPlayer));
    }
    public synchronized String contact(String identity) {
        refreshRound();
        return byPlayer.values().stream().filter(c -> c.id.equals(identity)).map(c -> c.controller).findFirst().orElse(null);
    }
    public synchronized boolean hasAccount(String accountId) {
        refreshRound();
        return byPlayer.keySet().stream().anyMatch(p -> accountId.equals(players.accountIdForPlayer(p)));
    }

    public synchronized void connected(String player) { awaitingConnection.remove(player); }

    @one.xis.context.Scheduled(fixedDelay = 1, timeUnit = java.util.concurrent.TimeUnit.SECONDS)
    public synchronized void expireUnclaimedPlaces() {
        refreshRound();
        var expired = awaitingConnection.entrySet().stream().filter(e -> e.getValue() <= System.currentTimeMillis())
                .map(Map.Entry::getKey).toList();
        for (String player : expired) { leave(player); players.unregisterPlayer(player); }
    }

    public synchronized View view(String player) {
        Crew c = crew(player);
        if (c == null) return null;
        var ship = game.snapshot().ships().stream().filter(s -> c.controller.equals(s.controlledBy())
                && "active".equals(s.state())).findFirst().orElse(null);
        if (ship != null) { c.shipId = ship.id(); c.respawnAt = 0; }
        else if (c.respawnAt == 0) c.respawnAt = System.currentTimeMillis() + 6000;
        else if (System.currentTimeMillis() >= c.respawnAt) {
            game.assignPlayerVehicle(c.controller, c.team, "torpedo-boat");
            c.respawnAt = System.currentTimeMillis() + 1000;
        }
        Member m = c.members.get(player);
        return new View(c.id, c.shipId, c.controller, m.station(), m.revision(), List.copyOf(c.members.values()));
    }

    public synchronized View join(String recipient, Account applicant) {
        Crew c = crew(recipient);
        if (c == null || !c.team.equals(applicant.team())) throw new IllegalArgumentException("Schiff nicht verfuegbar.");
        if (players.isAliasRegistered(applicant.alias()) || hasAccount(applicant.id())) throw new IllegalArgumentException("Bereits an Bord.");
        String station = STATIONS.stream().filter(s -> c.members.values().stream().noneMatch(m -> s.equals(m.station())))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Alle Positionen sind belegt."));
        if (game.snapshot().ships().stream().noneMatch(s -> s.id().equals(c.shipId) && "active".equals(s.state())))
            throw new IllegalArgumentException("Schiff nicht aktiv.");
        String player = "player-" + applicant.alias() + "-" + UUID.randomUUID().toString().substring(0, 12);
        players.register(player, applicant.alias(), applicant.nickname(), applicant.team(), applicant.id());
        c.members.put(player, new Member(player, applicant.nickname(), station, ++revision));
        byPlayer.put(player, c);
        awaitingConnection.put(player, System.currentTimeMillis() + 120_000);
        return view(player);
    }

    public synchronized View switchStation(Command command) {
        Crew c = authorized(command, null);
        if (!STATIONS.contains(command.station())) throw new IllegalArgumentException("Unbekannte Position.");
        if (c.members.values().stream().anyMatch(m -> m.station().equals(command.station()) && !m.playerId().equals(command.playerId())))
            throw new IllegalArgumentException("Position bereits belegt.");
        Member old = c.members.get(command.playerId());
        if (!old.station().equals(command.station()))
            c.members.put(old.playerId(), new Member(old.playerId(), old.name(), command.station(), ++revision));
        return view(command.playerId());
    }

    private Crew authorized(Command command, String station) {
        Crew c = crew(command.playerId());
        if (c == null) throw new IllegalArgumentException("Nicht an Bord.");
        Member m = c.members.get(command.playerId());
        if (m.revision() != command.revision() || !c.shipId.equals(command.shipId())
                || (station != null && !station.equals(m.station())))
            throw new IllegalArgumentException("Position nicht mehr zugeteilt.");
        return c;
    }

    public synchronized GameSnapshot motion(Command command) {
        Crew c = authorized(command, "bridge");
        PlayerStateUpdate u = Objects.requireNonNull(command.motion());
        requireActive(c);
        return game.updatePlayerState(new PlayerStateUpdate(c.controller, c.team, u.x(), u.z(), u.heading(), u.speed(),
                u.turnVelocity(), u.engineOrder(), u.rudderDegrees(), u.clientTime(), false, "torpedo-boat",
                0, 0, null, null, null, null, "surface"));
    }

    public synchronized GameSnapshot aim(Command command) {
        Crew c = authorized(command, command.station());
        if (!List.of("flak", "cannon").contains(command.station())) throw new IllegalArgumentException("Kein Geschuetz.");
        requireActive(c);
        var u = Objects.requireNonNull(command.motion());
        Double yaw = command.station().equals("flak") ? u.flakYaw() : u.cannonYaw();
        Double pitch = command.station().equals("flak") ? u.flakPitch() : u.cannonPitch();
        if (yaw == null || pitch == null || !Double.isFinite(yaw) || !Double.isFinite(pitch)) throw new IllegalArgumentException("Ungueltige Ausrichtung.");
        return game.aimCrewWeapon(c.controller, c.team, command.station(), yaw, pitch);
    }

    public synchronized GameSnapshot fire(Command command, String station) {
        Crew c = authorized(command, station.equals("torpedo") ? "bridge" : station);
        requireActive(c);
        if (station.equals("torpedo")) {
            return game.fireTorpedo(new FireTorpedoRequest(c.controller, c.team, "torpedo-boat", 0, 0, 0, 0, 0, 2, 0,
                    0, 0, command.tubeSide(), 0, "surface"));
        }
        var s = Objects.requireNonNull(command.shot());
        var shot = new FlakFireRequest(c.controller, c.team, c.shipId, s.x(), s.y(), s.z(), s.vx(), s.vy(), s.vz(), s.weaponYaw(), s.weaponPitch());
        return station.equals("flak") ? game.fireFlak(shot, command.playerId()) : game.fireCannon(shot, command.playerId());
    }

    public synchronized GameSnapshot planeHit(HitCommand command) {
        var hit = Objects.requireNonNull(command.hit());
        if (!List.of("flak", "cannon").contains(hit.weaponType())) throw new IllegalArgumentException("Unbekannte Waffe.");
        Crew c = authorized(new Command(command.playerId(), command.shipId(), command.revision(), null, null, null, null), hit.weaponType());
        requireActive(c);
        return game.reportClientPlaneHit(new ClientPlaneHitRequest(c.controller, c.team, c.shipId, hit.targetShipId(), hit.weaponType(), hit.x(), hit.y(), hit.z()));
    }

    private void requireActive(Crew c) {
        if (game.snapshot().ships().stream().noneMatch(s -> s.id().equals(c.shipId) && c.controller.equals(s.controlledBy()) && "active".equals(s.state())))
            throw new IllegalArgumentException("Schiff nicht aktiv.");
    }

    public synchronized void leave(String player) {
        refreshRound();
        awaitingConnection.remove(player);
        Crew c = byPlayer.remove(player);
        if (c == null) return;
        c.members.remove(player);
        if (c.controller.equals(player) && !c.members.isEmpty()) {
            String next = c.members.keySet().iterator().next();
            game.transferCrewController(player, next, c.team);
            c.controller = next;
        }
    }
}
