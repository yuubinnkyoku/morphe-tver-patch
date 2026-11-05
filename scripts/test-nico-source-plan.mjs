import { execFileSync } from 'node:child_process';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const out=mkdtempSync(join(tmpdir(),'morphe-source-plan-'));
try {
    execFileSync('javac',['-encoding','UTF-8','-d',out,
        resolve(root,'extensions/extension/src/main/java/org/example/tverpatches/extension/NicoSourcePlan.java'),
        resolve(root,'scripts/nco-search/NicoSourcePlanTest.java')],{stdio:'inherit'});
    execFileSync('java',['-cp',out,'org.example.tverpatches.extension.NicoSourcePlanTest'],
        {stdio:'inherit'});
} finally {
    rmSync(out,{recursive:true,force:true});
}
