package com.sourcepoint.cmplibrary.campaign

import com.example.cmplibrary.BuildConfig
import com.sourcepoint.cmplibrary.campaign.CampaignManager.Companion.selectPmId
import com.sourcepoint.cmplibrary.core.Either
import com.sourcepoint.cmplibrary.core.getOrNull
import com.sourcepoint.cmplibrary.core.map
import com.sourcepoint.cmplibrary.creation.validPattern
import com.sourcepoint.cmplibrary.data.local.DataStorage
import com.sourcepoint.cmplibrary.data.local.DataStorage.Companion.LOCAL_STATE
import com.sourcepoint.cmplibrary.data.local.DataStorage.Companion.LOCAL_STATE_OLD
import com.sourcepoint.cmplibrary.data.local.DataStorageGdpr.Companion.KEY_GDPR_APPLIES_OLD
import com.sourcepoint.cmplibrary.data.local.DataStorageGdpr.Companion.KEY_GDPR_MESSAGE_SUBCATEGORY_OLD
import com.sourcepoint.cmplibrary.data.local.getCCPAConsent
import com.sourcepoint.cmplibrary.data.local.getGDPRConsent
import com.sourcepoint.cmplibrary.data.network.converter.JsonConverter
import com.sourcepoint.cmplibrary.data.network.converter.converter
import com.sourcepoint.cmplibrary.data.network.converter.fail
import com.sourcepoint.cmplibrary.data.network.model.optimized.* //ktlint-disable
import com.sourcepoint.cmplibrary.data.network.model.toCCPA
import com.sourcepoint.cmplibrary.data.network.model.toGDPR
import com.sourcepoint.cmplibrary.data.network.util.CampaignsEnv
import com.sourcepoint.cmplibrary.data.network.util.Env
import com.sourcepoint.cmplibrary.exception.* //ktlint-disable
import com.sourcepoint.cmplibrary.model.* //ktlint-disable
import com.sourcepoint.cmplibrary.model.Campaigns
import com.sourcepoint.cmplibrary.model.exposed.* //ktlint-disable
import com.sourcepoint.cmplibrary.model.exposed.CCPAConsentInternal
import com.sourcepoint.cmplibrary.model.exposed.GDPRConsentInternal
import com.sourcepoint.cmplibrary.util.check
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.json.JSONObject
import java.time.Instant

internal interface CampaignManager {

    val spConfig: SpConfig
    val messageLanguage: MessageLanguage
    val campaigns4Config: List<CampaignReq>
    val ccpaMessageSubCategory: MessageSubCategory
    val gdprMessageSubCategory: MessageSubCategory
    fun addCampaign(campaignType: CampaignType, campaign: CampaignTemplate)

    fun isAppliedCampaign(campaignType: CampaignType): Boolean
    fun getMessSubCategoryByCamp(campaignType: CampaignType): MessageSubCategory
    fun getUnifiedMessageResp(): Either<UnifiedMessageResp>

    fun getGdpr(): Either<Gdpr>
    fun getCcpa(): Either<Ccpa>
    fun getAppliedCampaign(): Either<Pair<CampaignType, CampaignTemplate>>
    fun getCampaignTemplate(campaignType: CampaignType): Either<CampaignTemplate>

    fun getPmConfig(
        campaignType: CampaignType,
        pmId: String?,
        pmTab: PMTab?,
        useGroupPmIfAvailable: Boolean,
        groupPmId: String?
    ): Either<PmUrlConfig>

    fun getPmConfig(
        campaignType: CampaignType,
        pmId: String?,
        pmTab: PMTab?
    ): Either<PmUrlConfig>

    fun getUnifiedMessageReq(authId: String?, pubData: JSONObject?): UnifiedMessageRequest
    fun getMessageOptimizedReq(authId: String?, pubData: JSONObject?): MessagesParamReq

    fun getGDPRConsent(): Either<GDPRConsentInternal>
    fun getCCPAConsent(): Either<CCPAConsentInternal>

    fun getGroupId(campaignType: CampaignType): String?

    fun saveGdpr(gdpr: Gdpr)
    fun saveCcpa(ccpa: Ccpa)
    fun saveUnifiedMessageResp(unifiedMessageResp: UnifiedMessageResp)

    fun parseRenderingMessage()

    fun clearConsents()

    // Optimized
    val shouldCallMessages: Boolean
    val shouldCallConsentStatus: Boolean
    var gdprMessageMetaData: MessageMetaData?
    var ccpaMessageMetaData: MessageMetaData?

