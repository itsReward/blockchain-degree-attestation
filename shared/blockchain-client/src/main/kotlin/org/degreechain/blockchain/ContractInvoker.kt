// shared/blockchain-client/src/main/kotlin/org/degreechain/blockchain/ContractInvoker.kt
package org.degreechain.blockchain

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.degreechain.common.exceptions.BlockchainException
import org.degreechain.common.models.ErrorCode

private val logger = KotlinLogging.logger {}

open class ContractInvoker(
    protected val fabricClient: FabricGatewayClient
) {
    val objectMapper = ObjectMapper().registerKotlinModule()

    // ==================== HEALTH CHECK ====================

    open suspend fun ping(): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = fabricClient.evaluateTransaction("ping")
            response.success
        } catch (e: Exception) {
            logger.debug(e) { "Ping failed" }
            false
        }
    }

    open suspend fun initLedger(): String = withContext(Dispatchers.IO) {
        val result = fabricClient.submitTransaction("initLedger")
        result.result
    }

    // ==================== UNIVERSITY OPERATIONS ====================

    open suspend fun enrollUniversity(
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

    open suspend fun approveUniversity(universityCode: String): String = withContext(Dispatchers.IO) {
        val result = fabricClient.submitTransaction("approveUniversity", universityCode)
        result.result
    }

    open suspend fun blacklistUniversity(universityCode: String, reason: String): String = withContext(Dispatchers.IO) {
        val result = fabricClient.submitTransaction("blacklistUniversity", universityCode, reason)
        result.result
    }

    open suspend fun getUniversity(universityCode: String): String = withContext(Dispatchers.IO) {
        val response = fabricClient.evaluateTransaction("getUniversity", universityCode)
        response.data ?: throw BlockchainException(
            "No data returned from getUniversity",
            ErrorCode.CHAINCODE_EXECUTION_ERROR
        )
    }

    open suspend fun getAllUniversities(): String = withContext(Dispatchers.IO) {
        val response = fabricClient.evaluateTransaction("getAllUniversities")
        response.data ?: "[]"
    }

    open suspend fun getUniversityStatistics(universityCode: String): String = withContext(Dispatchers.IO) {
        val response = fabricClient.evaluateTransaction("getUniversityStatistics", universityCode)
        response.data ?: throw BlockchainException(
            "No statistics data returned",
            ErrorCode.CHAINCODE_EXECUTION_ERROR
        )
    }

    // ==================== DEGREE OPERATIONS ====================

    open suspend fun submitDegree(
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

    open suspend fun verifyDegree(
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

    open suspend fun getDegree(certificateNumber: String): String = withContext(Dispatchers.IO) {
        val response = fabricClient.evaluateTransaction("getDegree", certificateNumber)
        response.data ?: throw BlockchainException(
            "Degree not found: $certificateNumber",
            ErrorCode.RESOURCE_NOT_FOUND
        )
    }

    open suspend fun getAllDegrees(): String = withContext(Dispatchers.IO) {
        val response = fabricClient.evaluateTransaction("getAllDegrees")
        response.data ?: "[]"
    }

    open suspend fun getDegreesByUniversity(universityCode: String): String = withContext(Dispatchers.IO) {
        val response = fabricClient.evaluateTransaction("getDegreesByUniversity", universityCode)
        response.data ?: "[]"
    }

    open suspend fun revokeDegree(
        certificateNumber: String,
        reason: String
    ): String = withContext(Dispatchers.IO) {
        val result = fabricClient.submitTransaction("revokeDegree", certificateNumber, reason)
        result.result
    }

    // ==================== VERIFICATION OPERATIONS ====================

    open suspend fun recordVerification(
        verificationId: String,
        certificateNumber: String,
        verifierOrganization: String,
        verifierEmail: String,
        verificationResult: String,
        confidence: Double
    ): String = withContext(Dispatchers.IO) {
        val result = fabricClient.submitTransaction(
            "recordVerification",
            verificationId,
            certificateNumber,
            verifierOrganization,
            verifierEmail,
            verificationResult,
            confidence.toString()
        )
        result.result
    }

    open suspend fun getVerification(verificationId: String): String = withContext(Dispatchers.IO) {
        val response = fabricClient.evaluateTransaction("getVerification", verificationId)
        response.data ?: throw BlockchainException(
            "Verification not found: $verificationId",
            ErrorCode.RESOURCE_NOT_FOUND
        )
    }

    open suspend fun getVerificationHistory(certificateNumber: String): String = withContext(Dispatchers.IO) {
        val response = fabricClient.evaluateTransaction("getVerificationHistory", certificateNumber)
        response.data ?: "[]"
    }

    // ==================== PAYMENT OPERATIONS ====================

    open suspend fun processVerificationPayment(
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

    open suspend fun processStakePayment(
        universityCode: String,
        stakeAmount: Double,
        paymentMethod: String
    ): String = withContext(Dispatchers.IO) {
        val result = fabricClient.submitTransaction(
            "processStakePayment",
            universityCode,
            stakeAmount.toString(),
            paymentMethod
        )
        result.result
    }

    open suspend fun withdrawStake(universityCode: String): String = withContext(Dispatchers.IO) {
        val result = fabricClient.submitTransaction("withdrawStake", universityCode)
        result.result
    }

    open suspend fun confiscateStake(universityCode: String, reason: String): String = withContext(Dispatchers.IO) {
        val result = fabricClient.submitTransaction("confiscateStake", universityCode, reason)
        result.result
    }

    open suspend fun getPaymentHistory(organizationCode: String): String = withContext(Dispatchers.IO) {
        val response = fabricClient.evaluateTransaction("getPaymentHistory", organizationCode)
        response.data ?: "[]"
    }

    // ==================== GOVERNANCE OPERATIONS ====================

    open suspend fun createProposal(
        proposalId: String,
        proposalType: String,
        description: String,
        proposedChanges: String
    ): String = withContext(Dispatchers.IO) {
        val result = fabricClient.submitTransaction(
            "createProposal",
            proposalId,
            proposalType,
            description,
            proposedChanges
        )
        result.result
    }

    open suspend fun voteOnProposal(
        proposalId: String,
        voterOrganization: String,
        vote: String,
        comments: String?
    ): String = withContext(Dispatchers.IO) {
        val result = fabricClient.submitTransaction(
            "voteOnProposal",
            proposalId,
            voterOrganization,
            vote,
            comments ?: ""
        )
        result.result
    }

    open suspend fun getProposal(proposalId: String): String = withContext(Dispatchers.IO) {
        val response = fabricClient.evaluateTransaction("getProposal", proposalId)
        response.data ?: throw BlockchainException(
            "Proposal not found: $proposalId",
            ErrorCode.RESOURCE_NOT_FOUND
        )
    }

    open suspend fun getAllProposals(): String = withContext(Dispatchers.IO) {
        val response = fabricClient.evaluateTransaction("getAllProposals")
        response.data ?: "[]"
    }

    open suspend fun finalizeProposal(proposalId: String): String = withContext(Dispatchers.IO) {
        val result = fabricClient.submitTransaction("finalizeProposal", proposalId)
        result.result
    }

    // ==================== ANALYTICS AND STATISTICS ====================

    open suspend fun getSystemStatistics(): String = withContext(Dispatchers.IO) {
        val response = fabricClient.evaluateTransaction("getSystemStatistics")
        response.data ?: throw BlockchainException(
            "System statistics not available",
            ErrorCode.CHAINCODE_EXECUTION_ERROR
        )
    }

    open suspend fun getVerificationStatistics(organizationCode: String): String = withContext(Dispatchers.IO) {
        val response = fabricClient.evaluateTransaction("getVerificationStatistics", organizationCode)
        response.data ?: "{}"
    }

    open suspend fun getRevenueReport(organizationCode: String, startDate: String, endDate: String): String = withContext(Dispatchers.IO) {
        val response = fabricClient.evaluateTransaction("getRevenueReport", organizationCode, startDate, endDate)
        response.data ?: "{}"
    }
}