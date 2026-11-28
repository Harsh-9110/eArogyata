import fs from 'node:fs';
import {fileURLToPath} from 'node:url';
const origin=new URL(process.argv[2]??'https://replace-with-your-api-host.example');
if(origin.protocol!=='https:'||origin.username||origin.password||origin.pathname!=='/'||origin.search||origin.hash||origin.hostname.endsWith('.example'))throw new Error('Pass a real HTTPS API origin with no path or credentials.');
const file=fileURLToPath(new URL('../frontend/vercel.json',import.meta.url));
const config=JSON.parse(fs.readFileSync(file,'utf8'));config.rewrites[0].destination=origin.origin+'/api/:path*';fs.writeFileSync(file,JSON.stringify(config,null,2)+'\n');
console.log('Vercel API rewrite configured for '+origin.origin);
