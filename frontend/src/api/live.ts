import {API_BASE,getSession,refreshSession} from './client';
export async function connectLive(signal:AbortSignal,onChange:()=>void,onState:(state:'live'|'reconnecting')=>void){
 while(!signal.aborted){
  try{
   const current=getSession();if(!current)return;
   const response=await fetch(`${API_BASE}/events`,{headers:{Authorization:`Bearer ${current.accessToken}`},signal,credentials:'include'});
   if(response.status===401){await refreshSession();continue;}
   if(!response.ok || !response.body)throw new Error('Live connection unavailable');
   onState('live');onChange();
   const reader=response.body.getReader();const decoder=new TextDecoder();let pending='';
   try{while(!signal.aborted){const {value,done}=await reader.read();if(done)break;pending=(pending+decoder.decode(value,{stream:true})).replace(/\r\n/g,'\n');
    let boundary;while((boundary=pending.indexOf('\n\n'))>=0){const event=pending.slice(0,boundary);pending=pending.slice(boundary+2);if(event.includes('event:invalidate') || event.includes('event: invalidate'))onChange();}
   }}finally{reader.releaseLock();}
  }catch(error){if(signal.aborted)return;}
  if(signal.aborted)return;
  onState('reconnecting');
  await new Promise<void>(resolve=>{const abort=()=>{clearTimeout(timer);resolve();};const timer=setTimeout(()=>{signal.removeEventListener('abort',abort);resolve();},3000);signal.addEventListener('abort',abort,{once:true});});
 }
}
