let foundCount = 0;
let isScanning = false;
let devMode = false;

function log(text) {
    const div = document.getElementById('log');
    div.innerHTML += `<div>[${new Date().toLocaleTimeString()}] ${text}</div>`;
    div.scrollTop = div.scrollHeight;
}

function devLog(text) {
    if (!devMode) return;
    const div = document.getElementById('log');
    div.innerHTML += `<div style="color:#00ff00; background:#000000; padding:2px 6px; margin:1px 0; font-family:monospace; font-size:13px; white-space:pre;">${text}</div>`;
    div.scrollTop = div.scrollHeight;
}

function addFound(ip, info) {
    foundCount++;
    document.getElementById('count').textContent = foundCount;
    const div = document.createElement('div');
    div.style.cssText = 'background:#1f1f1f; padding:12px; margin:8px 0; border-left:4px solid #00ff9d; border-radius:6px;';
    div.innerHTML = `<strong>\( {ip}</strong><br> \){info}`;
    document.getElementById('found').appendChild(div);
    log("✅ FOUND: " + ip);
}

function showTab(n) {
    document.querySelectorAll('.tab-content').forEach(el => el.style.display = 'none');
    document.getElementById('tab' + n).style.display = 'block';
    
    document.querySelectorAll('.tab').forEach((el, i) => {
        el.classList.toggle('active', i === n);
    });
}

function startScan() {
    const ip = document.getElementById('firstIp').value.trim();
    let countInput = document.getElementById('ipCount').value.trim();

    if (countInput.toLowerCase() === "devlog") {
        devMode = true;
        log("Developer Mode Activated");
        devLog("========================================");
        devLog("          DEVLOG MODE ENABLED");
        devLog("========================================");
        document.getElementById('ipCount').value = "10000";
        return;
    }

    if (isScanning) return;
    isScanning = true;
    
    const count = parseInt(countInput) || 10000;
    
    log("Scan started with " + count + " IPs from " + ip);
    devLog("STARTING SCAN");
    devLog("Target : " + ip);
    devLog("Count  : " + count);
    document.getElementById('status').textContent = "Scanning...";

    if (window.Android) {
        window.Android.startScan(ip, count);
    } else {
        log("Test mode - no Java bridge");
    }
}

function stopScan() {
    isScanning = false;
    if (window.Android) window.Android.stopScan();
    log("Scan stopped by user");
    devLog("STOP COMMAND RECEIVED");
    document.getElementById('status').textContent = "Stopped";
}

function continueScan() {
    log("Continuing from last point...");
    devLog("CONTINUE FROM LAST REQUESTED");
    if (window.Android) window.Android.continueFromLast();
}

function scanCompleted() {
    isScanning = false;
    document.getElementById('status').textContent = "Scan completed";
    log("✅ Scan completed");
    devLog("SCAN FINISHED SUCCESSFULLY");
    
    const foundDiv = document.getElementById('found');
    if (foundCount === 0) {
        foundDiv.innerHTML = '<div style="text-align:center; padding:40px; color:#888; font-size:18px;">No servers found :(</div>';
    }
}

// Initialize
showTab(0);
log("App ready. Type 'devlog' in IP Count field for detailed scanning log.");
