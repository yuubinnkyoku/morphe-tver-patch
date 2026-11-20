import { execFileSync } from 'node:child_process';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root=resolve(dirname(fileURLToPath(import.meta.url)), '..');
const src=resolve(root,'extensions/extension/src/main/java/org/example/tverpatches/extension');
const folder=mkdtempSync(join(tmpdir(),'morphe-nico-video-search-'));
try {
    execFileSync('javac',['-encoding','UTF-8','-d',folder,
        join(src,'NcoSearchMatcher.java'),
        join(src,'NicoVideoTitleMatcher.java'),
        resolve(root,'scripts/nco-search/NicoVideoTitleMatcherTest.java')],{stdio:'inherit'});
    execFileSync('java',['-cp',folder,
        'org.example.tverpatches.extension.NicoVideoTitleMatcherTest'],{stdio:'inherit'});
} finally {
    rmSync(folder,{recursive:true,force:true});
}
