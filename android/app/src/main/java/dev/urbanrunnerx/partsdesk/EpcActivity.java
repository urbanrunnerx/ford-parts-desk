package dev.urbanrunnerx.partsdesk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
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
import android.view.ViewGroup;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.lang.ref.WeakReference;

/** App-owned catalog controls backed by the employee's isolated, signed-in EPC page. */
public class EpcActivity extends Activity {
 private static final int NAVY=0xff101d32, BLUE=0xff245af0, INK=0xff172b49, MUTED=0xff60718b, PAPER=0xfff3f6fb;
 private static final int IDLE=0, WAIT_VIN=1, WAIT_SEARCH=2;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private WebView web;
 private EpcSessionStore sessions;
 private EpcSessionStore.Session session;
 private FrameLayout stack;
 private TextView status,vehicle;
 private Spinner bases;
 private ProgressBar progress;
 private Button findButton,catalogButton,refreshButton,cancelButton;
 private ScrollView guided;
 private LinearLayout content;
 private AlertDialog reviewDialog;
 private String vin,requestId,adapter,currentBase,renderedKey="",uiScope="",pendingPartId="";
 private final Map<String,String> choicesByGroup=new HashMap<>();
 private final Map<String,Spinner> choiceMenus=new HashMap<>();
 private final Map<String,List<JSONObject>> choiceOptions=new HashMap<>();
 private JSONObject latest;
 private boolean resumed,destroyed,inFlight,catalogVisible,mutationPending,mutationSawBusy,autoStartPending,catalogFailed,pageLoading,lookupCancelled;
 private int generation,findPhase,restoredScroll=-1;
 private long operationSerial,mutationStarted,findDeadline,locateDeadline;
 private Runnable operationTimeout;
 private final Runnable pageTimeout=()->connectionFailed("The catalog did not finish loading. Check your connection and try again.");
 private String beforeMutation="";
 private final List<View> actionViews=new ArrayList<>();
 private final Runnable poll=()->readSnapshot(null);
 private interface ResultCallback { void receive(JSONObject result); }
 private ResultCallback activeCallback;
 public WebView getWebView(){return web;}

