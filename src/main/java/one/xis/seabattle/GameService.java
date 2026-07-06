package one.xis.seabattle;

interface GameService {
    GameEntity activeGame();

    default String activeGameId() {
        return activeGame().getId();
    }
}
