package com.vidmiss.serverscanner;

import android.os.Bundle;
import android.webkit.*;
import androidx.appcompat.app.AppCompatActivity;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private volatile boolean isScanning = false;
    private SharedPreferences prefs;
    private SharedPreferences foundPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("MCScannerPrefs", MODE_PRIVATE);
        foundPrefs = getSharedPreferences("FoundServers", MODE_PRIVATE);

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        webView.addJavascriptInterface(new AndroidInterface(), "Android");
        webView.setWebViewClient(new WebViewClient());

        setContentView(webView);
        webView.loadUrl("file:///android_asset/www/index.html");
    }

    /** JSON is a valid JS literal except for raw U+2028/2029, so escape those. */
    private static String toJsLiteral(JSONObject o) {
        return o.toString().replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
    }

    /** Stores the server, replacing any earlier entry with the same ip:port. */
    private synchronized void saveServer(JSONObject server) {
        try {
            JSONArray old = new JSONArray(foundPrefs.getString("servers", "[]"));
            JSONArray out = new JSONArray();
            String ip = server.optString("ip");
            for (int i = 0; i < old.length(); i++) {
                JSONObject e = old.optJSONObject(i);
                if (e != null && !ip.equals(e.optString("ip"))) out.put(e);
            }
            out.put(server);
            foundPrefs.edit().putString("servers", out.toString()).apply();
        } catch (Exception ignored) {}
    }

    public class AndroidInterface {
        @JavascriptInterface
        public void startScan(String startIp, int count) {
            if (isScanning) return;
            isScanning = true;

            prefs.edit().putString("lastIp", startIp).apply();

            runOnUiThread(() -> {
                webView.evaluateJavascript("log('Scan started with " + count + " IPs from " + startIp + "');", null);
                webView.evaluateJavascript("document.getElementById('status').textContent = 'Scanning...';", null);
            });

            new Thread(() -> {
                int checked = 0;

                try {
                    String[] parts = startIp.split(":");
                    String ipStr = parts[0];
                    int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 25565;

                    String[] octets = ipStr.split("\\.");
                    int base = Integer.parseInt(octets[3]);

                    for (int i = 0; i < count && isScanning; i++) {
                        int lastOctet = (base + i) % 256;
                        String testIp = octets[0] + "." + octets[1] + "." + octets[2] + "." + lastOctet + ":" + port;
                        checked++;

                        final String currentIp = testIp;
                        runOnUiThread(() -> webView.evaluateJavascript("devLog('Checking → " + currentIp + "');", null));

                        if (checked % 300 == 0) {
                            final int current = checked;
                            runOnUiThread(() -> webView.evaluateJavascript("log('Progress: " + current + " IPs checked...');", null));
                        }

                        final String hostOnly = testIp.split(":")[0];
                        final int finalPort = port;
                        JSONObject info = MinecraftPinger.query(hostOnly, finalPort, 600);

                        if (info != null) {
                            saveServer(info);
                            final String json = toJsLiteral(info);
                            runOnUiThread(() -> webView.evaluateJavascript("addFound(" + json + ");", null));
                        }

                        Thread.sleep(8);
                    }

                    String nextIp = calculateNextIp(startIp, checked);
                    prefs.edit().putString("lastIp", nextIp).apply();

                    final int finalChecked = checked;
                    runOnUiThread(() -> {
                        webView.evaluateJavascript("scanCompleted();", null);
                        webView.evaluateJavascript("log('✅ Scan completed. Checked " + finalChecked + " IPs.');", null);
                    });

                } catch (Exception e) {
                    runOnUiThread(() -> webView.evaluateJavascript("log('Error: " + e.getMessage() + "');", null));
                } finally {
                    isScanning = false;
                }
            }).start();
        }

        private String calculateNextIp(String startIp, int checked) {
            try {
                String[] parts = startIp.split(":");
                String ipStr = parts[0];
                int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 25565;
                String[] octets = ipStr.split("\\.");
                int base = Integer.parseInt(octets[3]);
                int nextOctet = (base + checked) % 256;
                return octets[0] + "." + octets[1] + "." + octets[2] + "." + nextOctet + ":" + port;
            } catch (Exception e) {
                return startIp;
            }
        }

        @JavascriptInterface
        public void loadFoundServers() {
            try {
                String json = foundPrefs.getString("servers", "[]")
                        .replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
                runOnUiThread(() -> webView.evaluateJavascript("renderFoundServers(" + json + ");", null));
            } catch (Exception e) {
                runOnUiThread(() -> webView.evaluateJavascript("renderFoundServers([]);", null));
            }
        }

        @JavascriptInterface
        public void stopScan() {
            isScanning = false;
            runOnUiThread(() -> webView.evaluateJavascript("log('Scan stopped by user');", null));
        }

        @JavascriptInterface
        public void continueFromLast() {
            String lastIp = prefs.getString("lastIp", "0.0.0.0:25565");
            runOnUiThread(() -> {
                webView.evaluateJavascript("log('Continuing from last saved: " + lastIp + "');", null);
                webView.evaluateJavascript("devLog('RESUMING FROM: " + lastIp + "');", null);
            });
            startScan(lastIp, 10000);
        }
    }
}
