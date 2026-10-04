import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

// Deterministic rendered-DOM contract fixtures. These exercise the real adapter,
// not the authenticated website or Android WebView layout implementation.
const adapter=fs.readFileSync(new URL('../dist/epc-adapter.js',import.meta.url),'utf8');
const VIN='1M8GDM9AXKP042788',OTHER='1M8GDM9A0KP042788';
class Element {
 constructor(text='',attrs={},queries={}){this.innerText=text;this.attrs=attrs;this.queries=queries;this.classList={contains:()=>false};this.events=[];}
 getClientRects(){return this.hidden?[]:[{}];}
 getAttribute(k){return this.attrs[k]??null;}
 closest(selector){for(let el=this;el;el=el.parentElement)if((selector==='#hiddenDiv'&&el.attrs?.id==='hiddenDiv')||(selector==='[aria-hidden="true"]'&&el.attrs?.['aria-hidden']==='true'))return el;return null;}
 contains(target){for(let el=target;el;el=el.parentElement)if(el===this)return true;return false;}
 getBoundingClientRect(){return this.rect||{left:0,top:0,width:8,height:6};}
 querySelectorAll(s){return this.queries[s]??[];}
 dispatchEvent(e){this.events.push(e.type);}
 click(){this.clicks=(this.clicks||0)+1;}
 focus(){} blur(){}
}
class Input extends Element {get value(){return this._value||'';}set value(v){this._value=v;}}
const cell=(col,value)=>new Element(value,{'col-id':col});
const row=(id,base,number,extra={})=>new Element('',{'row-id':String(id)},{'[role="gridcell"]':Object.entries({calloutLabel:base,renderedDescription:base?'Fixture hub':'',formattedPartNumber:number,...extra}).map(([k,v])=>cell(k,v))});
function fixture(){
 const vin=new Element(VIN),filter={checked:true},viewport=new Element();Object.assign(viewport,{clientHeight:100,scrollHeight:1000,scrollTop:0});
 const grid=new Element('',{}, {'[role="columnheader"]':['Call/Base','Part Description','Part Number'].map(t=>new Element(t)),'[role="row"]':[],'.ag-body-viewport':[viewport]});
 const crumb=new Element('Front Knuckle and Hub'),find=new Element('Find VIN'),search=new Element('Search'),vinInput=new Input(),partInput=new Input();
 const state={grid,vin,filter,viewport,crumb,find,search,vinInput,partInput,busy:false,modal:null,login:false,hasGrid:true,split:null,exports:0,drawSources:[],imageReads:0,dataUrl:'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg=='};
 const doc={createElement(tag){assert.equal(tag,'canvas');let source;return {getContext(){return {drawImage(canvas){source=canvas;state.drawSources.push(canvas);},getImageData(){state.imageReads++;if(state.tainted)throw Error('Synthetic origin restriction');return {data:source.pixels};}};},toDataURL(type){assert.equal(type,'image/png');state.exports++;if(state.exportError)throw Error('Synthetic export failure');return state.dataUrl;}};},querySelector:s=>s==='#toolbar-vin-url-anchor'?vin:s.includes('Toggle VIN')?filter:s==='#equipmentEntryInputId'?vinInput:s==='#partEntryInputId'?partInput:null,
 querySelectorAll:s=>s==='#sbsPanel'?(state.split?[state.split]:[]):s==='[role="grid"]'?(state.hasGrid?[grid]:[]):s==='ul.breadcrumb [role="menuitem"] > a.p-menuitem-link[href="#"]'?[crumb]:s==='button'?[find,search]:s==='input[type="password"]'?(state.login?[new Input()]:[]):s==='[role="dialog"],[role="alertdialog"]'?(state.modal?[new Element(state.modal)]:[]):s.startsWith('[aria-busy=')?(state.busy?[new Element()]:[]):[]};
 const context={window:{},document:doc,getComputedStyle:el=>({visibility:'visible',...el.style}),HTMLInputElement:Input,Event:class {constructor(type){this.type=type;}}};vm.createContext(context);
 state.call=(command='snapshot',extra={})=>{vm.runInContext(adapter,context);return JSON.parse(JSON.stringify(context.partsDeskEpc(command,{vin:VIN,base:'1104',requestId:'lookup-1',...extra})));};
 state.rows=rows=>{grid.queries['[role="row"]']=rows;};
 state.illustration=()=>{
  const split=new Element(),imagePanel=new Element(),partsPanel=new Element(),viewer=new Element(),td=new Element(),gridWrap=new Element();
  const layers=Array.from({length:3},(_,i)=>{const el=new Element();Object.assign(el,{width:8,height:6,parentElement:td,style:{zIndex:String(i),position:'absolute'},pixels:new Uint8ClampedArray(8*6*4).fill(255)});el.pixels[0]=i;return el;});
  split.queries={'#imagePanel':[imagePanel],'#partsPanel':[partsPanel]};imagePanel.parentElement=split;partsPanel.parentElement=split;
  imagePanel.queries={'#image-container snapon-imageviewer-component':[viewer]};partsPanel.queries={'#partsGrid':[gridWrap]};grid.parentElement=gridWrap;gridWrap.parentElement=partsPanel;
  viewer.queries={canvas:layers};state.split=split;Object.assign(state,{imagePanel,partsPanel,viewer,layers,gridWrap});return state;
 };
 return state;
}
test('continuation viewport retains deduplicated loaded parts without claiming live heading evidence',()=>{
 const f=fixture();f.rows([row(1,'1104','TEST-1104-A',{APPLICATION:'Option A'}),row(2,'','TEST-1104-B')]);
 const first=f.call();assert.equal(first.parts.length,2);assert.ok(first.parts.every(p=>p.visible));
 f.viewport.scrollTop=100;f.rows([row(2,'','TEST-1104-B'),row(3,'','TEST-1104-C')]);
 const next=f.call();assert.equal(next.stage,'parts');assert.equal(next.loadedCount,3);assert.equal(next.renderedCount,0);
 assert.deepEqual(next.parts.map(p=>p.serviceNumber),['TEST-1104-A','TEST-1104-B','TEST-1104-C']);
 assert.equal(next.parts[1].application,'Option A');assert.equal(next.parts[1].visible,false);
 assert.equal(next.parts[2].requiresCatalogReview,true);assert.equal(next.parts[2].application,'');
 assert.equal(f.call().loadedCount,3);
});
test('cached-only rows cannot verify; locate returns to original viewport and fresh live evidence verifies',()=>{
 const f=fixture();const rows=[row(1,'1104','TEST-1104-A'),row(2,'','TEST-1104-B')];f.rows(rows);const first=f.call(),part=first.parts[1];
 f.viewport.scrollTop=100;f.rows([row(2,'','TEST-1104-B')]);let s=f.call();
 assert.match(f.call('verify',{partId:part.id,snapshotId:s.snapshotId}).error,/changed/);
 assert.equal(f.call('locate',{partId:part.id,snapshotId:s.snapshotId}).partId,part.id);assert.equal(f.viewport.scrollTop,0);
 f.rows(rows);s=f.call();assert.equal(f.call('verify',{partId:part.id,snapshotId:s.snapshotId}).part.serviceNumber,'TEST-1104-B');
});
test('new continuation without a complete observation requires catalog review',()=>{
 const f=fixture();f.rows([row(2,'','TEST-1104-B')]);const s=f.call();assert.equal(s.parts.length,1);assert.equal(s.parts[0].requiresCatalogReview,true);
 assert.match(f.call('locate',{partId:s.parts[0].id,snapshotId:s.snapshotId}).error,/complete observed/);
 assert.ok(f.call('verify',{partId:s.parts[0].id,snapshotId:s.snapshotId}).error);
});
test('request, VIN, base and catalog-path boundaries clear prior loaded rows',()=>{
 for(const change of ['request','vin','base','path']){const f=fixture();f.rows([row(1,'1104','TEST-1104-A')]);f.call();f.rows([]);let extra={};
  if(change==='request')extra.requestId='lookup-2';if(change==='vin'){f.vin.innerText=OTHER;extra.vin=OTHER;}if(change==='base')extra.base='6731';if(change==='path')f.crumb.innerText='Rear Knuckle and Hub';
  assert.equal(f.call('snapshot',extra).parts.length,0,change);
 }
});
test('login, disabled VIN filters, modal and leaving parts view hide and clear accumulated data',()=>{
 for(const change of ['login','filter','modal','grid']){const f=fixture();f.rows([row(1,'1104','TEST-1104-A')]);f.call();
  if(change==='login')f.login=true;if(change==='filter')f.filter.checked=false;if(change==='modal')f.modal='Session expired';if(change==='grid')f.hasGrid=false;
  assert.equal(f.call().parts.length,0,change);f.login=false;f.filter.checked=true;f.modal=null;f.hasGrid=true;f.rows([]);assert.equal(f.call().parts.length,0,change);
 }
});
test('busy DOM cannot overwrite a complete observation',()=>{
 const f=fixture();f.rows([row(1,'1104','TEST-1104-A',{APPLICATION:'Option A'})]);f.call();f.busy=true;f.rows([row(1,'1104','TEST-1104-A',{APPLICATION:'Partial loading value'})]);assert.equal(f.call().parts.length,0);
 f.busy=false;f.rows([]);assert.equal(f.call().parts[0].application,'Option A');assert.equal(f.call().parts[0].visible,false);
});
test('changed application invalidates review and replaces rather than duplicates a row',()=>{
 const f=fixture();f.rows([row(1,'1104','TEST-1104-A',{APPLICATION:'Option A'})]);const a=f.call();f.rows([row(1,'1104','TEST-1104-A',{APPLICATION:'Option B'})]);const b=f.call();
 assert.notEqual(a.snapshotId,b.snapshotId);assert.equal(b.loadedCount,1);assert.equal(b.parts[0].application,'Option B');assert.ok(f.call('verify',{partId:a.parts[0].id,snapshotId:a.snapshotId}).error);
});
test('same service number with distinct applications remains distinct',()=>{
 const f=fixture();f.rows([row(1,'1104','TEST-1104-A',{APPLICATION:'Left'}),row(2,'1104','TEST-1104-A',{APPLICATION:'Right'})]);assert.equal(f.call().loadedCount,2);
});
test('new search clears previously loaded parts and emits input/change events',()=>{
 const f=fixture();f.rows([row(1,'1104','TEST-1104-A')]);f.call();assert.equal(f.call('search').message,'Finding locations for this base…');assert.equal(f.search.clicks,1);assert.deepEqual(f.partInput.events,['input','change']);f.rows([]);assert.equal(f.call().loadedCount,0);
});
test('collection is bounded and discloses its limit',()=>{
 const f=fixture();f.rows(Array.from({length:510},(_,i)=>row(i,'1104','TEST-1104-'+i)));const s=f.call();assert.equal(s.loadedCount,150);assert.equal(s.collectionLimited,true);
});
test('a changed service number in the same stable row replaces the old result',()=>{
 const f=fixture();f.rows([row(1,'1104','TEST-1104-A')]);const a=f.call();f.rows([row(1,'1104','TEST-1104-B')]);const b=f.call();
 assert.equal(b.loadedCount,1);assert.equal(b.parts[0].serviceNumber,'TEST-1104-B');assert.ok(f.call('verify',{partId:a.parts[0].id,snapshotId:a.snapshotId}).error);
});

