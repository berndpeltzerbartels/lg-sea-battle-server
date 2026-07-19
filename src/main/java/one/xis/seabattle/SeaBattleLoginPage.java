package one.xis.seabattle;

import one.xis.Action;
import one.xis.FormData;
import one.xis.LocalStorage;
import one.xis.ModelData;
import one.xis.NullAllowed;
import one.xis.Page;
import one.xis.PageUrlResponse;
import one.xis.validation.LabelKey;
import one.xis.validation.Mandatory;
import one.xis.validation.RegExpr;
import one.xis.validation.ValidationFailedException;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Page("/start.html")
public class SeaBattleLoginPage {

    private static final List<TeamOption> PUBLIC_TEAMS = List.of(
            new TeamOption("light", "Light"),
            new TeamOption("dark", "Dark")
    );

    private static final List<VehicleOption> VEHICLE_OPTIONS = List.of(
            new VehicleOption("torpedo-boat", "Torpedoboot"),
            new VehicleOption("scout-plane", "Aufklärungsflugzeug")
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
    private final GameService gameService;
    private final PlaySessionService playSessionService;

    public SeaBattleLoginPage(GameStateService gameStateService, SeaBattlePlayerRegistry playerRegistry,
                              AccountService accountService,
                              GameService gameService,
                              PlaySessionService playSessionService) {
        this.gameStateService = gameStateService;
        this.playerRegistry = playerRegistry;
        this.accountService = accountService;
        this.gameService = gameService;
        this.playSessionService = playSessionService;
    }

    @FormData("login")
    LoginForm login(@NullAllowed @LocalStorage("accountId") String accountId,
                    @NullAllowed @LocalStorage("vehicleType") String vehicleType) {
        Account account = accountService.findAccountById(accountId).orElseGet(this::newAccount);
        return new LoginForm(
                account.id(),
                account.nickname(),
                account.alias(),
                account.team(),
                account.email(),
                normalizeVehicleType(vehicleType)
        );
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

    @ModelData("vehicleOptions")
    List<VehicleOption> vehicleOptions() {
        return VEHICLE_OPTIONS;
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
    PageUrlResponse startGame(@FormData("login") LoginForm form) {
        Account account = normalizeAccount(form);
        String initials = account.alias();
        if (isAliasActive(initials, account.id())) {
            throw new ValidationFailedException("/login/alias", "seaBattle.aliasTaken");
        }
        Account savedAccount = accountService.saveAccount(account);
        startOrFindSession(savedAccount);
        String vehicleType = normalizeVehicleType(form.vehicleType());
        String target = "scout-plane".equals(vehicleType) ? "/app?vehicle=scout-plane" : "/app";
        return new PageUrlResponse(target)
                .localStorage("accountId", savedAccount.id())
                .localStorage("vehicleType", vehicleType);
    }

    private void startOrFindSession(Account account) {
        String initials = account.alias() == null ? "" : account.alias().trim().toUpperCase(Locale.ROOT);
        String teamId = account.team() == null ? "" : account.team().trim().toLowerCase(Locale.ROOT);
        String playerId = playerRegistry.activePlayerIdForAccountAlias(account.id(), initials);
        if (playerId == null || playerId.isBlank()) {
            playerId = createPlayerId(initials);
        }
        boolean gameIsEmpty = playerRegistry.players().isEmpty();
        playerRegistry.register(playerId, initials, normalizeName(account.nickname()), teamId, account.id());
        if (gameIsEmpty) {
            gameStateService.resetCurrentSetup();
        }
        gameStateService.activateTeam(teamId);
    }

    private String createPlayerId(String initials) {
        return "player-" + initials + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private Account newAccount() {
        return new Account(UUID.randomUUID().toString(), "", "", "", "");
    }

    private Account normalizeAccount(LoginForm form) {
        return new Account(
                form.id() == null || form.id().isBlank() ? UUID.randomUUID().toString() : form.id(),
                normalizeName(form.nickname()),
                form.alias() == null ? "" : form.alias().trim().toUpperCase(Locale.ROOT),
                form.team(),
                normalizeEmail(form.email()));
    }

    private String normalizeVehicleType(String vehicleType) {
        return "scout-plane".equals(vehicleType) ? "scout-plane" : "torpedo-boat";
    }

    private boolean isAliasActive(String initials, String accountId) {
        return playerRegistry.isAliasRegisteredForOtherAccount(initials, accountId)
                || playSessionService.isAliasActiveForOtherAccount(gameService.activeGameId(), initials, accountId);
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

    public record VehicleOption(String id, String label) {
    }

    public record LoginForm(
            String id,

            @Mandatory
            @RegExpr("[\\p{L}0-9 .'-]{2,40}")
            @LabelKey("seaBattle.nickname")
            String nickname,

            @Mandatory
            @RegExpr("[A-Za-z0-9]{1,5}")
            @LabelKey("seaBattle.alias")
            String alias,

            @Mandatory
            @RegExpr("light|dark")
            @LabelKey("seaBattle.team")
            String team,

            @LabelKey("seaBattle.email")
            String email,

            @Mandatory
            @RegExpr("torpedo-boat|scout-plane")
            String vehicleType
    ) {
    }

    public record PlayerEntry(String name, String initials, String teamId, String team, String ship, int kills, String sector) {
    }
}
