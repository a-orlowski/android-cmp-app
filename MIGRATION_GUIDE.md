# Migrate from v6 to v7 (Unified SDK) -- Kotlin/Java
In order to migrate our SDK from v6 to v7, it is necessary just to add the `propertyId` parameter into the config object:

Kotlin
```kotlin
    val cmpConfig : SpConfig = config {
                  accountId = 22
                  propertyId = 16893  // NEW FIELD
                  propertyName = "mobile.multicampaign.demo"
                  messLanguage = MessageLanguage.ENGLISH
                  campaignsEnv = CampaignsEnv.PUBLIC
                  messageTimeout = 4000
                  +CampaignType.CCPA
                  +CampaignType.GDPR
                }
```

Java
```java
    private final SpConfig spConfig = new SpConfigDataBuilder()
            .addAccountId(22)
            .addPropertyId(16893)   // NEW FIELD
            .addPropertyName("mobile.multicampaign.demo")
            .addMessageLanguage(MessageLanguage.ENGLISH) 
            .addCampaign(CampaignType.GDPR)
            .addCampaign(CampaignType.CCPA)
            .build();
```

# Migrate to v6 (Unified SDK) -- Java

In this guide we will cover how to migrate your app to the version 6.X.X of Sourcepoint's SDK (from v5.X.X).

>**Note:** In addition to the technical migration below, you will also need to enable the **Multi-Campaign** toggle for the app property within the Sourcepoint portal. 

## Upgrade library in project's build.gradle file

Navigate to your build.gradle file and upgrade the `cmplibrary`:

**v6 (Unified SDK)**
```java
implementation 'com.sourcepoint.cmplibrary:cmplibrary:<latest-version>'
```

## Remove out of date code from project
With the change to v6 (Unified SDK) the following configurations are no longer used and can be safely removed from your project.

```java
//remove from project

    final static int accountId = 22;
    final static int propertyId = 7639;
    final static String propertyName = "tcfv2.mobile.webview";
    final static String pmId = "122058";

    private ViewGroup mainViewGroup;

    private void showView(View view) {
        if(view.getParent() == null){
            view.setLayoutParams(new ViewGroup.LayoutParams(0, 0));
            view.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
            view.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;
            view.bringToFront();
            view.requestLayout();
            mainViewGroup.addView(view);
        }
    }
    private void removeView(View view) {
        if(view.getParent() != null) mainViewGroup.removeView(view);
    }

    private GDPRConsentLib buildGDPRConsentLib() {
        return GDPRConsentLib.newBuilder(accountId, propertyName, propertyId, pmId,this)
                .setOnConsentUIReady(this::showView)
                .setOnAction(actionType  -> Log.i(TAG , "ActionType: " + actionType.toString()))
                .setOnConsentUIFinished(this::removeView)
                .setOnConsentReady(consent -> {
                    // at this point it's safe to initialise vendors
                    for (String line : consent.toString().split("\n"))
                        Log.i(TAG, line);
                })
                .setOnError(error -> Log.e(TAG, "Something went wrong"))
                .setAuthId(dataProvider.getValue().getAuthId())
                .build();
    }

private void showView(View view) {
        if(view.getParent() == null){
            view.setLayoutParams(new ViewGroup.LayoutParams(0, 0));
            view.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
            view.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;
            view.bringToFront();
            view.requestLayout();
            mainViewGroup.addView(view);
        }
    }

private void removeView(View view) {
        if(view.getParent() != null) mainViewGroup.removeView(view);
    }
```
## Create new _Config_ object
Use the data builder to obtain a configuration for v6 (Unified SDK). This contains your organization's account information and includes the type of campaigns that will be run on this property. This object will be called when you instantiate your CMP SDK.

```java
    private final SpConfig spConfig = new SpConfigDataBuilder()
            .addAccountId(22)
            .addPropertyName("mobile.multicampaign.demo")
            .addMessageLanguage(MessageLanguage.ENGLISH)        //or desired language
            .addCampaign(CampaignType.GDPR)
            .addCampaign(CampaignType.CCPA)
            .build();
```
## Delegate Methods
Previously, in order to receive events from the CMP SDK, you needed to provide multiple delegates/clients using the sets method available.

