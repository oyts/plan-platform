// Owner-approved, exact synthetic collaboration artifact. No account/session files read.
import {readFile,writeFile} from 'node:fs/promises';
const path='acceptance-results/historical-closure/pc/2026-10-22T12-30-42-406Z/plan-cabin-ai-collaboration.json';
const source=JSON.parse(await readFile(path,'utf8'));
if(source.format!=='plan-cabin-ai-collaboration'||source.draft.title!=='PC长规划合成路线'||source.draft.items.length!==12||source.draft.items[0].localId!=='t0')throw Error('Not the approved synthetic artifact');
source.draft.items[0].note='PC协作返回：练习十分钟，检查输出，再记录一个改进点。';
await writeFile('acceptance-results/ai-long/pc/returned-collaboration.json',JSON.stringify(source,null,2),{flag:'wx',mode:0o600});
console.log('Approved synthetic return prepared; only first item note changed.');
