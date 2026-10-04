import {createCounter} from './counter-ui.js';
import {freshJob,changeVehicle,migrateReferences} from './jobs.js';
import {prepareWorkspaceRestore,applyWorkspaceRestore,migrateLegacyWorkspace} from './workspace.js';
import {createIndex,search,decode,normalize,importCSV,mergeImports,toCSV} from './core.js';
import {openVinDecoder} from './vin-ui.js';
const $=s=>document.querySelector(s), esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const paths={search:'m21 21-5-5 M19 10a9 9 0 1 1-18 0 9 9 0 0 1 18 0',bookmark:'M6 3h12v18l-6-4-6 4z',clock:'M12 8v5l3 2 M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0',library:'M4 3h4v18H4z M10 3h4v18h-4z M17 3l4 1-1 17-4-1z',sliders:'M4 3v5m0 5v8 M12 3v11m0 5v2 M20 3v2m0 5v11 M1 8h6v5H1z M9 14h6v5H9z M17 5h6v5h-6z',download:'M12 3v12m-5-5 5 5 5-5 M4 16v5h16v-5',sun:'M12 1v2m0 18v2M1 12h2m18 0h2M4 4l2 2m12 12 2 2M4 20l2-2M18 6l2-2 M17 12a5 5 0 1 1-10 0 5 5 0 0 1 10 0',layers:'m12 2 10 5-10 5L2 7z M2 12l10 5 10-5 M2 17l10 5 10-5',grid:'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z',list:'M9 5h12M9 12h12M9 19h12M3 5h1M3 12h1M3 19h1',check:'m8 12 3 3 6-7 M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0',info:'M12 11v6m0-11v1 M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0',engine:'M5 7h12l3 3h2v8h-3l-2 2H7l-2-3H2V9h3z M7 3h9m-5 0v4M0 10v6',wheel:'M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0 M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0 M12 2v7m0 6v7M2 12h7m6 0h7',bolt:'m13 2-9 12h7l-1 8 10-13h-8z',body:'m3 9 3-6h12l3 6v10H3z M3 9h18 M7 13h2m6 0h2M5 19v3m14-3v3',climate:'M12 2v20M3 7l18 10M3 17 21 7 M8 4l4 4 4-4M8 20l4-4 4 4',fuel:'M3 3h10v18H3z M5 5h6v5H5z M13 12h3v6c0 4 5 4 5 0V8l-4-4 M18 5v4h3',tool:'m14 7 3-4a6 6 0 0 0-8 8l-7 7a3 3 0 0 0 4 4l7-7a6 6 0 0 0 8-8l-4 3z'};
const icon=name=>`<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="${paths[name]||paths.layers}"/></svg>`;
paths.brake='M19 7a9 9 0 1 0 0 10 M16 7h5v10h-5z M13 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0';
paths.driveline='M2 9h5v6H2z M17 9h5v6h-5z M7 12h3m4 0h3 M14 12a2 2 0 1 1-4 0 2 2 0 0 1 4 0 M4 6v3m0 6v3m16-12v3m0 6v3';
paths.exhaust='M2 7h5v5h3 M10 9h8v7h-8z M18 12h4v5 M12 9v7m4-7v7';
paths.moon='M20 15A9 9 0 0 1 9 4a9 9 0 1 0 11 11z';
const systemIcon=c=>({'Engine & cooling':'engine','Fuel & emissions':'fuel','Electrical':'bolt','Climate control':'climate','Body & interior':'body','Wheels & tires':'wheel','Brakes':'brake','Steering & suspension':'tool','Transmission & driveline':'driveline','Exhaust & chassis':'exhaust'}[c]||'layers');
document.querySelectorAll('[data-icon]').forEach(e=>e.innerHTML=icon(e.dataset.icon));
let toastTimer;
const prefix='partsdesk-v1-';
const namespaceMigration=migrateLegacyWorkspace(localStorage,prefix);
let workspaceStorageBlocked=namespaceMigration.status==='blocked'&&!namespaceMigration.persisted;
if(namespaceMigration.status==='blocked'){$('#workspaceNotice').hidden=false;$('#workspaceNotice').textContent=namespaceMigration.message;}
function read(key,fallback){try{return JSON.parse(localStorage.getItem(prefix+key))??fallback;}catch{return fallback;}}
function saveRaw(key,value){if(workspaceStorageBlocked){toast('Restore your workspace backup before saving new changes.');return false;}try{localStorage.setItem(prefix+key,JSON.stringify(value));return true;}catch{toast('Device storage is full. Your latest change could not be saved.');return false;}}
let favorites=new Set(read('favorites',[])), recent=read('recent',[]),notes=read('notes',{}), imported=read('imports',[]), worksheet=read('worksheet',{job:'',vin:'',rows:[]});
let jobBook=read('jobs',null);
if(!jobBook?.items?.length){const j={...freshJob(),...worksheet};jobBook={activeId:j.id,items:[j]};saveRaw('jobs',jobBook);}
worksheet=jobBook.items.find(j=>j.id===jobBook.activeId)||jobBook.items[0];
function writeJobs(next){if(next.items.length>500){toast('Back up and archive old jobs before adding more than 500.');return false;}if(!saveRaw('jobs',next))return false;jobBook=next;worksheet=next.items.find(j=>j.id===next.activeId)||next.items[0];saveRaw('worksheet',worksheet);renderVehicle();return true;}
function writeJob(next){return writeJobs({...jobBook,items:jobBook.items.map(j=>j.id===next.id?next:j)});}
function save(key,value){return key==='worksheet'?writeJob(value):saveRaw(key,value);}
function renderVehicle(){const bar=document.querySelector('#vehicleBar');if(!bar)return;bar.innerHTML=`<div><span class="eyebrow small">ACTIVE JOB</span><strong>${esc(worksheet.job||'New counter job')}</strong><small>${esc(worksheet.vehicle||worksheet.vin||'Set a VIN to start an EPC lookup')}</small></div><div class="vehicle-actions"><button class="secondary" id="activeJob">${worksheet.rows.length} part${worksheet.rows.length===1?'':'s'} · Open job</button><button class="secondary" id="switchJob">Switch job</button></div>`;document.querySelector('#activeJob').onclick=()=>counter.openJob();document.querySelector('#switchJob').onclick=()=>counter.openJobs();}
let catalog,index,view='search',limit=24,results=[],currentPart=null,detailLimit=30,detailQuery='',searchTimer;
const fmt=n=>Number(n).toLocaleString();const android=()=>typeof window.PartsNative!=='undefined';
const counter=createCounter({esc,panel,toast,copy,download,read,save,getJob:()=>worksheet,getJobs:()=>jobBook,writeJob,writeJobs,getIndex:()=>index,showVin});
const defaultIds=['EVAP purge valve','Engine oil filter','Brake pads','Blend door actuator','Fuel pump','Oxygen sensor','Cabin air filter','Alternator','Water pump'];
applyTheme(read('theme','light'));
function applyTheme(theme){const dark=theme==='dark';document.documentElement.dataset.theme=dark?'dark':'light';$('#theme').innerHTML=icon(dark?'sun':'moon');$('#theme').setAttribute('aria-label',dark?'Switch to light theme':'Switch to dark theme');$('#theme').setAttribute('aria-pressed',String(dark));}
function toast(message){$('#toast').textContent=message;$('#toast').hidden=false;clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('#toast').hidden=true,2600);}
function remember(q){q=q.trim();if(!q)return;recent=[q,...recent.filter(x=>x!==q)].slice(0,18);save('recent',recent);}
async function copy(text){try{if(android())window.PartsNative.copyText(text);else await navigator.clipboard.writeText(text);toast('Copied to clipboard');}catch{toast('Copy unavailable. Select the displayed number to copy.');}}
function download(name,text,type='text/csv'){if(android()){window.PartsNative.saveText(name,text,type);return;}const url=URL.createObjectURL(new Blob([text],{type}));const a=document.createElement('a');a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),5000);}
function toggleFavorite(id){const next=new Set(favorites);next.has(id)?next.delete(id):next.add(id);if(!save('favorites',[...next]))return;favorites=next;updateCounts();render();if(currentPart)renderDetail();}
function updateCounts(){$('#savedCount').textContent=favorites.size;}
function setView(next){view=next;limit=24;$('#viewName').textContent={search:'Part search',saved:'Saved parts',recent:'Recent searches'}[view];document.querySelectorAll('[data-view]').forEach(b=>{b.classList.toggle('active',b.dataset.view===view);b.setAttribute('aria-pressed',String(b.dataset.view===view));});render();}
function render(){
 if(!index)return;const q=$('#query').value;$('#clear').hidden=!q;const category=$('#category').value,quality=$('#quality').value;
 results=search(q,index,{category,quality,saved:view==='saved'?favorites:null,sort:$('#sort').value});
 if(!q&&view==='search'&&category==='All systems'&&$('#sort').value==='relevance')results.sort((a,b)=>{const ai=defaultIds.map(normalize).indexOf(normalize(a.part.name)),bi=defaultIds.map(normalize).indexOf(normalize(b.part.name));return (ai<0?999:ai)-(bi<0?999:bi)||b.score-a.score||a.part.name.localeCompare(b.part.name);});
 $('#resultsTitle').innerHTML=esc(view==='saved'?'Saved parts':q?'Search results':'Explore the catalog')+` <span class="pill">${fmt(results.length)}</span>`;
 $('#resultSubtitle').textContent=view==='saved'?'Your frequently used parts, kept on this device.':q?`Part families matching “${q}”. Multiple bases stay together.`:'Common counter parts first. Tap any base number to copy it.';
 const d=decode(q,index);$('#decoder').hidden=!d;if(d)$('#decoder').innerHTML=`<h3>${d.inferred?'Possible':'Ford'} part-number breakdown</h3><div class="decoded"><div><small>Prefix</small>${esc(d.prefix)}</div>—<div class="focus"><small>Base</small>${esc(d.base)}</div>—<div><small>Suffix</small>${esc(d.suffix)}</div></div><p>${d.known?'Base found in this reference.':'This base is not in the reference; the format alone does not verify it exists.'} ${d.inferred?'Unseparated number split using known bases. ':''}Confirm the complete service part by VIN.</p>`;
 $('#recentPanel').hidden=view!=='recent';if(view==='recent')$('#recentPanel').innerHTML=`<h3>Recent searches</h3><p class="part-sub">${recent.length?'Pick up where you left off.':'Your searches will appear here after you press Search or open a result.'}</p><div class="recent-items">${recent.map(q=>`<button class="secondary" data-query="${esc(q)}">${esc(q)}</button>`).join('')}</div>`;
 $('#activeFilters').innerHTML=category!=='All systems'||quality!=='all'?`<div class="filter-summary">${esc(category)} · ${quality==='detailed'?'Detailed names only':'All reference detail levels'} <button id="resetInline">Reset</button></div>`:'';
 $('#results').innerHTML=results.slice(0,limit).map(({part:p,matched,reason})=>{
 const codes=[...matched,...p.codes.filter(x=>!matched.includes(x))],sources=new Set(p.bases.flatMap(b=>b.evidence.map(e=>e.source)));
 const sourceLabel=p.broad?'Broad source description':sources.has('epc')?'Includes EPC observations':sources.has('packaging')?'Published Ford reference':'Reference with sources';
 return `<article class="part-card" data-system="${systemIcon(p.category)}"><div class="card-top"><span class="system-icon"><span class="system-symbol">${icon(systemIcon(p.category))}</span>${esc(p.category)}</span><button class="save" data-save="${p.id}" aria-label="${favorites.has(p.id)?'Unsave':'Save'} ${esc(p.name)}" aria-pressed="${favorites.has(p.id)}">${icon('bookmark')}</button></div><h3><button data-detail="${p.id}">${esc(p.name)}</button></h3><p class="part-sub">${p.broad?'Broad catalog group · open for individual source records':q?esc(reason):p.bases.length>1?'Multiple base numbers across applications':'Part family reference'}</p><div class="base-label">${p.bases.length>1?'BASE NUMBERS':'BASE NUMBER'} <span>· TAP TO COPY</span></div><div class="base-chips">${codes.slice(0,5).map(b=>`<button class="base-chip ${matched.includes(b)?'exact':''}" data-copy="${b}" title="Copy base ${b}" aria-label="Copy base ${b}">${b}</button>`).join('')}${codes.length>5?`<button class="base-extra" data-detail="${p.id}">+${codes.length-5} more</button>`:''}</div><div class="card-bottom"><span class="source-dot ${p.broad?'broad':''}">${sourceLabel}</span><button class="detail-link" data-detail="${p.id}">View details <span aria-hidden="true">↗</span></button></div></article>`;
 }).join('');
 $('#empty').hidden=!!results.length;$('#more').hidden=results.length<=limit;
 if(view==='saved'&&!results.length){$('#empty h3').textContent=favorites.size?'No saved parts match these filters':'Your parts shelf is ready';$('#empty p').textContent=favorites.size?'Clear the search or reset filters to see your saved parts.':'Tap the bookmark on any part to keep it here.';}else{$('#empty h3').textContent='No matching part families';$('#empty p').textContent='Try another name, fewer words, or a base number. A missing result does not mean the part does not exist.';}
}
function showDetails(id){currentPart=index.parts.find(p=>p.id===id);if(!currentPart)return;remember($('#query').value);detailLimit=30;detailQuery='';renderDetail();$('#detail').showModal();}
function sourceEvidence(e){
 const src=catalog.sources.find(s=>s.id===e.source),url=e.url||src?.url;
 const descriptions=e.descriptions?e.descriptions.map(d=>`${d.name} (${d.count})`).join(' · '):e.description;
 return `<div class="source-evidence"><h4>${esc(src?.name||'Imported reference — unverified')}</h4><p>${esc(descriptions)}</p>${e.catalog?`<p><strong>Catalog context:</strong> ${esc(e.catalog)}</p>`:''}${e.locations?`<details><summary class="part-sub">${e.locations.length} observed catalog location${e.locations.length===1?'':'s'}</summary><p>${e.locations.map(esc).join('<br>')}</p></details>`:''}${e.examples?.length?`<p>Example service number${e.examples.length>1?'s':''}:<br><code>${e.examples.map(esc).join(' · ')}</code></p>`:''}${url?`<a href="${esc(url)}${e.page?'#page='+e.page:''}" target="_blank" rel="noopener">View source${e.page?' · page '+e.page:''} ↗</a>`:''}</div>`;
}
function renderDetail(){
 const p=currentPart;$('#detailBody').innerHTML=`<span class="dialog-category">${esc(p.category)}</span><h2>${esc(p.name)}</h2><p>${fmt(p.bases.length)} base number${p.bases.length===1?'':'s'} in this family${p.broad?' · broad catalog descriptions':''}.</p>${p.note?`<p class="detail-note">${esc(p.note)}</p>`:''}${p.broad?'<p class="detail-note">The source uses a general description for these records. This group may contain different components. Use the exact base and original catalog before selecting a service part.</p>':''}<div class="detail-actions"><button class="primary" data-lookup="${p.id}">Find service part by VIN ↗</button><button class="secondary" data-copy-all="${p.id}">Copy all base numbers</button><button class="secondary" data-add-job="${p.id}">Add to job</button><button class="secondary" data-save="${p.id}">${favorites.has(p.id)?'Saved ✓':'Save part'}</button></div>${p.bases.length>12?'<div class="detail-filter"><input id="baseFilter" aria-label="Filter base numbers in this part" placeholder="Filter this family’s base numbers…" value="'+esc(detailQuery)+'"></div>':''}<div id="baseDetails"></div><label class="note-label" for="partNote">Your counter note</label><textarea id="partNote" maxlength="3000" placeholder="Bin location, reminders, or lookup notes…">${esc(notes[p.id]||'')}</textarea><span class="note-state" id="noteState">Stored on this device.</span><p>Base numbers alone do not confirm fitment or interchangeability. <a href="https://parts.ford.com/" target="_blank" rel="noopener">Check the full service part by VIN ↗</a></p>`;
 renderBaseDetails();
 $('#partNote').oninput=e=>{const next={...notes,[p.id]:e.target.value};if(save('notes',next)){notes=next;$('#noteState').textContent='Saved on this device.';}};
 if($('#baseFilter'))$('#baseFilter').oninput=e=>{detailQuery=e.target.value.toUpperCase().replace(/[^A-Z0-9]/g,'');detailLimit=30;renderBaseDetails();};
}
function renderBaseDetails(){const bases=currentPart.bases.filter(b=>b.number.includes(detailQuery));$('#baseDetails').innerHTML=bases.slice(0,detailLimit).map(b=>`<section class="base-detail"><div class="base-detail-head"><div><strong>${b.number}</strong>${b.label?`<small class="base-position">${esc(b.label)}</small>`:''}</div><button class="secondary" data-copy="${b.number}">Copy</button></div>${b.evidence.map(sourceEvidence).join('')}</section>`).join('')+(bases.length>detailLimit?`<button class="secondary" id="moreBases">Show more bases (${bases.length-detailLimit} remaining)</button>`:'')+(!bases.length?'<p>No base numbers match this filter.</p>':'');}
function panel(html){$('#infoBody').innerHTML=html;if(!$('#info').open)$('#info').showModal();}
function showCoverage(){panel(`<span class="dialog-category">CATALOG & SOURCES</span><h2>The reference behind<br>every lookup.</h2><p>${esc(catalog.coverage)}</p><table class="stats-table"><tr><td>Exact distinct base strings</td><td>${fmt(catalog.stats.baseNumbers)}</td></tr><tr><td>Part families / broad groups</td><td>${fmt(catalog.stats.partFamilies)}</td></tr><tr><td>Families with detailed names</td><td>${fmt(catalog.stats.detailedFamilies)}</td></tr><tr><td>Groups with broad names</td><td>${fmt(catalog.stats.broadFamilies)}</td></tr><tr><td>Without two-digit body-style prefixes*</td><td>${fmt(catalog.stats.bodyPrefixCollapsedCount)}</td></tr><tr><td>Source service-part records</td><td>${fmt(catalog.stats.sourceServiceRows)}</td></tr></table><p>*Diagnostic count only. Exact published base strings, including leading zeros and body prefixes, remain intact in searches. Service-number prefixes, suffixes and repeated rows do not inflate the base count.</p><h3>Source library</h3>${catalog.sources.map(s=>`<div class="source-card"><strong>${esc(s.name)}</strong><p>${esc(s.scope)}</p><p>Snapshot: ${esc(s.checkedAt)}</p><a href="${esc(s.url)}" target="_blank" rel="noopener">Open source ↗</a></div>`).join('')}<p>Ford Parts Desk is an independent reference tool. It is not an official Ford electronic parts catalog. Ford and Motorcraft names belong to their respective owners.</p>`);}
function showTools(){panel(`<span class="dialog-category">COUNTER TOOLS</span><h2>A little less repetition.</h2><p>Jobs, selected service numbers, notes and searches stay on this device. EPC lookups send the VIN and base to Snap-on when you choose to open the catalog.</p><div class="tools-actions"><button id="openWorksheet" class="primary">Current job (${worksheet.rows.length})</button><button id="toolsVin" class="secondary">VIN Decoder</button><button id="backup" class="secondary">Back up my workspace</button></div><h3>Catalog exports</h3><p>Export the grouped catalog as one CSV row per base number, ready to filter in a spreadsheet.</p><div class="tools-actions"><button id="exportCatalog" class="secondary">Export complete catalog</button><button id="exportSaved" class="secondary">Export saved parts</button></div><h3>Add reference records</h3><p>CSV columns: <code>base,name,category,aliases,source</code>. Base and name are required; separate aliases with semicolons. An existing base adds evidence to its family. New bases with the same part name join that family.</p><input id="importFile" class="file-input" type="file" accept=".csv,text/csv" aria-label="Import reference CSV"><p id="importStatus" role="status">${imported.length} imported records on this device.</p><button id="template" class="secondary">Download CSV template</button><h3>Move from the previous app</h3><p>Keep the previous app installed. In that app, open Tools → Back up my workspace and save the JSON file. Then choose that file below in this app. Jobs, saved parts, notes, recent searches, VIN cache, theme and layout transfer. Snap-on sign-in and cookies do not transfer.</p><h3>Restore a workspace</h3><input id="restoreFile" class="file-input" type="file" accept=".json,application/json" aria-label="Restore workspace backup"><p id="restoreStatus" role="status"></p><h3>Android app</h3><p><a href="install.html">Download the APK & installation guide ↗</a></p><button id="toolsCoverage" class="secondary">Catalog coverage & sources</button>`);
 $('#toolsVin').onclick=showVin;$('#openWorksheet').onclick=showWorksheet;$('#toolsCoverage').onclick=showCoverage;
 $('#exportCatalog').onclick=()=>download('Ford-Parts-Desk-base-numbers.csv',toCSV(index.parts));
 $('#exportSaved').onclick=()=>download('Ford-Parts-Desk-saved-parts.csv',toCSV(index.parts.filter(p=>favorites.has(p.id))));
 $('#backup').onclick=()=>download('Ford-Parts-Desk-workspace-backup.json',JSON.stringify({schema:2,favorites:[...favorites],recent,notes,imports:imported,worksheet,jobs:jobBook,vinCache:read('vin-cache',[]),theme:read('theme','light'),layout:read('layout',false)},null,2),'application/json');
 $('#template').onclick=()=>download('Ford-Parts-Desk-import-template.csv','base,name,category,aliases,source\r\n9C915,EVAP purge valve,Fuel & emissions,canister purge solenoid,https://parts.ford.com/\r\n');
 $('#importFile').onchange=async e=>{const file=e.target.files[0];if(!file)return;try{if(file.size>10e6)throw Error('Choose a CSV under 10 MB.');const rows=importCSV(await file.text());const map=new Map([...imported,...rows].map(r=>[r.base+'|'+r.name,r]));const next=[...map.values()];if(save('imports',next)){imported=next;reindex();$('#importStatus').textContent=`Imported ${rows.length} records. ${imported.length} stored on this device.`;}}catch(err){$('#importStatus').textContent=err.message;}e.target.value='';};
 $('#restoreFile').onchange=async e=>{
  const file=e.target.files[0];if(!file)return;
  try{
   if(file.size>10e6)throw Error('Choose a backup under 10 MB.');
   const payload=prepareWorkspaceRestore(JSON.parse(await file.text()),catalog.parts);
   if(!confirm('Replace this app’s jobs, saved parts, notes, VIN history and settings with this backup? The other installed app will not change.'))return;
   applyWorkspaceRestore(localStorage,prefix,payload);
   workspaceStorageBlocked=false;$('#workspaceNotice').hidden=true;
   favorites=new Set(payload.favorites);recent=payload.recent;notes=payload.notes;imported=payload.imports;jobBook=payload.jobs;worksheet=payload.worksheet;
   applyTheme(payload.theme);layout(payload.layout);reindex();renderVehicle();
   $('#restoreStatus').textContent='Workspace restored. Check your jobs and saved parts; sign in to Snap-on separately in this app.';
  }catch(err){$('#restoreStatus').textContent=err.message;}
  finally{e.target.value='';}
 };

}

