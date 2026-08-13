package one.xis.seabattle.webapp;

import one.xis.*;
import one.xis.seabattle.game.GameSnapshot;
import one.xis.seabattle.game.GameStateService;
import one.xis.seabattle.game.SeaBattlePlayerRegistry;
import one.xis.seabattle.game.ShipSnapshot;
import one.xis.seabattle.webapp.account.AccountService;

import java.util.*;
import java.util.stream.Collectors;

@WelcomePage
@Page("/portal.html")
public class SeaBattleStartPage {

    private static final List<TeamOption> PUBLIC_TEAMS = List.of(
            new TeamOption("light", "Light"),
            new TeamOption("dark", "Dark")
    );

    private static final List<TeamOption> ALL_TEAMS = List.of(
            new TeamOption("light", "Light"),
            new TeamOption("dark", "Dark"),
            new TeamOption("green", "Green"),
            new TeamOption("sand", "Sand")
    );

    private final GameStateService gameStateService;
    private final SeaBattlePlayerRegistry playerRegistry;
    private final AccountService accountService;

    public SeaBattleStartPage(GameStateService gameStateService, SeaBattlePlayerRegistry playerRegistry,
                              AccountService accountService) {
        this.gameStateService = gameStateService;
        this.playerRegistry = playerRegistry;
        this.accountService = accountService;
    }

    @ModelData("accountStatus")
    AccountStatus accountStatus(@NullAllowed @LocalStorage("accountId") String accountId) {
        return accountService.findAccountById(accountId)
                .map(account -> new AccountStatus(account.nickname() + " (" + account.alias() + ")"))
                .orElseGet(() -> new AccountStatus("Noch nicht angemeldet"));
    }

    @ModelData("teams")
    List<TeamOption> teams() {
        Map<String, List<PlayerEntry>> playersByTeam = players().stream()
                .collect(Collectors.groupingBy(PlayerEntry::teamId, LinkedHashMap::new, Collectors.toList()));

        return PUBLIC_TEAMS.stream()
                .map(team -> team.withPlayers(playersByTeam.getOrDefault(team.id(), List.of())))
                .toList();
    }

    @ModelData("teamOptions")
    List<TeamOption> teamOptions() {
        return PUBLIC_TEAMS;
    }

    @ModelData("players")
    List<PlayerEntry> players() {
        GameSnapshot snapshot = gameStateService.snapshot();
        Map<String, Integer> kills = snapshot.killsByPlayer();
        Map<String, ShipSnapshot> shipsByPlayer = snapshot.ships().stream()
                .filter(ship -> ship.controlledBy() != null && ship.controlledBy().startsWith("player-"))
                .collect(Collectors.toMap(ShipSnapshot::controlledBy, ship -> ship, (left, ignored) -> left));

        return playerRegistry.players().stream()
                .map(player -> {
                    ShipSnapshot ship = player.playerId() == null ? null : shipsByPlayer.get(player.playerId());
                    String teamId = ship == null ? player.teamId() : ship.teamId();
                    String playerId = player.playerId();
                    return new PlayerEntry(
                            displayName(player.nickname()),
                            player.initials(),
                            teamId,
                            teamLabel(teamId),
                            ship == null ? "-" : ship.id().toUpperCase(Locale.ROOT).replace("-", " "),
                            playerId == null ? 0 : kills.getOrDefault(playerId, 0),
                            ship == null ? "-" : mapSector(ship.x(), ship.z()));
                })
                .sorted(Comparator.comparing(PlayerEntry::team).thenComparing(PlayerEntry::initials))
                .toList();
    }

    private String displayName(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String teamLabel(String teamId) {
        return ALL_TEAMS.stream()
                .filter(team -> team.id().equals(teamId))
                .map(TeamOption::label)
                .findFirst()
                .orElse(teamId);
    }

    private String mapSector(double x, double z) {
        int sectorSize = 600;
        int origin = 5400;
        int column = Math.max(0, Math.min(25, (int) Math.floor((x + origin) / sectorSize)));
        int row = Math.max(0, Math.min(99, (int) Math.floor((z + origin) / sectorSize)));
        return String.valueOf((char) ('A' + column)) + row;
    }

    public record TeamOption(String id, String label, List<PlayerEntry> players) {
        TeamOption(String id, String label) {
            this(id, label, List.of());
        }

        TeamOption withPlayers(List<PlayerEntry> players) {
            return new TeamOption(id, label, List.copyOf(players));
        }
    }

    public record AccountStatus(String label) {
    }

    public record PlayerEntry(String name, String initials, String teamId, String team, String ship, int kills,
                              String sector) {
    }
}
