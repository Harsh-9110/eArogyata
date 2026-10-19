import test from 'node:test';
import assert from 'node:assert/strict';
import compiled from './compile.cjs';
const {connectLive}=compiled.load('api/live.js');
const {acceptSession}=compiled.load('api/client.js');
test('fragmented SSE invalidations refresh data without putting a token in the URL',async()=>{
 const signal=new AbortController();let changes=0;let requested;
 acceptSession({accessToken:'sensitive-access',expiresIn:900,user:{id:1,name:'Synthetic',email:'s@example.test',role:'PATIENT'}});
 globalThis.fetch=async(url,init)=>{
  requested={url,init};
  const chunks=['event: heart','beat\ndata: {"resource":"heartbeat"}\n\n','event: invali','date\ndata: {"resource":"beds"}\n\n'];
  return new Response(new ReadableStream({start(controller){for(const text of chunks)controller.enqueue(new TextEncoder().encode(text));controller.close();}}),{status:200});
 };
 await connectLive(signal.signal,()=>{if(++changes===2)signal.abort();},()=>{});
 assert.equal(changes,2);assert.equal(requested.url,'/api/v1/events');assert.equal(requested.init.headers.Authorization,'Bearer sensitive-access');
});
