export const normalize = text => String(text ?? '').toLowerCase().replace(/a\/c/g,'ac').replace(/vapour/g,'vapor').replace(/moulding/g,'molding').replace(/[^a-z0-9]+/g,' ').trim();
export const validBase = value => /^[0-9][A-Z0-9]{3,7}$/.test(value);
const synonyms = {aircon:'ac',windscreen:'windshield',bonnet:'hood',tyre:'tire',stabiliser:'stabilizer',petrol:'gasoline',trans:'transmission',light:'lamp',lights:'lamp',lamps:'lamp'};
const stop = new Set('the a an for of part parts ford my on in with is that it need to assembly assy please find me i want number base'.split(' '));
const tokens = q => normalize(q).split(' ').filter(t=>t&&!stop.has(t)).map(t=>synonyms[t]||t);
export function hydrate(part){
 const title=normalize(part.name), aliases=(part.aliases||[]).map(normalize);
 return {...part, title, words:new Set(tokens([title,...aliases].join(' '))), codes:part.bases.map(b=>b.number), aliases};
}
export function createIndex(parts){
 const list=parts.map(hydrate), byBase=new Map(), byService=new Map();
 for(const p of list) for(const b of p.bases){
  byBase.set(b.number,p);
  for(const e of b.evidence||[]) for(const s of e.examples||[]) byService.set(s.replace(/[^A-Z0-9]/g,''),b.number);
 }
 return {parts:list,byBase,byService};
}
export function decode(query,index){
 const text=String(query).toUpperCase().trim().replace(/[–—]/g,'-');
 const compact=text.replace(/[^A-Z0-9]/g,'');
 if(index.byBase.has(compact))return null;
 const m=text.match(/^([A-Z0-9]{4}|M|CM)[ -]+([0-9][A-Z0-9]{3,7})[ -]+([A-Z0-9]{1,10})$/);
 if(m)return {prefix:m[1],base:m[2],suffix:m[3],known:index.byBase.has(m[2]),inferred:false};
 if(index.byService.has(compact)){
  const base=index.byService.get(compact);return {prefix:compact.slice(0,4),base,suffix:compact.slice(4+base.length),known:true,inferred:false};
 }
 if(/^[A-Z0-9]{4}[0-9][A-Z0-9]{4,17}$/.test(compact)){
  const tail=compact.slice(4), candidates=[...index.byBase.keys()].filter(b=>tail.startsWith(b)&&/^[A-Z][A-Z0-9]{0,9}$/.test(tail.slice(b.length)));
  if(candidates.length===1)return {prefix:compact.slice(0,4),base:candidates[0],suffix:tail.slice(candidates[0].length),known:true,inferred:true};
 }
 return null;
}
function near(a,b){
 if(a===b)return true;if(Math.abs(a.length-b.length)>1)return false;
 if(a.length===b.length){const mismatches=[...a].map((c,i)=>c===b[i]?-1:i).filter(i=>i>=0);if(mismatches.length===2&&mismatches[1]===mismatches[0]+1&&a[mismatches[0]]===b[mismatches[1]]&&a[mismatches[1]]===b[mismatches[0]])return true;}
 let i=0,j=0,edits=0;while(i<a.length&&j<b.length){if(a[i]===b[j]){i++;j++;continue;}if(++edits>1)return false;if(a.length>=b.length)i++;if(b.length>=a.length)j++;}return edits+(a.length-i)+(b.length-j)<=1;
}
export function search(query,index,{category='All systems',quality='all',saved=null,sort='relevance'}={}){
 const q=normalize(query), code=String(query).toUpperCase().replace(/[^A-Z0-9]/g,''), parsed=decode(query,index), qt=tokens(q);
 const candidates=index.parts.filter(p=>(category==='All systems'||p.category===category)&&(quality!=='detailed'||!p.broad)&&(!saved||saved.has(p.id)));
 let results=[];
 for(const p of candidates){
  let score=0,reason='Part family',matched=[];
  if(!q){score=p.broad?0:20;}
  else if(p.codes.includes(parsed?.base||code)){score=10000;reason='Exact base match';matched=[parsed?.base||code];}
  else if(parsed){continue;}
  else if(/^[0-9][A-Z0-9]*$/.test(code)&&p.codes.some(b=>b.startsWith(code))){score=5000;reason='Base starts with '+code;matched=p.codes.filter(b=>b.startsWith(code));}
  else if(qt.length){
   let fuzzy=false,hit=true,positionUnresolved=false;
   for(const t of qt){
    if(p.words.has(t))score+=60;
    else if(t.length>=3&&[...p.words].some(w=>w.startsWith(t)))score+=35;
    else if(t.length>=5&&[...p.words].some(w=>near(w,t))){score+=15;fuzzy=true;}
    else if(['left','right','front','rear','driver','passenger'].includes(t)){positionUnresolved=true;score-=25;}
    else {hit=false;break;}
   }
   if(!hit)continue;
   if(p.title===q||p.aliases.includes(q))score+=600;
   else if(p.title.includes(q))score+=300;
   score+=qt.filter(t=>p.title.split(' ').includes(t)).length*30;
   score-=p.broad?100:0;
   if(!qt.some(t=>!['left','right','front','rear','driver','passenger'].includes(t)))continue;
   reason=positionUnresolved?'Family match · confirm position in EPC':fuzzy?'Similar wording':'Name or synonym match';
  }else continue;
  results.push({part:p,score,reason,matched});
 }
 results.sort((a,b)=>(sort==='az'?0:b.score-a.score)||a.part.name.localeCompare(b.part.name)||a.part.category.localeCompare(b.part.category));
 return results;
}
export function parseCSV(input){
 let rows=[],row=[],field='',quoted=false;input=input.replace(/^\uFEFF/,'');
 for(let i=0;i<input.length;i++){const c=input[i];if(c==='"'){if(quoted&&input[i+1]==='"'){field+='"';i++;}else if(quoted||!field)quoted=!quoted;else throw Error('Unexpected quote in CSV.');}else if(c===','&&!quoted){row.push(field);field='';}else if((c==='\n'||c==='\r')&&!quoted){if(c==='\r'&&input[i+1]==='\n')i++;row.push(field);if(row.some(x=>x.trim()))rows.push(row);row=[];field='';}else field+=c;}
 if(quoted)throw Error('Unclosed quoted CSV field.');row.push(field);if(row.some(x=>x.trim()))rows.push(row);return rows;
}
export function importCSV(text){
 const rows=parseCSV(text);if(!rows.length)throw Error('The CSV is empty.');const headers=rows.shift().map(normalize);
 if(!headers.includes('base')||!headers.includes('name'))throw Error('The CSV needs base and name columns.');
 if(rows.length>50000)throw Error('Import at most 50,000 rows at a time.');
 return rows.map((r,i)=>{const x=Object.fromEntries(headers.map((h,j)=>[h,(r[j]||'').trim()]));x.base=x.base.toUpperCase();
 if(!validBase(x.base)||!x.name||x.name.length>200||x.name.startsWith('='))throw Error('Check the name and base on row '+(i+2)+'.');
 if(x.source&&!/^https?:\/\//i.test(x.source))throw Error('Source must be an HTTP or HTTPS link on row '+(i+2)+'.');
 return {base:x.base,name:x.name,category:x.category||'Body & interior',aliases:(x.aliases||'').split(';').filter(Boolean),source:x.source||''};});
}
export function mergeImports(parts,rows){
 const out=structuredClone(parts), byBase=new Map(out.flatMap(p=>p.bases.map(b=>[b.number,p]))), byName=new Map(out.map(p=>[normalize(p.name),p]));
 for(const r of rows){
  let p=byBase.get(r.base)||byName.get(normalize(r.name));
  if(!p){p={id:'custom-'+normalize(r.name).replace(/ /g,'-'),name:r.name,category:r.category,broad:false,aliases:[],note:'User imported reference; verify against the source.',bases:[]};out.push(p);byName.set(normalize(r.name),p);}
  p.aliases=[...new Set([...p.aliases,r.name,...r.aliases])];
  let b=p.bases.find(b=>b.number===r.base);if(!b){b={number:r.base,evidence:[]};p.bases.push(b);byBase.set(r.base,p);}
  b.evidence.push({source:'import',description:r.name,url:r.source});
 }
 return out;
}
export function toCSV(parts){
 const cell=s=>'"'+String(s??'').replace(/^[=+@-]/,"'$&").replace(/"/g,'""')+'"';
 return ['base,name,category,aliases',...parts.flatMap(p=>p.bases.map(b=>[b.number,p.name,p.category,(p.aliases||[]).join(';')].map(cell).join(',')))].join('\r\n');
}

