import test from 'node:test';
import assert from 'node:assert/strict';
import {selectedPartsList} from '../dist/jobs.js';
const vin='1M8GDM9AXKP042788';
const row=(number,qty=1,extra={})=>({qty,bases:['1104'],name:'Hub',note:'Do not copy private note',selection:{serviceNumber:number,vin,base:'1104',stale:false,...extra}});
test('parts-only clipboard combines duplicate numbers and excludes VIN and notes',()=>{
 const result=selectedPartsList({vin,job:'Private RO',rows:[row('TEST-1104-A',2),row('TEST-1104-A',1),row('TEST-1104-B',4)]});
 assert.deepEqual(result,{text:'3 × TEST-1104-A\n4 × TEST-1104-B',count:2,omitted:0});assert.ok(!result.text.includes(vin));assert.ok(!result.text.includes('Private'));
});
test('parts-only clipboard omits stale, wrong-vehicle, unrelated and unresolved items',()=>{
 const rows=[row('TEST-1104-A',1,{stale:true}),row('TEST-1104-B',1,{vin:'1M8GDM9A0KP042788'}),row('TEST-1104-C',1,{base:'6731'}),{qty:1,bases:['1104']},row('TEST-1104-D')];
 assert.deepEqual(selectedPartsList({vin,rows}),{text:'1 × TEST-1104-D',count:1,omitted:4});
 assert.equal(selectedPartsList({vin:'',rows}).count,0);
});
test('malformed selections and invalid quantities cannot enter the clipboard handoff',()=>{
 assert.deepEqual(selectedPartsList({vin,rows:[row('=FORMULA'),row('TEST-1104-A',0),row('TEST-1104-B',1.5)]}),{text:'',count:0,omitted:3});
});

test('rapid native launches cannot replace the active request and stale close events cannot unlock it',async()=>{
 const {createEpcLaunchGate}=await import('../dist/jobs.js'),gate=createEpcLaunchGate();
 assert.equal(gate.begin('first'),true);assert.equal(gate.begin('second'),false);assert.equal(gate.active(),'first');
 assert.equal(gate.finish('old'),false);assert.equal(gate.active(),'first');assert.equal(gate.finish('first'),true);
 assert.equal(gate.begin('second'),true);assert.equal(gate.finish('second'),true);assert.equal(gate.active(),null);
});
