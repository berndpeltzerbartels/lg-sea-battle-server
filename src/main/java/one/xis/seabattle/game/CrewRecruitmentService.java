package one.xis.seabattle.game;

import one.xis.context.Service;
import one.xis.context.Scheduled;
import one.xis.RefreshEventPublisher;
import one.xis.seabattle.webapp.account.Account;
import java.util.*;

/** Waiting applicants are deliberately not registered as active ship controllers. */
@Service
public class CrewRecruitmentService {
    public static final String UPDATE_EVENT = "crew-recruitment-updated";
    private final GameStateService game;
    private final SeaBattlePlayerRegistry players;
    private final CrewService crew;
    private final Map<String, Request> requests = new LinkedHashMap<>();
    private final Map<RetryKey, Long> retryAfter = new LinkedHashMap<>();
    private Object round;
    private final RefreshEventPublisher events;
    private List<?> previousView = List.of();

    public CrewRecruitmentService(GameStateService game, SeaBattlePlayerRegistry players, RefreshEventPublisher events, CrewService crew) {
        this.game = game;
        this.players = players;
        this.events = events;
        this.crew = crew;
    }

    long nowMillis() { return System.currentTimeMillis(); }

    @Scheduled(fixedDelay = 1, timeUnit = java.util.concurrent.TimeUnit.SECONDS)
    public void publishChanges() {
        boolean changed;
        synchronized (this) {
            var snapshot = refresh();
            var roster = snapshot.ships().stream()
                    .filter(s -> "active".equals(s.state()) && "torpedo-boat".equals(s.vehicleType())
                            && players.isRegisteredPlayer(s.controlledBy()))
                    .map(s -> List.of(s.id(), s.teamId(), s.controlledBy(), players.playerName(players.aliasForPlayer(s.controlledBy()))))
                    .toList();
            var view = List.of(roster, List.copyOf(requests.values()), Map.copyOf(retryAfter));
            changed = !view.equals(previousView);
            previousView = view;
        }
        if (changed) events.publishToAll(UPDATE_EVENT);
    }

    public synchronized List<ShipOption> ships(Account account) {
        var snapshot = refresh();
        boolean pending = requests.values().stream().anyMatch(r -> r.accountId().equals(account.id()) && r.pending());
        return snapshot.ships().stream().filter(ship -> eligible(ship, account))
                .map(ship -> {
                    boolean blocked = retryAfter.containsKey(new RetryKey(account.id(), crew.identity(ship.controlledBy())));
                    var previous = requests.get(key(account.id(), ship.id()));
                    String status = previous != null && previous.pending() ? previous.status()
                            : blocked ? "Wartezeit" : "Verfuegbar";
                    return new ShipOption(ship.id(), players.playerName(players.aliasForPlayer(ship.controlledBy())),
                            status, !blocked && !pending);
                }).toList();
    }

    public synchronized List<Request> requests(Account account) {
        refresh();
        return requests.values().stream().filter(r -> r.accountId().equals(account.id())).toList();
    }

    public synchronized void request(Account account, String shipId) {
        var snapshot = refresh();
        if (crew.hasAccount(account.id()) || snapshot.ships().stream().anyMatch(s -> account.id().equals(players.accountIdForPlayer(s.controlledBy())))) {
            throw new IllegalArgumentException("Du bist bereits einem Schiff zugeteilt.");
        }
        if (requests.values().stream().anyMatch(r -> r.accountId().equals(account.id()) && r.pending())) {
            throw new IllegalArgumentException("Es ist bereits eine Anfrage offen.");
        }
        var ship = snapshot.ships().stream().filter(s -> s.id().equals(shipId) && eligible(s, account))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Dieses Schiff ist nicht mehr verfuegbar."));
        if (retryAfter.containsKey(new RetryKey(account.id(), crew.identity(ship.controlledBy())))) {
            throw new IllegalArgumentException("Die Wartezeit fuer diese Besatzung ist noch nicht abgelaufen.");
        }
        // Reinsert so the latest attempt remains last in the applicant's history.
        requests.remove(key(account.id(), ship.id()));
        requests.put(key(account.id(), ship.id()), new Request(account.id(), ship.id(),
                ship.controlledBy(), "Offen", nowMillis() + 120_000,
                UUID.randomUUID().toString(), account.nickname(), account.alias(), crew.identity(ship.controlledBy())));
    }

