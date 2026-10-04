package dev.urbanrunnerx.partsdesk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.graphics.Insets;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.webkit.WebViewAssetLoader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;
import java.net.URL;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
 public static final String ORIGIN="https://appassets.androidplatform.net";
 private WebView web;
 private WebView printWeb;
 private ValueCallback<Uri[]> fileCallback;
 private String exportText;
 private String epcRequestId,epcVin;
 private String[] epcBases;
 private final ExecutorService decoderExecutor=Executors.newSingleThreadExecutor();
 private static final int PICK_FILE=41, SAVE_FILE=42, EPC_LOOKUP=43;
 public WebView getWebView(){return web;}

 @SuppressLint({"SetJavaScriptEnabled"})
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  if(state!=null){epcRequestId=state.getString("epc-request-id");epcVin=state.getString("epc-vin");epcBases=state.getStringArray("epc-bases");}
  web=new WebView(this);
  web.setBackgroundColor(0xfff6f8fb);
  // WebView padding does not inset its HTML viewport. Size it inside a native frame
  // instead, then consume the insets so CSS safe-area values cannot pad it twice.
  FrameLayout frame=new FrameLayout(this);frame.setTag("app-content-frame");frame.setBackgroundColor(0xff101d32);
  if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
  else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
  frame.setOnApplyWindowInsetsListener((v,insets)->{
   if(Build.VERSION.SDK_INT>=30){
    Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());
    v.setPadding(safe.left,safe.top,safe.right,safe.bottom);return WindowInsets.CONSUMED;
   }
   int left=insets.getSystemWindowInsetLeft(),top=insets.getSystemWindowInsetTop(),right=insets.getSystemWindowInsetRight(),bottom=insets.getSystemWindowInsetBottom();
   if(Build.VERSION.SDK_INT>=28&&insets.getDisplayCutout()!=null){android.view.DisplayCutout cutout=insets.getDisplayCutout();left=Math.max(left,cutout.getSafeInsetLeft());top=Math.max(top,cutout.getSafeInsetTop());right=Math.max(right,cutout.getSafeInsetRight());bottom=Math.max(bottom,cutout.getSafeInsetBottom());}
   v.setPadding(left,top,right,bottom);WindowInsets consumed=insets.consumeSystemWindowInsets();return Build.VERSION.SDK_INT>=28?consumed.consumeDisplayCutout():consumed;
  });
  frame.addView(web,new FrameLayout.LayoutParams(-1,-1));setContentView(frame);frame.requestApplyInsets();
  WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);
  s.setAllowFileAccess(false);s.setAllowContentAccess(true);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
  final WebViewAssetLoader loader=new WebViewAssetLoader.Builder().addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(this)).build();
  web.addJavascriptInterface(new LocalTools(),"PartsNative");
  web.setWebViewClient(new WebViewClient(){
   @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
    WebResourceResponse response=loader.shouldInterceptRequest(request.getUrl());
    return response!=null?response:new WebResourceResponse("text/plain","UTF-8",403,"Forbidden",null,new ByteArrayInputStream(new byte[0]));
   }
   @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
    Uri u=request.getUrl();
    if("https".equals(u.getScheme())&&"appassets.androidplatform.net".equals(u.getHost())&&u.getPath()!=null&&u.getPath().startsWith("/assets/"))return false;
    if("https".equals(u.getScheme())||"http".equals(u.getScheme())){
     try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception e){toast("No browser is available to open this link.");}
    }
    return true;
   }
  });
  web.setWebChromeClient(new WebChromeClient(){
   @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params){
    if(fileCallback!=null)fileCallback.onReceiveValue(null);fileCallback=callback;
    Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"text/csv","text/comma-separated-values","application/json","text/plain"});
    try{startActivityForResult(i,PICK_FILE);}catch(Exception e){fileCallback.onReceiveValue(null);fileCallback=null;toast("No file picker is available.");}
    return true;
   }
  });
  if(state==null||web.restoreState(state)==null)web.loadUrl(ORIGIN+"/assets/index.html");
 }
 private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
 public final class LocalTools {
  @JavascriptInterface public void printJob(String text){if(text==null||text.length()>200000)return;runOnUiThread(()->{if(printWeb!=null)printWeb.destroy();printWeb=new WebView(MainActivity.this);printWeb.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String u){android.print.PrintManager pm=(android.print.PrintManager)getSystemService(PRINT_SERVICE);pm.print("Ford Parts Desk job",v.createPrintDocumentAdapter("Ford Parts Desk job"),new android.print.PrintAttributes.Builder().build());}});printWeb.loadDataWithBaseURL(null,"<html><head><meta charset='utf-8'><style>body{font:12px sans-serif;padding:24px}pre{white-space:pre-wrap;line-height:1.6}</style></head><body><h2>Ford Parts Desk</h2><pre>"+android.text.TextUtils.htmlEncode(text)+"</pre></body></html>","text/html","UTF-8",null);});}
  @JavascriptInterface public void openEpc(String payload){
   String rejectedId=null;
   try{
    if(payload==null||payload.length()>16000){rejectEpcLaunch(null);return;}
    JSONObject p=new JSONObject(payload);String id=p.optString("requestId","");
    if(!id.matches("[A-Za-z0-9|_-]{1,180}")){rejectEpcLaunch(null);return;}rejectedId=id;
    String vin=p.getString("vin");org.json.JSONArray a=p.getJSONArray("bases");
    if(!vin.matches("[A-HJ-NPR-Z0-9]{17}")||a.length()<1||a.length()>100){rejectEpcLaunch(id);return;}
    String[] bases=new String[a.length()];for(int n=0;n<a.length();n++){bases[n]=a.getString(n);if(!bases[n].matches("[0-9][A-Z0-9]{3,7}")){rejectEpcLaunch(id);return;}}
    String partName=p.optString("partName","Service part lookup");if(partName.length()>160)partName=partName.substring(0,160);final String title=partName;
    runOnUiThread(()->{
     if(isFinishing()||isDestroyed())return;
     if(epcRequestId!=null){toast("Finish the open catalog lookup first.");notifyEpcClosed(id);return;}
     epcRequestId=id;epcVin=vin;epcBases=bases;
     try{startActivityForResult(new Intent(MainActivity.this,EpcActivity.class).putExtra("vin",vin).putExtra("requestId",id).putExtra("bases",bases).putExtra("partName",title),EPC_LOOKUP);}
     catch(Exception error){epcRequestId=null;epcVin=null;epcBases=null;toast("Unable to open the EPC lookup.");notifyEpcClosed(id);}
    });
   }catch(Exception e){rejectEpcLaunch(rejectedId);}
  }
  @JavascriptInterface public String getEpcResult(){return getSharedPreferences("epc-results",MODE_PRIVATE).getString("pending","");}
  @JavascriptInterface public void acknowledgeEpcResult(){getSharedPreferences("epc-results",MODE_PRIVATE).edit().remove("pending").apply();}
  @JavascriptInterface public void decodeVin(String vin,String year,String id){
   if(vin==null||!vin.matches("[A-HJ-NPR-Z0-9]{17}")||year==null||!year.matches("(?:[0-9]{4})?")||id==null||!id.matches("[0-9-]{1,40}"))return;
   decoderExecutor.execute(()->{
    JSONObject result=new JSONObject();HttpsURLConnection connection=null;
    try{
     result.put("id",id);
     String address="https://vpic.nhtsa.dot.gov/api/vehicles/DecodeVinValuesExtended/"+vin+"?format=json"+(year.isEmpty()?"":"&modelyear="+year);
     connection=(HttpsURLConnection)new URL(address).openConnection();connection.setConnectTimeout(7000);connection.setReadTimeout(10000);connection.setInstanceFollowRedirects(false);connection.setRequestProperty("Accept","application/json");
     if(connection.getResponseCode()!=200)throw new java.io.IOException("Service temporarily unavailable");
     ByteArrayOutputStream output=new ByteArrayOutputStream();
     try(InputStream input=connection.getInputStream()){byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))!=-1){output.write(buffer,0,count);if(output.size()>1000000)throw new java.io.IOException("Response too large");}}
     byte[] bytes=output.toByteArray();
     result.put("data",new JSONObject(new String(bytes,StandardCharsets.UTF_8)));
    }catch(Exception e){try{result.put("error","Vehicle lookup unavailable. Check your internet connection and try again.");}catch(Exception ignored){}}
    finally{if(connection!=null)connection.disconnect();}
    runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())web.evaluateJavascript("window.dispatchEvent(new CustomEvent('parts-vin-result',{detail:"+result.toString()+"}));",null);});
   });
  }
  @JavascriptInterface public void copyText(String text){if(text==null||text.length()>1000000)return;runOnUiThread(()->{ClipboardManager c=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);c.setPrimaryClip(ClipData.newPlainText("Ford Parts Desk",text));});}
  @JavascriptInterface public void saveText(String name,String text,String mime){
   if(text==null||text.length()>20000000||name==null||!name.matches("[A-Za-z0-9._ -]{1,100}"))return;
   if(!"text/csv".equals(mime)&&!"application/json".equals(mime)&&!"text/plain".equals(mime))return;
   runOnUiThread(()->{if(exportText!=null){toast("Finish the current file save first.");return;}exportText=text;Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType(mime);intent.putExtra(Intent.EXTRA_TITLE,name);try{startActivityForResult(intent,SAVE_FILE);}catch(Exception e){exportText=null;toast("Unable to open the file save picker.");}});
  }
 }
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(request==EPC_LOOKUP){
   String expectedId=epcRequestId,expectedVin=epcVin;String[] expectedBases=epcBases;epcRequestId=null;epcVin=null;epcBases=null;
   if(result==RESULT_OK&&data!=null){String selected=data.getStringExtra("selection");
    if(validEpcResult(selected,expectedId,expectedVin,expectedBases)){getSharedPreferences("epc-results",MODE_PRIVATE).edit().putString("pending",selected).apply();web.evaluateJavascript("window.dispatchEvent(new Event('parts-epc-result'));",null);}
    else toast("The catalog result no longer matches the open lookup. Please try again.");
   }
   notifyEpcClosed(expectedId);
  }
  if(request==PICK_FILE&&fileCallback!=null){fileCallback.onReceiveValue(result==RESULT_OK&&data!=null&&data.getData()!=null?new Uri[]{data.getData()}:null);fileCallback=null;}
  if(request==SAVE_FILE){String text=exportText;exportText=null;if(result==RESULT_OK&&text!=null&&data!=null&&data.getData()!=null){try(OutputStream stream=getContentResolver().openOutputStream(data.getData())){if(stream==null)throw new java.io.IOException();stream.write(text.getBytes(StandardCharsets.UTF_8));toast("File saved.");}catch(Exception e){toast("The file could not be saved.");}}}
 }
 private void rejectEpcLaunch(String requestId){runOnUiThread(()->{if(isFinishing()||isDestroyed())return;toast("The catalog lookup needs a valid VIN and base number.");if(requestId!=null)notifyEpcClosed(requestId);else if(epcRequestId==null)web.evaluateJavascript("window.dispatchEvent(new Event('parts-epc-launch-failed'));",null);});}
 private void notifyEpcClosed(String requestId){if(requestId!=null&&!isDestroyed())web.evaluateJavascript("window.dispatchEvent(new CustomEvent('parts-epc-closed',{detail:{requestId:"+JSONObject.quote(requestId)+"}}));",null);}
 static boolean validEpcResult(String selected,String requestId,String vin,String[] bases){
  if(selected==null||selected.length()>=12000||requestId==null||vin==null||bases==null)return false;
  try{JSONObject part=new JSONObject(selected);if(!requestId.equals(part.optString("requestId"))||!vin.equals(part.optString("vin"))||!part.optString("serviceNumber").matches("[A-Z0-9][A-Z0-9 -]{2,39}")||part.optBoolean("requiresCatalogReview"))return false;for(String base:bases)if(base.equals(part.optString("base")))return true;}catch(Exception ignored){}return false;
 }
 @Override public void onBackPressed(){web.evaluateJavascript("(()=>{const d=document.querySelector('dialog[open]');if(d){d.close();return true;}return false;})()",value->{if(!"true".equals(value)){if(web.canGoBack())web.goBack();else super.onBackPressed();}});}
 @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("epc-request-id",epcRequestId);state.putString("epc-vin",epcVin);state.putStringArray("epc-bases",epcBases);web.saveState(state);}
 @Override protected void onDestroy(){decoderExecutor.shutdownNow();if(fileCallback!=null)fileCallback.onReceiveValue(null);if(printWeb!=null)printWeb.destroy();web.removeJavascriptInterface("PartsNative");web.destroy();super.onDestroy();}
}

