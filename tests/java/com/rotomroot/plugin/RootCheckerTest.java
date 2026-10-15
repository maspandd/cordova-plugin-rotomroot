package com.rotomroot.plugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Dependency-free tests for {@link RootChecker}. Run with {@code npm run test:java}.
 */
public class RootCheckerTest {

    private static int failures = 0;

    public static void main(String[] args) {
        cleanDeviceIsNotRooted();
        suBinaryInStandardPathIsStrong();
        suBinaryOnPathEnvIsDetected();
        whichSuIsStrong();
        rootAppIsStrong();
        magiskTraceIsStrong();
        modernMagiskPathIsDetected();
        magiskMountIsDetected();
        kernelSuMountIsDetected();
        stockDebugRamdiskIsClean();
        rwSystemMountIsStrong();
        roSystemMountIsClean();
        writableRootfsAtSlashIsIgnored();
        writableSystemAsRootIsDetected();
        weakSignalsDoNotMarkRooted();
        userdebugBuildIsDangerous();
        nullOutputsAreSafe();

        if (failures > 0) {
            System.err.println(failures + " test(s) failed");
            System.exit(1);
        }
        System.out.println("All RootChecker tests passed");
    }

    static final String STOCK_MOUNTS =
            "/dev/block/dm-0 / ext4 ro,seclabel,relatime 0 0\n"
                    + "tmpfs /dev tmpfs rw,seclabel,nosuid 0 0\n"
                    + "/dev/block/dm-1 /vendor ext4 ro,seclabel,relatime 0 0\n"
                    + "/dev/block/dm-5 /data f2fs rw,lazytime,seclabel 0 0\n";

    static void cleanDeviceIsNotRooted() {
        FakeEnv env = new FakeEnv();
        RootChecker.Report r = new RootChecker(env).check();
        check("clean device: not rooted", !r.isRooted());
        for (Map.Entry<String, Boolean> e : r.checks().entrySet()) {
            check("clean device: " + e.getKey() + " false", !e.getValue());
        }
    }

    static void suBinaryInStandardPathIsStrong() {
        FakeEnv env = new FakeEnv();
        env.files.add("/system/xbin/su");
        RootChecker.Report r = new RootChecker(env).check();
        check("su in /system/xbin: suBinary", r.suBinary);
        check("su in /system/xbin: rooted", r.isRooted());
    }

    static void suBinaryOnPathEnvIsDetected() {
        FakeEnv env = new FakeEnv();
        env.path = "/custom/bin:/other";
        env.files.add("/custom/bin/su");
        check("su on PATH: suBinary", new RootChecker(env).check().suBinary);
    }

    static void whichSuIsStrong() {
        FakeEnv env = new FakeEnv();
        env.commands.put("which su", "/debug_ramdisk/su\n");
        RootChecker.Report r = new RootChecker(env).check();
        check("which su: suExec", r.suExec);
        check("which su: rooted", r.isRooted());
    }

    static void rootAppIsStrong() {
        FakeEnv env = new FakeEnv();
        env.packages.add("com.topjohnwu.magisk");
        RootChecker.Report r = new RootChecker(env).check();
        check("magisk app: rootApps", r.rootApps);
        check("magisk app: rooted", r.isRooted());
    }

    static void magiskTraceIsStrong() {
        FakeEnv env = new FakeEnv();
        env.files.add("/sbin/.magisk");
        RootChecker.Report r = new RootChecker(env).check();
        check("magisk path: magisk", r.magisk);
        check("magisk path: rooted", r.isRooted());
    }

    static void modernMagiskPathIsDetected() {
        FakeEnv env = new FakeEnv();
        env.files.add("/debug_ramdisk/magisk");
        check("/debug_ramdisk/magisk: magisk", new RootChecker(env).check().magisk);
    }

    static void magiskMountIsDetected() {
        FakeEnv env = new FakeEnv();
        env.mounts = STOCK_MOUNTS
                + "magisk /debug_ramdisk tmpfs rw,seclabel,relatime,mode=755 0 0\n"
                + "worker /debug_ramdisk/.magisk/worker tmpfs ro,seclabel,relatime 0 0\n";
        RootChecker.Report r = new RootChecker(env).check();
        check("magisk mount: magisk", r.magisk);
        check("magisk mount: rooted", r.isRooted());
    }

