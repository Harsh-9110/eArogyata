#!/usr/bin/env python3
"""Dependency-free structural checks; not a substitute for full OpenAPI or SQL runtime validation."""
import json,pathlib,re,xml.etree.ElementTree as ET
root=pathlib.Path(__file__).resolve().parents[1]
spec=json.loads((root/'docs/openapi.json').read_text());assert spec['openapi']=='3.0.3'
operations=set();supported={'get','post','patch','put','delete','options','head'}
def refs(node):
 if isinstance(node,dict):
  if '$ref' in node:
   reference=node['$ref'];assert reference.startswith('#/components/schemas/')
   assert reference.rsplit('/',1)[-1] in spec['components']['schemas'],reference
  for value in node.values():refs(value)
 elif isinstance(node,list):
  for value in node:refs(value)
refs(spec)
for path,methods in spec['paths'].items():
 for method,operation in methods.items():
  assert method in supported;name=operation['operationId'];assert name not in operations;operations.add(name)
  assert any(code.startswith('2') for code in operation['responses'])
  assert all(any(p['in']=='path' and p['name']==name and p.get('required') for p in operation.get('parameters',[])) for name in re.findall(r'\{(\w+)\}',path))
assert (root/'docs/openapi.json').read_bytes()==(root/'backend/src/main/resources/static/openapi.json').read_bytes()
ET.parse(root/'backend/pom.xml')
migrations=sorted((root/'backend/src/main/resources/db/migration').glob('V*__*.sql'));assert len(migrations)==3
sql='\n'.join(p.read_text() for p in migrations)
for name in ['uq_active_bed','uq_active_patient_bed','uq_slot_active','uq_patient_session_active','uq_one_consultation']:
 assert name in sql,name
java='\n'.join(p.read_text() for p in (root/'backend/src/main/java').rglob('*.java'))
assert 'FOR UPDATE' in java and 'AFTER_COMMIT' in java
assert 'spring-boot-starter-oauth2-resource-server' in (root/'backend/pom.xml').read_text()
assert 'localStorage' not in (root/'frontend/src/api/client.ts').read_text()
json.loads((root/'frontend/vercel.json').read_text())
print(f'Structural checks passed: {len(operations)} OpenAPI operations, {len(spec["components"]["schemas"])} schemas, {len(migrations)} migrations. This is not Spring/MySQL runtime verification.')
