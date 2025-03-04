package com.sourcepoint.app.v6

import android.webkit.CookieManager
import com.example.uitestutil.*
import com.sourcepoint.app.v6.TestData.ACCEPT
import com.sourcepoint.app.v6.TestData.ACCEPT_ALL
import com.sourcepoint.app.v6.TestData.CANCEL
import com.sourcepoint.app.v6.TestData.CCPA_CONSENT_LIST
import com.sourcepoint.app.v6.TestData.CONSENT_LIST
import com.sourcepoint.app.v6.TestData.CONSENT_LIST_2
import com.sourcepoint.app.v6.TestData.FEATURES
import com.sourcepoint.app.v6.TestData.GDPR_CONSENT_LIST_2
import com.sourcepoint.app.v6.TestData.MESSAGE
import com.sourcepoint.app.v6.TestData.NETWORK
import com.sourcepoint.app.v6.TestData.OPTIONS
import com.sourcepoint.app.v6.TestData.PARTIAL_CONSENT_LIST
import com.sourcepoint.app.v6.TestData.PRIVACY_MANAGER
import com.sourcepoint.app.v6.TestData.PURPOSES
import com.sourcepoint.app.v6.TestData.REJECT
import com.sourcepoint.app.v6.TestData.REJECT_ALL
import com.sourcepoint.app.v6.TestData.SAVE_AND_EXIT
import com.sourcepoint.app.v6.TestData.SETTINGS_DE
import com.sourcepoint.app.v6.TestData.SITE_VENDORS
import com.sourcepoint.app.v6.TestData.TITLE_GDPR
import com.sourcepoint.app.v6.TestData.VENDORS_LIST
import com.sourcepoint.app.v6.TestData.VENDORS_LIST_2
import com.sourcepoint.app.v6.core.DataProvider
import com.sourcepoint.app.v6.di.customCategoriesDataProd
import com.sourcepoint.app.v6.di.customVendorDataListProd
import com.sourcepoint.cmplibrary.SpClient
import com.sourcepoint.cmplibrary.model.exposed.SpConfig
import kotlinx.android.synthetic.main.activity_main_consent.*
import kotlinx.android.synthetic.main.activity_main_v7.*
import org.koin.core.module.Module
import org.koin.dsl.module

class TestUseCase {

