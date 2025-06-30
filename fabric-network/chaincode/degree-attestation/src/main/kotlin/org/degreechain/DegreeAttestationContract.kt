package org.degreechain

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.degreechain.models.*
import org.hyperledger.fabric.contract.Context
import org.hyperledger.fabric.contract.ContractInterface
import org.hyperledger.fabric.contract.annotation.*
import org.hyperledger.fabric.shim.ChaincodeException
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator
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
            address = "Global Network",
            contactEmail = "admin@attestation.org",
            publicKey = "attestation_public_key",
            stakeAmount = 0.0,
            status = "ACTIVE",
            totalDegreesIssued = 0L,
            revenue = 0.0,
            joinedAt = LocalDateTime.now().toString(),
            lastActive = LocalDateTime.now().toString(),
            version = 1L
        )

        val key = UNIVERSITY_PREFIX + ATTESTATION_ORG
        stub.putState(key, objectMapper.writeValueAsBytes(attestationOrg))
    }

    // ==================== HASH-BASED OPERATIONS ====================

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

        // Check if university exists
        val universityKey = UNIVERSITY_PREFIX + institutionName
        val universityBytes = stub.getState(universityKey)
        if (universityBytes == null || universityBytes.isEmpty()) {
            throw ChaincodeException("University not registered: $institutionName")
        }

        val university = objectMapper.readValue(universityBytes, University::class.java)

        // Check if hash already exists
        val hashKey = HASH_PREFIX + certificateHash
        val existingDegreeBytes = stub.getState(hashKey)
        if (existingDegreeBytes != null && existingDegreeBytes.isNotEmpty()) {
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
            verificationCount = 0,
            lastVerified = null
        )

        // Store degree record
        val degreeKey = DEGREE_PREFIX + degreeId
        stub.putState(degreeKey, objectMapper.writeValueAsBytes(degree))

        // Store hash mapping for quick lookup
        stub.putState(hashKey, degreeId.toByteArray())

        // Update university statistics
        val updatedUniversity = university.copy(
            totalDegreesIssued = university.totalDegreesIssued + 1,
            lastActive = LocalDateTime.now().toString()
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
        ocrData: String?
    ): String {
        val stub = ctx.stub

        // Find degree by hash
        val hashKey = HASH_PREFIX + extractedHash
        val degreeIdBytes = stub.getState(hashKey)

        if (degreeIdBytes == null || degreeIdBytes.isEmpty()) {
            return objectMapper.writeValueAsString(
                VerificationResultWithConfidence(
                    verified = false,
                    degreeId = null,
                    degree = null,
                    verificationMethod = "HASH_NOT_FOUND",
                    confidence = 0.0,
                    message = "Certificate hash not found in blockchain",
                    timestamp = LocalDateTime.now()
                )
            )
        }

        val degreeId = String(degreeIdBytes)
        val degreeKey = DEGREE_PREFIX + degreeId
        val degreeBytes = stub.getState(degreeKey)

        if (degreeBytes == null || degreeBytes.isEmpty()) {
            throw ChaincodeException("Degree record not found: $degreeId")
        }

        val degree = objectMapper.readValue(degreeBytes, DegreeWithHash::class.java)

        // Check if degree is active
        if (degree.status != DegreeStatus.ACTIVE) {
            return objectMapper.writeValueAsString(
                VerificationResultWithConfidence(
                    verified = false,
                    degreeId = degreeId,
                    degree = degree,
                    verificationMethod = "DEGREE_INACTIVE",
                    confidence = 0.0,
                    message = "Degree status: ${degree.status}",
                    timestamp = LocalDateTime.now()
                )
            )
        }

        // Calculate verification confidence
        var confidence = 1.0 // Hash match gives 100% confidence
        var verificationMethod = "HASH_MATCH"

        // If OCR data is provided, compare it for additional verification
        if (!ocrData.isNullOrBlank() && degree.ocrData.isNotBlank()) {
            val ocrConfidence = calculateOcrMatchConfidence(degree.ocrData, ocrData)
            confidence = (confidence + ocrConfidence) / 2.0 // Average the confidences
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
                lastActive = LocalDateTime.now().toString()
            )
            stub.putState(universityKey, objectMapper.writeValueAsBytes(updatedUniversity))
        }

        // Log verification
        val verificationId = UUID.randomUUID().toString()
        val verification = VerificationLogEntry(
            verificationId = verificationId,
            degreeId = degreeId,
            verifierOrg = getMspIdFromContext(ctx), // Use helper method to get MSP ID
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

        val iterator: QueryResultsIterator<org.hyperledger.fabric.shim.ledger.KeyValue> =
            stub.getStateByPartialCompositeKey(VERIFICATION_PREFIX)

        try {
            for (result in iterator) { // Use Kotlin's for-in loop instead of hasNext/next
                val verification = objectMapper.readValue(result.value, VerificationLogEntry::class.java)
                if (verification.degreeId == degreeId) {
                    verifications.add(verification)
                }
            }
        } finally {
            iterator.close()
        }

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
        if (getMspIdFromContext(ctx) != "AttestationMSP") {
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

    /**
     * Extract MSP ID from client identity using available methods
     * Works around the private mspId field issue in newer Fabric versions
     */
    private fun getMspIdFromContext(ctx: Context): String {
        return try {
            // Method 1: Try to access mspId directly (for compatibility with older versions)
            try {
                // This might work in some versions - let's try reflection as a fallback
                val field = ctx.clientIdentity.javaClass.getDeclaredField("mspId")
                field.isAccessible = true
                return field.get(ctx.clientIdentity) as String
            } catch (e: Exception) {
                // If reflection fails, fall back to parsing the client ID
            }

            // Method 2: Parse from client ID string
            val clientId = ctx.clientIdentity.id

            // Method 3: Parse from X509 certificate if available
            val cert = ctx.clientIdentity.x509Certificate
            if (cert != null) {
                val subjectDN = cert.subjectDN.toString()

                // Look for organizational unit (OU) that contains MSP information
                val ouRegex = "OU=([^,]+)".toRegex()
                val matches = ouRegex.findAll(subjectDN)

                for (match in matches) {
                    val ou = match.groupValues[1]
                    when {
                        ou.contains("AttestationMSP", ignoreCase = true) -> return "AttestationMSP"
                        ou.contains("UniversityMSP", ignoreCase = true) -> return "UniversityMSP"
                        ou.contains("EmployerMSP", ignoreCase = true) -> return "EmployerMSP"
                        ou.contains("attestation", ignoreCase = true) -> return "AttestationMSP"
                        ou.contains("university", ignoreCase = true) -> return "UniversityMSP"
                        ou.contains("employer", ignoreCase = true) -> return "EmployerMSP"
                    }
                }
            }

            // Method 4: Fallback - parse from client ID string patterns
            when {
                clientId.contains("attestation", ignoreCase = true) -> "AttestationMSP"
                clientId.contains("university", ignoreCase = true) -> "UniversityMSP"
                clientId.contains("employer", ignoreCase = true) -> "EmployerMSP"
                clientId.contains("AttestationMSP", ignoreCase = true) -> "AttestationMSP"
                clientId.contains("UniversityMSP", ignoreCase = true) -> "UniversityMSP"
                clientId.contains("EmployerMSP", ignoreCase = true) -> "EmployerMSP"
                else -> "UnknownMSP"
            }
        } catch (e: Exception) {
            "UnknownMSP"
        }
    }

    private fun calculateOcrMatchConfidence(storedOcrData: String, extractedOcrData: String): Double {
        return try {
            val storedData = objectMapper.readValue(storedOcrData, Map::class.java) as Map<String, Any>
            val extractedData = objectMapper.readValue(extractedOcrData, Map::class.java) as Map<String, Any>

            val keyFields = listOf("studentName", "degreeName", "institutionName", "issuanceDate", "certificateNumber")
            var matchCount = 0.0
            var totalFields = 0

            for (field in keyFields) {
                if (storedData.containsKey(field) && extractedData.containsKey(field)) {
                    totalFields++
                    val storedValue = storedData[field]?.toString()?.trim()?.lowercase()
                    val extractedValue = extractedData[field]?.toString()?.trim()?.lowercase()

                    if (storedValue == extractedValue) {
                        matchCount += 1.0
                    } else {
                        // Check for partial matches (useful for name variations)
                        val similarity = calculateStringSimilarity(storedValue ?: "", extractedValue ?: "")
                        if (similarity > 0.8) {
                            matchCount += similarity
                        }
                    }
                }
            }

            return if (totalFields > 0) matchCount / totalFields.toDouble() else 0.0
        } catch (e: Exception) {
            return 0.5 // Default confidence if OCR comparison fails
        }
    }

    private fun calculateStringSimilarity(str1: String, str2: String): Double {
        val longer = if (str1.length > str2.length) str1 else str2
        val shorter = if (str1.length > str2.length) str2 else str1

        if (longer.isEmpty()) return 1.0

        return (longer.length - editDistance(longer, shorter)).toDouble() / longer.length.toDouble()
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

    // ========== UNIVERSITY MANAGEMENT FUNCTIONS ==========

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
        if (getMspIdFromContext(ctx) != "AttestationMSP") {
            throw ChaincodeException("Only attestation authority can register universities")
        }

        if (initialStake < MINIMUM_STAKE) {
            throw ChaincodeException("Initial stake must be at least $MINIMUM_STAKE")
        }

        val university = University(
            universityCode = universityCode,
            universityName = universityName,
            country = country,
            address = "", // Default empty address
            contactEmail = contactEmail,
            publicKey = publicKey,
            stakeAmount = initialStake,
            status = "ACTIVE",
            totalDegreesIssued = 0L,
            revenue = 0.0,
            joinedAt = LocalDateTime.now().toString(),
            lastActive = LocalDateTime.now().toString(),
            version = 1L
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

        val iterator: QueryResultsIterator<org.hyperledger.fabric.shim.ledger.KeyValue> =
            stub.getStateByPartialCompositeKey(UNIVERSITY_PREFIX)

        try {
            for (result in iterator) { // Use Kotlin's for-in loop
                val university = objectMapper.readValue(result.value, University::class.java)
                universities.add(university)
            }
        } finally {
            iterator.close()
        }

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