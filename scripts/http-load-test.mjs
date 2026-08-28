#!/usr/bin/env node

import { performance } from "node:perf_hooks";

const options = parseArgs(process.argv.slice(2));
const baseUrl = options.baseUrl.replace(/\/+$/, "");
const durationMs = options.durationSeconds * 1000;
const intervalMs = 1000 / options.hz;
const metrics = new Map();
const eventControllers = [];
const runId = createRunId();

if (options.reset) {
  await request("POST", "/game/reset", { adminKey: "bernd", setupId: options.setupId }, "reset");
}

let initialState = await request("GET", "/game/state", null, "state");
let shipsByTeam = groupShipsByTeam(initialState.body.ships ?? []);
const clients = createClients(options.clients, shipsByTeam, runId);
if (options.register) {
  await registerClients(clients);
  initialState = await request("GET", "/game/state", null, "state");
  shipsByTeam = groupShipsByTeam(initialState.body.ships ?? []);
}
refreshClientStarts(clients, shipsByTeam);
const activeUpdateClients = clients.slice(0, options.updateClients);
if (options.events) {
  openEventStreams(clients);
}

console.log(`Base URL: ${baseUrl}`);
console.log(`Clients: ${clients.length}, update clients: ${activeUpdateClients.length}, duration: ${options.durationSeconds}s, rate: ${options.hz}Hz`);
console.log(`Requests per client tick: state=${options.state ? "yes" : "no"}, radar=${options.radar ? "yes" : "no"}, update=${options.update ? "yes" : "no"}, events=${options.events ? "yes" : "no"}`);

const stopAt = performance.now() + durationMs;
let tickCount = 0;
let nextTickAt = performance.now();
let inFlight = 0;

while (performance.now() < stopAt) {
  const now = performance.now();
  if (now < nextTickAt) {
    await sleep(Math.min(10, nextTickAt - now));
    continue;
  }

  tickCount += 1;
  const elapsedSeconds = tickCount / options.hz;
  const requests = [];

  for (const client of clients) {
    if (options.state) {
      requests.push(track(request("GET", "/game/state", null, "state")));
    }
    if (options.radar) {
      requests.push(track(request("POST", "/game/radar", {
        playerId: client.playerId,
        teamId: client.teamId
      }, "radar")));
    }
  }

  if (options.update) {
    for (const client of activeUpdateClients) {
      requests.push(track(request("POST", "/game/player-state", playerUpdate(client, elapsedSeconds), "player-state")
              .then(response => {
                if (response.body?.ships) {
                  refreshClientStarts([client], groupShipsByTeam(response.body.ships));
                }
                return response;
              })));
    }
  }

  if (options.fireEverySeconds > 0 && tickCount % Math.max(1, Math.round(options.fireEverySeconds * options.hz)) === 0) {
    for (const client of activeUpdateClients.filter((_, index) => index % 5 === 0)) {
      if (!canUseShipWeapons(client)) {
        continue;
      }
      requests.push(track(request("POST", "/game/fire-torpedo", {
        playerId: client.playerId,
        teamId: client.teamId,
        vehicleType: "torpedo-boat",
        x: client.lastX ?? client.startX,
        z: client.lastZ ?? client.startZ,
        heading: client.lastHeading ?? client.heading,
        speed: client.lastSpeed ?? 6.4,
        turnVelocity: client.lastTurnVelocity ?? 0,
        engineOrder: client.lastEngineOrder ?? 6,
        rudderDegrees: client.lastRudderDegrees ?? 0,
        tubeSide: client.nextTubeSide,
        clientTime: elapsedSeconds
      }, "fire-torpedo")));
      client.nextTubeSide *= -1;
    }
  }

  if (options.weaponMode !== "none") {
    for (const client of activeUpdateClients) {
      if (!canUseShipWeapons(client)) {
        continue;
      }
      const burst = weaponBurst(client, elapsedSeconds);
      if (!burst) {
        continue;
      }
      requests.push(track(request("POST", burst.path, burst.body, burst.label)));
    }
  }

  await Promise.allSettled(requests);
  nextTickAt += intervalMs;
}

