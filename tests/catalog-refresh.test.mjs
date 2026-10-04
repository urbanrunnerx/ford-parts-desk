import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {createIndex, search} from '../dist/core.js';
const read = name => JSON.parse(fs.readFileSync(new URL(name,import.meta.url),'utf8'));
const data=read('../dist/catalog.json'), source=read('../data/packaging-reference.json');
const history=read('../data/packaging-history.json'), labels=read('../data/label-evidence.json');
const report=read('../data/packaging-refresh.json'), index=createIndex(data.parts);
test('catalog date and current snapshot counters match their source',()=>{
 assert.equal(data.updatedAt,source.retrievedAt);
 assert.equal(data.stats.packagingBases,source.entries.length);
 assert.equal(data.stats.sourceServiceRows,source.entries.reduce((n,x)=>n+x.serviceCount,0));
 assert.equal(data.stats.historicalPackagingBases,history.entries.length);
 assert.equal(source.inputRows, data.stats.sourceServiceRows+Object.values(source.excludedRows).reduce((n,x)=>n+x,0));
 assert.equal(report.sha256,source.sha256);
 assert.equal(report.distinctBases-report.previousDistinctBases,report.addedBases.length-report.absentBases.length);
 for(const b of report.addedBases)assert.ok(index.byBase.has(b),b);
});
test('bases absent from the current download retain dated historical evidence',()=>{
 const active=new Set(source.entries.map(x=>x.base));
 for(const item of history.entries){
  assert.equal(active.has(item.base),false);
  const base=index.byBase.get(item.base).bases.find(x=>x.number===item.base);
  assert.equal(base.evidence.some(x=>x.source==='packaging'),false);
  const evidence=base.evidence.find(x=>x.source.startsWith('packaging-history-'));
  assert.ok(evidence,item.base);
  const origin=data.sources.find(x=>x.id===evidence.source);
  assert.equal(origin.checkedAt,item.retrievedAt);
  assert.equal(origin.sha256,item.sha256);
  assert.match(origin.scope,/absence does not imply discontinuation/i);
 }
});
test('documented label improvements are searchable and source-backed',()=>{
 for(const [query,bases] of [
  ['transmission range sensor',['7H557']],['caliper bracket',['2B292']],
  ['trunk torsion spring',['5444890','5444891']],['door opening seal',['1020708']],
  ['window sweep',['1021452','1021453','1021456','1021457']],
 ]){
  const results=search(query,index);
  assert.ok(results.length,query);
  for(const b of bases)assert.ok(results.slice(0,3).some(r=>r.part.codes.includes(b)),`${query}: ${b}`);
 }
 for(const entry of labels.entries){
  const p=index.byBase.get(entry.base), b=p.bases.find(b=>b.number===entry.base);
  assert.ok(b.evidence.some(e=>e.source===entry.source));
  assert.ok(data.sources.find(s=>s.id===entry.source)?.checkedAt);
  assert.ok(p.note.includes('EPC')||p.note.includes('fitment'));
  for(const example of entry.examples)assert.equal(example.slice(4,4+entry.base.length),entry.base);
 }
});
test('splitting broad groups preserves their bookmark IDs and does not reassign notes',()=>{
 for(const [name,category,id]of [
  ['Weatherstrip','Body & interior','182cf34c1fdc'],['Support','Brakes','2859103537ba'],['Bar','Body & interior','5e54bc1eaa42'],
 ]){
  const broad=data.parts.find(p=>p.name===name&&p.category===category);
  assert.equal(broad.id,id);
  for(const p of data.parts)if(p!==broad)assert.equal(p.legacyIds.includes(id),false,p.name);
 }
 assert.equal(new Set(data.parts.map(p=>p.id)).size,data.parts.length);
});

test('published packaging provenance uses public query-free download links',()=>{
 for(const snapshot of [source,history]){
  assert.equal(snapshot.landingPage,snapshot.source);
  assert.equal(new URL(snapshot.landingPage).search,'');
 }
 for(const origin of data.sources.filter(s=>s.id==='packaging'||s.id.startsWith('packaging-history-'))){
  assert.equal(origin.url,source.source);
  assert.equal(new URL(origin.url).search,'');
 }
});