test('diagram is opt-in, verifies a current part and reports no known diagram without guessing a URL',()=>{
 const f=fixture();f.rows([row(1,'1104','TEST-1104-A')]);const snapshot=f.call(),part=snapshot.parts[0];
 const result=f.call('diagram',{partId:part.id,snapshotId:snapshot.snapshotId});
 assert.equal(result.part.id,part.id);assert.equal(result.snapshotId,snapshot.snapshotId);assert.equal(result.requestId,'lookup-1');
 assert.equal(result.diagram.status,'unavailable');assert.match(result.diagram.message,/No verified diagram/);
 assert.equal(result.diagram.dataUrl,undefined);assert.equal(result.diagram.callout,undefined);assert.equal(f.find.clicks,undefined);assert.equal(f.search.clicks,undefined);
 assert.equal(f.call('verify',{partId:part.id,snapshotId:snapshot.snapshotId}).part.id,part.id,'Diagram inspection does not save or change the part');
});

test('diagram rejects cached-only and ambiguous continuation rows until a complete heading is visible again',()=>{
 const f=fixture(),initial=[row(1,'1104','TEST-1104-A'),row(2,'','TEST-1104-B')];f.rows(initial);const first=f.call(),part=first.parts[1];
 f.viewport.scrollTop=100;f.rows([row(2,'','TEST-1104-B'),row(3,'','TEST-1104-C')]);const current=f.call();
 for(const candidate of current.parts)assert.ok(f.call('diagram',{partId:candidate.id,snapshotId:current.snapshotId}).error);
 f.call('locate',{partId:part.id,snapshotId:current.snapshotId});f.rows(initial);const restored=f.call();
 assert.equal(f.call('diagram',{partId:part.id,snapshotId:restored.snapshotId}).part.id,part.id);
});

