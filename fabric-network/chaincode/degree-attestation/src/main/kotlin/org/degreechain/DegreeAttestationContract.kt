package org.degreechain

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.degreechain.models.*
import org.hyperledger.fabric.contract.Context
import org.hyperledger.fabric.contract.ContractInterface
import org.hyperledger.fabric.contract.annotation.*
import org.hyperledger.fabric.shim.ChaincodeException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@Contract(
    name = "DegreeAttestationContract",
    info = Info(
        title = "Degree Attestation Smart Contract",
        description = "Smart contract for blockchain-based degree verification system with VeryPhy integration",
        version = "2.0.0"
    )
)
@Default
class DegreeAttestationContract : ContractInterface {

    private val objectMapper = ObjectMapper().registerKotlinModule()
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    companion object {
        const val UNIVERSITY_PREFIX = "UNIVERSITY:"
        const val DEGREE_PREFIX = "DEGREE:"
        const val VERIFICATION_PREFIX = "VERIFICATION:"
        const val PAYMENT_PREFIX = "PAYMENT:"
        const val PROPOSAL_PREFIX = "PROPOSAL:"
        const val HASH_PREFIX = "HASH:"

        const val ATTESTATION_ORG = "ATTESTATION_AUTHORITY"
        const val VERIFICATION_FEE = 10.0
        const val MINIMUM_STAKE = 1000.0
    }

    // ==================== INITIALIZATION ====================

    @Transaction(intent = Transaction.TYPE.SUBMIT)
    fun initLedger(ctx: Context) {
        val stub = ctx.stub

        // Initialize the attestation authority organization
        val attestationOrg = University(
            universityCode = ATTESTATION_ORG,
            universityName = "Blockchain Degree Attestation Authority",
            country = "Global",
            contactEmail = "admin@attestation.org",
            publicKey = "attestation_public_key",
            registrationDate = LocalDateTime.now(),
            isActive = true,
            stake = 0.0,
            totalEarnings = 0.0,
            verificationCount = 0
        )

        val key = UNIVERSITY_PREFIX + ATTESTATION_ORG
        stub.putState(key, objectMapper.writeValueAsBytes(attestationOrg))

        // Set initialization event
        ctx.stub.setEvent("LedgerInitialized", objectMapper.writeValueAsBytes(
            mapOf("message" to "Degree Attestation ledger initialized with VeryPhy integration")
        ))
    }

    // ========== NEW VERYPHY INTEGRATION FUNCTIONS ==========

    @Transaction(intent = Transaction.TYPE.SUBMIT)
    fun submitDegreeWithHash(
        ctx: Context,
        studentId: String,
        degreeName: String,
        institutionName: String,
        issuanceDate: String,
        certificateHash: String,
        ocrData: String,
        processedImageUrl: String
    ): String {
        val stub = ctx.stub

        // Validate university registration
        val universityKey = UNIVERSITY_PREFIX + institutionName
        val universityBytes = stub.getState(universityKey)
        if (universityBytes == null || universityBytes.isEmpty()) {
            throw ChaincodeException("University not registered: $institutionName")
        }

        val university = objectMapper.readValue(universityBytes, University::class.java)
        if (!university.isActive) {
            throw ChaincodeException("University is not active: $institutionName")
        }

        // Validate input data
        if (studentId.isBlank() || degreeName.isBlank() || certificateHash.isBlank()) {
            throw ChaincodeException("Required fields cannot be empty")
        }

        // Check if hash already exists
        val hashKey = HASH_PREFIX + certificateHash
        val existingDegreeId = stub.getState(hashKey)
        if (existingDegreeId != null && existingDegreeId.isNotEmpty()) {
            throw ChaincodeException("Certificate hash already exists: $certificateHash")
        }

        // Create degree with hash
        val degreeId = UUID.randomUUID().toString()
        val degree = DegreeWithHash(
            degreeId = degreeId,
            studentId = studentId,
            degreeName = degreeName,
            institutionName = institutionName,
            issuanceDate = LocalDateTime.parse(issuanceDate, dateFormatter),
            certificateHash = certificateHash,
            ocrData = ocrData,
            processedImageUrl = processedImageUrl,
            submissionDate = LocalDateTime.now(),
            status = DegreeStatus.ACTIVE,
            verificationCount = 0
        )

        // Store degree record
        val degreeKey = DEGREE_PREFIX + degreeId
        stub.putState(degreeKey, objectMapper.writeValueAsBytes(degree))

        // Store hash mapping for quick lookup
        stub.putState(hashKey, degreeId.toByteArray())

        // Update university statistics
        val updatedUniversity = university.copy(
            submissionCount = university.submissionCount + 1
        )
        stub.putState(universityKey, objectMapper.writeValueAsBytes(updatedUniversity))

        // Log transaction
        ctx.stub.setEvent("DegreeSubmittedWithHash", objectMapper.writeValueAsBytes(
            mapOf(
                "degreeId" to degreeId,
                "certificateHash" to certificateHash,
                "institutionName" to institutionName,
                "studentId" to studentId,
                "timestamp" to LocalDateTime.now().toString()
            )
        ))

        return degreeId
    }

