package dev.urbanrunnerx.partsdesk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.UUID;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/** Resolves a suffix only through a live employee catalog search and native vehicle confirmation. */
public final class VinResolveActivity extends Activity {
 private static final String CATALOG="https://snaponepc.com/epc/";
 private static final int NAVY=0xff101d32,INK=0xff172b49,MUTED=0xff60718b,BLUE=0xff245af0,PAPER=0xfff3f6fb;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final Runnable poll=this::readSnapshot;
 private final Runnable pageTimeout=()->failConnection("The catalog did not finish loading. Check the connection and retry.");
 private EpcSessionStore sessions;
 private EpcSessionStore.Session session;
 private WebView web;
 private FrameLayout stack;
 private ScrollView review;
 private TextView status,fullVin,vehicle,explanation;
 private Button confirmButton,retryButton,catalogButton;
 private ProgressBar progress;
 private String suffix,requestId,adapter,searchId="";
 private JSONObject candidate;
 private boolean resumed,destroyed,pageLoading,catalogFailed,catalogVisible,inFlight,searchAccepted,autoSearch,stopped,confirming,resultSaved;
 private int generation;
 private long serial,deadline;
 private Runnable operationTimeout;
 private Callback callback;
 private interface Callback {void receive(JSONObject result);}
 public WebView getWebView(){return web;}