test('diagram rejects changed request, VIN, base, catalog path, application and service number',()=>{
 for(const change of ['request','vin','base','path','application','number']){
  const f=fixture();f.rows([row(1,'1104','TEST-1104-A',{APPLICATION:'Left'})]);const snapshot=f.call(),pending={partId:snapshot.parts[0].id,snapshotId:snapshot.snapshotId};
  if(change==='request')pending.requestId='lookup-2';if(change==='vin'){f.vin.innerText=OTHER;pending.vin=OTHER;}if(change==='base')pending.base='6731';if(change==='path')f.crumb.innerText='Rear Knuckle and Hub';
  if(change==='application')f.rows([row(1,'1104','TEST-1104-A',{APPLICATION:'Right'})]);if(change==='number')f.rows([row(1,'1104','TEST-1104-B',{APPLICATION:'Left'})]);
  assert.ok(f.call('diagram',pending).error,change);
 }
});

test('diagram cannot bypass busy, login, modal, filters, or missing parts screen',()=>{
 for(const change of ['busy','login','modal','filter','grid']){
  const f=fixture();f.rows([row(1,'1104','TEST-1104-A')]);const snapshot=f.call();
  if(change==='busy')f.busy=true;if(change==='login')f.login=true;if(change==='modal')f.modal='Session expired';if(change==='filter')f.filter.checked=false;if(change==='grid')f.hasGrid=false;
  assert.ok(f.call('diagram',{partId:snapshot.parts[0].id,snapshotId:snapshot.snapshotId}).error,change);
 }
});

