package com.tajmul.gapcalculator;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;

public class MainActivity extends AppCompatActivity {
    
    private AdView adView;
    
    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Ads ko background mein start karo taaki app jaldi khule
        new Thread(() -> {
            MobileAds.initialize(this, initializationStatus -> {});
            runOnUiThread(() -> {
                adView = findViewById(R.id.adView);
                if (adView != null) {
                    adView.loadAd(new AdRequest.Builder().build());
                }
            });
        }).start();

        // WebView setup (Tumhara calculator)
        WebView webView = findViewById(R.id.webView);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("file:///android_asset/index.html");
    }
    
    @Override 
    protected void onResume() { 
        super.onResume(); 
        if (adView != null) adView.resume(); 
    }
    
    @Override 
    protected void onPause() { 
        if (adView != null) adView.pause(); 
        super.onPause(); 
    }
    
    @Override 
    protected void onDestroy() { 
        if (adView != null) adView.destroy(); 
        super.onDestroy(); 
    }
}