while (inFlight > 0) {
  await sleep(20);
}

eventControllers.forEach(controller => controller.abort());
await sleep(50);
printSummary(metrics, tickCount, durationMs);

async function track(promise) {
  inFlight += 1;
  try {
    return await promise;
  } finally {
    inFlight -= 1;
  }
}

async function request(method, path, body, label) {
  const started = performance.now();
  let status = 0;
  let bytes = 0;
  let parsedBody = null;
  let ok = false;
  try {
    const response = await fetch(`${baseUrl}${path}`, {
      method,
      headers: body ? { "content-type": "application/json" } : undefined,
      body: body ? JSON.stringify(body) : undefined
    });
    status = response.status;
    const text = await response.text();
    bytes = Buffer.byteLength(text);
    parsedBody = text ? JSON.parse(text) : null;
    ok = response.ok;
    return { status, body: parsedBody };
  } catch (error) {
    record(label, performance.now() - started, status, bytes, false);
    throw error;
  } finally {
    record(label, performance.now() - started, status, bytes, ok);
  }
}

async function registerClients(clients) {
  for (const client of clients) {
    const response = await request("POST", "/game/start", {
      accountId: `loadtest-${runId}-${client.index}`,
      nickname: `Load ${String(client.index + 1).padStart(2, "0")}`,
      alias: client.alias,
      team: client.teamId,
      email: ""
    }, "start");
    if (response.body?.player?.playerId) {
      client.playerId = response.body.player.playerId;
      client.registered = true;
    }
  }
}

function openEventStreams(clients) {
  for (const client of clients) {
    const controller = new AbortController();
    eventControllers.push(controller);
    consumeEventStream(client, controller.signal);
  }
}

async function consumeEventStream(client, signal) {
  const started = performance.now();
  let status = 0;
  let bytes = 0;
  try {
    const response = await fetch(`${baseUrl}/game/events/${encodeURIComponent(client.playerId)}`, { signal });
    status = response.status;
    record("event-open", performance.now() - started, status, 0, response.ok);
    if (!response.ok || !response.body) {
      return;
    }
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    while (!signal.aborted) {
      const { done, value } = await reader.read();
      if (done) {
        break;
      }
      bytes += value.byteLength;
      buffer += decoder.decode(value, { stream: true });
      const messages = buffer.split("\n\n");
      buffer = messages.pop() ?? "";
      for (const message of messages) {
        if (message.trim()) {
          record("event-message", 0, 200, Buffer.byteLength(message), true);
          applyEventMessage(client, message);
        }
      }
    }
  } catch (error) {
    if (!signal.aborted) {
      record("event-error", performance.now() - started, status, bytes, false);
    }
  }
}

function record(label, latencyMs, status, bytes, ok) {
  const bucket = metrics.get(label) ?? {
    count: 0,
    ok: 0,
    error: 0,
    bytes: 0,
    statuses: new Map(),
    latencies: []
  };
  bucket.count += 1;
  bucket.ok += ok ? 1 : 0;
  bucket.error += ok ? 0 : 1;
  bucket.bytes += bytes;
  bucket.latencies.push(latencyMs);
  bucket.statuses.set(status, (bucket.statuses.get(status) ?? 0) + 1);
  metrics.set(label, bucket);
}

function createClients(count, shipsByTeam, runId) {
  const teams = ["light", "dark"];
  const clients = [];
  for (let index = 0; index < count; index += 1) {
    const teamId = teams[index % teams.length];
    const teamShips = shipsByTeam.get(teamId) ?? [];
    const initialShip = teamShips[Math.floor(index / teams.length)] ?? null;
    clients.push({
      index,
      teamId,
      alias: loadAlias(runId, index),
      playerId: `player-${loadAlias(runId, index)}-loadtest`,
      initialShip,
      startX: initialShip?.x ?? 0,
      startZ: initialShip?.z ?? 0,
      heading: initialShip?.heading ?? (teamId === "light" ? 0 : Math.PI),
      lastX: initialShip?.x ?? startingLaneX({ index, teamId }),
      lastZ: initialShip?.z ?? startingLaneZ({ index }),
      lastHeading: initialShip?.heading ?? (teamId === "light" ? 0 : Math.PI),
      lastSpeed: 0,
      lastTurnVelocity: 0,
      lastElapsedSeconds: 0,
      nextTubeSide: index % 2 === 0 ? -1 : 1
    });
  }
  return clients;
}

