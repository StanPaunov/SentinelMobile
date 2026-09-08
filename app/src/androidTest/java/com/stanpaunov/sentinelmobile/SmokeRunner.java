package com.stanpaunov.sentinelmobile;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Framework-only smoke runner. Install with assembleDebugAndroidTest, then am instrument. */
public class SmokeRunner extends Instrumentation {
    private Activity activity;
    private int checks;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();SystemClock.sleep(2000);
            check((long)field(field(activity,"snapshot"),"scannedAt")>0,"Scan completed");
            for(int i=0;i<5;i++) {
                final int index=i;
                ui(()->{
                    LinearLayout nav=(LinearLayout)field(activity,"nav");nav.getChildAt(index).performClick();
                    check((int)field(activity,"tab")==index,"Tap tab "+index);
                    check(((LinearLayout)field(activity,"content")).getChildCount()>1,"Tab has content");
                });
            }
            ui(()->{
                call(activity,"selectTab",new Class[]{int.class},0);
                long at=(long)field(field(activity,"snapshot"),"scannedAt");
                set(activity,"dark",false);call(activity,"buildUi");
                check((long)field(field(activity,"snapshot"),"scannedAt")==at,"Theme preserves readings");
                set(activity,"language","bg");call(activity,"localize");call(activity,"buildUi");
                check(allText(activity.getWindow().getDecorView()).contains("Проверете телефона"),"Bulgarian UI");
                for(int size=0;size<3;size++){set(activity,"textChoice",size);call(activity,"buildUi");}
                set(activity,"textChoice",0);set(activity,"dark",true);
                set(activity,"language","en");call(activity,"localize");call(activity,"save");call(activity,"buildUi");
            });
            for(int choice=0;choice<4;choice++) {
                final int c=choice;
                ui(()->{
                    set(activity,"refreshChoice",c);call(activity,"save");call(activity,"schedule");
                    Handler handler=(Handler)field(activity,"handler");Runnable callback=(Runnable)field(activity,"autoRefresh");
                    check(handler.hasCallbacks(callback)==(c!=0),"Refresh scheduled "+c);
                });
            }
            ui(()->{set(activity,"refreshChoice",1);call(activity,"schedule");});
            long before=(long)field(field(activity,"snapshot"),"scannedAt");
            SystemClock.sleep(6200);waitForIdleSync();
            check((long)field(field(activity,"snapshot"),"scannedAt")>before,"Five-second refresh actually runs");
            ui(()->{set(activity,"refreshChoice",0);call(activity,"schedule");});
            before=(long)field(field(activity,"snapshot"),"scannedAt");
            SystemClock.sleep(6200);
            check((long)field(field(activity,"snapshot"),"scannedAt")==before,"Never disables timed refresh");
            ui(()->{set(activity,"refreshChoice",3);call(activity,"save");call(activity,"schedule");});
            check(!allTextOnUi().contains("Installed applications"),"No application enumeration UI");
            ui(()->{
                check(activity.getSharedPreferences("display",0).getInt("refresh",-1)==3,"Refresh persisted");
                check(activity.getSharedPreferences("display",0).getString("language","").equals("en"),"Language persisted");
            });

            // Exercise real horizontal gesture dispatch.
            ui(()->call(activity,"selectTab",new Class[]{int.class},0));
            int[] point=new int[2];int[] width=new int[1];
            ui(()->{((View)field(activity,"content")).getLocationOnScreen(point);width[0]=activity.getWindow().getDecorView().getWidth();});
            long down=SystemClock.uptimeMillis();float y=point[1]+150;
            sendPointerSync(MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,width[0]*.85f,y,0));
            sendPointerSync(MotionEvent.obtain(down,down+100,MotionEvent.ACTION_MOVE,width[0]*.4f,y,0));
            sendPointerSync(MotionEvent.obtain(down,down+200,MotionEvent.ACTION_UP,width[0]*.15f,y,0));waitForIdleSync();
            check((int)field(activity,"tab")==1,"Swipe changes tab");
            // Simulated coordinates are test data; Android permission still gates the clipboard action.
            ui(()->{
                call(activity,"selectTab",new Class[]{int.class},0);
                android.location.Location loc=new android.location.Location("gps");
                loc.setLatitude(42.6977082);loc.setLongitude(23.3218675);loc.setAccuracy(5);
                loc.setTime(System.currentTimeMillis());loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
                set(activity,"location",loc);call(activity,"renderContent",new Class[]{boolean.class},true);
                TextView coords=find(activity.getWindow().getDecorView(),"42.6977082");
                check(coords!=null,"Coordinates shown without rounding");
                check(coords.performLongClick(),"Coordinate long press handled");
                android.content.ClipboardManager clipboard=activity.getSystemService(android.content.ClipboardManager.class);
                check(clipboard.getPrimaryClip()!=null && "42.6977082, 23.3218675".contentEquals(clipboard.getPrimaryClip().getItemAt(0).getText()),"Actual coordinates copied");
            });
            for(String action:new String[]{android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS,
                android.provider.Settings.ACTION_WIRELESS_SETTINGS,android.provider.Settings.ACTION_SECURITY_SETTINGS,
                android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS,android.provider.Settings.ACTION_DEVICE_INFO_SETTINGS}) {
                ui(()->call(activity,"settings",new Class[]{String.class},action));
                SystemClock.sleep(700);sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(700);waitForIdleSync();
                check(!activity.isFinishing(),"Returned from "+action);
            }
            ui(()->{set(activity,"refreshChoice",3);call(activity,"save");call(activity,"schedule");});
            result.putString("stream","PASS: "+checks+" device smoke assertions\n");
            finish(Activity.RESULT_OK,result);
        }catch(Throwable e){
            result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e));
            finish(Activity.RESULT_CANCELED,result);
        }
    }
    private TextView find(View view,String needle) {
        if(view instanceof TextView && ((TextView)view).getText().toString().contains(needle))return (TextView)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
            TextView found=find(((ViewGroup)view).getChildAt(i),needle);if(found!=null)return found;
        }
        return null;
    }
    private String allTextOnUi() throws Exception {
        AtomicReference<String> r=new AtomicReference<>();ui(()->r.set(allText(activity.getWindow().getDecorView())));return r.get();
    }
    private String allText(View v) {
        StringBuilder b=new StringBuilder(v instanceof TextView?((TextView)v).getText():"");
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)b.append('\n').append(allText(((ViewGroup)v).getChildAt(i)));
        return b.toString();
    }
    private interface Action{void run()throws Exception;}
    private void ui(Action a)throws Exception {
        AtomicReference<Throwable> error=new AtomicReference<>();
        runOnMainSync(()->{try{a.run();}catch(Throwable t){error.set(t);}});
        waitForIdleSync();if(error.get()!=null)throw new Exception(error.get());
    }
    private void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    private Object field(Object o,String name)throws Exception {Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private void set(Object o,String name,Object value)throws Exception {Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
    private void call(Object o,String name)throws Exception {call(o,name,new Class[0]);}
    private void call(Object o,String name,Class[] types,Object... args)throws Exception {Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(o,args);}
}