    @Transaction(intent = Transaction.TYPE.EVALUATE)
    fun verifyDegreeByHash(
        ctx: Context,
        extractedHash: String,
        ocrData: String? = null
    ): String {
        val stub = ctx.stub

        // Look up degree by hash
        val hashKey = HASH_PREFIX + extractedHash
        val degreeIdBytes = stub.getState(hashKey)

        if (degreeIdBytes == null || degreeIdBytes.isEmpty()) {
            return objectMapper.writeValueAsString(
                VerificationResultWithConfidence(
                    verified = false,
                    degreeId = null,
                    verificationMethod = "HASH_LOOKUP",
                    confidence = 0.0,
                    message = "Hash not found in blockchain",
                    timestamp = LocalDateTime.now()
                )
            )
        }

        val degreeId = String(degreeIdBytes)
        val degreeKey = DEGREE_PREFIX + degreeId
        val degreeBytes = stub.getState(degreeKey)

        if (degreeBytes == null || degreeBytes.isEmpty()) {
            return objectMapper.writeValueAsString(
                VerificationResultWithConfidence(
                    verified = false,
                    degreeId = degreeId,
                    verificationMethod = "HASH_LOOKUP",
                    confidence = 0.0,
                    message = "Degree record not found",
                    timestamp = LocalDateTime.now()
                )
            )
        }

        val degree = objectMapper.readValue(degreeBytes, DegreeWithHash::class.java)

        // If degree is revoked, return verification failure
        if (degree.status == DegreeStatus.REVOKED) {
            return objectMapper.writeValueAsString(
                VerificationResultWithConfidence(
                    verified = false,
                    degreeId = degreeId,
                    verificationMethod = "HASH_LOOKUP",
                    confidence = 0.0,
                    message = "Degree has been revoked",
                    timestamp = LocalDateTime.now()
                )
            )
        }

        // Calculate confidence based on OCR data match (if provided)
        var confidence = 1.0
        var verificationMethod = "HASH_MATCH"

        if (!ocrData.isNullOrBlank()) {
            confidence = calculateOcrMatchConfidence(degree.ocrData, ocrData)
            verificationMethod = "HASH_AND_OCR_MATCH"
        }

        // Update verification count
        val updatedDegree = degree.copy(
            verificationCount = degree.verificationCount + 1,
            lastVerified = LocalDateTime.now()
        )
        stub.putState(degreeKey, objectMapper.writeValueAsBytes(updatedDegree))

        // Update university verification count
        val universityKey = UNIVERSITY_PREFIX + degree.institutionName
        val universityBytes = stub.getState(universityKey)
        if (universityBytes != null && universityBytes.isNotEmpty()) {
            val university = objectMapper.readValue(universityBytes, University::class.java)
            val updatedUniversity = university.copy(
                verificationCount = university.verificationCount + 1
            )
            stub.putState(universityKey, objectMapper.writeValueAsBytes(updatedUniversity))
        }

        // Log verification
        val verificationId = UUID.randomUUID().toString()
        val verification = VerificationLogEntry(
            verificationId = verificationId,
            degreeId = degreeId,
            verifierOrg = ctx.clientIdentity.mspId,
            verificationMethod = verificationMethod,
            confidence = confidence,
            timestamp = LocalDateTime.now(),
            extractedHash = extractedHash
        )

        val verificationKey = VERIFICATION_PREFIX + verificationId
        stub.putState(verificationKey, objectMapper.writeValueAsBytes(verification))

        // Set event for successful verification
        ctx.stub.setEvent("DegreeVerified", objectMapper.writeValueAsBytes(
            mapOf(
                "degreeId" to degreeId,
                "verificationMethod" to verificationMethod,
                "confidence" to confidence,
                "timestamp" to LocalDateTime.now().toString()
            )
        ))

        return objectMapper.writeValueAsString(
            VerificationResultWithConfidence(
                verified = confidence >= 0.8,
                degreeId = degreeId,
                degree = updatedDegree,
                verificationMethod = verificationMethod,
                confidence = confidence,
                message = if (confidence >= 0.8) "Degree verified successfully" else "Low confidence verification",
                timestamp = LocalDateTime.now()
            )
        )
    }

