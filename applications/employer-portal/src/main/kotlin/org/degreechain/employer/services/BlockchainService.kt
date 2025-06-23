package org.degreechain.employer.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.degreechain.blockchain.ContractInvoker
import org.degreechain.blockchain.HealthChecker
import org.degreechain.common.exceptions.BlockchainException
import org.degreechain.common.exceptions.BusinessException
import org.degreechain.common.models.ErrorCode
import org.degreechain.common.utils.ValidationUtils
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

@Service
class BlockchainService(
    private val contractInvoker: ContractInvoker,
    private val healthChecker: HealthChecker
) {
    private val objectMapper = ObjectMapper().registerKotlinModule()
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    private fun safeMapOf(vararg pairs: Pair<String, Any?>): Map<String, Any> {
        return pairs.mapNotNull { (key, value) ->
            value?.let { key to it }
        }.toMap()
    }

    suspend fun verifyDegreeOnBlockchain(
        certificateNumber: String,
        verifierOrganization: String,
        verifierEmail: String,
        providedHash: String? = null
    ): Map<String, Any> = withContext(Dispatchers.IO) {
        logger.info { "Verifying degree on blockchain: $certificateNumber" }

        try {
            // Validate inputs
            ValidationUtils.validateCertificateNumber(certificateNumber)
            ValidationUtils.validateRequired(verifierOrganization, "Verifier Organization")
            ValidationUtils.validateEmail(verifierEmail)

            if (providedHash != null) {
                ValidationUtils.validateHash(providedHash)
            }

            // Call blockchain contract
            val verificationJson = contractInvoker.verifyDegree(
                certificateNumber = certificateNumber,
                verifierOrganization = verifierOrganization,
                verifierEmail = verifierEmail,
                providedHash = providedHash
            )

            val verificationData: Map<String, Any> = objectMapper.readValue(verificationJson)

            logger.info { "Blockchain verification completed for: $certificateNumber" }
            verificationData

        } catch (e: BlockchainException) {
            logger.error(e) { "Blockchain verification failed for: $certificateNumber" }
            throw BusinessException(
                "Blockchain verification failed: ${e.message}",
                ErrorCode.BLOCKCHAIN_CONNECTION_ERROR,
                cause = e
            )
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error during blockchain verification: $certificateNumber" }
            throw BusinessException(
                "Verification failed: ${e.message}",
                ErrorCode.INTERNAL_SERVER_ERROR,
                cause = e
            )
        }
    }

    suspend fun getDegreeDetails(certificateNumber: String): Map<String, Any> = withContext(Dispatchers.IO) {
        logger.debug { "Retrieving degree details from blockchain: $certificateNumber" }

        try {
            ValidationUtils.validateCertificateNumber(certificateNumber)

            val degreeJson = contractInvoker.getDegree(certificateNumber)
            val degreeData: Map<String, Any> = objectMapper.readValue(degreeJson)

            logger.debug { "Retrieved degree details for: $certificateNumber" }
            degreeData

        } catch (e: BlockchainException) {
            logger.error(e) { "Failed to retrieve degree details: $certificateNumber" }
            throw BusinessException(
                "Failed to retrieve degree from blockchain: ${e.message}",
                ErrorCode.RESOURCE_NOT_FOUND,
                cause = e
            )
        }
    }

    suspend fun getUniversityDetails(universityCode: String): Map<String, Any> = withContext(Dispatchers.IO) {
        logger.debug { "Retrieving university details from blockchain: $universityCode" }

        try {
            ValidationUtils.validateUniversityCode(universityCode)

            val universityJson = contractInvoker.getUniversity(universityCode)
            val universityData: Map<String, Any> = objectMapper.readValue(universityJson)

            logger.debug { "Retrieved university details for: $universityCode" }
            universityData

        } catch (e: BlockchainException) {
            logger.error(e) { "Failed to retrieve university details: $universityCode" }
            throw BusinessException(
                "Failed to retrieve university from blockchain: ${e.message}",
                ErrorCode.RESOURCE_NOT_FOUND,
                cause = e
            )
        }
    }

    suspend fun getAllUniversities(): List<Map<String, Any>> = withContext(Dispatchers.IO) {
        logger.debug { "Retrieving all universities from blockchain" }

        try {
            val universitiesJson = contractInvoker.getAllUniversities()
            val universities: List<Map<String, Any>> = objectMapper.readValue(universitiesJson)

            logger.debug { "Retrieved ${universities.size} universities from blockchain" }
            universities

        } catch (e: BlockchainException) {
            logger.error(e) { "Failed to retrieve universities list" }
            throw BusinessException(
                "Failed to retrieve universities from blockchain: ${e.message}",
                ErrorCode.BLOCKCHAIN_CONNECTION_ERROR,
                cause = e
            )
        }
    }

    suspend fun processVerificationPaymentOnBlockchain(
        certificateNumber: String,
        verifierOrganization: String,
        verifierEmail: String,
        paymentAmount: Double
    ): Map<String, Any> = withContext(Dispatchers.IO) {
        logger.info { "Processing verification payment on blockchain for: $certificateNumber" }

        try {
            // Validate inputs
            ValidationUtils.validateCertificateNumber(certificateNumber)
            ValidationUtils.validateRequired(verifierOrganization, "Verifier Organization")
            ValidationUtils.validateEmail(verifierEmail)

            if (paymentAmount <= 0) {
                throw BusinessException(
                    "Payment amount must be positive",
                    ErrorCode.VALIDATION_ERROR
                )
            }

            val result = contractInvoker.processVerificationPayment(
                certificateNumber = certificateNumber,
                verifierOrganization = verifierOrganization,
                verifierEmail = verifierEmail,
                paymentAmount = paymentAmount
            )

            logger.info { "Verification payment processed on blockchain: $certificateNumber" }

            safeMapOf(
                "success" to true,
                "message" to result,
                "certificateNumber" to certificateNumber,
                "paymentAmount" to paymentAmount,
                "timestamp" to LocalDateTime.now().format(dateFormatter)
            )

        } catch (e: BlockchainException) {
            logger.error(e) { "Blockchain payment processing failed for: $certificateNumber" }
            throw BusinessException(
                "Payment processing failed on blockchain: ${e.message}",
                ErrorCode.PAYMENT_FAILED,
                cause = e
            )
        }
    }

    suspend fun getBlockchainHealth(): Map<String, Any> = withContext(Dispatchers.IO) {
        logger.debug { "Checking blockchain health" }

        try {
            val healthStatus = healthChecker.getDetailedStatus()

            safeMapOf(
                "overall" to healthStatus,
                "timestamp" to LocalDateTime.now().format(dateFormatter),
                "networkStatus" to getNetworkStatus()
            )

        } catch (e: Exception) {
            logger.error(e) { "Failed to check blockchain health" }
            safeMapOf(
                "isHealthy" to false,
                "error" to e.message,
                "timestamp" to LocalDateTime.now().format(dateFormatter)
            )
        }
    }

    suspend fun getNetworkStatistics(): Map<String, Any> = withContext(Dispatchers.IO) {
        logger.debug { "Retrieving blockchain network statistics" }

        try {
            // Get all universities to calculate network stats
            val universities = getAllUniversities()

            val activeUniversities = universities.count { it["status"] == "ACTIVE" }
            val totalStake = universities.sumOf {
                (it["stakeAmount"] as? Number)?.toDouble() ?: 0.0
            }
            val totalDegrees = universities.sumOf {
                (it["totalDegreesIssued"] as? Number)?.toLong() ?: 0L
            }
            val totalRevenue = universities.sumOf {
                (it["revenue"] as? Number)?.toDouble() ?: 0.0
            }

            safeMapOf(
                "networkOverview" to mapOf(
                    "totalUniversities" to universities.size,
                    "activeUniversities" to activeUniversities,
                    "totalStakeAmount" to totalStake,
                    "totalDegreesIssued" to totalDegrees,
                    "totalNetworkRevenue" to totalRevenue
                ),
                "universityDistribution" to universities.groupingBy { it["status"] }.eachCount(),
                "countryDistribution" to universities.groupingBy { it["country"] }.eachCount(),
                "averageStakePerUniversity" to if (universities.isNotEmpty()) totalStake / universities.size else 0.0,
                "averageDegreesPerUniversity" to if (universities.isNotEmpty()) totalDegrees.toDouble() / universities.size else 0.0,
                "timestamp" to LocalDateTime.now().format(dateFormatter)
            )

        } catch (e: Exception) {
            logger.error(e) { "Failed to retrieve network statistics" }
            throw BusinessException(
                "Failed to retrieve network statistics: ${e.message}",
                ErrorCode.BLOCKCHAIN_CONNECTION_ERROR,
                cause = e
            )
        }
    }

    suspend fun validateCertificateExists(certificateNumber: String): Boolean = withContext(Dispatchers.IO) {
        logger.debug { "Validating certificate exists on blockchain: $certificateNumber" }

        try {
            getDegreeDetails(certificateNumber)
            true
        } catch (e: BusinessException) {
            if (e.errorCode == ErrorCode.RESOURCE_NOT_FOUND) {
                false
            } else {
                throw e
            }
        }
    }

    suspend fun getUniversityStatistics(universityCode: String): Map<String, Any> = withContext(Dispatchers.IO) {
        logger.debug { "Retrieving university statistics from blockchain: $universityCode" }

        try {
            ValidationUtils.validateUniversityCode(universityCode)

            val statisticsJson = contractInvoker.getUniversityStatistics(universityCode)
            val statistics: Map<String, Any> = objectMapper.readValue(statisticsJson)

            logger.debug { "Retrieved statistics for university: $universityCode" }
            statistics

        } catch (e: BlockchainException) {
            logger.error(e) { "Failed to retrieve university statistics: $universityCode" }
            throw BusinessException(
                "Failed to retrieve university statistics from blockchain: ${e.message}",
                ErrorCode.RESOURCE_NOT_FOUND,
                cause = e
            )
        }
    }

    suspend fun searchDegreesByUniversity(universityCode: String): List<Map<String, Any>> = withContext(Dispatchers.IO) {
        logger.debug { "Searching degrees by university: $universityCode" }

        try {
            // In a real implementation, this would query the blockchain for degrees from specific university
            // For now, we'll return empty list as the chaincode doesn't have this specific query function
            // This would require additional chaincode functions for filtering degrees

            logger.warn { "Search by university not implemented in current chaincode version" }
            emptyList()

        } catch (e: Exception) {
            logger.error(e) { "Failed to search degrees by university: $universityCode" }
            throw BusinessException(
                "Failed to search degrees: ${e.message}",
                ErrorCode.BLOCKCHAIN_CONNECTION_ERROR,
                cause = e
            )
        }
    }

    suspend fun getDegreeVerificationHistory(certificateNumber: String): List<Map<String, Any>> = withContext(Dispatchers.IO) {
        logger.debug { "Retrieving verification history for degree: $certificateNumber" }

        try {
            // In a real implementation, this would query verification history from blockchain
            // For now, return empty list as this would require additional chaincode functionality

            logger.warn { "Verification history query not implemented in current chaincode version" }
            emptyList()

        } catch (e: Exception) {
            logger.error(e) { "Failed to retrieve verification history: $certificateNumber" }
            throw BusinessException(
                "Failed to retrieve verification history: ${e.message}",
                ErrorCode.BLOCKCHAIN_CONNECTION_ERROR,
                cause = e
            )
        }
    }

    suspend fun checkUniversityStatus(universityCode: String): String = withContext(Dispatchers.IO) {
        logger.debug { "Checking university status: $universityCode" }

        try {
            val universityDetails = getUniversityDetails(universityCode)
            val status = universityDetails["status"] as? String ?: "UNKNOWN"

            logger.debug { "University $universityCode status: $status" }
            status

        } catch (e: BusinessException) {
            if (e.errorCode == ErrorCode.RESOURCE_NOT_FOUND) {
                "NOT_FOUND"
            } else {
                throw e
            }
        }
    }

    private suspend fun getNetworkStatus(): Map<String, Any> {
        return try {
            val pingResult = contractInvoker.ping()
            safeMapOf(
                "connectionStatus" to if (pingResult) "CONNECTED" else "DISCONNECTED",
                "lastPingTime" to LocalDateTime.now().format(dateFormatter),
                "responseTime" to "< 1s" // Simplified for demo
            )
        } catch (e: Exception) {
            safeMapOf(
                "connectionStatus" to "ERROR",
                "error" to e.message,
                "lastPingTime" to LocalDateTime.now().format(dateFormatter)
            )
        }
    }

    suspend fun initializeLedger(): String = withContext(Dispatchers.IO) {
        logger.info { "Initializing blockchain ledger" }

        try {
            val result = contractInvoker.initLedger()
            logger.info { "Ledger initialization completed" }
            result

        } catch (e: BlockchainException) {
            logger.error(e) { "Failed to initialize ledger" }
            throw BusinessException(
                "Ledger initialization failed: ${e.message}",
                ErrorCode.BLOCKCHAIN_CONNECTION_ERROR,
                cause = e
            )
        }
    }

    suspend fun getTransactionMetrics(): Map<String, Any> = withContext(Dispatchers.IO) {
        logger.debug { "Retrieving transaction metrics" }

        // In a real implementation, this would query actual blockchain metrics
        // For now, return mock metrics
        safeMapOf(
            "averageResponseTime" to 250, // milliseconds
            "successRate" to 0.99,
            "totalTransactions" to 15420,
            "transactionsToday" to 127,
            "peakTPS" to 15, // transactions per second
            "currentTPS" to 8,
            "timestamp" to LocalDateTime.now().format(dateFormatter)
        )
    }
}