function addToJob(id){counter.addPart(id);}
function showWorksheet(){counter.openJob();}
function showVin(){openVinDecoder({panel,esc,read,save,copy,useVehicle:r=>{const next={...changeVehicle(worksheet,r.vin,r.title),vehicleDetails:r.fields};if(writeJob(next)){toast('Vehicle added to current job');showWorksheet();}}});}
function resetFilters(){$('#category').value='All systems';$('#quality').value='all';$('#query').value='';limit=24;render();}
function catalogDate(options){const value=String(catalog.updatedAt||'');const date=/^\d{4}-\d{2}-\d{2}$/.test(value)?new Date(value+'T00:00:00Z'):null;return date&&!Number.isNaN(date.getTime())?date.toLocaleDateString('en-US',{timeZone:'UTC',...options}):'date unavailable';}
function reindex(){index=createIndex(mergeImports(catalog.parts,imported));$('#baseCount').textContent=fmt(index.byBase.size);$('#familyCount').textContent=fmt(index.parts.length);$('#footerCount').textContent=`${fmt(index.byBase.size)} base numbers · Reference updated ${catalogDate({month:'long',day:'numeric',year:'numeric'})}`;$('#catalogSnapshot').textContent=`REFERENCE · ${catalogDate({month:'short',day:'numeric',year:'numeric'})}`;$('#catalogSnapshot').setAttribute('datetime',catalog.updatedAt||'');const cats=[...new Set(index.parts.map(p=>p.category))].sort();const old=$('#category').value;$('#category').innerHTML='<option>All systems</option>'+cats.map(c=>`<option>${esc(c)}</option>`).join('');if(cats.includes(old))$('#category').value=old;updateCounts();render();}
document.addEventListener('click',e=>{
 const b=e.target.closest('button');if(!b)return;
 if(b.dataset.view)setView(b.dataset.view);
 if(b.dataset.query){$('#query').value=b.dataset.query;$('#category').value='All systems';remember(b.dataset.query);setView('search');}
 if(b.dataset.detail)showDetails(b.dataset.detail);
 if(b.dataset.save)toggleFavorite(b.dataset.save);
 if(b.dataset.copy)copy(b.dataset.copy);
 if(b.dataset.copyAll){const p=index.parts.find(p=>p.id===b.dataset.copyAll);copy(p.codes.join(', '));}
 if(b.dataset.addJob)addToJob(b.dataset.addJob);
 if(b.dataset.lookup){document.querySelector('#detail').close();counter.openLookup(b.dataset.lookup);}
 if(b.hasAttribute('data-remove-job')){const next=structuredClone(worksheet);next.rows.splice(+b.dataset.removeJob,1);if(save('worksheet',next)){worksheet=next;showWorksheet();}}
 if(b.id==='moreBases'){detailLimit+=30;renderBaseDetails();}
 if(b.id==='resetInline')resetFilters();
 if(b.classList.contains('close'))b.closest('dialog').close();
});
$('#searchForm').onsubmit=e=>{e.preventDefault();remember($('#query').value);render();$('#query').blur();};
$('#query').oninput=()=>{clearTimeout(searchTimer);searchTimer=setTimeout(()=>{limit=24;render();},100);};
$('#clear').onclick=()=>{$('#query').value='';limit=24;render();$('#query').focus();};
for(const id of ['category','quality','sort'])$('#'+id).onchange=()=>{limit=24;render();};
$('#more').onclick=()=>{limit+=24;render();};$('#reset').onclick=resetFilters;
$('#snaponButton').onclick=$('#mobileSnapon').onclick=()=>counter.openSnapOn();
$('#jobsButton').onclick=()=>counter.openJobs();$('#mobileJobs').onclick=()=>counter.openJobs();$('#vinDecoder').onclick=showVin;$('#coverage').onclick=$('#coverageInline').onclick=()=>catalog&&showCoverage();$('#tools').onclick=$('#mobileTools').onclick=()=>catalog&&showTools();
$('#theme').onclick=()=>{const t=document.documentElement.dataset.theme==='dark'?'light':'dark';applyTheme(t);save('theme',t);};
function layout(list){$('#results').classList.toggle('list',list);$('#gridView').classList.toggle('selected',!list);$('#listView').classList.toggle('selected',list);$('#gridView').setAttribute('aria-pressed',!list);$('#listView').setAttribute('aria-pressed',list);save('layout',list);}
$('#gridView').onclick=()=>layout(false);$('#listView').onclick=()=>layout(true);layout(read('layout',false));
document.addEventListener('keydown',e=>{if(e.key==='/'&&!/INPUT|TEXTAREA|SELECT/.test(document.activeElement.tagName)&&!document.querySelector('dialog[open]')){e.preventDefault();$('#query').focus();}});
$('#detail').addEventListener('close',()=>currentPart=null);
try{const response=await fetch('./catalog.json');if(!response.ok)throw Error('Catalog load failed');catalog=await response.json();const migrated=migrateReferences(catalog.parts,[...favorites],notes);favorites=new Set(migrated.favorites);notes=migrated.notes;save('favorites',migrated.favorites);save('notes',notes);reindex();renderVehicle();counter.receiveEpc();if(android()){$('#offlineState').textContent='Works offline';$('#offlineLabel').textContent='Offline ready';$('#metricOffline').textContent='Stored on this device';}}
catch(err){$('#resultSubtitle').textContent='The reference could not load. Reload to try again.';$('#results').innerHTML='<button class="secondary" id="retry">Reload catalog</button>';$('#retry').onclick=()=>location.reload();}
if('serviceWorker' in navigator&&!android()&&!['localhost','127.0.0.1'].includes(location.hostname))navigator.serviceWorker.register('./sw.js').then(()=>navigator.serviceWorker.ready).then(()=>{$('#offlineState').textContent='Available offline';$('#offlineLabel').textContent='Offline ready';$('#metricOffline').textContent='Saved on this device';}).catch(()=>{});

