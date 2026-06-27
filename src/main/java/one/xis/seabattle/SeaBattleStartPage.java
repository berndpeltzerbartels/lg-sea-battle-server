package one.xis.seabattle;

import one.xis.Action;
import one.xis.FormData;
import one.xis.ModelData;
import one.xis.Page;
import one.xis.PageUrlResponse;
import one.xis.WelcomePage;
import one.xis.validation.ValidationFailedException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    public SeaBattleStartPage(GameStateService gameStateService, SeaBattlePlayerRegistry playerRegistry) {
        this.gameStateService = gameStateService;
        this.playerRegistry = playerRegistry;
    }

    @FormData("start")
    SeaBattleStartForm start() {
        return new SeaBattleStartForm("", "", "");
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
    PageUrlResponse startGame(@FormData("start") SeaBattleStartForm form) {
        String initials = form.alias().toUpperCase(Locale.ROOT);
        if (isAliasActive(initials)) {
            throw new ValidationFailedException("/start/alias", "seaBattle.aliasTaken");
        }
        String nickname = normalizeName(form.nickname());
        boolean gameIsEmpty = players().isEmpty();
        playerRegistry.register(initials, nickname, form.team());
        if (gameIsEmpty) {
            gameStateService.resetToSetup("dense-land");
        }
        gameStateService.activateTeam(form.team());
        String url = "/sea-battle/app?team=" + encode(form.team())
                + "&initials=" + encode(initials)
                + "&playerName=" + encode(nickname)
                + (gameIsEmpty ? "&setup=dense-land" : "");
        return new PageUrlResponse(url);
    }

    private boolean isAliasActive(String initials) {
        return playerRegistry.isAliasRegistered(initials);
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
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

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
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
