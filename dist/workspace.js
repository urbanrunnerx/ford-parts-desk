import {freshJob,migrateReferences,validateJobs} from './jobs.js';

export function validateBackup(d){if(!d||typeof d!=='object'||Array.isArray(d))throw Error('This is not a valid workspace backup.');if(d.schema===2){if(!d.jobs?.items?.length)throw Error('Backup has no jobs.');validateJobs(d.jobs.items);if(!d.jobs.items.some(j=>j.id===d.jobs.activeId))throw Error('Invalid active job.');if(!Array.isArray(d.vinCache)||d.vinCache.length>15||d.vinCache.some(r=>typeof r.vin!=='string'||typeof r.title!=='string'||typeof r.decodedAt!=='string'||!Array.isArray(r.fields)||r.fields.some(f=>typeof f.label!=='string'||typeof f.value!=='string')))throw Error('Invalid saved VIN results.');}if(![1,2].includes(d.schema)||!Array.isArray(d.favorites)||!d.favorites.every(x=>typeof x==='string')||!Array.isArray(d.recent)||!d.recent.every(x=>typeof x==='string')||!d.notes||typeof d.notes!=='object'||Array.isArray(d.notes)||!Object.values(d.notes).every(x=>typeof x==='string')||!Array.isArray(d.imports)||!d.worksheet||typeof d.worksheet.job!=='string'||typeof d.worksheet.vin!=='string'||!Array.isArray(d.worksheet.rows))throw Error('This is not a valid workspace backup.');for(const r of d.imports)if(typeof r.base!=='string'||!/^\d[A-Z0-9]{3,7}$/.test(r.base)||typeof r.name!=='string'||!Array.isArray(r.aliases)||!r.aliases.every(x=>typeof x==='string')||(r.source&&!/^https?:\/\//i.test(r.source)))throw Error('Invalid reference in backup.');for(const r of d.worksheet.rows)if(typeof r.id!=='string'||typeof r.name!=='string'||!Array.isArray(r.bases)||!r.bases.every(x=>typeof x==='string')||!Number.isInteger(r.qty)||r.qty<1||r.qty>999||typeof r.note!=='string')throw Error('Invalid worksheet in backup.');}

// Deliberate allowlist: existing app data migrates, authentication/session data
// never does. The same schema works across separate Android application IDs.
export function prepareWorkspaceRestore(input,parts=[]){
 validateBackup(input);
 const d=structuredClone(input),legacy={...freshJob(),...d.worksheet};
 const jobs=d.schema===2?d.jobs:{activeId:legacy.id,items:[legacy]};
 validateJobs(jobs.items);
 const worksheet=jobs.items.find(j=>j.id===jobs.activeId);
 const migrated=migrateReferences(parts,d.favorites,d.notes);
 return {favorites:migrated.favorites,recent:d.recent,notes:migrated.notes,imports:d.imports,
  worksheet,jobs,'vin-cache':d.vinCache||[],theme:d.theme==='dark'?'dark':'light',layout:d.layout===true};
}

export function applyWorkspaceRestore(storage,prefix,payload){
 const before=new Map(),applied=[],marker=prefix+'migration-state';
 const markerBefore=storage.getItem(marker),pending=JSON.stringify({status:'pending',version:1});
 for(const key of Object.keys(payload))before.set(key,storage.getItem(prefix+key));
 try{
  // Every import, including a manual restore, must be recognizable after a
  // process exit. Commit is the final marker removal, never an early data key.
  if(markerBefore!==pending)storage.setItem(marker,pending);
  for(const [key,value]of Object.entries(payload)){storage.setItem(prefix+key,JSON.stringify(value));applied.push(key);}
  storage.removeItem(marker);
 }catch(error){
  // Reverse the successful operations: each previous state fit in storage.
  // Restoring in forward order can itself exceed quota after mixed-size writes.
  let failed=false;
  for(const key of applied.reverse())try{const value=before.get(key);value===null?storage.removeItem(prefix+key):storage.setItem(prefix+key,value);}catch(restoreError){failed=true;}
  try{
   if(failed){if(storage.getItem(marker)!==pending)storage.setItem(marker,pending);}
   else if(markerBefore!==null){if(storage.getItem(marker)!==markerBefore)storage.setItem(marker,markerBefore);}
   else if(storage.getItem(marker)!==null)storage.removeItem(marker);
  }catch(markerError){failed=true;}
  throw Error(failed?'Restore failed and could not fully roll back. Keep both apps and your backup; check the saved workspace before continuing.':'Not enough storage or storage is unavailable; restore was rolled back.');
 }
}

// Same-origin compatibility for a single older, versioned parts workspace.
// Detection is structural; neither a retired product name nor authentication
// keys are needed. Separate Android app sandboxes still use JSON backup/restore.
export function migrateLegacyWorkspace(storage,prefix='partsdesk-v1-'){
 const marker=prefix+'migration-state';
 const blocked=message=>{
  try{
   if(storage.getItem(marker)===null)storage.setItem(marker,JSON.stringify({status:'blocked',version:1,message}));
   return {status:'blocked',message,persisted:true};
  }catch(markerError){return {status:'blocked',message,persisted:false};}
 };
 const help='Keep your previous workspace and its backup. Use Tools → Restore a workspace to import the correct JSON backup.';
 try{
  const existingMarker=storage.getItem(marker);
  if(existingMarker!==null){
   let state;try{state=JSON.parse(existingMarker);}catch(markerError){}
   return blocked(state?.status==='blocked'&&typeof state.message==='string'?state.message:'A previous workspace import did not finish. Some current data may be incomplete. Restore the complete backup before relying on this workspace. '+help);
  }
  const keys=Array.from({length:storage.length},(_,i)=>storage.key(i)).filter(k=>typeof k==='string');
  if(keys.some(key=>key.startsWith(prefix)))return {status:'current'};
  const candidates=[...new Set(keys.map(key=>/^([a-z0-9][a-z0-9-]*-parts-v1-)(?:jobs|worksheet)$/.exec(key)?.[1]).filter(Boolean))];
  if(!candidates.length)return {status:'none'};
  if(candidates.length!==1)return blocked('More than one previous parts workspace was found, so nothing was imported automatically. '+help);
  const previous=candidates[0];
  const read=(key,fallback)=>{const value=storage.getItem(previous+key);return value===null?fallback:JSON.parse(value);};
  const jobs=read('jobs',null),worksheet=read('worksheet',jobs?.items?.find(j=>j.id===jobs.activeId));
  const backup={schema:jobs?2:1,jobs,worksheet,favorites:read('favorites',[]),recent:read('recent',[]),notes:read('notes',{}),imports:read('imports',[]),vinCache:read('vin-cache',[]),theme:read('theme','light'),layout:read('layout',false)};
  const payload=prepareWorkspaceRestore(backup);
  storage.setItem(marker,JSON.stringify({status:'pending',version:1}));
  applyWorkspaceRestore(storage,prefix,payload);
  return {status:'migrated'};
 }catch(error){
  return blocked('The previous workspace could not be imported automatically. Its original data was kept. '+help);
 }
}
