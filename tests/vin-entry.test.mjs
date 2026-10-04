import test from 'node:test';
import assert from 'node:assert/strict';
import {analyzeVin,analyzeVinEntry,savedVinMatches} from '../dist/vin.js';
import {createVinEntry,openVinDecoder} from '../dist/vin-ui.js';
import {attachSelection,selectedPartsList} from '../dist/jobs.js';
const VIN='1M8GDM9AXKP042788',OTHER='2M8GDM9A0KP042788',SUFFIX=VIN.slice(-8);
const escape=value=>String(value).replace(/[<>]/g,c=>c==='<'?'&lt;':'&gt;');

test('VIN suffix acceptance is separate from complete VIN identity',()=>{
 assert.equal(analyzeVinEntry(' kp 04-2788 ').kind,'suffix');
 assert.equal(analyzeVinEntry(VIN).kind,'full');
 for(const value of ['','1234567','123456789','KP0I2788','KP0O2788','KP0Q2788'])assert.equal(analyzeVinEntry(value).valid,false,value);
 assert.equal(analyzeVin(SUFFIX).valid,false);
 const job={vin:SUFFIX,rows:[{id:'hub',bases:['1104'],qty:1}]};
 assert.throws(()=>attachSelection(job,'hub',{vin:SUFFIX,base:'1104',serviceNumber:'TEST-1104-A'}));
 assert.equal(selectedPartsList({...job,rows:[{qty:1,bases:['1104'],selection:{vin:SUFFIX,base:'1104',serviceNumber:'TEST-1104-A'}}]}).count,0);
});

test('saved suffix matching deduplicates exact full VINs, never guesses or selects a winner',()=>{
 const matches=savedVinMatches(SUFFIX,[{vin:VIN,title:'Vehicle A'},{vin:VIN.toLowerCase(),vehicle:'Same A'},{vin:OTHER,job:'Vehicle B'},{vin:SUFFIX,title:'Not a full VIN'},{vin:'1M8GDM9AXKP042789'},null]);
 assert.deepEqual(matches,[{vin:VIN,title:'Vehicle A'},{vin:OTHER,title:'Vehicle B'}]);
 assert.deepEqual(savedVinMatches('ZZ123456',[{vin:VIN}]),[]);
 assert.deepEqual(savedVinMatches(VIN,[{vin:VIN}]),[]);
});

function mount(t,{records=[{vin:VIN,title:'Vehicle A'}],native=false}={}){
 const previous={window:globalThis.window,navigator:globalThis.navigator};
 t.after(()=>{for(const [key,value]of Object.entries(previous))Object.defineProperty(globalThis,key,{value,configurable:true,writable:true});});
 const listeners=new Map(),calls=[],confirmed=[];
 globalThis.window={addEventListener:(name,handler)=>listeners.set(name,handler),removeEventListener:(name,handler)=>{if(listeners.get(name)===handler)listeners.delete(name);},PartsNative:native?{resolveVin:(suffix,requestId)=>calls.push({suffix,requestId})}:undefined};
 Object.defineProperty(globalThis,'navigator',{value:{onLine:true},configurable:true,writable:true});
 const input={value:SUFFIX,isConnected:true},choices={innerHTML:''};
 const entry=createVinEntry({input,choices,esc:escape,getRecords:()=>records,onConfirmed:value=>confirmed.push(value)});
 const click=(selector,dataset)=>choices.onclick({target:{closest:value=>value===selector?{dataset}:null}});
 return {input,choices,entry,calls,confirmed,records,click,listeners,reply:result=>listeners.get('parts-vin-resolved')?.({detail:result})};
}

test('single and ambiguous saved matches require explicit, still-current confirmation',t=>{
 const ui=mount(t);assert.throws(()=>ui.entry.requireFull(),/Confirm/);assert.equal(ui.input.value,SUFFIX);
 ui.records.push({vin:OTHER,title:'<Another vehicle>'});ui.entry.render();
 assert.match(ui.choices.innerHTML,/More than one/);assert.match(ui.choices.innerHTML,/&lt;Another vehicle&gt;/);
 ui.click('[data-confirm-vin]',{confirmVin:OTHER,vinSuffix:SUFFIX});
 assert.equal(ui.input.value,OTHER);assert.equal(ui.entry.requireFull().vin,OTHER);assert.equal(ui.confirmed.length,1);
 ui.input.value='ZZ123456';ui.click('[data-confirm-vin]',{confirmVin:VIN,vinSuffix:SUFFIX});assert.equal(ui.input.value,'ZZ123456');
});

