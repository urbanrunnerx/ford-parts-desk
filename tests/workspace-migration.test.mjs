import test from 'node:test';
import assert from 'node:assert/strict';
import {prepareWorkspaceRestore,applyWorkspaceRestore,migrateLegacyWorkspace} from '../dist/workspace.js';
const vin='1M8GDM9AXKP042788';
const item={id:'old-hub',name:'Wheel hub',bases:['1104'],qty:2,note:'Bin 4',selection:{vin,base:'1104',serviceNumber:'TEST-1104-A',source:'Employee selected',context:'Front',stale:false}};
const job={id:'job-legacy',job:'RO fixture',vin,vehicle:'Fixture vehicle',rows:[item],status:'Open'};
const backup=()=>({schema:2,favorites:['old-hub'],recent:['hub'],notes:{'old-hub':'Keep note'},imports:[{base:'043B13',name:'Custom airbag',aliases:['srs'],source:'https://example.com/reference'}],worksheet:structuredClone(job),jobs:{activeId:job.id,items:[structuredClone(job),{...structuredClone(job),id:'done-job',status:'Done'}]},vinCache:[{vin,title:'Fixture vehicle',decodedAt:'2026-10-01T12:00:00Z',fields:[{label:'Make',value:'FORD'}]}],theme:'dark',layout:true});
test('v3 schema2 backup preserves complete jobs, saved references and settings in a separate app',()=>{
 const original=backup(),payload=prepareWorkspaceRestore(original,[{id:'new-hub',legacyIds:['old-hub']}]);
 assert.deepEqual(payload.favorites,['new-hub']);assert.equal(payload.notes['new-hub'],'Keep note');assert.equal(payload.jobs.items.length,2);assert.equal(payload.worksheet.id,job.id);
 assert.equal(payload.worksheet.rows[0].selection.serviceNumber,'TEST-1104-A');assert.equal(payload.worksheet.rows[0].note,'Bin 4');assert.equal(payload.theme,'dark');assert.equal(payload.layout,true);assert.equal(payload['vin-cache'][0].vin,vin);assert.equal(payload.imports[0].base,'043B13');
 payload.jobs.items[0].rows[0].note='Changed in Next';assert.equal(original.jobs.items[0].rows[0].note,'Bin 4');
});
test('legacy schema1 worksheet migrates into a job with default settings',()=>{
 const d=backup();d.schema=1;delete d.jobs;delete d.vinCache;delete d.theme;delete d.layout;delete d.worksheet.id;
 const payload=prepareWorkspaceRestore(d);assert.equal(payload.jobs.items.length,1);assert.ok(payload.jobs.activeId.startsWith('job-'));assert.equal(payload.worksheet.vin,vin);assert.equal(payload.theme,'light');assert.equal(payload.layout,false);assert.deepEqual(payload['vin-cache'],[]);
});
test('authentication and pending lookups never enter migration payload',()=>{
 const d={...backup(),cookies:['not-transferable'],password:'not-transferable',epcPending:{id:'old-request'},tokens:{secret:'not-transferable'}};
 assert.deepEqual(Object.keys(prepareWorkspaceRestore(d)).sort(),['favorites','imports','jobs','layout','notes','recent','theme','vin-cache','worksheet'].sort());
});
test('invalid and empty-base backups are rejected before writing destination data',()=>{
 for(const change of [d=>d.jobs.items[0].rows[0].bases=[],d=>d.jobs.activeId='missing',d=>d.jobs.items.push(d.jobs.items[0]),d=>d.imports[0].source='javascript:alert(1)']){const d=backup();change(d);assert.throws(()=>prepareWorkspaceRestore(d));}
 assert.throws(()=>prepareWorkspaceRestore(null));
});
test('quota failure restores original values in reverse mutation order',()=>{
 const values=new Map([['p-a','"aaaaaaaa"'],['p-b','""']]);
 const storage={getItem:k=>values.get(k)??null,removeItem:k=>values.delete(k),setItem(k,v){const next=new Map(values);next.set(k,v);if([...next.values()].reduce((n,x)=>n+x.length,0)>12)throw Error('QuotaExceeded');values.set(k,v);}};
 const before=[...values];assert.throws(()=>applyWorkspaceRestore(storage,'p-',{a:1,b:'bbbbbb',c:'x'.repeat(50)}),/rolled back/);assert.deepEqual([...values],before);
});
test('successful restore writes only the destination namespace and does not touch another app',()=>{
 const values=new Map([['unrelated-key','preserved']]);const storage={getItem:k=>values.get(k)??null,removeItem:k=>values.delete(k),setItem:(k,v)=>values.set(k,v)};
 const payload=prepareWorkspaceRestore(backup());applyWorkspaceRestore(storage,'partsdesk-v1-',payload);assert.equal(values.get('unrelated-key'),'preserved');assert.equal(JSON.parse(values.get('partsdesk-v1-jobs')).activeId,job.id);assert.equal(values.size,10);
});

