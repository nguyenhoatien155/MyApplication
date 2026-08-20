package com.nguyenhoatien.icloudsync;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

public class SyncLog {

    private static final int MAX_LINES = 300;
    private static final List<String> lines = new ArrayList<String>();

    private SyncLog() {
    }

    public static synchronized void add(String message) {
        lines.add(stamp() + " " + message);
        while (lines.size() > MAX_LINES) {
            lines.remove(0);
        }
    }

    public static synchronized void add(String message, Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        add(message + "\n" + sw.toString().trim());
    }

    public static synchronized String dump() {
        if (lines.isEmpty()) {
            return "(chưa có log)";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            sb.append(lines.get(i)).append('\n');
        }
        return sb.toString();
    }

    public static synchronized void clear() {
        lines.clear();
    }

    private static String stamp() {
        long ms = System.currentTimeMillis();
        long sec = ms / 1000;
        return String.format("%02d:%02d:%02d",
                (sec / 3600) % 24, (sec / 60) % 60, sec % 60);
    }
}
