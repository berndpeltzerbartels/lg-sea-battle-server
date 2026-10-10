export const cadence = { motion: .25, flak: .044, cannon: 1, torpedo: 5 };

export function stationAt(seconds, index) {
  const phase = (seconds + index * 2) % 30;
  return phase < 16 ? 'bridge' : phase < 23 ? 'flak' : 'cannon';
}

export function observeNavigation(ship, memory, now) {
  if (memory.shipId !== ship.id) {
    Object.assign(memory, { shipId: ship.id, sampleAt: now, x: ship.x, z: ship.z,
      recovery: null, attempts: 0 });
  }
  const beginReverse = () => {
    memory.attempts++;
    memory.recovery = 'reverse';
    memory.phaseAt = now;
    memory.originX = ship.x;
    memory.originZ = ship.z;
    memory.escapeHeading = ship.heading;
  };
  if (!memory.recovery && now - memory.sampleAt >= 6) {
    if (Math.hypot(ship.x - memory.x, ship.z - memory.z) < 2) {
      beginReverse();
    }
    Object.assign(memory, { sampleAt: now, x: ship.x, z: ship.z });
  }
  if (memory.recovery === 'reverse') {
    const retreat = -(ship.x - memory.originX) * Math.sin(memory.escapeHeading)
      - (ship.z - memory.originZ) * Math.cos(memory.escapeHeading);
    if (retreat >= 24) {
      memory.recovery = 'turn';
      memory.phaseAt = now;
      memory.turnHeading = ship.heading + (memory.attempts % 2 ? 1 : -1) * Math.PI / 2;
    } else if (now - memory.phaseAt > 20) {
      // Try the other rudder direction if straight backing has not cleared the shore.
      beginReverse();
    }
  } else if (memory.recovery === 'turn') {
    const error = Math.atan2(Math.sin(memory.turnHeading - ship.heading), Math.cos(memory.turnHeading - ship.heading));
    if (Math.abs(error) < .12 && ship.speed > 1) {
      memory.recovery = null;
      Object.assign(memory, { sampleAt: now, x: ship.x, z: ship.z });
    } else if (now - memory.phaseAt > 25) beginReverse();
  }
}

export function navigation(ship, memory, now) {
  observeNavigation(ship, memory, now);
  const reverse = memory.recovery === 'reverse';
  const recovering = !!memory.recovery;
  const desiredSpeed = reverse ? -8 : recovering ? 2.93 : 6.18;
  const speed = ship.speed + Math.max(-.3, Math.min(.3, desiredSpeed - ship.speed));
  const error = memory.recovery === 'turn'
    ? Math.atan2(Math.sin(memory.turnHeading - ship.heading), Math.cos(memory.turnHeading - ship.heading)) : 0;
  const rudder = reverse ? (memory.attempts > 1 ? (memory.attempts % 2 ? 20 : -20) : 0)
    : recovering ? Math.sign(error) * 35 : Math.sin(now * .08) * 12;
  const turn = rudder / 35 * (speed >= 0 ? .24 : -.16) * Math.min(1, Math.abs(speed) / 4.2);
  const heading = ship.heading + turn * cadence.motion;
  return { x: ship.x + Math.sin(heading) * speed * cadence.motion,
    z: ship.z + Math.cos(heading) * speed * cadence.motion, y: 0, heading, speed,
    turnVelocity: turn, rudderDegrees: Math.round(rudder),
    engineOrder: reverse ? 0 : recovering ? 4 : 5, vehicleType: 'torpedo-boat',
    clientTime: now, debugTeleport: false };
}

export function shotFor(ship, station, seconds, scale) {
  // Fire forward from the bow and aft from the stern, clear of own superstructure.
  const relativeYaw = (station === 'flak' ? Math.PI : 0) + Math.sin(seconds * .3) * .3;
  const pitch = station === 'flak' ? .65 : .08;
  const yaw = ship.heading + relativeYaw;
  const muzzleSpeed = (station === 'flak' ? 418 : 1455) * scale;
  const offset = (station === 'flak' ? -3 : 3) * scale;
  return { x: ship.x + Math.sin(ship.heading) * offset,
    z: ship.z + Math.cos(ship.heading) * offset, y: 1.3 * scale,
    vx: Math.sin(yaw) * Math.cos(pitch) * muzzleSpeed + Math.sin(ship.heading) * ship.speed,
    vy: Math.sin(pitch) * muzzleSpeed,
    vz: Math.cos(yaw) * Math.cos(pitch) * muzzleSpeed + Math.cos(ship.heading) * ship.speed,
    weaponYaw: relativeYaw, weaponPitch: pitch };
}
