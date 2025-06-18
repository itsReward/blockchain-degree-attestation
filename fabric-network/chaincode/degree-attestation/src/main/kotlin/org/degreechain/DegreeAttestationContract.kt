package org.degreechain

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.degreechain.models.*
import org.degreechain.utils.ValidationUtils
import org.degreechain.utils.CryptoUtils
import org.hyperledger.fabric.contract.Context
import org.hyperledger.fabric.contract.ContractInterface
import org.hyperledger.fabric.contract.annotation.*
import org.hyperledger.fabric.shim.ChaincodeException
import org.hyperledger.fabric.shim.ChaincodeStub
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@Contract(
    name = "DegreeAttestationContract",
    info = Info(
        title = "Degree Attestation Smart Contract",
        description = "Smart contract for blockchain-based degree verification system",
        version = "1.0.0"
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

        const val ATTESTATION_ORG = "ATTESTATION_AUTHORITY"
        const val VERIFICATION_FEE = 10.0 // Base verification fee
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
            address = "Decentralized Network",
            contactEmail = "authority@degreechain.org",
            publicKey = "ATTESTATION_AUTHORITY_PUBLIC_KEY",
            stakeAmount = 0.0,
            status = "ACTIVE",
            joinedAt = getCurrentTimestamp(),
            lastActive = getCurrentTimestamp()
        )

        stub.putState(
            UNIVERSITY_PREFIX + ATTESTATION_ORG,
            objectMapper.writeValueAsBytes(attestationOrg)
        )
    }

    // ==================== UNIVERSITY MANAGEMENT ====================

    @Transaction(intent = Transaction.TYPE.SUBMIT)
    fun enrollUniversity(
        ctx: Context,
        universityCode: String,
        universityName: String,
        country: String,
        address: String,
        contactEmail: String,
        publicKey: String,
        stakeAmount: Double
    ): String {
        val stub = ctx.stub

        // Validate input
        ValidationUtils.validateUniversityCode(universityCode)
        ValidationUtils.validateStakeAmount(stakeAmount)
        ValidationUtils.validateEmail(contactEmail)

        // Check if university already exists
        val existingUniversity = getUniversityByCode(ctx, universityCode)
        if (existingUniversity != null) {
            throw ChaincodeException("University already enrolled", "UNIVERSITY_EXISTS")
        }

        // Create university record
        val university = University(
            universityCode = universityCode,
            universityName = universityName,
            country = country,
            address = address,
            contactEmail = contactEmail,
            publicKey = publicKey,
            stakeAmount = stakeAmount,
            status = "PENDING_APPROVAL",
            joinedAt = getCurrentTimestamp(),
            lastActive = getCurrentTimestamp()
        )

        // Store university
        stub.putState(
            UNIVERSITY_PREFIX + universityCode,
            objectMapper.writeValueAsBytes(university)
        )

        // Create stake payment record
        val stakePayment = Payment(
            paymentId = UUID.randomUUID().toString(),
            fromOrganization = universityCode,
            toOrganization = ATTESTATION_ORG,
            amount = stakeAmount,
            currency = "USD",
            paymentType = "STAKE",
            status = "PENDING",
            transactionHash = null,
            relatedEntityId = universityCode,
            timestamp = getCurrentTimestamp()
        )

        stub.putState(
            PAYMENT_PREFIX + stakePayment.paymentId,
            objectMapper.writeValueAsBytes(stakePayment)
        )

        return "University enrollment initiated. Pending approval and stake payment."
    }

    @Transaction(intent = Transaction.TYPE.SUBMIT)
    fun approveUniversity(ctx: Context, universityCode: String): String {
        requireAttestationAuthority(ctx)

        val university = getUniversityByCode(ctx, universityCode)
            ?: throw ChaincodeException("University not found", "UNIVERSITY_NOT_FOUND")

        if (university.status != "PENDING_APPROVAL") {
            throw ChaincodeException("University not in pending status", "INVALID_STATUS")
        }

        val approvedUniversity = university.copy(
            status = "ACTIVE",
            lastActive = getCurrentTimestamp(),
            version = university.version + 1
        )

        ctx.stub.putState(
            UNIVERSITY_PREFIX + universityCode,
            objectMapper.writeValueAsBytes(approvedUniversity)
        )

        return "University approved and activated"
    }

    @Transaction(intent = Transaction.TYPE.SUBMIT)
    fun blacklistUniversity(ctx: Context, universityCode: String, reason: String): String {
        requireAttestationAuthority(ctx)

        val university = getUniversityByCode(ctx, universityCode)
            ?: throw ChaincodeException("University not found", "UNIVERSITY_NOT_FOUND")

        val blacklistedUniversity = university.copy(
            status = "BLACKLISTED",
            lastActive = getCurrentTimestamp(),
            version = university.version + 1,
            accreditation = university.accreditation + ("blacklistReason" to reason)
        )

        ctx.stub.putState(
            UNIVERSITY_PREFIX + universityCode,
            objectMapper.writeValueAsBytes(blacklistedUniversity)
        )

        // Confiscate stake
        confiscateStake(ctx, universityCode)

        return "University blacklisted and stake confiscated"
    }

    // ==================== DEGREE MANAGEMENT ====================

    @Transaction(intent = Transaction.TYPE.SUBMIT)
    fun submitDegree(
        ctx: Context,
        certificateNumber: String,
        studentName: String,
        degreeName: String,
        facultyName: String,
        degreeClassification: String,
        issuanceDate: String,
        expiryDate: String?,
        certificateHash: String
    ): String {
        val stub = ctx.stub
        val universityCode = getInvokerOrganization(ctx)

        // Validate university
        val university = getUniversityByCode(ctx, universityCode)
            ?: throw ChaincodeException("University not registered", "UNIVERSITY_NOT_REGISTERED")

        if (university.status != "ACTIVE") {
            throw ChaincodeException("University not active", "UNIVERSITY_NOT_ACTIVE")
        }

        // Validate input
        ValidationUtils.validateCertificateNumber(certificateNumber)
        ValidationUtils.validateHash(certificateHash)

        // Check if degree already exists
        val existingDegree = getDegreeByNumber(ctx, certificateNumber)
        if (existingDegree != null) {
            throw ChaincodeException("Degree already exists", "DEGREE_EXISTS")
        }

        // Create degree record
        val degree = Degree(
            certificateNumber = certificateNumber,
            universityCode = universityCode,
            studentName = studentName,
            degreeName = degreeName,
            facultyName = facultyName,
            degreeClassification = degreeClassification,
            issuanceDate = issuanceDate,
            expiryDate = expiryDate,
            certificateHash = certificateHash,
            status = "ACTIVE",
            createdAt = getCurrentTimestamp(),
            updatedAt = getCurrentTimestamp()
        )

        // Store degree
        stub.putState(
            DEGREE_PREFIX + certificateNumber,
            objectMapper.writeValueAsBytes(degree)
        )

        // Update university statistics
        val updatedUniversity = university.copy(
            totalDegreesIssued = university.totalDegreesIssued + 1,
            lastActive = getCurrentTimestamp(),
            version = university.version + 1
        )

        stub.putState(
            UNIVERSITY_PREFIX + universityCode,
            objectMapper.writeValueAsBytes(updatedUniversity)
        )

        return "Degree submitted successfully"
    }

    @Transaction(intent = Transaction.TYPE.EVALUATE)
    fun verifyDegree(
        ctx: Context,
        certificateNumber: String,
        verifierOrganization: String,
        verifierEmail: String,
        providedHash: String?
    ): String {
        val degree = getDegreeByNumber(ctx, certificateNumber)
            ?: throw ChaincodeException("Degree not found", "DEGREE_NOT_FOUND")

        val university = getUniversityByCode(ctx, degree.universityCode)
            ?: throw ChaincodeException("Issuing university not found", "UNIVERSITY_NOT_FOUND")

        if (university.status == "BLACKLISTED") {
            throw ChaincodeException("Issuing university is blacklisted", "UNIVERSITY_BLACKLISTED")
        }

        if (degree.status != "ACTIVE") {
            throw ChaincodeException("Degree is not active", "DEGREE_NOT_ACTIVE")
        }

        // Check expiry
        if (degree.expiryDate != null && isExpired(degree.expiryDate)) {
            throw ChaincodeException("Degree has expired", "DEGREE_EXPIRED")
        }

        var verificationResult = "VERIFIED"
        var confidence = 1.0
        var extractionMethod = "HASH"

        // Verify hash if provided
        if (providedHash != null) {
            if (degree.certificateHash != providedHash.lowercase()) {
                verificationResult = "FAILED"
                confidence = 0.0
            }
        }

        val verificationResponse = mapOf(
            "certificateNumber" to degree.certificateNumber,
            "studentName" to degree.studentName,
            "degreeName" to degree.degreeName,
            "facultyName" to degree.facultyName,
            "degreeClassification" to degree.degreeClassification,
            "issuanceDate" to degree.issuanceDate,
            "universityName" to university.universityName,
            "universityCode" to university.universityCode,
            "verificationResult" to verificationResult,
            "confidence" to confidence,
            "extractionMethod" to extractionMethod,
            "verificationTimestamp" to getCurrentTimestamp()
        )

        return objectMapper.writeValueAsString(verificationResponse)
    }

    @Transaction(intent = Transaction.TYPE.SUBMIT)
    fun processVerificationPayment(
        ctx: Context,
        certificateNumber: String,
        verifierOrganization: String,
        verifierEmail: String,
        paymentAmount: Double
    ): String {
        val degree = getDegreeByNumber(ctx, certificateNumber)
            ?: throw ChaincodeException("Degree not found", "DEGREE_NOT_FOUND")

        val university = getUniversityByCode(ctx, degree.universityCode)
            ?: throw ChaincodeException("University not found", "UNIVERSITY_NOT_FOUND")

        if (paymentAmount < VERIFICATION_FEE) {
            throw ChaincodeException("Insufficient payment amount", "INSUFFICIENT_PAYMENT")
        }

        val verificationId = UUID.randomUUID().toString()
        val paymentId = UUID.randomUUID().toString()

        // Create verification record
        val verification = Verification(
            verificationId = verificationId,
            certificateNumber = certificateNumber,
            verifierOrganization = verifierOrganization,
            verifierEmail = verifierEmail,
            verificationTimestamp = getCurrentTimestamp(),
            verificationResult = "VERIFIED",
            confidence = 1.0,
            paymentId = paymentId,
            verificationFee = paymentAmount,
            extractionMethod = "BLOCKCHAIN"
        )

        ctx.stub.putState(
            VERIFICATION_PREFIX + verificationId,
            objectMapper.writeValueAsBytes(verification)
        )

        // Process revenue sharing (50% to university, 50% to attestation authority)
        val universityShare = paymentAmount * 0.5
        val authorityShare = paymentAmount * 0.5

        // Create payment records
        createPaymentRecord(ctx, paymentId, verifierOrganization, degree.universityCode,
            universityShare, "VERIFICATION_FEE", certificateNumber)

        createPaymentRecord(ctx, UUID.randomUUID().toString(), verifierOrganization,
            ATTESTATION_ORG, authorityShare, "VERIFICATION_FEE", certificateNumber)

        // Update university revenue
        val updatedUniversity = university.copy(
            revenue = university.revenue + universityShare,
            lastActive = getCurrentTimestamp(),
            version = university.version + 1
        )

        ctx.stub.putState(
            UNIVERSITY_PREFIX + degree.universityCode,
            objectMapper.writeValueAsBytes(updatedUniversity)
        )

        return "Verification payment processed successfully"
    }

    // ==================== QUERY FUNCTIONS ====================

    @Transaction(intent = Transaction.TYPE.EVALUATE)
    fun getUniversity(ctx: Context, universityCode: String): String {
        val university = getUniversityByCode(ctx, universityCode)
            ?: throw ChaincodeException("University not found", "UNIVERSITY_NOT_FOUND")

        return objectMapper.writeValueAsString(university)
    }

    @Transaction(intent = Transaction.TYPE.EVALUATE)
    fun getDegree(ctx: Context, certificateNumber: String): String {
        val degree = getDegreeByNumber(ctx, certificateNumber)
            ?: throw ChaincodeException("Degree not found", "DEGREE_NOT_FOUND")

        return objectMapper.writeValueAsString(degree)
    }

    @Transaction(intent = Transaction.TYPE.EVALUATE)
    fun getAllUniversities(ctx: Context): String {
        val queryString = """{"selector":{"universityCode":{"${"$"}regex":".*"}}}"""
        val results = queryBySelector(ctx, queryString)
        return objectMapper.writeValueAsString(results)
    }

    @Transaction(intent = Transaction.TYPE.EVALUATE)
    fun getUniversityStatistics(ctx: Context, universityCode: String): String {
        val university = getUniversityByCode(ctx, universityCode)
            ?: throw ChaincodeException("University not found", "UNIVERSITY_NOT_FOUND")

        val stats = mapOf(
            "universityCode" to university.universityCode,
            "universityName" to university.universityName,
            "status" to university.status,
            "totalDegreesIssued" to university.totalDegreesIssued,
            "revenue" to university.revenue,
            "stakeAmount" to university.stakeAmount,
            "joinedAt" to university.joinedAt,
            "lastActive" to university.lastActive
        )

        return objectMapper.writeValueAsString(stats)
    }

    // ==================== UTILITY FUNCTIONS ====================

    private fun getUniversityByCode(ctx: Context, universityCode: String): University? {
        val universityBytes = ctx.stub.getState(UNIVERSITY_PREFIX + universityCode)
        return if (universityBytes != null && universityBytes.isNotEmpty()) {
            objectMapper.readValue(universityBytes, University::class.java)
        } else null
    }

    private fun getDegreeByNumber(ctx: Context, certificateNumber: String): Degree? {
        val degreeBytes = ctx.stub.getState(DEGREE_PREFIX + certificateNumber)
        return if (degreeBytes != null && degreeBytes.isNotEmpty()) {
            objectMapper.readValue(degreeBytes, Degree::class.java)
        } else null
    }

    private fun createPaymentRecord(
        ctx: Context,
        paymentId: String,
        fromOrg: String,
        toOrg: String,
        amount: Double,
        paymentType: String,
        relatedEntityId: String
    ) {
        val payment = Payment(
            paymentId = paymentId,
            fromOrganization = fromOrg,
            toOrganization = toOrg,
            amount = amount,
            currency = "USD",
            paymentType = paymentType,
            status = "COMPLETED",
            transactionHash = null,
            relatedEntityId = relatedEntityId,
            timestamp = getCurrentTimestamp()
        )

        ctx.stub.putState(
            PAYMENT_PREFIX + paymentId,
            objectMapper.writeValueAsBytes(payment)
        )
    }

    private fun confiscateStake(ctx: Context, universityCode: String) {
        val confiscationId = UUID.randomUUID().toString()
        val payment = Payment(
            paymentId = confiscationId,
            fromOrganization = universityCode,
            toOrganization = ATTESTATION_ORG,
            amount = 0.0, // Will be updated with actual stake amount
            currency = "USD",
            paymentType = "STAKE_CONFISCATION",
            status = "COMPLETED",
            transactionHash = null,
            relatedEntityId = universityCode,
            timestamp = getCurrentTimestamp()
        )

        ctx.stub.putState(
            PAYMENT_PREFIX + confiscationId,
            objectMapper.writeValueAsBytes(payment)
        )
    }

    private fun queryBySelector(ctx: Context, queryString: String): List<Map<String, Any>> {
        val resultsIterator = ctx.stub.getQueryResult(queryString)
        val results = mutableListOf<Map<String, Any>>()

        while (resultsIterator.hasNext()) {
            val queryResult = resultsIterator.next()
            val record = objectMapper.readValue(queryResult.stringValue, Map::class.java)
            results.add(record as Map<String, Any>)
        }

        resultsIterator.close()
        return results
    }

    private fun requireAttestationAuthority(ctx: Context) {
        val invokerOrg = getInvokerOrganization(ctx)
        if (invokerOrg != ATTESTATION_ORG) {
            throw ChaincodeException("Operation requires attestation authority", "UNAUTHORIZED")
        }
    }

    private fun getInvokerOrganization(ctx: Context): String {
        // In a real implementation, this would extract the organization from the client identity
        // For now, we'll use a placeholder
        return ctx.clientIdentity.mspId
    }

    private fun getCurrentTimestamp(): String {
        return LocalDateTime.now().format(dateFormatter)
    }

    private fun isExpired(expiryDate: String): Boolean {
        val expiry = LocalDateTime.parse(expiryDate, dateFormatter)
        return LocalDateTime.now().isAfter(expiry)
    }
}