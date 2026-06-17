#!/usr/bin/env node

import { performance } from "node:perf_hooks";

const options = parseArgs(process.argv.slice(2));
const baseUrl = options.baseUrl.replace(/\/+$/, "");
const durationMs = options.durationSeconds * 1000;
const intervalMs = 1000 / options.hz;
const metrics = new Map();

if (options.reset) {
  await request("POST", "/game/reset", { adminKey: "bernd", setupId: options.setupId }, "reset");
}

const initialState = await request("GET", "/game/state", null, "state");
const shipsByTeam = groupShipsByTeam(initialState.body.ships ?? []);
const clients = createClients(options.clients, shipsByTeam);
const activeUpdateClients = clients.filter(client => client.initialShip).slice(0, options.updateClients);

console.log(`Base URL: ${baseUrl}`);
console.log(`Clients: ${clients.length}, update clients: ${activeUpdateClients.length}, duration: ${options.durationSeconds}s, rate: ${options.hz}Hz`);
console.log(`Requests per client tick: state=${options.state ? "yes" : "no"}, radar=${options.radar ? "yes" : "no"}, update=${options.update ? "yes" : "no"}`);

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
      requests.push(track(request("POST", "/game/player-state", playerUpdate(client, elapsedSeconds), "player-state")));
    }
  }

  if (options.fireEverySeconds > 0 && tickCount % Math.max(1, Math.round(options.fireEverySeconds * options.hz)) === 0) {
    for (const client of activeUpdateClients.filter((_, index) => index % 5 === 0)) {
      requests.push(track(request("POST", "/game/fire-torpedo", {
        playerId: client.playerId,
        teamId: client.teamId
      }, "fire-torpedo")));
    }
  }

  await Promise.allSettled(requests);
  nextTickAt += intervalMs;
}

while (inFlight > 0) {
  await sleep(20);
}

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

function createClients(count, shipsByTeam) {
  const teams = ["light", "dark"];
  const clients = [];
  for (let index = 0; index < count; index += 1) {
    const teamId = teams[index % teams.length];
    const teamShips = shipsByTeam.get(teamId) ?? [];
    const initialShip = teamShips[Math.floor(index / teams.length)] ?? null;
    clients.push({
      index,
      teamId,
      playerId: `player-B${String(index + 1).padStart(2, "0")}-loadtest`,
      initialShip,
      startX: initialShip?.x ?? 0,
      startZ: initialShip?.z ?? 0,
      heading: initialShip?.heading ?? (teamId === "light" ? 0 : Math.PI)
    });
  }
  return clients;
}

function playerUpdate(client, elapsedSeconds) {
  const speed = 6.4;
  const turnVelocity = Math.sin(elapsedSeconds * 0.35 + client.index) * 0.025;
  const heading = client.heading + Math.sin(elapsedSeconds * 0.18 + client.index) * 0.35;
  const distance = speed * elapsedSeconds;
  return {
    playerId: client.playerId,
    teamId: client.teamId,
    x: client.startX + Math.sin(heading) * distance,
    z: client.startZ + Math.cos(heading) * distance,
    heading,
    speed,
    turnVelocity,
    engineOrder: 6,
    rudderDegrees: Math.round(Math.sin(elapsedSeconds * 0.45 + client.index) * 18),
    clientTime: elapsedSeconds
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

function parseArgs(args) {
  const parsed = {
    baseUrl: "http://127.0.0.1:9090",
    clients: 30,
    updateClients: 30,
    durationSeconds: 30,
    hz: 4,
    setupId: "default",
    reset: false,
    state: true,
    radar: true,
    update: true,
    fireEverySeconds: 0
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
      case "--no-state":
        parsed.state = false;
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

  return parsed;
}
