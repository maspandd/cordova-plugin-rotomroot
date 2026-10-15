package com.rotomroot.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Root detection heuristics. Pure Java: every interaction with the device goes through
 * {@link Env}, so the logic can be unit-tested on a plain JVM.
 */
public class RootChecker {

    /** Device access used by the checks. */
    public interface Env {
        boolean fileExists(String path);

        /** Returns the file's content, or null when it cannot be read. */
        String readFile(String path);

        /** Runs a command and returns its stdout, or null on failure or timeout. */
        String run(String... command);

        boolean isPackageInstalled(String packageName);

        /** {@code android.os.Build.TAGS}, may be null. */
        String buildTags();

        /** {@code android.os.Build.TYPE} ("user", "userdebug", "eng"), may be null. */
        String buildType();

        /** Value of the PATH environment variable, may be null. */
        String pathEnv();
    }

    public static final String SU_BINARY = "su";
    public static final String BUSYBOX_BINARY = "busybox";

    static final String[] BINARY_PATHS = {
            "/data/local/",
            "/data/local/bin/",
            "/data/local/xbin/",
            "/sbin/",
            "/su/bin/",
            "/system/bin/",
            "/system/bin/.ext/",
            "/system/bin/failsafe/",
            "/system/sd/xbin/",
            "/system/usr/we-need-root/",
            "/system/xbin/",
            "/system_ext/bin/",
            "/cache/",
            "/data/",
            "/dev/",
            "/odm/bin/",
            "/product/bin/",
            "/vendor/bin/",
            "/vendor/xbin/",
    };

    /** Keep in sync with the {@code <queries>} block in plugin.xml. */
    static final String[] ROOT_PACKAGES = {
            "com.topjohnwu.magisk",
            "io.github.huskydg.magisk",
            "io.github.vvb2060.magisk",
            "me.weishu.kernelsu",
            "me.bmax.apatch",
            "eu.chainfire.supersu",
            "com.noshufou.android.su",
            "com.noshufou.android.su.elite",
            "com.koushikdutta.superuser",
            "com.thirdparty.superuser",
            "com.yellowes.su",
            "com.kingroot.kinguser",
            "com.kingo.root",
            "com.smedialink.oneclickroot",
            "com.zhiqupk.root.global",
            "com.alephzain.framaroot",
    };

    static final String[] MAGISK_PATHS = {
            "/sbin/.magisk",
            "/sbin/.core/mirror",
            "/sbin/.core/img",
            "/cache/.disable_magisk",
            "/cache/magisk.log",
            "/dev/.magisk.unblock",
            // Magisk 26+ on devices without /sbin keeps its tmpfs at /debug_ramdisk. The
            // directory itself exists on stock GKI devices, so only Magisk's entries count.
            "/debug_ramdisk/magisk",
            "/debug_ramdisk/.magisk",
            "/debug_ramdisk/magiskpolicy",
            "/data/adb/magisk",
            "/data/adb/magisk.db",
            "/data/adb/modules",
            "/data/adb/ksu",
            "/data/adb/ap",
    };

    /** Mount sources used by root solutions for their tmpfs / module mounts. */
    static final String[] ROOT_MOUNT_SOURCES = {
            "magisk",
            "KSU",
            "APatch",
    };

    /** Mount points that are read-only on a stock device. */
    static final String[] READ_ONLY_MOUNTS = {
            "/system",
            "/system/bin",
            "/system/sbin",
            "/system/xbin",
            "/system_ext",
            "/vendor",
            "/vendor/bin",
            "/product",
    };

    private final Env env;

    public RootChecker(Env env) {
        this.env = env;
    }

    public Report check() {
        Report report = new Report();
        report.suBinary = hasSuBinary();
        report.suExec = canResolveSu();
        report.rootApps = hasRootApps();
        report.rwSystem = hasRwSystem();
        report.magisk = hasMagiskTraces();
        report.testKeys = hasTestKeys();
        report.dangerousProps = hasDangerousProps();
        report.busybox = hasBusybox();
        return report;
    }

    public boolean isRooted() {
        return check().isRooted();
    }

    boolean hasSuBinary() {
        return findBinary(SU_BINARY);
    }

    boolean hasBusybox() {
        return findBinary(BUSYBOX_BINARY);
    }

