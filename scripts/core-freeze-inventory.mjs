import ts from '../web/node_modules/typescript/lib/typescript.js';
import {readFileSync,existsSync,writeFileSync,mkdirSync} from 'node:fs';
import {resolve,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
const root=fileURLToPath(new URL('../',import.meta.url));
export function extractRoutes(text){
 const source=ts.createSourceFile('router.ts',text,ts.ScriptTarget.Latest,true),imports=new Map();let router;
 for(const s of source.statements)if(ts.isImportDeclaration(s)&&s.importClause?.name&&ts.isStringLiteral(s.moduleSpecifier))imports.set(s.importClause.name.text,s.moduleSpecifier.text);
 function find(n){if(ts.isCallExpression(n)&&n.expression.getText(source)==='createRouter')router=n.arguments[0];ts.forEachChild(n,find);}find(source);
 const props=o=>new Map(o.properties.filter(ts.isPropertyAssignment).map(p=>[p.name.getText(source).replaceAll(/['"]/g,''),p.initializer]));
 if(!router||!ts.isObjectLiteralExpression(router))throw Error('Explicit createRouter object required');
 const routes=props(router).get('routes');if(!routes||!ts.isArrayLiteralExpression(routes))throw Error('Explicit routes required');const result=[];
 function walk(array,parent='',inherited={}){for(const o of array.elements){if(!ts.isObjectLiteralExpression(o))throw Error('Unsupported route expression');const p=props(o),node=p.get('path');if(!node||!ts.isStringLiteral(node))throw Error('Literal route path required');const path=node.text.startsWith('/')?node.text:parent+(node.text?'/'+node.text:'');const meta={...inherited};if(p.get('meta')&&ts.isObjectLiteralExpression(p.get('meta')))for(const [k,v]of props(p.get('meta')))meta[k]=v.kind===ts.SyntaxKind.TrueKeyword;
 const c=p.get('component'),raw=c?.getText(source)||'',component=imports.get(raw)||raw.match(/import\(['"]([^'"]+)['"]\)/)?.[1]||null;const redirect=p.get('redirect');result.push({path,component,redirect:redirect&&ts.isStringLiteral(redirect)?redirect.text:null,public:meta.public===true,admin:meta.admin===true,layout:!!p.get('children')});if(p.get('children'))walk(p.get('children'),path,meta);}}
 walk(routes);return result;
}
export function auditInventory(){
 const routes=extractRoutes(readFileSync(resolve(root,'web/src/router/index.ts'),'utf8')),scope=JSON.parse(readFileSync(resolve(root,'scripts/core-freeze-scope.json'),'utf8')),contract=JSON.parse(readFileSync(resolve(root,'docs/ui-rebuild-contract.json'),'utf8'));
 for(const r of routes)if(r.component&&(!r.component.startsWith('@/views/')||!existsSync(resolve(root,'web/src',r.component.slice(2)))))throw Error('Missing view for '+r.path);
 for(const chain of scope.coreChains)for(const path of chain.routes)if(!routes.some(r=>r.path===path))throw Error('Missing frozen core path '+path);
 for(const alias of contract.compatibilityAliases)if(!routes.some(r=>r.path===alias.path&&r.redirect===alias.redirect))throw Error('Legacy alias changed '+alias.path);
 for(const link of contract.deepLinks)if(!routes.some(r=>r.path===link.path))throw Error('Deep-link destination missing '+link.path);
 return{schema:1,version:scope.version,scope:'Source-derived route/view/auth inventory, not runtime permission proof',routes};
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 const result=auditInventory(),out=resolve(root,'docs/ui-route-inventory.json');mkdirSync(dirname(out),{recursive:true});writeFileSync(out,JSON.stringify(result,null,2)+'\n');console.log('Source inventory validated: '+result.routes.length+' route records; UI behavior still requires regression.');
}