function refreshClientStarts(clients, shipsByTeam) {
  for (const client of clients) {
    const teamShips = shipsByTeam.get(client.teamId) ?? [];
    const assignedShip = teamShips.find(ship => ship.controlledBy === client.playerId && ship.state === "active");
    const fallbackShip = teamShips[Math.floor(client.index / 2)] ?? null;
    const ship = assignedShip ?? (client.registered ? null : client.initialShip ?? fallbackShip);
    const previousShipId = client.initialShip?.id ?? null;
    client.initialShip = ship;
    client.startX = ship?.x ?? startingLaneX(client);
    client.startZ = ship?.z ?? startingLaneZ(client);
    client.heading = ship?.heading ?? (client.teamId === "light" ? 0 : Math.PI);
    if (ship && previousShipId !== ship.id) {
      client.lastX = ship.x;
      client.lastZ = ship.z;
      client.lastHeading = ship.heading;
      client.lastSpeed = ship.speed ?? 0;
      client.lastTurnVelocity = ship.turnVelocity ?? 0;
      client.lastElapsedSeconds = 0;
    }
  }
}

function canUseShipWeapons(client) {
  return Boolean(client.initialShip?.id && client.initialShip.state === "active" && client.initialShip.vehicleType !== "scout-plane");
}

function playerUpdate(client, elapsedSeconds) {
  const deltaSeconds = Math.max(0, Math.min(1 / options.hz, elapsedSeconds - (client.lastElapsedSeconds ?? elapsedSeconds)));
  const targetSpeed = 5.2 + 2.8 * (0.5 + 0.5 * Math.sin(elapsedSeconds * 0.045 + client.index * 0.61));
  const targetTurnVelocity = Math.sin(elapsedSeconds * 0.12 + client.index * 1.37) * 0.032
          + Math.sin(elapsedSeconds * 0.035 + client.index * 0.43) * 0.014;
  const speed = approach(client.lastSpeed ?? 0, targetSpeed, 1.2 * deltaSeconds);
  const turnVelocity = approach(client.lastTurnVelocity ?? 0, targetTurnVelocity, 0.018 * deltaSeconds);
  const heading = MathSupportNormalize((client.lastHeading ?? client.heading) + turnVelocity * deltaSeconds);
  const x = (client.lastX ?? client.startX) + Math.sin(heading) * speed * deltaSeconds;
  const z = (client.lastZ ?? client.startZ) + Math.cos(heading) * speed * deltaSeconds;
  const update = {
    playerId: client.playerId,
    teamId: client.teamId,
    x,
    z,
    heading,
    speed,
    turnVelocity,
    engineOrder: 6,
    rudderDegrees: Math.round(Math.max(-35, Math.min(35, turnVelocity / 0.045 * 35))),
    clientTime: elapsedSeconds,
    vehicleType: "torpedo-boat"
  };
  client.lastX = update.x;
  client.lastZ = update.z;
  client.lastHeading = update.heading;
  client.lastSpeed = update.speed;
  client.lastTurnVelocity = update.turnVelocity;
  client.lastEngineOrder = update.engineOrder;
  client.lastRudderDegrees = update.rudderDegrees;
  client.lastElapsedSeconds = elapsedSeconds;
  return update;
}

function applyEventMessage(client, message) {
  const data = message
          .split("\n")
          .filter(line => line.startsWith("data:"))
          .map(line => line.slice("data:".length).trim())
          .join("\n");
  if (!data) {
    return;
  }
  try {
    const snapshot = JSON.parse(data);
    if (snapshot?.ships) {
      refreshClientStarts([client], groupShipsByTeam(snapshot.ships));
    }
  } catch {
    // Ignore non-snapshot server-sent events.
  }
}

