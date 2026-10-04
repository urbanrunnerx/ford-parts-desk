"""Compile one card per named family, with source evidence on each exact base."""
import hashlib,json,re
from collections import defaultdict,Counter
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
load=lambda p:json.loads((ROOT/p).read_text(encoding='utf-8'))
pack=load('data/packaging-reference.json'); epc=load('data/epc-reference.json')
history=load('data/packaging-history.json'); labels=load('data/label-evidence.json')
specs=load('data/families.json'); evidence=defaultdict(list); legacy=defaultdict(list); observed=defaultdict(list)
identities=load('data/family-identities.json')
sources=[{'id':'packaging','name':pack['sourceName'],'url':pack['landingPage'],'download':pack['source'],'checkedAt':pack['retrievedAt'],'sha256':pack['sha256'],'scope':'Published packaging records, not a complete historical or VIN-fitment catalog.'},
 {'id':'epc','name':'Saved Snap-on EPC observations','url':epc['source'],'checkedAt':epc['checkedAt'],'scope':epc['coverage']},
 {'id':'legacy','name':'Public Ford Basic Numbers reference','url':'https://www.terminator-cobra.com/FordBasicNumber.pdf','checkedAt':'undated','scope':'Historical quick reference; exact vehicle applicability varies.'},
 {'id':'purge','name':'Published purge valve / vapor line example','url':specs[0]['source'],'checkedAt':'2026-09-17','scope':'K2GZ-9D289-A example; not a universal fitment assertion.'}]
sources.extend(labels['sources'])
for stamp in sorted({(x['retrievedAt'],x['sha256']) for x in history['entries']}):
 sources.append({'id':'packaging-history-'+stamp[1][:12],'name':'Historical Ford / Motorcraft packaging snapshot','url':history['landingPage'],'download':history['source'],'checkedAt':stamp[0],'sha256':stamp[1],'scope':history['coverage']})
current_pack={x['base']:x for x in pack['entries']}
by_pack={x['base']:x for x in history['entries']}
by_pack.update(current_pack)
for line in (ROOT/'data/legacy-reference.txt').read_text(encoding='utf-8').splitlines():
 if not line.strip(): continue
 page,base,name=line.split('|'); legacy[base].append(name); evidence[base].append({'source':'legacy','description':name,'page':int(page)})
for x in epc['entries']:
 base=x['base']; observed[base].append(x['name']); evidence[base].append({'source':'epc','description':x['name'],'catalog':x['catalog'],'locations':x['locations']})
for b,x in by_pack.items():
 source='packaging' if b in current_pack else 'packaging-history-'+x['sha256'][:12]
 evidence[b].insert(0,{'source':source,'descriptions':x['descriptions'],'examples':x['examples'],'serviceCount':x['serviceCount']})
for x in labels['entries']:
 assert x['base'] in evidence, 'Label enrichment must describe an existing, sourced base'
 assert any(s['id']==x['source'] for s in sources), 'Label source must be registered'
 assert all(re.sub(r'[^A-Z0-9]','',n)[4:4+len(x['base'])]==x['base'] for n in x['examples']), 'Label example must contain the exact base'
 evidence[x['base']].append({k:v for k,v in x.items() if k!='base'})
overrides={b:s for s in specs for b in s['bases']}
generic={'kit','module','electronic module','cover','plate','bracket','panel','pump','actuator','seal','gasket','switch','valve','clip','motor','tube','hose','control','wiring','wire','sensor','vta','upc1','support','reinforcement','retainer','housing','screw','bolt','nut','washer','pin','spring','shaft','gear','ring','pad','trim','moulding','molding','insulator','lever','rod','link','cable','cap','plug','spacer','connector','seal kit','cover kit','bracket kit'}
generic.update({'weatherstrip','handle','latch','shield','member','extension','strap','flange','guide','bearing','bushing','grommet','stop','mount','reservoir','sleeve','sealant','tape','fitting','hook','stud','hub'})
generic.update({'bar','receiver','gasket kit'})
def norm(s):
 s=s.lower().replace('a/c','ac').replace('vapour','vapor').replace('moulding','molding')
 s=re.sub(r'\b(assy|assembly|asy)\b','',s)
 return re.sub(r'[^a-z0-9]+',' ',s).strip()
