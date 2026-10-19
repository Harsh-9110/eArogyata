const fs=require('node:fs');
const path=require('node:path');
const os=require('node:os');
const ts=require('typescript');
const {createRequire}=require('node:module');
const temp=fs.mkdtempSync(path.join(os.tmpdir(),'earogyata-test-'));
fs.writeFileSync(path.join(temp,'package.json'),'{"type":"commonjs"}');
fs.symlinkSync(path.resolve(__dirname,'../node_modules'),path.join(temp,'node_modules'),process.platform==='win32'?'junction':'dir');
function compile(dir,relative=''){
 for(const item of fs.readdirSync(dir,{withFileTypes:true})){
  if(item.isDirectory())compile(path.join(dir,item.name),path.join(relative,item.name));
  else if(/\.tsx?$/.test(item.name)){
   const source=fs.readFileSync(path.join(dir,item.name),'utf8').replaceAll('import.meta.env','({})');
   const result=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022,jsx:ts.JsxEmit.ReactJSX,esModuleInterop:true}});
   const output=path.join(temp,relative,item.name.replace(/\.tsx?$/,'.js'));fs.mkdirSync(path.dirname(output),{recursive:true});fs.writeFileSync(output,result.outputText);
  }
 }
}
compile(path.resolve(__dirname,'../src'));
globalThis.BroadcastChannel=undefined;
globalThis.window={location:{hash:''},addEventListener(){},removeEventListener(){}};
Object.defineProperty(globalThis,'navigator',{value:{locks:{request:async(_name,fn)=>await fn()}},configurable:true});
process.on('exit',()=>fs.rmSync(temp,{recursive:true,force:true}));
module.exports={load:(name)=>createRequire(path.join(temp,'index.cjs'))('./'+name)};
