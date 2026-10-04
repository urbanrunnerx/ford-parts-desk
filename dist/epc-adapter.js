/* Snap-on visible catalog adapter. Reads rendered controls only; no account tokens or private APIs. */
function partsDeskEpc(command, request) {
 'use strict';
 request = request || {};
 const clean = value => String(value || '').replace(/[\u200B-\u200D\uFEFF]/g, '').replace(/\s+/g, ' ').trim();
 const text = el => clean(el && el.innerText);
 const visible = el => !!el && el.getClientRects().length > 0 && getComputedStyle(el).visibility !== 'hidden' && !el.closest('[aria-hidden="true"]');
 const all = (selector, root = document) => [...root.querySelectorAll(selector)].filter(visible);
 const hash = value => { let n = 2166136261; for (const ch of value) { n ^= ch.charCodeAt(0); n = Math.imul(n, 16777619); } return (n >>> 0).toString(36); };
 const button = label => all('button').find(b => text(b) === label && !b.disabled);
 const vinNode=document.querySelector('#toolbar-vin-url-anchor');
 const currentVin = visible(vinNode)?text(vinNode).toUpperCase():'';
 const filter = document.querySelector('[title="Toggle VIN filters on and off"] input[type="checkbox"]');
 const ready = /^[A-HJ-NPR-Z0-9]{17}$/.test(request.vin || '') && currentVin === request.vin && !!filter?.checked;
 const signedIn = !!button('Find VIN') || !!currentVin;
 const login = all('input[type="password"]').length > 0;
 const busy = all('[aria-busy="true"],.ag-overlay-loading-center,.p-progress-spinner,.p-progressspinner').length > 0 || all('div,span,p').some(e => e.childElementCount === 0 && /^Loading(?:\.{3}|…)?$/i.test(text(e)));
 // Modal/session interruptions also block illustration reads.
 const modal = all('[role="dialog"],[role="alertdialog"]').find(e=>text(e));
 const targets = new Map(), scrollTargets = [];
 const crumbNodes = all('ul.breadcrumb [role="menuitem"] > a.p-menuitem-link[href="#"]');
 const breadcrumbs = crumbNodes.map((el, i) => {
  const label = text(el), id = 'crumb-' + i + '-' + hash(label);
  const disabled = el.classList.contains('p-breadcrumb-disabled') || el.getAttribute('aria-disabled') === 'true';
  if (!disabled) targets.set(id, el);
  return {id, label, disabled};
 });
 const context = breadcrumbs.map(b => b.label).join(' > ').slice(0,2000);
 // This is the observed Snap-on layered canvas viewer, not a guessed image URL.
 // Read only its visible base drawing (z=0), excluding search/selection overlays.
 // Exact pixel fingerprints bind a preview to the current drawing even though the
 // site exposes no public DOM image ID. Work is bounded; PNG export is opt-in only.
 const readDiagramSource = () => {
  const unavailable={status:'unavailable',message:'No verified diagram is available for this part at the current catalog location.',sourceId:''};
  if(!ready||busy||login||modal)return unavailable;
  const split=all('#sbsPanel');if(split.length!==1)return unavailable;
  const images=all('#imagePanel',split[0]),panels=all('#partsPanel',split[0]);
  if(images.length!==1)return unavailable;
  const fallback={status:'catalog',message:'This illustration needs the original catalog view. Open the catalog to inspect the drawing and its callouts.',sourceId:''};
  if(panels.length!==1||images[0].parentElement!==split[0]||panels[0].parentElement!==split[0])return fallback;
  const viewers=all('#image-container snapon-imageviewer-component',images[0]);if(viewers.length!==1)return fallback;
  const layers=all('canvas',viewers[0]).filter(el=>!el.closest('#hiddenDiv'));
  if(layers.length!==3)return fallback;
  const ordered=layers.map(el=>({el,style:getComputedStyle(el),rect:el.getBoundingClientRect()})).sort((a,b)=>Number(a.style.zIndex)-Number(b.style.zIndex));
  const source=ordered[0].el,width=source.width,height=source.height,rect=ordered[0].rect;
  if(!Number.isInteger(width)||!Number.isInteger(height)||width<1||height<1||width>4096||height>4096||width*height>2000000)return fallback;
  const close=(a,b)=>Number.isFinite(a)&&Number.isFinite(b)&&Math.abs(a-b)<.5;
  if(!rect||rect.width<=0||rect.height<=0||!ordered.every(({el,style,rect:r},i)=>style.zIndex===String(i)&&style.position==='absolute'&&el.parentElement===source.parentElement&&el.width===width&&el.height===height&&close(r.left,rect.left)&&close(r.top,rect.top)&&close(r.width,rect.width)&&close(r.height,rect.height)))return fallback;
  try {
   const drawing=document.createElement('canvas');drawing.width=width;drawing.height=height;
   const ctx=drawing.getContext('2d',{willReadFrequently:true});if(!ctx)return fallback;
   ctx.drawImage(source,0,0);const pixels=ctx.getImageData(0,0,width,height).data;
   if(pixels.length!==width*height*4)return fallback;
   let first=2166136261,second=5381,painted=false,varied=false;
   for(let i=0;i<pixels.length;i++){first=Math.imul(first^pixels[i],16777619);second=(Math.imul(second,33)^pixels[i])>>>0;if(i%4===3&&pixels[i])painted=true;if(pixels[i]!==pixels[i%4])varied=true;}
   if(!painted||!varied)return fallback;
   const geometry=[rect.left,rect.top,rect.width,rect.height].map(n=>Math.round(n*100)).join('_');
   const sourceId='diagram-'+width+'x'+height+'-'+geometry+'-'+(first>>>0).toString(36)+'-'+(second>>>0).toString(36);
   return {status:'available',sourceId,drawing,partsPanel:panels[0],width,height};
  }catch(error){return fallback;} // Tainted/unsupported canvases stay in the catalog.
 };
 let diagramSource=readDiagramSource();
 // Only observed part data is kept, never credentials. A new request, vehicle, base or path
 // gets a fresh bounded collection, even when the Android catalog session stays warm.
 const storage = window;
 const diagramScopeKey=busy?(storage.__partsDeskEpcObserved?.diagramScopeKey || ''):diagramSource.sourceId;
 const scopeIdentity = JSON.stringify([request.requestId || 'legacy', request.vin || '', request.base || '', currentVin, context,diagramScopeKey]);
 const scopeKey = hash(scopeIdentity);
 let collection = storage.__partsDeskEpcObserved;
 const resetCollection = () => collection = storage.__partsDeskEpcObserved = {scopeKey, scopeIdentity, diagramScopeKey, records:[], limited:false};
 if (!collection || collection.scopeIdentity !== scopeIdentity || !Array.isArray(collection.records)) resetCollection();
 const MAX_LOADED = 150;
 const clearCollection = () => { delete storage.__partsDeskEpcObserved; };

 const vehicleHint = all('[title]').find(e => /^ENG:/.test(e.getAttribute('title') || ''));
 const vehicle = clean(vehicleHint?.getAttribute('title') || '');
 const grids = all('[role="grid"]');
 const headings = grid => all('[role="columnheader"]', grid).map(h => ({col:h.getAttribute('col-id'), label:text(h).replace(/[\uF000-\uF8FF]/g, '').trim()}));
 // AG Grid can split one row into pinned and center cells. Merge only matching rendered row IDs.
 const rows = grid => {
  const out = new Map(); let ordinal = 0;
  for (const el of all('[role="row"]', grid)) {
   const cells = all('[role="gridcell"]', el); if (!cells.length) continue;
   const stableKey = el.getAttribute('row-id') || el.getAttribute('aria-rowindex');
   const key = stableKey || 'fixture-' + ordinal++;
   let row = out.get(key); if (!row) { row = {key, stable:!!stableKey, cells:new Map(), selected:false}; out.set(key,row); }
   row.selected ||= el.getAttribute('aria-selected') === 'true';
   cells.forEach((c,i) => row.cells.set(c.getAttribute('col-id') || 'column-' + i, c));
  }
  return [...out.values()];
 };
 const cell = (row, col, label, headers) => row.cells.get(col) || row.cells.get('column-' + headers.findIndex(h => h.label === label));
 const value = (row, col, label, headers) => text(cell(row,col,label,headers));
 const addScroll = (grid, groupId) => {
  const e = all('.ag-body-viewport',grid).find(e => e.clientHeight > 0 && e.scrollHeight > e.clientHeight + 2);
  if(e) scrollTargets.push({element:e, groupId, more:e.scrollTop + e.clientHeight < e.scrollHeight - 2});
 };
 const groups = [], parts = [], liveParts = new Map(); let partGrid = null, partViewport = null, groupCount = 0, partGridCount = 0;
 for (let gi = 0; gi < grids.length; gi++) {
  const grid = grids[gi], headers = headings(grid), has = label => headers.some(h => h.label === label);
  if (has('Call/Base') && has('Part Number')) { partGrid = grid; partGridCount++; continue; }
  // Only catalog navigation tables are actionable. Picklist/order/price tables are never targets.
  const locationGrid = has('Base Number') && has('Part Location');
  const illustrationGrid = has('Illustration');
  const groupGrid = has('Group') && headers.every(h => !h.label || h.label === 'Group');
  if (!locationGrid && !illustrationGrid && !groupGrid) continue;
  const id = 'group-' + gi, label = locationGrid ? 'Part location' : illustrationGrid ? 'Illustration' : (++groupCount === 1 ? 'System' : groupCount === 2 ? 'Section' : 'Group ' + groupCount);
  const options = []; let inheritedBase = '', inheritedDescription = '';
  for (const row of rows(grid)) {
   let target, detail = '';
   if (locationGrid) {
    const base = value(row,'crossCatKey','Base Number',headers);
    if(base) { inheritedBase=base; inheritedDescription=value(row,'partDescription','Part Description',headers); }
    if(inheritedBase !== request.base) continue;
    target = cell(row,'partLocation','Part Location',headers); detail=inheritedDescription;
   } else target = cell(row,'name',illustrationGrid?'Illustration':'Group',headers);
   const name = text(target); if(!name) continue;
   const optionId = id + '-' + hash(row.key + '|' + name) + '-' + options.length;
   targets.set(optionId,target); options.push({id:optionId,label:name,detail,selected:row.selected});
  }
  if(options.length) groups.push({id,label,options});
  addScroll(grid,id);
 }
 // A drawing must belong to the same single rendered parts grid being verified.
 if(diagramSource.status==='available') {
  const matchingGrids=all('#partsGrid',diagramSource.partsPanel);
  if(partGridCount!==1||matchingGrids.length!==1||!matchingGrids[0].contains(partGrid))diagramSource={status:'catalog',sourceId:diagramSource.sourceId,message:'The drawing cannot be tied to this parts grid. Open the catalog to inspect this part.'};
 }
 const thumbs = all('a.thumbnailNonIllustrated[title],a.thumbnail[title]');
 if (thumbs.length) {
  const id = 'thumbnails';
  const options = thumbs.map((el,i) => {
   const label=clean(el.getAttribute('title') || text(el)), optionId=id+'-'+i+'-'+hash(label);
   targets.set(optionId,el); return {id:optionId,label};
  });
  groups.push({id,label:thumbs.some(t=>t.classList.contains('thumbnail'))?'Illustration':'Catalog selection',options});
 }
 if (!partGrid && !busy) resetCollection();
 if (partGrid && ready && !busy && !login) {
  partViewport = all('.ag-body-viewport',partGrid).find(e => e.clientHeight > 0) || null;
  const headers=headings(partGrid); let base='',description='',application='',remarks='',from='',to='',quantity='';
  for(const row of rows(partGrid)) {
   const rowBase=value(row,'calloutLabel','Call/Base',headers);
   if(rowBase) {
    base=rowBase; description=value(row,'renderedDescription','Part Description',headers);
    application=value(row,'APPLICATION','Application',headers); remarks=value(row,'remarks','Restrictions/Remarks',headers);
    from=value(row,'FROM','From',headers);to=value(row,'TO','To',headers);quantity=value(row,'original_qty','Qty',headers);
   }
   const number=value(row,'formattedPartNumber','Part Number',headers);
   if(!/^([A-Z0-9]{4}-[0-9][A-Z0-9]{3,7}-[A-Z0-9]{1,10}|[A-Z]{1,6}-[A-Z0-9]{1,15})$/.test(number))continue;
   const conventional=number.match(/^[A-Z0-9]{4}-([0-9][A-Z0-9]{3,7})-/);
   const rowKey=row.key+'|'+number, previous=collection.records.find(r=>r.rowKey===rowKey);
   // A full service number can identify an unheaded continuation, but cannot supply
   // omitted application/restriction data. Never borrow a previous viewport's heading.
   const observedBase=base || (conventional?conventional[1]:previous?.part.base || '');
   if(observedBase!==request.base || (conventional&&conventional[1]!==request.base))continue;
   const complete=base===request.base;
   if(complete&&row.stable) collection.records=collection.records.filter(r=>r.rowId!==row.key||r.part.serviceNumber===number);
   const p={vin:currentVin,base:observedBase,serviceNumber:number,
    description:value(row,'renderedDescription','Part Description',headers)||description,
    application:value(row,'APPLICATION','Application',headers)||application,
    remarks:value(row,'remarks','Restrictions/Remarks',headers)||remarks,from:value(row,'FROM','From',headers)||from,
    to:value(row,'TO','To',headers)||to,quantity:value(row,'original_qty','Qty',headers)||quantity,context};
   const part={id:'part-'+hash(JSON.stringify(p)),...p};
   if(complete)liveParts.set(part.id,part);
   if(previous) {
    // Preserve the last complete observation for display. It remains unselectable
    // until locate brings its original heading and row back for a fresh verify.
    if(complete || !previous.complete) { previous.part=part;previous.complete=complete;previous.scrollTop=partViewport?.scrollTop || 0; }
   } else if(collection.records.length<MAX_LOADED) collection.records.push({rowKey,rowId:row.stable?row.key:null,part,complete,scrollTop:partViewport?.scrollTop || 0});
   else collection.limited=true;
  }
  addScroll(partGrid,'parts');
 }
 for(const record of collection.records) {
  if(parts.some(p=>p.id===record.part.id))continue;
  parts.push({...record.part,visible:liveParts.has(record.part.id),requiresCatalogReview:!record.complete});
 }
 let stage=busy?'loading':login?'login':!signedIn||modal?'unsupported':!ready?(currentVin===request.vin?'filters':'vehicle'):parts.length?'parts':groups.length?'choices':partGrid?'empty':'unsupported';
 if (!ready || login || modal) { resetCollection(); parts.length=0; liveParts.clear(); }
 const safeParts = stage==='parts'?parts:[];
 const safeGroups = stage==='choices'||stage==='parts'?groups:[];
 const messages={login:'Sign into your Snap-on account, then return to guided lookup.',vehicle:'Load the job VIN to start the catalog lookup.',filters:'Turn on VIN filters in the catalog before continuing.',loading:'Waiting for the catalog…',choices:'Choose a catalog location or application below.',parts:'Review the application and select the required service part.',empty:'No matching service number is displayed for this base at this location.',unsupported:modal?'The catalog needs attention: '+text(modal).slice(0,400):'This catalog screen needs the original catalog view. Open Catalog / sign in, then return here.'};
 const canMore=scrollTargets.some(s=>s.more) && ready && !busy && !modal;
 const payload={schema:3,stage,vin:currentVin,ready,signedIn,busy,vehicle,context,scopeKey,diagramSourceId:diagramSource.sourceId,message:messages[stage],breadcrumbs:ready?breadcrumbs:[],groups:safeGroups,parts:safeParts,canMore,
  loadedCount:safeParts.length,renderedCount:liveParts.size,collectionLimited:collection.limited,
  scope:'Observed catalog rows loaded in this lookup, not an exhaustive fitment result. Saved selections are rechecked against live rendered evidence.'};
 const snapshotId='snapshot-'+hash(JSON.stringify({...payload,requestedBase:request.base,requestedVin:request.vin,viewport:scrollTargets.map(s=>[s.groupId,s.element.scrollTop])}));
 payload.snapshotId=snapshotId;
 if(command==='snapshot')return payload;
 if(command==='state')return {vin:currentVin,ready,signedIn,busy,context,snapshotId};
 if(command==='capture')return ready&&safeParts.length?{vin:currentVin,parts:safeParts,context,snapshotId}:{error:messages[stage]||'Open the correct illustration first.'};
 const setInput=(selector,v)=>{const el=document.querySelector(selector);if(!visible(el))return false;el.focus();Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(el,v);el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));el.blur();return true;};
 if(command==='vin') {
  if(!/^[A-HJ-NPR-Z0-9]{17}$/.test(request.vin||''))return {error:'Enter a complete VIN.'};
  if(busy||modal)return {error:messages[stage]};
  if(!button('Find VIN')||!setInput('#equipmentEntryInputId',request.vin))return {error:'Sign into the Ford catalog, then return to guided lookup.'};
  clearCollection();button('Find VIN').click();return {message:'Loading the requested vehicle…'};
 }
 if(!ready)return {error:'The requested VIN must be active with VIN filters enabled.'};
 if(busy||modal)return {error:messages[stage]};
 if(command==='search') {
  if(!/^\d[A-Z0-9]{3,7}$/.test(request.base||''))return {error:'Invalid base number.'};
  if(!button('Search')||!setInput('#partEntryInputId',request.base))return {error:'Search controls could not be found. Open Catalog / sign in.'};
  clearCollection();button('Search').click();return {message:'Finding locations for this base…'};
 }
 if(request.snapshotId!==snapshotId)return {error:'The catalog changed. Refresh the choices and select again.'};
 if(command==='select') {
  const target=targets.get(request.optionId);
  if(!target||!visible(target))return {error:'This catalog choice is no longer available. Refresh the choices.'};
  clearCollection();target.click();return {message:'Loading the selected catalog location…'};
 }
 if(command==='more') {
  const next=scrollTargets.find(s=>s.more&&(!request.groupId||s.groupId===request.groupId));
  if(!next)return {error:'No additional rendered rows are available here.'};
  next.element.scrollTop=Math.min(next.element.scrollHeight-next.element.clientHeight,next.element.scrollTop+Math.max(80,next.element.clientHeight-55));
  next.element.dispatchEvent(new Event('scroll',{bubbles:true}));return {message:'Loading the next catalog rows…'};
 }
 if(command==='locate') {
  const record=collection.records.find(r=>r.part.id===request.partId);
  if(!record || !record.complete)return {error:'This row has no complete observed application heading. Open the catalog and review its full application first.'};
  if(liveParts.has(request.partId))return {message:'The selected part is visible and ready for review.',partId:request.partId};
  if(!partViewport)return {error:'The original catalog row is unavailable. Open the catalog or repeat the lookup.'};
  partViewport.scrollTop=Math.max(0,Math.min(record.scrollTop,partViewport.scrollHeight-partViewport.clientHeight));
  partViewport.dispatchEvent(new Event('scroll',{bubbles:true}));
  return {message:'Returning to the original part and application for review…',partId:request.partId};
 }
 if(command==='verify'||command==='diagram') {
  const part=liveParts.get(request.partId);
  if(!part)return {error:'The selected part or application changed. Refresh and review it again.'};
  if(command==='diagram') {
   let diagram={status:diagramSource.status,sourceId:diagramSource.sourceId,message:diagramSource.message || ''};
   if(diagramSource.status==='available') {
    try {
     const dataUrl=diagramSource.drawing.toDataURL('image/png');
     if(typeof dataUrl!=='string'||dataUrl.length>1500000||!/^data:image\/png;base64,[A-Za-z0-9+/]+={0,2}$/.test(dataUrl))throw Error('Unsupported illustration size');
     // Call/Base is from the freshly verified rendered row/group. No hotspot or
     // service-specific highlight is claimed; the catalog exposes no DOM mapping.
     diagram={status:'available',sourceId:diagramSource.sourceId,dataUrl,title:context || 'Current catalog illustration',callout:part.base,
      message:'Current catalog base drawing. Call/Base '+part.base+' comes from the selected row; no part-specific highlight has been added.'};
    }catch(error){diagram={status:'catalog',sourceId:diagramSource.sourceId,message:'This illustration could not be copied safely. Open the catalog to inspect the drawing and its callouts.'};}
   }
   return {part:{...part},requestId:request.requestId || 'legacy',snapshotId,diagram};
  }
  return {part:{...part}};
 }
 return {error:'Unsupported catalog action.'};
}