test('observed canvas viewer exports only on explicit diagram action and excludes selection overlays',()=>{
 const f=fixture().illustration();f.rows([row(1,'1104','TEST-1104-A',{APPLICATION:'Left'})]);const snapshot=f.call(),part=snapshot.parts[0];
 assert.ok(snapshot.diagramSourceId.startsWith('diagram-8x6-'));assert.equal(f.exports,0);assert.ok(f.imageReads>0);
 const verified=f.call('verify',{partId:part.id,snapshotId:snapshot.snapshotId});assert.equal(verified.part.id,part.id);assert.equal(f.exports,0);
 const result=f.call('diagram',{partId:part.id,snapshotId:snapshot.snapshotId});assert.equal(result.diagram.status,'available');
 assert.equal(result.diagram.dataUrl,f.dataUrl);assert.equal(result.diagram.sourceId,snapshot.diagramSourceId);assert.equal(result.snapshotId,snapshot.snapshotId);
 assert.equal(result.diagram.callout,'1104');assert.match(result.diagram.message,/no part-specific highlight/);assert.equal(f.exports,1);
 assert.ok(f.drawSources.every(source=>source===f.layers[0]),'Only the base drawing is read; selection overlays are not claimed to locate this service part');
 assert.equal(f.find.clicks,undefined);assert.equal(f.search.clicks,undefined);assert.equal(f.call().snapshotId,snapshot.snapshotId);
});

test('every base drawing pixel binds the snapshot; a changed drawing clears offscreen observations',()=>{
 const f=fixture().illustration();f.rows([row(1,'1104','TEST-1104-A'),row(2,'','TEST-1104-B')]);const first=f.call();
 f.viewport.scrollTop=100;f.rows([row(2,'','TEST-1104-B')]);const before=f.call();assert.equal(before.loadedCount,2);
 f.layers[0].pixels[f.layers[0].pixels.length-2]=4;
 assert.ok(f.call('diagram',{partId:first.parts[0].id,snapshotId:before.snapshotId}).error);assert.equal(f.exports,0);
 const changed=f.call();assert.notEqual(changed.diagramSourceId,before.diagramSourceId);assert.notEqual(changed.snapshotId,before.snapshotId);assert.equal(changed.loadedCount,1);
 assert.equal(changed.parts[0].requiresCatalogReview,true,'Previous illustration headings must not make a new continuation selectable');
});

test('changing an excluded selection overlay cannot add a false selected-part highlight',()=>{
 const f=fixture().illustration();f.rows([row(1,'1104','TEST-1104-A')]);const before=f.call();
 f.layers[1].pixels[0]=80;f.layers[2].pixels[0]=20;const after=f.call();assert.equal(before.diagramSourceId,after.diagramSourceId);
 const result=f.call('diagram',{partId:after.parts[0].id,snapshotId:after.snapshotId});assert.equal(result.diagram.status,'available');assert.ok(f.drawSources.every(source=>source===f.layers[0]));
});

