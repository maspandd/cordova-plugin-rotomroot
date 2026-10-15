export interface RootChecks {
    /** An `su` binary exists in a well-known path. Strong signal. */
    suBinary: boolean;
    /** `which su` resolved to a binary. Strong signal. */
    suExec: boolean;
    /** A known root-management app (Magisk, KernelSU, SuperSU, ...) is installed. Strong signal. */
    rootApps: boolean;
    /** `/system` (or `/` on system-as-root) is mounted read-write. Strong signal. */
    rwSystem: boolean;
    /** Magisk / KernelSU / APatch files or mounts are visible. Strong signal. */
    magisk: boolean;
    /** `Build.TAGS` contains `test-keys`. Weak signal. */
    testKeys: boolean;
    /** `userdebug`/`eng` build, `ro.debuggable=1` or `ro.secure=0`. Weak signal. */
    dangerousProps: boolean;
    /** A `busybox` binary is present. Weak signal. */
    busybox: boolean;
}

export interface RootReport {
    /** True when at least one strong signal is set. */
    isRooted: boolean;
    checks: RootChecks;
}

export interface RotomRootPlugin {
    isRooted(): Promise<boolean>;
    check(): Promise<RootReport>;
}

declare global {
    interface Window {
        RotomRoot: RotomRootPlugin;
    }
    const RotomRoot: RotomRootPlugin;
}

declare const RotomRoot: RotomRootPlugin;
export default RotomRoot;