 @SuppressLint("SetJavaScriptEnabled")
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  vin=getIntent().getStringExtra("vin");requestId=getIntent().getStringExtra("requestId");
  String[] values=getIntent().getStringArrayExtra("bases");
  if(vin==null||!vin.matches("[A-HJ-NPR-Z0-9]{17}")||requestId==null||!requestId.matches("[A-Za-z0-9|_-]{1,180}")||values==null||values.length<1||values.length>100){finish();return;}
  for(String value:values)if(value==null||!value.matches("[0-9][A-Z0-9]{3,7}")){finish();return;}
  sessions=((PartsApplication)getApplication()).epcSessions();
  currentBase=values[0];autoStartPending=state==null?getIntent().getBooleanExtra("autoStart",true):state.getBoolean("lookup-auto-start",false);
  if(state!=null){String saved=state.getString("lookup-base",currentBase);for(String value:values)if(value.equals(saved))currentBase=saved;}
  try(java.io.InputStream in=getAssets().open("epc-adapter.js");java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
   byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);adapter=out.toString("UTF-8");
  }catch(Exception e){finish();return;}

  LinearLayout root=column(NAVY);root.setOnApplyWindowInsetsListener((view,insets)->{view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
  LinearLayout heading=column(NAVY);heading.setPadding(dp(18),dp(14),dp(18),dp(12));heading.addView(label("FORD PARTS DESK  /  LOOKUP",11,0xff9fb9e5,true));
  String partName=getIntent().getStringExtra("partName");if(partName!=null)partName=partName.trim();
  TextView title=label(partName==null||partName.isEmpty()?"The right part. This vehicle.":partName.substring(0,Math.min(160,partName.length())),23,Color.WHITE,true);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);title.setPadding(0,dp(7),0,dp(9));heading.addView(title);
  TextView vinText=label(vin,15,Color.WHITE,true);vinText.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);vinText.setTextIsSelectable(true);heading.addView(vinText);
  vehicle=label("Connect your Snap-on catalog to begin",12,0xffbdcce2,false);vehicle.setPadding(0,dp(5),0,0);heading.addView(vehicle);root.addView(heading);
  LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER_VERTICAL);controls.setBackgroundColor(Color.WHITE);controls.setPadding(dp(12),dp(8),dp(12),dp(8));
  LinearLayout baseField=column(Color.WHITE);baseField.addView(label("BASE NUMBER",10,MUTED,true));
  bases=new Spinner(this);bases.setContentDescription("Base number");bases.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values));for(int i=0;i<values.length;i++)if(values[i].equals(currentBase))bases.setSelection(i);
  baseField.addView(bases,new LinearLayout.LayoutParams(-1,dp(42)));controls.addView(baseField,new LinearLayout.LayoutParams(0,-2,1));findButton=button("Find parts",true,this::startFind);LinearLayout.LayoutParams findParams=new LinearLayout.LayoutParams(dp(124),dp(50));findParams.leftMargin=dp(12);controls.addView(findButton,findParams);root.addView(controls);
  progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);progress.setVisibility(View.INVISIBLE);root.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
  LinearLayout statusRow=new LinearLayout(this);statusRow.setGravity(Gravity.CENTER_VERTICAL);statusRow.setBackgroundColor(0xffe7eef9);status=label("Connecting to your catalog…",12,INK,false);status.setTag("guided-status");status.setPadding(dp(16),dp(9),dp(8),dp(9));status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);statusRow.addView(status,new LinearLayout.LayoutParams(0,-2,1));cancelButton=button("Cancel",false,this::cancelLookup);cancelButton.setVisibility(View.GONE);statusRow.addView(cancelButton,new LinearLayout.LayoutParams(dp(78),dp(44)));root.addView(statusRow);

  // Both layers stay fully measured: a GONE/tiny WebView breaks virtualized catalog grids.
  stack=new FrameLayout(this);session=sessions.acquire(this);web=session.web;configureWebView();
  stack.addView(web,new FrameLayout.LayoutParams(-1,-1));guided=new ScrollView(this);guided.setFillViewport(true);guided.setBackgroundColor(PAPER);guided.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);content=column(PAPER);content.setTag("guided-content");content.setPadding(dp(16),dp(18),dp(16),dp(24));guided.addView(content,new ScrollView.LayoutParams(-1,-2));stack.addView(guided,new FrameLayout.LayoutParams(-1,-1));root.addView(stack,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout footer=new LinearLayout(this);footer.setPadding(dp(8),dp(6),dp(8),dp(6));footer.setBackgroundColor(Color.WHITE);Button back=button("Back to job",false,this::finish);catalogButton=button("Catalog / sign in",false,()->setCatalogVisible(!catalogVisible));refreshButton=button("Refresh",false,()->{if(catalogFailed||catalogVisible)retryConnection();else refreshSnapshot();});refreshButton.setTag("guided-refresh");footer.addView(back,new LinearLayout.LayoutParams(0,dp(48),1));footer.addView(catalogButton,new LinearLayout.LayoutParams(0,dp(48),1.4f));footer.addView(refreshButton,new LinearLayout.LayoutParams(0,dp(48),.8f));root.addView(footer);setContentView(root);
  showMessage("Your catalog, simpler.","Sign in once, then choose locations and illustrations here. Parts are read from your current VIN-filtered catalog.");
  bases.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> parent){}public void onItemSelected(AdapterView<?> parent,View view,int position,long id){String selected=values[position];if(!selected.equals(currentBase)){currentBase=selected;autoStartPending=false;invalidateLookup();showMessage("Base "+currentBase,"Tap Find parts to look up this base for the current vehicle.");schedule(100);}}});
  setCatalogVisible(state!=null&&state.getBoolean("catalog-visible",false));
  if(state!=null){uiScope=state.getString("lookup-ui-scope","");Bundle selected=state.getBundle("lookup-choices");if(selected!=null)for(String key:selected.keySet())choicesByGroup.put(key,selected.getString(key,""));restoredScroll=state.getInt("lookup-scroll",0);}
  // A warm, measured page is reused; no history bundle or credentials are copied.
  if(web.getUrl()==null||session.needsReload)web.loadUrl("https://snaponepc.com/epc/");else{pageLoading=web.getProgress()<100;schedule(100);}
 }

 @SuppressLint("SetJavaScriptEnabled")
 private void configureWebView(){
  web.setBackgroundColor(Color.WHITE);WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setUseWideViewPort(true);settings.setLoadWithOverviewMode(true);settings.setBuiltInZoomControls(true);settings.setDisplayZoomControls(false);settings.setUserAgentString(settings.getUserAgentString().replace("; wv","").replace(" Mobile "," "));
  CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);web.setWebChromeClient(new WebChromeClient());
  web.setWebViewClient(new WebViewClient(){
   @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){Uri uri=request.getUrl();if(allowed(uri))return false;if(request.isForMainFrame()&&("https".equals(uri.getScheme())||"http".equals(uri.getScheme())))try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(Exception ignored){}return true;}
   @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon){if(destroyed||view!=web)return;catalogFailed=false;session.needsReload=false;pageLoading=true;if(refreshButton!=null)refreshButton.setText("Refresh");invalidateLookup();handler.removeCallbacks(pageTimeout);if(resumed)handler.postDelayed(pageTimeout,30000);setStatus("Loading your catalog…",true);}
   @Override public void onPageFinished(WebView view,String url){if(destroyed||view!=web)return;pageLoading=false;handler.removeCallbacks(pageTimeout);if(!catalogFailed&&!lookupCancelled)schedule(250);}
   @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(view==web&&request.isForMainFrame())connectionFailed("Catalog connection interrupted. Check your connection, then retry. Your job is saved.");}
   @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse error){if(view==web&&request.isForMainFrame()&&error.getStatusCode()>=400)connectionFailed("The catalog returned a connection error ("+error.getStatusCode()+"). Try again shortly.");}
   @Override public void onReceivedSslError(WebView view,SslErrorHandler ssl,SslError error){ssl.cancel();if(view==web)connectionFailed("A secure catalog connection could not be established. Check the device date and connection, then retry.");}
   @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){
    if(destroyed||view!=web)return true;invalidateLookup();sessions.discardRenderer(session);session=sessions.acquire(EpcActivity.this);web=session.web;configureWebView();stack.addView(web,0,new FrameLayout.LayoutParams(-1,-1));setCatalogVisible(false);connectionFailed("Android restarted the catalog view. Your job is saved; retry the connection to continue.");return true;
   }
  });
 }
 private void connectionFailed(String message){
  if(destroyed)return;if(session!=null)session.needsReload=true;catalogFailed=true;pageLoading=false;autoStartPending=false;handler.removeCallbacks(pageTimeout);invalidateLookup();if(web!=null)web.stopLoading();setStatus(message,false);if(refreshButton!=null)refreshButton.setText("Retry");showRecovery("Catalog connection interrupted",message);
 }
 private void retryConnection(){
  if(destroyed||web==null)return;lookupCancelled=false;catalogFailed=false;pageLoading=true;invalidateLookup();refreshButton.setText("Refresh");setStatus("Reconnecting to your catalog…",true);handler.removeCallbacks(pageTimeout);handler.postDelayed(pageTimeout,30000);String url=web.getUrl();if(url!=null&&allowed(Uri.parse(url)))web.reload();else web.loadUrl("https://snaponepc.com/epc/");
 }
 private void cancelLookup(){
  lookupCancelled=true;autoStartPending=false;if(pageLoading&&session!=null)session.needsReload=true;pageLoading=false;invalidateLookup();handler.removeCallbacks(pageTimeout);if(web!=null)web.stopLoading();setStatus("Lookup stopped. Your job is unchanged.",false);showMessage("Lookup stopped","The catalog may have finished its last step. Check its current state or retry when you are ready.");addContentButton("Check current catalog",this::refreshSnapshot);addContentButton("Retry connection",this::retryConnection);
 }
 private void showRecovery(String title,String message){showMessage(title,message);if(!catalogFailed)addContentButton("Check current catalog",this::refreshSnapshot);addContentButton("Retry connection",this::retryConnection);addContentButton("Back to job",this::finish);}
 private void captureChoices(){
  for(Map.Entry<String,Spinner> entry:choiceMenus.entrySet()){List<JSONObject> options=choiceOptions.get(entry.getKey());int selected=entry.getValue().getSelectedItemPosition();if(options!=null&&selected>0&&selected<options.size())choicesByGroup.put(entry.getKey(),options.get(selected).optString("id"));else choicesByGroup.remove(entry.getKey());}
 }
 private void locatePart(JSONObject part,JSONObject snapshot){
  if(!isCurrent(snapshot)){refreshSnapshot();return;}if(part.optBoolean("requiresCatalogReview")){setCatalogVisible(true);return;}
  try{pendingPartId=part.getString("id");locateDeadline=SystemClock.elapsedRealtime()+15000;mutate("locate",new JSONObject().put("partId",pendingPartId).put("snapshotId",snapshot.getString("snapshotId")));}catch(Exception error){pendingPartId="";refreshSnapshot();}
 }

 /** Exposes the same isolated session only for authentication or catalog-specific exceptions. */
 public void setCatalogVisible(boolean visible){
  if(destroyed||web==null||guided==null)return;catalogVisible=visible;guided.setVisibility(visible?View.INVISIBLE:View.VISIBLE);web.setImportantForAccessibility(visible?View.IMPORTANT_FOR_ACCESSIBILITY_AUTO:View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);web.setFocusable(visible);web.setFocusableInTouchMode(visible);catalogButton.setText(visible?"Guided lookup":"Catalog / sign in");if(!visible){lookupCancelled=false;web.clearFocus();guided.requestFocus();schedule(100);}
 }
 public void refreshSnapshot(){if(destroyed)return;lookupCancelled=false;if(catalogFailed){retryConnection();return;}invalidateLookup(false);setStatus("Checking the current catalog…",true);schedule(0);}
 private void invalidateLookup(){invalidateLookup(true);}
 private void invalidateLookup(boolean clearContent){
  generation++;operationSerial++;activeCallback=null;inFlight=false;findPhase=IDLE;mutationPending=false;mutationSawBusy=false;pendingPartId="";latest=null;
  handler.removeCallbacks(poll);if(operationTimeout!=null)handler.removeCallbacks(operationTimeout);operationTimeout=null;
  if(reviewDialog!=null){reviewDialog.dismiss();reviewDialog=null;}handler.removeCallbacks(poll);
  if(clearContent){renderedKey="";if(content!=null)showMessage("Checking the catalog…","Selections will appear after the vehicle and catalog have been checked.");}updateEnabled();
 }
 private void startFind(){
  if(catalogFailed){autoStartPending=true;retryConnection();return;}if(inFlight||mutationPending){setStatus("Wait for the current catalog step to finish.",true);return;}autoStartPending=false;setCatalogVisible(false);readSnapshot(this::beginFind);
 }
 private void beginFind(JSONObject result){
   if(!result.optBoolean("signedIn")){autoStartPending=true;setStatus("Sign in to your Snap-on account, then return to Guided lookup.",false);setCatalogVisible(true);return;}
   setCatalogVisible(false);
   findDeadline=SystemClock.elapsedRealtime()+25000;
   if(result.optBoolean("ready")){findPhase=WAIT_SEARCH;mutate("search",null);}
   else if(vin.equals(result.optString("vin"))&&"filters".equals(result.optString("stage"))){findPhase=IDLE;setStatus("Enable VIN filters in the catalog, then tap Find parts.",false);setCatalogVisible(true);}
   else{findPhase=WAIT_VIN;mutate("vin",null);}
 }
 private void schedule(long delay){handler.removeCallbacks(poll);if(resumed&&!destroyed&&!catalogFailed&&!lookupCancelled&&reviewDialog==null)handler.postDelayed(poll,delay);}
 private JSONObject request(JSONObject extra)throws JSONException{JSONObject request=new JSONObject().put("vin",vin).put("base",currentBase).put("requestId",requestId);if(extra!=null){java.util.Iterator<String> keys=extra.keys();while(keys.hasNext()){String key=keys.next();request.put(key,extra.get(key));}}return request;}
 private boolean allowed(Uri uri){String host=uri.getHost();return "https".equals(uri.getScheme())&&host!=null&&(host.equals("snaponepc.com")||host.endsWith(".snaponepc.com"));}
 private void runAdapter(String command,JSONObject extra,ResultCallback callback){
  if(destroyed||!resumed||catalogFailed||lookupCancelled)return;if(inFlight){schedule(250);return;}String url=web.getUrl();if(url==null||!allowed(Uri.parse(url))){setStatus("Open the signed-in Snap-on catalog to continue.",false);return;}
  final int startedGeneration=generation;final long serial=++operationSerial;inFlight=true;updateEnabled();
  operationTimeout=()->{if(destroyed||serial!=operationSerial)return;invalidateLookup(false);setStatus("The catalog did not respond. Tap Refresh to check it, or Retry connection below.",false);showRecovery("Catalog not responding","Your job is saved. Retry the connection if refreshing does not help.");};
  handler.postDelayed(operationTimeout,8000);
  activeCallback=callback;final WeakReference<EpcActivity> owner=new WeakReference<>(this);
  try{String script="(()=>{"+adapter+";return JSON.stringify(partsDeskEpc("+JSONObject.quote(command)+","+request(extra)+"));})()";web.evaluateJavascript(script,raw->{EpcActivity activity=owner.get();if(activity!=null)activity.receiveAdapterResult(serial,startedGeneration,command,raw);});}
  catch(Exception error){activeCallback=null;if(operationTimeout!=null)handler.removeCallbacks(operationTimeout);operationTimeout=null;inFlight=false;findPhase=IDLE;mutationPending=false;pendingPartId="";setStatus("Unable to read the current catalog. Refresh to try again.",false);updateEnabled();}
 }
 private void receiveAdapterResult(long serial,int startedGeneration,String command,String raw){
  if(destroyed||serial!=operationSerial||startedGeneration!=generation||!resumed)return;
  ResultCallback callback=activeCallback;activeCallback=null;inFlight=false;if(operationTimeout!=null)handler.removeCallbacks(operationTimeout);operationTimeout=null;
  try{if(raw==null||raw.length()>2000000||callback==null)throw new JSONException("Invalid catalog response");JSONObject result=new JSONObject(String.valueOf(new JSONTokener(raw).nextValue()));if(result.has("error")){findPhase=IDLE;mutationPending=false;pendingPartId="";setStatus(result.optString("error","The catalog step could not be completed."),false);if(!"snapshot".equals(command))schedule(1600);}else callback.receive(result);}catch(Exception error){findPhase=IDLE;mutationPending=false;pendingPartId="";setStatus("The catalog is loading or its layout changed. Refresh, or open Catalog / sign in.",false);schedule(3000);}updateEnabled();
 }
 private void readSnapshot(ResultCallback then){
  runAdapter("snapshot",null,result->{
   latest=result;boolean busy=result.optBoolean("busy")||"loading".equals(result.optString("stage"));String id=result.optString("snapshotId");if(mutationPending&&busy)mutationSawBusy=true;if(mutationPending&&!busy&&(!id.equals(beforeMutation)||mutationSawBusy))mutationPending=false;
   if((mutationPending&&SystemClock.elapsedRealtime()-mutationStarted>25000)||(!pendingPartId.isEmpty()&&SystemClock.elapsedRealtime()>locateDeadline)){
    lookupCancelled=true;autoStartPending=false;invalidateLookup(false);setStatus("The catalog step did not finish. Refresh to check it, or retry the connection.",false);showRecovery("Catalog step did not finish","Check the current catalog before making another selection.");return;
   }
   if(!pendingPartId.isEmpty()&&!busy&&!mutationPending){
    JSONArray found=result.optJSONArray("parts");for(int i=0;found!=null&&i<found.length();i++){JSONObject part=found.optJSONObject(i);if(part!=null&&pendingPartId.equals(part.optString("id"))&&part.optBoolean("visible",true)&&!part.optBoolean("requiresCatalogReview")){pendingPartId="";renderSnapshot(result);reviewPart(part,result);return;}}
    if(SystemClock.elapsedRealtime()>locateDeadline){pendingPartId="";renderSnapshot(result);setStatus("This part is no longer visible in the catalog. Load more again or open the catalog to check it.",false);schedule(2500);return;}
   }
   if(findPhase!=IDLE&&SystemClock.elapsedRealtime()>findDeadline){lookupCancelled=true;autoStartPending=false;invalidateLookup(false);setStatus("The catalog is taking longer than expected. Refresh to check it or retry the connection.",false);showRecovery("Lookup needs another try","The catalog did not confirm the requested search. Your job is unchanged.");return;}
   if(findPhase==WAIT_VIN){if(result.optBoolean("ready")&&!busy){findPhase=WAIT_SEARCH;mutate("search",null);return;}if("filters".equals(result.optString("stage"))&&vin.equals(result.optString("vin"))){findPhase=IDLE;mutationPending=false;setStatus("Enable VIN filters in the catalog, then tap Find parts.",false);setCatalogVisible(true);}else setStatus("Loading this vehicle in your catalog…",true);}
   else if(findPhase==WAIT_SEARCH){String stage=result.optString("stage");if(!busy&&!mutationPending&&("choices".equals(stage)||"parts".equals(stage)||"empty".equals(stage))){findPhase=IDLE;renderSnapshot(result);}else setStatus("Finding base "+currentBase+" for this VIN…",true);}
   else if(!pendingPartId.isEmpty())setStatus("Returning to the selected service part…",true);else if(!mutationPending)renderSnapshot(result);else setStatus("Opening your catalog selection…",true);
   String stage=result.optString("stage");
   boolean canStart=result.optBoolean("signedIn")&&!busy&&("vehicle".equals(stage)||"choices".equals(stage)||"parts".equals(stage)||"empty".equals(stage));
   if(then==null&&autoStartPending&&findPhase==IDLE&&!mutationPending&&canStart&&pendingPartId.isEmpty()){autoStartPending=false;beginFind(result);return;}
   if(then!=null)then.receive(result);schedule((findPhase!=IDLE||mutationPending||busy||!pendingPartId.isEmpty())?650:2500);
  });
 }
 private void mutate(String command,JSONObject extra){if(inFlight)return;mutationPending=true;mutationSawBusy=false;mutationStarted=SystemClock.elapsedRealtime();beforeMutation=latest==null?"":latest.optString("snapshotId");renderedKey="";if("more".equals(command)||"locate".equals(command))setStatus("Loading the selected catalog rows…",true);else showMessage("One moment…","Loading the next catalog step for base "+currentBase+".");runAdapter(command,extra,result->{setStatus(result.optString("message","Loading the next catalog step…"),true);schedule(600);});}

 private void renderSnapshot(JSONObject result){
  String stage=result.optString("stage","unsupported"),message=result.optString("message","");boolean ready=result.optBoolean("ready")&&vin.equals(result.optString("vin"));String vehicleText=result.optString("vehicle","");vehicle.setText(vehicleText.isEmpty()?(ready?"VIN matched · catalog filters enabled":"Your job VIN stays fixed during this lookup"):vehicleText);setStatus(message.isEmpty()?(ready?"VIN matched · choose the application you need":"Connect the correct vehicle to continue"):message,result.optBoolean("busy"));String key=result.optString("snapshotId")+"|"+stage+"|"+message;if(key.equals(renderedKey)){updateEnabled();return;}captureChoices();String nextScope=vin+"|"+currentBase+"|"+result.optString("context");boolean sameScope=nextScope.equals(uiScope);int scroll=sameScope?(restoredScroll>=0?restoredScroll:guided.getScrollY()):0;restoredScroll=-1;if(!sameScope)choicesByGroup.clear();uiScope=nextScope;renderedKey=key;content.removeAllViews();actionViews.clear();choiceMenus.clear();choiceOptions.clear();final String paintedKey=key;guided.post(()->{if(!destroyed&&paintedKey.equals(renderedKey))guided.scrollTo(0,scroll);});
  if("login".equals(stage)){showMessage("Connect your catalog","Sign in with your own Snap-on account. Your account stays in the isolated catalog session.");addContentButton("Sign in to Snap-on",()->setCatalogVisible(true));return;}
  if("vehicle".equals(stage)){showMessage("Load this vehicle","Tap Find parts to load your job's VIN and search base "+currentBase+".");return;}
  if("filters".equals(stage)){showMessage("Turn on VIN filters","Open the catalog and enable its VIN filters so selections belong to this vehicle.");addContentButton("Open catalog filters",()->setCatalogVisible(true));return;}
  if("loading".equals(stage)){showMessage("Loading your catalog…","This view updates when the next selection is ready.");return;}
  if("unsupported".equals(stage)){showMessage("This step needs the catalog",message.isEmpty()?"Open the catalog to finish this step, then return to Guided lookup.":message);addContentButton("Open catalog",()->setCatalogVisible(true));return;}
  if(!ready){showMessage("Check the active vehicle","The requested VIN and VIN filters must match before catalog selections can be used.");return;}
  JSONArray crumbs=result.optJSONArray("breadcrumbs");if(crumbs!=null&&crumbs.length()>0)addBreadcrumbs(crumbs,result);
  JSONArray groups=result.optJSONArray("groups");if(groups!=null&&groups.length()>0){LinearLayout choices=column(PAPER);choices.setTag("guided-choices");for(int i=0;i<groups.length();i++){JSONObject group=groups.optJSONObject(i);if(group!=null)addGroup(choices,group,result);}content.addView(choices);TextView scope=label("Options reflect the rows currently available in your catalog.",12,MUTED,false);scope.setPadding(0,0,0,dp(10));content.addView(scope);}
  JSONArray parts=result.optJSONArray("parts");int count=parts==null?0:parts.length();
  if(count>0){TextView title=label("SELECT A SERVICE PART · "+result.optInt("loadedCount",count)+" LOADED",11,MUTED,true);title.setPadding(0,dp(16),0,dp(9));content.addView(title);String context=result.optString("context","");if(!context.isEmpty()){TextView path=label(context,13,INK,false);path.setPadding(0,0,0,dp(12));content.addView(path);}LinearLayout cards=column(PAPER);cards.setTag("guided-parts");for(int i=0;i<count;i++){JSONObject part=parts.optJSONObject(i);if(part!=null)addPart(cards,part,result);}content.addView(cards);TextView note=label("Review the position, dates, equipment and supersession notes before saving. Loaded parts stay here while you browse this catalog section. Off-screen parts are checked again before review.",12,MUTED,false);note.setPadding(0,dp(4),0,dp(12));content.addView(note);}
  else if("empty".equals(stage)){LinearLayout card=card();card.addView(label("No matching service numbers here",18,INK,true));card.addView(spaced("Choose a different location or illustration above, or open the catalog to inspect this application.",14,MUTED));content.addView(card);}
  else if(groups==null||groups.length()==0){showMessage("Choose a catalog location",message.isEmpty()?"Use Find parts to begin, or open the catalog for this step.":message);addContentButton("Open catalog",()->setCatalogVisible(true));}
  if(result.optBoolean("collectionLimited")){TextView limit=label("The loaded-results limit was reached. Narrow the catalog section or open the catalog to continue.",12,MUTED,false);content.addView(limit);}
  if(result.optBoolean("canMore"))addContentButton("Load more catalog results",()->selectMore(result));updateEnabled();
 }
 private void addBreadcrumbs(JSONArray crumbs,JSONObject snapshot){
  List<JSONObject> choices=new ArrayList<>();List<String> labels=new ArrayList<>();boolean available=false;
  for(int i=0;i<crumbs.length();i++){JSONObject crumb=crumbs.optJSONObject(i);if(crumb==null)continue;choices.add(crumb);labels.add(crumb.optString("label"));if(!crumb.optBoolean("disabled"))available=true;}
  if(choices.isEmpty())return;
  LinearLayout path=new LinearLayout(this);path.setGravity(Gravity.CENTER_VERTICAL);path.setPadding(0,0,0,dp(14));
  LinearLayout summary=column(PAPER);summary.addView(label("CATALOG PATH",10,MUTED,true));TextView trail=spaced(android.text.TextUtils.join(" › ",labels.subList(Math.max(0,labels.size()-2),labels.size())),12,INK);trail.setMaxLines(2);trail.setEllipsize(android.text.TextUtils.TruncateAt.END);summary.addView(trail);path.addView(summary,new LinearLayout.LayoutParams(0,-2,1));
  Button change=button("Back to section",false,()->{
   if(!isCurrent(snapshot)){refreshSnapshot();return;}handler.removeCallbacks(poll);
   ArrayAdapter<String> menu=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,labels){
    @Override public boolean areAllItemsEnabled(){return false;}
    @Override public boolean isEnabled(int position){return !choices.get(position).optBoolean("disabled");}
    @Override public View getView(int position,View recycled,ViewGroup parent){TextView row=(TextView)super.getView(position,recycled,parent);row.setTextColor(isEnabled(position)?INK:MUTED);row.setAlpha(isEnabled(position)?1:.5f);row.setSingleLine(false);row.setMaxLines(3);row.setEllipsize(android.text.TextUtils.TruncateAt.END);row.setPadding(dp(20),dp(12),dp(20),dp(12));return row;}
   };
   reviewDialog=new AlertDialog.Builder(this).setTitle("Back to catalog section").setAdapter(menu,(dialog,position)->{if(!choices.get(position).optBoolean("disabled"))select(choices.get(position).optString("id"),snapshot);}).setNegativeButton("Keep looking",null).create();reviewDialog.setOnDismissListener(dialog->{reviewDialog=null;if(!isFinishing()&&!destroyed)schedule(1000);});reviewDialog.show();
  });
  change.setEnabled(available);if(available)actionViews.add(change);LinearLayout.LayoutParams changeParams=new LinearLayout.LayoutParams(dp(112),dp(48));changeParams.leftMargin=dp(10);path.addView(change,changeParams);content.addView(path);
 }
 private void addGroup(LinearLayout parent,JSONObject group,JSONObject snapshot){
  JSONArray options=group.optJSONArray("options");if(options==null||options.length()==0)return;LinearLayout card=card();card.addView(label(group.optString("label","Choose a catalog section"),15,INK,true));List<String> labels=new ArrayList<>();List<JSONObject> choices=new ArrayList<>();labels.add("Choose an option…");choices.add(null);int selected=0;
  for(int i=0;i<options.length();i++){JSONObject option=options.optJSONObject(i);if(option==null||option.optBoolean("disabled"))continue;String detail=option.optString("detail");labels.add(option.optString("label")+(detail.isEmpty()?"":" — "+detail));choices.add(option);if(option.optBoolean("selected"))selected=choices.size()-1;}
  String groupId=group.optString("id",group.optString("label"));String remembered=choicesByGroup.get(groupId);for(int i=1;i<choices.size();i++)if(choices.get(i).optString("id").equals(remembered))selected=i;Spinner menu=new Spinner(this);menu.setContentDescription(group.optString("label","Catalog selection"));menu.setAdapter(choiceAdapter(labels));menu.setSelection(selected);choiceMenus.put(groupId,menu);choiceOptions.put(groupId,choices);card.addView(menu,new LinearLayout.LayoutParams(-1,dp(62)));Button go=button("Open selection",true,()->{int index=menu.getSelectedItemPosition();if(index<1||index>=choices.size()){setStatus("Choose an option first.",false);return;}select(choices.get(index).optString("id"),snapshot);});go.setContentDescription("Open "+group.optString("label","catalog selection"));card.addView(go,new LinearLayout.LayoutParams(-1,dp(48)));actionViews.add(menu);actionViews.add(go);parent.addView(card);
 }
 private void select(String optionId,JSONObject snapshot){if(!isCurrent(snapshot)){setStatus("The catalog changed. Choose from the refreshed options.",false);refreshSnapshot();return;}try{mutate("select",new JSONObject().put("optionId",optionId).put("snapshotId",snapshot.getString("snapshotId")));}catch(Exception e){refreshSnapshot();}}
 private void selectMore(JSONObject snapshot){if(!isCurrent(snapshot)){refreshSnapshot();return;}try{mutate("more",new JSONObject().put("snapshotId",snapshot.getString("snapshotId")));}catch(Exception e){refreshSnapshot();}}
 private boolean isCurrent(JSONObject snapshot){return !inFlight&&!mutationPending&&findPhase==IDLE&&pendingPartId.isEmpty()&&latest!=null&&vin.equals(latest.optString("vin"))&&snapshot.optString("snapshotId").equals(latest.optString("snapshotId"))&&latest.optBoolean("ready");}
 private void addPart(LinearLayout parent,JSONObject part,JSONObject snapshot){
  if(!vin.equals(part.optString("vin"))||!currentBase.equals(part.optString("base")))return;LinearLayout card=card();card.setTag("guided-part-"+part.optString("serviceNumber"));card.addView(label("BASE "+currentBase+"  ·  SERVICE NUMBER",10,MUTED,true));TextView number=spaced(part.optString("serviceNumber"),23,INK);number.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);number.setTextIsSelectable(true);card.addView(number);String description=part.optString("description");if(!description.isEmpty())card.addView(spaced(description,15,INK));addPartField(card,"Application",part.optString("application"));String from=part.optString("from"),to=part.optString("to");if(!from.isEmpty()||!to.isEmpty())addPartField(card,"Build dates",(from.isEmpty()?"Not shown":from)+" → "+(to.isEmpty()?"Not shown":to));addPartField(card,"Quantity",part.optString("quantity"));addPartField(card,"Catalog notes",part.optString("remarks"));boolean incomplete=part.optBoolean("requiresCatalogReview");if(incomplete)card.addView(spaced("The catalog has not exposed the complete application details for this row. Check it in the catalog before choosing a service number.",13,MUTED));else if(!part.optBoolean("visible",true))card.addView(spaced("Loaded earlier · returns to the catalog row for a fresh check.",12,MUTED));Button choose=button(incomplete?"Check in catalog":"Review this part",true,()->{if(incomplete)setCatalogVisible(true);else if(!part.optBoolean("visible",true))locatePart(part,snapshot);else reviewPart(part,snapshot);});LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(48));params.topMargin=dp(14);card.addView(choose,params);actionViews.add(choose);parent.addView(card);
 }
 private void addPartField(LinearLayout card,String name,String value){if(value==null||value.isEmpty())return;card.addView(spaced(name+"  ·  "+value,13,MUTED));}
 private ArrayAdapter<String> choiceAdapter(List<String> labels){return new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,labels){
  @Override public View getView(int position,View recycled,ViewGroup parent){TextView row=(TextView)super.getView(position,recycled,parent);row.setSingleLine(false);row.setMaxLines(2);row.setEllipsize(android.text.TextUtils.TruncateAt.END);row.setTextSize(14);row.setTextColor(INK);row.setGravity(Gravity.CENTER_VERTICAL);return row;}
  @Override public View getDropDownView(int position,View recycled,ViewGroup parent){TextView row=(TextView)super.getDropDownView(position,recycled,parent);row.setSingleLine(false);row.setMaxLines(4);row.setEllipsize(android.text.TextUtils.TruncateAt.END);row.setTextSize(14);row.setTextColor(INK);row.setPadding(dp(16),dp(12),dp(16),dp(12));row.setMinHeight(dp(56));row.setGravity(Gravity.CENTER_VERTICAL);return row;}
 };}
 private void reviewPart(JSONObject part,JSONObject snapshot){
  if(part.optBoolean("requiresCatalogReview")||!part.optBoolean("visible",true)){setStatus("Open the catalog to inspect this part before saving.",false);return;}
  if(!isCurrent(snapshot)){refreshSnapshot();return;}handler.removeCallbacks(poll);String context=part.optString("context",snapshot.optString("context"));StringBuilder detail=new StringBuilder("VIN: ").append(vin).append("\nBase: ").append(currentBase);for(String field:new String[]{context,part.optString("description"),part.optString("application"),part.optString("remarks")})if(!field.isEmpty())detail.append("\n\n").append(field);if(!part.optString("from").isEmpty()||!part.optString("to").isEmpty())detail.append("\n\nBuild dates: ").append(part.optString("from")).append(" → ").append(part.optString("to"));detail.append("\n\nCheck the position, equipment and latest supersession. Save this reviewed selection to your job?");final int capturedGeneration=generation;
  reviewDialog=new AlertDialog.Builder(this).setTitle(part.optString("serviceNumber")).setMessage(detail.toString()).setNegativeButton("Keep looking",null).setPositiveButton("Save to job",null).create();reviewDialog.setOnShowListener(dialog->reviewDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view->{
   if(capturedGeneration!=generation||!isCurrent(snapshot)){reviewDialog.dismiss();reviewDialog=null;setStatus("The catalog changed. Review the current part again.",false);schedule(0);return;}
   try{JSONObject extra=new JSONObject().put("partId",part.getString("id")).put("snapshotId",snapshot.getString("snapshotId"));reviewDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);runAdapter("verify",extra,result->{JSONObject selected=result.optJSONObject("part");if(selected==null||selected.optBoolean("requiresCatalogReview")||!selected.optBoolean("visible",true)||!vin.equals(selected.optString("vin"))||!currentBase.equals(selected.optString("base"))||!selected.optString("serviceNumber").matches("[A-Z0-9][A-Z0-9 -]{2,39}")){setStatus("This selection could not be verified against the current vehicle. Repeat the lookup.",false);schedule(0);return;}try{selected.put("source","Snap-on EPC — employee selected").put("requestId",requestId);setResult(RESULT_OK,new Intent().putExtra("selection",selected.toString()));finish();}catch(JSONException e){setStatus("Unable to save this catalog selection.",false);}});reviewDialog.dismiss();reviewDialog=null;}catch(Exception e){if(reviewDialog!=null)reviewDialog.dismiss();reviewDialog=null;setStatus("The selection expired. Review the current part again.",false);schedule(0);}
  }));reviewDialog.setOnDismissListener(dialog->{reviewDialog=null;if(!isFinishing()&&!destroyed)schedule(2000);});reviewDialog.show();
 }

 private void updateEnabled(){boolean enabled=latest!=null&&!catalogFailed&&!inFlight&&!mutationPending&&findPhase==IDLE&&pendingPartId.isEmpty();if(findButton!=null)findButton.setEnabled(!inFlight&&!mutationPending&&findPhase==IDLE&&pendingPartId.isEmpty());if(bases!=null)bases.setEnabled(!inFlight&&!mutationPending&&findPhase==IDLE&&pendingPartId.isEmpty());for(View view:actionViews)view.setEnabled(enabled);}
 private void setStatus(String text,boolean loading){if(status!=null)status.setText(text);if(progress!=null)progress.setVisibility(loading?View.VISIBLE:View.INVISIBLE);if(cancelButton!=null)cancelButton.setVisibility(loading?View.VISIBLE:View.GONE);}
 private void showMessage(String title,String description){if(content==null)return;renderedKey="";captureChoices();content.removeAllViews();actionViews.clear();choiceMenus.clear();choiceOptions.clear();LinearLayout card=card();card.addView(label(title,21,INK,true));card.addView(spaced(description,15,MUTED));content.addView(card);}
 private void addContentButton(String title,Runnable action){Button button=button(title,true,action);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(50));params.topMargin=dp(8);content.addView(button,params);if(title.equals("Load more catalog results"))actionViews.add(button);}
 private LinearLayout column(int color){LinearLayout view=new LinearLayout(this);view.setOrientation(LinearLayout.VERTICAL);view.setBackgroundColor(color);return view;}
 private LinearLayout card(){LinearLayout view=column(Color.WHITE);view.setPadding(dp(17),dp(17),dp(17),dp(17));GradientDrawable background=new GradientDrawable();background.setColor(Color.WHITE);background.setCornerRadius(dp(16));background.setStroke(dp(1),0xffdce4ef);view.setBackground(background);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=dp(13);view.setLayoutParams(params);return view;}
 private TextView label(String text,int size,int color,boolean bold){TextView view=new TextView(this);view.setText(text);view.setTextSize(size);view.setTextColor(color);view.setLineSpacing(dp(3),1);if(bold)view.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return view;}
 private TextView spaced(String text,int size,int color){TextView view=label(text,size,color,false);view.setPadding(0,dp(9),0,0);return view;}
 private Button button(String text,boolean primary,Runnable action){Button view=new Button(this);view.setText(text);view.setAllCaps(false);view.setTextSize(12);view.setTypeface(Typeface.DEFAULT,Typeface.BOLD);view.setMinWidth(0);view.setMinimumWidth(0);view.setMinHeight(0);view.setMinimumHeight(0);view.setPadding(dp(10),dp(4),dp(10),dp(4));view.setTextColor(primary?Color.WHITE:INK);GradientDrawable background=new GradientDrawable();background.setColor(primary?BLUE:Color.WHITE);background.setCornerRadius(dp(10));view.setBackground(background);view.setOnClickListener(v->action.run());return view;}
 private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
 @Override protected void onResume(){super.onResume();resumed=true;if(web!=null){web.onResume();if(pageLoading)handler.postDelayed(pageTimeout,30000);schedule(200);}}
 @Override protected void onPause(){resumed=false;boolean interrupted=findPhase!=IDLE||mutationPending||inFlight;invalidateLookup(false);handler.removeCallbacksAndMessages(null);if(interrupted){autoStartPending=false;setStatus("Lookup paused. Tap Find parts to retry if the catalog step did not finish.",false);}if(web!=null)web.onPause();super.onPause();}
 @Override public void onBackPressed(){if(catalogVisible){setCatalogVisible(false);return;}super.onBackPressed();}
 @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);captureChoices();state.putString("lookup-base",currentBase);state.putBoolean("catalog-visible",catalogVisible);state.putBoolean("lookup-auto-start",autoStartPending);state.putString("lookup-ui-scope",uiScope);state.putInt("lookup-scroll",guided==null?0:guided.getScrollY());Bundle selected=new Bundle();for(Map.Entry<String,String> entry:choicesByGroup.entrySet())selected.putString(entry.getKey(),entry.getValue());state.putBundle("lookup-choices",selected);}
 @Override protected void onDestroy(){destroyed=true;invalidateLookup(false);handler.removeCallbacksAndMessages(null);if(session!=null)sessions.release(session);web=null;session=null;super.onDestroy();}
}
