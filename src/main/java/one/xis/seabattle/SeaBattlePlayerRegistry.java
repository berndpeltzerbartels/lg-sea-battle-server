package one.xis.seabattle;

import one.xis.context.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SeaBattlePlayerRegistry {

    private final Map<String, PlayerRegistration> registrationByAlias = new ConcurrentHashMap<>();

    public void register(String playerId, String initials, String nickname, String teamId, String accountId) {
        String alias = normalizeAlias(initials);
        registrationByAlias.entrySet().removeIf(entry ->
                accountId != null && accountId.equals(entry.getValue().accountId()) && !entry.getKey().equals(alias));
        registrationByAlias.put(alias, new PlayerRegistration(playerId, nickname, teamId, accountId));
    }

    public String registerPlayer(String playerId, String nickname, String teamId) {
        String initials = initialsFromPlayerId(playerId);
        if (initials.isBlank()) {
            return null;
        }
        PlayerRegistration previous = registrationByAlias.get(initials);
        if (previous == null || previous.playerId() == null || !previous.playerId().equals(playerId)) {
            return null;
        }
        registrationByAlias.put(initials, new PlayerRegistration(
                playerId,
                nickname == null || nickname.isBlank() ? previous.nickname() : nickname,
                previous.teamId(),
                previous.accountId()
        ));
        return null;
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

    public boolean isRegisteredPlayer(String playerId) {
        PlayerRegistration registration = registrationByAlias.get(initialsFromPlayerId(playerId));
        return registration != null
                && registration.playerId() != null
                && registration.playerId().equals(playerId);
    }

    public boolean isAliasRegisteredForOtherAccount(String initials, String accountId) {
        PlayerRegistration registration = registrationByAlias.get(normalizeAlias(initials));
        return registration != null && (accountId == null || !accountId.equals(registration.accountId()));
    }

    public String activePlayerIdForAccountAlias(String accountId, String initials) {
        PlayerRegistration registration = registrationByAlias.get(normalizeAlias(initials));
        if (registration == null || accountId == null || !accountId.equals(registration.accountId())) {
            return null;
        }
        return registration.playerId();
    }

    public String playerName(String initials) {
        PlayerRegistration registration = registrationByAlias.get(normalizeAlias(initials));
        return registration == null || registration.nickname() == null || registration.nickname().isBlank()
                ? "-"
                : registration.nickname();
    }

    public String playerTeam(String initials) {
        PlayerRegistration registration = registrationByAlias.get(normalizeAlias(initials));
        return registration == null || registration.teamId() == null || registration.teamId().isBlank()
                ? ""
                : registration.teamId();
    }

    public String accountIdForPlayer(String playerId) {
        return accountId(initialsFromPlayerId(playerId));
    }

    public String teamIdForPlayer(String playerId) {
        return playerTeam(initialsFromPlayerId(playerId));
    }

    public List<RegisteredPlayer> players() {
        return registrationByAlias.entrySet().stream()
                .map(entry -> new RegisteredPlayer(
                        entry.getKey(),
                        entry.getValue().nickname(),
                        entry.getValue().teamId(),
                        entry.getValue().playerId()))
                .sorted(Comparator.comparing(RegisteredPlayer::teamId).thenComparing(RegisteredPlayer::initials))
                .toList();
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

    private String accountId(String initials) {
        PlayerRegistration registration = registrationByAlias.get(normalizeAlias(initials));
        return registration == null ? null : registration.accountId();
    }

    private record PlayerRegistration(String playerId, String nickname, String teamId, String accountId) {
    }

    public record RegisteredPlayer(String initials, String nickname, String teamId, String playerId) {
    }
}
