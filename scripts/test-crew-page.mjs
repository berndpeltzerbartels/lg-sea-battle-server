import assert from "node:assert/strict";
import { createRequire } from "node:module";
const require = createRequire(new URL("../../client/package.json", import.meta.url));
const { chromium } = require("playwright");
const base = process.env.CREW_TEST_URL ?? "http://127.0.0.1:9092";
const browser = await chromium.launch({ headless: true });
try {
  const context = await browser.newContext({ viewport: { width: 1100, height: 800 } });
  const page = await context.newPage();
  const errors = [];
  let eventStream = false;
  page.on("pageerror", error => errors.push(error.message));
  page.on("response", response => {
    if (response.headers()["content-type"]?.includes("text/event-stream")) eventStream = true;
  });
  const alias = `A${Date.now().toString(36).slice(-4)}`.toUpperCase();
  await page.goto(`${base}/start.html`);
  await page.locator("#nickname").fill("Crew Test");
  await page.locator("#alias").fill(alias);
  await page.locator("#team").selectOption("light");
  await page.locator("#vehicleType").selectOption("crew");
  await page.locator('button[xis\\:action="startGame"]').click();
  await page.waitForURL("**/crew.html");
  await page.locator("h1").filter({ hasText: "Anheuern" }).waitFor();
  const initialState = await (await page.request.get(`${base}/game/state`)).json();
  assert.ok(initialState.ships.every(s => !s.controlledBy?.startsWith(`player-${alias}-`)));

  // Another client registers while the applicant stays on the open XIS page.
  const captain = await browser.newContext();
  const registration = await captain.request.post(`${base}/game/start`, { data: {
    accountId: `crew-smoke-${Date.now()}`, nickname: "Captain Test", alias: `C${alias.slice(1)}`,
    team: "light", vehicleType: "torpedo-boat"
  }});
  assert.equal(registration.status(), 200);
  const login = await registration.json();
  const state = await (await captain.request.get(`${base}/game/state`)).json();
  const ship = state.ships.find(s => s.controlledBy === login.player.playerId);
  assert.ok(ship);
  try {
    await page.waitForFunction(id => [...document.querySelectorAll("#shipId option")].some(o => o.value === id), ship.id, { timeout: 10000 });
  } catch (error) {
    console.log({ eventStream, errors, body: await page.locator("body").innerText(), ship: ship.id });
    throw error;
  }
  assert.ok(eventStream, "XIS SSE connection must be active");
  await page.locator("#shipId").selectOption(ship.id);
  await page.locator('button[xis\\:action="request"]').click();
  await page.locator("li").filter({ hasText: `${ship.id}: Offen` }).waitFor();
  await page.reload();
  await page.locator("li").filter({ hasText: `${ship.id}: Offen` }).waitFor();
  assert.equal(await page.locator(`#shipId option[value="${ship.id}"]`).count(), 0);
  await page.screenshot({ path: "/tmp/crew-page-desktop.png" });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: "/tmp/crew-page-mobile.png" });
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth));
  assert.deepEqual(errors, []);
  console.log("PASS: XIS login, no own ship, live SSE arrival, request, reload protection, mobile layout");
} finally {
  await browser.close();
}
