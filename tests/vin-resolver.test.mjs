import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
const adapter=fs.readFileSync(new URL('../dist/vin-resolver.js',import.meta.url),'utf8');
const VIN='1M8GDM9AXKP042788',OTHER='2M8GDM9A0KP042788',SUFFIX=VIN.slice(-8),BEFORE='1M8GDM9AXZZ123456';
class Element {
 constructor(text='',attrs={}){this.innerText=text;this.attrs=attrs;this.childElementCount=0;this.events=[];}
 getClientRects(){return this.hidden?[]:[{}];}getAttribute(key){return this.attrs[key]??null;}closest(){return null;}
 dispatchEvent(event){this.events.push(event.type);}click(){this.onClick?.();}
}
class Input extends Element {get value(){return this._value||'';}set value(value){this._value=value;}}
function fixture({vin=BEFORE,loading=true}={}){
 const observers=new Set(),toolbar=new Element(vin),filter={checked:true},input=new Input(),button=new Element('Find VIN'),hint=new Element('',{title:'ENG: synthetic engine | Fixture vehicle'});
 const state={toolbar,filter,input,button,hint,busy:false,modal:null,login:false};
 button.onClick=()=>{state.busy=loading;};
 const document={documentElement:{},querySelector:selector=>selector==='#toolbar-vin-url-anchor'?toolbar:selector.includes('Toggle VIN filters')?filter:selector==='#equipmentEntryInputId'?input:null,
  querySelectorAll:selector=>selector==='[title]'?[hint]:selector==='button'?[button]:selector==='input[type="password"]'?(state.login?[new Input()]:[]):selector==='[role="dialog"],[role="alertdialog"]'?(state.modal?[new Element(state.modal)]:[]):selector.startsWith('[aria-busy=')?(state.busy?[new Element()]:[]):[]};
 const context={window:{},document,HTMLInputElement:Input,Event:class{constructor(type){this.type=type;}},getComputedStyle:()=>({visibility:'visible'}),MutationObserver:class{constructor(callback){this.callback=callback;}observe(){observers.add(this.callback);}disconnect(){observers.delete(this.callback);}}};vm.createContext(context);
 state.notify=records=>{for(const observer of [...observers])observer(records);};
 state.update=update=>{update(state);for(const observer of [...observers])observer();};
 state.call=(command='snapshot',extra={})=>{vm.runInContext(adapter,context);return JSON.parse(JSON.stringify(context.partsDeskVinResolve(command,{requestId:'fixture-request',suffix:SUFFIX,searchId:'search-1',...extra})));};
 state.resolve=()=>{state.call('search');state.update(s=>{s.busy=false;s.toolbar.innerText=VIN;});return state.call();};
 state.context=context;return state;
}

test('suffix search only changes the VIN field and requires explicit fresh confirmation',()=>{
 const page=fixture();const search=page.call('search');assert.equal(search.accepted,true);assert.equal(page.input.value,SUFFIX);assert.deepEqual(page.input.events,['input','change']);
 assert.equal(page.call().stage,'loading');page.update(s=>{s.busy=false;s.toolbar.innerText=VIN;});
 const candidate=page.call();assert.equal(candidate.stage,'resolved');assert.equal(candidate.vin,VIN);assert.equal(candidate.filtersOn,true);assert.equal(candidate.confirmed,undefined);
 const result=page.call('confirm',{vin:VIN,identityId:candidate.identityId});assert.equal(result.confirmed,true);assert.equal(result.requestId,'fixture-request');
 assert.equal(page.call('confirm',{vin:VIN,identityId:candidate.identityId}).confirmed,false);
});

test('an already matching warm toolbar without a new search cycle is not resolution',()=>{
 const page=fixture({vin:VIN,loading:false});assert.equal(page.call().stage,'idle');page.call('search');
 assert.equal(page.call().stage,'loading');assert.equal(page.call('confirm',{vin:VIN,identityId:'search-1-1'}).confirmed,false);
});

test('known no-match stays terminal after dismissing modal over an old matching VIN',()=>{
 const page=fixture({vin:VIN});page.call('search');page.update(s=>{s.busy=false;s.modal='VIN Search Results VIN search results for: '+SUFFIX+' VIN was not found';});
 assert.equal(page.call().stage,'no-match');page.update(s=>{s.modal=null;});
 const closed=page.call();assert.equal(closed.stage,'no-match');assert.equal(closed.ready,false);
 assert.equal(page.call('confirm',{vin:VIN,identityId:closed.identityId}).confirmed,false);
 page.call('search',{searchId:'search-2'});page.update(s=>{s.busy=false;});assert.equal(page.call('snapshot',{searchId:'search-2'}).stage,'resolved');
});

