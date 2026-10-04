package dev.urbanrunnerx.partsdesk;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.Base64;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** An optional, memory-only preview. It cannot navigate, fetch URLs, or save a part. */
@SuppressLint("ViewConstructor") // Constructed only by the native lookup; never inflated from XML.
final class PartDiagramPanel extends LinearLayout {
 private final TextView message;
 private final ProgressBar loading;
 private final DiagramImage image;
 private final Button retry,zoomIn,zoomOut,fit;
 private static final int INK=0xff172b49, MUTED=0xff60718b;

 PartDiagramPanel(Context context,String number,String vin,String base,Runnable close,Runnable tryAgain,Runnable catalog){
  super(context);setOrientation(VERTICAL);setBackgroundColor(Color.WHITE);setPadding(dp(12),dp(12),dp(12),dp(12));setTag("part-diagram-panel");setClickable(true);setFocusable(true);
  LinearLayout heading=new LinearLayout(context);heading.setGravity(Gravity.CENTER_VERTICAL);
  TextView title=text("Part diagram",19,INK);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);heading.addView(title,new LayoutParams(0,-2,1));
  Button dismiss=button("Close",close);dismiss.setTag("diagram-close");heading.addView(dismiss,new LayoutParams(dp(84),dp(48)));addView(heading);
  TextView part=text(number+" · Base "+base,15,INK);part.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);addView(part);addView(text(vin,11,MUTED));
  message=text("Checking this part against the current catalog…",13,MUTED);message.setTag("diagram-message");message.setMaxLines(6);message.setEllipsize(android.text.TextUtils.TruncateAt.END);message.setPadding(0,dp(8),0,dp(8));message.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);addView(message);
  loading=new ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal);loading.setIndeterminate(true);addView(loading,new LayoutParams(-1,dp(3)));
  image=new DiagramImage(context);image.setTag("diagram-image");addView(image,new LayoutParams(-1,0,1));
  LinearLayout zoom=new LinearLayout(context);zoomIn=button("Zoom in",()->image.zoom(1.5f));zoomOut=button("Zoom out",()->image.zoom(1/1.5f));fit=button("Fit",image::reset);
  zoom.addView(zoomOut,new LayoutParams(0,dp(48),1));zoom.addView(zoomIn,new LayoutParams(0,dp(48),1));zoom.addView(fit,new LayoutParams(0,dp(48),1));addView(zoom);
  LinearLayout actions=new LinearLayout(context);retry=button("Try again",tryAgain);retry.setTag("diagram-retry");actions.addView(retry,new LayoutParams(0,dp(48),1));Button original=button("Open catalog",catalog);original.setTag("diagram-catalog");actions.addView(original,new LayoutParams(0,dp(48),1));addView(actions);
  setLoading("Checking this part against the current catalog…");
 }
 void setLoading(String text){clearImage();message.setText(text);loading.setVisibility(VISIBLE);retry.setVisibility(GONE);}
 void unavailable(String text,boolean canRetry){clearImage();message.setText(text);loading.setVisibility(INVISIBLE);retry.setVisibility(canRetry?VISIBLE:GONE);}
 boolean showImage(String dataUrl,String title,String callout){
  Bitmap bitmap=decode(dataUrl);if(bitmap==null)return false;
  image.setBitmap(bitmap);loading.setVisibility(INVISIBLE);retry.setVisibility(GONE);zoomIn.setEnabled(true);zoomOut.setEnabled(true);fit.setEnabled(true);
  String shortTitle=title.length()>160?title.substring(0,157)+"…":title;String shortCallout=callout.length()>80?callout.substring(0,77)+"…":callout;
  String caption=(shortTitle.isEmpty()?"Current catalog illustration":shortTitle)+(shortCallout.isEmpty()?"\nNo exact callout was supplied for this part.":"\nCatalog Call/Base: "+shortCallout+". Find this label in the drawing.")+"\nPinch or use the buttons to zoom. Drag to move.";
  message.setText(caption);image.setContentDescription(caption);return true;
 }
 void clearImage(){image.clear();zoomIn.setEnabled(false);zoomOut.setEnabled(false);fit.setEnabled(false);}
 boolean hasImage(){return image.bitmap!=null;}
 /** Accept only bounded image bytes exported by the adapter, never arbitrary network/file URLs. */
 static Bitmap decode(String dataUrl){
  final String prefix="data:image/png;base64,";
  if(dataUrl==null||!dataUrl.startsWith(prefix)||dataUrl.length()>1500000)return null;
  try{
   String body=dataUrl.substring(prefix.length());if(body.isEmpty()||!body.matches("[A-Za-z0-9+/]*={0,2}"))return null;
   byte[] bytes=Base64.decode(body,Base64.NO_WRAP);
   if(bytes.length<8||bytes[0]!=(byte)137||bytes[1]!=80||bytes[2]!=78||bytes[3]!=71||bytes[4]!=13||bytes[5]!=10||bytes[6]!=26||bytes[7]!=10)return null;
   BitmapFactory.Options size=new BitmapFactory.Options();size.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,size);
   if(size.outWidth<1||size.outHeight<1||size.outWidth>4096||size.outHeight>4096||(long)size.outWidth*size.outHeight>6000000)return null;
   BitmapFactory.Options options=new BitmapFactory.Options();options.inPreferredConfig=Bitmap.Config.ARGB_8888;
   return BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
  }catch(IllegalArgumentException|OutOfMemoryError error){return null;}
 }
 private TextView text(String value,int size,int color){TextView v=new TextView(getContext());v.setText(value);v.setTextSize(size);v.setTextColor(color);return v;}
 private Button button(String value,Runnable action){Button b=new Button(getContext());b.setText(value);b.setAllCaps(false);b.setTextSize(12);b.setPadding(dp(4),0,dp(4),0);b.setMinWidth(0);b.setMinimumWidth(0);b.setOnClickListener(v->action.run());return b;}
 private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}

 private static final class DiagramImage extends View {
  private Bitmap bitmap;
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
  private final ScaleGestureDetector scaleGesture;
  private final GestureDetector gestures;
  private float scale=1,minimum=1,offsetX,offsetY;
  DiagramImage(Context context){
   super(context);setBackgroundColor(Color.WHITE);setFocusable(true);
   scaleGesture=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector detector){zoomAt(detector.getScaleFactor(),detector.getFocusX(),detector.getFocusY());return true;}});
   gestures=new GestureDetector(context,new GestureDetector.SimpleOnGestureListener(){
    @Override public boolean onDown(MotionEvent e){return true;}
    @Override public boolean onScroll(MotionEvent first,MotionEvent current,float dx,float dy){if(!scaleGesture.isInProgress()){offsetX-=dx;offsetY-=dy;clamp();invalidate();}return true;}
    @Override public boolean onSingleTapUp(MotionEvent e){performClick();return true;}
    @Override public boolean onDoubleTap(MotionEvent e){if(scale>minimum*1.5f)reset();else zoomAt(2,e.getX(),e.getY());return true;}
   });
  }
  void setBitmap(Bitmap value){clear();bitmap=value;reset();}
  void clear(){bitmap=null;invalidate();} // Release our reference; Android may still be presenting the last frame.
  void reset(){if(bitmap==null||getWidth()==0||getHeight()==0)return;minimum=Math.min((float)getWidth()/bitmap.getWidth(),(float)getHeight()/bitmap.getHeight());scale=minimum;offsetX=(getWidth()-bitmap.getWidth()*scale)/2;offsetY=(getHeight()-bitmap.getHeight()*scale)/2;invalidate();}
  void zoom(float factor){zoomAt(factor,getWidth()/2f,getHeight()/2f);}
  private void zoomAt(float factor,float x,float y){if(bitmap==null)return;float next=Math.max(minimum,Math.min(minimum*8,scale*factor)),ratio=next/scale;offsetX=x-(x-offsetX)*ratio;offsetY=y-(y-offsetY)*ratio;scale=next;clamp();invalidate();}
  private void clamp(){if(bitmap==null)return;float width=bitmap.getWidth()*scale,height=bitmap.getHeight()*scale;offsetX=width<=getWidth()?(getWidth()-width)/2:Math.min(0,Math.max(getWidth()-width,offsetX));offsetY=height<=getHeight()?(getHeight()-height)/2:Math.min(0,Math.max(getHeight()-height,offsetY));}
  @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){reset();}
  @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(bitmap!=null&&!bitmap.isRecycled()){canvas.save();canvas.translate(offsetX,offsetY);canvas.scale(scale,scale);canvas.drawBitmap(bitmap,0,0,paint);canvas.restore();}}
  @SuppressLint("ClickableViewAccessibility") // GestureDetector calls performClick in onSingleTapUp; zoom also has native buttons.
  @Override public boolean onTouchEvent(MotionEvent event){if(bitmap==null)return false;scaleGesture.onTouchEvent(event);gestures.onTouchEvent(event);return true;}
  @Override public boolean performClick(){super.performClick();return true;}
 }
}