    public synchronized List<Request> inbox(Account recipient) {
        refresh();
        return requests.values().stream().filter(r -> r.status().equals("Offen")
                && recipient.id().equals(players.accountIdForPlayer(crew.contact(r.crewId())))).toList();
    }

    public synchronized void decide(Account recipient, String requestId, boolean accept) {
        refresh();
        Request request = requests.values().stream().filter(r -> r.id().equals(requestId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Anfrage nicht mehr vorhanden."));
        String contact = crew.contact(request.crewId());
        if (!recipient.id().equals(players.accountIdForPlayer(contact))) {
            throw new IllegalArgumentException("Keine Berechtigung fuer diese Anfrage.");
        }
        String decision = accept ? "Angenommen" : "Abgelehnt";
        if (request.status().equals(decision)) return;
        if (!request.status().equals("Offen")) throw new IllegalArgumentException("Anfrage nicht mehr offen.");
        if (accept) crew.join(contact, new Account(request.accountId(), request.nickname(), request.alias(), recipient.team(), null));
        requests.put(key(request.accountId(), request.shipId()), request.withStatus(decision,
                accept ? nowMillis() + 120_000 : request.expiresAt()));
        if (!accept) retryAfter.put(new RetryKey(request.accountId(), request.crewId()), nowMillis() + 900_000);
    }

    private GameSnapshot refresh() {
        crew.synchronizeWorld();
        var snapshot = game.snapshot();
        Object currentRound = game.recruitmentRoundIdentity();
        if (round != currentRound) {
            requests.clear();
            retryAfter.clear();
            round = currentRound;
        }
        requests.replaceAll((key, request) -> {
            if (request.status().equals("Angenommen") && !crew.hasAccount(request.accountId())) {
                retryAfter.put(new RetryKey(request.accountId(), request.crewId()), nowMillis() + 120_000);
                return request.withStatus("Abgelaufen", request.expiresAt());
            }
            boolean unavailable = snapshot.ships().stream().noneMatch(ship -> ship.id().equals(request.shipId())
                    && "active".equals(ship.state()) && Objects.equals(ship.controlledBy(), crew.contact(request.crewId()))
                    && players.isRegisteredPlayer(ship.controlledBy()));
            if (request.pending() && (nowMillis() >= request.expiresAt() || unavailable)) {
                long expiredAt = Math.min(nowMillis(), request.expiresAt());
                retryAfter.put(new RetryKey(request.accountId(), request.crewId()), expiredAt + 120_000);
                return request.withStatus("Abgelaufen", request.expiresAt());
            }
            return request;
        });
        // A crew identity survives respawn and replacement of its admission contact.
        retryAfter.entrySet().removeIf(entry -> entry.getValue() <= nowMillis()
                || !crew.exists(entry.getKey().recipientPlayerId()));
        return snapshot;
    }

    private boolean eligible(ShipSnapshot ship, Account account) {
        return "active".equals(ship.state()) && "torpedo-boat".equals(ship.vehicleType())
                && Objects.equals(account.team(), ship.teamId()) && players.isRegisteredPlayer(ship.controlledBy())
                && !account.id().equals(players.accountIdForPlayer(ship.controlledBy()));
    }

    private String key(String accountId, String shipId) { return accountId + ":" + shipId; }
    private record RetryKey(String accountId, String recipientPlayerId) {}
    public record ShipOption(String id, String captain, String status, boolean available) {}
    public record Request(String accountId, String shipId, String recipientPlayerId, String status, long expiresAt,
                          String id, String nickname, String alias, String crewId) {
        boolean pending() { return status.equals("Offen"); }
        Request withStatus(String status, long expiresAt) {
            return new Request(accountId, shipId, recipientPlayerId, status, expiresAt, id, nickname, alias, crewId);
        }
    }
}
