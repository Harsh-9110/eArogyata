import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const spec=JSON.parse(fs.readFileSync(path.join(root,'docs/openapi.json'),'utf8'));
function type(s) {
 let t;
 if(s.$ref)t=`Schemas[${JSON.stringify(s.$ref.split('/').at(-1))}]`;
 else if(s.enum)t=s.enum.map(JSON.stringify).join(' | ');
 else if(s.type==='array')t=`Array<${type(s.items)}>`;
 else if(s.type==='object')t=`{ ${Object.entries(s.properties??{}).map(([k,v])=>`${JSON.stringify(k)}${(s.required??[]).includes(k)?'':'?'}: ${type(v)}`).join('; ')} }`;
 else t=({integer:'number',number:'number',boolean:'boolean',string:'string'})[s.type]??'unknown';
 return t+(s.nullable?' | null':'');
}
let output='// Generated from docs/openapi.json. Run npm run generate:api; do not edit.\nexport interface Schemas {\n';
for(const [name,schema] of Object.entries(spec.components.schemas)) output+=`${JSON.stringify(name)}: ${type(schema)};\n`;
output+='}\nexport interface Operations {\n';
const meta={};
for(const [route,methods] of Object.entries(spec.paths)) for(const [method,op] of Object.entries(methods)) {
 if(!op.operationId || route==='/events')continue;
 const success=Object.entries(op.responses).find(([code])=>code.startsWith('2'))?.[1];
 const response=success?.content?.['application/json']?.schema;
 const body=op.requestBody?.content?.['application/json']?.schema;
 const pathParams=(op.parameters??[]).filter(x=>x.in==='path');
 const query=(op.parameters??[]).filter(x=>x.in==='query');
 let inputs=[];
 if(body)inputs.push(`body: ${type(body)}`);
 if(pathParams.length)inputs.push(`path: {${pathParams.map(x=>`${x.name}: ${type(x.schema)}`).join(';')}}`);
 if(query.length)inputs.push(`query?: {${query.map(x=>`${x.name}${x.required?'':'?'}: ${type(x.schema)}`).join(';')}}`);
 output+=`${op.operationId}: {input: {${inputs.join(';')}}; response: ${response?type(response):'void'}};\n`;
 meta[op.operationId]={method:method.toUpperCase(),path:route};
}
output+='}\nexport const operationMeta = '+JSON.stringify(meta,null,2)+' as const;\n';
const target=path.join(root,'frontend/src/api/generated.ts');fs.mkdirSync(path.dirname(target),{recursive:true});fs.writeFileSync(target,output);
fs.copyFileSync(path.join(root,'docs/openapi.json'),path.join(root,'backend/src/main/resources/static/openapi.json'));
console.log(`Generated ${Object.keys(spec.components.schemas).length} schemas and ${Object.keys(meta).length} typed operations.`);
