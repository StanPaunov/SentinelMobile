package com.stanpaunov.sentinelmobile;

import java.util.ArrayDeque;

/** Session-only samples measured with elapsedRealtime, never wall-clock time. */
public final class BatteryEstimator {
    private static final class Sample {
        final long at; final int level;
        Sample(long at, int level) { this.at=at; this.level=level; }
    }
    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    private long deadline = -1;
    private int previous = -1;
    public void reset() { samples.clear(); deadline=-1; previous=-1; }
    public long estimate(long now, int level, boolean discharging, long systemMillis) {
        if (!discharging || level < 0 || level > 100) { reset(); return -1; }
        if (previous >= 0 && level > previous) reset();
        previous = level;
        if (systemMillis > 0) return stabilize(now, systemMillis);
        if (samples.isEmpty()) samples.add(new Sample(now, level));
        Sample last = samples.peekLast();
        if (now - last.at > 5 * 60_000L) {
            reset(); previous=level; samples.add(new Sample(now, level)); return -1;
        }
        if (now != last.at) samples.add(new Sample(now,level));
        while(samples.size()>1 && now-samples.peekFirst().at>60*60_000L) samples.removeFirst();
        Sample first=samples.peekFirst();
        long elapsed=now-first.at;
        int drop=first.level-level;
        // Three percentage points reduce rounding error and allow a stability check.
        if (elapsed<60_000 || drop<3) return -1;
        long firstDropAt=-1, previousDropAt=-1;
        double min=Double.MAX_VALUE,max=0;
        int intervals=0, previousLevel=first.level;
        for(Sample s:samples) {
            if(s.level<previousLevel) {
                if(previousDropAt>=0) {
                    double rate=(previousLevel-s.level)*60_000.0/Math.max(1,s.at-previousDropAt);
                    min=Math.min(min,rate); max=Math.max(max,rate); intervals++;
                }
                if(firstDropAt<0) firstDropAt=s.at;
                previousDropAt=s.at; previousLevel=s.level;
            }
        }
        if(intervals<2 || max>min*2.5 || drop*60_000.0/elapsed>5
                || now-previousDropAt>Math.max(180_000, (elapsed/drop)*3)) {
            deadline=-1; return -1;
        }
        return stabilize(now,Math.round(level*elapsed/(double)drop));
    }
    private long stabilize(long now,long duration) {
        long candidate=now+duration;
        if(deadline<0 || candidate<deadline) deadline=candidate;
        return deadline>now ? deadline-now : -1;
    }
}