    boolean canResolveSu() {
        String out = env.run("which", SU_BINARY);
        return out != null && !out.trim().isEmpty();
    }

    boolean hasRootApps() {
        for (String pkg : ROOT_PACKAGES) {
            if (env.isPackageInstalled(pkg)) {
                return true;
            }
        }
        return false;
    }

    boolean hasMagiskTraces() {
        for (String path : MAGISK_PATHS) {
            if (env.fileExists(path)) {
                return true;
            }
        }
        return hasRootMounts();
    }

    private boolean hasRootMounts() {
        for (String[] fields : mounts()) {
            if (fields[1].contains("/.magisk")) {
                return true;
            }
            for (String source : ROOT_MOUNT_SOURCES) {
                if (source.equals(fields[0])) {
                    return true;
                }
            }
        }
        return false;
    }

    boolean hasTestKeys() {
        String tags = env.buildTags();
        return tags != null && tags.contains("test-keys");
    }

    boolean hasDangerousProps() {
        // Newer Android versions hide ro.debuggable / ro.secure from apps via SELinux, so
        // Build.TYPE (always readable) is checked as well.
        String type = env.buildType();
        return "userdebug".equals(type) || "eng".equals(type)
                || "1".equals(prop("ro.debuggable")) || "0".equals(prop("ro.secure"));
    }

    boolean hasRwSystem() {
        for (String[] fields : mounts()) {
            String mountPoint = fields[1];
            String fsType = fields[2];
            if (!isReadOnlyMount(mountPoint, fsType)) {
                continue;
            }
            for (String option : fields[3].split(",")) {
                if ("rw".equals(option)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Lines of /proc/self/mounts split into {@code <device> <mount point> <fs type> <options> ...}. */
    private List<String[]> mounts() {
        List<String[]> result = new ArrayList<>();
        String mounts = env.readFile("/proc/self/mounts");
        if (mounts == null) {
            return result;
        }
        for (String line : mounts.split("\n")) {
            String[] fields = line.trim().split("\\s+");
            if (fields.length >= 4) {
                result.add(fields);
            }
        }
        return result;
    }

    private static boolean isReadOnlyMount(String mountPoint, String fsType) {
        if ("/".equals(mountPoint)) {
            // Pre system-as-root devices mount a writable rootfs/tmpfs at "/"; only a real
            // system partition at "/" is expected to be read-only.
            return !"rootfs".equals(fsType) && !"tmpfs".equals(fsType);
        }
        for (String path : READ_ONLY_MOUNTS) {
            if (path.equals(mountPoint)) {
                return true;
            }
        }
        return false;
    }

    private boolean findBinary(String name) {
        for (String dir : searchDirs()) {
            if (env.fileExists(dir + name)) {
                return true;
            }
        }
        return false;
    }

    private List<String> searchDirs() {
        Set<String> dirs = new LinkedHashSet<>();
        Collections.addAll(dirs, BINARY_PATHS);
        String path = env.pathEnv();
        if (path != null) {
            for (String dir : path.split(":")) {
                if (!dir.isEmpty()) {
                    dirs.add(dir.endsWith("/") ? dir : dir + "/");
                }
            }
        }
        return new ArrayList<>(dirs);
    }

    private String prop(String name) {
        String out = env.run("getprop", name);
        return out == null ? null : out.trim();
    }

    /** Result of every check. Strong signals decide {@link #isRooted()}. */
    public static class Report {
        public boolean suBinary;
        public boolean suExec;
        public boolean rootApps;
        public boolean rwSystem;
        public boolean magisk;
        public boolean testKeys;
        public boolean dangerousProps;
        public boolean busybox;

        public boolean isRooted() {
            return suBinary || suExec || rootApps || rwSystem || magisk;
        }

        public Map<String, Boolean> checks() {
            Map<String, Boolean> map = new LinkedHashMap<>();
            map.put("suBinary", suBinary);
            map.put("suExec", suExec);
            map.put("rootApps", rootApps);
            map.put("rwSystem", rwSystem);
            map.put("magisk", magisk);
            map.put("testKeys", testKeys);
            map.put("dangerousProps", dangerousProps);
            map.put("busybox", busybox);
            return map;
        }
    }
}
