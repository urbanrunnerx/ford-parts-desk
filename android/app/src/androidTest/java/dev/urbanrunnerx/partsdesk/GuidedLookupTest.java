package dev.urbanrunnerx.partsdesk;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.webkit.WebView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Exercises the employee-facing native screen against synthetic catalog HTML. */
@RunWith(AndroidJUnit4.class)
public class GuidedLookupTest {
 private static String shell(String command)throws Exception{
  ParcelFileDescriptor descriptor=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);
  try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(descriptor);ByteArrayOutputStream out=new ByteArrayOutputStream()){
   byte[] buffer=new byte[4096];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);return out.toString("UTF-8").trim();
  }
 }

 private static <T extends Activity> boolean previewReady(ActivityScenario<T> scenario,String name){
  AtomicBoolean ready=new AtomicBoolean();
  scenario.onActivity(a->{
   View root=a.getWindow().getDecorView();
   View target=name.equals("guided-choices.png")?root.findViewWithTag("guided-choices"):name.equals("guided-parts.png")?root.findViewWithTag("guided-parts"):root;
   ready.set(target!=null&&target.isShown()&&target.isLaidOut()&&target.getWidth()>0&&target.getHeight()>0);
  });
  return ready.get();
 }

 private static <T extends Activity> void awaitPreviewPaint(ActivityScenario<T> scenario,String name)throws Exception{
  if(name.equals("snapon-menu.png")||name.equals("parts-home.png")||name.equals("parts-search-dark.png")){
   CountDownLatch visualState=new CountDownLatch(1);
   scenario.onActivity(a->((MainActivity)a).getWebView().postVisualStateCallback(System.nanoTime(),new WebView.VisualStateCallback(){
    @Override public void onComplete(long requestId){visualState.countDown();}
   }));
   assertTrue("The WebView content must reach its render state",visualState.await(10,TimeUnit.SECONDS));
  }
  long deadline=System.currentTimeMillis()+15000;
  while(System.currentTimeMillis()<deadline){
   if(!previewReady(scenario,name)){Thread.sleep(100);continue;}
   CountDownLatch painted=new CountDownLatch(1);
   scenario.onActivity(a->{
    View root=a.getWindow().getDecorView();
    // Message-queue idle can precede rendering. Wait for a submitted frame, then presentation frames.
    Runnable committed=()->root.postOnAnimation(()->root.postOnAnimation(painted::countDown));
    if(Build.VERSION.SDK_INT>=29&&root.isHardwareAccelerated()){
     root.getViewTreeObserver().registerFrameCommitCallback(committed);root.invalidate();
    }else root.postOnAnimation(committed);
   });
   assertTrue("The preview must have a committed display frame",painted.await(10,TimeUnit.SECONDS));
   if(previewReady(scenario,name))return;
  }
  fail("Expected preview content was not visibly laid out: "+name);
 }

 static <T extends Activity> void savePreview(ActivityScenario<T> scenario,String name)throws Exception{
  AtomicReference<File> destination=new AtomicReference<>();
  scenario.onActivity(a->destination.set(new File(a.getExternalFilesDir(null),"preview/"+name)));
  File file=destination.get();assertTrue(file.getParentFile().isDirectory()||file.getParentFile().mkdirs());
  InstrumentationRegistry.getInstrumentation().waitForIdleSync();
  awaitPreviewPaint(scenario,name);
  Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull("Emulator screenshot failed",bitmap);
  try(FileOutputStream out=new FileOutputStream(file)){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}finally{bitmap.recycle();}
  // CI uninstalls the test app after execution; export fixture previews before that cleanup.
  assertTrue(name.matches("(?:guided-(?:choices|parts)|snapon-menu|parts-home|parts-search-dark)\\.png"));
  String shared="/sdcard/Download/partsdesk-preview/"+name;
  shell("mkdir -p /sdcard/Download/partsdesk-preview");
  shell("cp "+file.getAbsolutePath()+" "+shared);
  String copied=shell("wc -c "+shared);
  assertTrue("The fixture preview must survive app uninstall: "+copied,copied.matches(file.length()+"\\s+.*"));
 }

 private static String nativeText(View view){
  StringBuilder text=new StringBuilder();
  if(view instanceof TextView)text.append(((TextView)view).getText()).append('\n');
  if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int n=0;n<group.getChildCount();n++)text.append(nativeText(group.getChildAt(n)));}
  return text.toString();
 }

 private static String screenText(ActivityScenario<EpcActivity> scenario){
  AtomicReference<String> text=new AtomicReference<>("");scenario.onActivity(a->text.set(nativeText(a.getWindow().getDecorView())));return text.get();
 }

 private static void awaitText(ActivityScenario<EpcActivity> scenario,String expected)throws Exception{
  long deadline=System.currentTimeMillis()+15000;String latest="";
  while(System.currentTimeMillis()<deadline){latest=screenText(scenario);if(latest.contains(expected))return;Thread.sleep(100);}
  fail("Native interface did not display '"+expected+"'. Last text: "+latest);
 }

 private static Spinner spinner(View view){
  if(view instanceof Spinner)return (Spinner)view;
  if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int n=0;n<group.getChildCount();n++){Spinner result=spinner(group.getChildAt(n));if(result!=null)return result;}}
  return null;
 }

 private static Button button(View view,String text){
  if(view instanceof Button&&text.equals(((Button)view).getText().toString()))return (Button)view;
  if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int n=0;n<group.getChildCount();n++){Button result=button(group.getChildAt(n),text);if(result!=null)return result;}}
  return null;
 }

 @Test public void guidedScreenShowsPartDetailsAndRefreshesWhenCatalogChanges()throws Exception{
  try(ActivityScenario<EpcActivity> scenario=EpcAdapterTest.launch()){
   EpcAdapterTest.load(scenario,EpcAdapterTest.partsFixture());
   scenario.onActivity(a->{a.setCatalogVisible(false);a.refreshSnapshot();});
   awaitText(scenario,"TEST-1104-A");awaitText(scenario,"With fixture option A");awaitText(scenario,"Front position only");
   scenario.onActivity(a->{
    View content=a.getWindow().getDecorView().findViewWithTag("guided-content");
    assertNotNull("Native guided content should exist",content);assertTrue("Native results should be visible",content.isShown());
    assertNotNull("Service results should have native review controls",button(content,"Review this part"));
   });
   savePreview(scenario,"guided-parts.png");
   assertEquals("0",EpcAdapterTest.js(scenario,"window.fixtureRowClicks"));
   EpcAdapterTest.js(scenario,"document.querySelector('[col-id=formattedPartNumber]').textContent='TEST-1104-B'");
   scenario.onActivity(a->{View refresh=a.getWindow().getDecorView().findViewWithTag("guided-refresh");assertNotNull(refresh);assertTrue(refresh.performClick());});
   awaitText(scenario,"TEST-1104-B");
   assertFalse("Refresh must replace stale native result cards",screenText(scenario).contains("TEST-1104-A"));
   assertEquals("0",EpcAdapterTest.js(scenario,"window.fixtureRowClicks"));
  }
 }

 @Test public void dropdownSelectionUsesTheChosenCatalogOption()throws Exception{
  try(ActivityScenario<EpcActivity> scenario=EpcAdapterTest.launch()){
   EpcAdapterTest.load(scenario,EpcAdapterTest.choicesFixture());
   scenario.onActivity(a->{a.setCatalogVisible(false);a.refreshSnapshot();});
   awaitText(scenario,"Open selection");
   scenario.onActivity(a->{
    View choices=a.getWindow().getDecorView().findViewWithTag("guided-choices");assertNotNull(choices);
    Spinner dropdown=spinner(choices);assertNotNull("Catalog choices should use a native dropdown",dropdown);
    assertTrue(dropdown.getCount()>=2);dropdown.setSelection(dropdown.getCount()-1);
   });
   assertEquals("Choosing a dropdown value should not immediately navigate","\"\"",EpcAdapterTest.js(scenario,"window.fixtureChoice"));
   scenario.onActivity(a->{Button open=button(a.getWindow().getDecorView(),"Open selection");assertNotNull(open);assertTrue(open.performClick());});
   long deadline=System.currentTimeMillis()+15000;String actual="";
   while(System.currentTimeMillis()<deadline){actual=EpcAdapterTest.js(scenario,"window.fixtureChoice");if("\"rear\"".equals(actual))break;Thread.sleep(100);}
   assertEquals("The second duplicate label must still target the rear catalog branch","\"rear\"",actual);
  }
 }

 @Test public void systemSectionAndIllustrationMenusAreNativeAndVisible()throws Exception{
  try(ActivityScenario<EpcActivity> scenario=EpcAdapterTest.launch()){
   EpcAdapterTest.load(scenario,EpcAdapterTest.listChoicesFixture());
   scenario.onActivity(a->{a.setCatalogVisible(false);a.refreshSnapshot();});
   awaitText(scenario,"System");awaitText(scenario,"Section");awaitText(scenario,"Illustration");
   scenario.onActivity(a->{View choices=a.getWindow().getDecorView().findViewWithTag("guided-choices");assertNotNull(choices);assertTrue(choices.isShown());});
   savePreview(scenario,"guided-choices.png");
  }
 }

 @Test public void failedCatalogActionRestoresReadableResults()throws Exception{
  try(ActivityScenario<EpcActivity> scenario=EpcAdapterTest.launch()){
   EpcAdapterTest.load(scenario,EpcAdapterTest.partsFixture());
   EpcAdapterTest.js(scenario,"[...document.querySelectorAll('button')].find(b=>b.textContent==='Search').remove()");
   scenario.onActivity(a->{a.setCatalogVisible(false);a.refreshSnapshot();});
   awaitText(scenario,"TEST-1104-A");
   scenario.onActivity(a->{Button find=button(a.getWindow().getDecorView(),"Find parts");assertNotNull(find);assertTrue(find.performClick());});
   awaitText(scenario,"Search controls could not be found");
   awaitText(scenario,"TEST-1104-A");
   assertFalse("Failed actions must not leave the native screen stuck on its loading card",screenText(scenario).contains("One moment…"));
   assertEquals("0",EpcAdapterTest.js(scenario,"window.fixtureRowClicks"));
  }
 }
}

