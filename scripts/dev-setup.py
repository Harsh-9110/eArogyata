#!/usr/bin/env python3
import argparse,secrets,pathlib
p=argparse.ArgumentParser(description='Generate local-only development secrets. Existing .env files are preserved.')
p.add_argument('--demo',action='store_true');a=p.parse_args()
root=pathlib.Path(__file__).resolve().parents[1];target=root/'.env'
if target.exists():raise SystemExit('.env already exists; keep it or explicitly remove your local copy before regenerating.')
text=(root/'.env.example').read_text()
for key in ['MYSQL_ROOT_PASSWORD','DB_PASSWORD','JWT_SECRET']:
 text=text.replace(key+'=\n',key+'='+secrets.token_urlsafe(40)+'\n')
if a.demo:text=text.replace('SPRING_PROFILES_ACTIVE=\n','SPRING_PROFILES_ACTIVE=demo\n').replace('VITE_DEMO_MODE=false','VITE_DEMO_MODE=true')
target.write_text(text);target.chmod(0o600)
print('Local development configuration created. Run docker compose up --build -d.')
if a.demo:print('Demo profile enabled. All demo accounts use DemoOnly-ChangeMe42! on this local instance.')