function weaponBurst(client, elapsedSeconds) {
  if (options.weaponMode === "flak-burst") {
    const burstPeriod = 16;
    const burstLength = 8;
    const offset = (elapsedSeconds + client.index * 0.37) % burstPeriod;
    if (offset > burstLength) {
      return null;
    }
    return flakShot(client, elapsedSeconds);
  }
  if (options.weaponMode === "mixed") {
    if ((client.index + Math.floor(elapsedSeconds / 7)) % 5 === 0) {
      return cannonShot(client, elapsedSeconds);
    }
    if ((client.index + Math.floor(elapsedSeconds / 2)) % 3 === 0) {
      return flakShot(client, elapsedSeconds);
    }
  }
  return null;
}

function flakShot(client, elapsedSeconds) {
  const shot = shotBody(client, elapsedSeconds, 0.8, 180, 45);
  return { path: "/game/fire-flak", label: "fire-flak", body: shot };
}

function cannonShot(client, elapsedSeconds) {
  const shot = shotBody(client, elapsedSeconds, 0.08, 260, 18);
  return { path: "/game/fire-cannon", label: "fire-cannon", body: shot };
}

function shotBody(client, elapsedSeconds, pitch, muzzleSpeed, swayDegrees) {
  const yaw = (client.lastHeading ?? client.heading) + Math.sin(elapsedSeconds * 1.7 + client.index) * radians(swayDegrees);
  const pitchNow = pitch + Math.sin(elapsedSeconds * 2.1 + client.index * 0.5) * 0.12;
  const horizontal = Math.cos(pitchNow);
  return {
    playerId: client.playerId,
    teamId: client.teamId,
    shipId: client.initialShip?.id ?? "",
    x: client.lastX ?? client.startX,
    y: 5,
    z: client.lastZ ?? client.startZ,
    vx: Math.sin(yaw) * horizontal * muzzleSpeed,
    vy: Math.sin(pitchNow) * muzzleSpeed,
    vz: Math.cos(yaw) * horizontal * muzzleSpeed,
    weaponYaw: yaw,
    weaponPitch: pitchNow
  };
}

function groupShipsByTeam(ships) {
  const grouped = new Map();
  for (const ship of ships) {
    if (!grouped.has(ship.teamId)) {
      grouped.set(ship.teamId, []);
    }
    grouped.get(ship.teamId).push(ship);
  }
  return grouped;
}

function printSummary(allMetrics, tickCount, durationMs) {
  const totalRequests = [...allMetrics.values()].reduce((sum, metric) => sum + metric.count, 0);
  console.log("");
  console.log(`Ticks: ${tickCount}, total requests: ${totalRequests}, throughput: ${(totalRequests / (durationMs / 1000)).toFixed(1)} req/s`);
  console.log("endpoint        count   ok     err    min     avg     std     p50     p95     p99     max     avg KB   statuses");
  for (const [label, metric] of [...allMetrics.entries()].sort()) {
    metric.latencies.sort((a, b) => a - b);
    const count = metric.latencies.length;
    const averageMs = average(metric.latencies);
    const stddevMs = standardDeviation(metric.latencies, averageMs);
    const avgBytes = count === 0 ? 0 : metric.bytes / count / 1024;
    const statuses = [...metric.statuses.entries()]
      .sort(([a], [b]) => a - b)
      .map(([status, value]) => `${status}:${value}`)
      .join(",");
    console.log([
      label.padEnd(14),
      String(metric.count).padStart(6),
      String(metric.ok).padStart(6),
      String(metric.error).padStart(6),
      formatMs(metric.latencies[0] ?? 0).padStart(7),
      formatMs(averageMs).padStart(7),
      formatMs(stddevMs).padStart(7),
      formatMs(percentile(metric.latencies, 0.50)).padStart(7),
      formatMs(percentile(metric.latencies, 0.95)).padStart(7),
      formatMs(percentile(metric.latencies, 0.99)).padStart(7),
      formatMs(metric.latencies.at(-1) ?? 0).padStart(7),
      avgBytes.toFixed(1).padStart(8),
      statuses
    ].join(" "));
  }
}