test('ambiguous results dismissal does not reuse the previous matching vehicle',()=>{
 const page=fixture({vin:VIN});page.call('search');page.update(s=>{s.busy=false;s.modal='VIN Search Results Two matching vehicles';});
 assert.equal(page.call().stage,'catalog');page.update(s=>{s.modal=null;});
 assert.equal(page.call().stage,'vehicle-needed');
 page.update(s=>{s.toolbar.innerText=OTHER;});const selected=page.call();assert.equal(selected.stage,'resolved');assert.equal(selected.vin,OTHER);
});

test('a visible failure dialog wins over its stale Loading overlay and toolbar',()=>{
 const page=fixture({vin:VIN});page.call('search');page.update(s=>{s.modal='VIN Search Results VIN was not found';s.busy=true;});
 assert.equal(page.call().stage,'no-match');
});

test('confirmed identity token expires after vehicle changes even if it returns between polls',()=>{
 const page=fixture();const old=page.resolve();page.update(s=>{s.toolbar.innerText=OTHER;});page.update(s=>{s.toolbar.innerText=VIN;});
 assert.equal(page.call('confirm',{vin:VIN,identityId:old.identityId}).confirmed,false);
 const fresh=page.call();assert.notEqual(fresh.identityId,old.identityId);assert.equal(page.call('confirm',{vin:VIN,identityId:fresh.identityId}).confirmed,true);
});

test('filter, details and modal changes require reviewing a fresh identity',()=>{
 for(const update of [s=>{s.filter.checked=false;},s=>{s.hint.attrs.title='ENG: different vehicle options';},s=>{s.modal='Session expired';}]){
  const page=fixture();const candidate=page.resolve();page.update(update);
  assert.equal(page.call('confirm',{vin:VIN,identityId:candidate.identityId}).confirmed,false);
 }
});

test('wrong suffix, partial toolbar and filters off never resolve',()=>{
 const page=fixture();page.call('search');page.update(s=>{s.busy=false;s.toolbar.innerText='KP042788';});assert.equal(page.call().ready,false);
 page.update(s=>{s.toolbar.innerText=BEFORE;});assert.equal(page.call().ready,false);
 page.update(s=>{s.toolbar.innerText=VIN;s.filter.checked=false;});assert.equal(page.call().stage,'filters');
});

test('login and search interruptions cannot authorize a cached vehicle',()=>{
 const page=fixture({vin:VIN});page.login=true;assert.equal(page.call('search').stage,'login');assert.equal(page.call().ready,false);
 page.login=false;page.modal='Choose catalog';assert.equal(page.call('search').accepted,undefined);
 page.modal=null;page.call('search');page.update(s=>{s.busy=false;});assert.equal(page.call().ready,true);
 page.modal='Choose a catalog';page.call('search',{searchId:'search-2'});page.modal=null;
 assert.equal(page.call('snapshot',{searchId:'search-1'}).ready,false);
});

test('new search IDs, cancellation and page recreation invalidate old candidates',()=>{
 const page=fixture();const old=page.resolve();assert.equal(page.call('confirm',{searchId:'other-search',vin:VIN,identityId:old.identityId}).confirmed,false);
 page.call('cancel');assert.equal(page.call().stage,'idle');assert.equal(page.call('confirm',{vin:VIN,identityId:old.identityId}).confirmed,false);
 page.resolve();page.context.window={};assert.equal(page.call().stage,'idle');
});

test('invalid request or suffix has no side effects',()=>{
 const page=fixture();for(const extra of [{suffix:'INVALID'}, {suffix:'KP0I2788'},{requestId:''},{searchId:''},{suffix:VIN}])assert.equal(page.call('search',extra).stage,'invalid');
 assert.equal(page.input.value,'');assert.equal(page.input.events.length,0);
});


test('same-task identity mutations invalidate confirmation even when final text is unchanged',()=>{
 const page=fixture(),candidate=page.resolve();page.notify([{target:page.toolbar,addedNodes:[],removedNodes:[]}]);
 assert.equal(page.call('confirm',{vin:VIN,identityId:candidate.identityId}).confirmed,false);
});

test('a transient no-match modal removed before the next poll still invalidates the search',()=>{
 const page=fixture({vin:VIN});page.call('search');page.update(s=>{s.busy=false;});
 page.notify([{target:{},addedNodes:[],removedNodes:[new Element('VIN Search Results VIN was not found',{role:'dialog'})]}]);
 assert.equal(page.call().stage,'no-match');
});