    companion object {

        fun checkConsentIsNotSelected() {
            CONSENT_LIST.forEach { consent ->
                checkConsentState(consent, false)
            }
        }

        fun checkConsentIsSelected() {
            CONSENT_LIST.forEach { consent ->
                checkConsentState(consent, true)
            }
        }

        fun tapToEnableAllConsent() {
            CONSENT_LIST_2.forEach { consent ->
                tapOnToggle(property = consent, tapOnlyWhen = false)
            }
        }

        fun tapAllConsent() {
            CONSENT_LIST_2.forEach { consent ->
                tapOnToggle(property = consent)
            }
        }

        fun tapToDisableAllConsent() {
            CONSENT_LIST_2.forEach { consent ->
                tapOnToggle2(property = consent, tapOnlyWhen = true)
            }
        }

        fun checkAllConsentsOn() {
            CONSENT_LIST_2.forEach { consent ->
                checkConsentState(consent, true, "tcfv2-stack")
            }
        }

        fun checkAllConsentsOff() {
            CONSENT_LIST_2.forEach { consent ->
                checkConsentState(consent, false, "tcfv2-stack")
            }
        }

        fun checkAllCcpaConsentsOn() {
            CCPA_CONSENT_LIST.forEach { consent ->
                checkConsentState(consent, true, "ccpa-stack")
            }
        }

        fun checkCustomCategoriesData() {
            // the customCategoriesData elements are enabled
            customCategoriesDataProd.map { it.second }.forEach { consent ->
                checkConsentState(consent, true, "tcfv2-stack")
            }
            // all CONSENT_LIST_2 elements are disabled except the customCategoriesData
            CONSENT_LIST_2.subtract(customCategoriesDataProd.map { it.second }).forEach { consent ->
                checkConsentState(consent, false, "tcfv2-stack")
            }
        }

        fun checkDeletedCustomCategoriesData() {
            // the customCategoriesData elements are enabled
            customCategoriesDataProd.map { it.second }.forEach { consent ->
                checkConsentState(consent, false, "tcfv2-stack")
            }
            // all CONSENT_LIST_2 elements are disabled except the customCategoriesData
            CONSENT_LIST_2.subtract(customCategoriesDataProd.map { it.second }).forEach { consent ->
                checkConsentState(consent, false, "tcfv2-stack")
            }
        }

        fun checkCustomVendorDataList() {
            // the customVendorDataList elements are enabled
            customVendorDataListProd.map { it.second }.forEach { consent ->
                checkConsentStateVendor(consent, true, "tcfv2-stack")
            }
            // all CONSENT_LIST_2 elements are disabled except the customCategoriesData
            VENDORS_LIST.subtract(customVendorDataListProd.map { it.second }).forEach { consent ->
                checkConsentStateVendor(consent, false, "tcfv2-stack")
            }
        }

        fun checkDeletedCustomVendorDataList() {
            // the customVendorDataList elements are enabled
            customVendorDataListProd.map { it.second }.forEach { consent ->
                checkConsentStateVendor(consent, false, "tcfv2-stack")
            }
//            // all CONSENT_LIST_2 elements are disabled except the customCategoriesData
            VENDORS_LIST.subtract(customVendorDataListProd.map { it.second }).forEach { consent ->
                checkConsentStateVendor(consent, false, "tcfv2-stack")
            }
        }

        fun checkMainWebViewDisplayed() {
            isDisplayedAllOfByResId(resId = R.id.review_consents_gdpr)
        }

        fun checkDeepLinkDisplayed() {
            isDisplayedAllOfByResId(resId = R.id.app_dl_tv)
        }

        fun clickOnGdprReviewConsent() {
            performClickById(resId = R.id.review_consents_gdpr)
        }

        fun checkAllGdprConsentsOn() {
            GDPR_CONSENT_LIST_2.forEach { consent ->
                checkConsentState(consent, true, "tcfv2-stack")
            }
        }

        fun clickOnClearConsent() {
            performClickById(resId = R.id.clear_all)
        }

        fun checkGdprNativeTitle() {
            isDisplayedByResIdByText(resId = R.id.title_nm, text = "GDPR Lorem Ipsum")
        }

        fun tapNmDismiss() {
            performClickById(R.id.cancel)
        }

        fun checkCcpaNativeTitle() {
            isDisplayedByResIdByText(resId = R.id.title_nm, text = "CCPA Lorem Ipsum")
        }

        fun tapNmAcceptAll() {
            performClickById(R.id.accept_all)
        }

        fun clickOnCustomConsent() {
            performClickById(resId = R.id.custom_consent)
        }

        fun clickOnDeleteCustomConsent() {
            performClickById(resId = R.id.delete_custom_consent)
        }

        fun clickOnCcpaReviewConsent() {
            performClickById(resId = R.id.review_consents_ccpa)
        }

        fun clickOnConsentActivity() {
            performClickById(resId = R.id.consent_btn)
        }

        fun clickOnRefreshBtnActivity() {
            performClickById(resId = R.id.refresh_btn)
        }

        fun openAuthIdActivity() {
            performClickByIdCompletelyDisplayed(resId = R.id.auth_id_activity)
        }

        fun checkAuthIdIsDisplayed(autId : String) {
            checkElementWithText("authId", autId)
        }

        fun checkEuconsent(euconsent : String) {
            containsText(R.id.consent_uuid, euconsent)
        }

        fun checkGdprApplies(value : String) {
            containsText(R.id.gdpr_applies, value)
        }

        fun checkAuthIdIsNotDisplayed() {
            checkElementWithText("authId", "no_auth_id")
        }

        fun checkWebViewDisplayedForMessage() {
            checkWebViewHasText(MESSAGE)
        }

        fun clickPMTabSelectedPurposes() {
            performClickPMTabSelected(PURPOSES)
        }

        fun clickPMTabSelectedFeatures() {
            performClickPMTabSelected(FEATURES)
        }

        fun tapOptionWebView() {
            performClickOnWebViewByContent(OPTIONS)
        }

        fun tapCancelOnWebView() {
            performClickOnWebViewByContent(CANCEL)
        }

        fun checkWebViewDisplayedGDPRFirstLayerMessage() {
            checkTextInParagraph(TITLE_GDPR)
        }

        fun tapPartnersOnWebView() {
            performClickOnLabelWebViewByContent("Partners")
        }

        fun checkAllVendorsOff() {
            VENDORS_LIST_2.forEach { consent ->
                checkConsentStateVendor(consent, false, "tcfv2-stack")
            }
        }

        fun tapFeaturesOnWebView() {
            performClickOnLabelWebViewByContent("Features")
        }

        fun tapPurposesOnWebView() {
            performClickOnLabelWebViewByContent("Purposes")
        }

        fun checkFeaturesTab() {
            checkTextInParagraph("Features are a use of the data that you have already agreed to share with us")
        }

        fun checkPurposesTab() {
            checkTextInParagraph("You give an affirmative action to indicate that we can use your data for this purpose.")
        }

        fun tapSiteVendorsWebView() {
            performClickPMTabSelected(SITE_VENDORS)
        }

        fun tapAcceptAllOnWebView() {
            performClickOnWebViewByContent(ACCEPT_ALL)
        }

        fun tapNetworkOnWebView() {
            performClickOnLabelWebViewByContent(NETWORK)
        }

        fun tapRejectOnWebView() {
            performClickOnWebViewByContent(REJECT)
        }

        fun tapSaveAndExitWebView() {
            performClickOnWebViewByContent(SAVE_AND_EXIT)
        }

        fun tapRejectAllWebView() {
            performClickOnWebViewByContent(REJECT_ALL)
        }

        fun tapDismissWebView() {
            performClickOnWebViewByClass("message-stacksclose")
        }

        fun tapAcceptOnWebView() {
            performClickOnWebViewByContent(ACCEPT)
        }

        fun tapSettingsOnWebViewDE() {
            performClickOnWebViewByContent(SETTINGS_DE)
        }

        fun tapAcceptCcpaOnWebView() {
            performClickOnWebViewByContent(ACCEPT_ALL)
        }

        fun setFocusOnLayoutActivity() {
            performClickByIdCompletelyDisplayed(resId = R.id.main_view)
        }

        fun checkWebViewDisplayedForPrivacyManager() {
            checkWebViewHasText(PRIVACY_MANAGER)
        }

        fun checkPartialConsentIsSelected() {
            PARTIAL_CONSENT_LIST.forEach { consent ->
                checkConsentState(consent, true)
            }
        }

        fun checkPartialConsentIsNotSelected() {
            PARTIAL_CONSENT_LIST.forEach { consent ->
                checkConsentState(consent, false)
            }
        }

        fun checkConsentAsSelectedFromPartialConsentList() {
            PARTIAL_CONSENT_LIST.forEach { consent ->
                tapOnToggle(consent)
            }
        }
        fun checkConsentAsSelectedConsentList() {
            CONSENT_LIST.forEach { consent ->
                tapOnToggle(consent)
            }
        }

        fun setConsent() {
            CONSENT_LIST.forEach { consent ->
                tapOnToggle(consent)
            }
        }

        fun checkPMTabSelectedFeatures() {
            checkPMTabSelected(FEATURES)
        }

        fun checkPMTabSelectedPurposes() {
            checkPMTabSelected(PURPOSES)
        }

        fun selectPartialConsentList() {
            PARTIAL_CONSENT_LIST.forEach { consent ->
                checkConsentWebView(consent)
            }
        }

        fun checkCookieExist(url : String, value : String){
            CookieManager.getInstance()
                .getCookie(url)
                .contains(value)
                .assertTrue()
        }

        fun checkCookieNotExist(url : String){
            CookieManager.getInstance()
                .getCookie(url)
                .contains("authId=")
                .assertFalse()
        }

        fun mockModule(
            spConfig: SpConfig,
            gdprPmId: String,
            ccpaPmId: String = "",
            pAuthId: String? = null,
            url: String = "",
            pResetAll: Boolean = true,
            pStoreStateGdpr: Boolean = false,
            pStoreStateCcpa: Boolean = false,
            spClientObserver: List<SpClient> = emptyList()
        ): Module {
            return module(override = true) {
                single<List<SpClient?>> { spClientObserver }
                single<DataProvider> {
                    object : DataProvider {
                        override val authId = pAuthId
                        override val resetAll = pResetAll
                        override val storeStateGdpr: Boolean = pStoreStateGdpr
                        override val storeStateCcpa: Boolean = pStoreStateCcpa
                        override val url = url
                        override val spConfig: SpConfig = spConfig
                        override val gdprPmId: String = gdprPmId
                        override val ccpaPmId: String = ccpaPmId
                        override val customVendorList: List<String> = customVendorDataListProd.map { it.first }
                        override val customCategories: List<String> = customCategoriesDataProd.map { it.first }
                    }
                }
            }
        }
    }
}