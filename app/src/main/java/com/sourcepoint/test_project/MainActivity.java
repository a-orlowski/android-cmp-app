package com.sourcepoint.test_project;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.sourcepoint.gdpr_cmplibrary.GDPRConsentLib;
import com.sourcepoint.gdpr_cmplibrary.NativeMessage;
import com.sourcepoint.gdpr_cmplibrary.NativeMessageAttrs;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "**MainActivity";

    private ViewGroup mainViewGroup;

    private void showMessage(View view) {
        if(view.getParent() == null){
            view.setLayoutParams(new ViewGroup.LayoutParams(0, 0));
            view.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
            view.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;
            view.bringToFront();
            view.requestLayout();
            mainViewGroup.addView(view);

        }

    }
    private void removeWebView(View view) {
        if(view.getParent() != null)
            mainViewGroup.removeView(view);
    }


    private GDPRConsentLib buildGDPRConsentLib() {
        return GDPRConsentLib.newBuilder(22, "mobile.demo", 2372,"5c0e81b7d74b3c30c6852301",this)
                .setStagingCampaign(false)
                .setTargetingParam("native", "true")
                .setOnConsentUIReady(view -> {
                    showMessage(view);
                    Log.i(TAG, "onConsentUIReady");
                })
                .setOnConsentUIFinished(view -> {
                    removeWebView(view);
                    Log.i(TAG, "onConsentUIFinished");
                })
                .setOnConsentReady(consent -> {
                    Log.i(TAG, "onConsentReady");
                    for (String vendorId : consent.acceptedVendors) {
                        Log.i(TAG, "The vendor " + vendorId + " was accepted.");
                    }
                    for (String purposeId : consent.acceptedCategories) {
                        Log.i(TAG, "The category " + purposeId + " was accepted.");
                    }
                })
                .setOnError(error -> {
                    Log.e(TAG, "Something went wrong: ", error);
                    Log.i(TAG, "ConsentLibErrorMessage: " + error.consentLibErrorMessage);
                })
                .build();
    }

    private NativeMessage buildNativeMessage(){
        return new NativeMessage(this){
            @Override
            public void init(){
                super.init();
                // When using a customized layout one can completely override the init method
                // not calling super.init() and inflating the native view with the chosen layout instead.
                // In this case its important to set all the default child views using the setter methods
                // like its done in the super.init()
            }
            @Override
            public void setAttributes(NativeMessageAttrs attrs){
                super.setAttributes(attrs);
                //Here one can extend this method in order to set customized attributes other then the ones
                //already set in the super.setAttributes. No need to completely override this method.
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        buildGDPRConsentLib().run(buildNativeMessage());
        //buildGDPRConsentLib().run() can be called (with no arg) in order to work with the webview based message
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mainViewGroup = findViewById(android.R.id.content);
        findViewById(R.id.review_consents).setOnClickListener(_v -> buildGDPRConsentLib().showPm());
    }
}