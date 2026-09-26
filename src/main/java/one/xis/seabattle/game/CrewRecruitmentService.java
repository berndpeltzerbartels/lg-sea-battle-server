package one.xis.seabattle.game;

import one.xis.context.Service;
import one.xis.context.Scheduled;
import one.xis.RefreshEventPublisher;
import one.xis.seabattle.webapp.account.Account;
import java.time.Clock;
import java.util.*;

/** Waiting applicants are deliberately not registered as active ship controllers. */
@Service
public class CrewRecruitmentService {
    public static final String UPDATE_EVENT = "crew-recruitment-updated";
    private final GameStateService game;
    private final SeaBattlePlayerRegistry players;
    private final Clock clock = Clock.systemUTC();
    private final Map<String, Request> requests = new LinkedHashMap<>();
    private Object round;
    private final RefreshEventPublisher events;
    private List<?> previousView = List.of();

    public CrewRecruitmentService(GameStateService game, SeaBattlePlayerRegistry players, RefreshEventPublisher events) {
        this.game = game;
        this.players = players;
        this.events = events;
    }

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
            var view = List.of(roster, List.copyOf(requests.values()));
            changed = !view.equals(previousView);
            previousView = view;
        }
        if (changed) events.publishToAll(UPDATE_EVENT);
    }

    public synchronized List<ShipOption> ships(Account account) {
        var snapshot = refresh();
        boolean pending = requests.values().stream().anyMatch(r -> r.accountId().equals(account.id()) && r.status().equals("Offen"));
        return snapshot.ships().stream().filter(ship -> eligible(ship, account))
                .map(ship -> {
                    var previous = requests.get(key(account.id(), ship.id()));
                    String status = previous == null ? "Verfuegbar" : previous.status();
                    return new ShipOption(ship.id(), players.playerName(players.aliasForPlayer(ship.controlledBy())),
                            status, previous == null && !pending);
                }).toList();
    }

    public synchronized List<Request> requests(Account account) {
        refresh();
        return requests.values().stream().filter(r -> r.accountId().equals(account.id())).toList();
    }

    public synchronized void request(Account account, String shipId) {
        var snapshot = refresh();
        if (snapshot.ships().stream().anyMatch(s -> account.id().equals(players.accountIdForPlayer(s.controlledBy())))) {
            throw new IllegalArgumentException("Du bist bereits einem Schiff zugeteilt.");
        }
        if (requests.containsKey(key(account.id(), shipId))) {
            throw new IllegalArgumentException("Dieses Schiff wurde in dieser Partie bereits angefragt.");
        }
        if (requests.values().stream().anyMatch(r -> r.accountId().equals(account.id()) && r.status().equals("Offen"))) {
            throw new IllegalArgumentException("Es ist bereits eine Anfrage offen.");
        }
        var ship = snapshot.ships().stream().filter(s -> s.id().equals(shipId) && eligible(s, account))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Dieses Schiff ist nicht mehr verfuegbar."));
        requests.put(key(account.id(), ship.id()), new Request(account.id(), ship.id(),
                ship.controlledBy(), "Offen", clock.millis() + 120_000));
    }

    private GameSnapshot refresh() {
        var snapshot = game.snapshot();
        Object currentRound = game.recruitmentRoundIdentity();
        if (round != currentRound) {
            requests.clear();
            round = currentRound;
        }
        requests.replaceAll((key, request) -> {
            boolean unavailable = snapshot.ships().stream().noneMatch(ship -> ship.id().equals(request.shipId())
                    && "active".equals(ship.state()) && Objects.equals(ship.controlledBy(), request.recipientPlayerId())
                    && players.isRegisteredPlayer(ship.controlledBy()));
            return request.status().equals("Offen") && (clock.millis() >= request.expiresAt() || unavailable)
                    ? new Request(request.accountId(), request.shipId(), request.recipientPlayerId(), "Abgelaufen", request.expiresAt())
                    : request;
        });
        return snapshot;
    }

    private boolean eligible(ShipSnapshot ship, Account account) {
        return "active".equals(ship.state()) && "torpedo-boat".equals(ship.vehicleType())
                && Objects.equals(account.team(), ship.teamId()) && players.isRegisteredPlayer(ship.controlledBy())
                && !account.id().equals(players.accountIdForPlayer(ship.controlledBy()));
    }

    private String key(String accountId, String shipId) { return accountId + ":" + shipId; }
    public record ShipOption(String id, String captain, String status, boolean available) {}
    public record Request(String accountId, String shipId, String recipientPlayerId, String status, long expiresAt) {}
}
