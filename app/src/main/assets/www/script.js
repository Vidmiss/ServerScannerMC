let foundCount = 0;
let isScanning = false;

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

function showTab(n) {
    document.querySelectorAll('.tab-content').forEach(el => el.style.display = 'none');
    document.getElementById('tab' + n).style.display = 'block';
    
    document.querySelectorAll('.tab').forEach((el, i) => {
        el.classList.toggle('active', i === n);
    });
}

function startScan() {
    if (isScanning) return;
    isScanning = true;
    
    const ip = document.getElementById('firstIp').value.trim();
    const count = parseInt(document.getElementById('ipCount').value) || 10000;
    
    log("Scan started with " + count + " IPs from " + ip);
    document.getElementById('status').textContent = "Scanning...";

    if (window.Android) {
        window.Android.startScan(ip, count);
    }
}

function stopScan() {
    isScanning = false;
    if (window.Android) window.Android.stopScan();
    log("Scan stopped by user");
    document.getElementById('status').textContent = "Stopped";
}

function continueScan() {
    log("Continuing from last point...");
    if (window.Android) window.Android.continueFromLast();
}

// Called from Java when scan finishes
function scanCompleted() {
    isScanning = false;
    log("✅ Scan completed.");
    document.getElementById('status').textContent = "Scan completed";
    
    // Show message if no servers found
    const foundDiv = document.getElementById('found');
    if (foundCount === 0) {
        foundDiv.innerHTML = '<div style="text-align:center; padding:30px; color:#888;">No servers found :(</div>';
    }
}

// Make Found Servers tab always accessible
showTab(0); // Start on Log tab