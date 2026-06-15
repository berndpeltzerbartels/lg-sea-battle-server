package one.xis.seabattle;

import one.xis.Action;
import one.xis.FormData;
import one.xis.ModelData;
import one.xis.Page;
import one.xis.PageUrlResponse;
import one.xis.WelcomePage;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@WelcomePage
@Page("/index.html")
public class SeaBattleStartPage {

    private static final List<TeamOption> TEAMS = List.of(
            new TeamOption("light", "Light", "Helle Flotte", "Kuestennah, gut sichtbar"),
            new TeamOption("dark", "Dark", "Dunkle Flotte", "Tarnung im Abendlicht"),
            new TeamOption("green", "Green", "Gruene Flotte", "Zwischen Inseln schwerer zu lesen"),
            new TeamOption("sand", "Sand", "Sand-Flotte", "Unauffaellig an Kueste und Strand")
    );

    private final GameStateService gameStateService;

    public SeaBattleStartPage(GameStateService gameStateService) {
        this.gameStateService = gameStateService;
    }

    @FormData("start")
    SeaBattleStartForm start() {
        return new SeaBattleStartForm("", "light");
    }

    @ModelData("teams")
    List<TeamOption> teams() {
        Map<String, Long> occupiedShips = gameStateService.snapshot().ships().stream()
                .filter(ship -> ship.controlledBy() != null && !ship.controlledBy().isBlank())
                .collect(Collectors.groupingBy(ShipSnapshot::teamId, Collectors.counting()));
        Map<String, Long> activeShips = gameStateService.snapshot().ships().stream()
                .filter(ship -> "active".equals(ship.state()))
                .collect(Collectors.groupingBy(ShipSnapshot::teamId, Collectors.counting()));

        return TEAMS.stream()
                .map(team -> team.withCounts(
                        occupiedShips.getOrDefault(team.id(), 0L).intValue(),
                        activeShips.getOrDefault(team.id(), 0L).intValue()))
                .toList();
    }

    @ModelData("players")
    List<PlayerEntry> players() {
        Map<String, Integer> kills = gameStateService.snapshot().killsByPlayer();
        return gameStateService.snapshot().ships().stream()
                .filter(ship -> ship.controlledBy() != null && ship.controlledBy().startsWith("player-"))
                .map(ship -> new PlayerEntry(
                        playerInitials(ship.controlledBy()),
                        teamLabel(ship.teamId()),
                        ship.id().toUpperCase(Locale.ROOT).replace("-", " "),
                        kills.getOrDefault(ship.controlledBy(), 0),
                        mapSector(ship.x(), ship.z())))
                .sorted(Comparator.comparing(PlayerEntry::team).thenComparing(PlayerEntry::initials))
                .toList();
    }

    @Action
    PageUrlResponse startGame(@FormData("start") SeaBattleStartForm form) {
        String initials = form.initials().toUpperCase(Locale.ROOT);
        String url = "/sea-battle/app?team=" + encode(form.team()) + "&initials=" + encode(initials);
        return new PageUrlResponse(url);
    }

    private String teamLabel(String teamId) {
        return TEAMS.stream()
                .filter(team -> team.id().equals(teamId))
                .map(TeamOption::label)
                .findFirst()
                .orElse(teamId);
    }

    private String playerInitials(String playerId) {
        String prefix = "player-";
        if (!playerId.startsWith(prefix)) {
            return playerId;
        }
        int end = playerId.indexOf('-', prefix.length());
        if (end <= prefix.length()) {
            return playerId.substring(prefix.length()).toUpperCase(Locale.ROOT);
        }
        return playerId.substring(prefix.length(), end).toUpperCase(Locale.ROOT);
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

    public record TeamOption(String id, String label, String name, String description, int players, int activeShips) {
        TeamOption(String id, String label, String name, String description) {
            this(id, label, name, description, 0, 0);
        }

        TeamOption withCounts(int players, int activeShips) {
            return new TeamOption(id, label, name, description, players, activeShips);
        }
    }

    public record PlayerEntry(String initials, String team, String ship, int kills, String sector) {
    }
}
