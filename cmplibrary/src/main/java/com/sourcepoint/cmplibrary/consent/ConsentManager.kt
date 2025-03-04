package com.sourcepoint.cmplibrary.consent

import com.sourcepoint.cmplibrary.core.Either.Left
import com.sourcepoint.cmplibrary.core.Either.Right
import com.sourcepoint.cmplibrary.core.ExecutorManager
import com.sourcepoint.cmplibrary.core.executeOnLeft
import com.sourcepoint.cmplibrary.core.executeOnRight
import com.sourcepoint.cmplibrary.core.getOrNull
import com.sourcepoint.cmplibrary.data.Service
import com.sourcepoint.cmplibrary.data.local.DataStorage
import com.sourcepoint.cmplibrary.data.network.model.optimized.* //ktlint-disable
import com.sourcepoint.cmplibrary.data.network.model.optimized.toGDPRUserConsent
import com.sourcepoint.cmplibrary.data.network.model.toCCPAUserConsent
import com.sourcepoint.cmplibrary.data.network.model.toGDPRUserConsent
import com.sourcepoint.cmplibrary.data.network.util.Env
import com.sourcepoint.cmplibrary.exception.CampaignType
import com.sourcepoint.cmplibrary.exception.Logger
import com.sourcepoint.cmplibrary.model.* //ktlint-disable
import com.sourcepoint.cmplibrary.model.ConsentActionImpl
import com.sourcepoint.cmplibrary.model.exposed.SPCCPAConsent
import com.sourcepoint.cmplibrary.model.exposed.SPConsents
import com.sourcepoint.cmplibrary.model.exposed.SPGDPRConsent
import com.sourcepoint.cmplibrary.model.getMap
import com.sourcepoint.cmplibrary.model.toTreeMap
import com.sourcepoint.cmplibrary.util.check
import java.util.* //ktlint-disable

internal interface ConsentManager {
    var localStateStatus: LocalStateStatus
    fun enqueueConsent(consentActionImpl: ConsentActionImpl) // to delete
    fun enqueueConsentOptimized(consentActionImpl: ConsentActionImpl)
    fun enqueueConsentOptimized(nativeConsentAction: NativeConsentAction)
    fun sendStoredConsentToClientOptimized()
    fun sendConsent(
        actionImpl: ConsentAction,
        localState: String
    )

    fun sendConsentOptimized(
        actionImpl: ConsentActionImpl
    )

    val enqueuedActions: Int
    val gdprUuid: String?
    val ccpaUuid: String?
    var sPConsentsSuccess: ((SPConsents) -> Unit)?
    var sPConsentsError: ((Throwable) -> Unit)?

    val storedConsent: Boolean

    companion object {

        internal fun responseConsentHandler(
            gdpr: GdprCS?,
            consentManagerUtils: ConsentManagerUtils
        ): SPConsents {
            val ccpaCached = consentManagerUtils.ccpaConsentOptimized.getOrNull()
            return SPConsents(
                gdpr = gdpr?.let { SPGDPRConsent(it.toGDPRUserConsent()) },
                ccpa = ccpaCached?.let { cc -> SPCCPAConsent(consent = cc) }
            )
        }

        internal fun responseConsentHandler(
            ccpa: CcpaCS?,
            consentManagerUtils: ConsentManagerUtils
        ): SPConsents {
            val gdprCached = consentManagerUtils.gdprConsentOptimized.getOrNull()
            return SPConsents(
                gdpr = gdprCached?.let { gc -> SPGDPRConsent(consent = gc) },
                ccpa = ccpa?.let { SPCCPAConsent(it.toCCPAConsentInternal()) },
            )
        }
    }
}

internal fun ConsentManager.Companion.create(
    service: Service,
    consentManagerUtils: ConsentManagerUtils,
    env: Env,
    logger: Logger,
    dataStorage: DataStorage,
    executorManager: ExecutorManager,
    clientEventManager: ClientEventManager
): ConsentManager =
    ConsentManagerImpl(service, consentManagerUtils, logger, env, dataStorage, executorManager, clientEventManager)

