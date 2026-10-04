package dev.urbanrunnerx.partsdesk;

import android.app.Activity;
import android.app.Instrumentation.ActivityResult;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Synthetic suffix fixtures only. No real account, VIN, customer or catalog record is used. */
@RunWith(AndroidJUnit4.class)
public class VinResolveTest {
 private static final String VIN="1M8GDM9AXKP042788",OTHER="2M8GDM9AXKP042788",SUFFIX="KP042788",REQUEST="synthetic-vin-resolution";
 private static ActivityScenario<VinResolveActivity> launch(){
  return ActivityScenario.launchActivityForResult(new Intent(ApplicationProvider.getApplicationContext(),VinResolveActivity.class).putExtra("suffix",SUFFIX).putExtra("requestId",REQUEST).putExtra("autoStart",false));
 }
 private static String js(ActivityScenario<VinResolveActivity> scenario,String code)throws Exception{
  CountDownLatch done=new CountDownLatch(1);AtomicReference<String> result=new AtomicReference<>();scenario.onActivity(a->a.getWebView().evaluateJavascript(code,value->{result.set(value);done.countDown();}));assertTrue("JavaScript callback timed out",done.await(10,TimeUnit.SECONDS));return result.get();
 }
 private static String text(View view){StringBuilder out=new StringBuilder();if(view instanceof TextView)out.append(((TextView)view).getText()).append('\n');if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int n=0;n<group.getChildCount();n++)out.append(text(group.getChildAt(n)));}return out.toString();}
 private static String screen(ActivityScenario<VinResolveActivity> scenario){AtomicReference<String> result=new AtomicReference<>();scenario.onActivity(a->result.set(text(a.getWindow().getDecorView())));return result.get();}
 private static void awaitText(ActivityScenario<VinResolveActivity> scenario,String expected)throws Exception{
  long until=System.currentTimeMillis()+15000;String actual="";while(System.currentTimeMillis()<until){actual=screen(scenario);if(actual.contains(expected))return;Thread.sleep(100);}fail("Missing '"+expected+"' in native vehicle resolver: "+actual);
 }
 private static void click(ActivityScenario<VinResolveActivity> scenario,String tag)throws Exception{
  long until=System.currentTimeMillis()+10000;java.util.concurrent.atomic.AtomicBoolean clicked=new java.util.concurrent.atomic.AtomicBoolean();while(System.currentTimeMillis()<until){scenario.onActivity(a->{View view=a.getWindow().getDecorView().findViewWithTag(tag);if(view!=null&&view.isShown()&&view.isEnabled())clicked.set(view.performClick());});if(clicked.get())return;Thread.sleep(50);}fail("Button did not become clickable: "+tag);
 }
 private static void assertNoConfirmation(ActivityScenario<VinResolveActivity> scenario){scenario.onActivity(a->{View button=a.getWindow().getDecorView().findViewWithTag("vin-resolve-confirm");assertNotNull(button);assertFalse("Unverified vehicle must have no active confirmation",button.isShown()&&button.isEnabled());});}
 private static void load(ActivityScenario<VinResolveActivity> scenario,String contents)throws Exception{
  String token="fixture-"+System.nanoTime();String html="<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'><style>body{font:16px sans-serif}a,button,input,span{display:block;min-height:24px}</style></head><body data-fixture='"+token+"'>"+contents+"</body></html>";
  scenario.onActivity(a->{a.getWebView().stopLoading();a.getWebView().loadDataWithBaseURL("https://snaponepc.com/epc/",html,"text/html","UTF-8","https://snaponepc.com/epc/");});
  long until=System.currentTimeMillis()+15000;while(System.currentTimeMillis()<until){if(JSONObject.quote(token).equals(js(scenario,"document.body?.getAttribute('data-fixture')"))){scenario.onActivity(a->a.setCatalogVisible(false));return;}Thread.sleep(100);}fail("Synthetic VIN fixture failed to load");
 }
 private static String fixture(String initial,String found,boolean filters,boolean fresh){
  return "<script>window.fixtureSearches=0;function findVehicle(){window.fixtureSearches++;"+(fresh?"document.querySelector('#toolbar-vin-url-anchor').textContent='';document.querySelector('#busy').setAttribute('aria-busy','true');setTimeout(function(){document.querySelector('#toolbar-vin-url-anchor').textContent='"+found+"';document.querySelector('#busy').removeAttribute('aria-busy');},220);":"")+"}</script>"
   +"<input id='equipmentEntryInputId'><button onclick='findVehicle()'>Find VIN</button><span id='busy'></span><a id='toolbar-vin-url-anchor'>"+initial+"</a>"
   +"<span title='ENG: Synthetic fixture engine; Vehicle: Synthetic test model'>Synthetic vehicle</span><div title='Toggle VIN filters on and off'><input type='checkbox' "+(filters?"checked":"")+"></div>";
 }
 @Test public void confirmsFreshFullVinOnlyAfterExplicitNativeReview()throws Exception{
  try(ActivityScenario<VinResolveActivity> scenario=launch()){
   load(scenario,fixture("",VIN,true,true));assertNoConfirmation(scenario);assertEquals("\"undefined\"",js(scenario,"typeof LocalTools"));assertEquals("\"undefined\"",js(scenario,"typeof PartsNative"));
   click(scenario,"vin-resolve-retry");awaitText(scenario,VIN);awaitText(scenario,"Synthetic fixture engine");assertEquals("1",js(scenario,"window.fixtureSearches"));assertEquals(JSONObject.quote(SUFFIX),js(scenario,"document.querySelector('#equipmentEntryInputId').value"));
   click(scenario,"vin-resolve-confirm");ActivityResult result=scenario.getResult();assertEquals(Activity.RESULT_OK,result.getResultCode());JSONObject vehicle=new JSONObject(result.getResultData().getStringExtra("resolvedVehicle"));assertEquals(VIN,vehicle.getString("vin"));assertEquals(SUFFIX,vehicle.getString("suffix"));assertEquals(REQUEST,vehicle.getString("requestId"));assertTrue(vehicle.getBoolean("confirmed"));assertFalse(vehicle.has("parts"));
  }
 }
 @Test public void matchingWarmToolbarWithoutObservedSearchCannotConfirm()throws Exception{
  try(ActivityScenario<VinResolveActivity> scenario=launch()){
   load(scenario,fixture(VIN,VIN,true,false));click(scenario,"vin-resolve-retry");awaitText(scenario,"previous catalog vehicle cannot confirm");assertNoConfirmation(scenario);assertEquals("1",js(scenario,"window.fixtureSearches"));scenario.onActivity(VinResolveActivity::finish);assertEquals(Activity.RESULT_CANCELED,scenario.getResult().getResultCode());
  }
 }
 @Test public void filtersOffCannotConfirmAndLiveFilterChangeCanRecover()throws Exception{
  try(ActivityScenario<VinResolveActivity> scenario=launch()){
   load(scenario,fixture("",VIN,false,true));click(scenario,"vin-resolve-retry");awaitText(scenario,"Turn on VIN filters");assertNoConfirmation(scenario);js(scenario,"document.querySelector('input[type=checkbox]').checked=true");awaitText(scenario,VIN);click(scenario,"vin-resolve-confirm");assertEquals(Activity.RESULT_OK,scenario.getResult().getResultCode());
  }
 }
 @Test public void changedVehicleBetweenDisplayAndClickCannotReturnOldVin()throws Exception{
  try(ActivityScenario<VinResolveActivity> scenario=launch()){
   load(scenario,fixture("",VIN,true,true));click(scenario,"vin-resolve-retry");awaitText(scenario,VIN);
   CountDownLatch clicked=new CountDownLatch(1);scenario.onActivity(a->a.getWebView().evaluateJavascript("document.querySelector('#toolbar-vin-url-anchor').textContent='"+OTHER+"'",value->{View confirm=a.getWindow().getDecorView().findViewWithTag("vin-resolve-confirm");confirm.performClick();clicked.countDown();}));assertTrue(clicked.await(10,TimeUnit.SECONDS));
   awaitText(scenario,OTHER);scenario.onActivity(VinResolveActivity::finish);assertEquals(Activity.RESULT_CANCELED,scenario.getResult().getResultCode());
  }
 }
 @Test public void reloadDiscardsEarlierReviewEvenIfToolbarStillMatches()throws Exception{
  try(ActivityScenario<VinResolveActivity> scenario=launch()){
   load(scenario,fixture("",VIN,true,true));click(scenario,"vin-resolve-retry");awaitText(scenario,VIN);load(scenario,fixture(VIN,VIN,true,false));Thread.sleep(1000);assertNoConfirmation(scenario);assertEquals("0",js(scenario,"window.fixtureSearches"));
  }
 }
 @Test public void noMatchModalOverridesOldMatchingVinAndCancelReturnsNothing()throws Exception{
  try(ActivityScenario<VinResolveActivity> scenario=launch()){
   load(scenario,fixture(VIN,VIN,true,false)+"<script>function findVehicle(){window.fixtureSearches++;var modal=document.createElement('div');modal.setAttribute('role','dialog');modal.textContent='VIN Search Results: VIN was not found';document.body.appendChild(modal);}</script>");click(scenario,"vin-resolve-retry");awaitText(scenario,"did not find this VIN suffix");assertNoConfirmation(scenario);scenario.onActivity(VinResolveActivity::finish);ActivityResult result=scenario.getResult();assertEquals(Activity.RESULT_CANCELED,result.getResultCode());assertNull(result.getResultData());
  }
 }
 @Test public void loginShowsCatalogAndDoesNotUseCachedMatchingVin()throws Exception{
  try(ActivityScenario<VinResolveActivity> scenario=launch()){
   load(scenario,fixture(VIN,VIN,true,false)+"<input type='password' autocomplete='current-password'>");awaitText(scenario,"Sign in to your Snap-on account");assertNoConfirmation(scenario);assertEquals("0",js(scenario,"window.fixtureSearches"));
  }
 }
 @Test public void cancelDuringSearchNeverReturnsAResolvedVehicle()throws Exception{
  try(ActivityScenario<VinResolveActivity> scenario=launch()){
   load(scenario,fixture("",VIN,true,true).replace("},220);","},5000);"));click(scenario,"vin-resolve-retry");scenario.onActivity(VinResolveActivity::finish);ActivityResult result=scenario.getResult();assertEquals(Activity.RESULT_CANCELED,result.getResultCode());assertNull(result.getResultData());
  }
 }
 @Test public void missingVehicleDetailsStayUnknownAndAreNeverGuessed()throws Exception{
  try(ActivityScenario<VinResolveActivity> scenario=launch()){
   load(scenario,fixture("",VIN,true,true).replace("title='ENG: Synthetic fixture engine; Vehicle: Synthetic test model'",""));click(scenario,"vin-resolve-retry");awaitText(scenario,VIN);awaitText(scenario,"Vehicle details not shown by the catalog");click(scenario,"vin-resolve-confirm");ActivityResult result=scenario.getResult();assertEquals(Activity.RESULT_OK,result.getResultCode());JSONObject resolved=new JSONObject(result.getResultData().getStringExtra("resolvedVehicle"));assertEquals("",resolved.getString("vehicle"));
  }
 }
 @Test public void mainActivityAcceptsOnlyConfirmedMatchingFullVin()throws Exception{
  JSONObject good=new JSONObject().put("requestId",REQUEST).put("suffix",SUFFIX).put("vin",VIN).put("vehicle","Synthetic fixture vehicle").put("confirmed",true);
  assertTrue(MainActivity.validResolvedVin(good.toString(),REQUEST,SUFFIX));
  assertFalse(MainActivity.validResolvedVin(good.toString(),"different-request",SUFFIX));
  assertFalse(MainActivity.validResolvedVin(new JSONObject(good.toString()).put("requestId","different-request").toString(),REQUEST,SUFFIX));
  assertFalse(MainActivity.validResolvedVin(new JSONObject(good.toString()).put("suffix","KP042789").toString(),REQUEST,SUFFIX));
  assertFalse(MainActivity.validResolvedVin(new JSONObject(good.toString()).put("vin","1M8GDM9AXKP042789").toString(),REQUEST,SUFFIX));
  assertFalse(MainActivity.validResolvedVin(new JSONObject(good.toString()).put("vin",SUFFIX).toString(),REQUEST,SUFFIX));
  JSONObject unconfirmed=new JSONObject(good.toString());unconfirmed.remove("confirmed");assertFalse(MainActivity.validResolvedVin(unconfirmed.toString(),REQUEST,SUFFIX));
  assertFalse(MainActivity.validResolvedVin(new JSONObject(good.toString()).put("confirmed",false).toString(),REQUEST,SUFFIX));
  char[] oversized=new char[2001];java.util.Arrays.fill(oversized,'x');assertFalse(MainActivity.validResolvedVin(new JSONObject(good.toString()).put("vehicle",new String(oversized)).toString(),REQUEST,SUFFIX));
 }
 @Test public void durableVinMailboxAcknowledgesOnlyItsExactRequest()throws Exception{
  String pending=new JSONObject().put("requestId",REQUEST).put("suffix",SUFFIX).put("vin",VIN).put("vehicle","Synthetic fixture vehicle").put("confirmed",true).toString();
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(activity->{
    android.content.SharedPreferences mailbox=activity.getSharedPreferences("vin-results",Activity.MODE_PRIVATE);String previous=mailbox.getString("pending",null);
    try{
     mailbox.edit().putString("pending",pending).apply();MainActivity.LocalTools bridge=activity.new LocalTools();assertEquals(pending,bridge.getVinResolution());
     bridge.acknowledgeVinResolution(null);assertEquals(pending,bridge.getVinResolution());bridge.acknowledgeVinResolution("different-request");assertEquals(pending,bridge.getVinResolution());
     bridge.acknowledgeVinResolution(REQUEST);assertEquals("",bridge.getVinResolution());bridge.acknowledgeVinResolution(REQUEST);assertEquals("",bridge.getVinResolution());
    }finally{if(previous==null)mailbox.edit().remove("pending").apply();else mailbox.edit().putString("pending",previous).apply();}
   });
  }
 }
 @Test public void finalNativeBoundaryRejectsEveryWrongIdentity()throws Exception{
  JSONObject good=new JSONObject().put("requestId",REQUEST).put("suffix",SUFFIX).put("searchId","fixture-search").put("identityId","fixture-search-2").put("stage","resolved").put("ready",true).put("filtersOn",true).put("vin",VIN).put("vehicle","Fixture vehicle");
  assertTrue(VinResolveActivity.validResolution(good,REQUEST,SUFFIX,"fixture-search"));
  for(String field:new String[]{"requestId","suffix","searchId","identityId","stage","vin"}){JSONObject bad=new JSONObject(good.toString()).put(field,"");assertFalse(field,VinResolveActivity.validResolution(bad,REQUEST,SUFFIX,"fixture-search"));}
  assertFalse(VinResolveActivity.validResolution(new JSONObject(good.toString()).put("filtersOn",false),REQUEST,SUFFIX,"fixture-search"));assertFalse(VinResolveActivity.validResolution(new JSONObject(good.toString()).put("ready",false),REQUEST,SUFFIX,"fixture-search"));assertFalse(VinResolveActivity.validResolution(new JSONObject(good.toString()).put("error","stale"),REQUEST,SUFFIX,"fixture-search"));
 }
}
