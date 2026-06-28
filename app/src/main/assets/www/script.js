let foundCount = 0;

function log(text) {
    const div = document.getElementById('log');
    div.innerHTML += `<div>[${new Date().toLocaleTimeString()}] ${text}</div>`;
    div.scrollTop = div.scrollHeight;
}

function addFound(ip, info) {
    foundCount++;
    document.getElementById('count').textContent = foundCount;
    const div = document.createElement('div');
    div.style.cssText = 'background:#1f1f1f; padding:12px; margin:8px 0; border-left:4px solid #00ff9d; border-radius:6px;';
    div.innerHTML = `<strong>\( {ip}</strong><br> \){info}`;
    document.getElementById('found').appendChild(div);
}

function startScan() {
    const ip = document.getElementById('firstIp').value;
    const count = parseInt(document.getElementById('ipCount').value) || 100000;
    log("Scan started...");
    if (window.Android) {
        window.Android.startScan(ip, count);
    }
}

function stopScan() {
    if (window.Android) window.Android.stopScan();
    log("Scan stopped by user");
}

function continueScan() {
    log("Continuing from last point...");
    if (window.Android) window.Android.continueFromLast();
}
