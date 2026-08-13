package one.xis.seabattle.webapp.game;

public interface GameService {
    GameEntity activeGame();

    default String activeGameId() {
        return activeGame().getId();
    }
}
