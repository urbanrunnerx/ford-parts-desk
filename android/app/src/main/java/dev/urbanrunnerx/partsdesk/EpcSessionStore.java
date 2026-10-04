package dev.urbanrunnerx.partsdesk;

import android.content.Context;
import android.content.MutableContextWrapper;
import android.os.Handler;
import android.net.Uri;
import android.net.http.SslError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;
import android.webkit.WebResourceResponse;
import android.webkit.SslErrorHandler;
import android.webkit.RenderProcessGoneDetail;
import android.os.Looper;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** One short-lived, in-process EPC page. Cookies remain managed by WebView itself. */
final class EpcSessionStore {
 static final long IDLE_MILLIS=5*60*1000;
 static final class Session {
  final MutableContextWrapper context;
  final WebView web;
  boolean leased=true,destroyed,needsReload;
  Session(Context application){context=new MutableContextWrapper(application);web=new WebView(context);}
 }
 private final Context application;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private Session cached;
 private final Runnable expire=()->discardIdle();
 EpcSessionStore(Context context){application=context.getApplicationContext();}
 Session acquire(Context activity){
  assertMainThread();handler.removeCallbacks(expire);
  // A second lookup never steals a live Activity's WebView.
  if(cached==null||cached.destroyed||cached.leased)cached=new Session(application);
  cached.leased=true;cached.context.setBaseContext(activity);return cached;
 }
 void release(Session session){
  assertMainThread();if(session==null||session.destroyed)return;
  ViewGroup parent=(ViewGroup)session.web.getParent();if(parent!=null)parent.removeView(session.web);
  session.web.onPause();session.web.clearFocus();
  // Clients/listeners belong to the Activity and must not survive its lifetime.
  session.web.setWebViewClient(new WebViewClient(){
   @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){Uri uri=request.getUrl();String host=uri.getHost();return !"https".equals(uri.getScheme())||host==null||!(host.equals("snaponepc.com")||host.endsWith(".snaponepc.com"));}
   @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame())session.needsReload=true;}
   @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse response){if(request.isForMainFrame()&&response.getStatusCode()>=400)session.needsReload=true;}
   @Override public void onReceivedSslError(WebView view,SslErrorHandler handler,SslError error){handler.cancel();session.needsReload=true;}
   @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){discardRenderer(session);return true;}
  });session.web.setWebChromeClient(new WebChromeClient());
  // No Activity-owned view listeners are installed on the cached WebView.
  session.context.setBaseContext(application);session.leased=false;
  if(session!=cached){destroy(session);return;}
  handler.removeCallbacks(expire);handler.postDelayed(expire,IDLE_MILLIS);
 }
 void discard(Session session){assertMainThread();if(session==cached){cached=null;handler.removeCallbacks(expire);}destroy(session);}
 void discardRenderer(Session session){assertMainThread();if(session==cached){cached=null;handler.removeCallbacks(expire);}destroy(session,true);}
 void discardIdle(){assertMainThread();if(cached!=null&&!cached.leased)discard(cached);}
 private void destroy(Session session){destroy(session,false);}
 private void destroy(Session session,boolean rendererGone){
  if(session==null||session.destroyed)return;session.destroyed=true;session.leased=false;
  ViewGroup parent=(ViewGroup)session.web.getParent();if(parent!=null)parent.removeView(session.web);
  if(!rendererGone)session.web.stopLoading();session.web.setWebViewClient(new WebViewClient());session.web.setWebChromeClient(new WebChromeClient());
  session.context.setBaseContext(application);session.web.destroy();
 }
 private void assertMainThread(){if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("EPC sessions require the main thread");}
}