    @Transaction(intent = Transaction.TYPE.EVALUATE)
    fun getDegreeByHash(
        ctx: Context,
        certificateHash: String
    ): String {
        val stub = ctx.stub

        val hashKey = HASH_PREFIX + certificateHash
        val degreeIdBytes = stub.getState(hashKey)

        if (degreeIdBytes == null || degreeIdBytes.isEmpty()) {
            throw ChaincodeException("Degree not found for hash: $certificateHash")
        }

        val degreeId = String(degreeIdBytes)
        val degreeKey = DEGREE_PREFIX + degreeId
        val degreeBytes = stub.getState(degreeKey)

        if (degreeBytes == null || degreeBytes.isEmpty()) {
            throw ChaincodeException("Degree record not found: $degreeId")
        }

        return String(degreeBytes)
    }

    @Transaction(intent = Transaction.TYPE.EVALUATE)
    fun getVerificationHistory(
        ctx: Context,
        degreeId: String
    ): String {
        val stub = ctx.stub
        val verifications = mutableListOf<VerificationLogEntry>()

        val iterator = stub.getStateByPartialCompositeKey(VERIFICATION_PREFIX)
        while (iterator.hasNext()) {
            val result = iterator.next()
            val verification = objectMapper.readValue(result.value, VerificationLogEntry::class.java)
            if (verification.degreeId == degreeId) {
                verifications.add(verification)
            }
        }
        iterator.close()

        return objectMapper.writeValueAsString(verifications.sortedByDescending { it.timestamp })
    }

    @Transaction(intent = Transaction.TYPE.SUBMIT)
    fun revokeDegree(
        ctx: Context,
        degreeId: String,
        reason: String
    ): String {
        val stub = ctx.stub

        // Only attestation authority can revoke degrees
        if (ctx.clientIdentity.mspId != "AttestationMSP") {
            throw ChaincodeException("Only attestation authority can revoke degrees")
        }

        val degreeKey = DEGREE_PREFIX + degreeId
        val degreeBytes = stub.getState(degreeKey)

        if (degreeBytes == null || degreeBytes.isEmpty()) {
            throw ChaincodeException("Degree not found: $degreeId")
        }

        val degree = objectMapper.readValue(degreeBytes, DegreeWithHash::class.java)
        val revokedDegree = degree.copy(
            status = DegreeStatus.REVOKED,
            lastVerified = LocalDateTime.now()
        )

        stub.putState(degreeKey, objectMapper.writeValueAsBytes(revokedDegree))

        // Log revocation
        ctx.stub.setEvent("DegreeRevoked", objectMapper.writeValueAsBytes(
            mapOf(
                "degreeId" to degreeId,
                "reason" to reason,
                "timestamp" to LocalDateTime.now().toString()
            )
        ))

        return "Degree revoked successfully"
    }

    // ========== HELPER FUNCTIONS ==========