test('viewer topology, geometry, sibling grid and visibility must match observed catalog evidence',()=>{
 for(const change of ['one-layer','duplicate-z','position','different-parent','different-size','different-rect','hidden-base','hidden-auxiliary-only','unrelated-grid','unrelated-panel','blank','solid-white']){
  const f=fixture().illustration();f.rows([row(1,'1104','TEST-1104-A')]);
  if(change==='one-layer')f.viewer.queries.canvas=[f.layers[0]];
  if(change==='duplicate-z')f.layers[1].style.zIndex='0';if(change==='position')f.layers[0].style.position='static';
  if(change==='different-parent')f.layers[1].parentElement=new Element();if(change==='different-size')f.layers[1].width=7;
  if(change==='different-rect')f.layers[1].rect={left:4,top:0,width:8,height:6};if(change==='hidden-base')f.layers[0].hidden=true;
  if(change==='hidden-auxiliary-only'){const hidden=new Element('',{id:'hiddenDiv'});f.layers.forEach(el=>el.parentElement=hidden);}
  if(change==='unrelated-grid')f.grid.parentElement=new Element();if(change==='unrelated-panel')f.partsPanel.parentElement=new Element();
  if(change==='blank')f.layers[0].pixels.fill(0);if(change==='solid-white')f.layers[0].pixels.fill(255);
  const snapshot=f.call(),result=f.call('diagram',{partId:snapshot.parts[0].id,snapshotId:snapshot.snapshotId});
  assert.equal(result.diagram.status,'catalog',change);assert.equal(result.diagram.dataUrl,undefined,change);assert.equal(result.diagram.callout,undefined,change);assert.equal(f.exports,0,change);
 }
});

test('hidden auxiliary canvases never replace the actual visible base drawing',()=>{
 const f=fixture().illustration();const auxiliary=new Element();Object.assign(auxiliary,{parentElement:new Element('',{id:'hiddenDiv'}),width:8,height:6,style:{zIndex:'0',position:'absolute'}});f.viewer.queries.canvas.push(auxiliary);
 f.rows([row(1,'1104','TEST-1104-A')]);const s=f.call(),result=f.call('diagram',{partId:s.parts[0].id,snapshotId:s.snapshotId});assert.equal(result.diagram.status,'available');assert.ok(f.drawSources.every(source=>source===f.layers[0]));
});

test('pixel read restrictions, oversize canvases and unsafe PNG exports fall back without exposing URLs',()=>{
 for(const change of ['tainted','wide','many-pixels','export-error','large-export','wrong-format','empty-export']){
  const f=fixture().illustration();f.rows([row(1,'1104','TEST-1104-A')]);
  if(change==='tainted')f.tainted=true;if(change==='wide')f.layers.forEach(el=>el.width=4097);if(change==='many-pixels')f.layers.forEach(el=>{el.width=2000;el.height=1001;});
  if(change==='export-error')f.exportError=true;if(change==='large-export')f.dataUrl='data:image/png;base64,'+'A'.repeat(1500000);if(change==='wrong-format')f.dataUrl='https://example.test/guessed-diagram.png';if(change==='empty-export')f.dataUrl='data:image/png;base64,';
  const s=f.call(),result=f.call('diagram',{partId:s.parts[0].id,snapshotId:s.snapshotId});assert.equal(result.diagram.status,'catalog',change);assert.equal(result.diagram.dataUrl,undefined,change);assert.equal(result.diagram.callout,undefined,change);
  assert.ok(JSON.stringify(result).length<5000,change);
 }
});

test('known drawing fingerprint survives busy polling without losing the complete observation',()=>{
 const f=fixture().illustration();f.rows([row(1,'1104','TEST-1104-A',{APPLICATION:'Left'})]);const first=f.call();
 f.busy=true;f.rows([row(1,'1104','TEST-1104-A',{APPLICATION:'Loading'})]);f.call();f.busy=false;f.rows([]);const restored=f.call();
 assert.equal(restored.parts.length,1);assert.equal(restored.parts[0].application,'Left');assert.equal(restored.diagramSourceId,first.diagramSourceId);assert.equal(restored.parts[0].visible,false);
});


test('moving the actual drawing changes its source fingerprint and invalidates pending preview',()=>{
 const f=fixture().illustration();f.rows([row(1,'1104','TEST-1104-A')]);const before=f.call();
 f.layers.forEach(el=>{el.rect={left:12,top:6,width:8,height:6};});const after=f.call();
 assert.notEqual(after.diagramSourceId,before.diagramSourceId);assert.notEqual(after.snapshotId,before.snapshotId);
 assert.ok(f.call('diagram',{partId:before.parts[0].id,snapshotId:before.snapshotId}).error);assert.equal(f.exports,0);
});
