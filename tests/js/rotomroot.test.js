const mockExec = jest.fn();
jest.mock('cordova/exec', () => mockExec, { virtual: true });

const RotomRoot = require('../../www/rotomroot');

function respondWith(fn) {
    mockExec.mockImplementationOnce((success, error, service, action, args) => fn(success, error));
}

beforeEach(() => mockExec.mockReset());

describe('isRooted', () => {
    test('calls the native isRooted action', async () => {
        respondWith((success) => success(0));
        await RotomRoot.isRooted();
        expect(mockExec).toHaveBeenCalledWith(
            expect.any(Function), expect.any(Function), 'RotomRoot', 'isRooted', []);
    });

    test.each([[1, true], [true, true], ['true', true], [0, false], [false, false]])(
        'native %p resolves to %p', async (native, expected) => {
            respondWith((success) => success(native));
            await expect(RotomRoot.isRooted()).resolves.toBe(expected);
        });

    test('rejects with an Error when native fails', async () => {
        respondWith((success, error) => error('boom'));
        await expect(RotomRoot.isRooted()).rejects.toThrow('boom');
    });
});

describe('check', () => {
    test('resolves with the native report unchanged', async () => {
        const report = {
            isRooted: true,
            checks: {
                suBinary: true, suExec: false, rootApps: false, rwSystem: false,
                magisk: false, testKeys: true, dangerousProps: false, busybox: false
            }
        };
        respondWith((success) => success(report));
        await expect(RotomRoot.check()).resolves.toEqual(report);
        expect(mockExec.mock.calls[0][3]).toBe('check');
    });

    test('keeps an Error rejection as-is', async () => {
        const err = new Error('native failure');
        respondWith((success, error) => error(err));
        await expect(RotomRoot.check()).rejects.toBe(err);
    });
});
