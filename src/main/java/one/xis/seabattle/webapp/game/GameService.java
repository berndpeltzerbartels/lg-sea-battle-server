package one.xis.seabattle.webapp.game;

public interface GameService {
    GameEntity activeGame();

    void endActiveGame();

    default String activeGameId() {
        return activeGame().getId();
    }
}
