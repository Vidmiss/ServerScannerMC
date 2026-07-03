package com.vidmiss.serverscanner;

import android.os.Bundle;
import android.webkit.*;
import androidx.appcompat.app.AppCompatActivity;
import android.content.SharedPreferences;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private volatile boolean isScanning = false;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("MCScannerPrefs", MODE_PRIVATE);

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

                        // Always send to devLog (every IP)
                        final String currentIp = testIp;
                        runOnUiThread(() -> {
                            webView.evaluateJavascript("devLog('Checking → " + currentIp + "');", null);
                        });

                        if (checked % 300 == 0) {
                            final int current = checked;
                            runOnUiThread(() -> webView.evaluateJavascript("log('Progress: " + current + " IPs checked...');", null));
                        }

                        try {
                            java.net.Socket socket = new java.net.Socket();
                            socket.connect(new java.net.InetSocketAddress(testIp.split(":")[0], port), 400);
                            socket.close();

                            runOnUiThread(() -> {
                                webView.evaluateJavascript("addFound('" + currentIp + "', 'Open port 25565');", null);
                                webView.evaluateJavascript("log('✅ FOUND: " + currentIp + "');", null);
                            });
                        } catch (Exception ignored) {}

                        Thread.sleep(5);
                    }

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
