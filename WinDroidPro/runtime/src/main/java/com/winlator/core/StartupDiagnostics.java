package com.winlator.core;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.winlator.xenvironment.RootFS;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** A private, bounded report and watchdog for the first visible Wine window. */
public final class StartupDiagnostics {
    private static volatile StartupDiagnostics current;
    private static final long WARNING_DELAY_MS = 180000;
    private final Activity activity;
    private final PreloaderDialog preloader;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final StartupLog output = new StartupLog(64 * 1024);
    private final long started = SystemClock.elapsedRealtime();
    private final File report;
    private final String metadata;
    private final Callback<String> processOutput = this::append;
    private volatile String phase = "Preparing Windows desktop";
    private volatile boolean ready;
    private volatile boolean closed;
    private volatile boolean failed;
    private AlertDialog warning;

    private final Runnable heartbeat = new Runnable() {
        @Override public void run() {
            if (closed || ready) return;
            save();
            preloader.setMessageOnUiThread(phase + "\n" + elapsed() + " seconds elapsed");
            handler.postDelayed(this, 1000);
        }
    };

    private final Runnable watchdog = new Runnable() {
        @Override public void run() {
            if (closed || ready || failed) return;
            if (!activity.hasWindowFocus()) {
                handler.postDelayed(this, 1000);
                return;
            }
            preloader.close();
            append("No visible Windows window after " + elapsed() + " seconds; last phase: " + phase);
            save();
            warning = new AlertDialog.Builder(activity)
                .setTitle("Windows is taking longer to start")
                .setMessage("Last step: " + phase + ". First startup can be slow. You can keep waiting or view the saved startup report. Exiting keeps your container.")
                .setCancelable(false)
                .setPositiveButton("Keep waiting", (dialog, which) -> resumeWaiting())
                .setNeutralButton("View report", (dialog, which) -> showReport(activity, readLatest(activity), StartupDiagnostics.this::resumeWaiting))
                .setNegativeButton("Exit", (dialog, which) -> activity.finish())
                .show();
        }
    };

