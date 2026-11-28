import type {Operations,Schemas} from './generated';
import {operationMeta} from './generated';
export type User=Schemas['User'];
type Session=Schemas['AuthResponse'];
export const API_BASE=import.meta.env.VITE_API_BASE_URL || '/api/v1';
let session:Session|null=null;
let refreshing:Promise<Session|null>|null=null;
const listeners=new Set<()=>void>();
const channel=typeof BroadcastChannel!=='undefined'?new BroadcastChannel('earogyata-auth'):null;
export class ApiError extends Error {
 constructor(public status:number,message:string) {super(message);this.name='ApiError';}
}
export function getSession(){return session;}
export function subscribe(listener:()=>void){listeners.add(listener);return ()=>{listeners.delete(listener);};}
export function acceptSession(value:Session|null,broadcast=true){session=value;listeners.forEach(fn=>fn());if(broadcast)channel?.postMessage(value);}
channel?.addEventListener('message',event=>acceptSession(event.data as Session|null,false));
async function parse<T>(response:Response):Promise<T>{
 if(!response.ok){let detail='The request could not be completed.';try{const p=await response.json();detail=p.detail||p.title||detail;}catch{/* Non-JSON gateway errors. */}throw new ApiError(response.status,detail);}
 return response.status===204?undefined as T:response.json() as Promise<T>;
}
export async function refreshSession():Promise<Session|null>{
 if(refreshing)return refreshing;
 const rotate=async()=>{
  try{const result=await parse<Session>(await fetch(`${API_BASE}/auth/refresh`,{method:'POST',credentials:'include',signal:AbortSignal.timeout(15000)}));acceptSession(result);return result;}
  catch(error){if(error instanceof ApiError && [401,403].includes(error.status)){acceptSession(null);return null;}throw error;}
 };
 const coordinated=async():Promise<Session|null>=>navigator.locks?await navigator.locks.request('earogyata-refresh',rotate):await rotate();
 refreshing=coordinated().finally(()=>{refreshing=null;});
 return refreshing;
}
export async function api<K extends keyof Operations>(name:K,args:Operations[K]['input']):Promise<Operations[K]['response']>{
 const definition=operationMeta[name];
 const input=args as {path?:Record<string,string|number>;query?:Record<string,string|number|undefined>;body?:unknown};
 let route:string=definition.path;
 for(const [key,value] of Object.entries(input.path??{}))route=route.replace(`{${key}}`,encodeURIComponent(String(value)));
 const search=new URLSearchParams();for(const [key,value] of Object.entries(input.query??{}))if(value!==undefined)search.set(key,String(value));
 const url=`${API_BASE}${route}${search.size?'?'+search:''}`;
 const publicAuth=['login','register','refresh'].includes(name);
 const send=()=>fetch(url,{method:definition.method,credentials:'include',signal:AbortSignal.timeout(15000),headers:{...(input.body!==undefined?{'Content-Type':'application/json'}:{}),...(!publicAuth && session?{'Authorization':`Bearer ${session.accessToken}`}:{})},...(input.body!==undefined?{body:JSON.stringify(input.body)}:{})});
 let response=await send();
 if(response.status===401 && !publicAuth && await refreshSession())response=await send();
 if(response.status===401 && !publicAuth)acceptSession(null);
 return parse<Operations[K]['response']>(response);
}
