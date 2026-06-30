package org.example.mcscanner;

import android.os.Bundle;
import android.webkit.*;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private WebView webView;

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
            webView.evaluateJavascript("log('Scan started with " + count + " IPs from " + startIp + "');", null);

            // Safe background thread
            new Thread(() -> {
                try {
                    for (int i = 0; i < Math.min(count, 10000); i++) {   // Limited for safety
                        final String logMsg = "Scanning... " + (i + 1) + "/" + count;
                        runOnUiThread(() -> {
                            webView.evaluateJavascript("log('" + logMsg + "');", null);
                        });
                        Thread.sleep(5); // Small delay to prevent freezing
                    }
                    runOnUiThread(() -> webView.evaluateJavascript("log('Scan completed.');", null));
                } catch (Exception e) {
                    runOnUiThread(() -> webView.evaluateJavascript("log('Error: " + e.getMessage() + "');", null));
                }
            }).start();
        }

        @JavascriptInterface
        public void stopScan() {
            webView.evaluateJavascript("log('Scan stopped by user');", null);
        }

        @JavascriptInterface
        public void continueFromLast() {
            webView.evaluateJavascript("log('Continuing from last point...');", null);
        }
    }
}
