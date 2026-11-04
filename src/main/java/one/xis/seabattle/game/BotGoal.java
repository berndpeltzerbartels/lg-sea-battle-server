package one.xis.seabattle.game;

/**
 * Destination prepared before navigation. Direct target reference only for an observed enemy;
 * remembered contacts/search points contain coordinates, never a hidden vehicle reference.
 */
public record BotGoal(
    Kind kind, Vehicle target, String targetShipId, long targetLifeId, double x, double z) {
  public enum Kind {
    ENEMY,
    LAST_KNOWN_COURSE,
    SEARCH,
    RETAINED_COURSE,
    PATROL,
    NONE
  }

  public BotGoal(Kind kind, String targetShipId, long targetLifeId, double x, double z) {
    this(kind, null, targetShipId, targetLifeId, x, z);
  }

  public static BotGoal enemy(Vehicle target) {
    return new BotGoal(
        Kind.ENEMY,
        target,
        target.id(),
        target.lifeId(),
        target.position().x(),
        target.position().z());
  }
}
