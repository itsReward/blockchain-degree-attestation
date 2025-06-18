package org.degreechain.authority.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.degreechain.authority.models.UniversityRegistration
import org.degreechain.blockchain.ContractInvoker
import org.degreechain.common.exceptions.BusinessException
import org.degreechain.common.models.ErrorCode
import org.degreechain.common.utils.ValidationUtils
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class UniversityService(
    private val contractInvoker: ContractInvoker,
    private val paymentService: PaymentService
) {
    private val objectMapper = ObjectMapper().registerKotlinModule()

    suspend fun enrollUniversity(registration: UniversityRegistration): String = withContext(Dispatchers.IO) {
        logger.info { "Enrolling university: ${registration.universityCode}" }

        // Validate registration data
        validateRegistration(registration)

        // Check if university already exists
        try {
            contractInvoker.getUniversity(registration.universityCode)
            throw BusinessException(
                "University already enrolled with code: ${registration.universityCode}",
                ErrorCode.UNIVERSITY_NOT_REGISTERED
            )
        } catch (e: BusinessException) {
            if (e.errorCode != ErrorCode.RESOURCE_NOT_FOUND) {
                throw e
            }
            // University doesn't exist, proceed with enrollment
        }

        // Enroll university on blockchain
        val result = contractInvoker.enrollUniversity(
            universityCode = registration.universityCode,
            universityName = registration.universityName,
            country = registration.country,
            address = registration.address,
            contactEmail = registration.contactEmail,
            publicKey = registration.publicKey,
            stakeAmount = registration.stakeAmount
        )

        // Process stake payment
        paymentService.processStakePayment(
            universityCode = registration.universityCode,
            amount = registration.stakeAmount,
            paymentMethod = registration.paymentMethod
        )

        logger.info { "University enrollment completed: ${registration.universityCode}" }
        result
    }

    suspend fun approveUniversity(universityCode: String): String = withContext(Dispatchers.IO) {
        logger.info { "Approving university: $universityCode" }

        // Validate university exists and is in pending status
        val university = getUniversityDetails(universityCode)
        if (university["status"] != "PENDING_APPROVAL") {
            throw BusinessException(
                "University $universityCode is not in pending approval status",
                ErrorCode.UNIVERSITY_NOT_REGISTERED
            )
        }

        val result = contractInvoker.approveUniversity(universityCode)

        logger.info { "University approved: $universityCode" }
        result
    }

    suspend fun blacklistUniversity(universityCode: String, reason: String): String = withContext(Dispatchers.IO) {
        logger.warn { "Blacklisting university: $universityCode, reason: $reason" }

        // Validate university exists
        getUniversityDetails(universityCode)

        val result = contractInvoker.blacklistUniversity(universityCode, reason)

        // Handle stake confiscation
        paymentService.confiscateStake(universityCode, reason)

        logger.warn { "University blacklisted: $universityCode" }
        result
    }

    suspend fun getAllUniversities(): List<Map<String, Any>> = withContext(Dispatchers.IO) {
        logger.debug { "Retrieving all universities" }

        val universitiesJson = contractInvoker.getAllUniversities()
        val universities: List<Map<String, Any>> = objectMapper.readValue(universitiesJson)

        universities
    }

    suspend fun getUniversity(universityCode: String): Map<String, Any> = withContext(Dispatchers.IO) {
        getUniversityDetails(universityCode)
    }

    suspend fun getUniversityStatistics(universityCode: String): Map<String, Any> = withContext(Dispatchers.IO) {
        logger.debug { "Retrieving statistics for university: $universityCode" }

        val statisticsJson = contractInvoker.getUniversityStatistics(universityCode)
        val statistics: Map<String, Any> = objectMapper.readValue(statisticsJson)

        statistics
    }

    suspend fun getPendingUniversities(): List<Map<String, Any>> = withContext(Dispatchers.IO) {
        logger.debug { "Retrieving pending universities" }

        val allUniversities = getAllUniversities()
        val pendingUniversities = allUniversities.filter {
            it["status"] == "PENDING_APPROVAL"
        }

        pendingUniversities
    }

    private suspend fun getUniversityDetails(universityCode: String): Map<String, Any> {
        try {
            val universityJson = contractInvoker.getUniversity(universityCode)
            return objectMapper.readValue(universityJson)
        } catch (e: Exception) {
            throw BusinessException(
                "University not found: $universityCode",
                ErrorCode.RESOURCE_NOT_FOUND,
                cause = e
            )
        }
    }

    private fun validateRegistration(registration: UniversityRegistration) {
        ValidationUtils.validateUniversityCode(registration.universityCode)
        ValidationUtils.validateEmail(registration.contactEmail)
        ValidationUtils.validateStakeAmount(registration.stakeAmount)
        ValidationUtils.validateRequired(registration.universityName, "University Name")
        ValidationUtils.validateRequired(registration.country, "Country")
        ValidationUtils.validateRequired(registration.address, "Address")
        ValidationUtils.validateRequired(registration.publicKey, "Public Key")
    }
}