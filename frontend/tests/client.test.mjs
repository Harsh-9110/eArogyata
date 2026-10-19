import test from 'node:test';
import assert from 'node:assert/strict';
import compiled from './compile.cjs';
const {api,acceptSession,getSession,refreshSession}=compiled.load('api/client.js');
const user={id:1,name:'Synthetic Patient',email:'synthetic@example.test',role:'PATIENT'};
const session={accessToken:'renewed',expiresIn:900,user};
function response(value,status=200){return new Response(status===204?null:JSON.stringify(value),{status,headers:{'Content-Type':'application/json'}});}

test('concurrent unauthorized requests rotate refresh once and retry with the new token',async()=>{
 acceptSession({...session,accessToken:'old'});let refreshes=0;let successes=0;
 globalThis.fetch=async(url,init)=>{
  if(url.endsWith('/auth/refresh')){refreshes++;await new Promise(resolve=>setTimeout(resolve,10));return response(session);}
  if(init.headers.Authorization==='Bearer renewed'){successes++;return response([]);}
  return response({detail:'expired'},401);
 };
 await Promise.all([api('beds',{}),api('doctors',{})]);
 assert.equal(refreshes,1);assert.equal(successes,2);assert.equal(getSession().accessToken,'renewed');
});
test('refresh failure clears only the in-memory session',async()=>{
 globalThis.fetch=async()=>response({detail:'invalid refresh session'},401);
 assert.equal(await refreshSession(),null);assert.equal(getSession(),null);
});
test('typed operation builds the expected URL and mutation body',async()=>{
 acceptSession(session);let captured;
 globalThis.fetch=async(url,init)=>{captured={url,init};return response({id:7,status:'AVAILABLE'});};
 await api('bedStatus',{path:{id:7},body:{status:'AVAILABLE'}});
 assert.equal(captured.url,'/api/v1/beds/7/status');assert.equal(captured.init.method,'PATCH');
 assert.deepEqual(JSON.parse(captured.init.body),{status:'AVAILABLE'});assert.equal(captured.init.credentials,'include');
});
test('server conflict details reach the UI and are not reported as success',async()=>{
 globalThis.fetch=async()=>response({status:409,detail:'Bed already reserved'},409);
 await assert.rejects(api('reserve',{body:{bedId:1}}),error=>error.status===409&&error.message==='Bed already reserved');
});
test('no-content response is supported for logout',async()=>{
 globalThis.fetch=async()=>response(null,204);assert.equal(await api('logout',{}),undefined);
});