    // Consent Status
    fun saveConsentStatusResponse(c: ConsentStatusResp)
    var consentStatus: ConsentStatus?
    var gdprConsentStatus: GdprCS?
    var ccpaConsentStatus: CcpaCS?
    var messagesOptimizedLocalState: JsonElement?
    var gdprUuid: String?
    var ccpaUuid: String?
    val hasLocalData: Boolean

    // dateCreated
    var gdprDateCreated: String?
    var ccpaDateCreated: String?

//    var consentStatus: ConsentStatus?zvz
//    var ccpaStatus: CcpaStatus?

    var metaDataResp: MetaDataResp?
    var pvDataResp: PvDataResp?
    var choiceResp: ChoiceResp?
    var dataRecordedConsent: Instant?
    var authId: String?

    fun handleMetaDataLogic(md: MetaDataResp?)
    fun handleOldLocalData()
    fun getGdprChoiceBody(): JsonObject
    fun getCcpaChoiceBody(): JsonObject
    fun getGdprPvDataBody(messageReq: MessagesParamReq): JsonObject
    fun getCcpaPvDataBody(messageReq: MessagesParamReq): JsonObject

    companion object {
        fun selectPmId(userPmId: String?, childPmId: String?, useGroupPmIfAvailable: Boolean): String {
            return when {
                useGroupPmIfAvailable && !childPmId.isNullOrEmpty() && childPmId.isNotBlank() -> childPmId
                else -> userPmId ?: ""
            }
        }
    }
}

internal fun CampaignManager.Companion.create(
    dataStorage: DataStorage,
    spConfig: SpConfig
): CampaignManager = CampaignManagerImpl(dataStorage, spConfig)

