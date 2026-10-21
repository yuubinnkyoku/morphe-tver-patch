import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { runInNewContext } from "node:vm";

// Test the actual Java text-block script, not a duplicate of clock code.
const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const java = readFileSync(resolve(root, "extensions/extension/src/main/java/org/example/tverpatches/extension/NicoOverlayBridge.java"), "utf8");
const html = java.match(/private static final String HTML = """([\s\S]*?)""";/)?.[1];
assert.ok(html, "HTML renderer text block not found");
const script = html.match(/<script>([\s\S]*?)<\/script>/)?.[1];
assert.ok(script, "Renderer JavaScript not found");

let elapsed = 0;
let nextFrame;
let lines = [];
const canvasContext = {
    measureText: text => ({ width: text.length * 24 }),
    clearRect() {},
    strokeText(text, x, y) { lines.push({ text, x, y }); },
    fillText() {},
};
const window = {};
const sandbox = {
    window,
    document: { getElementById: () => ({ getContext: () => canvasContext }) },
    performance: { now: () => elapsed },
    requestAnimationFrame: callback => { nextFrame = callback; },
};
runInNewContext(script, sandbox, { timeout: 1000 });
assert.equal(typeof window.MorpheNico.update, "function");

function draw(text) {
    lines = [];
    assert.equal(typeof nextFrame, "function");
    nextFrame();
    return lines.find(item => item.text === text)?.x ?? null;
}

const update = window.MorpheNico.update;
const marker = "Morphe × TVer テストコメント";
update(3000, true, 1, false, false, 0);
let lastX = draw(marker);
assert.ok(lastX !== null, "test comment should be visible");

for (let i = 1; i < 5; i++) {
    elapsed = i * 200;
    const before = draw(marker);
    assert.ok(before < lastX, "scroll must progress left");
    update(3000, true, 1, false, false, 0);
    const after = draw(marker);
    assert.ok(after <= before + 0.001, "stale position must not bounce the comment right");
    lastX = after;
}

elapsed = 1000;
const xBeforeFresh = draw(marker);
update(4000, true, 1, false, false, 0);
const xAfterFresh = draw(marker);
assert.ok(Math.abs(xAfterFresh - xBeforeFresh) < 60, "fresh position must not jump abruptly");

elapsed = 1200;
update(4000, false, 1, false, false, 0);
const pausedX = draw(marker);
elapsed = 2200;
assert.equal(draw(marker), pausedX, "comment must freeze when paused");
update(4000, true, 1, false, false, 0);
elapsed = 2300;
assert.ok(draw(marker) < pausedX, "comment must resume");

const special = "特殊コメントは次の段階で対応";
update(10000, true, 1, false, false, 1);
assert.ok(draw(special) !== null, "seek generation change must jump to new position");

elapsed = 2400;
update(10000, true, 2, false, false, 1);
const speedX = draw(special);
elapsed = 2500;
const twiceX = draw(special);
assert.ok(twiceX < speedX, "2x playback must advance comments");

update(10000, true, 2, true, false, 1);
assert.equal(draw(special), null, "comments must hide during ads");
update(3000, true, 1, false, false, 2);
assert.ok(draw(marker) !== null, "seeking back must restore earlier comments");

console.log("Nico overlay playback-clock regression tests passed");
