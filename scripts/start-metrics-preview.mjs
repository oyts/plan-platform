// Same-process preview: killing its wrapper cannot orphan a Vite child.
import {preview} from '../web/node_modules/vite/dist/node/index.js';
import {fileURLToPath} from 'node:url';
import {mkdir} from 'node:fs/promises';import {fixtureControl} from './metrics-fixture-control.mjs';
const root=fileURLToPath(new URL('../web/',import.meta.url));
const server=await preview({root,configFile:root+'vite.admin.config.ts',preview:{host:'127.0.0.1',port:43130,strictPort:true}});
const folder=fileURLToPath(new URL('../acceptance-results/metrics-preview/'+new Date().toISOString().replace(/[:.]/g,'-')+'/',import.meta.url));await mkdir(folder,{recursive:true});let closing=false;const control=await fixtureControl(folder+'control.json',close);function close(){if(closing)return;closing=true;control.close();server.httpServer.close();}
for(const signal of ['SIGINT','SIGTERM'])process.on(signal,close);
