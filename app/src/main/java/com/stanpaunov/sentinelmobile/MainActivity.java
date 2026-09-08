package com.stanpaunov.sentinelmobile;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.*;
import android.location.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private Future<?> scanTask;
    private Monitor monitor;
    private Monitor.Snapshot snapshot=new Monitor.Snapshot();
    private android.content.SharedPreferences prefs;
    private Context localized;
    private String language;
    private boolean dark,started,busy,batteryExpanded;
    private int textChoice,tab,refreshChoice,generation;
    private static final int[] INTERVALS={0,5000,30000,60000};
    private static final String[] TABS={"overview","network","security","storage","device"};
    private LinearLayout root,content,nav;
    private ScrollView scroll;
    private HorizontalScrollView navScroll;
    private TextView scanLabel;
    private Button refreshButton;
    private LocationManager locationManager;
    private Location location;
    private boolean locating;
    private String locationState="location_timeout";
    private long lastLocationRequest=-1;
    private float downX,downY;
    private boolean swipeEligible;
    private final Runnable autoRefresh=()->requestScan();
    private final Runnable locationTimeout=()->{ stopLocation(); locationState="location_timeout"; renderContent(true); };
    private final LocationListener locationListener=new LocationListener() {
        @Override public void onLocationChanged(Location fresh) {
            if(!started || fresh==null) return;
            if(location==null || fresh.getElapsedRealtimeNanos()>=location.getElapsedRealtimeNanos()) location=fresh;
            stopLocation();renderContent(true);
        }
        @Override public void onProviderDisabled(String provider) { if(started) refreshLocation(false); }
        @Override public void onProviderEnabled(String provider) { }
        @Override public void onStatusChanged(String provider,int status,Bundle extras) { }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs=getSharedPreferences("display",MODE_PRIVATE);
        language=prefs.getString("language",Locale.getDefault().getLanguage().equals("bg")?"bg":"en");
        dark=prefs.getBoolean("dark",true);
        textChoice=prefs.getInt("textSize",0);
        tab=Math.max(0,Math.min(4,prefs.getInt("tab",0)));
        refreshChoice=Math.max(0,Math.min(3,prefs.getInt("refresh",3)));
        monitor=new Monitor(this);
        locationManager=getSystemService(LocationManager.class);
        localize();buildUi();
    }
    @Override protected void onStart() {
        super.onStart();started=true;requestScan();refreshLocation(true);
    }
    @Override protected void onResume() {
        super.onResume();
        // Settings activities may pause us without stopping in multi-window mode.
        if(started) { requestScan();refreshLocation(false); }
    }
    @Override protected void onStop() {
        started=false;generation++;busy=false;
        handler.removeCallbacksAndMessages(null);
        if(scanTask!=null) scanTask.cancel(true);
        stopLocation();location=null;
        executor.execute(monitor::pause);
        super.onStop();
    }
    @Override protected void onDestroy() { executor.shutdownNow();super.onDestroy(); }
    @SuppressLint("AppBundleLocaleChanges") // bundle.language.enableSplit=false; verified in BundleConfig.pb.
    private void localize() {
        Configuration c=new Configuration(getResources().getConfiguration());
        c.setLocale(Locale.forLanguageTag(language));
        localized=createConfigurationContext(c);
    }
    private String t(String key) {
        int id=Strings.id(key);
        return id==0?key:localized.getString(id);
    }
    private void save() {
        prefs.edit().putBoolean("dark",dark).putInt("textSize",textChoice).putString("language",language)
            .putInt("tab",tab).putInt("refresh",refreshChoice).apply();
    }
    private float size(float sp) { return sp+textChoice*3; }
    private int dp(float v) {return Math.round(v*getResources().getDisplayMetrics().density);}
    private int bg(){return Color.parseColor(dark?"#0B1511":"#F2F6F1");}
    private int panel(){return Color.parseColor(dark?"#14271E":"#FFFFFF");}
    private int fg(){return Color.parseColor(dark?"#F3FAF6":"#132C20");}
    private int muted(){return Color.parseColor(dark?"#BDCEC3":"#496251");}
    private int accent(){return Color.parseColor(dark?"#83E8BA":"#15653F");}
    private int border(){return Color.parseColor(dark?"#355343":"#B6CFC0");}
    private int warning(){return Color.parseColor(dark?"#FFD38B":"#754900");}
    private GradientDrawable shape(int fill,int stroke) {
        GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(10));d.setStroke(dp(1),stroke);return d;
    }
    private LinearLayout vertical() { LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l; }
    private TextView text(LinearLayout parent,String value,float sp,int color,boolean bold) {
        TextView v=new TextView(this);v.setText(value);v.setTextSize(size(sp));v.setTextColor(color);
        v.setFontFeatureSettings("tnum");v.setPadding(0,dp(3),0,dp(3));
        if(bold)v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        parent.addView(v,new LinearLayout.LayoutParams(-1,-2));return v;
    }
    private Button button(LinearLayout parent,String label,boolean primary,Runnable action) {
        Button b=new Button(this);b.setAllCaps(false);b.setText(label);b.setTextSize(size(18));
        b.setTextColor(primary?(dark?Color.parseColor("#0C2D1D"):Color.WHITE):fg());
        b.setMinHeight(dp(56));b.setMinimumHeight(dp(56));b.setMinWidth(0);b.setMinimumWidth(0);
        b.setPadding(dp(14),dp(10),dp(14),dp(10));
        b.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(dark?0x337FFFFF:0x22000000),
            shape(primary?accent():panel(),primary?accent():border()),null));
        b.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(5),0,dp(5));
        parent.addView(b,lp);return b;
    }
    private void buildUi() {
        root=vertical();root.setBackgroundColor(bg());setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            root.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;
        });
        root.requestApplyInsets();
        getWindow().setStatusBarColor(bg());getWindow().setNavigationBarColor(bg());
        getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        // In landscape or with very large system fonts, the entire header scrolls.
        boolean compact=getResources().getConfiguration().screenHeightDp<520 || getResources().getConfiguration().fontScale>1.45f;
        LinearLayout header=vertical();header.setPadding(dp(18),dp(8),dp(18),dp(4));
        if(!compact)root.addView(header,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout branding=new LinearLayout(this);branding.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(branding,new LinearLayout.LayoutParams(-1,-2));
        View mark=new View(this) {
            final Paint paint=new Paint(3); final Path shieldPath=new Path();
            @Override protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);float w=getWidth(),h=getHeight();
                Path p=shieldPath;p.reset();p.moveTo(w*.5f,h*.04f);p.lineTo(w*.9f,h*.2f);p.lineTo(w*.87f,h*.62f);
                p.quadTo(w*.8f,h*.85f,w*.5f,h*.97f);p.quadTo(w*.2f,h*.85f,w*.13f,h*.62f);p.lineTo(w*.1f,h*.2f);p.close();
                paint.setColor(accent());canvas.drawPath(p,paint);paint.setColor(bg());paint.setStrokeWidth(dp(3));
                canvas.drawLine(w*.5f,h*.3f,w*.5f,h*.68f,paint);canvas.drawLine(w*.32f,h*.49f,w*.68f,h*.49f,paint);
            }
        };
        mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        branding.addView(mark,new LinearLayout.LayoutParams(dp(30),dp(36)));
        TextView title=text(branding,"Sentinel Mobile",26,fg(),true);
        LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(0,-2,1);titleLp.setMarginStart(dp(10));title.setLayoutParams(titleLp);
        text(header,t("subtitle"),17,muted(),false);
        LinearLayout tools=new LinearLayout(this);header.addView(tools);
        Button lang=button(tools,language.equals("en")?"Български":"English",false,()->{
            language=language.equals("en")?"bg":"en";save();localize();buildUi();
        });lang.setContentDescription(language.equals("en")?"Switch to Bulgarian / Български":"Смяна на английски / English");
        Button display=button(tools,t("display"),false,this::showDisplay);
        LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,-2,1);a.setMargins(0,dp(6),dp(5),dp(6));lang.setLayoutParams(a);
        LinearLayout.LayoutParams b=new LinearLayout.LayoutParams(0,-2,1);b.setMargins(dp(5),dp(6),0,dp(6));display.setLayoutParams(b);
        scanLabel=text(header,"",15,muted(),false);updateScanLabel();
        LinearLayout refreshRow=new LinearLayout(this);
        boolean stackedRefresh=compact||textChoice>0;
        refreshRow.setOrientation(stackedRefresh?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);
        refreshRow.setGravity(Gravity.CENTER_VERTICAL);header.addView(refreshRow);
        TextView refreshTitle=text(refreshRow,t("refresh_every"),16,muted(),false);
        refreshTitle.setLayoutParams(stackedRefresh?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-2,1));
        Spinner headerRefresh=spinner(refreshRow,new String[]{t("never"),t("five_seconds"),t("thirty_seconds"),t("one_minute")},refreshChoice);
        headerRefresh.setLayoutParams(stackedRefresh?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-2,1));
        headerRefresh.setContentDescription(t("refresh_every"));
        headerRefresh.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p,View v,int position,long id) {
                if(refreshChoice!=position){refreshChoice=position;save();schedule();}
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });
        navScroll=new HorizontalScrollView(this);navScroll.setHorizontalScrollBarEnabled(false);
        nav=new LinearLayout(this);nav.setPadding(dp(14),0,dp(14),dp(6));navScroll.addView(nav);
        if(!compact)root.addView(navScroll,new LinearLayout.LayoutParams(-1,-2));
        renderTabs();
        scroll=new ScrollView(this);scroll.setFillViewport(true);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout stack=vertical();scroll.addView(stack,new ScrollView.LayoutParams(-1,-2));
        if(compact){stack.addView(header);stack.addView(navScroll);}
        content=vertical();content.setPadding(dp(18),dp(10),dp(18),dp(24));stack.addView(content);
        renderContent(false);
    }
    private void renderTabs() {
        nav.removeAllViews();
        for(int i=0;i<TABS.length;i++) {
            final int target=i;Button b=button(nav,t(TABS[i]),i==tab,()->selectTab(target));
            b.setSelected(i==tab);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.setMargins(dp(4),0,dp(4),0);b.setLayoutParams(lp);
        }
        navScroll.post(()->{ if(tab<nav.getChildCount())navScroll.smoothScrollTo(nav.getChildAt(tab).getLeft()-dp(14),0); });
    }
    private void selectTab(int selected) {tab=selected;save();renderTabs();renderContent(false);scroll.scrollTo(0,0);}
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent e) {
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN) {
            downX=e.getX();downY=e.getY();
            int[] p=new int[2];if(content!=null)content.getLocationOnScreen(p);
            swipeEligible=content!=null&&e.getRawY()>=p[1];
        }
        if(e.getActionMasked()==MotionEvent.ACTION_UP && swipeEligible) {
            float dx=e.getX()-downX,dy=e.getY()-downY;
            if(Math.abs(dx)>dp(88)&&Math.abs(dx)>Math.abs(dy)*1.8) {
                MotionEvent cancel=MotionEvent.obtain(e);cancel.setAction(MotionEvent.ACTION_CANCEL);super.dispatchTouchEvent(cancel);cancel.recycle();
                selectTab(Math.max(0,Math.min(4,tab+(dx<0?1:-1))));return true;
            }
        }
        return super.dispatchTouchEvent(e);
    }
    @SuppressLint("SetTextI18n") // Both the label and the time are localized independently.
    private void updateScanLabel() {
        if(scanLabel!=null)scanLabel.setText(t("last_scan")+": "+(snapshot.scannedAt==0?t("not_scanned"):
            DateFormat.getTimeInstance(DateFormat.MEDIUM,Locale.forLanguageTag(language)).format(new Date(snapshot.scannedAt))));
    }
    private void schedule() {
        handler.removeCallbacks(autoRefresh);
        if(started&&INTERVALS[refreshChoice]>0)handler.postDelayed(autoRefresh,INTERVALS[refreshChoice]);
    }
    private void requestScan() {
        if(!started||busy)return;
        busy=true;handler.removeCallbacks(autoRefresh);
        if(refreshButton!=null){refreshButton.setText(t("checking"));refreshButton.setEnabled(false);}
        final int token=++generation;
        scanTask=executor.submit(()->{
            Monitor.Snapshot result=monitor.scan();
            handler.post(()->{
                if(token!=generation||!started)return;
                busy=false;snapshot=result;updateScanLabel();renderContent(true);refreshLocation(false);schedule();
            });
        });
    }
    private LinearLayout card(String title) {
        LinearLayout c=vertical();c.setPadding(dp(18),dp(16),dp(18),dp(16));c.setBackground(shape(panel(),border()));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(14));content.addView(c,lp);
        if(title!=null) {TextView h=text(c,title,22,fg(),true);if(Build.VERSION.SDK_INT>=28)h.setAccessibilityHeading(true);}
        return c;
    }
    private void row(LinearLayout c,String key,String value) {
        LinearLayout item=vertical();item.setPadding(0,dp(9),0,dp(9));c.addView(item,new LinearLayout.LayoutParams(-1,-2));
        text(item,t(key),17,muted(),false);text(item,value,20,fg(),true);
    }
    private void progress(LinearLayout c,int value) {
        if(value<0)return;
        ProgressBar p=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        p.setProgressTintList(android.content.res.ColorStateList.valueOf(accent()));
        p.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(border()));
        p.setProgress(value);p.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(10));lp.setMargins(0,dp(8),0,dp(8));c.addView(p,lp);
    }
    private void renderContent(boolean keepScroll) {
        if(content==null)return;
        int y=keepScroll?scroll.getScrollY():0;
        content.removeAllViews();refreshButton=null;
        if(tab==0)overview();
        if(tab==1)network();
        if(tab==2)security();
        if(tab==3)storage();
        if(tab==4)device();
        button(content,t("help"),false,this::showHelp);
        if(keepScroll)scroll.post(()->scroll.scrollTo(0,y));
    }
    private void overview() {
        refreshButton=button(content,t(busy?"checking":"refresh_now"),true,this::requestScan);refreshButton.setEnabled(!busy);
        LinearLayout battery=card(t("battery"));
        text(battery,t("remaining"),18,muted(),false);
        text(battery,remainingText(),25,accent(),true);
        text(battery,t("level"),18,muted(),false);
        text(battery,snapshot.level<0?t("unavailable"):snapshot.level+"%",40,fg(),true);
        progress(battery,snapshot.level);
        text(battery,t(batteryStatus()),20,muted(),false);
        button(battery,t(batteryExpanded?"hide_details":"battery_details"),false,()->{batteryExpanded=!batteryExpanded;renderContent(true);});
        if(batteryExpanded) {
            text(battery,t(snapshot.plugged!=0?"charge_help":"estimate_help"),18,muted(),false);
            row(battery,"health",t(batteryHealth()));
            row(battery,"temperature",snapshot.temp==Integer.MIN_VALUE?t("unavailable"):number(snapshot.temp/10.0,1)+" °C");
            row(battery,"voltage",snapshot.voltage<0?t("unavailable"):number(snapshot.voltage/1000.0,2)+" V");
            row(battery,"power_source",t(powerSource()));
        }
        List<String> alerts=alerts();
        LinearLayout notices=card(t("attention"));
        if(alerts.isEmpty())text(notices,t("no_alerts"),20,accent(),true);
        else for(String alert:alerts)text(notices,"• "+t(alert),20,warning(),false);
        text(notices,t("no_alerts_body"),18,muted(),false);
        if(snapshot.partial)text(notices,t("partial"),18,warning(),false);
        renderLocation();
        metric("memory",snapshot.totalMemory-snapshot.freeMemory,snapshot.totalMemory,"memory_help");
        metric("storage",snapshot.totalStorage-snapshot.freeStorage,snapshot.totalStorage,"storage_help");
        LinearLayout net=card(t("network_rate"));
        text(net,rate(snapshot.rxRate==-2||snapshot.txRate==-2?-2:snapshot.rxRate<0||snapshot.txRate<0?-1:snapshot.rxRate+snapshot.txRate),28,accent(),true);
        text(net,t("rate_help"),18,muted(),false);
        LinearLayout limits=card(t("limits"));text(limits,t("limits_body"),18,muted(),false);
    }
    private List<String> alerts() {
        ArrayList<String> a=new ArrayList<>();
        if(Boolean.FALSE.equals(snapshot.locked))a.add("alert_lock");
        if(Boolean.TRUE.equals(snapshot.debugging))a.add("alert_adb");
        if(Boolean.TRUE.equals(snapshot.developer))a.add("alert_developer");
        if(percent(snapshot.totalStorage-snapshot.freeStorage,snapshot.totalStorage)>=90)a.add("alert_storage");
        if(percent(snapshot.totalMemory-snapshot.freeMemory,snapshot.totalMemory)>=85)a.add("alert_memory");
        if(snapshot.level>=0&&snapshot.level<=20&&snapshot.plugged==0)a.add("alert_battery");
        if(snapshot.health==BatteryManager.BATTERY_HEALTH_OVERHEAT||snapshot.temp>=450)a.add("alert_heat");
        return a;
    }
    private void metric(String title,long used,long total,String help) {
        LinearLayout c=card(t(title));int value=percent(used,total);
        text(c,value<0?t("unavailable"):value+"% "+t("used").toLowerCase(Locale.forLanguageTag(language)),28,accent(),true);
        progress(c,value);text(c,t(help),18,muted(),false);
    }
    private void network() {
        button(content,t("network_settings"),true,()->settings(Settings.ACTION_WIRELESS_SETTINGS));
        LinearLayout rates=card(t("network_rate"));row(rates,"download",rate(snapshot.rxRate));row(rates,"upload",rate(snapshot.txRate));
        text(rates,t("rate_help"),18,muted(),false);
        LinearLayout c=card(t("connection"));row(c,"transport",t(snapshot.transport));
        row(c,"vpn_state",bool(snapshot.vpn,"active","inactive"));row(c,"metered",bool(snapshot.metered,"yes","no"));
        row(c,"internet_status",bool(snapshot.validated,"yes","no"));
        LinearLayout interfaces=card(t("interfaces"));
        if(snapshot.interfaces.isEmpty())text(interfaces,t("unavailable"),20,fg(),false);
        for(String item:snapshot.interfaces)text(interfaces,item,18,fg(),false);
        text(interfaces,t("interfaces_help"),18,muted(),false);
    }
    private void security() {
        button(content,t("security_settings"),true,()->settings(Settings.ACTION_SECURITY_SETTINGS));
        LinearLayout c=card(t("security_checks"));
        text(c,t("no_alerts_body"),18,muted(),false);
        row(c,"lock",bool(snapshot.locked,"lock_on","lock_off"));
        row(c,"debugging",bool(snapshot.debugging,"enabled","disabled"));
        row(c,"developer",bool(snapshot.developer,"enabled","disabled"));
        // No REQUEST_INSTALL_PACKAGES permission is declared, so this app cannot request installs.
        row(c,"unknown_apps",t("unknown_apps_value"));text(c,t("unknown_apps_help"),18,muted(),false);
        row(c,"vpn_state",bool(snapshot.vpn,"active","inactive"));row(c,"dns",t(snapshot.dns));
        row(c,"patch",Build.VERSION.SECURITY_PATCH.isEmpty()?t("unavailable"):Build.VERSION.SECURITY_PATCH);
        LinearLayout a=card(t("attention"));List<String> alerts=alerts();
        for(String key:alerts)text(a,"• "+t(key),20,warning(),false);
        if(alerts.isEmpty())text(a,t("no_alerts"),20,accent(),true);
    }
    private void storage() {
        button(content,t("storage_settings"),true,()->settings(Settings.ACTION_INTERNAL_STORAGE_SETTINGS));
        LinearLayout c=card(t("storage_details"));row(c,"used",bytes(snapshot.totalStorage>0?snapshot.totalStorage-snapshot.freeStorage:-1));
        progress(c,percent(snapshot.totalStorage-snapshot.freeStorage,snapshot.totalStorage));
        row(c,"free",bytes(snapshot.totalStorage>0?snapshot.freeStorage:-1));row(c,"total",bytes(snapshot.totalStorage));
        text(c,t("storage_note"),18,muted(),false);
    }
    private void device() {
        button(content,t("device_settings"),true,()->settings(Settings.ACTION_DEVICE_INFO_SETTINGS));
        LinearLayout c=card(t("device_info"));row(c,"manufacturer",Build.MANUFACTURER);row(c,"model",Build.MODEL);
        row(c,"model_code",Build.DEVICE);row(c,"android_version",Build.VERSION.RELEASE+" · API "+Build.VERSION.SDK_INT);
        row(c,"build",Build.DISPLAY);row(c,"hardware",Build.HARDWARE);text(c,t("device_note"),18,muted(),false);
        LinearLayout privacy=card(t("privacy"));text(privacy,t("privacy_body"),18,muted(),false);
    }
    private boolean hasLocation() {return checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
        ||checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    private boolean locationEnabled() {
        try {return locationManager!=null&&(Build.VERSION.SDK_INT>=28?locationManager.isLocationEnabled():
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)||locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER));}
        catch(RuntimeException e){return false;}
    }
    private boolean recent(Location l) {return l!=null && (SystemClock.elapsedRealtimeNanos()-l.getElapsedRealtimeNanos())/1_000_000L<=120_000;}
    @SuppressLint("MissingPermission")
    private void refreshLocation(boolean force) {
        if(!started)return;
        if(!hasLocation()||!locationEnabled()){location=null;stopLocation();renderContent(true);return;}
        if(!recent(location))location=null;
        try {
            List<String> providers=locationManager.getProviders(true);
            for(String p:providers) {
                Location cached=locationManager.getLastKnownLocation(p);
                if(recent(cached)&&(location==null||cached.getElapsedRealtimeNanos()>location.getElapsedRealtimeNanos()))location=cached;
            }
            if(locating || (!force&&lastLocationRequest>=0&&SystemClock.elapsedRealtime()-lastLocationRequest<60_000))return;
            lastLocationRequest=SystemClock.elapsedRealtime();locating=true;locationState="location_wait";
            boolean requested=false;
            for(String p:new String[]{LocationManager.NETWORK_PROVIDER,LocationManager.GPS_PROVIDER}) {
                if(providers.contains(p)) {
                    try {locationManager.requestLocationUpdates(p,1000,0,locationListener,Looper.getMainLooper());requested=true;}
                    catch(SecurityException ignored) { }
                }
            }
            if(requested)handler.postDelayed(locationTimeout,10_000);else {locating=false;locationState="location_unavailable";}
        } catch(RuntimeException e){stopLocation();locationState="location_unavailable";}
        renderContent(true);
    }
    private void stopLocation() {
        handler.removeCallbacks(locationTimeout);
        try{if(locationManager!=null)locationManager.removeUpdates(locationListener);}catch(RuntimeException ignored){}
        locating=false;
    }
    private void renderLocation() {
        LinearLayout c=card(t("gps"));
        if(!locationEnabled()) {text(c,t("location_reason"),18,muted(),false);button(c,t("turn_on_location"),true,()->settings(Settings.ACTION_LOCATION_SOURCE_SETTINGS));return;}
        if(!hasLocation()) {
            boolean blocked=prefs.getBoolean("askedLocation",false)&&!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
                &&!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION);
            text(c,t(blocked?"location_denied":"location_reason"),18,muted(),false);
            button(c,t(blocked?"app_settings":"allow_location"),true,()->{
                if(blocked)openAppSettings();
                else new AlertDialog.Builder(dialogContext()).setTitle(t("allow_location")).setMessage(t("location_reason"))
                    .setPositiveButton(t("continue_label"),(d,w)->{
                        prefs.edit().putBoolean("askedLocation",true).apply();
                        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},42);
                    }).setNegativeButton(t("cancel"),null).show();
            });return;
        }
        if(!recent(location)) {
            text(c,t(locating?"location_wait":locationState),20,muted(),false);
            button(c,t("try_location"),false,()->refreshLocation(true));return;
        }
        TextView coordinates=text(c,t("latitude")+"\n"+Double.toString(location.getLatitude())+"\n"+t("longitude")+"\n"+Double.toString(location.getLongitude()),20,accent(),true);
        coordinates.setOnLongClickListener(v->{copyCoordinates();return true;});
        row(c,"accuracy",location.hasAccuracy()?number(location.getAccuracy(),0)+" "+t("metres"):t("unavailable"));
        long age=Math.max(0,(SystemClock.elapsedRealtimeNanos()-location.getElapsedRealtimeNanos())/1_000_000_000);
        text(c,t("location_age")+": "+age+" "+t("seconds"),18,muted(),false);
        button(c,t("copy_coordinates"),false,this::copyCoordinates);text(c,t("copy_hint"),18,muted(),false);
    }
    private void copyCoordinates() {
        if(!hasLocation()||!locationEnabled()||!recent(location))return;
        try {
            ClipboardManager clipboard=getSystemService(ClipboardManager.class);
            if(clipboard==null)throw new IllegalStateException();
            ClipData clip=ClipData.newPlainText(t("gps"),Double.toString(location.getLatitude())+", "+Double.toString(location.getLongitude()));
            if(Build.VERSION.SDK_INT>=33) {
                PersistableBundle extras=new PersistableBundle();extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE,true);clip.getDescription().setExtras(extras);
            }
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this,t("copied"),Toast.LENGTH_SHORT).show();
        } catch(RuntimeException e){Toast.makeText(this,t("copy_failed"),Toast.LENGTH_SHORT).show();}
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(code,permissions,results);
        if(code==42){refreshLocation(true);renderContent(true);}
    }
    private void settings(String action) {
        try{startActivity(new Intent(action));}
        catch(RuntimeException e){try{startActivity(new Intent(Settings.ACTION_SETTINGS));}
            catch(RuntimeException ignored){Toast.makeText(this,t("settings_unavailable"),Toast.LENGTH_LONG).show();}}
    }
    private void openAppSettings(){try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}
        catch(RuntimeException e){settings(Settings.ACTION_SETTINGS);}}
    @SuppressLint("AppBundleLocaleChanges") // Both languages are bundled; no downloads are used.
    private Context dialogContext() {
        android.view.ContextThemeWrapper context=new android.view.ContextThemeWrapper(this,
            dark?android.R.style.Theme_Material_Dialog_Alert:android.R.style.Theme_Material_Light_Dialog_Alert);
        Configuration configuration=new Configuration(getResources().getConfiguration());
        configuration.setLocale(Locale.forLanguageTag(language));
        context.applyOverrideConfiguration(configuration);
        return context;
    }
    private void showHelp() {
        new AlertDialog.Builder(dialogContext()).setTitle(t("help")).setMessage(t("help_body")+"\n\n"+t("privacy_body")+"\n\n"+t("description")+"\n\n"+t("swipe_hint"))
            .setPositiveButton(t("close"),null).show();
    }
    private void showDisplay() {
        LinearLayout box=vertical();box.setPadding(dp(20),dp(8),dp(20),dp(8));
        button(box,t(dark?"light_mode":"dark_mode"),false,()->{dark=!dark;save();buildUi();});
        text(box,t("text_size"),20,fg(),true);
        Spinner sizes=spinner(box,new String[]{t("standard"),t("large"),t("largest")},textChoice);
        text(box,t("refresh_every"),20,fg(),true);
        Spinner refresh=spinner(box,new String[]{t("never"),t("five_seconds"),t("thirty_seconds"),t("one_minute")},refreshChoice);
        AlertDialog dialog=new AlertDialog.Builder(dialogContext()).setTitle(t("preferences")).setView(scrollDialog(box))
            .setPositiveButton(t("close"),(d,w)->{textChoice=sizes.getSelectedItemPosition();refreshChoice=refresh.getSelectedItemPosition();save();buildUi();schedule();}).create();
        // Theme changes apply immediately and close the old themed dialog.
        ((Button)box.getChildAt(0)).setOnClickListener(v->{textChoice=sizes.getSelectedItemPosition();refreshChoice=refresh.getSelectedItemPosition();dark=!dark;save();dialog.dismiss();buildUi();schedule();});
        dialog.show();
    }
    private ScrollView scrollDialog(View child) { ScrollView s=new ScrollView(this);s.addView(child);return s; }
    private Spinner spinner(LinearLayout parent,String[] values,int selected) {
        Spinner s=new Spinner(dialogContext(),Spinner.MODE_DIALOG);
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(dialogContext(),android.R.layout.simple_spinner_dropdown_item,values) {
            @Override public View getView(int position,View old,ViewGroup p) {
                TextView v=new TextView(MainActivity.this);v.setText(values[position]);v.setTextColor(fg());v.setTextSize(size(20));
                v.setSingleLine(false);v.setPadding(dp(12),dp(12),dp(12),dp(12));v.setMinHeight(dp(56));
                v.setLayoutParams(new AbsListView.LayoutParams(-1,-2));return v;
            }
            @Override public View getDropDownView(int position,View old,ViewGroup p) {
                TextView v=new TextView(MainActivity.this);v.setText(values[position]);v.setTextColor(fg());v.setTextSize(size(20));
                v.setSingleLine(false);v.setPadding(dp(12),dp(12),dp(12),dp(12));v.setMinHeight(dp(56));
                v.setLayoutParams(new AbsListView.LayoutParams(-1,-2));return v;
            }
        };
        s.setAdapter(adapter);s.setSelection(selected);parent.addView(s,new LinearLayout.LayoutParams(-1,-2));return s;
    }
    private String remainingText() {
        if(snapshot.plugged!=0)return t("charging_estimate");
        if(snapshot.remaining<0)return t("calculating");
        long minutes=Math.max(1,snapshot.remaining/60_000);
        return "≈ "+(minutes>=60?minutes/60+" "+t("hours")+" ":"")+minutes%60+" "+t("minutes");
    }
    private String bool(Boolean b,String yes,String no){return t(b==null?"unavailable":b?yes:no);}
    private String number(double n,int decimals){return String.format(Locale.forLanguageTag(language),"%."+decimals+"f",n);}
    private String bytes(long n) {
        if(n<0)return t("unavailable");
        if(n<1024)return n+" B";
        if(n<1024*1024)return number(n/1024.0,1)+" KiB";
        if(n<1024L*1024*1024)return number(n/(1024.0*1024),1)+" MiB";
        return number(n/(1024.0*1024*1024),1)+" GiB";
    }
    private String rate(long n){return n==-2?t("unavailable"):n<0?t("calculating"):bytes(n)+t("per_second");}
    private int percent(long used,long total){return total<=0?-1:(int)Math.round(used*100.0/total);}
    private String batteryStatus(){
        switch(snapshot.status){case BatteryManager.BATTERY_STATUS_CHARGING:return "charging";case BatteryManager.BATTERY_STATUS_DISCHARGING:return "discharging";
        case BatteryManager.BATTERY_STATUS_FULL:return "full";case BatteryManager.BATTERY_STATUS_NOT_CHARGING:return "not_charging";default:return "unavailable";}
    }
    private String batteryHealth(){
        switch(snapshot.health){case BatteryManager.BATTERY_HEALTH_GOOD:return "good";case BatteryManager.BATTERY_HEALTH_OVERHEAT:return "hot";
        case BatteryManager.BATTERY_HEALTH_COLD:return "cold";case BatteryManager.BATTERY_HEALTH_DEAD:return "dead";
        case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:return "over_voltage";case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE:return "failure";default:return "unavailable";}
    }
    private String powerSource(){
        if((snapshot.plugged&BatteryManager.BATTERY_PLUGGED_USB)!=0)return "USB";
        if((snapshot.plugged&BatteryManager.BATTERY_PLUGGED_AC)!=0)return "wall_power";
        if((snapshot.plugged&BatteryManager.BATTERY_PLUGGED_WIRELESS)!=0)return "wireless_power";
        return "battery_power";
    }
}
