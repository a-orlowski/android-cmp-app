package com.sourcepoint.cmplibrary.model

import com.sourcepoint.cmplibrary.data.network.model.toJsonObject
import com.sourcepoint.cmplibrary.exception.CampaignType
import com.sourcepoint.cmplibrary.model.exposed.SPCustomConsents
import org.json.JSONArray
import org.json.JSONObject

/**
 * REQUEST
 */

internal data class ConsentReq(
    val uuid: String,
    val choiceId: String,
    val consentLanguage: String,
    val meta: String,
    val propertyHref: String,
    val privacyManagerId: String,
    val requestUUID: String,
    val accountId: Int,
    val actionType: Int,
    val requestFromPM: Boolean,
    val pubData: JSONObject = JSONObject(),
    val pmSaveAndExitVariables: JSONObject = JSONObject(),
    val includeData: IncludeData = IncludeData()
)

internal fun ConsentReq.toBodyRequest(): String {
    return JSONObject()
        .apply {
            put("uuid", uuid)
            put("choiceId", choiceId)
            put("consentLanguage", consentLanguage)
            put("meta", meta)
            put("propertyHref", propertyHref)
            put("privacyManagerId", privacyManagerId)
            put("requestUUID", requestUUID)
            put("accountId", accountId)
            put("actionType", actionType)
            put("requestFromPM", requestFromPM)
            put("pubData", pubData)
            put("includeData", includeData.toJsonObject())
            put("pmSaveAndExitVariables", pmSaveAndExitVariables)
        }
        .toString()
}

internal class CustomConsentReq(
    val consentUUID: String,
    val propertyId: Int,
    val vendors: List<String>,
    val categories: List<String>,
    val legIntCategories: List<String>,
)

internal fun CustomConsentReq.toBodyRequest(): String {
    return JSONObject()
        .apply {
            put("consentUUID", consentUUID)
            put("propertyId", propertyId)
            put("vendors", JSONArray(vendors))
            put("categories", JSONArray(categories))
            put("legIntCategories", JSONArray(legIntCategories))
        }
        .toString()
}

internal fun CustomConsentReq.toBodyRequestDeleteCustomConsentTo(): String {
    return JSONObject()
        .apply {
            put("vendors", JSONArray(vendors))
            put("categories", JSONArray(categories))
            put("legIntCategories", JSONArray(legIntCategories))
        }
        .toString()
}

/**
 * RESPONSE
 */

internal data class CustomConsentResp(val content: JSONObject)

internal fun CustomConsentResp.toSpCustomConsent(): SPCustomConsents = SPCustomConsents(content)

internal data class ConsentResp(
    val content: JSONObject,
    val userConsent: String?,
    val uuid: String?,
    val localState: String,
    var campaignType: CampaignType? = null
)
