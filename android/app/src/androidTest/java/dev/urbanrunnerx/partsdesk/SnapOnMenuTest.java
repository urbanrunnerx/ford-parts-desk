package dev.urbanrunnerx.partsdesk;

import android.webkit.JavascriptInterface;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real app menu and catalog search; the test bridge captures requests without opening EPC. */
@RunWith(AndroidJUnit4.class)
public class SnapOnMenuTest {
 public static final class RecordingBridge {
  final AtomicReference<String> payload=new AtomicReference<>();
  final AtomicInteger calls=new AtomicInteger();
  @JavascriptInterface public boolean isFixture(){return true;}
  @JavascriptInterface public void openEpc(String request){payload.set(request);calls.incrementAndGet();}
 }

 private static String js(ActivityScenario<MainActivity> scenario,String code)throws Exception{
  CountDownLatch done=new CountDownLatch(1);AtomicReference<String> result=new AtomicReference<>();
  scenario.onActivity(a->a.getWebView().evaluateJavascript(code,value->{result.set(value);done.countDown();}));
  assertTrue("JavaScript callback timed out",done.await(10,TimeUnit.SECONDS));return result.get();
 }

 private static void until(ActivityScenario<MainActivity> scenario,String code,String expected)throws Exception{
  long deadline=System.currentTimeMillis()+30000;String latest="";
  while(System.currentTimeMillis()<deadline){latest=js(scenario,code);if(expected.equals(latest))return;Thread.sleep(100);}
  assertEquals(expected,latest);
 }

 private static void reload(ActivityScenario<MainActivity> scenario)throws Exception{
  js(scenario,"window.__beforeReload=true;location.reload()");
  until(scenario,"window.__beforeReload===undefined && Number(document.querySelector('#baseCount')?.textContent.replaceAll(',',''))>0","true");
 }

 private static void assertDialogCloseReachable(ActivityScenario<MainActivity> scenario)throws Exception{
  String script="(()=>{const dialog=document.querySelector('#info'),close=dialog.querySelector('.close');const r=close.getBoundingClientRect(),d=dialog.getBoundingClientRect(),hit=document.elementFromPoint((r.left+r.right)/2,(r.top+r.bottom)/2);const overlapsForm=[...dialog.querySelectorAll('#snaponForm input,#snaponForm select,#snaponForm button')].some(el=>{const b=el.getBoundingClientRect();return b.width>0&&b.height>0&&Math.min(r.right,b.right)>Math.max(r.left,b.left)&&Math.min(r.bottom,b.bottom)>Math.max(r.top,b.top)});return JSON.stringify({visible:r.width>0&&r.height>0&&r.left>=Math.max(0,d.left)-1&&r.top>=Math.max(0,d.top)-1&&r.right<=Math.min(innerWidth,d.right)+1&&r.bottom<=Math.min(innerHeight,d.bottom)+1,tappable:hit===close||close.contains(hit),overlapsForm,close:{top:r.top,bottom:r.bottom,left:r.left,right:r.right},dialog:{top:d.top,bottom:d.bottom}})})()";
  JSONObject bounds=new JSONObject(String.valueOf(new JSONTokener(js(scenario,script)).nextValue()));
  assertTrue("Close must remain inside the visible dialog after scrolling: "+bounds,bounds.getBoolean("visible"));assertTrue("Close must remain reachable above other content: "+bounds,bounds.getBoolean("tappable"));assertFalse("Close must not cover a form control: "+bounds,bounds.getBoolean("overlapsForm"));
 }

 private static JSONObject stored(ActivityScenario<MainActivity> scenario,String key)throws Exception{
  Object value=new JSONTokener(js(scenario,"localStorage.getItem('partsdesk-v1-"+key+"')")).nextValue();
  return new JSONObject(String.valueOf(value));
 }

 private static JSONObject activeJob(JSONObject book)throws Exception{
  JSONArray jobs=book.getJSONArray("items");for(int n=0;n<jobs.length();n++){JSONObject job=jobs.getJSONObject(n);if(job.getString("id").equals(book.getString("activeId")))return job;}
  throw new AssertionError("The active job must be present");
 }

 private static JSONObject awaitRequest(RecordingBridge bridge,int calls)throws Exception{
  long deadline=System.currentTimeMillis()+10000;while(bridge.calls.get()<calls&&System.currentTimeMillis()<deadline)Thread.sleep(50);
  assertEquals("The valid request should reach the native bridge",calls,bridge.calls.get());assertNotNull(bridge.payload.get());
  JSONObject payload=new JSONObject(bridge.payload.get());String id=payload.getString("requestId");
  assertTrue("Request IDs must fit native validation",id.length()<=180&&id.matches("[A-Za-z0-9|_-]+"));return payload;
 }

