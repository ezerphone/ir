package com.offlineremote.app.util;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Lightweight file-backed log. Not shown on the working screens — it only records
 * whether each capture/send succeeded, so there is one quiet place to check when
 * something misbehaves. Capped to the most recent lines.
 */
public final class ErrorLog {

    private static final String FILE = "errorlog.txt";
    private static final int MAX_CHARS = 40000;

    private ErrorLog() {}

    public static synchronized void add(Context ctx, String line) {
        try {
            String stamp = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date());
            String existing = read(ctx);
            String out = existing + stamp + "  " + line + "\n";
            if (out.length() > MAX_CHARS) out = out.substring(out.length() - MAX_CHARS);
            File f = new File(ctx.getFilesDir(), FILE);
            try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8")) {
                w.write(out);
            }
        } catch (Throwable ignore) {}
    }

    public static synchronized String read(Context ctx) {
        try {
            File f = new File(ctx.getFilesDir(), FILE);
            if (!f.exists()) return "";
            byte[] buf = new byte[(int) f.length()];
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                int off = 0, n;
                while (off < buf.length && (n = in.read(buf, off, buf.length - off)) > 0) off += n;
            }
            return new String(buf, "UTF-8");
        } catch (Throwable t) {
            return "";
        }
    }

    public static synchronized void clear(Context ctx) {
        try { new File(ctx.getFilesDir(), FILE).delete(); } catch (Throwable ignore) {}
    }
}
