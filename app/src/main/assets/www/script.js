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

// ---------- helpers ----------
function esc(str) {
    return String(str).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
}

function el(tag, cls, text) {
    const e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text !== undefined && text !== null) e.textContent = text;
    return e;
}

const MC_COLORS = {
    '0':'#000000','1':'#0000AA','2':'#00AA00','3':'#00AAAA','4':'#AA0000','5':'#AA00AA','6':'#FFAA00','7':'#AAAAAA',
    '8':'#555555','9':'#5555FF','a':'#55FF55','b':'#55FFFF','c':'#FF5555','d':'#FF55FF','e':'#FFFF55','f':'#FFFFFF'
};

// Renders Minecraft "§" formatted text to safe HTML (all text is escaped).
function renderMotd(text) {
    let html = '', buf = '';
    let color = null, bold = false, italic = false, under = false, strike = false;

    const flush = () => {
        if (!buf) return;
        let st = '';
        if (color) st += 'color:' + color + ';';
        if (bold) st += 'font-weight:bold;';
        if (italic) st += 'font-style:italic;';
        if (under || strike) st += 'text-decoration:' + (under ? 'underline ' : '') + (strike ? 'line-through' : '') + ';';
        html += '<span style="' + st + '">' + esc(buf).replace(/\n/g, '<br>') + '</span>';
        buf = '';
    };

    for (let k = 0; k < text.length; k++) {
        const ch = text[k];
        if (ch === '\u00a7' && k + 1 < text.length) {
            const c = text[k + 1].toLowerCase();
            flush();
            if (c === 'x') {
                const hex = text.substr(k + 2, 12);
                if (/^(\u00a7[0-9a-f]){6}$/i.test(hex)) {
                    color = '#' + hex.replace(/\u00a7/g, '');
                    bold = italic = under = strike = false;
                    k += 13;
                    continue;
                }
            }
            k++;
            if (MC_COLORS[c]) { color = MC_COLORS[c]; bold = italic = under = strike = false; }
            else if (c === 'l') bold = true;
            else if (c === 'o') italic = true;
            else if (c === 'n') under = true;
            else if (c === 'm') strike = true;
            else if (c === 'r') { color = null; bold = italic = under = strike = false; }
            continue;
        }
        buf += ch;
    }
    flush();
    return html;
}

function plainMotd(text) {
    return String(text || '').replace(/\u00a7x(\u00a7[0-9a-f]){6}/gi, '').replace(/\u00a7./g, '').replace(/\s+/g, ' ').trim();
}

function addRow(parent, label, value) {
    if (value === undefined || value === null || value === '') return;
    const row = el('div', 'srv-row');
    row.appendChild(el('span', 'srv-label', label));
    row.appendChild(el('span', 'srv-value', value));
    parent.appendChild(row);
}

function yesNo(v) { return v ? 'Yes' : 'No'; }

