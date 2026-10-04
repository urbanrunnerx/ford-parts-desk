package dev.urbanrunnerx.partsdesk;

import android.app.AlertDialog;
import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Lifecycle and recovery regressions use only synthetic pages, never a real account. */
@RunWith(AndroidJUnit4.class)
public class EpcSessionTest {
 private static String text(View view){StringBuilder out=new StringBuilder();if(view instanceof TextView)out.append(((TextView)view).getText()).append('\n');if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)out.append(text(group.getChildAt(i)));}return out.toString();}
 private static Button button(View view,String title){if(view instanceof Button&&title.equals(((Button)view).getText().toString()))return (Button)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){Button found=button(group.getChildAt(i),title);if(found!=null)return found;}}return null;}
 private static Spinner spinner(View view){if(view instanceof Spinner)return (Spinner)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){Spinner found=spinner(group.getChildAt(i));if(found!=null)return found;}}return null;}
 private static void awaitText(ActivityScenario<EpcActivity> scenario,String expected)throws Exception{long until=System.currentTimeMillis()+15000;AtomicReference<String> found=new AtomicReference<>("");while(System.currentTimeMillis()<until){scenario.onActivity(a->found.set(text(a.getWindow().getDecorView())));if(found.get().contains(expected))return;Thread.sleep(100);}fail("Missing "+expected+" in "+found.get());}
 private static AlertDialog dialog(EpcActivity activity){try{java.lang.reflect.Field f=EpcActivity.class.getDeclaredField("reviewDialog");f.setAccessible(true);return (AlertDialog)f.get(activity);}catch(Exception error){throw new AssertionError(error);}}
 private static AlertDialog awaitDialog(ActivityScenario<EpcActivity> scenario)throws Exception{long until=System.currentTimeMillis()+15000;AtomicReference<AlertDialog> result=new AtomicReference<>();while(System.currentTimeMillis()<until){scenario.onActivity(a->result.set(dialog(a)));if(result.get()!=null)return result.get();Thread.sleep(100);}throw new AssertionError("Expected a fresh part review dialog");}

 @Test public void sessionReleasesActivityContextAndClientsThenReusesWarmWebView(){
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(activity->{
    Context application=activity.getApplicationContext();EpcSessionStore store=new EpcSessionStore(application);EpcSessionStore.Session first=store.acquire(activity);WebView web=first.web;
    WebViewClient client=new WebViewClient();web.setWebViewClient(client);assertSame(activity,first.context.getBaseContext());store.release(first);
    assertSame("Idle page must not retain an Activity context",application,first.context.getBaseContext());assertNull(web.getParent());assertNotSame("Activity callbacks must be detached",client,web.getWebViewClient());
    EpcSessionStore.Session next=store.acquire(activity);assertSame("Consecutive lookups reuse the already loaded page",web,next.web);store.release(next);store.discardIdle();assertTrue(next.destroyed);
    EpcSessionStore.Session fresh=store.acquire(activity);assertNotSame(web,fresh.web);store.release(fresh);store.discardIdle();
   });
  }
 }

 @Test public void simultaneousLeaseNeverStealsAnActivePage(){
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(activity->{EpcSessionStore store=new EpcSessionStore(activity);EpcSessionStore.Session first=store.acquire(activity),second=store.acquire(activity);assertNotSame(first.web,second.web);assertFalse(first.destroyed);store.release(first);assertTrue("Superseded pages are destroyed instead of cached",first.destroyed);store.release(second);store.discardIdle();assertTrue(second.destroyed);});
  }
 }

 @Test public void recreationRetainsTheLivePageAndDoesNotReloadCatalog()throws Exception{
  try(ActivityScenario<EpcActivity> scenario=EpcAdapterTest.launch()){
   EpcAdapterTest.load(scenario,EpcAdapterTest.partsFixture());EpcAdapterTest.js(scenario,"window.warmSessionMarker='keep-this-page'");scenario.onActivity(EpcActivity::refreshSnapshot);awaitText(scenario,"TEST-1104-A");AtomicReference<WebView> original=new AtomicReference<>();scenario.onActivity(a->original.set(a.getWebView()));
   scenario.recreate();scenario.onActivity(a->assertSame("Rotation must reuse the rendered catalog",original.get(),a.getWebView()));assertEquals("\"keep-this-page\"",EpcAdapterTest.js(scenario,"window.warmSessionMarker"));awaitText(scenario,"TEST-1104-A");
  }
 }

 @Test public void benignRefreshPreservesDropdownChoiceAndScroll()throws Exception{
  try(ActivityScenario<EpcActivity> scenario=EpcAdapterTest.launch()){
   String option="<a class='thumbnailNonIllustrated' title='Location one' href='#'>One</a><a class='thumbnailNonIllustrated' title='Location two' href='#'>Two</a>";
   EpcAdapterTest.load(scenario,EpcAdapterTest.partsFixture()+option);scenario.onActivity(EpcActivity::refreshSnapshot);awaitText(scenario,"TEST-1104-A");AtomicReference<Integer> scroll=new AtomicReference<>();
   scenario.onActivity(a->{View root=a.getWindow().getDecorView();Spinner menu=spinner(root.findViewWithTag("guided-choices"));assertNotNull(menu);menu.setSelection(2);ScrollView guided=(ScrollView)root.findViewWithTag("guided-content").getParent();guided.scrollTo(0,150);scroll.set(guided.getScrollY());assertTrue(scroll.get()>0);});
   // A new option changes the snapshot without changing VIN, base or catalog path.
   EpcAdapterTest.js(scenario,"document.body.insertAdjacentHTML('beforeend',\"<a class='thumbnailNonIllustrated' title='Location three' href='#'>Three</a>\")");scenario.onActivity(EpcActivity::refreshSnapshot);
   long until=System.currentTimeMillis()+10000;AtomicReference<Integer> count=new AtomicReference<>(0);while(System.currentTimeMillis()<until){scenario.onActivity(a->{Spinner menu=spinner(a.getWindow().getDecorView().findViewWithTag("guided-choices"));count.set(menu==null?0:menu.getCount());});if(count.get()==4)break;Thread.sleep(100);}assertEquals(Integer.valueOf(4),count.get());
   scenario.onActivity(a->{View root=a.getWindow().getDecorView();assertEquals(2,spinner(root.findViewWithTag("guided-choices")).getSelectedItemPosition());assertEquals(scroll.get().intValue(),((ScrollView)root.findViewWithTag("guided-content").getParent()).getScrollY());});
  }
 }

 @Test public void connectionErrorStaysActionableAndCancelDoesNotRestartWork()throws Exception{
  try(ActivityScenario<EpcActivity> scenario=EpcAdapterTest.launch()){
   EpcAdapterTest.load(scenario,EpcAdapterTest.partsFixture());scenario.onActivity(EpcActivity::refreshSnapshot);awaitText(scenario,"TEST-1104-A");
   scenario.onActivity(a->{WebView web=a.getWebView();web.getWebViewClient().onReceivedError(web,new WebResourceRequest(){public Uri getUrl(){return Uri.parse("https://snaponepc.com/epc/");}public boolean isForMainFrame(){return true;}public boolean isRedirect(){return false;}public boolean hasGesture(){return false;}public String getMethod(){return "GET";}public Map<String,String> getRequestHeaders(){return Collections.emptyMap();}},null);});
   awaitText(scenario,"Retry connection");Thread.sleep(2800);awaitText(scenario,"Catalog connection interrupted");scenario.onActivity(a->{assertNotNull(button(a.getWindow().getDecorView(),"Retry"));assertNull(button(a.getWindow().getDecorView(),"Review this part"));});
   EpcAdapterTest.load(scenario,EpcAdapterTest.partsFixture());scenario.onActivity(EpcActivity::refreshSnapshot);awaitText(scenario,"TEST-1104-A");
   EpcAdapterTest.js(scenario,"[...document.querySelectorAll('button')].find(b=>b.textContent==='Search').onclick=()=>document.body.insertAdjacentHTML('beforeend','<div aria-busy=\"true\">Loading</div>')");
   scenario.onActivity(a->button(a.getWindow().getDecorView(),"Find parts").performClick());awaitText(scenario,"Finding base");scenario.onActivity(a->button(a.getWindow().getDecorView(),"Cancel").performClick());awaitText(scenario,"Lookup stopped");Thread.sleep(2800);awaitText(scenario,"Lookup stopped");
   EpcAdapterTest.js(scenario,"document.querySelector('[aria-busy]').remove()");scenario.onActivity(EpcActivity::refreshSnapshot);awaitText(scenario,"TEST-1104-A");
  }
 }

 @Test public void loadMoreKeepsPriorPartsAndRevalidatesOffscreenReview()throws Exception{
  try(ActivityScenario<EpcActivity> scenario=EpcAdapterTest.launch()){
   String fixture=EpcAdapterTest.vehicle(EpcAdapterTest.VIN,true)+EpcAdapterTest.breadcrumbs("Fixture virtual grid")
    +"<div role='grid'><div role='row'><div role='columnheader'>Call/Base</div><div role='columnheader'>Part Description</div><div role='columnheader'>Part Number</div></div>"
    +"<div id='viewport' class='ag-body-viewport' style='height:120px;overflow:auto'><div style='height:900px;position:relative'><div id='fixture-rows'></div></div></div></div>"
    +"<script>window.fixtureRowClicks=0;const viewport=document.querySelector('#viewport'),rows=document.querySelector('#fixture-rows');"
    +"const cell=(id,v)=>'<div role=gridcell col-id='+id+'>'+v+'</div>';const row=(id,base,number)=>'<div role=row row-id='+id+' onclick=\"window.fixtureRowClicks++\">'+cell('calloutLabel',base)+cell('renderedDescription',base?'Fixture hub':'')+cell('formattedPartNumber',number)+'</div>';"
    +"function paint(){rows.style.transform='translateY('+viewport.scrollTop+'px)';rows.innerHTML=viewport.scrollTop<50?row('a','1104','TEST-1104-A')+row('b','','TEST-1104-B'):row('b','','TEST-1104-B')+row('c','','TEST-1104-C');}viewport.addEventListener('scroll',paint);paint();</script>";
   EpcAdapterTest.load(scenario,fixture);scenario.onActivity(EpcActivity::refreshSnapshot);awaitText(scenario,"TEST-1104-A");
   scenario.onActivity(a->{Button more=button(a.getWindow().getDecorView(),"Load more catalog results");assertNotNull(more);more.performClick();});awaitText(scenario,"TEST-1104-C");awaitText(scenario,"TEST-1104-A");awaitText(scenario,"3 LOADED");awaitText(scenario,"Check in catalog");
   scenario.onActivity(a->{View card=a.getWindow().getDecorView().findViewWithTag("guided-part-TEST-1104-A");assertNotNull(card);button(card,"Review this part").performClick();});awaitDialog(scenario);assertEquals("0",EpcAdapterTest.js(scenario,"document.querySelector('#viewport').scrollTop"));assertEquals("0",EpcAdapterTest.js(scenario,"window.fixtureRowClicks"));
   scenario.onActivity(a->{AlertDialog review=dialog(a);assertNotNull(review);assertTrue(review.isShowing());review.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();});
   EpcAdapterTest.js(scenario,"document.querySelector('[col-id=formattedPartNumber]').textContent='TEST-1104-D'");scenario.onActivity(EpcActivity::refreshSnapshot);awaitText(scenario,"TEST-1104-D");
  }
 }

 @Test public void cancelledJavascriptCallbackCannotRestoreOldResults()throws Exception{
  try(ActivityScenario<EpcActivity> scenario=EpcAdapterTest.launch()){
   EpcAdapterTest.load(scenario,EpcAdapterTest.partsFixture());scenario.onActivity(EpcActivity::refreshSnapshot);awaitText(scenario,"TEST-1104-A");
   scenario.onActivity(a->{a.refreshSnapshot();try{Class<?> callback=Class.forName("dev.urbanrunnerx.partsdesk.EpcActivity$ResultCallback");java.lang.reflect.Method read=EpcActivity.class.getDeclaredMethod("readSnapshot",callback);read.setAccessible(true);read.invoke(a,new Object[]{null});}catch(Exception error){throw new AssertionError(error);}button(a.getWindow().getDecorView(),"Cancel").performClick();});
   Thread.sleep(2800);awaitText(scenario,"Lookup stopped");scenario.onActivity(a->assertNull(button(a.getWindow().getDecorView(),"Review this part")));
   scenario.onActivity(EpcActivity::refreshSnapshot);awaitText(scenario,"TEST-1104-A");
  }
 }

 @Test public void silentSearchDoesNotMistakeElapsedTimeForSuccess()throws Exception{
  try(ActivityScenario<EpcActivity> scenario=EpcAdapterTest.launch()){
   EpcAdapterTest.load(scenario,EpcAdapterTest.partsFixture());scenario.onActivity(EpcActivity::refreshSnapshot);awaitText(scenario,"TEST-1104-A");
   scenario.onActivity(a->button(a.getWindow().getDecorView(),"Find parts").performClick());awaitText(scenario,"Finding base");Thread.sleep(1800);
   scenario.onActivity(a->{assertNull("Old result cards must not imply a stalled search succeeded",button(a.getWindow().getDecorView(),"Review this part"));assertFalse(button(a.getWindow().getDecorView(),"Find parts").isEnabled());try{java.lang.reflect.Field deadline=EpcActivity.class.getDeclaredField("findDeadline");deadline.setAccessible(true);deadline.setLong(a,android.os.SystemClock.elapsedRealtime()-1);}catch(Exception error){throw new AssertionError(error);}});
   awaitText(scenario,"Lookup needs another try");Thread.sleep(1000);awaitText(scenario,"Lookup needs another try");
  }
 }

 @Test public void observedBusyThenSameResultsCompletesSearch()throws Exception{
  try(ActivityScenario<EpcActivity> scenario=EpcAdapterTest.launch()){
   EpcAdapterTest.load(scenario,EpcAdapterTest.partsFixture());scenario.onActivity(EpcActivity::refreshSnapshot);awaitText(scenario,"TEST-1104-A");
   EpcAdapterTest.js(scenario,"[...document.querySelectorAll('button')].find(b=>b.textContent==='Search').onclick=()=>document.body.insertAdjacentHTML('beforeend','<div aria-busy=\"true\">Loading</div>')");
   scenario.onActivity(a->button(a.getWindow().getDecorView(),"Find parts").performClick());awaitText(scenario,"Finding base");
   EpcAdapterTest.js(scenario,"document.querySelector('[aria-busy]').remove()");awaitText(scenario,"TEST-1104-A");scenario.onActivity(a->assertTrue(button(a.getWindow().getDecorView(),"Find parts").isEnabled()));
  }
 }

 @Test public void invalidLaunchEmitsMatchingCloseWithoutClearingAnotherRequest()throws Exception{
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   long until=System.currentTimeMillis()+15000;while(System.currentTimeMillis()<until){if("true".equals(mainJs(scenario,"Number(document.querySelector('#baseCount')?.textContent.replaceAll(',',''))>0")))break;Thread.sleep(100);}
   mainJs(scenario,"window.fixtureClosed=[];window.addEventListener('parts-epc-closed',event=>window.fixtureClosed.push(event.detail.requestId))");
   scenario.onActivity(a->{try{java.lang.reflect.Field active=MainActivity.class.getDeclaredField("epcRequestId");active.setAccessible(true);active.set(a,"different-active-request");}catch(Exception error){throw new AssertionError(error);}a.new LocalTools().openEpc("{\"requestId\":\"invalid-base-request\",\"vin\":\""+EpcAdapterTest.VIN+"\",\"bases\":[\"\"]}");a.new LocalTools().openEpc("{\"requestId\":\"missing-vin-request\",\"bases\":[\"1104\"]}");});
   until=System.currentTimeMillis()+10000;String closed="";while(System.currentTimeMillis()<until){closed=mainJs(scenario,"JSON.stringify(window.fixtureClosed)");if(closed.contains("missing-vin-request"))break;Thread.sleep(100);}assertTrue(closed.contains("invalid-base-request"));assertTrue(closed.contains("missing-vin-request"));
   scenario.onActivity(a->{try{java.lang.reflect.Field active=MainActivity.class.getDeclaredField("epcRequestId");active.setAccessible(true);assertEquals("different-active-request",active.get(a));}catch(Exception error){throw new AssertionError(error);}});
  }
 }
 private static String mainJs(ActivityScenario<MainActivity> scenario,String script)throws Exception{
  java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);AtomicReference<String> result=new AtomicReference<>();scenario.onActivity(a->a.getWebView().evaluateJavascript(script,value->{result.set(value);done.countDown();}));assertTrue(done.await(10,java.util.concurrent.TimeUnit.SECONDS));return result.get();
 }

 @Test public void nativeResultMustMatchTheOpenRequestVehicleAndBase()throws Exception{
  String id="lookup-one",vin=EpcAdapterTest.VIN;String[] bases={"1104","1105"};JSONObject selected=new JSONObject().put("requestId",id).put("vin",vin).put("base","1104").put("serviceNumber","TEST-1104-A");
  assertTrue(MainActivity.validEpcResult(selected.toString(),id,vin,bases));assertFalse(MainActivity.validEpcResult(selected.toString(),"old-request",vin,bases));assertFalse(MainActivity.validEpcResult(selected.toString(),id,EpcAdapterTest.OTHER_VIN,bases));assertFalse(MainActivity.validEpcResult(selected.toString(),id,vin,new String[]{"6731"}));assertFalse(MainActivity.validEpcResult(selected.toString(),null,vin,bases));selected.put("requiresCatalogReview",true);assertFalse(MainActivity.validEpcResult(selected.toString(),id,vin,bases));
 }
}