    public StartupDiagnostics(Activity activity, PreloaderDialog preloader) {
        this.activity = activity;
        this.preloader = preloader;
        this.report = reportFile(activity);
        StringBuilder info = new StringBuilder("WinDroid Pro startup report\n");
        info.append("Package: ").append(activity.getPackageName()).append('\n');
        info.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            .append("; Android ").append(Build.VERSION.RELEASE).append(" / API ").append(Build.VERSION.SDK_INT).append('\n');
        info.append("Container: ").append(activity.getIntent().getIntExtra("container_id", 0)).append('\n');
        try { info.append("Page size: ").append(Os.sysconf(OsConstants._SC_PAGESIZE)).append('\n'); }
        catch (Exception exception) { info.append("Page size unavailable\n"); }
        ActivityManager manager = (ActivityManager)activity.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
            manager.getMemoryInfo(memory);
            info.append("RAM total / available MB: ").append(memory.totalMem / 1048576).append(" / ")
                .append(memory.availMem / 1048576).append('\n');
        }
        info.append("Free storage MB: ").append(activity.getFilesDir().getUsableSpace() / 1048576).append('\n');
        File root = RootFS.find(activity).getRootDir();
        info.append("Runtime: ").append(root).append('\n');
        for (String name : new String[]{"opt/wine/bin/wine", "usr/lib/libc.so.6", "home/xuser/.wine/system.reg"}) {
            File file = new File(root, name);
            info.append(name).append(": exists=").append(file.exists()).append(", bytes=").append(file.length())
                .append(", executable=").append(file.canExecute()).append('\n');
        }
        metadata = info.toString();
        current = this;
        ProcessHelper.addDebugCallback(processOutput);
        phase(phase);
    }

    public void startWatchdog() {
        handler.post(heartbeat);
        handler.postDelayed(watchdog, WARNING_DELAY_MS);
    }

    private long elapsed() { return (SystemClock.elapsedRealtime() - started) / 1000; }

    public void append(String line) {
        if (!closed && !ready) output.append("[" + elapsed() + "s] " + line);
    }

    public void phase(String value) {
        if (closed || failed || ready) return;
        phase = value;
        append(value);
        save();
    }

    public static void phaseCurrent(String value) {
        StartupDiagnostics session = current;
        if (session != null) session.phase(value);
    }

    public static void logCurrent(String value) {
        StartupDiagnostics session = current;
        if (session != null) session.append(value);
    }

    public boolean isReady() { return ready; }

    public void desktopReady() {
        if (closed || failed || ready) return;
        append("Windows opened its first visible window");
        ready = true;
        save();
        handler.removeCallbacksAndMessages(null);
        ProcessHelper.removeDebugCallback(processOutput);
        activity.runOnUiThread(() -> {
            if (warning != null) warning.dismiss();
            preloader.close();
        });
    }

    public void fail(Throwable exception) {
        if (closed || ready || failed) return;
        failed = true;
        StringWriter trace = new StringWriter();
        exception.printStackTrace(new PrintWriter(trace));
        append("STARTUP FAILED at " + phase + "\n" + trace);
        save();
        handler.removeCallbacksAndMessages(null);
        activity.runOnUiThread(() -> {
            if (closed || activity.isFinishing() || activity.isDestroyed()) return;
            preloader.close();
            if (warning != null) warning.dismiss();
            warning = new AlertDialog.Builder(activity)
                .setTitle("Windows could not start")
                .setMessage(exception.getMessage() + "\n\nA startup report was saved. Your container is intact.")
                .setCancelable(false)
                .setPositiveButton("View report", (dialog, which) -> showReport(activity, readLatest(activity), activity::finish))
                .setNegativeButton("Exit", (dialog, which) -> activity.finish())
                .show();
        });
    }

    private void resumeWaiting() {
        if (closed || activity.isFinishing()) return;
        if (ready) return;
        if (failed) { activity.finish(); return; }
        preloader.show(com.winlator.R.string.starting_up);
        handler.postDelayed(watchdog, WARNING_DELAY_MS);
    }

    public synchronized void save() {
        try {
            Files.write(report.toPath(), (metadata + "Status: " + (ready ? "Desktop opened" : failed ? "Failed" : "Waiting")
                + "; last step: " + phase + "\n\n" + output.snapshot()).getBytes(StandardCharsets.UTF_8));
        }
        catch (Exception ignored) { /* Reporting must never prevent Wine from starting. */ }
    }

    public void close() {
        if (closed) return;
        append("Session closed");
        closed = true;
        save();
        handler.removeCallbacksAndMessages(null);
        ProcessHelper.removeDebugCallback(processOutput);
        if (current == this) current = null;
        if (warning != null) warning.dismiss();
    }

    private static File reportFile(Context context) { return new File(context.getFilesDir(), "latest-startup.txt"); }

    public static String readLatest(Context context) {
        StartupDiagnostics session = current;
        if (session != null) session.save();
        try { return new String(Files.readAllBytes(reportFile(context).toPath()), StandardCharsets.UTF_8); }
        catch (Exception exception) { return "No startup report yet. Open a Windows container first."; }
    }

    public static void showLatest(Activity activity) { showReport(activity, readLatest(activity), null); }

    private static void showReport(Activity activity, String text, Runnable onClose) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextSize(12);
        view.setTextIsSelectable(true);
        view.setPadding(24, 16, 24, 16);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(view);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Startup report").setView(scroll)
            .setPositiveButton("Close", null).setNeutralButton("Copy", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(button -> {
            ClipboardManager clipboard = (ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("WinDroid startup report", text));
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setText("Copied");
        }));
        if (onClose != null) dialog.setOnDismissListener(ignored -> onClose.run());
        dialog.show();
    }
}
