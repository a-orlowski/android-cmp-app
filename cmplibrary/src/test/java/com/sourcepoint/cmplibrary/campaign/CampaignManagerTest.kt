package com.sourcepoint.cmplibrary.campaign

import com.sourcepoint.cmplibrary.assertEquals
import com.sourcepoint.cmplibrary.assertFalse
import com.sourcepoint.cmplibrary.assertNotNull
import com.sourcepoint.cmplibrary.assertTrue
import com.sourcepoint.cmplibrary.core.Either
import com.sourcepoint.cmplibrary.core.getOrNull
import com.sourcepoint.cmplibrary.data.local.DataStorage
import com.sourcepoint.cmplibrary.data.network.model.toUnifiedMessageRespDto
import com.sourcepoint.cmplibrary.exception.CampaignType
import com.sourcepoint.cmplibrary.model.* //ktlint-disable
import com.sourcepoint.cmplibrary.model.exposed.* //ktlint-disable
import com.sourcepoint.cmplibrary.util.file2String
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.verify
import org.json.JSONObject
import org.junit.Before
import org.junit.Test

class CampaignManagerTest {

    @MockK
    private lateinit var dataStorage: DataStorage

    @MockK
    private lateinit var gdprConsent: GDPRConsentInternal

    @MockK
    private lateinit var ccpaConsent: CCPAConsentInternal

    @MockK
    private lateinit var gdpr: CampaignTemplate

    @MockK
    private lateinit var ccpa: CampaignTemplate

    private val gdprCampaign = SpCampaign(
        CampaignType.GDPR,
        listOf(TargetingParam("location", "EU"))
    )

    private val ccpaCamapign = SpCampaign(
        CampaignType.CCPA,
        listOf(TargetingParam("location", "EU"))
    )

