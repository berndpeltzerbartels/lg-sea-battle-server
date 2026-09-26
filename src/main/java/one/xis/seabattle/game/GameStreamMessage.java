package one.xis.seabattle.game;

public record GameStreamMessage(
        String type,
        GameSnapshot state,
        CrewService.View crew
) {
    public GameStreamMessage(String type, GameSnapshot state) { this(type, state, null); }
}
