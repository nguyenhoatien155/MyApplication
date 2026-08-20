package com.nguyenhoatien.icloudsync;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

public class CrashLog {

    private static final String FILE = "last-crash.txt";

    private CrashLog() {
    }

    // Installed from every entry point, because a crash in onCreate happens
    // before any UI exists to show it and there is no adb to read logcat.
    public static void install(final Context context) {
        final File target = new File(context.getFilesDir(), FILE);
        final Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();
        if (previous instanceof Handler) {
            return;
        }
        Thread.setDefaultUncaughtExceptionHandler(new Handler(target, previous));
    }

    public static String read(Context context) {
        File f = new File(context.getFilesDir(), FILE);
        if (!f.exists()) {
            return null;
        }
        FileInputStream in = null;
        try {
            in = new FileInputStream(f);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toString("UTF-8");
        } catch (Exception e) {
            return "could not read crash file: " + e;
        } finally {
            close(in);
        }
    }

    public static void clear(Context context) {
        new File(context.getFilesDir(), FILE).delete();
    }

    private static class Handler implements Thread.UncaughtExceptionHandler {

        private final File target;
        private final Thread.UncaughtExceptionHandler previous;

        Handler(File target, Thread.UncaughtExceptionHandler previous) {
            this.target = target;
            this.previous = previous;
        }

        @Override
        public void uncaughtException(Thread thread, Throwable e) {
            FileOutputStream out = null;
            try {
                StringWriter sw = new StringWriter();
                sw.write("thread " + thread.getName() + "\n");
                e.printStackTrace(new PrintWriter(sw));
                sw.write("\n--- log before crash ---\n");
                sw.write(SyncLog.dump());
                out = new FileOutputStream(target);
                out.write(sw.toString().getBytes("UTF-8"));
                out.flush();
            } catch (Throwable ignored) {
                // Never mask the original crash with a logging failure.
            } finally {
                close(out);
            }
            if (previous != null) {
                previous.uncaughtException(thread, e);
            }
        }
    }

    private static void close(java.io.Closeable c) {
        if (c == null) {
            return;
        }
        try {
            c.close();
        } catch (Exception ignored) {
            // Nothing useful to do if closing the crash file fails.
        }
    }
}