def clean(s):
 s=s.split(' — ')[0].strip(); s=re.sub(r'\b(Assy|Assembly|ASY|assy)\b','',s)
 s=re.sub(r'\s+',' ',s).strip(' -')
 return s[0].upper()+s[1:] if s else 'Unspecified part'
def base_system(b):
 # Broad navigation only. Original full body bases remain unchanged.
 if len(b)>=7: return 'Body & interior'
 m=re.match(r'(\d+)',b); n=m.group(1)
 lead=int(n[:2]) if len(n)>=5 or (len(n)>=2 and len(b)>=6) or (len(n)==2 and len(b)==6) else int(n[0])
 if lead==1: return 'Wheels & tires'
 if lead==2: return 'Brakes'
 if lead==3: return 'Steering & suspension'
 if lead==4: return 'Transmission & driveline'
 if lead==5: return 'Exhaust & chassis'
 if lead in (6,8): return 'Engine & cooling'
 if lead==7: return 'Transmission & driveline'
 if lead==9: return 'Fuel & emissions'
 if 10<=lead<=15:return 'Electrical'
 if lead in (18,19): return 'Climate control' if not b.startswith('18C') else 'Electrical'
 return 'Body & interior'
def system(name,b):
 n=norm(name)
 rules=[('Climate control',r'\bac\b|air conditioning|blower|heater|cabin|hvac|evaporator'),('Brakes',r'brake|caliper|\babs\b'),('Wheels & tires',r'wheel|tire|tyre|tpms'),('Fuel & emissions',r'fuel|purge|vapor|throttle|injector|air filter|oxygen|\begr\b|charcoal'),('Steering & suspension',r'steering|suspension|control arm|tie rod|ball joint|shock|strut|stabilizer|pitman'),('Transmission & driveline',r'transmission|clutch|differential|axle|driveshaft|transfer case|flywheel|torque converter'),('Engine & cooling',r'engine|camshaft|crank|piston|oil |radiator|coolant|water pump|turbo|timing|valve cover'),('Exhaust & chassis',r'exhaust|muffler|catalytic'),('Electrical',r'ignition|battery|alternator|starter|lamp|bulb|wire|wiring|electrical|radio|speaker|camera')]
 return next((c for c,p in rules if re.search(p,n)),base_system(b))
families={}
for b in sorted(evidence):
 custom=overrides.get(b)
 names=legacy.get(b,[])+observed.get(b,[])
 if custom:
  name=custom['name']; cat=custom['category']; broad=False; aliases=custom['aliases']; note=custom.get('note','')
 elif names:
  name=clean(names[0]); cat=system(name,b); broad=norm(name) in generic
  # Keep EPC context for unqualified names; don't invent a component identity.
  if broad and observed.get(b):
   scoped=next((n for n in observed[b] if ' — ' in n),None)
   if scoped: name=scoped; broad=False
  aliases=[]; note=''
 else:
  raw=by_pack[b]['descriptions'][0]['name'].strip()
  name=clean(raw.title()); broad=norm(raw) in generic
  cat=system(name,b); aliases=[]; note=''
 key=norm(name)+' | '+cat
 g=families.setdefault(key,{'id':hashlib.sha1(key.encode()).hexdigest()[:12],'name':name,'category':cat,'broad':broad,'aliases':set(),'note':note,'bases':[]})
 if custom:
  g.update(name=custom['name'],category=custom['category'],broad=False)
  if custom.get('note'):g['note']=custom['note']
  g['preferredId']=identities.get(custom['bases'][0],g['id'])
 g['aliases'].update(names+aliases)
 # Packaging descriptions stay in evidence; generic terms must not crowd search matches.
 if not names and b in by_pack: g['aliases'].update(x['name'] for x in by_pack[b]['descriptions'])
 if b=='9D289': evidence[b].append({'source':'purge','description':'Vapor canister purge valve supplied as a fuel vapor separator tube assembly','examples':['K2GZ9D289A']})
 g['bases'].append({'number':b,'label':custom.get('labels',{}).get(b,'') if custom else '', 'evidence':evidence[b]})
