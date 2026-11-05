import ts from 'typescript';
import {readdirSync,readFileSync} from 'node:fs';
import {join,relative} from 'node:path';
const root=new URL('../src/',import.meta.url).pathname;
const native=new Set(['button','input','select','textarea','table']);
const issues=[];let checked=0;
function scan(dir){for(const e of readdirSync(dir,{withFileTypes:true})){const file=join(dir,e.name);if(e.isDirectory()){scan(file);continue}if(!e.name.endsWith('.tsx')||e.name.endsWith('.test.tsx')||file===join(root,'ui.tsx'))continue;checked++;const source=ts.createSourceFile(file,readFileSync(file,'utf8'),ts.ScriptTarget.Latest,true,ts.ScriptKind.TSX);function visit(node){if((ts.isJsxOpeningElement(node)||ts.isJsxSelfClosingElement(node))&&native.has(node.tagName.getText(source))){const line=source.getLineAndCharacterOfPosition(node.getStart(source)).line+1;issues.push(`${relative(root,file)}:${line}: usa il componente comune per <${node.tagName.getText(source)}>`)}ts.forEachChild(node,visit)}visit(source)}}
scan(root);
if(issues.length){console.error(issues.join('\n'));process.exitCode=1}else console.log(`UI condivisa: ${checked} file verificati, nessun controllo duplicato.`);
