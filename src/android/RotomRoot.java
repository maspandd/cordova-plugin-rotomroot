package com.rotomroot.plugin;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;

public class RotomRoot extends CordovaPlugin {

    private static final String TAG = "RotomRoot";
    private static final long COMMAND_TIMEOUT_MS = 2000;

    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) {
        if ("isRooted".equals(action)) {
            cordova.getThreadPool().execute(() -> {
                try {
                    callbackContext.success(newChecker().isRooted() ? 1 : 0);
                } catch (Exception e) {
                    fail(callbackContext, e);
                }
            });
            return true;
        }
        if ("check".equals(action)) {
            cordova.getThreadPool().execute(() -> {
                try {
                    callbackContext.success(toJson(newChecker().check()));
                } catch (Exception e) {
                    fail(callbackContext, e);
                }
            });
            return true;
        }
        return false;
    }

    private RootChecker newChecker() {
        return new RootChecker(new AndroidEnv(cordova.getContext().getApplicationContext()));
    }

    private static JSONObject toJson(RootChecker.Report report) throws JSONException {
        JSONObject checks = new JSONObject();
        for (Map.Entry<String, Boolean> entry : report.checks().entrySet()) {
            checks.put(entry.getKey(), entry.getValue().booleanValue());
        }
        JSONObject result = new JSONObject();
        result.put("isRooted", report.isRooted());
        result.put("checks", checks);
        return result;
    }

    private static void fail(CallbackContext callbackContext, Exception e) {
        Log.e(TAG, "Root check failed", e);
        callbackContext.error(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
    }

    /** {@link RootChecker.Env} backed by the real device. */
    static class AndroidEnv implements RootChecker.Env {

        private final Context context;

        AndroidEnv(Context context) {
            this.context = context;
        }

        @Override
        public boolean fileExists(String path) {
            try {
                return new File(path).exists();
            } catch (SecurityException e) {
                return false;
            }
        }

        @Override
        public String readFile(String path) {
            try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
                return readAll(reader);
            } catch (IOException | SecurityException e) {
                return null;
            }
        }

        @Override
        public String run(String... command) {
            Process process = null;
            try {
                process = new ProcessBuilder(command).start();
                process.getOutputStream().close();
                // Process.waitFor(timeout) needs API 26; poll so minSdk 24 still works.
                long deadline = System.currentTimeMillis() + COMMAND_TIMEOUT_MS;
                Integer exitCode = null;
                while (exitCode == null) {
                    try {
                        exitCode = process.exitValue();
                    } catch (IllegalThreadStateException running) {
                        if (System.currentTimeMillis() > deadline) {
                            return null;
                        }
                        Thread.sleep(20);
                    }
                }
                if (exitCode != 0) {
                    return null;
                }
                return readAll(process.getInputStream());
            } catch (IOException | SecurityException e) {
                return null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } finally {
                if (process != null) {
                    process.destroy();
                }
            }
        }

        @Override
        public boolean isPackageInstalled(String packageName) {
            try {
                context.getPackageManager().getPackageInfo(packageName, 0);
                return true;
            } catch (PackageManager.NameNotFoundException e) {
                return false;
            }
        }

        @Override
        public String buildTags() {
            return Build.TAGS;
        }

        @Override
        public String buildType() {
            return Build.TYPE;
        }

        @Override
        public String pathEnv() {
            return System.getenv("PATH");
        }

        private static String readAll(InputStream in) throws IOException {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                return readAll(reader);
            }
        }

        private static String readAll(BufferedReader reader) throws IOException {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }
}
