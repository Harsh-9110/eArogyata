#!/usr/bin/env python3
"""Active probes for your own isolated synthetic deployment. Never emits credentials/tokens."""
import argparse,json,pathlib,time,urllib.request,urllib.error,uuid
p=argparse.ArgumentParser();p.add_argument('--api',default='http://localhost:8080/api/v1');p.add_argument('--origin',default='http://localhost:3000');args=p.parse_args()
records=[]
def call(method,path,body=None,token=None,cookie=None,origin=None):
 headers={'Content-Type':'application/json'}
 if token:headers['Authorization']='Bearer '+token
 if cookie:headers['Cookie']='earogyata_refresh='+cookie
 if origin:headers['Origin']=origin
 request=urllib.request.Request(args.api+path,data=None if body is None else json.dumps(body).encode(),headers=headers,method=method)
 try:response=urllib.request.urlopen(request,timeout=15)
 except urllib.error.HTTPError as error:response=error
 payload=response.read();data=json.loads(payload) if payload and 'json' in response.headers.get('Content-Type','') else None
 return response.status,data,response.headers
def expect(label,response,statuses):
 expected=[statuses] if isinstance(statuses,int) else statuses
 ok=response[0] in expected;records.append({'probe':label,'status':response[0],'expected':expected,'passed':ok})
 if not ok:raise AssertionError(label+' returned '+str(response[0]))
def cookie(headers):return headers.get('Set-Cookie','').split(';')[0].partition('=')[2]
try:
 expect('unauthenticated bed access',call('GET','/beds'),401)
 email='probe-'+uuid.uuid4().hex+'@example.test';password='Synthetic-security-test42!'
 expect('public role escalation rejected',call('POST','/auth/register',{'name':'Probe','email':email,'password':password,'role':'ADMIN'}),400)
 registered=call('POST','/auth/register',{'name':'Synthetic Probe Patient','email':email,'password':password});expect('patient signup',registered,201)
 token=registered[1]['accessToken'];old_cookie=cookie(registered[2]);assert registered[1]['user']['role']=='PATIENT'
 assert 'refreshToken' not in registered[1];assert 'HttpOnly' in registered[2]['Set-Cookie'];assert 'SameSite=Strict' in registered[2]['Set-Cookie']
 records.append({'probe':'refresh token excluded from JSON and cookie hardened','passed':True})
 expect('patient audit access denied',call('GET','/admin/audit',token=token),403)
 expect('patient staff provisioning denied',call('POST','/admin/users',{'name':'Elevated','email':'elevated-'+uuid.uuid4().hex+'@example.test','password':password,'role':'ADMIN'},token=token),403)
 expect('JWT signature tampering rejected',call('GET','/auth/me',token=token.rsplit('.',1)[0]+'.invalid'),401)
 expect('negative bed id validation',call('POST','/reservations',{'bedId':-1},token=token),400)
 inventory=call('GET','/beds',token=token);expect('authorized availability',inventory,200)
 assert all(not any(k in item for k in ['patientName','patientId','email','password']) for item in inventory[1])
 records.append({'probe':'bed availability does not disclose patient identity','passed':True})
 expect('refresh without Origin denied',call('POST','/auth/refresh',cookie=old_cookie),403)
 expect('cross-site refresh denied',call('POST','/auth/refresh',cookie=old_cookie,origin='https://attacker.example'),403)
 rotated=call('POST','/auth/refresh',cookie=old_cookie,origin=args.origin);expect('refresh rotation',rotated,200)
 expect('used refresh token rejected',call('POST','/auth/refresh',cookie=old_cookie,origin=args.origin),401)
 expect('replay revokes rotated access token',call('GET','/auth/me',token=rotated[1]['accessToken']),401)
 session=call('POST','/auth/login',{'email':email,'password':password});expect('fresh login',session,200)
 expect('session logout',call('POST','/auth/logout',token=session[1]['accessToken']),204)
 expect('logged-out token rejected',call('GET','/auth/me',token=session[1]['accessToken']),401)
 # A different nonexistent email isolates this test from the successful login bucket.
 bad_email='throttle-'+uuid.uuid4().hex+'@example.test'
 for _ in range(8):expect('invalid credentials rejected',call('POST','/auth/login',{'email':bad_email,'password':password}),401)
 expect('credential brute-force throttled',call('POST','/auth/login',{'email':bad_email,'password':password}),429)
except Exception as error:
 records.append({'probe':'probe execution','passed':False,'detail':str(error)[:300]})
finally:
 root=pathlib.Path(__file__).resolve().parents[1];out=root/'results/security-probe.json';out.parent.mkdir(exist_ok=True)
 passed=all(item['passed'] for item in records)
 report={'kind':'active HTTP security probes','recorded_at':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),'api':args.api,'passed':passed,'probes':records}
 out.write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
 if not passed:raise SystemExit(1)
