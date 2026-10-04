package dev.urbanrunnerx.partsdesk;

import android.content.Intent;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Synthetic catalog fixtures: these tests never authenticate or use dealer/customer data. */
@RunWith(AndroidJUnit4.class)
public class EpcAdapterTest {
 static final String VIN="1M8GDM9AXKP042788";
 static final String OTHER_VIN="1M8GDM9A0KP042788";

 static ActivityScenario<EpcActivity> launch(){
  Intent intent=new Intent(ApplicationProvider.getApplicationContext(),EpcActivity.class)
   .putExtra("vin",VIN).putExtra("requestId","synthetic-fixture").putExtra("autoStart",false)
   .putExtra("bases",new String[]{"1104"});
  return ActivityScenario.launch(intent);
 }

 static String js(ActivityScenario<EpcActivity> scenario,String code)throws Exception{
  CountDownLatch done=new CountDownLatch(1);AtomicReference<String> result=new AtomicReference<>();
  scenario.onActivity(a->a.getWebView().evaluateJavascript(code,value->{result.set(value);done.countDown();}));
  assertTrue("JavaScript callback timed out",done.await(10,TimeUnit.SECONDS));return result.get();
 }

 static String adapter()throws Exception{
  try(InputStream in=ApplicationProvider.getApplicationContext().getAssets().open("epc-adapter.js");ByteArrayOutputStream out=new ByteArrayOutputStream()){
   byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);
   return out.toString("UTF-8");
  }
 }

 static JSONObject request()throws Exception{return new JSONObject().put("vin",VIN).put("base","1104").put("requestId","synthetic-fixture");}

 static JSONObject call(ActivityScenario<EpcActivity> scenario,String command,JSONObject request)throws Exception{
  String raw=js(scenario,"(()=>{"+adapter()+";return JSON.stringify(partsDeskEpc("+JSONObject.quote(command)+","+request+"));})()");
  return new JSONObject(String.valueOf(new JSONTokener(raw).nextValue()));
 }

 static void load(ActivityScenario<EpcActivity> scenario,String contents)throws Exception{
  String token="fixture-"+System.nanoTime();
  String html="<!doctype html><html><head><meta name='viewport' content='width=device-width, initial-scale=1'>"
   +"<style>body{font:16px sans-serif}a,button,input,[role=gridcell],[role=columnheader]{display:block;min-height:24px}"
   +"[role=row]{display:flex}[role=gridcell],[role=columnheader]{min-width:100px}</style></head>"
   +"<body data-fixture='"+token+"'>"+contents+"</body></html>";
  scenario.onActivity(a->{a.getWebView().stopLoading();a.getWebView().loadDataWithBaseURL("https://snaponepc.com/epc/",html,"text/html","UTF-8","https://snaponepc.com/epc/");});
  long deadline=System.currentTimeMillis()+20000;String ready="";AtomicReference<String> address=new AtomicReference<>();
  while(System.currentTimeMillis()<deadline){ready=js(scenario,"document.body?.getAttribute('data-fixture')");scenario.onActivity(a->address.set(a.getWebView().getUrl()));if(JSONObject.quote(token).equals(ready)&&"https://snaponepc.com/epc/".equals(address.get()))return;Thread.sleep(100);}
  assertEquals("Synthetic catalog fixture did not load",JSONObject.quote(token),ready);
  assertEquals("Synthetic fixture must finish navigating to the trusted catalog origin","https://snaponepc.com/epc/",address.get());
 }

 static String vehicle(String vin,boolean filters){
  return "<input id='equipmentEntryInputId'><button>Find VIN</button><input id='partEntryInputId'><button>Search</button>"
   +"<a id='toolbar-vin-url-anchor'>"+vin+"</a><div title='Toggle VIN filters on and off'><input type='checkbox' "+(filters?"checked":"")+"></div>";
 }

 static String breadcrumbs(String location){
  return "<nav aria-label='Catalog location'><ul class='breadcrumb'>"
   +"<li role='menuitem'><a class='p-menuitem-link' href='#'><span title='Chassis'>Chassis</span></a></li>"
   +"<li role='menuitem'><a class='p-menuitem-link p-breadcrumb-disabled' href='#'><span id='fixture-location' title='"+location+"'>"+location+"</span></a></li>"
   +"</ul></nav>";
 }

 private static String cell(String column,String value){return "<div role='gridcell' col-id='"+column+"'>"+value+"</div>";}

 static String partsFixture(){
  return vehicle(VIN,true)+breadcrumbs("10 - Front Knuckle and Hub")
   +"<script>window.fixtureRowClicks=0;</script><div role='grid'><div role='row'>"
   +"<div role='columnheader'>Call/Base</div><div role='columnheader'>Part Description</div><div role='columnheader'>Part Number</div></div>"
   +"<div role='row' row-id='fixture-primary' onclick='window.fixtureRowClicks++'>"+cell("calloutLabel","1104")+cell("renderedDescription","Fixture hub")
   +cell("formattedPartNumber","TEST-1104-A")+cell("APPLICATION","With fixture option A")+cell("remarks","Front position only")
   +cell("FROM","01/01/2020")+cell("TO","12/31/2021")+cell("original_qty","2")+"</div>"
   +"<div role='row' row-id='fixture-alternate' onclick='window.fixtureRowClicks++'>"+cell("calloutLabel","")+cell("renderedDescription","")+cell("formattedPartNumber","HUB-TEST")+"</div>"
   +"<div role='row' row-id='fixture-filter' onclick='window.fixtureRowClicks++'>"+cell("calloutLabel","6731")+cell("renderedDescription","Fixture filter")+cell("formattedPartNumber","TEST-6731-A")+"</div></div>";
 }

 static String choicesFixture(){
  return vehicle(VIN,true)+breadcrumbs("Chassis")+"<script>window.fixtureChoice='';</script>"
   +"<div><a class='thumbnailNonIllustrated' title='Knuckle and Hub' href='#' onclick=\"window.fixtureChoice='front';return false\">Knuckle and Hub</a>"
   +"<a class='thumbnailNonIllustrated' title='Knuckle and Hub' href='#' onclick=\"window.fixtureChoice='rear';return false\">Knuckle and Hub</a></div>";
 }

 static JSONArray options(JSONObject snapshot)throws Exception{
  JSONArray result=new JSONArray(),groups=snapshot.getJSONArray("groups");
  for(int g=0;g<groups.length();g++){JSONArray group=groups.getJSONObject(g).getJSONArray("options");for(int n=0;n<group.length();n++)result.put(group.getJSONObject(n));}
  return result;
 }

 static String listChoicesFixture(){
  String[] headers={"Group","Group","Illustration"};
  String[][] labels={{"Chassis","Powertrain"},{"Front suspension","Rear suspension"},{"Front Knuckle and Hub","Rear Knuckle and Hub"}};
  StringBuilder contents=new StringBuilder(vehicle(VIN,true)+breadcrumbs("Chassis")+"<script>window.fixtureChoice='';</script>");
  for(int g=0;g<headers.length;g++){
   contents.append("<div role='grid'><div role='row'><div role='columnheader' col-id='name'>").append(headers[g]).append("</div></div>");
   for(int n=0;n<labels[g].length;n++)contents.append("<div role='row' row-id='").append(n).append("' aria-selected='").append(n==0)
    .append("'><div role='gridcell' col-id='name' onclick=\"window.fixtureChoice='").append(g).append("-").append(n).append("'\">")
    .append(labels[g][n]).append("</div></div>");
   contents.append("</div>");
  }
  return contents.toString();
 }

 @Test public void requestedBaseResultsPreserveApplicationAndNeverClickRows()throws Exception{
  try(ActivityScenario<EpcActivity> scenario=launch()){
   load(scenario,partsFixture());
   assertEquals("Remote catalog must have no local workspace bridge","\"undefined\"",js(scenario,"typeof PartsNative"));
   JSONObject snapshot=call(scenario,"snapshot",request());
   assertFalse(snapshot.toString(),snapshot.has("error"));assertEquals(3,snapshot.getInt("schema"));
   assertEquals("parts",snapshot.getString("stage"));assertTrue(snapshot.getBoolean("ready"));
   JSONArray parts=snapshot.getJSONArray("parts");assertEquals(2,parts.length());
   JSONObject part=parts.getJSONObject(0);assertEquals("TEST-1104-A",part.getString("serviceNumber"));
   assertEquals("With fixture option A",part.getString("application"));assertTrue(part.getString("remarks").contains("Front position only"));
   assertEquals("01/01/2020",part.getString("from"));assertEquals("12/31/2021",part.getString("to"));assertEquals("2",part.getString("quantity"));
   assertTrue(part.getString("context").contains("Front"));assertEquals("HUB-TEST",parts.getJSONObject(1).getString("serviceNumber"));
   JSONObject verified=call(scenario,"verify",request().put("partId",part.getString("id")).put("snapshotId",snapshot.getString("snapshotId")));
   assertFalse(verified.toString(),verified.has("error"));assertEquals("TEST-1104-A",verified.getJSONObject("part").getString("serviceNumber"));
   assertEquals("Snapshot and review must not add parts to the EPC picklist","0",js(scenario,"window.fixtureRowClicks"));
   JSONObject legacy=call(scenario,"capture",request());assertEquals(2,legacy.getJSONArray("parts").length());
  }
 }

 @Test public void vehicleAndFilterMismatchPreventPartReturn()throws Exception{
  try(ActivityScenario<EpcActivity> scenario=launch()){
   load(scenario,partsFixture());JSONObject snapshot=call(scenario,"snapshot",request());
   String partId=snapshot.getJSONArray("parts").getJSONObject(0).getString("id");
   JSONObject selected=request().put("partId",partId).put("snapshotId",snapshot.getString("snapshotId"));
   js(scenario,"document.querySelector('#toolbar-vin-url-anchor').textContent='"+OTHER_VIN+"'");
   JSONObject changed=call(scenario,"snapshot",request());assertFalse(changed.getBoolean("ready"));
   assertEquals("vehicle",changed.getString("stage"));assertEquals(0,changed.getJSONArray("parts").length());
   assertTrue(call(scenario,"verify",selected).has("error"));assertTrue(call(scenario,"capture",request()).has("error"));
   js(scenario,"document.querySelector('#toolbar-vin-url-anchor').textContent='"+VIN+"';document.querySelector('input[type=checkbox]').checked=false");
   JSONObject unfiltered=call(scenario,"snapshot",request());assertFalse(unfiltered.getBoolean("ready"));
   assertEquals("filters",unfiltered.getString("stage"));assertEquals(0,unfiltered.getJSONArray("parts").length());
   assertTrue(call(scenario,"verify",selected).has("error"));assertTrue(call(scenario,"capture",request()).has("error"));
  }
 }

 @Test public void duplicateChoiceLabelsHaveDistinctTargets()throws Exception{
  try(ActivityScenario<EpcActivity> scenario=launch()){
   load(scenario,choicesFixture());JSONObject snapshot=call(scenario,"snapshot",request());assertEquals("choices",snapshot.getString("stage"));
   JSONArray options=options(snapshot);
   assertEquals(2,options.length());JSONObject first=options.getJSONObject(0),second=options.getJSONObject(1);
   assertEquals(first.getString("label"),second.getString("label"));assertNotEquals(first.getString("id"),second.getString("id"));
   JSONObject selected=call(scenario,"select",request().put("optionId",second.getString("id")).put("snapshotId",snapshot.getString("snapshotId")));
   assertFalse(selected.toString(),selected.has("error"));assertEquals("\"rear\"",js(scenario,"window.fixtureChoice"));
  }
 }

 @Test public void illustrationOrApplicationChangeInvalidatesPendingSelection()throws Exception{
  try(ActivityScenario<EpcActivity> scenario=launch()){
   load(scenario,partsFixture());JSONObject snapshot=call(scenario,"snapshot",request());
   JSONObject pending=request().put("partId",snapshot.getJSONArray("parts").getJSONObject(0).getString("id")).put("snapshotId",snapshot.getString("snapshotId"));
   js(scenario,"document.querySelector('#fixture-location').textContent='20 - Rear Knuckle and Hub';document.querySelector('#fixture-location').title='20 - Rear Knuckle and Hub'");
   assertTrue("A part reviewed on a different illustration must be rejected",call(scenario,"verify",pending).has("error"));
   snapshot=call(scenario,"snapshot",request());pending=request().put("partId",snapshot.getJSONArray("parts").getJSONObject(0).getString("id")).put("snapshotId",snapshot.getString("snapshotId"));
   js(scenario,"document.querySelector('[col-id=APPLICATION]').textContent='With fixture option B'");
   assertTrue("A changed application must require a fresh review",call(scenario,"verify",pending).has("error"));
   assertEquals("0",js(scenario,"window.fixtureRowClicks"));
  }
 }

 @Test public void searchLocationsInheritBaseAndNavigateWithoutSelectingServiceRows()throws Exception{
  try(ActivityScenario<EpcActivity> scenario=launch()){
   String fixture=vehicle(VIN,true)+"<script>window.fixtureChoice='';</script><div role='grid'><div role='row'>"
    +"<div role='columnheader'>Base Number</div><div role='columnheader'>Part Description</div><div role='columnheader'>Part Location</div></div>"
    +"<div role='row'>"+cell("crossCatKey","1104")+cell("partDescription","Fixture hub")+"<div role='gridcell' col-id='partLocation' onclick=\"window.fixtureChoice='front'\">Front Knuckle and Hub</div></div>"
    +"<div role='row'>"+cell("crossCatKey","")+cell("partDescription","")+"<div role='gridcell' col-id='partLocation' onclick=\"window.fixtureChoice='rear'\">Rear Knuckle and Hub</div></div>"
    +"<div role='row'>"+cell("crossCatKey","6731")+cell("partDescription","Fixture filter")+"<div role='gridcell' col-id='partLocation' onclick=\"window.fixtureChoice='unrelated'\">Oil Filter</div></div></div>";
   load(scenario,fixture);JSONObject snapshot=call(scenario,"snapshot",request());
   assertEquals("choices",snapshot.getString("stage"));assertEquals(0,snapshot.getJSONArray("parts").length());
   JSONArray choices=options(snapshot);assertEquals("Both positions of the requested base should be available",2,choices.length());
   assertTrue(choices.getJSONObject(1).getString("label").contains("Rear"));
   JSONObject selected=call(scenario,"select",request().put("optionId",choices.getJSONObject(1).getString("id")).put("snapshotId",snapshot.getString("snapshotId")));
   assertFalse(selected.toString(),selected.has("error"));assertEquals("\"rear\"",js(scenario,"window.fixtureChoice"));
  }
 }

 @Test public void signedOutSessionCannotExposeStaleVehicleParts()throws Exception{
  try(ActivityScenario<EpcActivity> scenario=launch()){
   load(scenario,"<form><label>User name<input name='user'></label><label>Password<input type='password'></label><button>Sign in</button></form>");
   JSONObject snapshot=call(scenario,"snapshot",request());
   assertEquals("login",snapshot.getString("stage"));assertFalse(snapshot.getBoolean("signedIn"));assertFalse(snapshot.getBoolean("ready"));
   assertEquals(0,snapshot.getJSONArray("parts").length());assertEquals(0,snapshot.getJSONArray("groups").length());
   assertTrue(call(scenario,"verify",request().put("partId","old-part").put("snapshotId","old-snapshot")).has("error"));
  }
 }

 @Test public void catalogListViewKeepsSystemSectionAndIllustrationSeparate()throws Exception{
  try(ActivityScenario<EpcActivity> scenario=launch()){
   load(scenario,listChoicesFixture());JSONObject snapshot=call(scenario,"snapshot",request());
   assertEquals("choices",snapshot.getString("stage"));JSONArray groups=snapshot.getJSONArray("groups");assertEquals(3,groups.length());
   assertEquals("System",groups.getJSONObject(0).getString("label"));assertEquals("Section",groups.getJSONObject(1).getString("label"));
   assertEquals("Illustration",groups.getJSONObject(2).getString("label"));
   assertTrue("The active breadcrumb cannot be re-opened",snapshot.getJSONArray("breadcrumbs").getJSONObject(1).getBoolean("disabled"));
   JSONObject illustration=groups.getJSONObject(2).getJSONArray("options").getJSONObject(1);
   assertEquals("Rear Knuckle and Hub",illustration.getString("label"));
   JSONObject result=call(scenario,"select",request().put("optionId",illustration.getString("id")).put("snapshotId",snapshot.getString("snapshotId")));
   assertFalse(result.toString(),result.has("error"));assertEquals("\"2-1\"",js(scenario,"window.fixtureChoice"));
  }
 }
}

