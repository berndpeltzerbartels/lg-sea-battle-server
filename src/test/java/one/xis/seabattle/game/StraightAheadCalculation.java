package one.xis.seabattle.game;

/** Minimal replacement example. Intentionally has no targeting, evasion or weapons. */
public final class StraightAheadCalculation implements BotCalculation {
    @Override
    public void calculate(BotSituation situation, long tickId, java.util.Map<String, BotGoal> goals) {
        for (ShipSnapshot ship : situation.ships()) {
            if ("bot".equals(ship.controlledBy()) && "active".equals(ship.state())) {
                situation.steer(ship.id(), 8, 0);
            }
        }
    }
}
