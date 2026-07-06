package one.xis.seabattle;

interface PlaySessionService {
    void beginSession(String playerId, String accountId, String gameId, String alias, String teamId);

    void endSession(String playerId, int score);

    boolean isAliasActiveForOtherAccount(String gameId, String alias, String accountId);
}
