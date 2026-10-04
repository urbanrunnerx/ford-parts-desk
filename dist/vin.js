export const normalizeVin=input=>String(input??'').trim().toUpperCase().replace(/[\s-]/g,'');
// Entry validation is intentionally separate from full vehicle identity. A
// suffix can find saved vehicles; it must never satisfy fitment/result guards.
export function analyzeVinEntry(input){
 const vin=normalizeVin(input);
 if(/^[A-HJ-NPR-Z0-9]{8}$/.test(vin))return {vin,valid:true,kind:'suffix'};
 const full=analyzeVin(vin);
 return full.valid?{...full,kind:'full'}:{vin,valid:false,kind:'invalid',error:'Enter the full 17-character VIN or its last 8 characters. VINs do not use I, O or Q.'};
}
export function savedVinMatches(input,records=[]){
 const entry=analyzeVinEntry(input);if(entry.kind!=='suffix')return [];
 const matches=new Map();
 for(const record of Array.isArray(records)?records:[]){
  const full=analyzeVin(record?.vin);if(!full.valid||!full.vin.endsWith(entry.vin))continue;
  const title=String(record.title||record.vehicle||record.job||'Saved vehicle');
  if(!matches.has(full.vin))matches.set(full.vin,{vin:full.vin,title});
 }
 return [...matches.values()];
}
export function analyzeVin(input){
 const vin=normalizeVin(input);
 if(!/^[A-HJ-NPR-Z0-9]{17}$/.test(vin))return {vin,valid:false,error:'Enter a complete 17-character VIN. VINs do not use I, O or Q. Older, nonstandard VINs need the original vehicle catalog.'};
 const values={A:1,B:2,C:3,D:4,E:5,F:6,G:7,H:8,J:1,K:2,L:3,M:4,N:5,P:7,R:9,S:2,T:3,U:4,V:5,W:6,X:7,Y:8,Z:9};
 const weights=[8,7,6,5,4,3,2,10,0,9,8,7,6,5,4,3,2];
 const remainder=[...vin].reduce((sum,c,i)=>sum+(values[c]??Number(c))*weights[i],0)%11;
 const expected=remainder===10?'X':String(remainder);
 return {vin,valid:true,checksumValid:vin[8]===expected,expected,warning:vin[8]===expected?'':'The check digit does not match the North American VIN formula. Recheck the VIN; some other markets use different rules.'};
}
const fields=[['ModelYear','Model year'],['Make','Make'],['Model','Model'],['Series','Series'],['Trim','Trim'],['BodyClass','Body style'],['DriveType','Drive'],['EngineModel','Engine model'],['DisplacementL','Engine liters'],['EngineCylinders','Cylinders'],['FuelTypePrimary','Primary fuel'],['FuelTypeSecondary','Secondary fuel'],['ElectrificationLevel','Electrification'],['TransmissionStyle','Transmission'],['TransmissionSpeeds','Transmission speeds'],['GVWR','GVWR class'],['BrakeSystemType','Brake system'],['Doors','Doors'],['CabType','Cab type'],['PlantCity','Assembly city'],['PlantState','Assembly state'],['PlantCountry','Assembly country']];
export function decodeReport(payload,vin){
 const r=payload?.Results?.[0];if(!r||typeof r!=='object')throw Error('NHTSA returned an unexpected response. Try again.');
 const codes=String(r.ErrorCode??'').split(',').map(x=>x.trim()).filter(Boolean),warning=codes.some(c=>c!=='0');
 const populated=fields.filter(([key])=>r[key]&&r[key]!=='Not Applicable'&&r[key]!=='0').map(([key,label])=>({key,label,value:String(r[key])}));
 if(!populated.some(x=>['Make','Model','ModelYear'].includes(x.key)))throw Error(r.ErrorText||'No usable vehicle identity was returned. Confirm the VIN and try a model-year hint.');
 return {vin,title:[r.ModelYear,r.Make,r.Model].filter(Boolean).join(' '),fields:populated,warning,errorCode:String(r.ErrorCode??''),errorText:String(r.ErrorText||''),suggestedVin:String(r.SuggestedVIN||''),decodedAt:new Date().toISOString(),source:'https://vpic.nhtsa.dot.gov/'};
}
export const vinURL=(vin,year='')=>'https://vpic.nhtsa.dot.gov/api/vehicles/DecodeVinValuesExtended/'+encodeURIComponent(vin)+'?format=json'+(year?'&modelyear='+encodeURIComponent(year):'');
