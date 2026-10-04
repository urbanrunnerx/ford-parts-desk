import test from 'node:test';
import assert from 'node:assert/strict';
import {createCounter} from '../dist/counter-ui.js';
import {validateJobs} from '../dist/jobs.js';

const VIN='1M8GDM9AXKP042788';

// Exercise the real counter's launch handlers. Only the DOM and native bridge
// are doubled; Android rejection delivery is covered by instrumentation tests.
function mount(t,{bases=['1104'],openEpc,savePending}={}){
 const previous={window:globalThis.window,document:globalThis.document};
 t.after(()=>{for(const [key,value]of Object.entries(previous))if(value===undefined)delete globalThis[key];else globalThis[key]=value;});
 const nodes=new Map(),listeners=new Map(),storage=new Map(),writes=[],calls=[],messages=[];
 const node=selector=>{if(!nodes.has(selector))nodes.set(selector,{value:'',textContent:''});return nodes.get(selector);};
 let job={id:'fixture-job',job:'Counter launch fixture',vin:VIN,rows:[{id:'fixture-row',name:'Hub',bases,qty:1,note:''}]};
 globalThis.document={querySelector:node};
 globalThis.window={
  addEventListener(type,callback){listeners.set(type,callback);},
  PartsNative:{openEpc(raw){const payload=JSON.parse(raw);calls.push(payload);openEpc?.(payload);}},
 };
 const counter=createCounter({
  esc:String,panel(){},toast(message){messages.push(message);},copy(){},download(){},
  read:(key,fallback)=>storage.get(key)??fallback,
  save(key,value){writes.push({key,value:structuredClone(value)});if(key==='epc-pending'&&savePending&&!savePending(value))return false;storage.set(key,structuredClone(value));return true;},
  getJob:()=>job,getJobs:()=>({activeId:job.id,items:[job]}),
  writeJob(next){job=next;return true;},getIndex:()=>({byBase:new Map()}),
 });
 return {
  calls,messages,writes,storage,node,get job(){return job;},
  launch(base='1104'){
   counter.openLookup('fixture-row');node('#lookupVin').value=VIN;node('#lookupBase').value=base;node('#launchEpc').onclick();
  },
  close(requestId){listeners.get('parts-epc-closed')?.({detail:{requestId}});},
  launchFailed(){listeners.get('parts-epc-launch-failed')?.({});},
 };
}

test('empty-base imported row is rejected without locking subsequent valid lookups',t=>{
 const ui=mount(t,{bases:[]});
 assert.throws(()=>validateJobs([ui.job]),/Invalid job item/);
 ui.launch('');
 assert.equal(ui.calls.length,0);
 assert.equal(ui.storage.has('epc-pending'),false);
 assert.match(ui.node('#lookupStatus').textContent,/valid base/);
 ui.job.rows[0].bases=['1104'];ui.launch();
 assert.equal(ui.calls.length,1);
 assert.deepEqual(ui.calls[0].bases,['1104']);
 assert.equal(ui.storage.get('epc-pending').id,ui.calls[0].requestId);
});

test('unrelated selected base is rejected before reserving the native launch',t=>{
 const ui=mount(t);ui.launch('6731');
 assert.equal(ui.calls.length,0);assert.equal(ui.writes.length,0);
 assert.match(ui.node('#lookupStatus').textContent,/valid base/);
 ui.launch();assert.equal(ui.calls.length,1);
});

test('invalid family base cannot strand the gate even when the chosen base is valid',t=>{
 const ui=mount(t,{bases:['1104','']});ui.launch();
 assert.equal(ui.calls.length,0);assert.equal(ui.writes.length,0);
 assert.match(ui.messages.at(-1),/valid vehicle and a base/);
 ui.job.rows[0].bases=['1104'];ui.launch();assert.equal(ui.calls.length,1);
});

test('long imported row names are capped before the native payload size guard',t=>{
 const ui=mount(t,{openEpc(payload){assert.ok(JSON.stringify(payload).length<=16000,'Native must be able to parse and acknowledge this request');}});
 const name='Imported hub '.repeat(2000);ui.job.rows[0].name=name;
 assert.equal(validateJobs([ui.job]),true);
 ui.launch();assert.equal(ui.calls.length,1);
 assert.equal(ui.calls[0].partName,name.slice(0,160));
 assert.equal(ui.job.rows[0].name,name,'The display-title cap must not change the saved job');
 ui.close(ui.calls[0].requestId);ui.launch();assert.equal(ui.calls.length,2);
});

test('duplicate taps preserve request routing until its own native close event arrives',t=>{
 const ui=mount(t);ui.launch();const first=ui.calls[0],pending=structuredClone(ui.storage.get('epc-pending'));
 ui.launch();ui.close('unrelated-request');ui.launchFailed();ui.launch();
 assert.equal(ui.calls.length,1);assert.deepEqual(ui.storage.get('epc-pending'),pending);
 assert.equal(ui.writes.length,1);
 ui.close(first.requestId);ui.launch();
 assert.equal(ui.calls.length,2);assert.notEqual(ui.calls[1].requestId,first.requestId);
 assert.equal(ui.storage.get('epc-pending').id,ui.calls[1].requestId);
 ui.close(first.requestId);ui.launch();assert.equal(ui.calls.length,2);
});

test('failed pending-request persistence releases the gate without calling native',t=>{
 let available=false;const ui=mount(t,{savePending:()=>available});ui.launch();
 assert.equal(ui.calls.length,0);assert.equal(ui.storage.has('epc-pending'),false);
 available=true;ui.launch();assert.equal(ui.calls.length,1);
});

test('synchronous native launch failure clears pending state and permits retry',t=>{
 let fail=true;const ui=mount(t,{openEpc(){if(fail)throw Error('Synthetic bridge failure');}});ui.launch();
 assert.equal(ui.calls.length,1);assert.equal(ui.storage.get('epc-pending'),null);
 assert.match(ui.messages.at(-1),/could not open/);
 fail=false;ui.launch();assert.equal(ui.calls.length,2);
 assert.equal(ui.storage.get('epc-pending').id,ui.calls[1].requestId);
});
