package com.tajmul.gapcalculator;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.IntentSender;
import android.net.Uri;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.snackbar.Snackbar;
import java.util.concurrent.atomic.AtomicBoolean;

// AdMob Imports
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;

// User Messaging Platform (GDPR / EEA+UK consent)
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

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
    private ConsentInformation consentInformation;
    private final AtomicBoolean adsInitialized = new AtomicBoolean(false);
    private static final String PRIVACY_POLICY_URL =
        "https://tajmulansa.github.io/Gap-Calculator-App/privacy.html";
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
        // Edge-to-edge on every Android version (dark bars -> light icons, app bg is dark)
        EdgeToEdge.enable(this,
            SystemBarStyle.dark(Color.TRANSPARENT),
            SystemBarStyle.dark(Color.TRANSPARENT));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Keep content clear of the status bar and nav bar
        rootView = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Privacy links shown at the bottom of the app
        TextView privacyPolicyLink = findViewById(R.id.privacyPolicyLink);
        privacyPolicyLink.setOnClickListener(v ->
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))));
        TextView privacySettingsLink = findViewById(R.id.privacySettingsLink);
        privacySettingsLink.setOnClickListener(v ->
            UserMessagingPlatform.showPrivacyOptionsForm(this, formError -> {
                if (consentInformation.canRequestAds()) initializeAdsOnce();
            }));

        // Ask for consent (EEA/UK/CH) BEFORE any ad is requested
        setUpConsentThenAds(privacySettingsLink);

        // Setup the Calculator Web Interface
        WebView webView = findViewById(R.id.webView);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(false);      // bundled assets still load via file:///android_asset
        ws.setAllowContentAccess(false);
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

    private void setUpConsentThenAds(TextView privacySettingsLink) {
        consentInformation = UserMessagingPlatform.getConsentInformation(this);
        ConsentRequestParameters params = new ConsentRequestParameters.Builder().build();

        consentInformation.requestConsentInfoUpdate(this, params,
            () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(this, formError -> {
                updatePrivacySettingsLink(privacySettingsLink);
                if (consentInformation.canRequestAds()) initializeAdsOnce();
            }),
            requestError -> {
                // Could not reach the consent service: only load ads if consent was already obtained earlier
                updatePrivacySettingsLink(privacySettingsLink);
                if (consentInformation.canRequestAds()) initializeAdsOnce();
            });

        // Returning users who already gave consent can load ads right away
        if (consentInformation.canRequestAds()) initializeAdsOnce();
    }

    private void updatePrivacySettingsLink(TextView link) {
        boolean required = consentInformation.getPrivacyOptionsRequirementStatus()
            == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
        link.setVisibility(required ? View.VISIBLE : View.GONE);
    }

    private void initializeAdsOnce() {
        if (!adsInitialized.compareAndSet(false, true)) return;
        new Thread(() -> {
            MobileAds.initialize(this, initializationStatus -> {});
            runOnUiThread(() -> {
                adView = findViewById(R.id.adView);
                if (adView != null) {
                    adView.loadAd(new AdRequest.Builder().build());
                }
            });
        }).start();
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
