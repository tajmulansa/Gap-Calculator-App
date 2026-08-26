package com.tajmul.gapcalculator;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

// AdMob Imports
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.FullScreenContentCallback;

public class MainActivity extends AppCompatActivity {

    private AdView adView;
    private InterstitialAd mInterstitialAd;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Push content below the status bar and above the nav bar
        // (Android 15 / targetSdk 35 draws edge-to-edge by default)
        View rootView = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Start Ads in the background
        new Thread(() -> {
            MobileAds.initialize(this, initializationStatus -> {});
            runOnUiThread(() -> {
                // 1. Load the Banner Ad at the bottom
                adView = findViewById(R.id.adView);
                if (adView != null) {
                    adView.loadAd(new AdRequest.Builder().build());
                }

                // 2. Load the Full-Screen Interstitial Ad
                loadFullScreenAd();
            });
        }).start();

        // Setup the Calculator Web Interface
        WebView webView = findViewById(R.id.webView);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void loadFullScreenAd() {
        AdRequest adRequest = new AdRequest.Builder().build();

        // This is your LIVE AdMob Interstitial ID
        InterstitialAd.load(this, "ca-app-pub-4812493783151469/2570826540", adRequest,
            new InterstitialAdLoadCallback() {
                @Override
                public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                    mInterstitialAd = interstitialAd;

                    // Show the ad instantly as soon as it is ready
                    mInterstitialAd.show(MainActivity.this);

                    mInterstitialAd.setFullScreenContentCallback(new FullScreenContentCallback(){
                        @Override
                        public void onAdDismissedFullScreenContent() {
                            // This runs when the user hits the "X" to close the ad
                            mInterstitialAd = null;
                        }
                    });
                }

                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                    // If ad fails to load (e.g., no internet), it just skips quietly
                    mInterstitialAd = null;
                }
            });
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
