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

            new Thread(() -> {
                try {
                    String[] parts = startIp.split(":");
                    String ipStr = parts[0];
                    int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 25565;

                    String[] octets = ipStr.split("\\.");
                    int base = Integer.parseInt(octets[3]);

                    for (int i = 0; i < Math.min(count, 20000) && isScanning; i++) {
                        int lastOctet = (base + i) % 256;
                        String testIp = octets[0] + "." + octets[1] + "." + octets[2] + "." + lastOctet;

                        try {
                            java.net.Socket socket = new java.net.Socket();
                            socket.connect(new java.net.InetSocketAddress(testIp, port), 600);
                            socket.close();

                            String result = "Open port 25565";
                            runOnUiThread(() -> {
                                webView.evaluateJavascript("addFound('" + testIp + ":" + port + "', '" + result + "');", null);
                                webView.evaluateJavascript("log('FOUND: " + testIp + ":" + port + "');", null);
                            });
                        } catch (Exception ignored) {}
                        
                        Thread.sleep(8);
                    }

                    runOnUiThread(() -> webView.evaluateJavascript("scanCompleted();", null));

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
