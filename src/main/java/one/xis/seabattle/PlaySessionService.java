package one.xis.seabattle;

interface PlaySessionService {
    void beginSession(String playerId, String accountId);

    void endSession(String playerId, int score);
}