// ---------- server card ----------
function buildServerCard(server) {
    const card = el('div', 'srv-card');
    card.dataset.ip = server.ip || '';

    // Header: favicon + ip + ping badge
    const head = el('div', 'srv-head');
    if (typeof server.favicon === 'string' && server.favicon.startsWith('data:image/png;base64,')) {
        const img = el('img', 'srv-icon');
        img.src = server.favicon;
        img.alt = '';
        head.appendChild(img);
    }
    const title = el('div', 'srv-title');
    title.appendChild(el('div', 'srv-ip', server.ip || 'unknown'));
    if (server.hostname) title.appendChild(el('div', 'srv-sub', server.hostname));
    head.appendChild(title);

    const badge = el('span', 'srv-badge');
    if (server.status === 'online') {
        badge.classList.add('ok');
        badge.textContent = typeof server.pingMs === 'number' && server.pingMs >= 0 ? server.pingMs + ' ms' : 'online';
    } else if (server.status === 'open') {
        badge.classList.add('warn');
        badge.textContent = 'port open';
    } else {
        badge.textContent = 'saved';
    }
    head.appendChild(badge);
    card.appendChild(head);

    // MOTD
    if (server.motd) {
        const motd = el('div', 'srv-motd');
        motd.innerHTML = renderMotd(server.motd);
        card.appendChild(motd);
    }

    const info = el('div', 'srv-info');

    if (server.status === 'open') {
        addRow(info, 'Status', 'Port is open, but it did not answer the Minecraft status query (not a Minecraft server, or it blocks pings).');
    } else if (!server.status) {
        addRow(info, 'Status', 'Saved by an older version of the app. Rescan to get full details.');
    }

    addRow(info, 'Version', server.version);
    addRow(info, 'Protocol', (typeof server.protocol === 'number' && server.protocol >= 0) ? String(server.protocol) : '');
    addRow(info, 'Software', server.software);

    if (typeof server.online === 'number' && server.online >= 0) {
        const max = (typeof server.max === 'number' && server.max >= 0) ? server.max : '?';
        addRow(info, 'Players', server.online + ' / ' + max);
    }
    if (Array.isArray(server.players) && server.players.length) {
        addRow(info, 'Player list', server.players.map(p => p.name).filter(Boolean).join(', '));
    } else if (server.online > 0) {
        addRow(info, 'Player list', 'Hidden by the server');
    }

    addRow(info, 'Mod loader', server.modLoader);
    addRow(info, 'Modpack', server.modpack);
    if (Array.isArray(server.mods) && server.mods.length) {
        addRow(info, 'Mods (' + server.mods.length + ')', server.mods.join(', '));
    }

    if (typeof server.enforcesSecureChat === 'boolean') addRow(info, 'Enforces secure chat', yesNo(server.enforcesSecureChat));
    if (typeof server.previewsChat === 'boolean') addRow(info, 'Chat previews', yesNo(server.previewsChat));
    if (typeof server.preventsChatReports === 'boolean') addRow(info, 'Prevents chat reports', yesNo(server.preventsChatReports));

    addRow(info, 'Query type', server.pingType === 'modern' ? 'Modern (1.7+)' : (server.pingType === 'legacy' ? 'Legacy (pre-1.7)' : ''));
    if (typeof server.connectMs === 'number') addRow(info, 'Connect time', server.connectMs + ' ms');
    if (server.foundAt) addRow(info, 'Found', new Date(server.foundAt).toLocaleString());
    card.appendChild(info);

    if (server.raw) {
        const det = el('details', 'srv-raw');
        det.appendChild(el('summary', null, 'Raw server response'));
        det.appendChild(el('pre', null, server.raw));
        card.appendChild(det);
    }

    return card;
}

function addFound(server) {
    if (typeof server === 'string') server = { ip: server };

    const foundDiv = document.getElementById('found');
    const empty = foundDiv.querySelector('.srv-empty');
    if (empty) empty.remove();

    const card = buildServerCard(server);
    const existing = Array.from(foundDiv.children).find(c => c.dataset && c.dataset.ip === server.ip);
    if (existing) {
        foundDiv.replaceChild(card, existing);
    } else {
        foundDiv.appendChild(card);
        foundCount++;
        document.getElementById('count').textContent = foundCount;
    }

    let summary = server.ip;
    if (server.version) summary += ' - ' + server.version;
    if (typeof server.online === 'number' && server.online >= 0) summary += ' - ' + server.online + '/' + server.max + ' players';
    if (server.motd) summary += ' - "' + plainMotd(server.motd).slice(0, 60) + '"';
    if (server.status === 'open') summary += ' (port open, no Minecraft response)';
    log("✅ FOUND: " + esc(summary));
}

function renderFoundServers(servers) {
    const foundDiv = document.getElementById('found');
    foundDiv.innerHTML = '';

    if (!servers || servers.length === 0) {
        foundCount = 0;
        const empty = el('div', 'srv-empty', 'No servers found :(');
        foundDiv.appendChild(empty);
        document.getElementById('count').textContent = 0;
        return;
    }

    foundCount = servers.length;
    document.getElementById('count').textContent = foundCount;

    servers.forEach(server => {
        if (typeof server === 'string') server = { ip: server };
        foundDiv.appendChild(buildServerCard(server));
    });
}

function loadFoundServers() {
    if (window.Android) {
        window.Android.loadFoundServers();
    } else {
        renderFoundServers([]);
    }
}

function showTab(n) {
    document.querySelectorAll('.tab-content').forEach(el => el.style.display = 'none');
    document.getElementById('tab' + n).style.display = 'block';
    
    document.querySelectorAll('.tab').forEach((el, i) => {
        el.classList.toggle('active', i === n);
    });
    
    if (n === 1) loadFoundServers();
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
    log("Continuing from last saved point...");
    devLog("CONTINUE FROM LAST REQUESTED");
    if (window.Android) {
        window.Android.continueFromLast();
    }
}

function scanCompleted() {
    isScanning = false;
    document.getElementById('status').textContent = "Scan completed";
    log("✅ Scan completed");
    devLog("SCAN FINISHED SUCCESSFULLY");
}

// Initialize
showTab(0);
log("App ready. Type 'devlog' in IP Count field for detailed scanning log.");
