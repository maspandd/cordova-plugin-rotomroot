var exec = require('cordova/exec');

var SERVICE = 'RotomRoot';

function call(action) {
    return new Promise(function (resolve, reject) {
        exec(resolve, function (err) {
            reject(err instanceof Error ? err : new Error(String(err)));
        }, SERVICE, action, []);
    });
}

var RotomRoot = {
    /**
     * Resolves to true when any strong root signal is detected.
     * @returns {Promise<boolean>}
     */
    isRooted: function () {
        return call('isRooted').then(function (result) {
            return result === true || result === 1 || result === 'true';
        });
    },

    /**
     * Resolves to a full report of every individual check.
     * @returns {Promise<{isRooted: boolean, checks: Object<string, boolean>}>}
     */
    check: function () {
        return call('check');
    }
};

module.exports = RotomRoot;
