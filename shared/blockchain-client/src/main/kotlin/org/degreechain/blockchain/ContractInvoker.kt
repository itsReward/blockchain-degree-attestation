package org.degreechain.blockchain

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.degreechain.blockchain.models.BlockchainResponse
import org.degreechain.common.exceptions.BlockchainException
import org.degreechain.common.models.ErrorCode

private val logger = KotlinLogging.logger {}

class ContractInvoker(
    private val fabricClient: FabricGatewayClient
) {
    private val objectMapper = ObjectMapper().registerKotlinModule()

    // ==================== UNIVERSITY OPERATIONS ====================

    suspend fun enrollUniversity(
        universityCode: String,
        universityName: String,
        country: String,
        address: String,
        contactEmail: String,
        publicKey: String,
        stakeAmount: Double
    ): String = withContext(Dispatchers.IO) {
        val result = fabricClient.submitTransaction(
            "enrollUniversity",
            universityCode,
            universityName,
            country,
            address,
            contactEmail,
            publicKey,
            stakeAmount.toString()
        )
        result.result
    }

    suspend fun approveUniversity(universityCode: String): String = withContext(Dispatchers.IO) {
        val result = fabricClient.submitTransaction("approveUniversity", universityCode)
        result.result
    }

    suspend fun blacklistUniversity(universityCode: String, reason: String): String = withContext(Dispatchers.IO) {
        val result = fabricClient.submitTransaction("blacklistUniversity", universityCode, reason)
        result.result
    }

    suspend fun getUniversity(universityCode: String): String = withContext(Dispatchers.IO) {
        val response = fabricClient.evaluateTransaction("getUniversity", universityCode)
        response.data ?: throw BlockchainException(
            "No data returned from getUniversity",
            ErrorCode.CHAINCODE_EXECUTION_ERROR
        )
    }

    suspend fun getAllUniversities(): String = withContext(Dispatchers.IO) {
        val response = fabricClient.evaluateTransaction("getAllUniversities")
        response.data ?: "[]"
    }

    suspend fun getUniversityStatistics(universityCode: String): String = withContext(Dispatchers.IO) {
        val response = fabricClient.evaluateTransaction("getUniversityStatistics", universityCode)
        response.data ?: throw BlockchainException(
            "No statistics data returned",
            ErrorCode.CHAINCODE_EXECUTION_ERROR
        )
    }

    // ==================== DEGREE OPERATIONS ====================

    suspend fun submitDegree(
        certificateNumber: String,
        studentName: String,
        degreeName: String,
        facultyName: String,
        degreeClassification: String,
        issuanceDate: String,
        expiryDate: String?,
        certificateHash: String
    ): String = withContext(Dispatchers.IO) {
        val result = fabricClient.submitTransaction(
            "submitDegree",
            certificateNumber,
            studentName,
            degreeName,
            facultyName,
            degreeClassification,
            issuanceDate,
            expiryDate ?: "",
            certificateHash
        )
        result.result
    }

    suspend fun verifyDegree(
        certificateNumber: String,
        verifierOrganization: String,
        verifierEmail: String,
        providedHash: String?
    ): String = withContext(Dispatchers.IO) {
        val response = fabricClient.evaluateTransaction(
            "verifyDegree",
            certificateNumber,
            verifierOrganization,
            verifierEmail,
            providedHash ?: ""
        )
        response.data ?: throw BlockchainException(
            "No verification data returned",
            ErrorCode.CHAINCODE_EXECUTION_ERROR
        )
    }

    suspend fun getDegree(certificateNumber: String): String = withContext(Dispatchers.IO) {
        val response = fabricClient.evaluateTransaction("getDegree", certificateNumber)
        response.data ?: throw BlockchainException(
            "Degree not found",
            ErrorCode.RESOURCE_NOT_FOUND
        )
    }

    // ==================== PAYMENT OPERATIONS ====================

    suspend fun processVerificationPayment(
        certificateNumber: String,
        verifierOrganization: String,
        verifierEmail: String,
        paymentAmount: Double
    ): String = withContext(Dispatchers.IO) {
        val result = fabricClient.submitTransaction(
            "processVerificationPayment",
            certificateNumber,
            verifierOrganization,
            verifierEmail,
            paymentAmount.toString()
        )
        result.result
    }

    // ==================== UTILITY OPERATIONS ====================

    suspend fun initLedger(): String = withContext(Dispatchers.IO) {
        val result = fabricClient.submitTransaction("initLedger")
        result.result
    }

    suspend fun ping(): Boolean = withContext(Dispatchers.IO) {
        try {
            fabricClient.evaluateTransaction("getAllUniversities")
            true
        } catch (e: Exception) {
            logger.warn(e) { "Blockchain ping failed" }
            false
        }
    }

    inline fun <reified T> parseJsonResponse(jsonString: String): T {
        return try {
            objectMapper.readValue(jsonString, T::class.java)
        } catch (e: Exception) {
            throw BlockchainException(
                "Failed to parse blockchain response: ${e.message}",
                ErrorCode.CHAINCODE_EXECUTION_ERROR,
                cause = e
            )
        }
    }
}