// Runs only against your own local/isolated demo. All created accounts are synthetic.
import {chromium} from 'playwright';
import {randomBytes} from 'node:crypto';
import fs from 'node:fs';
const API=process.env.API_BASE||'http://localhost:8080/api/v1';
const WEB=process.env.WEB_ORIGIN||'http://localhost:3000';
const adminEmail=process.env.ADMIN_EMAIL||'admin@demo.earogyata.test';
const adminPassword=process.env.ADMIN_PASSWORD||'DemoOnly-ChangeMe42!';
const wardEmail=process.env.WARD_EMAIL||'ward@demo.earogyata.test';
const wardPassword=process.env.WARD_PASSWORD||adminPassword;
const ZONE=process.env.HOSPITAL_TIMEZONE||'Asia/Kolkata';
async function api(method,path,body,token){const response=await fetch(API+path,{method,headers:{'Content-Type':'application/json',Origin:WEB,...(token?{Authorization:`Bearer ${token}`}:{})},...(body?{body:JSON.stringify(body)}:{})});if(!response.ok)throw new Error(`${method} ${path}: HTTP ${response.status}`);return response.status===204?null:response.json();}
function localParts(date){const parts=new Intl.DateTimeFormat('en-US',{timeZone:ZONE,year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).formatToParts(date);const get=type=>parts.find(p=>p.type===type).value;return{date:['year','month','day'].map(get).join('-'),time:['hour','minute'].map(get).join(':')};}
const admin=(await api('POST','/auth/login',{email:adminEmail,password:adminPassword})).accessToken;
const suffix=Date.now(),password=randomBytes(20).toString('base64url')+'!';
const patient=await api('POST','/admin/users',{name:'Walkthrough Patient',email:`walkthrough-patient-${suffix}@example.test`,password,role:'PATIENT'},admin);
const doctor=await api('POST','/admin/users',{name:'Walkthrough Doctor',email:`walkthrough-doctor-${suffix}@example.test`,password,role:'DOCTOR',specialty:'General Medicine',room:'Demo room'},admin);
const profile=(await api('GET','/doctors',undefined,admin)).find(d=>d.userId===doctor.id);
const start=localParts(new Date(Date.now()+15*60000));
if(start.date!==localParts(new Date()).date||start.time>'23:20')throw new Error('Record during the hospital day, at least 40 minutes before midnight.');
await api('POST','/sessions',{doctorId:profile.id,date:start.date,startTime:start.time,averageMinutes:12,slotMinutes:12,slotCount:2},admin);
const bed=(await api('GET','/beds',undefined,admin)).find(b=>b.status==='AVAILABLE');if(!bed)throw new Error('No available demo bed');
fs.mkdirSync('output',{recursive:true});
const browser=await chromium.launch({headless:true});
const context=await browser.newContext({viewport:{width:1440,height:960},recordVideo:{dir:'output',size:{width:1440,height:960}}});
const page=await context.newPage();const recordingStarted=Date.now();
const pause=()=>page.waitForTimeout(3500);
async function login(email,pass){await page.goto(WEB);await page.getByLabel('Email address').fill(email);await page.getByLabel('Password',{exact:true}).fill(pass);await page.getByRole('button',{name:'Sign in',exact:true}).click();await page.getByRole('heading',{name:'Care, in motion.'}).waitFor();await pause();}
async function logout(){await page.getByRole('button',{name:'Sign out'}).click();await page.getByRole('heading',{name:'Welcome back.'}).waitFor();}
try{
 await login(adminEmail,adminPassword);await page.getByRole('link',{name:'People & access'}).click();await page.getByText(patient.email,{exact:true}).waitFor();await pause();await logout();
 await login(patient.email,password);await page.getByRole('link',{name:'My OPD visits'}).click();await page.getByRole('button',{name:/Walkthrough Doctor/}).click();await page.getByRole('button',{name:'Book a slot',exact:true}).click();await page.locator('.slot-button:not([disabled])').first().click();await page.getByRole('button',{name:'Confirm appointment'}).click();await page.getByRole('dialog').waitFor({state:'hidden'});await page.getByRole('button',{name:'Check in',exact:true}).click();await page.getByText('waiting',{exact:true}).first().waitFor();await pause();
 await page.getByRole('link',{name:'Beds & admissions'}).click();await page.locator('.bed-card').filter({has:page.getByRole('heading',{name:bed.label,exact:true})}).getByRole('button',{name:'Reserve bed'}).click();await page.getByRole('button',{name:'Confirm reservation'}).click();await page.getByRole('dialog').waitFor({state:'hidden'});await pause();await logout();
 await login(doctor.email,password);await page.getByRole('link',{name:'OPD queue'}).click();await page.getByRole('button',{name:/Walkthrough Doctor/}).click();await page.getByRole('button',{name:'Call next'}).click();await page.getByRole('button',{name:'Complete visit'}).waitFor();await pause();await page.getByRole('button',{name:'Complete visit'}).click();await pause();await logout();
 await login(wardEmail,wardPassword);await page.getByRole('link',{name:'Beds & admissions'}).click();let row=page.getByRole('row').filter({hasText:'Walkthrough Patient'});await row.getByRole('button',{name:'Admit',exact:true}).click();await row.getByRole('button',{name:'Discharge',exact:true}).waitFor();await pause();page.once('dialog',dialog=>dialog.accept());await row.getByRole('button',{name:'Discharge',exact:true}).click();await page.locator('.bed-card').filter({has:page.getByRole('heading',{name:bed.label,exact:true})}).getByRole('button',{name:'Mark ready'}).click();await pause();await logout();
 await login(adminEmail,adminPassword);await page.getByRole('link',{name:'Audit trail'}).click();await page.getByRole('heading',{name:'Audit trail',exact:true}).waitFor();
 await page.waitForTimeout(Math.max(1000,120000-(Date.now()-recordingStarted)));
 const video=page.video();await context.close();const path=await video.path();console.log('Recorded real UI walkthrough: '+path);
}finally{await browser.close();}
