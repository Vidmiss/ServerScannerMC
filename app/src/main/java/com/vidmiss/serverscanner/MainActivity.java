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

                        boolean isServer = false;
                        try {
                            java.net.Socket socket = new java.net.Socket();
                            socket.connect(new java.net.InetSocketAddress(testIp.split(":")[0], port), 600);
                            socket.close();
                            isServer = true;
                        } catch (Exception ignored) {}

                        if (isServer) {
                            String info = "Open port 25565 (possible MC server)";
                            runOnUiThread(() -> {
                                webView.evaluateJavascript("addFound('" + currentIp + "', '" + info + "');", null);
                                webView.evaluateJavascript("log('✅ FOUND: " + currentIp + "');", null);
                            });
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
        public void saveFoundServer(String ip, String info) {
            try {
                JSONArray servers = new JSONArray(foundPrefs.getString("servers", "[]"));
                JSONObject server = new JSONObject();
                server.put("ip", ip);
                server.put("info", info);
                servers.put(server);
                foundPrefs.edit().putString("servers", servers.toString()).apply();
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void loadFoundServers() {
            try {
                String data = foundPrefs.getString("servers", "[]");
                runOnUiThread(() -> webView.evaluateJavascript("renderFoundServers(" + data + ");", null));
            } catch (Exception ignored) {}
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