The v6 (Unified SDK) needs just one delegate which you need to implement and add it to you CMP instance.

```java
class LocalClient implements SpClient {
        @Override
        public void onMessageReady(@NotNull JSONObject message) { /* ... */ }
        @Override
        public void onError(@NotNull Throwable error){ /* ... */ }
        @Override
        public void onConsentReady(@NotNull SPConsents c){ /* ... */ }
        @Override
        public void onAction(@NotNull View view, @NotNull ActionType actionType) { /* ... */ }

				@Override
        public void onUIFinished(@NotNull View v) {
            spConsentLib.removeView(v); // remove the view consent
        }

        @Override
        public void onUIReady(@NotNull View v) {
            spConsentLib.showView(v);  // add the view consent
        }
    }
```
## Retrieve CMP SDK instance
Declare new library:
```
private SpConsentLib spConsentLib = null;
```
Add the following to `OnCreate`:
```java
spConsentLib = FactoryKt.makeConsentLib(
                spConfig,   // config object
                this,       // activity
                new LocalClient()
        );
```
## Run the consent
Call `loadMessage` inside `onResume`

**Original Version**
```java
    protected void onResume() {
        super.onResume();
        buildGDPRConsentLib().run();
    }
```
**v6 (Unified SDK)**
```java
    @Override
    protected void onResume() {
        super.onResume();
        spConsentLib.loadMessage();
    }
```
## Release all resources
In order to make sure that all the resources are released after the activity `onDestroy` callback call, you need to execute this method when the activity gets destroyed
```java
    @Override
    protected void onDestroy() {
        super.onDestroy();
        spConsentLib.dispose();
    }
```
## App lifecycle
The Android CMP SDK does not modify the Activity lifecycle. This means that every time the `onDestroy` gets called  and the consent WebView is in the foreground, the WebView self gets removed (i.e. during a configuration change).

If you need to show the WebView consent after such an event,  you have to handle the change configuration on the client side.

## Load the privacy manager(s)
Replace `showPM` with the Privacy Managers that will be shown for each campaign

**Original version**
```java
                buildGDPRConsentLib().showPm();
```
**v6 (Unified SDK)**
```java
                spConsentLib.loadPrivacyManager(
                        "10000", //PM id
                        PMTab.PURPOSES, //Initial PM tab to open
                        CampaignType.GDPR //Campaign type
                );

                spConsentLib.loadPrivacyManager(
                        "20000",
                        PMTab.PURPOSES,
                        CampaignType.CCPA
                );
```

# Summary
Below is a full example of the changes covered in this article:
```java
    private final SpConfig spConfig = new SpConfigDataBuilder()
            .addAccountId(22)
            .addPropertyName("mobile.multicampaign.demo")
            .addMessageLanguage(MessageLanguage.ENGLISH)
            .addCampaign(CampaignType.GDPR)
            .addCampaign(CampaignType.CCPA)
            .build();

    private SpConsentLib spConsentLib = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        spConsentLib = FactoryKt.makeConsentLib(
                spConfig,
                this,
                new LocalClient()
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        spConsentLib.loadMessage();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        spConsentLib.dispose();
    }

    class LocalClient implements SpClient {
        @Override
        public void onMessageReady(@NotNull JSONObject message) { /* ... */ }
        @Override
        public void onError(@NotNull Throwable error){ /* ... */ }
        @Override
        public void onConsentReady(@NotNull SPConsents c){ /* ... */ }
        @Override
        public void onAction(@NotNull View view, @NotNull ActionType actionType) { /* ... */ }
        @Override
        public void onUIFinished(@NotNull View v) {
            spConsentLib.removeView(v);
        }
        @Override
        public void onUIReady(@NotNull View v) {
            spConsentLib.showView(v);
        }
    }
```
