package com.tajmul.gapcalculator;

import android.annotation.SuppressLint;
import android.content.IntentSender;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.snackbar.Snackbar;

// AdMob Imports
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.FullScreenContentCallback;

// In-App Update Imports
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.install.InstallStateUpdatedListener;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.InstallStatus;
import com.google.android.play.core.install.model.UpdateAvailability;

public class MainActivity extends AppCompatActivity {

    private AdView adView;
    private InterstitialAd mInterstitialAd;
    private AppUpdateManager appUpdateManager;
    private View rootView;

    private final ActivityResultLauncher<IntentSenderRequest> updateLauncher =
        registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> {
            // Result of the update flow - nothing special needed here for FLEXIBLE updates
        });

    private final InstallStateUpdatedListener installStateListener = state -> {
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            // Update downloaded in the background - prompt the user to restart and install it
            popupSnackbarForCompleteUpdate();
        }
    };

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Push content below the status bar and above the nav bar
        // (Android 15 / targetSdk 35+ draws edge-to-edge by default)
        rootView = findViewById(R.id.main);
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

        // Check if a newer version is available on the Play Store
        appUpdateManager = AppUpdateManagerFactory.create(this);
        checkForAppUpdate();
    }

    private void checkForAppUpdate() {
        appUpdateManager.getAppUpdateInfo().addOnSuccessListener(appUpdateInfo -> {
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                    && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                try {
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        updateLauncher,
                        com.google.android.play.core.appupdate.AppUpdateOptions
                            .newBuilder(AppUpdateType.FLEXIBLE).build()
                    );
                } catch (Exception e) {
                    // Silently ignore - update prompt just won't show this time
                }
            }
        });
    }

    private void popupSnackbarForCompleteUpdate() {
        Snackbar snackbar = Snackbar.make(
            rootView,
            "An update has been downloaded.",
            Snackbar.LENGTH_INDEFINITE
        );
        snackbar.setAction("RESTART", view -> appUpdateManager.completeUpdate());
        snackbar.show();
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

        // If a FLEXIBLE update was downloaded while the app was in the background, prompt now
        if (appUpdateManager != null) {
            appUpdateManager.getAppUpdateInfo().addOnSuccessListener(appUpdateInfo -> {
                if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                    popupSnackbarForCompleteUpdate();
                }
            });
            appUpdateManager.registerListener(installStateListener);
        }
    }

    @Override 
    protected void onPause() { 
        if (adView != null) adView.pause(); 
        if (appUpdateManager != null) {
            appUpdateManager.unregisterListener(installStateListener);
        }
        super.onPause(); 
    }

    @Override 
    protected void onDestroy() { 
        if (adView != null) adView.destroy(); 
        super.onDestroy(); 
    }
}