 @Test public void snapOnMenuGroupsCommonNamesAndValidatesBeforeNativeLookup()throws Exception{
  RecordingBridge bridge=new RecordingBridge();
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   until(scenario,"Number(document.querySelector('#baseCount')?.textContent.replaceAll(',',''))>0","true");
   js(scenario,"localStorage.clear();localStorage.setItem('partsdesk-v1-worksheet',JSON.stringify({job:'Existing fixture job',vin:'"+EpcAdapterTest.OTHER_VIN+"',rows:[{id:'fixture-existing',name:'Existing fixture part',bases:['6731'],qty:2,note:'Keep this note'}]}))");
   // addJavascriptInterface replaces the production bridge on the next page load.
   scenario.onActivity(a->a.getWebView().addJavascriptInterface(bridge,"PartsNative"));reload(scenario);
   assertEquals("true",js(scenario,"PartsNative.isFixture()"));
   String before=js(scenario,"localStorage.getItem('partsdesk-v1-jobs')");
   assertEquals("true",js(scenario,"!!document.querySelector('#snaponButton') && !!document.querySelector('#mobileSnapon')"));
   js(scenario,"document.querySelector('#snaponButton').click()");
   until(scenario,"document.querySelector('#info').open && !!document.querySelector('#snaponForm')","true");
   assertEquals("true",js(scenario,"document.querySelector('#snaponLaunch').disabled"));
   js(scenario,"document.querySelector('#snaponQuery').value='purge valve';document.querySelector('#snaponQuery').dispatchEvent(new Event('input',{bubbles:true}))");
   assertEquals("One common part name should produce one family","1",js(scenario,"document.querySelectorAll('[data-snap-match]').length"));
   assertEquals("true",js(scenario,"document.querySelector('#snaponMatches').textContent.includes('9C915') && document.querySelector('#snaponMatches').textContent.includes('9D289')"));
   js(scenario,"document.querySelector('[data-snap-match]').click()");
   assertEquals("true",js(scenario,"[...document.querySelector('#snaponBase').options].some(o=>o.value==='9C915') && [...document.querySelector('#snaponBase').options].some(o=>o.value==='9D289')"));
   js(scenario,"document.querySelector('#snaponVin').value='"+EpcAdapterTest.VIN+"';document.querySelector('#snaponSelected').scrollIntoView({block:'nearest'})");
   assertDialogCloseReachable(scenario);
   GuidedLookupTest.savePreview(scenario,"snapon-menu.png");
   js(scenario,"document.querySelector('#snaponVin').value='INVALID';document.querySelector('#snaponLaunch').click()");
   assertEquals("true",js(scenario,"document.querySelector('#snaponStatus').textContent.includes('17-character VIN')"));
   assertEquals("Invalid input must not launch EPC",0,bridge.calls.get());
   assertEquals("Invalid VIN must not change the saved jobs",before,js(scenario,"localStorage.getItem('partsdesk-v1-jobs')"));
   assertEquals("null",js(scenario,"localStorage.getItem('partsdesk-v1-epc-pending')"));

   js(scenario,"document.querySelector('#snaponVin').value='"+EpcAdapterTest.VIN+"';document.querySelector('#snaponBase').value='9D289';document.querySelector('#snaponLaunch').click()");
   JSONObject payload=awaitRequest(bridge,1);assertEquals(EpcAdapterTest.VIN,payload.getString("vin"));
   assertEquals("9D289",payload.getJSONArray("bases").getString(0));assertTrue(payload.getJSONArray("bases").toString().contains("9C915"));
   assertTrue(payload.getString("partName").toLowerCase().contains("purge"));
   JSONObject book=stored(scenario,"jobs");JSONArray jobs=book.getJSONArray("items");assertEquals(2,jobs.length());
   JSONObject active=null,original=null;
   for(int n=0;n<jobs.length();n++){JSONObject job=jobs.getJSONObject(n);if(job.getString("id").equals(book.getString("activeId")))active=job;if(job.getString("vin").equals(EpcAdapterTest.OTHER_VIN))original=job;}
   assertNotNull(active);assertNotNull(original);assertEquals(EpcAdapterTest.VIN,active.getString("vin"));assertEquals(1,active.getJSONArray("rows").length());
   assertEquals("Keep this note",original.getJSONArray("rows").getJSONObject(0).getString("note"));
   JSONObject pending=stored(scenario,"epc-pending");assertEquals(payload.getString("requestId"),pending.getString("id"));
   assertEquals(active.getString("id"),pending.getString("jobId"));assertEquals("9D289",pending.getString("base"));

   // Model a saved job created before this family's current base list was expanded.
   String oldJobId=active.getString("id");
   js(scenario,"(()=>{const book=JSON.parse(localStorage.getItem('partsdesk-v1-jobs'));const job=book.items.find(j=>j.id===book.activeId);const row=job.rows[0];row.bases=['9C915'];row.qty=7;row.note='Keep updated counter note';row.selection={vin:job.vin,base:'9C915',serviceNumber:'TEST-9C915-A',source:'Synthetic fixture',stale:false};localStorage.setItem('partsdesk-v1-jobs',JSON.stringify(book));localStorage.setItem('partsdesk-v1-worksheet',JSON.stringify(job));})()");
   reload(scenario);
   js(scenario,"document.querySelector('#snaponButton').click();document.querySelector('[data-snap-query=\"purge valve\"]').click();document.querySelector('[data-snap-match]').click();document.querySelector('#snaponBase').value='9D289';document.querySelector('#snaponLaunch').click()");
   payload=awaitRequest(bridge,2);assertEquals("9D289",payload.getJSONArray("bases").getString(0));
   book=stored(scenario,"jobs");active=activeJob(book);assertEquals("Same-VIN lookup should reuse its job",oldJobId,active.getString("id"));
   assertEquals(2,book.getJSONArray("items").length());assertEquals(1,active.getJSONArray("rows").length());
   JSONObject preserved=active.getJSONArray("rows").getJSONObject(0);
   assertTrue("The saved row must accept the newly selected base on return",preserved.getJSONArray("bases").toString().contains("9D289"));
   assertTrue(preserved.getJSONArray("bases").toString().contains("9C915"));assertEquals(7,preserved.getInt("qty"));
   assertEquals("Keep updated counter note",preserved.getString("note"));assertEquals("TEST-9C915-A",preserved.getJSONObject("selection").getString("serviceNumber"));

   js(scenario,"document.querySelector('#info').close();document.querySelector('#mobileSnapon').click()");
   assertEquals(JSONObject.quote(EpcAdapterTest.VIN),js(scenario,"document.querySelector('#snaponVin').value"));
   assertEquals("true",js(scenario,"document.querySelector('#snaponLaunch').disabled"));assertEquals(2,bridge.calls.get());
   js(scenario,"localStorage.clear()");reload(scenario);
  }
 }

 @Test public void longImportedFamilyCanLaunchFromMenuAndExistingJob()throws Exception{
  RecordingBridge bridge=new RecordingBridge();StringBuilder name=new StringBuilder("Fixture ");
  for(int n=0;n<9;n++)name.append("extended reference ");String longName=name.append("part").toString();
  assertTrue(longName.length()>160&&longName.length()<=200);
  JSONArray imported=new JSONArray().put(new JSONObject().put("base","9TEST99").put("name",longName).put("category","Body & interior").put("aliases",new JSONArray().put("fixture custom lookup")).put("source",""));
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   try{
   until(scenario,"Number(document.querySelector('#baseCount')?.textContent.replaceAll(',',''))>0","true");
   js(scenario,"localStorage.clear();localStorage.setItem('partsdesk-v1-imports',JSON.stringify("+imported+"))");
   scenario.onActivity(a->a.getWebView().addJavascriptInterface(bridge,"PartsNative"));reload(scenario);
   assertEquals("true",js(scenario,"PartsNative.isFixture()"));
   js(scenario,"document.querySelector('#snaponButton').click();document.querySelector('#snaponVin').value='"+EpcAdapterTest.VIN+"';document.querySelector('#snaponQuery').value='9TEST99';document.querySelector('#snaponQuery').dispatchEvent(new Event('input',{bubbles:true}))");
   assertEquals("1",js(scenario,"document.querySelectorAll('[data-snap-match]').length"));
   js(scenario,"document.querySelector('[data-snap-match]').click();document.querySelector('#snaponLaunch').click()");
   JSONObject fromMenu=awaitRequest(bridge,1);assertEquals(longName.substring(0,160),fromMenu.getString("partName"));assertEquals("9TEST99",fromMenu.getJSONArray("bases").getString(0));
   // The recording bridge has no Activity to return a close event; model Back to job.
   js(scenario,"window.dispatchEvent(new CustomEvent('parts-epc-closed',{detail:{requestId:"+JSONObject.quote(fromMenu.getString("requestId"))+"}}))");
   JSONObject row=activeJob(stored(scenario,"jobs")).getJSONArray("rows").getJSONObject(0);
   assertEquals("Transport titles are capped without truncating saved job names",longName,row.getString("name"));
   assertTrue("This fixture should exercise a long imported family ID",row.getString("id").length()>180);
   js(scenario,"document.querySelector('#info').close();document.querySelector('#activeJob').click();document.querySelector('[data-job-epc]').click();document.querySelector('#launchEpc').click()");
   JSONObject fromJob=awaitRequest(bridge,2);assertEquals("9TEST99",fromJob.getJSONArray("bases").getString(0));
   assertNotEquals("Each lookup needs its own request ID",fromMenu.getString("requestId"),fromJob.getString("requestId"));
   JSONObject pending=stored(scenario,"epc-pending");assertEquals(row.getString("id"),pending.getString("rowId"));assertEquals(fromJob.getString("requestId"),pending.getString("id"));
   }finally{js(scenario,"localStorage.clear()");reload(scenario);}
  }
 }
}

