import {analyzeVin,analyzeVinEntry,savedVinMatches,decodeReport,vinURL} from './vin.js';
export function readPendingVinResolution(){
 try{
  const raw=globalThis.window?.PartsNative?.getVinResolution?.();if(!raw)return null;
  const result=JSON.parse(raw),full=analyzeVin(result.vin);
  return result.confirmed===true&&typeof result.requestId==='string'&&/^[A-Za-z0-9|_-]{1,180}$/.test(result.requestId)&&/^[A-HJ-NPR-Z0-9]{8}$/.test(result.suffix||'')&&full.valid&&full.vin.endsWith(result.suffix)?{...result,vin:full.vin,title:String(result.vehicle||'Confirmed Snap-on vehicle')}:null;
 }catch{return null;}
}
// A choice is explicit even when only one saved VIN shares the suffix. Recheck
// the current input and saved record at click time so stale choices cannot win.
export function createVinEntry({input,choices,esc,getRecords,onConfirmed=()=>{}}){
 let resolving=false,inputGeneration=0,lastInput=analyzeVinEntry(input.value).vin;
 const isActive=()=>input.isConnected!==false&&input.closest?.('dialog')?.open!==false;
 input.closest?.('dialog')?.addEventListener?.('close',()=>{inputGeneration++;},{once:true});
 const records=()=>{const pending=readPendingVinResolution();return [...(pending?[pending]:[]),...getRecords()];};
 const acknowledge=vin=>{const pending=readPendingVinResolution();if(pending?.vin===vin)globalThis.window?.PartsNative?.acknowledgeVinResolution?.(pending.requestId);};
 function render(){
  const entry=analyzeVinEntry(input.value);if(entry.vin!==lastInput){inputGeneration++;lastInput=entry.vin;}choices.innerHTML='';
  if(!input.value)return entry;
  if(!entry.valid){choices.innerHTML=`<p class="micro">${esc(entry.error)}</p>`;return entry;}
  if(entry.kind==='full'){if(entry.warning)choices.innerHTML=`<p class="micro">${esc(entry.warning)}</p>`;return entry;}
  const matches=savedVinMatches(entry.vin,records());
  choices.innerHTML=matches.length?`<p class="micro">${matches.length===1?'One saved vehicle matches':'More than one saved vehicle matches'} these last 8 characters. Check the full VIN and confirm the correct vehicle before continuing.</p>${matches.map(r=>`<button type="button" class="vin-history secondary" data-confirm-vin="${r.vin}" data-vin-suffix="${entry.vin}"><strong>${esc(r.title)}</strong><code>${r.vin}</code><span>Confirm this vehicle</span></button>`).join('')}`:'<p class="micro">No saved full VIN matches these last 8 characters. Find the vehicle in Snap-on on Android, or enter the complete 17-character VIN. A suffix alone cannot identify the vehicle or confirm parts fitment.</p>';
  if(typeof globalThis.window?.PartsNative?.resolveVin==='function')choices.innerHTML+=`<button type="button" class="secondary" data-resolve-vin="${entry.vin}" ${resolving?'disabled':''}>${resolving?'Resolving vehicle…':'Find full VIN in Snap-on ↗'}</button><p class="micro">Sends these last 8 characters to your signed-in Snap-on catalog. Review and confirm the full vehicle before continuing. Internet and a phone sign-in are required.</p>`;
  return entry;
 }
 choices.onclick=e=>{
  const resolveButton=e.target.closest('[data-resolve-vin]');
  if(resolveButton){
   const entry=analyzeVinEntry(input.value);
   if(!isActive()||resolving||entry.kind!=='suffix'||entry.vin!==resolveButton.dataset.resolveVin)return;
   if(globalThis.navigator?.onLine===false){choices.innerHTML+='<p class="micro">Offline: choose a saved full VIN or reconnect to search Snap-on.</p>';return;}
   const suffix=entry.vin,generation=inputGeneration,requestId='vin-'+Date.now().toString(36)+'-'+Math.random().toString(36).slice(2,10);
   const handler=event=>{
    const result=event.detail;if(result?.requestId!==requestId)return;
    window.removeEventListener('parts-vin-resolved',handler);resolving=false;
    if(!isActive())return;
    if(inputGeneration!==generation||analyzeVinEntry(input.value).vin!==suffix){render();return;}
    const full=analyzeVin(result.vin);
    if(result.suffix!==suffix||!full.valid||!full.vin.endsWith(suffix)||result.confirmed!==true){render();choices.innerHTML+=`<p class="micro">${esc(result.error||'Vehicle was not confirmed. Try again or enter the full VIN.')}</p>`;return;}
    const match={vin:full.vin,title:String(result.vehicle||'Snap-on vehicle')};
    input.value=full.vin;choices.innerHTML=`<p class="vin-ok">Vehicle confirmed in Snap-on: ${esc(match.title)} · ${full.vin}</p>`;onConfirmed(match);acknowledge(match.vin);
   };
   resolving=true;render();window.addEventListener('parts-vin-resolved',handler);
   try{window.PartsNative.resolveVin(suffix,requestId);}catch(error){window.removeEventListener('parts-vin-resolved',handler);resolving=false;render();choices.innerHTML+='<p class="micro">The vehicle lookup could not open. Try again or enter the full VIN.</p>';}
   return;
  }
  const button=e.target.closest('[data-confirm-vin]');if(!button||!isActive())return;
  const entry=analyzeVinEntry(input.value);
  if(entry.kind!=='suffix'||entry.vin!==button.dataset.vinSuffix){render();return;}
  const match=savedVinMatches(entry.vin,records()).find(r=>r.vin===button.dataset.confirmVin);
  if(!match){render();return;}
  input.value=match.vin;choices.innerHTML=`<p class="vin-ok">Vehicle confirmed: ${esc(match.title)} · ${match.vin}</p>`;onConfirmed(match);acknowledge(match.vin);
 };
 function requireFull(){
  const entry=analyzeVinEntry(input.value);
  if(entry.kind==='full')return analyzeVin(entry.vin);
  render();
  if(!entry.valid)throw Error(entry.error);
  throw Error(savedVinMatches(entry.vin,records()).length?'Confirm the matching saved vehicle above, search Snap-on, or enter the full 17-character VIN.':'No saved full VIN matches. Use Find full VIN in Snap-on on Android, or enter the complete 17-character VIN to continue.');
 }
 input.oninput=render;render();return {render,requireFull};
}
export function openVinDecoder({panel,esc,read,save,copy,useVehicle}){
 let history=read('vin-cache',[]),current=null,serial=0,pending=false;
 panel(`<span class="dialog-category">VIN DECODER</span><h2>Start with the vehicle.</h2><p>Identify the vehicle and engine before your parts lookup. Manufacturer-reported vehicle data; missing fields remain unknown.</p><form id="vinForm"><label class="note-label" for="vinInput">Full VIN or last 8 characters</label><div class="detail-filter"><input id="vinInput" maxlength="30" autocomplete="off" autocapitalize="characters" spellcheck="false" placeholder="Full VIN or last 8 characters"></div><div id="vinChoices" aria-live="polite"></div><label class="note-label" for="vinYear">Model year, if known (optional)</label><div class="detail-filter"><input id="vinYear" type="number" min="1981" max="2100" placeholder="Helps resolve repeating year codes"></div><p id="vinValidation" role="status"></p><button class="primary" id="vinSubmit" type="submit">Decode VIN ↗</button></form><p class="vin-privacy">Internet is required for a new lookup. Your VIN and optional model year are sent to the vehicle-data provider. It does not send your notes or worksheet. Successful and partial lookups are saved on this device for offline reference.</p><div id="vinResult" aria-live="polite"></div><div id="vinHistory"></div><p>VIN data does not establish exact parts fitment, paint/trim codes, axle ratio, production options or service supersessions. Confirm those in Ford’s catalog. <a href="https://vpic.nhtsa.dot.gov/decoder/" target="_blank" rel="noopener">Data source: NHTSA vPIC ↗</a></p>`);
 const $=s=>document.querySelector(s),form=$('#vinForm');
 const vinEntry=createVinEntry({input:$('#vinInput'),choices:$('#vinChoices'),esc,getRecords:()=>[...history,...(read('jobs',null)?.items||[])],onConfirmed:()=>{$('#vinResult').innerHTML='';current=null;$('#vinValidation').textContent='Full VIN confirmed. Choose Decode VIN to continue.';}});
 function renderHistory(){if(!$('#vinHistory'))return;$('#vinHistory').innerHTML=`<h3>Recent decoded vehicles</h3>${history.length?history.map((r,i)=>`<button class="vin-history secondary" data-vin-history="${i}"><strong>${esc(r.title)}</strong><code>${esc(r.vin)}</code>${r.warning?'<span>Partial / warnings</span>':''}</button>`).join('')+'<button class="secondary" id="clearVinHistory">Clear decoded history</button>':'<p>No saved VIN lookups yet.</p>'}`;$('#vinHistory').onclick=e=>{const b=e.target.closest('button');if(!b)return;if(b.dataset.vinHistory!==undefined){const r=history[+b.dataset.vinHistory];$('#vinInput').value=r.vin;$('#vinYear').value=r.modelYearHint||'';vinEntry.render();renderResult(r,true);}else if(b.id==='clearVinHistory'&&confirm('Clear saved VIN results from this device?')){if(save('vin-cache',[])){history=[];renderHistory();}}};}
 function renderResult(r,cached){if(!$('#vinResult'))return;current=r;$('#vinResult').innerHTML=`<div class="vin-vehicle"><span class="eyebrow">${cached?'SAVED LOOKUP · ':'VEHICLE RESULT · '}${esc(r.decodedAt.slice(0,10))}</span><h3>${esc(r.title)}</h3><code>${esc(r.vin)}</code>${r.warning?`<p class="detail-note">Partial decode / source warnings: ${esc(r.errorText||r.errorCode)}${r.suggestedVin?'<br>Source suggested VIN: '+esc(r.suggestedVin)+' (not applied automatically)':''}</p>`:'<p class="vin-ok">The provider returned no decoding errors.</p>'}<table class="stats-table">${r.fields.map(f=>`<tr><td>${esc(f.label)}</td><td>${esc(f.value)}</td></tr>`).join('')}</table><p>Unlisted details were not supplied by the source.</p><div class="tools-actions"><button class="primary" id="vinToWorksheet">Use in worksheet</button><button class="secondary" id="copyVinResult">Copy vehicle details</button></div></div>`;$('#vinToWorksheet').onclick=()=>{if(analyzeVin($('#vinInput').value).vin!==r.vin){$('#vinValidation').textContent='The VIN changed. Decode or confirm the current vehicle first.';return;}useVehicle(r);};$('#copyVinResult').onclick=()=>copy([r.title,r.vin,...r.fields.map(f=>f.label+': '+f.value),r.warning?'Source warnings: '+r.errorText:'','Source: NHTSA vPIC · '+r.decodedAt.slice(0,10),'Confirm exact part fitment by VIN.'].filter(Boolean).join('\n'));}
 $('#vinInput').oninput=()=>{const a=vinEntry.render();current=null;$('#vinResult').innerHTML='';$('#vinValidation').textContent=!$('#vinInput').value?'':!a.valid?a.error:a.kind==='suffix'?'Confirm a saved full VIN below before decoding.':a.warning||'VIN format and check digit pass.';};
 async function nativeLookup(vin,year,id){return new Promise((resolve,reject)=>{const timeout=setTimeout(()=>{window.removeEventListener('parts-vin-result',handler);reject(Error('NHTSA lookup timed out. Please try again.'));},18000);const handler=e=>{if(e.detail.id!==id)return;clearTimeout(timeout);window.removeEventListener('parts-vin-result',handler);e.detail.error?reject(Error(e.detail.error)):resolve(e.detail.data);};window.addEventListener('parts-vin-result',handler);window.PartsNative.decodeVin(vin,year,id);});}
 $('#vinForm').onsubmit=async e=>{
  e.preventDefault();if(pending)return;let a;try{a=vinEntry.requireFull();}catch(error){$('#vinValidation').textContent=error.message;return;}
  const year=$('#vinYear').value.trim();if(year&&(!/^\d{4}$/.test(year)||+year<1981||+year>2100)){$('#vinValidation').textContent='Use a model year between 1981 and 2100, or leave it blank.';return;}
  $('#vinInput').value=a.vin;$('#vinValidation').textContent=a.warning||'Looking up vehicle…';const cached=history.find(r=>r.vin===a.vin&&(r.modelYearHint||'')===year);
  if(!navigator.onLine&&cached){renderResult(cached,true);$('#vinValidation').textContent='Offline: showing the saved lookup.';return;}
  pending=true;$('#vinSubmit').disabled=true;$('#vinSubmit').textContent='Decoding…';const id=String(Date.now())+'-'+(++serial);
  try{
   let payload;if(typeof window.PartsNative?.decodeVin==='function')payload=await nativeLookup(a.vin,year,id);
   else{const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),15000);try{const response=await fetch(vinURL(a.vin,year),{signal:controller.signal});if(!response.ok)throw Error('NHTSA is unavailable (HTTP '+response.status+'). Try again.');payload=await response.json();}finally{clearTimeout(timer);}}
   const r={...decodeReport(payload,a.vin),modelYearHint:year};if(!a.checksumValid&&!r.warning){r.warning=true;r.errorText=a.warning;}
   if($('#vinForm')!==form)return;history=read('vin-cache',[]);const next=[r,...history.filter(x=>x.vin!==r.vin||x.modelYearHint!==year)].slice(0,15);if(save('vin-cache',next))history=next;
   if(analyzeVin($('#vinInput')?.value).vin!==a.vin)return;renderResult(r,false);renderHistory();if($('#vinValidation'))$('#vinValidation').textContent=r.warning?'Review the source warnings below.':'Vehicle decoded. Missing specifications are not inferred.';
  }catch(err){if($('#vinForm')!==form)return;if($('#vinValidation'))$('#vinValidation').textContent=(err.name==='AbortError'?'NHTSA lookup timed out.':err.message||'Lookup unavailable.')+(cached?' Showing the last saved result.':' Try again, or use the official decoder link below.');if(cached&&analyzeVin($('#vinInput')?.value).vin===a.vin)renderResult(cached,true);}
  finally{pending=false;if($('#vinForm')===form&&$('#vinSubmit')){$('#vinSubmit').disabled=false;$('#vinSubmit').textContent='Decode VIN ↗';}}
 };
 renderHistory();
}
