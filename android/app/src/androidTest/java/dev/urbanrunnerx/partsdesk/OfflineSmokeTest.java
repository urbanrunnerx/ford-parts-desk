package dev.urbanrunnerx.partsdesk;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import android.os.Build;
import android.graphics.Insets;
import android.view.WindowInsets;
import android.webkit.WebView;
import android.widget.FrameLayout;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class OfflineSmokeTest {
 private String js(ActivityScenario<MainActivity> scenario,String code)throws Exception{
  CountDownLatch done=new CountDownLatch(1);AtomicReference<String> result=new AtomicReference<>();
  scenario.onActivity(a->a.getWebView().evaluateJavascript(code,v->{result.set(v);done.countDown();}));
  assertTrue("JavaScript callback timed out",done.await(10,TimeUnit.SECONDS));return result.get();
 }
 private void until(ActivityScenario<MainActivity> s,String code,String expected)throws Exception{
  long end=System.currentTimeMillis()+30000;String last="";
  while(System.currentTimeMillis()<end){last=js(s,code);if(expected.equals(last))return;Thread.sleep(250);}
  assertEquals(expected,last);
 }
 private static void assertNativeInsets(MainActivity activity){
  WebView web=activity.getWebView();assertTrue("A native container must own system insets",web.getParent() instanceof FrameLayout);FrameLayout frame=(FrameLayout)web.getParent();assertEquals("app-content-frame",frame.getTag());
  WindowInsets root=frame.getRootWindowInsets();assertNotNull("System insets must be dispatched before capturing the app",root);
  if(Build.VERSION.SDK_INT>=30){Insets safe=root.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());assertEquals(safe.left,frame.getPaddingLeft());assertEquals(safe.top,frame.getPaddingTop());assertEquals(safe.right,frame.getPaddingRight());assertEquals(safe.bottom,frame.getPaddingBottom());}
  int[] webPosition=new int[2],framePosition=new int[2];web.getLocationOnScreen(webPosition);frame.getLocationOnScreen(framePosition);
  assertEquals("HTML viewport must start below the status bar",framePosition[1]+frame.getPaddingTop(),webPosition[1]);assertEquals("HTML viewport must end above navigation/keyboard insets",framePosition[1]+frame.getHeight()-frame.getPaddingBottom(),webPosition[1]+web.getHeight());
  assertEquals(framePosition[0]+frame.getPaddingLeft(),webPosition[0]);assertEquals(framePosition[0]+frame.getWidth()-frame.getPaddingRight(),webPosition[0]+web.getWidth());assertEquals(0,web.getPaddingTop());assertEquals(0,web.getPaddingBottom());
 }
 @Test public void offlineCatalogAndGroupedSearchLoadInAndroid()throws Exception{
  try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
   until(s,"Number(document.querySelector('#baseCount')?.textContent.replaceAll(',',''))>0","true");
   js(s,"localStorage.clear();window.__beforeReload=true;location.reload()");
   until(s,"window.__beforeReload===undefined && Number(document.querySelector('#baseCount')?.textContent.replaceAll(',',''))>0","true");
   assertEquals("\"object\"",js(s,"typeof PartsNative"));
   s.onActivity(OfflineSmokeTest::assertNativeInsets);
   assertEquals("Native insets must not also be counted as CSS safe-area padding","0",js(s,"(()=>{const probe=document.createElement('div');probe.style.cssText='position:absolute;visibility:hidden;pointer-events:none;padding:env(safe-area-inset-top) env(safe-area-inset-right) env(safe-area-inset-bottom) env(safe-area-inset-left)';document.body.append(probe);const css=getComputedStyle(probe);const sum=['paddingTop','paddingRight','paddingBottom','paddingLeft'].reduce((n,key)=>n+(parseFloat(css[key])||0),0);probe.remove();return sum})()"));
   GuidedLookupTest.savePreview(s,"parts-home.png");
   js(s,"document.querySelector('[data-query=\"purge valve\"]').click()");
   until(s,"document.querySelectorAll('#results .part-card').length","1");
   assertEquals("true",js(s,"document.querySelector('#results').innerText.includes('9C915') && document.querySelector('#results').innerText.includes('9D289')"));
   js(s,"if(document.documentElement.dataset.theme!=='dark')document.querySelector('#theme').click()");
   until(s,"document.documentElement.dataset.theme","\"dark\"");
   GuidedLookupTest.savePreview(s,"parts-search-dark.png");
   js(s,"document.querySelector('#results [data-detail]').click()");
   until(s,"document.querySelector('#detail').open","true");
   js(s,"document.querySelector('[data-add-job]').click()");
   assertEquals("1",js(s,"JSON.parse(localStorage.getItem('partsdesk-v1-worksheet')).rows.length"));
  }
 }
}

