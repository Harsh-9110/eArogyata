import test from 'node:test';
import assert from 'node:assert/strict';
import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import compiled from './compile.cjs';
const {BedsPage,QueuePage,Overview}=compiled.load('App.js');
const data={sessions:[],doctors:[],beds:[{id:1,wardId:1,wardName:'Synthetic Ward',floor:'Level 1',label:'T-01',status:'AVAILABLE'}],appointments:[],reservations:[],users:[],audit:[],queue:[],selected:null};
const user={id:1,name:'Synthetic User',email:'synthetic@example.test',role:'PATIENT'};
const common={data,user,busy:false,run:async()=>true,onReserve(){},onBook(){},onSession(){},date:'2026-10-20',setDate(){},select(){},selected:null};
test('patients can reserve but cannot see ward operational controls',()=>{
 const html=renderToStaticMarkup(createElement(BedsPage,common));assert.match(html,/Reserve bed/);assert.doesNotMatch(html,/Mark out of service|Mark ready|Admit<|Discharge</);
});
test('ward staff can take a bed out of service',()=>{
 const html=renderToStaticMarkup(createElement(BedsPage,{...common,user:{...user,role:'WARD_STAFF'}}));assert.match(html,/Mark out of service/);
});
test('doctor bed view has availability without reservation controls',()=>{
 const html=renderToStaticMarkup(createElement(BedsPage,{...common,user:{...user,role:'DOCTOR'}}));assert.doesNotMatch(html,/Reserve bed|Confirm reservation|Admissions &amp; reservations/);
});
test('empty queues and sessions have actionable empty states',()=>{
 const html=renderToStaticMarkup(createElement(QueuePage,common));assert.match(html,/No sessions on this date/);assert.match(html,/Your next visit starts here/);
});
test('dashboard numbers derive from data rather than invented performance claims',()=>{
 const html=renderToStaticMarkup(createElement(Overview,{data,user,go(){},onBook(){}}));assert.match(html,/Available beds/);assert.match(html,/>01</);assert.doesNotMatch(html,/35%|500\+/);
});