    private fun calculateOcrMatchConfidence(storedOcrData: String, extractedOcrData: String): Double {
        return try {
            val storedData = objectMapper.readValue(storedOcrData, Map::class.java)
            val extractedData = objectMapper.readValue(extractedOcrData, Map::class.java)

            val keyFields = listOf("studentName", "degreeName", "institutionName", "issuanceDate", "certificateNumber")
            var matchCount = 0
            var totalFields = 0

            for (field in keyFields) {
                if (storedData.containsKey(field) && extractedData.containsKey(field)) {
                    totalFields++
                    val storedValue = storedData[field]?.toString()?.trim()?.lowercase()
                    val extractedValue = extractedData[field]?.toString()?.trim()?.lowercase()

                    if (storedValue == extractedValue) {
                        matchCount++
                    } else {
                        // Check for partial matches (useful for name variations)
                        val similarity = calculateStringSimilarity(storedValue ?: "", extractedValue ?: "")
                        if (similarity > 0.8) {
                            matchCount += similarity
                        }
                    }
                }
            }

            return if (totalFields > 0) matchCount / totalFields else 0.0
        } catch (e: Exception) {
            return 0.5 // Default confidence if OCR comparison fails
        }
    }

    private fun calculateStringSimilarity(str1: String, str2: String): Double {
        val longer = if (str1.length > str2.length) str1 else str2
        val shorter = if (str1.length > str2.length) str2 else str1

        if (longer.isEmpty()) return 1.0

        return (longer.length - editDistance(longer, shorter)) / longer.length.toDouble()
    }

    private fun editDistance(str1: String, str2: String): Int {
        val dp = Array(str1.length + 1) { IntArray(str2.length + 1) }

        for (i in 0..str1.length) dp[i][0] = i
        for (j in 0..str2.length) dp[0][j] = j

        for (i in 1..str1.length) {
            for (j in 1..str2.length) {
                dp[i][j] = if (str1[i - 1] == str2[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }

        return dp[str1.length][str2.length]
    }

    // ========== EXISTING UNIVERSITY MANAGEMENT FUNCTIONS ==========

    @Transaction(intent = Transaction.TYPE.SUBMIT)
    fun registerUniversity(
        ctx: Context,
        universityCode: String,
        universityName: String,
        country: String,
        contactEmail: String,
        publicKey: String,
        initialStake: Double
    ): String {
        val stub = ctx.stub

        // Only attestation authority can register universities
        if (ctx.clientIdentity.mspId != "AttestationMSP") {
            throw ChaincodeException("Only attestation authority can register universities")
        }

        if (initialStake < MINIMUM_STAKE) {
            throw ChaincodeException("Initial stake must be at least $MINIMUM_STAKE")
        }

        val university = University(
            universityCode = universityCode,
            universityName = universityName,
            country = country,
            contactEmail = contactEmail,
            publicKey = publicKey,
            registrationDate = LocalDateTime.now(),
            isActive = true,
            stake = initialStake,
            totalEarnings = 0.0,
            verificationCount = 0,
            submissionCount = 0
        )

        val key = UNIVERSITY_PREFIX + universityCode
        stub.putState(key, objectMapper.writeValueAsBytes(university))

        ctx.stub.setEvent("UniversityRegistered", objectMapper.writeValueAsBytes(
            mapOf(
                "universityCode" to universityCode,
                "universityName" to universityName,
                "timestamp" to LocalDateTime.now().toString()
            )
        ))

        return "University registered successfully"
    }

    @Transaction(intent = Transaction.TYPE.EVALUATE)
    fun getAllUniversities(ctx: Context): String {
        val stub = ctx.stub
        val universities = mutableListOf<University>()

        val iterator = stub.getStateByPartialCompositeKey(UNIVERSITY_PREFIX)
        while (iterator.hasNext()) {
            val result = iterator.next()
            val university = objectMapper.readValue(result.value, University::class.java)
            universities.add(university)
        }
        iterator.close()

        return objectMapper.writeValueAsString(universities)
    }

    @Transaction(intent = Transaction.TYPE.EVALUATE)
    fun getUniversity(ctx: Context, universityCode: String): String {
        val stub = ctx.stub
        val key = UNIVERSITY_PREFIX + universityCode
        val universityBytes = stub.getState(key)

        if (universityBytes == null || universityBytes.isEmpty()) {
            throw ChaincodeException("University not found: $universityCode")
        }

        return String(universityBytes)
    }
}