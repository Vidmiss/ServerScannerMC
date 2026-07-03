package com.vidmiss.serverscanner;

import android.os.Bundle;
import android.webkit.*;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private volatile boolean isScanning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

            webView.evaluateJavascript("log('Scan started with " + count + " IPs from " + startIp + "');", null);
            webView.evaluateJavascript("document.getElementById('status').textContent = 'Scanning...';", null);

            new Thread(() -> {
                int checked = 0;
                int foundThisScan = 0;

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

                        // Update UI every 100 checks
                        if (checked % 100 == 0) {
                            final int currentChecked = checked;
                            runOnUiThread(() -> webView.evaluateJavascript(
                                "log('Checked " + currentChecked + " IPs...');", null));
                        }

                        try {
                            java.net.Socket socket = new java.net.Socket();
                            socket.connect(new java.net.InetSocketAddress(testIp.split(":")[0], port), 500);
                            socket.close();

                            foundThisScan++;
                            runOnUiThread(() -> {
                                webView.evaluateJavascript("addFound('" + testIp + "', 'Open port 25565');", null);
                                webView.evaluateJavascript("log('✅ FOUND: " + testIp + "');", null);
                            });
                        } catch (Exception ignored) {}

                        Thread.sleep(5); // Small delay for responsiveness
                    }

                    // Scan finished
                    final int finalFound = foundThisScan;
                    runOnUiThread(() -> {
                        webView.evaluateJavascript("scanCompleted();", null);
                        webView.evaluateJavascript("log('✅ Scan completed. Checked " + checked + " IPs. Found " + finalFound + " servers.');", null);
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
            runOnUiThread(() -> webView.evaluateJavascript("log('Continuing from last point...');", null));
        }
    }
}
