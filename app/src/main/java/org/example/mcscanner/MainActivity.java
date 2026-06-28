package org.example.mcscanner;

import android.os.Bundle;
import android.webkit.*;
import androidx.appcompat.app.AppCompatActivity;
import java.net.*;
import java.util.concurrent.*;

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

        webView.addJavascriptInterface(new AndroidInterface(), "Android");
        setContentView(webView);
        webView.loadUrl("file:///android_asset/www/index.html");
    }

    public class AndroidInterface {
        @JavascriptInterface
        public void startScan(String startIp, int count) {
            if (isScanning) return;
            isScanning = true;

            new Thread(() -> {
                try {
                    String[] parts = startIp.split(":");
                    String ipStr = parts[0];
                    int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 25565;

                    String[] octets = ipStr.split("\\.");
                    int base = Integer.parseInt(octets[3]);

                    for (int i = 0; i < count && isScanning; i++) {
                        int lastOctet = (base + i) % 256;
                        String testIp = octets[0] + "." + octets[1] + "." + octets[2] + "." + lastOctet;

                        try {
                            Socket socket = new Socket();
                            socket.connect(new InetSocketAddress(testIp, port), 700);
                            socket.close();
                            String result = "Open port 25565";
                            runOnUiThread(() -> {
                                webView.evaluateJavascript("addFound('" + testIp + ":" + port + "', '" + result + "');", null);
                                webView.evaluateJavascript("log('FOUND: " + testIp + ":" + port + "');", null);
                            });
                        } catch (Exception ignored) {}
                    }
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
        }

        @JavascriptInterface
        public void continueFromLast() {
            runOnUiThread(() -> webView.evaluateJavascript("log('Continuing scan...');", null));
        }
    }
}
