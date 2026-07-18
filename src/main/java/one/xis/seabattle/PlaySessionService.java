package one.xis.seabattle;

import java.time.LocalDateTime;

interface PlaySessionService {
    void beginSession(String playerId, String accountId, String gameId, String alias, String teamId);

    void endSession(String playerId, int score);

    void endActiveSessionsForGame(String gameId, LocalDateTime endTime);

    boolean isAliasActiveForOtherAccount(String gameId, String alias, String accountId);
}
