package one.xis.seabattle;

import one.xis.context.Service;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SeaBattlePlayerRegistry {

    private final Map<String, String> playerNameByAlias = new ConcurrentHashMap<>();

    public void register(String initials, String nickname) {
        playerNameByAlias.put(normalizeAlias(initials), nickname);
    }

    public void unregisterPlayer(String playerId) {
        String initials = initialsFromPlayerId(playerId);
        if (!initials.isBlank()) {
            playerNameByAlias.remove(initials);
        }
    }

    public boolean isAliasRegistered(String initials) {
        return playerNameByAlias.containsKey(normalizeAlias(initials));
    }

    public String playerName(String initials) {
        return playerNameByAlias.getOrDefault(normalizeAlias(initials), "-");
    }

    private String initialsFromPlayerId(String playerId) {
        String prefix = "player-";
        if (playerId == null || !playerId.startsWith(prefix)) {
            return "";
        }
        int end = playerId.indexOf('-', prefix.length());
        if (end <= prefix.length()) {
            return normalizeAlias(playerId.substring(prefix.length()));
        }
        return normalizeAlias(playerId.substring(prefix.length(), end));
    }

    private String normalizeAlias(String initials) {
        return initials == null ? "" : initials.toUpperCase(Locale.ROOT);
    }
}
