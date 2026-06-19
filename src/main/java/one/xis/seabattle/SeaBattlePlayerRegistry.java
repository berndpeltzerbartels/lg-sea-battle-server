package one.xis.seabattle;

import one.xis.context.Service;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SeaBattlePlayerRegistry {

    private final Map<String, PlayerRegistration> registrationByAlias = new ConcurrentHashMap<>();

    public void register(String initials, String nickname) {
        registrationByAlias.put(normalizeAlias(initials), new PlayerRegistration(null, nickname));
    }

    public String registerPlayer(String playerId, String nickname) {
        String initials = initialsFromPlayerId(playerId);
        if (initials.isBlank()) {
            return null;
        }
        PlayerRegistration previous = registrationByAlias.put(
                initials,
                new PlayerRegistration(playerId, nickname == null || nickname.isBlank() ? playerName(initials) : nickname)
        );
        if (previous == null || previous.playerId() == null || previous.playerId().equals(playerId)) {
            return null;
        }
        return previous.playerId();
    }

    public void unregisterPlayer(String playerId) {
        String initials = initialsFromPlayerId(playerId);
        if (initials.isBlank()) {
            return;
        }
        registrationByAlias.computeIfPresent(initials, (ignored, registration) ->
                registration.playerId() == null || registration.playerId().equals(playerId) ? null : registration);
    }

    public boolean isAliasRegistered(String initials) {
        return registrationByAlias.containsKey(normalizeAlias(initials));
    }

    public String playerName(String initials) {
        PlayerRegistration registration = registrationByAlias.get(normalizeAlias(initials));
        return registration == null || registration.nickname() == null || registration.nickname().isBlank()
                ? "-"
                : registration.nickname();
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

    private record PlayerRegistration(String playerId, String nickname) {
    }
}
