package dev.urbanrunnerx.partsdesk;

import android.app.Application;
import android.content.ComponentCallbacks2;

/** Owns the warm catalog only while this app process is alive. */
public final class PartsApplication extends Application {
 private EpcSessionStore epcSessions;
 EpcSessionStore epcSessions(){if(epcSessions==null)epcSessions=new EpcSessionStore(this);return epcSessions;}
 @Override public void onTrimMemory(int level){super.onTrimMemory(level);if(epcSessions!=null&&(level==ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN||level>=ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW))epcSessions.discardIdle();}
 @Override public void onLowMemory(){super.onLowMemory();if(epcSessions!=null)epcSessions.discardIdle();}
}
