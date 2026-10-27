package com.gathin.tvhome;

import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * CPU busy percent for the stats line, kept out of MainActivity.
 *
 * <p>Prefers /proc/stat; Android 9 hides it from apps, so it falls back to summing cpuidle time across
 * the cores. {@link #reset()} is called when sampling restarts, so the first sample only seeds the
 * counters and returns -1.
 */
final class CpuLoad {
    /** This source cannot be read (Android 9 hides /proc/stat from apps). */
    private static final int UNREADABLE = -2;

    private long statTotalPrev = -1, statIdlePrev = -1;
    private boolean procStatDead;
    private long cpuidlePrev = -1, wallPrev = -1;

    void reset() {
        statTotalPrev = -1;
        statIdlePrev = -1;
        procStatDead = false;
        cpuidlePrev = -1;
        wallPrev = -1;
    }

    /** Busy percent, or -1 when another sample is needed. */
    int percent() {
        if (!procStatDead) {
            int fromStat = fromProcStat();
            if (fromStat != UNREADABLE) return fromStat;
            procStatDead = true;
            cpuidlePrev = -1;
        }
        return fromCpuidle();
    }

    private int fromProcStat() {
        String line = readFirstLine("/proc/stat");
        if (line == null || !line.startsWith("cpu")) return UNREADABLE;
        String[] fields = line.trim().split("\\s+");
        if (fields.length < 5) return UNREADABLE;
        try {
            long user = Long.parseLong(fields[1]), nice = Long.parseLong(fields[2]);
            long sys = Long.parseLong(fields[3]), idle = Long.parseLong(fields[4]);
            long iowait = fields.length > 5 ? Long.parseLong(fields[5]) : 0;
            long irq = fields.length > 6 ? Long.parseLong(fields[6]) : 0;
            long soft = fields.length > 7 ? Long.parseLong(fields[7]) : 0;
            long total = user + nice + sys + idle + iowait + irq + soft;
            long idleAll = idle + iowait;
            if (statTotalPrev < 0) {
                statTotalPrev = total;
                statIdlePrev = idleAll;
                return -1;
            }
            long totalDelta = total - statTotalPrev, idleDelta = idleAll - statIdlePrev;
            statTotalPrev = total;
            statIdlePrev = idleAll;
            if (totalDelta <= 0) return -1;
            int busy = (int) Math.round((totalDelta - idleDelta) * 100.0 / totalDelta);
            return Math.max(0, Math.min(100, busy));
        } catch (NumberFormatException e) {
            return UNREADABLE;
        }
    }

    /** Busy percent from cpuidle time (microseconds) across the cores. -1 until the second sample. */
    private int fromCpuidle() {
        long idle = 0;
        int cores = 0;
        for (int core = 0; core < 8; core++) {
            boolean any = false;
            for (int state = 0; state < 8; state++) {
                String time = readFirstLine("/sys/devices/system/cpu/cpu" + core
                        + "/cpuidle/state" + state + "/time");
                if (time == null) break;
                try {
                    idle += Long.parseLong(time.trim());
                    any = true;
                } catch (NumberFormatException e) {
                    break;
                }
            }
            if (!any) break;
            cores++;
        }
        if (cores == 0) return -1;
        long wall = SystemClock.elapsedRealtime() * 1000L;
        if (cpuidlePrev < 0) {
            cpuidlePrev = idle;
            wallPrev = wall;
            return -1;
        }
        long idleDelta = idle - cpuidlePrev;
        long wallDelta = (wall - wallPrev) * cores;
        cpuidlePrev = idle;
        wallPrev = wall;
        if (wallDelta <= 0) return -1;
        int busy = (int) Math.round((wallDelta - idleDelta) * 100.0 / wallDelta);
        return Math.max(0, Math.min(100, busy));
    }

    private static String readFirstLine(String path) {
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            return reader.readLine();
        } catch (IOException e) {
            return null;
        }
    }
}