function migrationStorage(backupData=backup(),legacyPrefix='legacy-parts-v1-'){
 const payload={favorites:backupData.favorites,recent:backupData.recent,notes:backupData.notes,imports:backupData.imports,worksheet:backupData.worksheet,jobs:backupData.jobs,'vin-cache':backupData.vinCache,theme:backupData.theme,layout:backupData.layout};
 const values=new Map(Object.entries(payload).map(([key,value])=>[legacyPrefix+key,JSON.stringify(value)]));
 const storage={get length(){return values.size;},key:i=>[...values.keys()][i]??null,getItem:key=>values.get(key)??null,setItem:(key,value)=>values.set(key,value),removeItem:key=>values.delete(key)};
 return {storage,values};
}
test('one validated same-origin legacy workspace copies only workspace data and preserves its originals',()=>{
 const {storage,values}=migrationStorage();values.set('legacy-parts-v1-epcPending','private-session-fixture');values.set('legacy-parts-v1-token','private-token-fixture');const before=new Map(values);
 assert.equal(migrateLegacyWorkspace(storage).status,'migrated');
 assert.equal(JSON.parse(values.get('partsdesk-v1-jobs')).activeId,job.id);
 assert.equal(JSON.parse(values.get('partsdesk-v1-notes'))['old-hub'],'Keep note');
 assert.equal(values.has('partsdesk-v1-token'),false);assert.equal(values.has('partsdesk-v1-epcPending'),false);
 for(const [key,value]of before)assert.equal(values.get(key),value,key);
});
test('multiple legacy workspaces are not guessed or copied',()=>{
 const {storage,values}=migrationStorage();values.set('another-parts-v1-jobs',JSON.stringify(backup().jobs));const before=[...values];
 assert.equal(migrateLegacyWorkspace(storage).status,'blocked');assert.deepEqual([...values].filter(([key])=>key!=='partsdesk-v1-migration-state'),before);assert.equal(JSON.parse(values.get('partsdesk-v1-migration-state')).status,'blocked');
});
test('invalid legacy payloads and malformed JSON cannot seed the new namespace',()=>{
 for(const invalid of ['not-json',JSON.stringify({activeId:'missing',items:[]})]){
  const {storage,values}=migrationStorage();values.set('legacy-parts-v1-jobs',invalid);const before=[...values];
  assert.equal(migrateLegacyWorkspace(storage).status,'blocked');assert.deepEqual([...values].filter(([key])=>key!=='partsdesk-v1-migration-state'),before);assert.equal(JSON.parse(values.get('partsdesk-v1-migration-state')).status,'blocked');
 }
});
test('any existing destination data takes precedence over automatic legacy discovery',()=>{
 const {storage,values}=migrationStorage();values.set('partsdesk-v1-theme','"light"');const before=[...values];
 assert.equal(migrateLegacyWorkspace(storage).status,'current');assert.deepEqual([...values],before);
});
test('unrelated storage is not treated as a compatible parts workspace',()=>{
 const values=new Map([['another-app-jobs',JSON.stringify(backup().jobs)],['unrelated-parts-v2-jobs',JSON.stringify(backup().jobs)]]);
 const storage={get length(){return values.size;},key:i=>[...values.keys()][i],getItem:key=>values.get(key)??null,setItem:(key,value)=>values.set(key,value),removeItem:key=>values.delete(key)};
 const before=[...values];assert.equal(migrateLegacyWorkspace(storage).status,'none');assert.deepEqual([...values],before);
});
test('automatic namespace migration rolls back partial writes on quota failure',()=>{
 const {storage,values}=migrationStorage();const before=[...values];const set=storage.setItem;let writes=0;
 storage.setItem=(key,value)=>{if(++writes===3)throw Error('Quota');set(key,value);};
 assert.equal(migrateLegacyWorkspace(storage).status,'blocked');
 assert.deepEqual([...values].filter(([key])=>key!=='partsdesk-v1-migration-state'),before);
 assert.equal(JSON.parse(values.get('partsdesk-v1-migration-state')).status,'pending');
});