parts=sorted(families.values(),key=lambda x:(x['broad'],x['name'].lower(),x['category']))
# A small, newly named subset must not steal the old broad group's bookmark ID.
# Assign each previous ID to the family retaining the most of its exact bases.
old_ids=set(identities.values())
# Unchanged name/category hashes retain their IDs even when older identity files
# cover only a subset of the bases now present in that broad group.
owners={g['id']:(float('inf'),g) for g in parts if g['id'] in old_ids}
for g in parts:
 for old_id,count in Counter(identities[b['number']] for b in g['bases'] if b['number'] in identities).items():
  if old_id not in owners or count>owners[old_id][0]: owners[old_id]=(count,g)
used_ids=set()
for g in parts:
 g['aliases']=sorted(g['aliases'])
 prior=sorted({identities[b['number']] for b in g['bases'] if b['number'] in identities and owners[identities[b['number']]][1] is g})
 preferred=g.pop('preferredId',None)
 candidate=(preferred if preferred in prior else None) or (prior[0] if prior else g['id'])
 if candidate in used_ids:
  candidate=hashlib.sha1((g['id']+'|'+','.join(b['number'] for b in g['bases'])).encode()).hexdigest()[:12]
 assert candidate not in used_ids, 'Family IDs must be unique'
 g['id']=candidate;used_ids.add(candidate)
 g['legacyIds']=[i for i in prior if i!=candidate]
allbases={b['number'] for p in parts for b in p['bases']}
# This additional diagnostic shows count without the first two body-style digits.
# It is not used for decoding or identity, and never claims shortened bases are interchangeable.
bodyCollapsed={b[2:] if len(b)>=7 else b for b in allbases}
stats={'baseNumbers':len(allbases),'partFamilies':len(parts),'detailedFamilies':sum(not p['broad'] for p in parts),'broadFamilies':sum(p['broad'] for p in parts),'packagingBases':pack['distinctBases'],'historicalPackagingBases':len(set(by_pack)-set(current_pack)),'legacyBases':len(legacy),'epcBases':len(observed),'bodyPrefixCollapsedCount':len(bodyCollapsed),'sourceServiceRows':sum(x['serviceCount'] for x in current_pack.values()),'categories':dict(sorted(Counter(p['category'] for p in parts).items()))}
assert len(allbases)>=3000
assert len(allbases)==sum(len(p['bases']) for p in parts), 'Each exact base belongs to one family'
assert len({(norm(p['name']),p['category']) for p in parts})==len(parts)
out={'schemaVersion':2,'version':'2.1.0','updatedAt':pack['retrievedAt'],'stats':stats,'coverage':'Broad published Ford/Motorcraft snapshot plus saved EPC observations and historical references. Some packaging descriptions are generic. Absent snapshot records remain dated historical evidence. No claim of every Ford base number or VIN fitment.','sources':sources,'parts':parts}
(ROOT/'dist/catalog.json').write_text(json.dumps(out,separators=(',',':'),ensure_ascii=False)+'\n',encoding='utf-8')
(ROOT/'data/catalog-audit.json').write_text(json.dumps({'stats':stats,'sourceSha256':pack['sha256'],'excludedRows':pack['excludedRows'],'coverage':out['coverage']},indent=2)+'\n')
print(json.dumps(stats,indent=2))
