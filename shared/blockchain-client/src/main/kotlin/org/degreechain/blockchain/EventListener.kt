package org.degreechain.blockchain

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import mu.KotlinLogging
import org.hyperledger.fabric.gateway.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

private val logger = KotlinLogging.logger {}

class EventListener(
    private val network: Network
) {
    private val eventListeners = ConcurrentHashMap<String, ContractEventListener>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun listenForDegreeEvents(): Flow<ContractEvent> = flow {
        val channel = Channel<ContractEvent>(Channel.UNLIMITED)

        val listener = network.addContractEventListener(
            Consumer { event ->
                scope.launch {
                    channel.send(event)
                }
            },
            "DegreeAttestationContract",
            "DegreeSubmitted|DegreeVerified|UniversityEnrolled|UniversityApproved|PaymentProcessed"
        )

        eventListeners["degree-events"] = listener

        try {
            while (true) {
                val event = channel.receive()
                emit(event)
            }
        } finally {
            listener.unregister()
            eventListeners.remove("degree-events")
            channel.close()
        }
    }

    fun listenForUniversityEvents(): Flow<ContractEvent> = flow {
        val channel = Channel<ContractEvent>(Channel.UNLIMITED)

        val listener = network.addContractEventListener(
            Consumer { event ->
                scope.launch {
                    channel.send(event)
                }
            },
            "DegreeAttestationContract",
            "UniversityEnrolled|UniversityApproved|UniversityBlacklisted|StakeConfiscated"
        )

        eventListeners["university-events"] = listener

        try {
            while (true) {
                val event = channel.receive()
                emit(event)
            }
        } finally {
            listener.unregister()
            eventListeners.remove("university-events")
            channel.close()
        }
    }

    fun listenForPaymentEvents(): Flow<ContractEvent> = flow {
        val channel = Channel<ContractEvent>(Channel.UNLIMITED)

        val listener = network.addContractEventListener(
            Consumer { event ->
                scope.launch {
                    channel.send(event)
                }
            },
            "DegreeAttestationContract",
            "PaymentProcessed|RevenueDistributed|StakeConfiscated"
        )

        eventListeners["payment-events"] = listener

        try {
            while (true) {
                val event = channel.receive()
                emit(event)
            }
        } finally {
            listener.unregister()
            eventListeners.remove("payment-events")
            channel.close()
        }
    }

    fun stopAllListeners() {
        scope.cancel()
        eventListeners.values.forEach { listener ->
            try {
                listener.unregister()
            } catch (e: Exception) {
                logger.warn(e) { "Error unregistering event listener" }
            }
        }
        eventListeners.clear()
        logger.info { "All event listeners stopped" }
    }
}