test('interrupted namespace copy is disclosed before current-data guard and preserves destination edits',()=>{
 const {storage,values}=migrationStorage();
 values.set('partsdesk-v1-migration-state',JSON.stringify({status:'pending',version:1}));
 values.set('partsdesk-v1-favorites','["copied-before-process-exit"]');
 values.set('partsdesk-v1-notes','{"current-edit":"Keep my new note"}');
 const before=[...values],result=migrateLegacyWorkspace(storage);
 assert.equal(result.status,'blocked');assert.match(result.message,/did not finish/);assert.deepEqual([...values],before);
 assert.equal(values.has('partsdesk-v1-jobs'),false);
});
test('successful manual backup restore resolves a pending namespace migration marker',()=>{
 const {storage,values}=migrationStorage();values.set('partsdesk-v1-migration-state','{"status":"pending","version":1}');
 applyWorkspaceRestore(storage,'partsdesk-v1-',prepareWorkspaceRestore(backup()));
 assert.equal(values.has('partsdesk-v1-migration-state'),false);
 assert.equal(JSON.parse(values.get('partsdesk-v1-jobs')).activeId,job.id);
 assert.equal(migrateLegacyWorkspace(storage).status,'current');
});
test('failed manual restore rolls back edited destination values and retains the pending marker',()=>{
 const {storage,values}=migrationStorage();values.set('partsdesk-v1-migration-state','{"status":"pending","version":1}');values.set('partsdesk-v1-notes','{"current":"Keep edit"}');
 const before=new Map(values),set=storage.setItem;let writes=0;
 storage.setItem=(key,value)=>{if(++writes===3)throw Error('Quota');set(key,value);};
 assert.throws(()=>applyWorkspaceRestore(storage,'partsdesk-v1-',prepareWorkspaceRestore(backup())),/rolled back/);
 assert.deepEqual(values,before);assert.equal(migrateLegacyWorkspace(storage).status,'blocked');
});

test('a process exit during ordinary manual restore leaves a recoverable marker before any data write',()=>{
 const values=new Map([['partsdesk-v1-notes','{"existing":"Keep until confirmed restore"}']]);let interrupted;
 const storage={get length(){return values.size;},key:i=>[...values.keys()][i],getItem:key=>values.get(key)??null,setItem(key,value){values.set(key,value);if(key==='partsdesk-v1-favorites'&&!interrupted)interrupted=new Map(values);},removeItem:key=>values.delete(key)};
 applyWorkspaceRestore(storage,'partsdesk-v1-',prepareWorkspaceRestore(backup()));
 assert.equal(values.has('partsdesk-v1-migration-state'),false);
 assert.equal(JSON.parse(interrupted.get('partsdesk-v1-migration-state')).status,'pending');
 const recovered={get length(){return interrupted.size;},key:i=>[...interrupted.keys()][i],getItem:key=>interrupted.get(key)??null,setItem:(key,value)=>interrupted.set(key,value),removeItem:key=>interrupted.delete(key)};
 const before=new Map(interrupted),result=migrateLegacyWorkspace(recovered);
 assert.equal(result.status,'blocked');assert.match(result.message,/incomplete/);assert.deepEqual(interrupted,before);
 applyWorkspaceRestore(recovered,'partsdesk-v1-',prepareWorkspaceRestore(backup()));
 assert.equal(interrupted.has('partsdesk-v1-migration-state'),false);assert.equal(JSON.parse(interrupted.get('partsdesk-v1-jobs')).activeId,job.id);
});
test('manual restore cannot mutate data if its initial recovery marker cannot be saved',()=>{
 const values=new Map([['partsdesk-v1-notes','{"existing":"Keep note"}']]),before=new Map(values);
 const storage={getItem:key=>values.get(key)??null,setItem(key,value){if(key==='partsdesk-v1-migration-state')throw Error('Quota');values.set(key,value);},removeItem:key=>values.delete(key)};
 assert.throws(()=>applyWorkspaceRestore(storage,'partsdesk-v1-',prepareWorkspaceRestore(backup())),/rolled back/);assert.deepEqual(values,before);
});
test('a failed rollback keeps a recovery marker for an ordinary manual restore',()=>{
 const values=new Map([['partsdesk-v1-favorites','["existing"]']]);let writes=0;
 const storage={get length(){return values.size;},key:i=>[...values.keys()][i],getItem:key=>values.get(key)??null,setItem(key,value){writes++;if(writes===3||writes===4)throw Error('Storage unavailable');values.set(key,value);},removeItem:key=>values.delete(key)};
 assert.throws(()=>applyWorkspaceRestore(storage,'partsdesk-v1-',prepareWorkspaceRestore(backup())),/could not fully roll back/);
 assert.equal(JSON.parse(values.get('partsdesk-v1-migration-state')).status,'pending');assert.equal(migrateLegacyWorkspace(storage).status,'blocked');
});