function average(values) {
  if (values.length === 0) {
    return 0;
  }
  return values.reduce((sum, value) => sum + value, 0) / values.length;
}

function standardDeviation(values, averageValue) {
  if (values.length === 0) {
    return 0;
  }
  const variance = values.reduce((sum, value) => sum + (value - averageValue) ** 2, 0) / values.length;
  return Math.sqrt(variance);
}

function percentile(values, ratio) {
  if (values.length === 0) {
    return 0;
  }
  return values[Math.min(values.length - 1, Math.floor((values.length - 1) * ratio))];
}

function formatMs(value) {
  return value.toFixed(1);
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function approach(value, target, maxStep) {
  if (value < target) {
    return Math.min(target, value + maxStep);
  }
  if (value > target) {
    return Math.max(target, value - maxStep);
  }
  return value;
}

function MathSupportNormalize(angle) {
  let normalized = angle;
  while (normalized <= -Math.PI) {
    normalized += Math.PI * 2;
  }
  while (normalized > Math.PI) {
    normalized -= Math.PI * 2;
  }
  return normalized;
}

function createRunId() {
  return Date.now().toString(36).slice(-2).toUpperCase();
}

function loadAlias(runId, index) {
  return `L${runId}${index.toString(36).toUpperCase().padStart(2, "0")}`.slice(0, 5);
}

function startingLaneX(client) {
  return client.teamId === "light" ? -180 - client.index * 8 : 180 + client.index * 8;
}

function startingLaneZ(client) {
  return (client.index % 15 - 7) * 35;
}

function radians(degrees) {
  return degrees * Math.PI / 180;
}

function parseArgs(args) {
  const parsed = {
    baseUrl: "http://127.0.0.1:9090",
    clients: 30,
    updateClients: 30,
    durationSeconds: 30,
    hz: 4,
    setupId: "default",
    reset: false,
    register: false,
    events: true,
    state: false,
    radar: false,
    update: true,
    fireEverySeconds: 0,
    weaponMode: "none"
  };

  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    const next = () => args[++index];
    switch (arg) {
      case "--base":
        parsed.baseUrl = next();
        break;
      case "--clients":
        parsed.clients = Number(next());
        break;
      case "--update-clients":
        parsed.updateClients = Number(next());
        break;
      case "--duration":
        parsed.durationSeconds = Number(next());
        break;
      case "--hz":
        parsed.hz = Number(next());
        break;
      case "--setup":
        parsed.setupId = next();
        break;
      case "--reset":
        parsed.reset = true;
        break;
      case "--register":
        parsed.register = true;
        break;
      case "--no-events":
        parsed.events = false;
        break;
      case "--events":
        parsed.events = true;
        break;
      case "--state":
        parsed.state = true;
        break;
      case "--no-state":
        parsed.state = false;
        break;
      case "--radar":
        parsed.radar = true;
        break;
      case "--no-radar":
        parsed.radar = false;
        break;
      case "--no-update":
        parsed.update = false;
        break;
      case "--fire-every":
        parsed.fireEverySeconds = Number(next());
        break;
      case "--weapon-mode":
        parsed.weaponMode = next();
        break;
      default:
        throw new Error(`Unknown argument: ${arg}`);
    }
  }

  for (const [name, value] of Object.entries({
    clients: parsed.clients,
    updateClients: parsed.updateClients,
    durationSeconds: parsed.durationSeconds,
    hz: parsed.hz
  })) {
    if (!Number.isFinite(value) || value <= 0) {
      throw new Error(`${name} must be a positive number`);
    }
  }
  if (!["none", "flak-burst", "mixed"].includes(parsed.weaponMode)) {
    throw new Error("weaponMode must be none, flak-burst or mixed");
  }

  return parsed;
}
