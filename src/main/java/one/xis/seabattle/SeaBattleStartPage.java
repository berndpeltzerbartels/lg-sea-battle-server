package one.xis.seabattle;

import one.xis.Action;
import one.xis.FormData;
import one.xis.LocalStorage;
import one.xis.ModelData;
import one.xis.NullAllowed;
import one.xis.Page;
import one.xis.PageUrlResponse;
import one.xis.WelcomePage;
import one.xis.validation.ValidationFailedException;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@WelcomePage
@Page("/index.html")
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

    @FormData("account")
    Account account(@NullAllowed @LocalStorage("accountId") String accountId) {
        return accountService.findAccountById(accountId).orElseGet(this::newAccount);
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

    @Action
    PageUrlResponse startGame(@FormData("account") Account form) {
        Account account = normalizeAccount(form);
        String initials = account.alias();
        if (isAliasActive(initials, account.id())) {
            throw new ValidationFailedException("/account/alias", "seaBattle.aliasTaken");
        }
        Account savedAccount = accountService.saveAccount(account);
        return new PageUrlResponse("/sea-battle/app")
                .localStorage("accountId", savedAccount.id());
    }

    private Account newAccount() {
        return new Account(UUID.randomUUID().toString(), "", "", "", "");
    }

    private Account normalizeAccount(Account account) {
        return new Account(
                account.id(),
                normalizeName(account.nickname()),
                account.alias() == null ? "" : account.alias().trim().toUpperCase(Locale.ROOT),
                account.team(),
                normalizeEmail(account.email()));
    }

    private boolean isAliasActive(String initials, String accountId) {
        return playerRegistry.isAliasRegisteredForOtherAccount(initials, accountId);
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private String normalizeEmail(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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

    public record PlayerEntry(String name, String initials, String teamId, String team, String ship, int kills, String sector) {
    }
}