private class ConsentManagerImpl(
    private val service: Service,
    private val consentManagerUtils: ConsentManagerUtils,
    private val logger: Logger,
    private val env: Env,
    private val dataStorage: DataStorage,
    private val executorManager: ExecutorManager,
    private val clientEventManager: ClientEventManager
) : ConsentManager {

    override var sPConsentsSuccess: ((SPConsents) -> Unit)? = null
    override var sPConsentsError: ((Throwable) -> Unit)? = null
    override var localStateStatus: LocalStateStatus = LocalStateStatus.Absent
        set(value) {
            field = value
            changeLocalState(value)
        }
        get() = dataStorage.getLocalState()?.let { LocalStateStatus.Present(it) } ?: LocalStateStatus.Absent
    private val consentQueueImpl: Queue<ConsentAction> = LinkedList()
    override val enqueuedActions: Int
        get() = consentQueueImpl.size

    override val gdprUuid: String?
        get() = dataStorage.gdprConsentUuid

    override val ccpaUuid: String?
        get() = dataStorage.ccpaConsentUuid

    override val storedConsent: Boolean
        get() = dataStorage.getCcpaConsentResp() != null ||
            dataStorage.getGdprConsentResp() != null

    override fun enqueueConsent(consentActionImpl: ConsentActionImpl) {

        when (dataStorage.messagesOptimizedLocalState) {
            null -> {
                consentQueueImpl.offer(consentActionImpl)
                val lState: LocalStateStatus.Present? = localStateStatus as? LocalStateStatus.Present
                if (lState != null) {
                    val localState = lState.value
                    val action = consentQueueImpl.poll()
                    sendConsent(action, localState)
                }
            }
            else -> sendConsentOptimized(consentActionImpl)
        }
    }

    override fun enqueueConsentOptimized(consentActionImpl: ConsentActionImpl) {
        sendConsentOptimized(consentActionImpl)
    }

    override fun enqueueConsentOptimized(nativeConsentAction: NativeConsentAction) {
        sendConsentOptimized(nativeConsentAction.toConsentAction())
    }

    fun changeLocalState(newState: LocalStateStatus) {
        when (newState) {
            is LocalStateStatus.Present -> {
                if (consentQueueImpl.isNotEmpty()) {
                    val localState = newState.value
                    val action = consentQueueImpl.poll()
                    sendConsent(action, localState)
                    localStateStatus = LocalStateStatus.Consumed
                }
            }
            LocalStateStatus.Absent,
            LocalStateStatus.Consumed -> return
        }
    }

    override fun sendStoredConsentToClientOptimized() {
        check {
            val ccpaCached = consentManagerUtils.ccpaConsentOptimized.getOrNull()
            val gdprCached = consentManagerUtils.gdprConsentOptimized.getOrNull()
            SPConsents(
                gdpr = gdprCached?.let { gc -> SPGDPRConsent(consent = gc) },
                ccpa = ccpaCached?.let { cc -> SPCCPAConsent(consent = cc) }
            ).let { sPConsentsSuccess?.invoke(it) }
        }
    }

    override fun sendConsent(actionImpl: ConsentAction, localState: String) {

        executorManager.executeOnSingleThread {
            when (val either = service.sendConsent(localState, actionImpl, env, actionImpl.privacyManagerId)) {
                is Right -> {
                    val updatedLocalState = LocalStateStatus.Present(either.r.localState)
                    val sPConsents = responseConsentHandler(either, actionImpl, consentManagerUtils, dataStorage)
                    sPConsentsSuccess?.invoke(sPConsents)
                    this.localStateStatus = updatedLocalState
                    clientEventManager.storedConsent()
                }
                is Left -> sPConsentsError?.invoke(either.t)
            }
        }
    }

    override fun sendConsentOptimized(actionImpl: ConsentActionImpl) {
        executorManager.executeOnSingleThread {
            service.sendConsentOptimized(actionImpl, env, sPConsentsSuccess, actionImpl.privacyManagerId)
                .executeOnLeft { sPConsentsError?.invoke(it) }
                .executeOnRight {
                    clientEventManager.storedConsent()
                }
        }
    }
}

internal sealed class LocalStateStatus {
    data class Present(val value: String) : LocalStateStatus()
    object Absent : LocalStateStatus()
    object Consumed : LocalStateStatus()
}

internal fun responseConsentHandler(
    either: Right<ConsentResp>,
    actionImpl: ConsentAction,
    consentManagerUtils: ConsentManagerUtils,
    dataStorage: DataStorage
): SPConsents {
    val map: Map<String, Any?> = either.r.content.toTreeMap()
    val uuid: String? = either.r.uuid
    return map.getMap("userConsent")
        ?.let {
            when (actionImpl.campaignType) {
                CampaignType.GDPR -> it.toGDPRUserConsent(uuid = uuid, dataStorage.gdprApplies).let { gdprConsent ->
                    val ccpaCached = consentManagerUtils.getCcpaConsent().getOrNull()
                    SPConsents(
                        gdpr = SPGDPRConsent(consent = gdprConsent),
                        ccpa = ccpaCached?.let { cc -> SPCCPAConsent(consent = cc) }
                    )
                }
                CampaignType.CCPA -> it.toCCPAUserConsent(uuid = uuid, dataStorage.gdprApplies).let { ccpaConsent ->
                    val gdprCached = consentManagerUtils.getGdprConsent().getOrNull()
                    SPConsents(
                        gdpr = gdprCached?.let { gc -> SPGDPRConsent(consent = gc) },
                        ccpa = SPCCPAConsent(consent = ccpaConsent)
                    )
                }
            }
        } ?: SPConsents()
}
