import { execFileSync } from "node:child_process";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const source = resolve(root, "extensions/extension/src/main/java/org/example/tverpatches/extension/NcoSearchMatcher.java");
const tests = resolve(root, "scripts/nco-search/NcoSearchMatcherTest.java");
const temp = mkdtempSync(join(tmpdir(), "morphe-nco-search-"));
try {
    execFileSync("javac", ["-encoding", "UTF-8", "-d", temp, source, tests], { stdio: "inherit" });
    execFileSync("java", ["-cp", temp, "org.example.tverpatches.extension.NcoSearchMatcherTest"], { stdio: "inherit" });
} finally {
    rmSync(temp, { recursive: true, force: true });
}
