package one.xis.seabattle.game;

public final class TorpedoBoat extends SurfaceVehicle {
  private double cannonYaw, cannonPitch;

  public TorpedoBoat(String id, String team, Vector2 position, double heading, String controller) {
    super(id, team, position, heading, controller);
  }

  @Override
  public String vehicleType() {
    return "torpedo-boat";
  }

  @Override
  protected int maxRudderDegrees() {
    return 35;
  }

  @Override
  void cannonAim(double yaw, double pitch) {
    cannonYaw = MathSupport.normalizeAngle(yaw);
    cannonPitch = pitch;
  }

  @Override
  protected double cannonYawValue() {
    return cannonYaw;
  }

  @Override
  protected double cannonPitchValue() {
    return cannonPitch;
  }
}