    private val spConfig = SpConfig(
        22,
        "carm.uw.con",
        listOf(
            ccpaCamapign,
            gdprCampaign
        ),
        MessageLanguage.ENGLISH,
        propertyId = 9090,
        messageTimeout = 3000,
    )

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true, relaxed = true)
        every { gdprConsent.thisContent }.returns(JSONObject())
        sut.clearConsents()
    }

    private val sut by lazy { CampaignManager.create(dataStorage, spConfig) }

    @Test
    fun `CHECK that getGDPRConsent RETURNS a GDPRConsent from the dataStorage`() {
        val unifiedResp = "unified_wrapper_resp/response_gdpr_and_ccpa.json".file2String().toUnifiedMessageRespDto()
        val gdprTest = unifiedResp.campaigns.find { it is Gdpr } as Gdpr

        every { dataStorage.getGdprConsentResp() }.returns(gdprTest.userConsent.thisContent.toString())

        (sut.getGDPRConsent() as? Either.Right).assertNotNull()

        verify(exactly = 0) { dataStorage.saveGdprConsentResp(any()) }
    }

    @Test
    fun `CHECK that getCCPAConsent RETURNS a CCPAConsent from the dataStorage`() {
        val unifiedResp = "unified_wrapper_resp/response_gdpr_and_ccpa.json".file2String().toUnifiedMessageRespDto()
        val ccpaTest = unifiedResp.campaigns.find { it is Ccpa } as Ccpa

        every { dataStorage.getCcpaConsentResp() }.returns(ccpaTest.userConsent.thisContent.toString())

        (sut.getCCPAConsent() as? Either.Right).assertNotNull()

        verify(exactly = 1) { dataStorage.getCcpaConsentResp() }
        verify(exactly = 0) { dataStorage.saveCcpaConsentResp(any()) }
    }

    @Test
    fun `GIVEN a gdpr applied campaign RETURN a gdpr type`() {
        every { dataStorage.getGdprMessage() }.returns("GDPR")
        sut.run {
            addCampaign(CampaignType.CCPA, ccpa)
            addCampaign(CampaignType.GDPR, gdpr)
        }
        val pairOutput = sut.getAppliedCampaign()

        (pairOutput as Either.Right).r.first.assertEquals(CampaignType.GDPR)
    }

    @Test
    fun `GIVEN a ccpa applied campaign RETURN a ccpa type`() {
        every { dataStorage.getCcpaMessage() }.returns("CCPA")
        sut.run {
            addCampaign(CampaignType.CCPA, ccpa)
            addCampaign(CampaignType.GDPR, gdpr)
        }
        val pairOutput = sut.getAppliedCampaign()

        (pairOutput as Either.Right).r.first.assertEquals(CampaignType.CCPA)
    }

    @Test
    fun `GIVEN a gdpr applied campaign RETURN true passing GPDR as parameter`() {
        every { dataStorage.getGdprMessage() }.returns("GDPR")
        sut.run {
            addCampaign(CampaignType.CCPA, ccpa)
            addCampaign(CampaignType.GDPR, gdpr)
        }
        sut.isAppliedCampaign(CampaignType.GDPR).assertTrue()
        sut.isAppliedCampaign(CampaignType.CCPA).assertFalse()
    }

    @Test
    fun `GIVEN a ccpa applied campaign RETURN true passing CCPA as parameter`() {
        every { dataStorage.getCcpaMessage() }.returns("CCPA")
        sut.run {
            addCampaign(CampaignType.CCPA, ccpa)
            addCampaign(CampaignType.GDPR, gdpr)
        }
        sut.isAppliedCampaign(CampaignType.CCPA).assertTrue()
        sut.isAppliedCampaign(CampaignType.GDPR).assertFalse()
    }

    @Test
    fun `GIVEN a GDPR config RETURN the configuration with language EN`() {
        val sut = CampaignManager.create(
            dataStorage,
            spConfig.copy(messageLanguage = MessageLanguage.ENGLISH)
        )
        val config = sut.getPmConfig(CampaignType.GDPR, "11", PMTab.DEFAULT).getOrNull().assertNotNull()!!
        config.run {
            pmTab.assertEquals(PMTab.DEFAULT)
            consentLanguage.assertEquals("EN")
            uuid.assertEquals("")
            siteId.assertEquals("9090")
            messageId.assertEquals("11")
        }
    }

    @Test
    fun `GIVEN a GDPR config RETURN the configuration with language BG`() {
        val sut = CampaignManager.create(
            dataStorage,
            spConfig.copy(messageLanguage = MessageLanguage.BULGARIAN)
        )
        val config = sut.getPmConfig(CampaignType.GDPR, "22", PMTab.PURPOSES).getOrNull().assertNotNull()!!

        config.run {
            pmTab.assertEquals(PMTab.PURPOSES)
            consentLanguage.assertEquals("BG")
            uuid.assertEquals("")
            siteId.assertEquals("9090")
            messageId.assertEquals("22")
        }
    }

    @Test
    fun `GIVEN a GDPR config RETURN the configuration with language ES`() {

        every { dataStorage.gdprConsentUuid }.returns("uuid-test")

        val sut = CampaignManager.create(
            dataStorage,
            spConfig.copy(messageLanguage = MessageLanguage.SPANISH)
        )

        val config = sut.getPmConfig(
            campaignType = CampaignType.GDPR,
            pmId = "22",
            pmTab = PMTab.PURPOSES,
            useGroupPmIfAvailable = true,
            groupPmId = null
        ).getOrNull().assertNotNull()!!

        config.run {
            pmTab.assertEquals(PMTab.PURPOSES)
            consentLanguage.assertEquals("ES")
            uuid.assertEquals("uuid-test")
            siteId.assertEquals("9090")
            messageId.assertEquals("22")
        }
    }

    @Test
    fun `GIVEN a GDPR config RETURN the configuration with language NL and groupPmId not empty`() {

        every { dataStorage.gdprConsentUuid }.returns("uuid")
        every { dataStorage.gdprChildPmId }.returns("8989")

        val sut = CampaignManager.create(
            dataStorage,
            spConfig.copy(messageLanguage = MessageLanguage.DUTCH)
        )

        val config = sut.getPmConfig(
            campaignType = CampaignType.GDPR,
            pmId = "22",
            pmTab = PMTab.PURPOSES,
            useGroupPmIfAvailable = true,
            groupPmId = "111"
        ).getOrNull().assertNotNull()!!

        config.run {
            pmTab.assertEquals(PMTab.PURPOSES)
            consentLanguage.assertEquals("NL")
            uuid.assertEquals("uuid")
            siteId.assertEquals("9090")
            messageId.assertEquals("8989")
        }
    }
}