private class CampaignManagerImpl(
    val dataStorage: DataStorage,
    override val spConfig: SpConfig
) : CampaignManager {

    override val messageLanguage: MessageLanguage = spConfig.messageLanguage

    private val mapTemplate = mutableMapOf<String, CampaignTemplate>()
    private val campaignsEnv: CampaignsEnv = spConfig.campaignsEnv
    val logger: Logger? = spConfig.logger

    init {
        if (!spConfig.propertyName.contains(validPattern)) {
            throw InvalidArgumentException(
                description = """
                PropertyName can only include letters, numbers, '.', ':', '-' and '/'. (string) passed is invalid
                """.trimIndent()
            )
        }
        spConfig.also { spp ->
            spp.campaigns.forEach {
                when (it.campaignType) {
                    CampaignType.GDPR -> {
                        val ce: CampaignsEnv = it.targetingParams
                            .find { c -> c.key == "campaignEnv" }
                            ?.let { env ->
                                CampaignsEnv.values().find { t -> t.env == env.value }
                            } ?: CampaignsEnv.PUBLIC
                        addCampaign(
                            it.campaignType,
                            CampaignTemplate(
                                ce,
                                it.targetingParams.filter { tp -> tp.key != "campaignEnv" },
                                it.campaignType,
                                it.groupPmId
                            )
                        )
                    }

                    CampaignType.CCPA -> {
                        val ce: CampaignsEnv = it.targetingParams
                            .find { c -> c.key == "campaignEnv" }
                            ?.let { env ->
                                CampaignsEnv.values().find { t -> t.env == env.value }
                            } ?: CampaignsEnv.PUBLIC
                        addCampaign(
                            it.campaignType,
                            CampaignTemplate(
                                ce,
                                it.targetingParams.filter { tp -> tp.key != "campaignEnv" },
                                it.campaignType,
                                it.groupPmId
                            )
                        )
                    }
                }
            }
        }
    }

    override val campaigns4Config: List<CampaignReq>
        get() {
            val campaigns = mutableListOf<CampaignReq>()
            mapTemplate[CampaignType.GDPR.name]
                ?.let {
                    it.toCampaignReqImpl(
                        targetingParams = it.targetingParams,
                        campaignsEnv = it.campaignsEnv,
                        groupPmId = it.groupPmId
                    )
                }
                ?.let { campaigns.add(it) }
            mapTemplate[CampaignType.CCPA.name]
                ?.let {
                    it.toCampaignReqImpl(
                        targetingParams = it.targetingParams,
                        campaignsEnv = it.campaignsEnv
                    )
                }
                ?.let { campaigns.add(it) }
            return campaigns
        }

    override val ccpaMessageSubCategory: MessageSubCategory
        get() = ccpaMessageMetaData?.subCategoryId ?: MessageSubCategory.TCFv2

    override val gdprMessageSubCategory: MessageSubCategory
        get() = gdprMessageMetaData?.subCategoryId ?: MessageSubCategory.TCFv2

    override fun addCampaign(campaignType: CampaignType, campaign: CampaignTemplate) {
        mapTemplate[campaignType.name] = campaign
    }

    override fun getGdpr(): Either<Gdpr> = check {
        dataStorage.getGdpr()?.toGDPR(dataStorage.gdprConsentUuid) ?: fail("GDPR is not stored in memory!!!")
    }

    override fun getCcpa(): Either<Ccpa> = check {
        dataStorage.getCcpa()?.toCCPA(dataStorage.ccpaConsentUuid) ?: fail("CCPA is not stored in memory!!!")
    }

    override fun getCampaignTemplate(campaignType: CampaignType): Either<CampaignTemplate> = check {
        mapTemplate[campaignType.name] ?: fail("${campaignType.name} Campain is missing!!!")
    }

    override fun getPmConfig(
        campaignType: CampaignType,
        pmId: String?,
        pmTab: PMTab?,
        useGroupPmIfAvailable: Boolean,
        groupPmId: String?
    ): Either<PmUrlConfig> {
        return when (campaignType) {
            CampaignType.GDPR -> getGdprPmConfig(pmId, pmTab ?: PMTab.PURPOSES, useGroupPmIfAvailable, groupPmId)
            CampaignType.CCPA -> getCcpaPmConfig(pmId)
        }
    }

    override fun getPmConfig(
        campaignType: CampaignType,
        pmId: String?,
        pmTab: PMTab?
    ): Either<PmUrlConfig> {
        return when (campaignType) {
            CampaignType.GDPR -> getGdprPmConfig(pmId, pmTab ?: PMTab.PURPOSES, false, null)
            CampaignType.CCPA -> getCcpaPmConfig(pmId)
        }
    }

    private fun getGdprPmConfig(
        pmId: String?,
        pmTab: PMTab,
        useGroupPmIfAvailable: Boolean,
        groupPmId: String?
    ): Either<PmUrlConfig> = check {
        val uuid = dataStorage.gdprConsentUuid
        val siteId = spConfig.propertyId.toString()

        val childPmId: String? = dataStorage.gdprChildPmId
        val isChildPmIdAbsent: Boolean = childPmId == null
        val hasGroupPmId: Boolean = groupPmId != null

        val usedPmId = selectPmId(pmId, childPmId, useGroupPmIfAvailable)

        if (hasGroupPmId && useGroupPmIfAvailable && isChildPmIdAbsent) {
            logger?.error(
                ChildPmIdNotFound(
                    description = """
                              childPmId not found!!!
                              GroupPmId[$groupPmId]
                              useGroupPmIfAvailable [true] 
                    """.trimIndent()
                )
            )
        }

        logger?.computation(
            tag = "Property group - GDPR PM",
            msg = """
                pmId[$pmId]
                childPmId[$childPmId]
                useGroupPmIfAvailable [$useGroupPmIfAvailable] 
                Query Parameter pmId[$usedPmId]
            """.trimIndent()
        )

        PmUrlConfig(
            pmTab = pmTab,
            consentLanguage = spConfig.messageLanguage.value,
            uuid = uuid,
            siteId = siteId,
            messageId = usedPmId
        )
    }

    private fun getCcpaPmConfig(pmId: String?): Either<PmUrlConfig> = check {
        val uuid = dataStorage.ccpaConsentUuid
        val siteId = spConfig.propertyId.toString()

        val childPmId: String? = dataStorage.ccpaChildPmId
        val isChildPmIdAbsent: Boolean = childPmId == null
        val hasGroupPmId = false // feature not yet implemented
        val useGroupPmIfAvailable = false // feature not yet implemented

        if (hasGroupPmId && useGroupPmIfAvailable && isChildPmIdAbsent) {
            logger?.error(
                ChildPmIdNotFound(
                    description = """
                              childPmId not found!!!
                              GroupPmId[groupPmId]
                              useGroupPmIfAvailable [true] 
                    """.trimIndent()
                )
            )
        }

        val usedPmId = childPmId ?: pmId

//        logger?.computation(
//            tag = "Property group - CCPA PM",
//            msg = "pmId[$pmId] - childPmId[$childPmId] -> used[$usedPmId]"
//        )

        PmUrlConfig(
            consentLanguage = spConfig.messageLanguage.value,
            uuid = uuid,
            siteId = siteId,
            messageId = usedPmId
        )
    }

    override fun isAppliedCampaign(campaignType: CampaignType): Boolean {
        return getAppliedCampaign()
            .map { it.first == campaignType }
            .getOrNull()
            ?: false
    }

    override fun getMessSubCategoryByCamp(campaignType: CampaignType): MessageSubCategory {
        return when (campaignType) {
            CampaignType.GDPR -> gdprMessageSubCategory
            CampaignType.CCPA -> ccpaMessageSubCategory
        }
    }

    override fun getUnifiedMessageResp(): Either<UnifiedMessageResp> = check {
        val campaigns = mutableListOf<CampaignResp>()
        getGdpr().map { campaigns.add(it) }
        getCcpa().map { campaigns.add(it) }
        val localState: String = dataStorage.getLocalState() ?: ""
        val propertyPriorityData: String? = dataStorage.getPropertyPriorityData()
        UnifiedMessageResp(
            campaigns = campaigns,
            localState = localState,
            propertyPriorityData = propertyPriorityData?.let { JSONObject(it) } ?: JSONObject(),
            thisContent = JSONObject(),
        )
    }

    override fun getAppliedCampaign(): Either<Pair<CampaignType, CampaignTemplate>> = check {
        when {
            dataStorage
                .getGdprMessage().isNotBlank() -> Pair(CampaignType.GDPR, mapTemplate[CampaignType.GDPR.name]!!)
            dataStorage
                .getCcpaMessage().isNotBlank() -> Pair(CampaignType.CCPA, mapTemplate[CampaignType.CCPA.name]!!)
            else -> throw MissingPropertyException(description = "Inconsistent Legislation!!!")
        }
    }

    override fun getUnifiedMessageReq(authId: String?, pubData: JSONObject?): UnifiedMessageRequest {
        val campaigns = mutableListOf<CampaignReq>()
        mapTemplate[CampaignType.GDPR.name]
            ?.let {
                it.toCampaignReqImpl(
                    targetingParams = it.targetingParams,
                    campaignsEnv = it.campaignsEnv,
                    groupPmId = it.groupPmId
                )
            }
            ?.let { campaigns.add(it) }
        mapTemplate[CampaignType.CCPA.name]
            ?.let {
                it.toCampaignReqImpl(
                    targetingParams = it.targetingParams,
                    campaignsEnv = it.campaignsEnv
                )
            }
            ?.let { campaigns.add(it) }

        val localState = dataStorage.getLocalState()

        return UnifiedMessageRequest(
            requestUUID = "test",
            propertyHref = spConfig.propertyName,
            accountId = spConfig.accountId,
            campaigns = Campaigns(list = campaigns),
            consentLanguage = messageLanguage,
            localState = localState,
            authId = authId,
            campaignsEnv = campaignsEnv,
            pubData = pubData
        )
    }

    override fun getMessageOptimizedReq(authId: String?, pubData: JSONObject?): MessagesParamReq {
        val campaigns = mutableListOf<CampaignReq>()
        mapTemplate[CampaignType.GDPR.name]
            ?.let {
                it.toCampaignReqImpl(
                    targetingParams = it.targetingParams,
                    campaignsEnv = it.campaignsEnv,
                    groupPmId = it.groupPmId
                )
            }
            ?.let { campaigns.add(it) }
        mapTemplate[CampaignType.CCPA.name]
            ?.let {
                it.toCampaignReqImpl(
                    targetingParams = it.targetingParams,
                    campaignsEnv = it.campaignsEnv
                )
            }
            ?.let { campaigns.add(it) }

        return MessagesParamReq(
            metadataArg = campaigns.toMetadataArgs(),
            nonKeyedLocalState = "",
            body = "",
            env = Env.values().find { it.name == BuildConfig.SDK_ENV } ?: Env.PROD,
            propertyHref = spConfig.propertyName,
            accountId = spConfig.accountId.toLong(),
            authId = authId,
            propertyId = spConfig.propertyId.toLong(),
            pubData = pubData?.toString()
                ?.let { check { JsonConverter.converter.decodeFromString<JsonObject>(it) }.getOrNull() }
                ?: JsonObject(mapOf())
        )
    }

    override fun getGDPRConsent(): Either<GDPRConsentInternal> = dataStorage.getGDPRConsent()

    override fun getCCPAConsent(): Either<CCPAConsentInternal> = dataStorage.getCCPAConsent()

    override fun getGroupId(campaignType: CampaignType): String? {
        return spConfig.campaigns.find { it.campaignType == campaignType }?.groupPmId
    }

    override fun saveGdpr(gdpr: Gdpr) {
        dataStorage.run {
            saveGdpr(gdpr.thisContent.toString())
            saveGdprConsentResp(gdpr.userConsent.thisContent.toString())
            gdprApplies = gdpr.applies
            gdprChildPmId = gdpr.userConsent.childPmId
        }
    }

    override fun saveCcpa(ccpa: Ccpa) {
        dataStorage.run {
            saveCcpa(ccpa.thisContent.toString())
            saveCcpaConsentResp(ccpa.userConsent.thisContent.toString())
            usPrivacyString = ccpa.userConsent.uspstring
            ccpaApplies = ccpa.applies
            ccpaChildPmId = ccpa.userConsent.childPmId
        }
    }

    override fun saveUnifiedMessageResp(unifiedMessageResp: UnifiedMessageResp) {
        dataStorage.saveLocalState(unifiedMessageResp.localState)
        val map = JSONObject(unifiedMessageResp.localState).toTreeMap()
        // save GDPR uuid
        map.getMap("gdpr")?.apply {
            getFieldValue<String>("uuid")?.let { dataStorage.gdprConsentUuid = it }
        }
        // save GDPR uuid
        map.getMap("ccpa")?.apply {
            getFieldValue<String>("uuid")?.let { dataStorage.ccpaConsentUuid = it }
        }

        // save campaigns and consents
        unifiedMessageResp
            .campaigns
            .forEach {
                when (it) {
                    is Gdpr -> saveGdpr(it)
                    is Ccpa -> saveCcpa(it)
                }
            }
    }

    override fun parseRenderingMessage() {
    }

    override fun clearConsents() {
        dataStorage.clearGdprConsent()
        dataStorage.clearCcpaConsent()
    }

    override var authId: String?
        get() = dataStorage.getAuthId()
        set(value) {
            dataStorage.saveAuthId(value)
        }

    // Optimized Implementation below

    val isNewUser: Boolean
        get() {
            val localStateSize = messagesOptimizedLocalState?.jsonObject?.size ?: 0
            return messagesOptimizedLocalState == null || localStateSize == 0 || (
                dataStorage.gdprConsentUuid == null &&
                    (ccpaConsentStatus?.newUser == null || ccpaConsentStatus?.newUser == true)
                )
        }

    override val shouldCallMessages: Boolean
        get() {

            val gdprToBeCompleted: Boolean = spConfig.campaigns.find { it.campaignType == CampaignType.GDPR }
                ?.let {
                    dataStorage.gdprApplies && (gdprConsentStatus?.consentStatus?.consentedAll != true)
                }
                ?: false

            val ccpaToBeCompleted: Boolean = spConfig.campaigns.find { it.campaignType == CampaignType.CCPA }
                ?.let { true }
                ?: false

            val res = (isNewUser || ccpaToBeCompleted || gdprToBeCompleted)

            logger?.computation(
                tag = "shouldCallMessages",
                msg = """
                isNewUser[$isNewUser]
                ccpaToBeCompleted[$ccpaToBeCompleted]
                gdprToBeCompleted[$gdprToBeCompleted]
                shouldCallMessages[$res]  
                """.trimIndent()
            )

            return res
        }

    override val shouldCallConsentStatus: Boolean
        get() {
            val gdprUUID = dataStorage.gdprConsentUuid
            val ccpaUUID = dataStorage.ccpaConsentUuid
            val localStateSize = messagesOptimizedLocalState?.jsonObject?.size ?: 0
            val isV6LocalStatePresent = dataStorage.preference.all.containsKey(LOCAL_STATE)
            val isV6LocalStatePresent2 = dataStorage.preference.all.containsKey(DataStorage.LOCAL_STATE_OLD)
            val res =
                ((gdprUUID != null || ccpaUUID != null) && localStateSize == 0) || isV6LocalStatePresent || isV6LocalStatePresent2

            logger?.computation(
                tag = "shouldCallConsentStatus",
                msg = """
                gdprUUID != null [${gdprUUID != null}] - ccpaUUID != null [${ccpaUUID != null}]
                localStateSize empty [${localStateSize == 0}]
                V6.7 ls [$isV6LocalStatePresent] or V6.3 ls [$isV6LocalStatePresent2]  
                shouldCallConsentStatus[$res]  
                """.trimIndent()
            )

            return res
        }

    override var gdprMessageMetaData: MessageMetaData?
        get() {
            return dataStorage.gdprMessageMetaData?.let { JsonConverter.converter.decodeFromString<MessageMetaData>(it) }
        }
        set(value) {
            val serialised = value?.let { JsonConverter.converter.encodeToString(value) }
            dataStorage.gdprMessageMetaData = serialised
        }

    override var ccpaMessageMetaData: MessageMetaData?
        get() {
            return dataStorage.ccpaMessageMetaData?.let { JsonConverter.converter.decodeFromString<MessageMetaData>(it) }
        }
        set(value) {
            val serialised = value?.let { JsonConverter.converter.encodeToString(value) }
            dataStorage.ccpaMessageMetaData = serialised
        }

    override fun saveConsentStatusResponse(c: ConsentStatusResp) {
        gdprConsentStatus = c.consentStatusData?.gdpr
        consentStatus = c.consentStatusData?.gdpr?.consentStatus
        ccpaConsentStatus = c.consentStatusData?.ccpa
        messagesOptimizedLocalState = c.localState
    }

    override var consentStatus: ConsentStatus?
        get() {
            return dataStorage.consentStatus?.let { JsonConverter.converter.decodeFromString<ConsentStatus>(it) }
        }
        set(value) {
            val serialised = value?.let { JsonConverter.converter.encodeToString(value) }
            dataStorage.consentStatus = serialised
        }

    override var gdprConsentStatus: GdprCS?
        get() {
            return dataStorage.gdprConsentStatus?.let { JsonConverter.converter.decodeFromString<GdprCS>(it) }
        }
        set(value) {
            val serialised = value?.let { JsonConverter.converter.encodeToString(value) }
            dataStorage.apply {
                value?.gdprApplies?.let {
                    gdprApplies = it
                }
                gdprConsentStatus = serialised
                value?.TCData
                    ?.let { this.tcData = it }
                    ?: run { clearTCData() }
            }
        }

    override var ccpaConsentStatus: CcpaCS?
        get() {
            return dataStorage.ccpaConsentStatus?.let { JsonConverter.converter.decodeFromString<CcpaCS>(it) }
        }
        set(value) {
            val serialised = value?.let { JsonConverter.converter.encodeToString(value) }
            dataStorage.apply {
                value?.ccpaApplies?.let {
                    ccpaApplies = it
                }
                ccpaConsentStatus = serialised
                usPrivacyString = value?.uspstring
            }
        }

    override var messagesOptimizedLocalState: JsonElement?
        get() {
            return dataStorage.messagesOptimizedLocalState?.let {
                JsonConverter.converter.decodeFromString<JsonElement>(
                    it
                )
            }
        }
        set(value) {
            val serialised = value?.let { JsonConverter.converter.encodeToString(value) }
            dataStorage.messagesOptimizedLocalState = serialised
        }

    override var gdprUuid: String?
        get() {
            return dataStorage.gdprConsentUuid
        }
        set(value) {
            dataStorage.gdprConsentUuid = value
        }

    override var ccpaUuid: String?
        get() {
            return dataStorage.ccpaConsentUuid
        }
        set(value) {
            dataStorage.ccpaConsentUuid = value
        }

    override val hasLocalData: Boolean
        get() = dataStorage.preference.all.containsKey(LOCAL_STATE) || dataStorage.preference.all.containsKey(
            LOCAL_STATE_OLD
        )

    override fun handleMetaDataLogic(md: MetaDataResp?) {
        metaDataResp = md
        md?.let {
            it.ccpa?.apply {
                applies?.let { i -> dataStorage.ccpaApplies = i }
                sampleRate?.let { i ->
                    if (i != dataStorage.ccpaSamplingValue) {
                        dataStorage.ccpaSamplingValue = i
                        dataStorage.ccpaSamplingResult = null
                    }
                }
            }
            it.gdpr?.apply {
                applies?.let { i -> dataStorage.gdprApplies = i }
                sampleRate?.let { i ->
                    if (i != dataStorage.gdprSamplingValue) {
                        dataStorage.gdprSamplingValue = i
                        dataStorage.gdprSamplingResult = null
                    }
                }
            }
        }
    }

    override fun handleOldLocalData() {
        if (dataStorage.preference.contains(DataStorage.LOCAL_STATE) || dataStorage.preference.contains(DataStorage.LOCAL_STATE_OLD)) {
            dataStorage.preference
                .edit().apply {
                    remove(LOCAL_STATE)
                    remove(LOCAL_STATE_OLD)
                    remove(KEY_GDPR_APPLIES_OLD)
                    remove(KEY_GDPR_MESSAGE_SUBCATEGORY_OLD)
                    remove("key_property_id")
                    remove("key_ccpa")
                    remove("key_gdpr")
                    remove("ccpa_consent_resp")
                    remove("gdpr_consent_resp")
                }
                .apply()
        }
    }

    override var gdprDateCreated: String?
        get() = dataStorage.gdprDateCreated
        set(value) {
            dataStorage.gdprDateCreated = value
        }

    override var ccpaDateCreated: String?
        get() = dataStorage.ccpaDateCreated
        set(value) {
            dataStorage.ccpaDateCreated = value
        }

    override var metaDataResp: MetaDataResp?
        get() {
            return dataStorage.metaDataResp?.let { JsonConverter.converter.decodeFromString<MetaDataResp>(it) }
        }
        set(value) {
            val serialised = value?.let { JsonConverter.converter.encodeToString(value) }
            dataStorage.metaDataResp = serialised
        }

    override var pvDataResp: PvDataResp?
        get() {
            return dataStorage.pvDataResp?.let { JsonConverter.converter.decodeFromString<PvDataResp>(it) }
        }
        set(value) {
            val serialised = value?.let { JsonConverter.converter.encodeToString(value) }
            dataStorage.pvDataResp = serialised
        }

    override var choiceResp: ChoiceResp?
        get() {
            return dataStorage.choiceResp?.let { JsonConverter.converter.decodeFromString<ChoiceResp>(it) }
        }
        set(value) {
            val serialised = value?.let { JsonConverter.converter.encodeToString(value) }
            dataStorage.pvDataResp = serialised
        }

    override var dataRecordedConsent: Instant?
        get() {
            return dataStorage.dataRecordedConsent?.let { Instant.parse(it) }
        }
        set(value) {
            dataStorage.dataRecordedConsent = value?.toString()
        }

    override fun getGdprChoiceBody(): JsonObject {
        return toGdprChoiceBody(
            accountId = spConfig.accountId,
            propertyId = spConfig.propertyId,
            gdprCs = gdprConsentStatus?.consentStatus,
            gdprMessageMetaData = gdprMessageMetaData,
            gdprApplies = dataStorage.gdprApplies,
            sampleRate = dataStorage.gdprSamplingValue
        )
    }

    override fun getCcpaChoiceBody(): JsonObject {
        return toCcpaChoiceBody(
            accountId = spConfig.accountId,
            propertyId = spConfig.propertyId,
            gdprCs = gdprConsentStatus?.consentStatus,
            gdprMessageMetaData = gdprMessageMetaData,
            gdprApplies = dataStorage.gdprApplies,
            sampleRate = dataStorage.gdprSamplingValue
        )
    }

    override fun getGdprPvDataBody(messageReq: MessagesParamReq): JsonObject {
        return toPvDataBody(
            accountId = messageReq.accountId,
            propertyId = messageReq.propertyId,
            gdprMessageMetaData = gdprMessageMetaData,
            ccpaMessageMetaData = ccpaMessageMetaData,
            gdprApplies = dataStorage.gdprApplies,
            ccpaApplies = dataStorage.ccpaApplies,
            pubData = messageReq.pubData,
            gdprCs = gdprConsentStatus?.consentStatus,
            ccpaCS = null,
        )
    }

    override fun getCcpaPvDataBody(messageReq: MessagesParamReq): JsonObject {
        return toPvDataBody(
            accountId = messageReq.accountId,
            propertyId = messageReq.propertyId,
            gdprMessageMetaData = gdprMessageMetaData,
            ccpaMessageMetaData = ccpaMessageMetaData,
            gdprApplies = dataStorage.gdprApplies,
            ccpaApplies = dataStorage.ccpaApplies,
            pubData = messageReq.pubData,
            gdprCs = null,
            ccpaCS = ccpaConsentStatus
        )
    }
}
