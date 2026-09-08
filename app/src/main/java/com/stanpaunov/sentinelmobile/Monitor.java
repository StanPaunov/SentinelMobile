package com.stanpaunov.sentinelmobile;

import android.app.*;
import android.content.*;
import android.os.*;
import android.net.*;
import android.provider.Settings;
import java.net.*;
import java.time.Duration;
import java.util.*;

/** Reads local APIs only. Snapshot is replaced atomically on the UI thread. */
final class Monitor {
    static final class Snapshot {
        long scannedAt, totalMemory, freeMemory, totalStorage, freeStorage;
        long rxRate=-1,txRate=-1, remaining=-1;
        int level=-1,status=-1,plugged,health=-1,temp=Integer.MIN_VALUE,voltage=-1;
        Boolean locked,debugging,developer,vpn,metered,validated;
        String transport="unavailable",dns="unavailable";
        List<String> interfaces=new ArrayList<>();
        boolean partial;
    }
    private final Context context;
    private long lastRx=-1,lastTx=-1,lastAt=-1;
    final BatteryEstimator estimator=new BatteryEstimator();
    Monitor(Context c) {context=c.getApplicationContext();}
    void pause() { lastAt=-1; estimator.reset(); }
    Snapshot scan() {
        Snapshot s=new Snapshot();
        try {
            ActivityManager.MemoryInfo m=new ActivityManager.MemoryInfo();
            context.getSystemService(ActivityManager.class).getMemoryInfo(m);
            s.totalMemory=m.totalMem;s.freeMemory=m.availMem;
        } catch(RuntimeException e) { s.partial=true; }
        try {
            StatFs disk=new StatFs(context.getDataDir().getPath());
            s.totalStorage=disk.getTotalBytes();s.freeStorage=disk.getAvailableBytes();
        } catch(RuntimeException e) { s.partial=true; }
        try {
            Intent b=context.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if(b!=null) {
                int scale=b.getIntExtra(BatteryManager.EXTRA_SCALE,-1),level=b.getIntExtra(BatteryManager.EXTRA_LEVEL,-1);
                s.level=scale>0&&level>=0 ? Math.round(100f*level/scale) : -1;
                s.status=b.getIntExtra(BatteryManager.EXTRA_STATUS,-1);
                s.plugged=b.getIntExtra(BatteryManager.EXTRA_PLUGGED,0);
                s.health=b.getIntExtra(BatteryManager.EXTRA_HEALTH,-1);
                s.temp=b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,Integer.MIN_VALUE);
                s.voltage=b.getIntExtra(BatteryManager.EXTRA_VOLTAGE,-1);
            }
            boolean discharging=s.plugged==0 && s.status==BatteryManager.BATTERY_STATUS_DISCHARGING;
            long prediction=-1;
            if(Build.VERSION.SDK_INT>=31 && discharging) {
                try {
                    Duration d=context.getSystemService(PowerManager.class).getBatteryDischargePrediction();
                    if(d!=null) prediction=d.toMillis();
                } catch(RuntimeException ignored) { }
            }
            s.remaining=estimator.estimate(SystemClock.elapsedRealtime(),s.level,discharging,prediction);
        } catch(RuntimeException e) { s.partial=true; }
        long now=SystemClock.elapsedRealtime(),rx=TrafficStats.getTotalRxBytes(),tx=TrafficStats.getTotalTxBytes();
        if(lastAt>=0 && now>lastAt && rx>=0 && tx>=0 && lastRx>=0 && lastTx>=0 && rx>=lastRx && tx>=lastTx) {
            s.rxRate=(rx-lastRx)*1000/(now-lastAt);s.txRate=(tx-lastTx)*1000/(now-lastAt);
        }
        if(rx<0||tx<0){s.rxRate=-2;s.txRate=-2;}
        lastAt=now;lastRx=rx;lastTx=tx;
        try {
            ConnectivityManager cm=context.getSystemService(ConnectivityManager.class);
            Network active=cm.getActiveNetwork();
            NetworkCapabilities c=cm.getNetworkCapabilities(active);
            s.transport="offline";s.vpn=false;
            if(c!=null) {
                s.transport=c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)?"wifi":
                    c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)?"mobile":
                    c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)?"ethernet":
                    c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)?"vpn":"other_network";
                s.validated=c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                s.metered=cm.isActiveNetworkMetered();
            }
            for(Network n:cm.getAllNetworks()) {
                NetworkCapabilities caps=cm.getNetworkCapabilities(n);
                if(caps!=null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) s.vpn=true;
            }
            if(Build.VERSION.SDK_INT>=28 && active!=null) {
                LinkProperties lp=cm.getLinkProperties(active);
                if(lp!=null) s.dns=lp.isPrivateDnsActive() ?
                    (lp.getPrivateDnsServerName()!=null ? "dns_strict":"dns_auto") : "dns_inactive";
            }
        } catch(RuntimeException e) { s.partial=true; }
        try {
            Enumeration<NetworkInterface> all=NetworkInterface.getNetworkInterfaces();
            if(all!=null) while(all.hasMoreElements()) {
                NetworkInterface n=all.nextElement();
                if(n.isUp()) {
                    StringBuilder row=new StringBuilder(n.getName());
                    for(InetAddress a:Collections.list(n.getInetAddresses())) row.append("\n").append(a.getHostAddress());
                    s.interfaces.add(row.toString());
                }
            }
        } catch(Exception ignored) { }
        try { s.locked=context.getSystemService(KeyguardManager.class).isDeviceSecure(); }
        catch(RuntimeException ignored) { }
        s.debugging=setting(Settings.Global.ADB_ENABLED);
        s.developer=setting(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED);
        s.scannedAt=System.currentTimeMillis();
        return s;
    }
    private Boolean setting(String key) {
        try { String v=Settings.Global.getString(context.getContentResolver(),key);return v==null?null:"1".equals(v); }
        catch(RuntimeException e) {return null;}
    }
}
