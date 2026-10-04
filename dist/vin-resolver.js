/* VIN-only rendered-page adapter. A suffix never becomes a parts lookup VIN. */
function partsDeskVinResolve(command, request) {
 'use strict';
 request=request||{};
 const {requestId,suffix,searchId}=request;
 const echoed={requestId,suffix,searchId};
 if(!/^[A-Za-z0-9|_-]{1,180}$/.test(requestId||'')||!/^[A-HJ-NPR-Z0-9]{8}$/.test(suffix||'')||!/^[A-Za-z0-9|_-]{1,180}$/.test(searchId||''))return {...echoed,stage:'invalid',ready:false,error:'A valid last-eight VIN and request are required.'};
 const clean=value=>String(value||'').replace(/[\u200B-\u200D\uFEFF]/g,'').replace(/\s+/g,' ').trim();
 const visible=el=>!!el&&el.getClientRects().length>0&&getComputedStyle(el).visibility!=='hidden'&&!el.closest('[aria-hidden="true"]');
 const all=selector=>[...document.querySelectorAll(selector)].filter(visible);
 const text=el=>clean(el?.innerText);
 const read=()=>{
  const vinNode=document.querySelector('#toolbar-vin-url-anchor'),vin=visible(vinNode)?text(vinNode).toUpperCase():'';
  const filter=document.querySelector('[title="Toggle VIN filters on and off"] input[type="checkbox"]');
  const hint=all('[title]').find(el=>/^ENG:/.test(el.getAttribute('title')||''));
  const modal=all('[role="dialog"],[role="alertdialog"]').find(el=>text(el));
  const busy=all('[aria-busy="true"],.ag-overlay-loading-center,.p-progress-spinner,.p-progressspinner').length>0||all('div,span,p').some(el=>el.childElementCount===0&&/^Loading(?:\.{3}|…)?$/i.test(text(el)));
  return {vin,identityNodes:[vinNode,filter,hint].filter(Boolean),vehicle:clean(hint?.getAttribute('title')).slice(0,2000),filtersOn:!!filter?.checked,modal:text(modal).slice(0,4000),busy,login:all('input[type="password"]').length>0};
 };
 let state=window.__partsDeskVinResolution;
 const same=()=>state&&state.requestId===requestId&&state.suffix===suffix&&state.searchId===searchId;
 const stop=()=>{if(state?.observer)state.observer.disconnect();};
 if(command==='cancel'){if(same()){stop();delete window.__partsDeskVinResolution;}return {...echoed,stage:'cancelled',ready:false};}
 if(command==='search'){
  stop();if(state){state.active=false;state.candidate=null;}const initial=read();
  if(initial.login)return {...echoed,stage:'login',ready:false,message:'Sign into Snap-on in the catalog, then retry the VIN search.'};
  if(initial.modal)return {...echoed,stage:'catalog',ready:false,message:'Close or review the catalog dialog before searching this VIN.'};
  if(initial.busy)return {...echoed,stage:'loading',ready:false,message:'Wait for the catalog to finish loading.'};
  const input=document.querySelector('#equipmentEntryInputId'),find=all('button').find(el=>text(el)==='Find VIN'&&!el.disabled);
  if(!visible(input)||!find)return {...echoed,stage:'unsupported',ready:false,message:'Open the catalog, select Ford if needed, then retry the VIN search.'};
  state={requestId,suffix,searchId,beforeVin:initial.vin,revision:0,sawBusy:false,sawChanged:false,candidate:null,fingerprint:'',active:true,noMatch:false,modalVin:null,needsPostModalChange:false};
  const observe=(mutations=[])=>{
   const view=read(),fingerprint=JSON.stringify([view.vin,view.vehicle,view.filtersOn,view.modal,view.busy,view.login]);
   const identityNodes=[...(state.identityNodes||[]),...view.identityNodes];
   const identityTouched=mutations.some(record=>identityNodes.some(node=>record.target===node||node.contains?.(record.target)||[...(record.removedNodes||[]),...(record.addedNodes||[])].some(changed=>changed===node||changed.contains?.(node))));
   const changedDialogs=mutations.flatMap(record=>{const dialogs=[],target=record.target?.nodeType===3?record.target.parentElement:record.target;const parentDialog=target?.closest?.('[role="dialog"],[role="alertdialog"]');if(parentDialog)dialogs.push(parentDialog);for(const node of [...(record.addedNodes||[]),...(record.removedNodes||[])]){const role=node?.getAttribute?.('role');if(role==='dialog'||role==='alertdialog')dialogs.push(node);dialogs.push(...(node?.querySelectorAll?.('[role="dialog"],[role="alertdialog"]')||[]));}return dialogs;});
   for(const dialog of changedDialogs){if(/\bVIN was not found\b/i.test(text(dialog)))state.noMatch=true;if(!state.needsPostModalChange)state.modalVin=view.vin;state.needsPostModalChange=true;}
   if(fingerprint!==state.fingerprint||identityTouched||changedDialogs.length){state.revision++;state.fingerprint=fingerprint;state.candidate=null;}
   state.identityNodes=view.identityNodes;
   if(view.busy)state.sawBusy=true;if(view.vin!==state.beforeVin)state.sawChanged=true;
   if(view.modal){if(/\bVIN was not found\b/i.test(view.modal))state.noMatch=true;if(!state.needsPostModalChange)state.modalVin=view.vin;state.needsPostModalChange=true;}
   else if(state.needsPostModalChange&&view.vin!==state.modalVin)state.needsPostModalChange=false;
   return view;
  };
  state.observe=observe;window.__partsDeskVinResolution=state;observe();
  if(typeof MutationObserver==='function'){state.observer=new MutationObserver(observe);state.observer.observe(document.documentElement,{subtree:true,childList:true,characterData:true,attributes:true,attributeFilter:['title','class','style','aria-hidden','aria-busy','checked']});}
  const setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value')?.set;
  if(setter)setter.call(input,suffix);else input.value=suffix;
  input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));find.click();
  observe();return {...echoed,stage:'loading',accepted:true,ready:false,message:'Finding the full VIN in Snap-on…'};
 }
 const view=same()&&state.active?state.observe():read();
 let stage='idle',message='Search these last 8 characters to identify the full vehicle.';
 if(view.login){stage='login';message='Sign into Snap-on in the catalog, then retry the VIN search.';}
 else if(view.modal){stage=/\bVIN was not found\b/i.test(view.modal)?'no-match':'catalog';message=stage==='no-match'?'Snap-on did not find this VIN suffix. Check the last 8 characters or enter the full VIN.':'Review the VIN search results in the catalog. If there is more than one vehicle, choose the correct one before confirming.';}
 else if(same()&&state.noMatch){stage='no-match';message='Snap-on did not find this VIN suffix. Retry the search or enter the full VIN.';}
 else if(view.busy){stage='loading';message='Waiting for the VIN search to finish…';}
 else if(same()&&state.active){
  if(state.needsPostModalChange){stage='vehicle-needed';message='No new vehicle was selected from the catalog results. Retry the search or enter the full VIN.';}
  else if(!(state.sawBusy||state.sawChanged)){stage='loading';message='Waiting for a fresh search result. The previous catalog vehicle cannot confirm this request.';}
  else if(!/^[A-HJ-NPR-Z0-9]{17}$/.test(view.vin)||!view.vin.endsWith(suffix)){stage='vehicle-needed';message='No matching full VIN is visible. Check the catalog results or enter the full VIN.';}
  else if(!view.filtersOn){stage='filters';message='Turn on VIN filters in the catalog before confirming this vehicle.';}
  else {stage='resolved';message='Check the complete VIN and vehicle, then confirm this vehicle.';}
 }
 const ready=stage==='resolved',identityId=ready?searchId+'-'+state.revision:'';
 const result={...echoed,stage,ready,filtersOn:view.filtersOn,message,...(ready?{vin:view.vin,vehicle:view.vehicle,identityId}:{})};
 if(command==='confirm'){
  if(!ready||request.vin!==view.vin||request.identityId!==identityId||state.candidate!==identityId)return {...result,confirmed:false,error:'The catalog vehicle changed or has not been reviewed. Refresh and confirm the matching full VIN again.'};
  state.candidate=null;stop();state.active=false;
  return {...result,confirmed:true};
 }
 if(command!=='snapshot')return {...echoed,stage:'invalid',ready:false,error:'Unsupported VIN action.'};
 if(ready)state.candidate=identityId;
 return result;
}
