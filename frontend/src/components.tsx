import {createContext,useContext,useEffect,useRef} from 'react';
import type {ReactNode} from 'react';
import {X,LoaderCircle,Activity,Inbox} from 'lucide-react';
export const ActionErrorContext=createContext('');
export function Brand(){return <span className="brand"><span className="brand-mark"><Activity size={22} strokeWidth={2.5}/></span><span>e-AROGYATA<small>HOSPITAL WORKSPACE</small></span></span>;}
export function Badge({value}:{value:string}){return <span className={`badge ${value.toLowerCase().replaceAll('_','-')}`}>{value.replaceAll('_',' ').toLowerCase()}</span>;}
export function Empty({title,children}:{title:string;children?:ReactNode}){return <div className="empty"><Inbox size={28}/><strong>{title}</strong>{children&&<p>{children}</p>}</div>;}
export function Spinner(){return <LoaderCircle className="spin" size={18}/>;}
export function Modal({title,description,onClose,children}:{title:string;description?:string;onClose:()=>void;children:ReactNode}){
 const ref=useRef<HTMLDialogElement>(null);const error=useContext(ActionErrorContext);
 useEffect(()=>{const dialog=ref.current;dialog?.showModal();return()=>dialog?.close();},[]);
 return <dialog ref={ref} className="modal" aria-labelledby="dialog-title" aria-describedby={description?'dialog-description':undefined} onCancel={onClose}><div className="modal-heading"><div><span className="eyebrow">e-AROGYATA</span><h2 id="dialog-title">{title}</h2></div><button className="icon-button" aria-label="Close dialog" onClick={onClose}><X size={20}/></button></div>{description&&<p id="dialog-description" className="muted">{description}</p>}{error&&<p className="error modal-error" role="alert">{error}</p>}{children}</dialog>;
}
export function initials(name:string){return name.split(' ').filter(Boolean).slice(0,2).map(n=>n[0]).join('').toUpperCase();}
export const ZONE=import.meta.env.VITE_HOSPITAL_TIMEZONE||'Asia/Kolkata';
export function hospitalDate(){const parts=new Intl.DateTimeFormat('en-US',{timeZone:ZONE,year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(new Date());return ['year','month','day'].map(k=>parts.find(p=>p.type===k)?.value).join('-');}
export function time(value:string){return new Intl.DateTimeFormat('en-IN',{hour:'numeric',minute:'2-digit',timeZone:ZONE}).format(new Date(value));}
export function displayDate(value:string){return new Intl.DateTimeFormat('en-IN',{day:'numeric',month:'short',timeZone:ZONE}).format(new Date(value+'T12:00:00Z'));}
