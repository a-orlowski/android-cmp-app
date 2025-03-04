package com.sourcepoint.cmplibrary.consent

import com.sourcepoint.cmplibrary.assertFalse
import com.sourcepoint.cmplibrary.assertNull
import com.sourcepoint.cmplibrary.assertTrue
import com.sourcepoint.cmplibrary.campaign.CampaignManager
import com.sourcepoint.cmplibrary.data.Service
import com.sourcepoint.cmplibrary.data.local.DataStorage
import com.sourcepoint.cmplibrary.data.network.converter.JsonConverter
import com.sourcepoint.cmplibrary.data.network.converter.converter
import com.sourcepoint.cmplibrary.data.network.model.optimized.ConsentStatus
import com.sourcepoint.cmplibrary.exception.Logger
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import kotlinx.serialization.decodeFromString
import org.junit.Before
import org.junit.Test

class ConsentManagerUtilsImplTest {

    @MockK
    private lateinit var campaignManager: CampaignManager

    @MockK
    private lateinit var service: Service

    @MockK
    private lateinit var logger: Logger

    @MockK
    private lateinit var dataStorage: DataStorage

    private val sut: ConsentManagerUtils by lazy {
        ConsentManagerUtils.create(campaignManager, dataStorage, logger, "")
    }

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true, relaxed = true)
    }

    @Test
    fun `GIVEN a recent dataRecordedConsent SET legalBasisChanges to true`() {
        val dataRecordedConsent = "2022-10-12T14:00:00.63Z"
        val additionsChangeDate = "2022-10-12T14:00:00.63Z"
        val legalBasisChangeDate = "2022-10-12T14:00:01.63Z"
        val gdprConsentStatus: ConsentStatus = JsonConverter.converter.decodeFromString(
            """
        {
            "rejectedAny": false,
            "rejectedLI": false,
            "consentedAll": true,
            "granularStatus": {
              "vendorConsent": "ALL",
              "vendorLegInt": "ALL",
              "purposeConsent": "ALL",
              "purposeLegInt": "ALL",
              "previousOptInAll": false,
              "defaultConsent": false
            },
            "hasConsentData": true,
            "consentedToAny": true
        }
            """.trimIndent()
        )
        sut
            .updateGdprConsentOptimized(dataRecordedConsent, gdprConsentStatus, additionsChangeDate, legalBasisChangeDate)
            .run {
                vendorListAdditions.assertNull()
                legalBasisChanges!!.assertTrue()
                granularStatus!!.previousOptInAll!!.assertTrue()
            }
    }

    @Test
    fun `GIVEN a recent dataRecordedConsent SET vendorListAdditions to true`() {
        val dataRecordedConsent = "2022-10-12T14:00:00.63Z"
        val additionsChangeDate = "2022-10-12T14:00:01.63Z"
        val legalBasisChangeDate = "2022-10-12T14:00:00.63Z"
        val gdprConsentStatus: ConsentStatus = JsonConverter.converter.decodeFromString(
            """
                {
                    "rejectedAny": false,
                    "rejectedLI": false,
                    "consentedAll": true,
                    "granularStatus": {
                      "vendorConsent": "ALL",
                      "vendorLegInt": "ALL",
                      "purposeConsent": "ALL",
                      "purposeLegInt": "ALL",
                      "previousOptInAll": false,
                      "defaultConsent": false
                    },
                    "hasConsentData": true,
                    "consentedToAny": true
                }
            """.trimIndent()
        )
        sut
            .updateGdprConsentOptimized(dataRecordedConsent, gdprConsentStatus, additionsChangeDate, legalBasisChangeDate)
            .run {
                vendorListAdditions!!.assertTrue()
                legalBasisChanges.assertNull()
                consentedAll!!.assertFalse()
                granularStatus!!.previousOptInAll!!.assertTrue()
            }
    }

    @Test
    fun `GIVEN an old dataRecordedConsent VERIFY that legalBasisChanges, vendorListAdditions and previousOptInAll aren't edited`() {
        val dataRecordedConsent = "2022-10-12T14:00:00.63Z"
        val additionsChangeDate = "2022-10-12T14:00:00.63Z"
        val legalBasisChangeDate = "2022-10-12T14:00:00.63Z"
        val gdprConsentStatus: ConsentStatus = JsonConverter.converter.decodeFromString(
            """
                {
                    "rejectedAny": false,
                    "rejectedLI": false,
                    "consentedAll": true,
                    "granularStatus": {
                      "vendorConsent": "ALL",
                      "vendorLegInt": "ALL",
                      "purposeConsent": "ALL",
                      "purposeLegInt": "ALL",
                      "previousOptInAll": false,
                      "defaultConsent": false
                    },
                    "hasConsentData": true,
                    "consentedToAny": true
                }
            """.trimIndent()
        )
        sut
            .updateGdprConsentOptimized(dataRecordedConsent, gdprConsentStatus, additionsChangeDate, legalBasisChangeDate)
            .run {
                vendorListAdditions.assertNull()
                legalBasisChanges.assertNull()
                granularStatus!!.previousOptInAll!!.assertFalse()
            }
    }
}