test('offline lookup only uses saved confirmed full VINs',t=>{
 const ui=mount(t,{records:[],native:true});navigator.onLine=false;
 ui.click('[data-resolve-vin]',{resolveVin:SUFFIX});assert.equal(ui.calls.length,0);assert.match(ui.choices.innerHTML,/Offline/);
 assert.throws(()=>ui.entry.requireFull(),/No saved full VIN/);
});

test('native suffix resolution is request-bound and must return a confirmed matching full VIN',t=>{
 const ui=mount(t,{records:[],native:true});ui.click('[data-resolve-vin]',{resolveVin:SUFFIX});ui.click('[data-resolve-vin]',{resolveVin:SUFFIX});
 assert.equal(ui.calls.length,1);const {requestId}=ui.calls[0];
 ui.reply({requestId:'unrelated',suffix:SUFFIX,vin:VIN,confirmed:true});assert.equal(ui.input.value,SUFFIX);
 ui.reply({requestId,suffix:SUFFIX,vin:VIN,vehicle:'Catalog vehicle',confirmed:true});
 assert.equal(ui.input.value,VIN);assert.equal(ui.confirmed.length,1);assert.equal(ui.listeners.size,0);
});

test('native cancellation, unconfirmed or wrong suffix cannot replace entry',t=>{
 const ui=mount(t,{records:[],native:true});
 for(const response of [{error:'Cancelled'},{vin:VIN,confirmed:false},{vin:'1M8GDM9AXZZ123456',confirmed:true}]){
  ui.click('[data-resolve-vin]',{resolveVin:SUFFIX});ui.reply({...response,requestId:ui.calls.at(-1).requestId,suffix:SUFFIX});
  assert.equal(ui.input.value,SUFFIX);assert.equal(ui.confirmed.length,0);
 }
});

test('late native results cannot overwrite a changed or dismissed entry',t=>{
 const ui=mount(t,{records:[],native:true});ui.click('[data-resolve-vin]',{resolveVin:SUFFIX});
 ui.input.value='ZZ123456';ui.reply({...ui.calls[0],vin:VIN,confirmed:true});assert.equal(ui.input.value,'ZZ123456');
 ui.input.value=SUFFIX;ui.click('[data-resolve-vin]',{resolveVin:SUFFIX});ui.input.isConnected=false;
 ui.reply({...ui.calls.at(-1),vin:VIN,confirmed:true});assert.equal(ui.input.value,SUFFIX);assert.equal(ui.confirmed.length,0);
});

test('closed but connected dialogs retain native results only for later explicit recovery',t=>{
 const ui=mount(t,{records:[],native:true}),dialog={open:true};ui.input.closest=()=>dialog;
 ui.click('[data-resolve-vin]',{resolveVin:SUFFIX});dialog.open=false;
 ui.reply({...ui.calls[0],vin:VIN,confirmed:true});assert.equal(ui.input.value,SUFFIX);assert.equal(ui.confirmed.length,0);
});

test('editing A to B to A during native lookup invalidates its original input generation',t=>{
 const ui=mount(t,{records:[],native:true});ui.click('[data-resolve-vin]',{resolveVin:SUFFIX});
 ui.input.value='ZZ123456';ui.entry.render();ui.input.value=SUFFIX;ui.entry.render();
 ui.reply({...ui.calls[0],vin:VIN,confirmed:true});assert.equal(ui.input.value,SUFFIX);assert.equal(ui.confirmed.length,0);
 assert.doesNotMatch(ui.choices.innerHTML,/Resolving vehicle/);
});

test('recovered mailbox results remain choices until explicit confirmation and ack uses exact request ID',t=>{
 const ui=mount(t,{records:[],native:true}),acknowledged=[];
 window.PartsNative.getVinResolution=()=>JSON.stringify({requestId:'recovered-request',suffix:SUFFIX,vin:VIN,vehicle:'Recovered vehicle',confirmed:true});
 window.PartsNative.acknowledgeVinResolution=id=>acknowledged.push(id);ui.entry.render();
 assert.equal(ui.input.value,SUFFIX);assert.equal(ui.confirmed.length,0);assert.deepEqual(acknowledged,[]);
 ui.click('[data-confirm-vin]',{confirmVin:VIN,vinSuffix:SUFFIX});assert.equal(ui.input.value,VIN);assert.deepEqual(acknowledged,['recovered-request']);
});
