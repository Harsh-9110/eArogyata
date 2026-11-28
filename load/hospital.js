import http from 'k6/http';
import {check,sleep,fail} from 'k6';
import exec from 'k6/execution';
import {Counter,Trend} from 'k6/metrics';

const committed=new Counter('committed_transactions');
const businessLatency=new Trend('business_latency',true);
const BASE=__ENV.API_BASE||'http://localhost:8080/api/v1';
const ORIGIN=__ENV.WEB_ORIGIN||'http://localhost:3000';
export const options={
 scenarios:{hospital:{executor:'per-vu-iterations',vus:8,iterations:25,maxDuration:'5m'}},
 thresholds:{http_req_failed:['rate<0.01'],checks:['rate==1'],business_latency:['p(95)<500'],committed_transactions:['count>=500']},
 summaryTrendStats:['avg','med','p(90)','p(95)','p(99)','max'],
};
function request(method,path,token,body,expected,measure=false){
 const response=http.request(method,BASE+path,body===undefined?null:JSON.stringify(body),{headers:{'Content-Type':'application/json',Origin:ORIGIN,...(token?{Authorization:'Bearer '+token}:{})},tags:{name:method+' '+path.replace(/\/\d+/g,'/:id'),phase:measure?'workload':'setup'}});
 const good=check(response,{[`${method} ${path.replace(/\/\d+/g,'/:id')} status ${expected}`]:r=>r.status===expected});
 if(measure){businessLatency.add(response.timings.duration);if(good&&method!=='GET')committed.add(1);}
 if(!good)fail('Unexpected HTTP '+response.status+' for '+method+' '+path+'; inspect the API logs.');
 return expected===204?null:response.json();
}
export function setup(){
 if(!__ENV.ADMIN_EMAIL||!__ENV.ADMIN_PASSWORD)fail('Set ADMIN_EMAIL and ADMIN_PASSWORD for your isolated test deployment.');
 const admin=request('POST','/auth/login',null,{email:__ENV.ADMIN_EMAIL,password:__ENV.ADMIN_PASSWORD},200).accessToken;
 const suffix=Date.now().toString();const password='Load-test-only-'+suffix+'!';
 const doctor=request('POST','/admin/users',admin,{name:'Synthetic Load Doctor',email:'k6-doctor-'+suffix+'@example.test',password,role:'DOCTOR',specialty:'Load Test',room:'Load room'},201);
 const profile=request('GET','/doctors',admin,undefined,200).find(d=>d.userId===doctor.id);
 const date=new Date(Date.now()+2*86400000).toISOString().slice(0,10);
 const session=request('POST','/sessions',admin,{doctorId:profile.id,date,averageMinutes:12,startTime:'09:00',slotMinutes:12,slotCount:8},201);
 const slots=request('GET','/sessions/'+session.id+'/slots',admin,undefined,200);
 const beds=request('GET','/beds',admin,undefined,200).filter(b=>b.status==='AVAILABLE').slice(0,8);
 if(beds.length<8)fail('This test requires 8 available beds. Use an isolated fresh demo database.');
 const clients=[];
 for(let i=0;i<8;i++){
  const email='k6-patient-'+suffix+'-'+i+'@example.test';
  request('POST','/admin/users',admin,{name:'Synthetic Load Patient '+i,email,password,role:'PATIENT'},201);
  const token=request('POST','/auth/login',null,{email,password},200).accessToken;
  clients.push({token,bedId:beds[i].id,slotId:slots[i].id});
 }
 return {clients};
}
export default function(data){
 const client=data.clients[(exec.vu.idInTest-1)%data.clients.length];
 const appointment=request('POST','/appointments',client.token,{slotId:client.slotId},201,true);
 request('POST','/appointments/'+appointment.id+'/cancel',client.token,undefined,200,true);
 const reservation=request('POST','/reservations',client.token,{bedId:client.bedId},201,true);
 request('POST','/reservations/'+reservation.id+'/cancel',client.token,undefined,200,true);
 request('GET','/beds',client.token,undefined,200,true);
 sleep(0.4);
}
export function teardown(data){
 // Best-effort cleanup after interrupted iterations. Histories deliberately remain.
 for(const client of data.clients){
  const headers={Authorization:'Bearer '+client.token,Origin:ORIGIN};
  for(const [resource,states] of [['appointments',['BOOKED','WAITING']],['reservations',['RESERVED']]]){
   const rows=http.get(BASE+'/'+resource,{headers});if(rows.status!==200)continue;
   for(const item of rows.json())if(states.includes(item.status))http.post(BASE+'/'+resource+'/'+item.id+'/cancel',null,{headers});
  }
 }
}
export function handleSummary(data){
 data.run_metadata={recorded_at:new Date().toISOString(),api_base:BASE,scenario:'8 VUs × 25 iterations; OPD book/cancel and bed reserve/cancel; isolated synthetic accounts',environment:__ENV.BENCHMARK_ENV||'not specified'};
 return {'results/k6-summary.json':JSON.stringify(data,null,2),stdout:'k6 finished. Raw measured results: results/k6-summary.json. Run python3 scripts/update-benchmark.py to update README.\n'};
}
