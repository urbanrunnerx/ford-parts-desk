package dev.urbanrunnerx.partsdesk;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Native opt-in/lifecycle coverage. All parts and image bytes are synthetic. */
@RunWith(AndroidJUnit4.class)
public class DiagramPreviewTest {
 private static PartDiagramPanel panel(EpcActivity a){return a.getWindow().getDecorView().findViewWithTag("part-diagram-panel");}
 private static String text(View view){StringBuilder value=new StringBuilder();if(view instanceof TextView)value.append(((TextView)view).getText()).append('\n');if(view instanceof ViewGroup){ViewGroup parent=(ViewGroup)view;for(int i=0;i<parent.getChildCount();i++)value.append(text(parent.getChildAt(i)));}return value.toString();}
 private static Button button(View view,String title){if(view instanceof Button&&title.equals(((Button)view).getText().toString()))return (Button)view;if(view instanceof ViewGroup){ViewGroup parent=(ViewGroup)view;for(int i=0;i<parent.getChildCount();i++){Button found=button(parent.getChildAt(i),title);if(found!=null)return found;}}return null;}
 private static void awaitText(ActivityScenario<EpcActivity> scenario,String expected)throws Exception{long end=System.currentTimeMillis()+15000;AtomicReference<String> found=new AtomicReference<>("");while(System.currentTimeMillis()<end){scenario.onActivity(a->found.set(text(a.getWindow().getDecorView())));if(found.get().contains(expected))return;Thread.sleep(100);}fail("Missing "+expected+" in "+found.get());}
 private static void open(ActivityScenario<EpcActivity> scenario)throws Exception{long end=System.currentTimeMillis()+10000;AtomicBoolean clicked=new AtomicBoolean();while(System.currentTimeMillis()<end&&!clicked.get()){scenario.onActivity(a->{View view=a.getWindow().getDecorView().findViewWithTag("view-diagram-TEST-1104-A");if(view!=null&&view.isEnabled())clicked.set(view.performClick());});if(!clicked.get())Thread.sleep(50);}assertTrue("Per-part diagram button must become available",clicked.get());}
 private static void load(ActivityScenario<EpcActivity> scenario)throws Exception{EpcAdapterTest.load(scenario,EpcAdapterTest.partsFixture());scenario.onActivity(a->{a.setCatalogVisible(false);a.refreshSnapshot();});awaitText(scenario,"View diagram");}
 private static String png(int width,int height){Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.BLUE);ByteArrayOutputStream out=new ByteArrayOutputStream();assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));bitmap.recycle();return "data:image/png;base64,"+Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP);}

 // Mirrors the observed Snap-on layered-canvas container. Artwork is a synthetic fixture.
 static String illustratedFixture(){
  return "<style>#imagePanel{width:466px;height:582px}#image-container td{position:relative;width:466px;height:582px}#image-container canvas{position:absolute;left:0;top:0;width:466px;height:582px}</style>"
   +"<div id='sbsPanel'><div id='imagePanel'><div id='image-container'><snapon-imageviewer-component><table><tbody><tr><td>"
   +"<canvas id='fixture-diagram-base' width='466' height='582' style='z-index:0'></canvas><canvas id='fixture-diagram-lines' width='466' height='582' style='z-index:1'></canvas><canvas id='fixture-diagram-labels' width='466' height='582' style='z-index:2'></canvas>"
   +"</td></tr></tbody></table></snapon-imageviewer-component></div></div><div id='partsPanel'>"
   +EpcAdapterTest.partsFixture().replace("<div role='grid'>","<div id='partsGrid' role='grid'>")+"</div></div><div id='hiddenDiv' style='display:none'><canvas width='466' height='582'></canvas></div>"
   +"<script>window.fixtureExports=0;const originalExport=HTMLCanvasElement.prototype.toDataURL;HTMLCanvasElement.prototype.toDataURL=function(){window.fixtureExports++;return originalExport.apply(this,arguments)};"
   +"const base=document.querySelector('#fixture-diagram-base').getContext('2d');base.fillStyle='#f8fafc';base.fillRect(0,0,466,582);"
   +"const line=document.querySelector('#fixture-diagram-base').getContext('2d');line.strokeStyle='#172b49';line.lineWidth=4;line.beginPath();line.ellipse(235,290,90,130,-0.3,0,Math.PI*2);line.stroke();line.beginPath();line.ellipse(235,290,40,58,-0.3,0,Math.PI*2);line.stroke();"
   +"for(let i=0;i<5;i++){const angle=i*Math.PI*2/5;line.beginPath();line.arc(235+Math.cos(angle)*68,290+Math.sin(angle)*100,10,0,Math.PI*2);line.stroke();}"
   +"const labels=document.querySelector('#fixture-diagram-base').getContext('2d');labels.fillStyle='#60718b';labels.font='16px sans-serif';labels.fillText('SYNTHETIC CATALOG FIXTURE',60,75);labels.fillText('Front knuckle and hub',130,485);labels.fillText('1104',95,175);labels.beginPath();labels.moveTo(135,180);labels.lineTo(193,232);labels.stroke();</script>";
 }
 private static void awaitImage(ActivityScenario<EpcActivity> scenario)throws Exception{long end=System.currentTimeMillis()+15000;AtomicBoolean shown=new AtomicBoolean();while(System.currentTimeMillis()<end){scenario.onActivity(a->shown.set(panel(a)!=null&&panel(a).hasImage()));if(shown.get())return;Thread.sleep(100);}AtomicReference<String> state=new AtomicReference<>();scenario.onActivity(a->state.set(text(a.getWindow().getDecorView())));fail("Expected a verified native image: "+state.get());}
 @Test public void currentRenderedDrawingBecomesAnOptionalZoomableImageAndSourceChangesClearIt()throws Exception{
  try(ActivityScenario<EpcActivity> scenario=EpcAdapterTest.launch()){
   EpcAdapterTest.load(scenario,illustratedFixture());scenario.onActivity(a->{a.setCatalogVisible(false);a.refreshSnapshot();});awaitText(scenario,"View diagram");
   assertEquals("Normal lookup must not export or open diagrams","0",EpcAdapterTest.js(scenario,"window.fixtureExports"));open(scenario);awaitImage(scenario);
   assertEquals("Only the requested preview exports a drawing","1",EpcAdapterTest.js(scenario,"window.fixtureExports"));
   scenario.onActivity(a->{PartDiagramPanel preview=panel(a);assertTrue(button(preview,"Zoom in").isEnabled());button(preview,"Zoom in").performClick();button(preview,"Zoom out").performClick();button(preview,"Fit").performClick();assertTrue(text(preview).contains("Catalog Call/Base: 1104"));assertFalse(a.isFinishing());});
   GuidedLookupTest.savePreview(scenario,"guided-diagram.png");
   EpcAdapterTest.js(scenario,"const overlay=document.querySelector('#fixture-diagram-labels').getContext('2d');overlay.fillStyle='magenta';overlay.fillRect(200,200,20,20)");Thread.sleep(1600);scenario.onActivity(a->assertTrue("Transient selection overlays must not alter the verified drawing",panel(a).hasImage()));
   EpcAdapterTest.js(scenario,"const changed=document.querySelector('#fixture-diagram-base').getContext('2d');changed.fillStyle='red';changed.fillRect(10,10,10,10)");awaitText(scenario,"The catalog or illustration changed");
   scenario.onActivity(a->{assertFalse("Changed source pixels must not leave a stale illustration on screen",panel(a).hasImage());assertEquals(View.GONE,panel(a).findViewWithTag("diagram-retry").getVisibility());a.onBackPressed();assertNull(panel(a));});
   assertEquals("Previewing cannot select parts or add to a picklist","0",EpcAdapterTest.js(scenario,"window.fixtureRowClicks"));
  }
 }
 @Test public void cachedPartReturnsToItsLiveHeadingBeforeShowingADiagram()throws Exception{
  String fixture=illustratedFixture()+"<script>const grid=document.querySelector('#partsGrid');grid.innerHTML='<div role=row><div role=columnheader>Call/Base</div><div role=columnheader>Part Description</div><div role=columnheader>Part Number</div></div><div id=viewport class=ag-body-viewport style=\"height:120px;overflow:auto\"><div style=\"height:900px;position:relative\"><div id=fixture-rows></div></div></div>';"
   +"const viewport=document.querySelector('#viewport'),rows=document.querySelector('#fixture-rows');const cell=(id,value)=>'<div role=gridcell col-id='+id+'>'+value+'</div>';const row=(id,base,number)=>'<div role=row row-id='+id+' onclick=\"window.fixtureRowClicks++\">'+cell('calloutLabel',base)+cell('renderedDescription',base?'Fixture hub':'')+cell('formattedPartNumber',number)+'</div>';"
   +"function paint(){rows.style.transform='translateY('+viewport.scrollTop+'px)';rows.innerHTML=viewport.scrollTop<50?row('a','1104','TEST-1104-A')+row('b','','TEST-1104-B'):row('b','','TEST-1104-B')+row('c','','TEST-1104-C');}viewport.addEventListener('scroll',paint);paint();</script>";
  try(ActivityScenario<EpcActivity> scenario=EpcAdapterTest.launch()){
   EpcAdapterTest.load(scenario,fixture);scenario.onActivity(EpcActivity::refreshSnapshot);awaitText(scenario,"TEST-1104-A");
   scenario.onActivity(a->{Button more=button(a.getWindow().getDecorView(),"Load more catalog results");assertNotNull(more);more.performClick();});awaitText(scenario,"TEST-1104-C");
   open(scenario);awaitImage(scenario);assertEquals("A cached row must be freshly visible under its original heading","0",EpcAdapterTest.js(scenario,"document.querySelector('#viewport').scrollTop"));
   scenario.onActivity(a->{try{java.lang.reflect.Field review=EpcActivity.class.getDeclaredField("reviewDialog");review.setAccessible(true);assertNull("Diagram intent must not accidentally open Save to job review",review.get(a));}catch(Exception e){throw new AssertionError(e);}assertFalse(a.isFinishing());a.onBackPressed();});
   assertEquals("0",EpcAdapterTest.js(scenario,"window.fixtureRowClicks"));
  }
 }
 @Test public void previewIsOptInAndClosePreservesPartListAndScroll()throws Exception{
  try(ActivityScenario<EpcActivity> scenario=EpcAdapterTest.launch()){
   load(scenario);AtomicReference<View> original=new AtomicReference<>();AtomicReference<Integer> scroll=new AtomicReference<>();
   scenario.onActivity(a->{assertNull("Results must never open diagrams automatically",panel(a));View parts=a.getWindow().getDecorView().findViewWithTag("guided-parts");original.set(parts);ScrollView guided=(ScrollView)a.getWindow().getDecorView().findViewWithTag("guided-content").getParent();guided.scrollTo(0,180);scroll.set(guided.getScrollY());assertTrue(scroll.get()>0);});
   assertEquals("0",EpcAdapterTest.js(scenario,"window.fixtureRowClicks"));open(scenario);awaitText(scenario,"No verified diagram");
   scenario.onActivity(a->{PartDiagramPanel preview=panel(a);assertNotNull(preview);assertFalse(preview.hasImage());assertNotNull(button(preview,"Open catalog"));assertFalse(button(preview,"Zoom in").isEnabled());assertTrue(preview.findViewWithTag("diagram-close").performClick());assertNull(panel(a));assertSame("Dismissing keeps existing result cards mounted",original.get(),a.getWindow().getDecorView().findViewWithTag("guided-parts"));assertEquals(scroll.get().intValue(),((ScrollView)a.getWindow().getDecorView().findViewWithTag("guided-content").getParent()).getScrollY());assertFalse(a.isFinishing());});
   assertEquals("Looking at a diagram cannot select a catalog row","0",EpcAdapterTest.js(scenario,"window.fixtureRowClicks"));
  }
 }
 @Test public void backAndRecreationDoNotReopenARequestedPreview()throws Exception{
  try(ActivityScenario<EpcActivity> scenario=EpcAdapterTest.launch()){
   load(scenario);open(scenario);scenario.onActivity(a->{assertNotNull(panel(a));a.onBackPressed();assertNull(panel(a));assertFalse(a.isFinishing());});
   // A delayed adapter callback must not resurrect the dismissed view.
   Thread.sleep(1800);scenario.onActivity(a->assertNull(panel(a)));open(scenario);scenario.recreate();awaitText(scenario,"View diagram");scenario.onActivity(a->assertNull("Rotation/backgrounding requires a fresh opt-in",panel(a)));
  }
 }
 @Test public void catalogChangeInvalidatesOpenPreviewWithoutSaving()throws Exception{
  try(ActivityScenario<EpcActivity> scenario=EpcAdapterTest.launch()){
   load(scenario);open(scenario);awaitText(scenario,"No verified diagram");
   EpcAdapterTest.js(scenario,"document.querySelector('#toolbar-vin-url-anchor').textContent='"+EpcAdapterTest.OTHER_VIN+"'");awaitText(scenario,"The catalog or illustration changed");
   scenario.onActivity(a->{assertNotNull(panel(a));assertFalse(panel(a).hasImage());assertEquals(View.GONE,panel(a).findViewWithTag("diagram-retry").getVisibility());assertFalse(a.isFinishing());a.onBackPressed();});awaitText(scenario,"Load this vehicle");
  }
 }
 @Test public void fallbackOpensOnlyTheExistingIsolatedCatalog()throws Exception{
  try(ActivityScenario<EpcActivity> scenario=EpcAdapterTest.launch()){
   load(scenario);AtomicReference<android.webkit.WebView> original=new AtomicReference<>();scenario.onActivity(a->original.set(a.getWebView()));open(scenario);awaitText(scenario,"No verified diagram");
   scenario.onActivity(a->{panel(a).findViewWithTag("diagram-catalog").performClick();assertNull(panel(a));assertSame(original.get(),a.getWebView());assertFalse(a.getWindow().getDecorView().findViewWithTag("guided-content").isShown());a.onBackPressed();assertTrue(a.getWindow().getDecorView().findViewWithTag("guided-content").isShown());assertFalse(a.isFinishing());});
   assertEquals("\"undefined\"",EpcAdapterTest.js(scenario,"typeof PartsNative"));assertEquals("0",EpcAdapterTest.js(scenario,"window.fixtureRowClicks"));
  }
 }
 @Test public void responseMustMatchTheExactPartSnapshotRequestAndIllustration()throws Exception{
  JSONObject part=new JSONObject().put("id","part-fixture").put("vin",EpcAdapterTest.VIN).put("base","1104").put("serviceNumber","TEST-1104-A");
  JSONObject snapshot=new JSONObject().put("snapshotId","snapshot-fixture").put("diagramSourceId","source-fixture");
  JSONObject result=new JSONObject().put("part",part).put("requestId","request-fixture").put("snapshotId","snapshot-fixture").put("diagram",new JSONObject().put("status","available").put("sourceId","source-fixture"));
  assertTrue(EpcActivity.validDiagramResult(result,part,snapshot,EpcAdapterTest.VIN,"1104","request-fixture"));
  assertFalse(EpcActivity.validDiagramResult(result,part,snapshot,EpcAdapterTest.OTHER_VIN,"1104","request-fixture"));assertFalse(EpcActivity.validDiagramResult(result,part,snapshot,EpcAdapterTest.VIN,"6731","request-fixture"));assertFalse(EpcActivity.validDiagramResult(result,part,snapshot,EpcAdapterTest.VIN,"1104","old-request"));
  for(String field:new String[]{"id","vin","base","serviceNumber"}){JSONObject altered=new JSONObject(result.toString());altered.getJSONObject("part").put(field,"changed");assertFalse(field,EpcActivity.validDiagramResult(altered,part,snapshot,EpcAdapterTest.VIN,"1104","request-fixture"));}
  for(String field:new String[]{"snapshotId","requestId"}){JSONObject altered=new JSONObject(result.toString());altered.put(field,"changed");assertFalse(field,EpcActivity.validDiagramResult(altered,part,snapshot,EpcAdapterTest.VIN,"1104","request-fixture"));}
  JSONObject altered=new JSONObject(result.toString());altered.getJSONObject("diagram").put("sourceId","other-illustration");assertFalse(EpcActivity.validDiagramResult(altered,part,snapshot,EpcAdapterTest.VIN,"1104","request-fixture"));
  altered=new JSONObject(result.toString());altered.getJSONObject("part").put("visible",false);assertFalse(EpcActivity.validDiagramResult(altered,part,snapshot,EpcAdapterTest.VIN,"1104","request-fixture"));
  altered=new JSONObject(result.toString());altered.getJSONObject("part").put("requiresCatalogReview",true);assertFalse(EpcActivity.validDiagramResult(altered,part,snapshot,EpcAdapterTest.VIN,"1104","request-fixture"));
 }
 @Test public void onlyBoundedPngDataCanReachNativeImageRenderer(){
  assertNull(PartDiagramPanel.decode("https://snaponepc.com/picture.png"));assertNull(PartDiagramPanel.decode("file:///data/private.png"));assertNull(PartDiagramPanel.decode("data:image/svg+xml;base64,PHN2Zz4="));assertNull(PartDiagramPanel.decode("data:image/png;base64,AAAA"));assertNull(PartDiagramPanel.decode("data:image/png;base64,"+"A".repeat(1500000)));
  String valid=png(640,400);Bitmap decoded=PartDiagramPanel.decode(valid);assertNotNull(decoded);assertEquals(640,decoded.getWidth());assertEquals(400,decoded.getHeight());decoded.recycle();assertNull("Reject oversize dimensions even with small compressed bytes",PartDiagramPanel.decode(png(4097,1)));assertNull("Reject oversized decoded allocations",PartDiagramPanel.decode(png(3000,2001)));
  try(ActivityScenario<EpcActivity> scenario=EpcAdapterTest.launch()){
   scenario.onActivity(a->{PartDiagramPanel preview=new PartDiagramPanel(a,"TEST-1104-A",EpcAdapterTest.VIN,"1104",()->{},()->{},()->{});preview.layout(0,0,900,1600);assertTrue(preview.showImage(valid,"Fixture illustration","1104"));assertTrue(preview.hasImage());assertTrue(button(preview,"Zoom in").isEnabled());button(preview,"Zoom in").performClick();button(preview,"Zoom out").performClick();button(preview,"Fit").performClick();preview.unavailable("The catalog changed",false);assertFalse("Stale bitmap must be released",preview.hasImage());assertFalse(button(preview,"Zoom in").isEnabled());});
  }
 }
}
