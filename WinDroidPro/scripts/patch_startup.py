"""Checked startup patches for the pinned runtime; never edit the submodule."""


def patch_startup(java, replace):
    display = java / "com/winlator/XServerDisplayActivity.java"
    replace(display, "    private DebugDialog debugDialog;", """    private DebugDialog debugDialog;
    private com.winlator.core.StartupDiagnostics startupDiagnostics;
    private volatile boolean startupCancelled;
    private final java.util.concurrent.ExecutorService startupExecutor = Executors.newSingleThreadExecutor();""")
    replace(display, "        ProcessHelper.removeAllDebugCallbacks();",
            "        ProcessHelper.removeAllDebugCallbacks();\n        startupDiagnostics = new com.winlator.core.StartupDiagnostics(this, preloaderDialog);")
    replace(display, "        preloaderDialog.show(R.string.starting_up);",
            "        preloaderDialog.show(R.string.starting_up);\n        startupDiagnostics.startWatchdog();")
    replace(display, "            public void onMapWindow(Window window) {",
            '            public void onMapWindow(Window window) {\n                startupDiagnostics.append("X11 window mapped: " + window.id + "; class=" + window.getClassName());')
    replace(display, "                    preloaderDialog.closeOnUiThread();",
            "                    startupDiagnostics.desktopReady();")
    replace(display, "        Executors.newSingleThreadExecutor().execute(() -> {", "        startupExecutor.execute(() -> {")
    replace(display, """            if (!isGenerateWineprefix()) {
                setupWineSystemFiles();
                extractGraphicsDriverFiles();
                changeWineAudioDriver();
            }
            setupXEnvironment();""", """            try {
                if (startupCancelled) return;
                if (!isGenerateWineprefix()) {
                    startupDiagnostics.phase("Preparing Windows files");
                    setupWineSystemFiles();
                    if (startupCancelled) return;
                    startupDiagnostics.phase("Installing graphics drivers");
                    startupDiagnostics.append("Graphics: " + graphicsDriver[0] + ", " + graphicsDriver[1] + "; wrapper=" + dxwrapper);
                    extractGraphicsDriverFiles();
                    if (startupCancelled) return;
                    startupDiagnostics.phase("Setting up audio");
                    changeWineAudioDriver();
                }
                if (startupCancelled) return;
                startupDiagnostics.phase("Preparing runtime services");
                setupXEnvironment();
                startupDiagnostics.phase("Waiting for the Windows desktop");
            }
            catch (Exception | LinkageError exception) {
                startupDiagnostics.fail(exception);
            }""")
    replace(display, """    protected void onDestroy() {
        winHandler.stop();
        if (environment != null) environment.stopEnvironmentComponents();""", """    protected void onDestroy() {
        startupCancelled = true;
        if (startupDiagnostics != null) startupDiagnostics.close();
        winHandler.stop();
        // Finish in-flight setup before cleanup, without blocking the UI.
        startupExecutor.execute(() -> {
            winHandler.stop();
            if (environment != null) environment.stopEnvironmentComponents();
        });
        startupExecutor.shutdown();""")
    replace(display, "        winHandler.start();", "        if (startupCancelled) return;\n        winHandler.start();")
    replace(display, ' : "-all");', ' : "+err,+warn");')
    replace(display, '        guestProgramLauncherComponent.setTerminationCallback((status) -> exit());', """        guestProgramLauncherComponent.setTerminationCallback((status) -> runOnUiThread(() -> {
            if (!startupDiagnostics.isReady() && !isGenerateWineprefix()) {
                startupDiagnostics.fail(new IllegalStateException("Windows exited before opening a window (status " + status + ")."));
            }
            else exit();
        }));""")

    environment = java / "com/winlator/xenvironment/XEnvironment.java"
    replace(environment, "        for (EnvironmentComponent environmentComponent : this) environmentComponent.start();", """        for (EnvironmentComponent environmentComponent : this) {
            if (context instanceof android.app.Activity) {
                android.app.Activity activity = (android.app.Activity)context;
                if (activity.isFinishing() || activity.isDestroyed()) return;
            }
            com.winlator.core.StartupDiagnostics.phaseCurrent("Starting " + environmentComponent.getClass().getSimpleName());
            environmentComponent.start();
        }""")

    launcher = java / "com/winlator/xenvironment/components/GuestProgramLauncherComponent.java"
    replace(launcher, '        envVars.put("BOX64_NOBANNER", box64Logs >= 1 ? "0" : "1");',
            '        envVars.put("BOX64_NOBANNER", "0");\n        envVars.put("BOX64_LOG", "1");')
    replace(launcher, "        String command = rootDir+\"/usr/local/bin/box64 \"+guestExecutable;", """        File box64 = new File(rootDir, "usr/local/bin/box64");
        if (!box64.isFile() || !box64.canExecute()) {
            throw new IllegalStateException("Box64 is missing or not executable. Reinstall its component from Runtime settings.");
        }
        String command = rootDir+"/usr/local/bin/box64 "+guestExecutable;
        com.winlator.core.StartupDiagnostics.logCurrent("Launching: " + command);""")

    process = java / "com/winlator/core/ProcessHelper.java"
    replace(process, "        int pid = -1;\n        try {", "        int pid = -1;\n        java.lang.Process process = null;\n        try {")
    replace(process, "            for (String name : envVars) environment.put(name, envVars.get(name));",
            "            if (envVars != null) for (String name : envVars) environment.put(name, envVars.get(name));")
    replace(process, "            java.lang.Process process = processBuilder.start();", "            process = processBuilder.start();")
    replace(process, "            pidField.setAccessible(false);", "            pidField.setAccessible(false);\n            StartupDiagnostics.logCurrent(\"Process started: pid=\" + pid);")
    replace(process, "        catch (Exception e) {}\n        return pid;", """        catch (Exception e) {
            if (process != null) process.destroy();
            throw new IllegalStateException("Could not start Windows process: " + e, e);
        }
        return pid;""")

    preloader = java / "com/winlator/core/PreloaderDialog.java"
    replace(preloader, "    public boolean isShowing() {", """    public void setMessageOnUiThread(String message) {
        activity.runOnUiThread(() -> {
            if (dialog != null && dialog.isShowing()) {
                ((TextView)dialog.findViewById(R.id.TextView)).setText(message);
            }
        });
    }

    public boolean isShowing() {""")