 @Override public void onCreate(Bundle state){
  super.onCreate(state);setResult(RESULT_CANCELED);
  suffix=getIntent().getStringExtra("suffix");requestId=getIntent().getStringExtra("requestId");
  if(suffix==null||!suffix.matches("[A-HJ-NPR-Z0-9]{8}")||requestId==null||!requestId.matches("[A-Za-z0-9|_-]{1,180}")){finish();return;}
  try(InputStream in=getAssets().open("vin-resolver.js");ByteArrayOutputStream out=new ByteArrayOutputStream()){
   byte[] bytes=new byte[8192];int count;while((count=in.read(bytes))!=-1)out.write(bytes,0,count);adapter=out.toString("UTF-8");
  }catch(Exception error){finish();return;}
  sessions=((PartsApplication)getApplication()).epcSessions();session=sessions.acquire(this);web=session.web;
  autoSearch=state==null&&getIntent().getBooleanExtra("autoStart",true);searchId=UUID.randomUUID().toString();
  LinearLayout root=column(NAVY);root.setOnApplyWindowInsetsListener((view,insets)->{view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
  LinearLayout heading=column(NAVY);heading.setPadding(dp(18),dp(14),dp(18),dp(16));heading.addView(label("FORD PARTS DESK  /  VEHICLE",11,0xff9fb9e5,true));
  TextView title=label("Confirm the full VIN",24,Color.WHITE,true);title.setPadding(0,dp(8),0,dp(10));heading.addView(title);
  TextView suffixLabel=label("LAST 8  ·  "+suffix,16,Color.WHITE,true);suffixLabel.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);heading.addView(suffixLabel);root.addView(heading);
  progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);progress.setVisibility(View.INVISIBLE);root.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
  status=label("Connecting to your catalog…",13,INK,false);status.setTag("vin-resolve-status");status.setPadding(dp(16),dp(12),dp(16),dp(12));status.setBackgroundColor(0xffe7eef9);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);root.addView(status);
  stack=new FrameLayout(this);configureWebView();stack.addView(web,new FrameLayout.LayoutParams(-1,-1));
  review=new ScrollView(this);review.setFillViewport(true);review.setBackgroundColor(PAPER);LinearLayout content=column(PAPER);content.setPadding(dp(18),dp(22),dp(18),dp(22));
  content.addView(label("REVIEW THE CATALOG VEHICLE",11,MUTED,true));fullVin=label("Full VIN not yet verified",21,INK,true);fullVin.setTag("vin-resolve-full-vin");fullVin.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);fullVin.setTextIsSelectable(true);fullVin.setPadding(0,dp(16),0,dp(10));content.addView(fullVin);
  vehicle=label("Vehicle details are not yet available.",16,INK,false);vehicle.setTag("vin-resolve-vehicle");content.addView(vehicle);
  explanation=label("The last 8 characters are a search key. Check the full VIN and vehicle returned by Snap-on before continuing. No job is created until you confirm.",15,MUTED,false);explanation.setPadding(0,dp(20),0,dp(20));content.addView(explanation);
  confirmButton=button("Confirm this vehicle",true,this::confirmVehicle);confirmButton.setTag("vin-resolve-confirm");confirmButton.setVisibility(View.GONE);content.addView(confirmButton,new LinearLayout.LayoutParams(-1,dp(54)));
  retryButton=button("Retry lookup",false,this::retryLookup);retryButton.setTag("vin-resolve-retry");LinearLayout.LayoutParams retryParams=new LinearLayout.LayoutParams(-1,dp(50));retryParams.topMargin=dp(12);content.addView(retryButton,retryParams);
  review.addView(content,new ScrollView.LayoutParams(-1,-2));stack.addView(review,new FrameLayout.LayoutParams(-1,-1));root.addView(stack,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout footer=new LinearLayout(this);footer.setPadding(dp(8),dp(6),dp(8),dp(6));footer.setBackgroundColor(Color.WHITE);footer.setGravity(Gravity.CENTER_VERTICAL);
  footer.addView(button("Cancel",false,this::finish),new LinearLayout.LayoutParams(0,dp(48),1));catalogButton=button("Catalog / sign in",false,()->setCatalogVisible(!catalogVisible));footer.addView(catalogButton,new LinearLayout.LayoutParams(0,dp(48),1.6f));root.addView(footer);setContentView(root);
  setCatalogVisible(state!=null&&state.getBoolean("catalog-visible",false));
  if(web.getUrl()==null||session.needsReload)web.loadUrl(CATALOG);else{pageLoading=web.getProgress()<100;schedule(100);}
 }

 @SuppressLint("SetJavaScriptEnabled")
 private void configureWebView(){
  web.setBackgroundColor(Color.WHITE);WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setUseWideViewPort(true);settings.setLoadWithOverviewMode(true);settings.setBuiltInZoomControls(true);settings.setDisplayZoomControls(false);settings.setUserAgentString(settings.getUserAgentString().replace("; wv","").replace(" Mobile "," "));
  CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);web.setWebChromeClient(new WebChromeClient());
  // No JavascriptInterface is installed on this remote page.
  web.setWebViewClient(new WebViewClient(){
   @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){Uri uri=request.getUrl();if(allowed(uri))return false;if(request.isForMainFrame()&&("https".equals(uri.getScheme())||"http".equals(uri.getScheme())))try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(Exception ignored){}return true;}
   @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon){
    if(destroyed||view!=web)return;boolean interrupted=searchAccepted||candidate!=null;invalidate();searchAccepted=false;deadline=0;searchId=UUID.randomUUID().toString();if(interrupted)autoSearch=false;catalogFailed=false;pageLoading=true;session.needsReload=false;handler.removeCallbacks(pageTimeout);if(resumed)handler.postDelayed(pageTimeout,30000);setStatus("Loading your catalog…",true);
   }
   @Override public void onPageFinished(WebView view,String url){if(destroyed||view!=web)return;pageLoading=false;handler.removeCallbacks(pageTimeout);if(!catalogFailed)schedule(250);}
   @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(view==web&&request.isForMainFrame())failConnection("Catalog connection interrupted. Check the connection, then retry.");}
   @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse error){if(view==web&&request.isForMainFrame()&&error.getStatusCode()>=400)failConnection("The catalog returned a connection error ("+error.getStatusCode()+"). Retry shortly.");}
   @Override public void onReceivedSslError(WebView view,SslErrorHandler ssl,SslError error){ssl.cancel();if(view==web)failConnection("A secure catalog connection could not be established. Check the device date and connection, then retry.");}
   @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){
    if(destroyed||view!=web)return true;invalidate();sessions.discardRenderer(session);session=sessions.acquire(VinResolveActivity.this);web=session.web;configureWebView();stack.addView(web,0,new FrameLayout.LayoutParams(-1,-1));setCatalogVisible(false);failConnection("Android restarted the catalog view. Retry the connection to continue.");return true;
   }
  });
 }
 private static boolean allowed(Uri uri){String host=uri.getHost();return "https".equals(uri.getScheme())&&host!=null&&(host.equals("snaponepc.com")||host.endsWith(".snaponepc.com"));}
 private void setStatus(String text,boolean loading){if(status!=null)status.setText(text);if(progress!=null)progress.setVisibility(loading?View.VISIBLE:View.INVISIBLE);updateEnabled();}
 private void updateEnabled(){if(confirmButton!=null)confirmButton.setEnabled(candidate!=null&&!inFlight&&!confirming&&!pageLoading&&!catalogFailed);if(retryButton!=null)retryButton.setEnabled(!inFlight&&!confirming);}
 private void clearCandidate(){candidate=null;if(confirmButton!=null)confirmButton.setVisibility(View.GONE);if(fullVin!=null)fullVin.setText("Full VIN not yet verified");if(vehicle!=null)vehicle.setText("Vehicle details are not yet available.");if(explanation!=null)explanation.setText("The last 8 characters are a search key. Check the full VIN and vehicle returned by Snap-on before continuing. No job is created until you confirm.");}
 private void invalidate(){generation++;serial++;callback=null;inFlight=false;confirming=false;clearCandidate();handler.removeCallbacks(poll);if(operationTimeout!=null)handler.removeCallbacks(operationTimeout);operationTimeout=null;updateEnabled();}
 private void schedule(long delay){handler.removeCallbacks(poll);if(resumed&&!destroyed&&!isFinishing()&&!catalogFailed&&!stopped&&!pageLoading)handler.postDelayed(poll,delay);}
 private JSONObject request()throws JSONException{return new JSONObject().put("requestId",requestId).put("suffix",suffix).put("searchId",searchId);}
 private void run(String command,JSONObject extra,Callback then){
  if(destroyed||!resumed||inFlight||catalogFailed||pageLoading||stopped)return;String url=web.getUrl();if(url==null||!allowed(Uri.parse(url))){clearCandidate();setStatus("Open the signed-in Snap-on catalog to continue.",false);return;}
  final int capturedGeneration=generation;final long operation=++serial;inFlight=true;callback=then;updateEnabled();
  operationTimeout=()->{if(operation==serial&&!destroyed){invalidate();stopped=true;autoSearch=false;setStatus("The catalog did not respond. Retry lookup or reopen the catalog.",false);}};handler.postDelayed(operationTimeout,8000);
  try{JSONObject data=request();if(extra!=null){java.util.Iterator<String> keys=extra.keys();while(keys.hasNext()){String key=keys.next();data.put(key,extra.get(key));}}
   String script="(()=>{"+adapter+";return JSON.stringify(partsDeskVinResolve("+JSONObject.quote(command)+","+data+"));})()";WeakReference<VinResolveActivity> owner=new WeakReference<>(this);
   web.evaluateJavascript(script,raw->{VinResolveActivity activity=owner.get();if(activity!=null)activity.receive(operation,capturedGeneration,raw);});
  }catch(Exception error){invalidate();stopped=true;setStatus("Unable to read the catalog. Retry lookup or open the catalog.",false);}
 }
 private void receive(long operation,int capturedGeneration,String raw){
  if(destroyed||!resumed||operation!=serial||capturedGeneration!=generation||isFinishing())return;Callback then=callback;callback=null;inFlight=false;if(operationTimeout!=null)handler.removeCallbacks(operationTimeout);operationTimeout=null;
  try{if(raw==null||raw.length()>40000||then==null)throw new JSONException("Invalid response");JSONObject result=new JSONObject(String.valueOf(new JSONTokener(raw).nextValue()));
   if(!requestId.equals(result.optString("requestId"))||!suffix.equals(result.optString("suffix"))||!searchId.equals(result.optString("searchId")))throw new JSONException("Different request");then.receive(result);
  }catch(Exception error){clearCandidate();confirming=false;autoSearch=false;stopped=true;setStatus("The catalog response could not be verified. Retry lookup or open the catalog.",false);}updateEnabled();
 }
 private void readSnapshot(){
  if(inFlight){schedule(200);return;}run("snapshot",null,result->{
   String stage=result.optString("stage");
   if(searchAccepted&&deadline>0&&SystemClock.elapsedRealtime()>deadline&&!validResolution(result,requestId,suffix,searchId)){autoSearch=false;stopped=true;clearCandidate();setStatus("No verified full VIN was returned. Check for multiple matches or no match in the catalog, or retry lookup.",false);return;}
   render(result);
   if(autoSearch&&!searchAccepted&&!result.has("error")&&!"login".equals(stage)&&!"loading".equals(stage)&&!"modal".equals(stage)&&!"catalog".equals(stage)&&!"no-match".equals(stage)){startSearch();return;}
   schedule(searchAccepted?700:1500);
  });
 }
 private void startSearch(){
  autoSearch=false;clearCandidate();setStatus("Searching your catalog for the last 8…",true);run("search",null,result->{
   searchAccepted=result.optBoolean("accepted");deadline=searchAccepted?SystemClock.elapsedRealtime()+35000:0;render(result);schedule(500);
  });
 }
 private void render(JSONObject result){
  String stage=result.optString("stage"),message=result.optString("message");boolean valid=searchAccepted&&validResolution(result,requestId,suffix,searchId);
  if(valid){candidate=result;fullVin.setText(result.optString("vin"));String details=result.optString("vehicle").trim();vehicle.setText(details.isEmpty()?"Vehicle details not shown by the catalog. Check this VIN against the vehicle before confirming.":details);explanation.setText("The catalog returned this full VIN with VIN filters enabled. Check all 17 characters and the vehicle before continuing.");confirmButton.setVisibility(View.VISIBLE);setStatus("Full VIN found. Review and confirm this vehicle.",false);return;}
  clearCandidate();if(result.has("error"))message=result.optString("error");if(message.isEmpty())message="Check the vehicle search in your catalog, or retry lookup.";
  if("login".equals(stage)){message="Sign in to your Snap-on account, then return to Vehicle review.";setCatalogVisible(true);}
  else if("catalog".equals(stage)||"no-match".equals(stage)||"modal".equals(stage)||"filters".equals(stage)||"unsupported".equals(stage)){explanation.setText("Open the catalog to inspect this step. If several vehicles match, choose the correct one there. A full VIN with active VIN filters must still be verified before you can confirm.");}
  setStatus(message,"loading".equals(stage)||(searchAccepted&&"pending".equals(stage)));
 }
 /** A full VIN is never accepted merely because its suffix matches a cached toolbar. */
 static boolean validResolution(JSONObject result,String requestId,String suffix,String searchId){
  if(result==null||result.has("error")||!requestId.equals(result.optString("requestId"))||!suffix.equals(result.optString("suffix"))||!searchId.equals(result.optString("searchId"))||!result.optBoolean("ready")||!result.optBoolean("filtersOn")||!"resolved".equals(result.optString("stage"))||result.optString("identityId").isEmpty())return false;
  String vin=result.optString("vin");return vin.matches("[A-HJ-NPR-Z0-9]{17}")&&vin.endsWith(suffix)&&result.optString("vehicle").length()<=2000;
 }
 private void confirmVehicle(){
  if(candidate==null||inFlight||confirming||catalogFailed||pageLoading||!searchAccepted)return;JSONObject shown=candidate;final int capturedGeneration=generation;confirming=true;handler.removeCallbacks(poll);setStatus("Checking this exact vehicle once more…",true);
  try{JSONObject extra=new JSONObject().put("vin",shown.optString("vin")).put("identityId",shown.optString("identityId"));run("confirm",extra,result->{
   confirming=false;if(capturedGeneration!=generation||!result.optBoolean("confirmed")||!validResolution(result,requestId,suffix,searchId)||!shown.optString("identityId").equals(result.optString("identityId"))||!shown.optString("vin").equals(result.optString("vin"))||!shown.optString("vehicle").equals(result.optString("vehicle"))){clearCandidate();autoSearch=false;setStatus("The catalog vehicle changed. Review the current result before confirming.",false);schedule(700);return;}
   try{JSONObject resolved=new JSONObject().put("requestId",requestId).put("suffix",suffix).put("vin",result.getString("vin")).put("vehicle",result.optString("vehicle")).put("confirmed",true);resultSaved=true;setResult(RESULT_OK,new Intent().putExtra("resolvedVehicle",resolved.toString()));finish();}catch(JSONException error){clearCandidate();setStatus("Unable to return this vehicle. Retry lookup.",false);}
  });}catch(JSONException error){confirming=false;clearCandidate();setStatus("This result expired. Retry lookup.",false);}
 }
 private void retryLookup(){
  boolean reconnect=catalogFailed;cancelAdapter();invalidate();searchAccepted=false;searchId=UUID.randomUUID().toString();deadline=0;stopped=false;autoSearch=true;catalogFailed=false;setCatalogVisible(false);
  if(reconnect){pageLoading=true;session.needsReload=false;setStatus("Reconnecting to your catalog…",true);handler.removeCallbacks(pageTimeout);handler.postDelayed(pageTimeout,30000);String url=web.getUrl();if(url!=null&&allowed(Uri.parse(url)))web.reload();else web.loadUrl(CATALOG);}else{setStatus("Checking the catalog before a new search…",true);schedule(0);}
 }
 private void failConnection(String message){if(destroyed)return;if(session!=null)session.needsReload=true;invalidate();catalogFailed=true;pageLoading=false;autoSearch=false;searchAccepted=false;handler.removeCallbacks(pageTimeout);if(web!=null)web.stopLoading();setStatus(message,false);}
 public void setCatalogVisible(boolean visible){if(destroyed||review==null)return;catalogVisible=visible;review.setVisibility(visible?View.INVISIBLE:View.VISIBLE);web.setImportantForAccessibility(visible?View.IMPORTANT_FOR_ACCESSIBILITY_AUTO:View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);web.setFocusable(visible);web.setFocusableInTouchMode(visible);catalogButton.setText(visible?"Vehicle review":"Catalog / sign in");if(!visible){web.clearFocus();review.requestFocus();schedule(0);}}
 private void cancelAdapter(){if(web==null||adapter==null||requestId==null||suffix==null)return;String url=web.getUrl();if(url==null||!allowed(Uri.parse(url)))return;try{web.evaluateJavascript("(()=>{"+adapter+";partsDeskVinResolve('cancel',"+request()+");})()",null);}catch(Exception ignored){}}
 @Override public void finish(){if(!resultSaved){cancelAdapter();if(pageLoading&&session!=null)session.needsReload=true;}invalidate();stopped=true;super.finish();}
 @Override public void onBackPressed(){if(catalogVisible){setCatalogVisible(false);return;}finish();}
 @Override protected void onResume(){super.onResume();resumed=true;if(web!=null){web.onResume();if(pageLoading)handler.postDelayed(pageTimeout,30000);schedule(200);}}
 @Override protected void onPause(){resumed=false;invalidate();handler.removeCallbacksAndMessages(null);if(web!=null)web.onPause();super.onPause();}
 @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putBoolean("catalog-visible",catalogVisible);}
 @Override protected void onDestroy(){destroyed=true;invalidate();handler.removeCallbacksAndMessages(null);if(session!=null)sessions.release(session);session=null;web=null;super.onDestroy();}
 private LinearLayout column(int color){LinearLayout result=new LinearLayout(this);result.setOrientation(LinearLayout.VERTICAL);result.setBackgroundColor(color);return result;}
 private TextView label(String text,int size,int color,boolean bold){TextView result=new TextView(this);result.setText(text);result.setTextSize(size);result.setTextColor(color);if(bold)result.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return result;}
 private Button button(String text,boolean primary,Runnable action){Button result=new Button(this);result.setText(text);result.setAllCaps(false);result.setTextSize(13);result.setTypeface(Typeface.DEFAULT,Typeface.BOLD);result.setTextColor(primary?Color.WHITE:INK);result.setPadding(dp(10),dp(4),dp(10),dp(4));result.setMinWidth(0);result.setMinimumWidth(0);GradientDrawable background=new GradientDrawable();background.setColor(primary?BLUE:Color.WHITE);background.setCornerRadius(dp(10));result.setBackground(background);result.setOnClickListener(view->action.run());return result;}
 private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
