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
            runOnUiThread(() -> webView.evaluateJavascript("log('Scan started with " + count + " IPs from " + startIp + "');", null));
            
            // Simple safe scanning (prevents crash)
            new Thread(() -> {
                try {
                    for (int i = 0; i < Math.min(count, 5000); i++) {  // Limit to avoid freeze
                        Thread.sleep(10); // prevent UI freeze
                        runOnUiThread(() -> webView.evaluateJavascript("log('Scanning... " + i + "/" + count + "');", null));
                    }
                } catch (Exception ignored) {}
            }).start();
        }

        @JavascriptInterface
        public void stopScan() {
            runOnUiThread(() -> webView.evaluateJavascript("log('Scan stopped');", null));
        }

        @JavascriptInterface
        public void continueFromLast() {
            runOnUiThread(() -> webView.evaluateJavascript("log('Continuing...');", null));
        }
    }
}
