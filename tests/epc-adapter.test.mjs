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
 closest(){return null;}
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
 const state={grid,vin,filter,viewport,crumb,find,search,vinInput,partInput,busy:false,modal:null,login:false,hasGrid:true};
 const doc={querySelector:s=>s==='#toolbar-vin-url-anchor'?vin:s.includes('Toggle VIN')?filter:s==='#equipmentEntryInputId'?vinInput:s==='#partEntryInputId'?partInput:null,
 querySelectorAll:s=>s==='[role="grid"]'?(state.hasGrid?[grid]:[]):s==='ul.breadcrumb [role="menuitem"] > a.p-menuitem-link[href="#"]'?[crumb]:s==='button'?[find,search]:s==='input[type="password"]'?(state.login?[new Input()]:[]):s==='[role="dialog"],[role="alertdialog"]'?(state.modal?[new Element(state.modal)]:[]):s.startsWith('[aria-busy=')?(state.busy?[new Element()]:[]):[]};
 const context={window:{},document:doc,getComputedStyle:()=>({visibility:'visible'}),HTMLInputElement:Input,Event:class {constructor(type){this.type=type;}}};vm.createContext(context);
 state.call=(command='snapshot',extra={})=>{vm.runInContext(adapter,context);return JSON.parse(JSON.stringify(context.partsDeskEpc(command,{vin:VIN,base:'1104',requestId:'lookup-1',...extra})));};
 state.rows=rows=>{grid.queries['[role="row"]']=rows;};return state;
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
