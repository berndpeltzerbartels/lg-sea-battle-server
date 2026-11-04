package one.xis.seabattle.game;

public record Vector2(double x, double z) {

  public Vector2 add(Vector2 vector) {
    return new Vector2(x + vector.x, z + vector.z);
  }

  public Vector2 subtract(Vector2 vector) {
    return new Vector2(x - vector.x, z - vector.z);
  }

  public Vector2 scale(double factor) {
    return new Vector2(x * factor, z * factor);
  }

  public Vector2 normalized() {
    double length = Math.hypot(x, z);
    if (length <= 0.0001) {
      return new Vector2(0, 1);
    }
    return new Vector2(x / length, z / length);
  }

  public double distanceTo(Vector2 vector) {
    return Math.hypot(x - vector.x, z - vector.z);
  }

  public static Vector2 fromHeading(double heading) {
    return new Vector2(Math.sin(heading), Math.cos(heading));
  }
}