    static void kernelSuMountIsDetected() {
        FakeEnv env = new FakeEnv();
        env.mounts = STOCK_MOUNTS + "KSU /system/bin overlay ro,seclabel 0 0\n";
        check("KSU mount: magisk", new RootChecker(env).check().magisk);
    }

    static void stockDebugRamdiskIsClean() {
        FakeEnv env = new FakeEnv();
        env.files.add("/debug_ramdisk");
        env.mounts = STOCK_MOUNTS + "tmpfs /debug_ramdisk tmpfs rw,seclabel 0 0\n";
        check("stock /debug_ramdisk: not magisk", !new RootChecker(env).check().magisk);
    }

    static void rwSystemMountIsStrong() {
        FakeEnv env = new FakeEnv();
        env.mounts = "/dev/block/sda1 /system ext4 rw,seclabel,relatime 0 0\n";
        RootChecker.Report r = new RootChecker(env).check();
        check("rw /system: rwSystem", r.rwSystem);
        check("rw /system: rooted", r.isRooted());
    }

    static void roSystemMountIsClean() {
        FakeEnv env = new FakeEnv();
        env.mounts = STOCK_MOUNTS;
        check("stock mounts: rwSystem false", !new RootChecker(env).check().rwSystem);
    }

    static void writableRootfsAtSlashIsIgnored() {
        FakeEnv env = new FakeEnv();
        env.mounts = "rootfs / rootfs rw,seclabel 0 0\n"
                + "/dev/block/sda1 /system ext4 ro,seclabel 0 0\n";
        check("rw rootfs at /: ignored", !new RootChecker(env).check().rwSystem);
    }

    static void writableSystemAsRootIsDetected() {
        FakeEnv env = new FakeEnv();
        env.mounts = "/dev/block/dm-0 / ext4 rw,seclabel,relatime 0 0\n";
        check("rw ext4 at /: rwSystem", new RootChecker(env).check().rwSystem);
    }

    static void weakSignalsDoNotMarkRooted() {
        FakeEnv env = new FakeEnv();
        env.tags = "dev-keys,test-keys";
        env.commands.put("getprop ro.debuggable", "1\n");
        env.commands.put("getprop ro.secure", "0\n");
        env.files.add("/system/xbin/busybox");
        RootChecker.Report r = new RootChecker(env).check();
        check("weak: testKeys", r.testKeys);
        check("weak: dangerousProps", r.dangerousProps);
        check("weak: busybox", r.busybox);
        check("weak only: not rooted", !r.isRooted());
    }

    static void userdebugBuildIsDangerous() {
        FakeEnv env = new FakeEnv();
        env.type = "userdebug";
        RootChecker.Report r = new RootChecker(env).check();
        check("userdebug: dangerousProps", r.dangerousProps);
        check("userdebug only: not rooted", !r.isRooted());
    }

    static void nullOutputsAreSafe() {
        FakeEnv env = new FakeEnv();
        env.tags = null;
        env.type = null;
        env.path = null;
        env.mounts = null;
        check("nulls: not rooted", !new RootChecker(env).check().isRooted());
    }

    static void check(String name, boolean condition) {
        if (!condition) {
            failures++;
            System.err.println("FAIL: " + name);
        }
    }

    static class FakeEnv implements RootChecker.Env {
        final Set<String> files = new HashSet<>();
        final Set<String> packages = new HashSet<>();
        final Map<String, String> commands = new HashMap<>();
        String mounts = STOCK_MOUNTS;
        String tags = "release-keys";
        String type = "user";
        String path = "/product/bin:/apex/com.android.runtime/bin:/system/bin:/system/xbin";

        @Override
        public boolean fileExists(String p) {
            return files.contains(p);
        }

        @Override
        public String readFile(String p) {
            return "/proc/self/mounts".equals(p) ? mounts : null;
        }

        @Override
        public String run(String... command) {
            return commands.get(String.join(" ", command));
        }

        @Override
        public boolean isPackageInstalled(String packageName) {
            return packages.contains(packageName);
        }

        @Override
        public String buildTags() {
            return tags;
        }

        @Override
        public String buildType() {
            return type;
        }

        @Override
        public String pathEnv() {
            return path;
        }
    }
}
