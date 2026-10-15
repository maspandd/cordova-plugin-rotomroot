document.addEventListener('deviceready', function () {
    var button = document.getElementById('run');
    var verdict = document.getElementById('verdict');
    var report = document.getElementById('report');

    function run() {
        verdict.textContent = 'Checking…';
        RotomRoot.check().then(function (result) {
            verdict.textContent = (result.isRooted ? 'Rooted' : 'Not rooted') + ' · ' + new Date().toLocaleTimeString();
            report.textContent = JSON.stringify(result, null, 2);
            console.log('RotomRoot report ' + JSON.stringify(result));
        }).catch(function (err) {
            verdict.textContent = 'Error';
            report.textContent = String(err && err.message || err);
            console.error('RotomRoot error ' + report.textContent);
        });
    }

    button.disabled = false;
    button.addEventListener('click', run);
    run();
}, false);
