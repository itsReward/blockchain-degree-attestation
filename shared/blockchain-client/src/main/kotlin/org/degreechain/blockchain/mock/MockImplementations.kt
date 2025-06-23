// shared/blockchain-client/src/main/kotlin/org/degreechain/blockchain/mock/MockImplementations.kt
package org.degreechain.blockchain.mock

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.degreechain.blockchain.*
import org.degreechain.blockchain.models.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

/**
 * Mock implementation of FabricGatewayClient for testing without actual blockchain
 */
class MockFabricGatewayClient : FabricGatewayClient(null) {
    private val objectMapper = ObjectMapper().registerKotlinModule()
    private var isInitialized = false

    override suspend fun initialize(): FabricGatewayClient = withContext(Dispatchers.IO) {
        logger.info { "Initializing Mock Fabric Gateway Client" }
        isInitialized = true
        this@MockFabricGatewayClient
    }

    override suspend fun submitTransaction(
        functionName: String,
        vararg args: String
    ): TransactionResult = withContext(Dispatchers.IO) {
        logger.debug { "Mock submitting transaction: $functionName with args: ${args.contentToString()}" }

        TransactionResult(
            transactionId = "mock-tx-${UUID.randomUUID()}",
            result = getMockTransactionResult(functionName, args),
            success = true,
            blockNumber = Random.nextLong(1000, 9999),
            timestamp = System.currentTimeMillis()
        )
    }

    override suspend fun evaluateTransaction(
        functionName: String,
        vararg args: String
    ): BlockchainResponse<String> = withContext(Dispatchers.IO) {
        logger.debug { "Mock evaluating transaction: $functionName with args: ${args.contentToString()}" }

        BlockchainResponse(
            success = true,
            data = getMockTransactionResult(functionName, args),
            transactionId = null,
            blockNumber = Random.nextLong(1000, 9999),
            timestamp = System.currentTimeMillis()
        )
    }

    override suspend fun submitTransactionWithTimeout(
        functionName: String,
        timeoutSeconds: Long,
        vararg args: String
    ): TransactionResult = submitTransaction(functionName, *args)

    override fun isConnected(): Boolean = isInitialized

    override fun getNetworkName(): String? = "mock-network"

    override fun getContractName(): String = "MockDegreeAttestationContract"

    override suspend fun reconnect(): Boolean = true

    override fun close() {
        isInitialized = false
        logger.info { "Mock Fabric Gateway Client closed" }
    }

    private fun getMockTransactionResult(functionName: String, args: Array<out String>): String {
        return when (functionName) {
            "ping" -> "pong"
            "initLedger" -> "Ledger initialized successfully"
            "getAllUniversities" -> getMockUniversities()
            "getUniversity" -> getMockUniversity(args.getOrNull(0) ?: "UNI001")
            "getSystemStatistics" -> getMockSystemStatistics()
            "getAllDegrees" -> getMockDegrees()
            "getDegree" -> getMockDegree(args.getOrNull(0) ?: "CERT001")
            "verifyDegree" -> getMockVerificationResult(args.getOrNull(0) ?: "CERT001")
            "enrollUniversity" -> "University enrolled successfully: ${args.getOrNull(0)}"
            "submitDegree" -> "Degree submitted successfully: ${args.getOrNull(0)}"
            "processVerificationPayment" -> "Payment processed successfully"
            "approveUniversity" -> "University approved successfully: ${args.getOrNull(0)}"
            "blacklistUniversity" -> "University blacklisted: ${args.getOrNull(0)}"
            "revokeDegree" -> "Degree revoked: ${args.getOrNull(0)}"
            "recordVerification" -> "Verification recorded: ${args.getOrNull(0)}"
            "getVerification" -> getMockVerification(args.getOrNull(0) ?: "VER001")
            "getVerificationHistory" -> getMockVerificationHistory(args.getOrNull(0) ?: "CERT001")
            "processStakePayment" -> "Stake payment processed: ${args.getOrNull(1)}"
            "withdrawStake" -> "Stake withdrawn for: ${args.getOrNull(0)}"
            "confiscateStake" -> "Stake confiscated for: ${args.getOrNull(0)}"
            "getPaymentHistory" -> getMockPaymentHistory(args.getOrNull(0) ?: "ORG001")
            "createProposal" -> "Proposal created: ${args.getOrNull(0)}"
            "voteOnProposal" -> "Vote recorded for proposal: ${args.getOrNull(0)}"
            "getProposal" -> getMockProposal(args.getOrNull(0) ?: "PROP001")
            "getAllProposals" -> getMockProposals()
            "finalizeProposal" -> "Proposal finalized: ${args.getOrNull(0)}"
            "getVerificationStatistics" -> getMockVerificationStatistics(args.getOrNull(0) ?: "ORG001")
            "getRevenueReport" -> getMockRevenueReport(args.getOrNull(0) ?: "ORG001")
            "getDegreesByUniversity" -> getMockDegreesByUniversity(args.getOrNull(0) ?: "UNI001")
            "getUniversityStatistics" -> getMockUniversityStatistics(args.getOrNull(0) ?: "UNI001")
            else -> """{"success": true, "message": "Mock response for $functionName"}"""
        }
    }

    private fun getMockUniversities(): String = objectMapper.writeValueAsString(
        listOf(
            mapOf(
                "universityCode" to "UNI001",
                "universityName" to "Mock University of Technology",
                "country" to "MockLand",
                "address" to "123 Mock Street, MockCity",
                "contactEmail" to "contact@mock.edu",
                "isApproved" to true,
                "isBlacklisted" to false,
                "stakeAmount" to 1000.0,
                "totalDegrees" to 150,
                "totalVerifications" to 1250,
                "enrollmentDate" to LocalDateTime.now().minusYears(2).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "lastActivity" to LocalDateTime.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            ),
            mapOf(
                "universityCode" to "UNI002",
                "universityName" to "Mock State University",
                "country" to "MockLand",
                "address" to "456 Academic Ave, MockCity",
                "contactEmail" to "admin@mockstate.edu",
                "isApproved" to true,
                "isBlacklisted" to false,
                "stakeAmount" to 1500.0,
                "totalDegrees" to 89,
                "totalVerifications" to 756,
                "enrollmentDate" to LocalDateTime.now().minusYears(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "lastActivity" to LocalDateTime.now().minusHours(6).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            ),
            mapOf(
                "universityCode" to "UNI003",
                "universityName" to "Mock International University",
                "country" to "MockLand",
                "address" to "789 Global Plaza, MockCity",
                "contactEmail" to "info@mockinternational.edu",
                "isApproved" to false,
                "isBlacklisted" to false,
                "stakeAmount" to 2000.0,
                "totalDegrees" to 0,
                "totalVerifications" to 0,
                "enrollmentDate" to LocalDateTime.now().minusWeeks(2).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "lastActivity" to LocalDateTime.now().minusWeeks(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            )
        )
    )

    private fun getMockUniversity(universityCode: String): String = objectMapper.writeValueAsString(
        mapOf(
            "universityCode" to universityCode,
            "universityName" to "Mock University ($universityCode)",
            "country" to "MockLand",
            "address" to "123 Mock Street, MockCity",
            "contactEmail" to "contact@$universityCode.mock.edu",
            "publicKey" to "mock-public-key-${UUID.randomUUID()}",
            "isApproved" to true,
            "isBlacklisted" to false,
            "blacklistReason" to null,
            "stakeAmount" to 1000.0,
            "totalDegrees" to Random.nextInt(50, 200),
            "totalVerifications" to Random.nextInt(100, 1000),
            "enrollmentDate" to LocalDateTime.now().minusMonths(Random.nextLong(6, 24)).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            "lastActivity" to LocalDateTime.now().minusDays(Random.nextLong(0, 30)).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )
    )

    private fun getMockSystemStatistics(): String = objectMapper.writeValueAsString(
        mapOf(
            "totalUniversities" to 25,
            "approvedUniversities" to 23,
            "blacklistedUniversities" to 1,
            "totalDegrees" to 2847,
            "totalVerifications" to 15632,
            "totalStakeAmount" to 25000.0,
            "totalRevenue" to 156320.0,
            "averageVerificationTime" to 2.3,
            "successfulVerifications" to 15401,
            "failedVerifications" to 231,
            "lastUpdated" to LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )
    )

    private fun getMockDegrees(): String = objectMapper.writeValueAsString(
        listOf(
            mapOf(
                "certificateNumber" to "CERT001",
                "studentName" to "John Mock Doe",
                "degreeName" to "Bachelor of Science in Computer Science",
                "facultyName" to "Faculty of Engineering",
                "degreeClassification" to "First Class Honours",
                "universityCode" to "UNI001",
                "issuanceDate" to "2023-06-15",
                "expiryDate" to null,
                "certificateHash" to "mock-hash-cert001",
                "isRevoked" to false,
                "revocationReason" to null,
                "revocationDate" to null,
                "createdAt" to LocalDateTime.now().minusMonths(6).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "verificationCount" to 12
            ),
            mapOf(
                "certificateNumber" to "CERT002",
                "studentName" to "Jane Mock Smith",
                "degreeName" to "Master of Business Administration",
                "facultyName" to "Business School",
                "degreeClassification" to "Distinction",
                "universityCode" to "UNI002",
                "issuanceDate" to "2023-12-20",
                "expiryDate" to null,
                "certificateHash" to "mock-hash-cert002",
                "isRevoked" to false,
                "revocationReason" to null,
                "revocationDate" to null,
                "createdAt" to LocalDateTime.now().minusMonths(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "verificationCount" to 5
            ),
            mapOf(
                "certificateNumber" to "CERT003",
                "studentName" to "Bob Mock Wilson",
                "degreeName" to "Bachelor of Arts in Economics",
                "facultyName" to "School of Social Sciences",
                "degreeClassification" to "Upper Second Class Honours",
                "universityCode" to "UNI001",
                "issuanceDate" to "2023-07-10",
                "expiryDate" to null,
                "certificateHash" to "mock-hash-cert003",
                "isRevoked" to false,
                "revocationReason" to null,
                "revocationDate" to null,
                "createdAt" to LocalDateTime.now().minusMonths(5).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "verificationCount" to 8
            )
        )
    )

    private fun getMockDegree(certificateNumber: String): String = objectMapper.writeValueAsString(
        mapOf(
            "certificateNumber" to certificateNumber,
            "studentName" to "Mock Student Name",
            "degreeName" to "Bachelor of Mock Studies",
            "facultyName" to "Faculty of Mock Sciences",
            "degreeClassification" to "Upper Second Class Honours",
            "universityCode" to "UNI001",
            "issuanceDate" to "2023-07-15",
            "expiryDate" to null,
            "certificateHash" to "mock-hash-${UUID.randomUUID()}",
            "isRevoked" to false,
            "revocationReason" to null,
            "revocationDate" to null,
            "createdAt" to LocalDateTime.now().minusMonths(4).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            "verificationCount" to Random.nextInt(1, 20)
        )
    )

    private fun getMockVerificationResult(certificateNumber: String): String = objectMapper.writeValueAsString(
        mapOf(
            "verificationResult" to "VERIFIED",
            "confidence" to 0.95,
            "studentName" to "Mock Student Name",
            "degreeName" to "Bachelor of Mock Studies",
            "facultyName" to "Faculty of Mock Sciences",
            "degreeClassification" to "Upper Second Class Honours",
            "universityCode" to "UNI001",
            "issuanceDate" to "2023-07-15",
            "verificationDate" to LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            "certificateNumber" to certificateNumber,
            "verifierOrganization" to "Mock Employer Corp",
            "verifierEmail" to "hr@mockemployer.com",
            "paymentAmount" to 10.0,
            "transactionId" to "mock-tx-${UUID.randomUUID()}"
        )
    )

    private fun getMockVerification(verificationId: String): String = objectMapper.writeValueAsString(
        mapOf(
            "verificationId" to verificationId,
            "certificateNumber" to "CERT001",
            "verifierOrganization" to "Mock Employer Corp",
            "verifierEmail" to "hr@mockemployer.com",
            "verificationResult" to "VERIFIED",
            "confidence" to 0.95,
            "verificationDate" to LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            "paymentAmount" to 10.0,
            "ipfsHash" to null,
            "additionalData" to mapOf("notes" to "Standard verification process")
        )
    )

    private fun getMockVerificationHistory(certificateNumber: String): String = objectMapper.writeValueAsString(
        listOf(
            mapOf(
                "verificationId" to "VER001",
                "verifierOrganization" to "Mock Corp",
                "verificationDate" to LocalDateTime.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "verificationResult" to "VERIFIED",
                "confidence" to 0.95
            ),
            mapOf(
                "verificationId" to "VER002",
                "verifierOrganization" to "Another Mock Company",
                "verificationDate" to LocalDateTime.now().minusDays(5).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "verificationResult" to "VERIFIED",
                "confidence" to 0.93
            )
        )
    )

    private fun getMockPaymentHistory(organizationCode: String): String = objectMapper.writeValueAsString(
        listOf(
            mapOf(
                "paymentId" to "PAY001",
                "paymentType" to "VERIFICATION_FEE",
                "fromOrganization" to organizationCode,
                "toOrganization" to "ATTESTATION_AUTHORITY",
                "amount" to 10.0,
                "paymentDate" to LocalDateTime.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "certificateNumber" to "CERT001",
                "status" to "COMPLETED",
                "transactionHash" to "mock-hash-${UUID.randomUUID()}",
                "description" to "Degree verification fee"
            ),
            mapOf(
                "paymentId" to "PAY002",
                "paymentType" to "STAKE_PAYMENT",
                "fromOrganization" to organizationCode,
                "toOrganization" to "ATTESTATION_AUTHORITY",
                "amount" to 1000.0,
                "paymentDate" to LocalDateTime.now().minusMonths(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "certificateNumber" to null,
                "status" to "COMPLETED",
                "transactionHash" to "mock-hash-${UUID.randomUUID()}",
                "description" to "University stake payment"
            )
        )
    )

    private fun getMockProposal(proposalId: String): String = objectMapper.writeValueAsString(
        mapOf(
            "proposalId" to proposalId,
            "proposalType" to "FEE_CHANGE",
            "title" to "Increase Verification Fee",
            "description" to "Proposal to increase verification fee from $10 to $15",
            "proposedChanges" to "Update verification fee configuration",
            "proposer" to "ATTESTATION_AUTHORITY",
            "status" to "ACTIVE",
            "createdDate" to LocalDateTime.now().minusDays(7).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            "votingDeadline" to LocalDateTime.now().plusDays(3).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            "votes" to mapOf(
                "UNI001" to mapOf(
                    "vote" to "YES",
                    "voteDate" to LocalDateTime.now().minusDays(2).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    "comments" to "Agreed, inflation justifies increase"
                ),
                "UNI002" to mapOf(
                    "vote" to "NO",
                    "voteDate" to LocalDateTime.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    "comments" to "Fee increase too high"
                )
            ),
            "requiredVotes" to 3,
            "currentVotes" to 2,
            "finalizedDate" to null
        )
    )

    private fun getMockProposals(): String = objectMapper.writeValueAsString(
        listOf(
            mapOf(
                "proposalId" to "PROP001",
                "proposalType" to "FEE_CHANGE",
                "status" to "ACTIVE",
                "createdDate" to LocalDateTime.now().minusDays(7).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            ),
            mapOf(
                "proposalId" to "PROP002",
                "proposalType" to "POLICY_UPDATE",
                "status" to "PASSED",
                "createdDate" to LocalDateTime.now().minusMonths(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            )
        )
    )

    private fun getMockVerificationStatistics(organizationCode: String): String = objectMapper.writeValueAsString(
        mapOf(
            "organizationCode" to organizationCode,
            "totalVerifications" to Random.nextInt(50, 500),
            "successfulVerifications" to Random.nextInt(40, 480),
            "failedVerifications" to Random.nextInt(1, 20),
            "averageVerificationTime" to Random.nextDouble(1.0, 5.0),
            "totalFeesGenerated" to Random.nextDouble(100.0, 5000.0),
            "mostVerifiedDegree" to "Bachelor of Computer Science",
            "lastVerificationDate" to LocalDateTime.now().minusHours(Random.nextLong(1, 48)).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )
    )

    private fun getMockRevenueReport(organizationCode: String): String = objectMapper.writeValueAsString(
        mapOf(
            "organizationCode" to organizationCode,
            "totalRevenue" to Random.nextDouble(1000.0, 10000.0),
            "totalTransactions" to Random.nextInt(100, 1000),
            "averageTransactionValue" to Random.nextDouble(10.0, 50.0),
            "revenueByMonth" to mapOf(
                "2024-01" to Random.nextDouble(800.0, 1200.0),
                "2024-02" to Random.nextDouble(900.0, 1300.0),
                "2024-03" to Random.nextDouble(850.0, 1250.0)
            ),
            "transactionsByType" to mapOf(
                "VERIFICATION_FEE" to Random.nextInt(80, 200),
                "STAKE_PAYMENT" to Random.nextInt(1, 5),
                "REVENUE_DISTRIBUTION" to Random.nextInt(10, 30)
            )
        )
    )

    private fun getMockDegreesByUniversity(universityCode: String): String = objectMapper.writeValueAsString(
        listOf(
            mapOf("certificateNumber" to "MOCK001-$universityCode", "universityCode" to universityCode, "studentName" to "Student One"),
            mapOf("certificateNumber" to "MOCK002-$universityCode", "universityCode" to universityCode, "studentName" to "Student Two"),
            mapOf("certificateNumber" to "MOCK003-$universityCode", "universityCode" to universityCode, "studentName" to "Student Three")
        )
    )

    private fun getMockUniversityStatistics(universityCode: String): String = objectMapper.writeValueAsString(
        mapOf(
            "universityCode" to universityCode,
            "totalDegrees" to Random.nextInt(100, 500),
            "degreesThisYear" to Random.nextInt(20, 100),
            "totalVerifications" to Random.nextInt(500, 2000),
            "verificationsThisMonth" to Random.nextInt(50, 200),
            "averageVerificationTime" to Random.nextDouble(1.0, 5.0),
            "topDegreePrograms" to listOf(
                "Computer Science",
                "Business Administration",
                "Engineering"
            ),
            "revenueGenerated" to Random.nextDouble(5000.0, 20000.0),
            "lastDegreeSubmission" to LocalDateTime.now().minusDays(Random.nextLong(1, 30)).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )
    )
}

/**
 * Mock implementation of ContractInvoker for testing without actual blockchain
 */
class MockContractInvoker : ContractInvoker(MockFabricGatewayClient()) {
    private val mockFabricClient = MockFabricGatewayClient()

    override suspend fun ping(): Boolean = true

    override suspend fun initLedger(): String = "Mock ledger initialized successfully"

    override suspend fun enrollUniversity(
        universityCode: String,
        universityName: String,
        country: String,
        address: String,
        contactEmail: String,
        publicKey: String,
        stakeAmount: Double
    ): String = "Mock university enrolled: $universityCode"

    override suspend fun approveUniversity(universityCode: String): String =
        "Mock university approved: $universityCode"

    override suspend fun blacklistUniversity(universityCode: String, reason: String): String =
        "Mock university blacklisted: $universityCode"

    override suspend fun getUniversity(universityCode: String): String =
        mockFabricClient.evaluateTransaction("getUniversity", universityCode).data!!

    override suspend fun getAllUniversities(): String =
        mockFabricClient.evaluateTransaction("getAllUniversities").data!!

    override suspend fun getUniversityStatistics(universityCode: String): String =
        mockFabricClient.evaluateTransaction("getUniversityStatistics", universityCode).data!!

    override suspend fun submitDegree(
        certificateNumber: String,
        studentName: String,
        degreeName: String,
        facultyName: String,
        degreeClassification: String,
        issuanceDate: String,
        expiryDate: String?,
        certificateHash: String
    ): String = "Mock degree submitted: $certificateNumber"

    override suspend fun verifyDegree(
        certificateNumber: String,
        verifierOrganization: String,
        verifierEmail: String,
        providedHash: String?
    ): String = mockFabricClient.evaluateTransaction("verifyDegree", certificateNumber).data!!

    override suspend fun getDegree(certificateNumber: String): String =
        mockFabricClient.evaluateTransaction("getDegree", certificateNumber).data!!

    override suspend fun getAllDegrees(): String =
        mockFabricClient.evaluateTransaction("getAllDegrees").data!!

    override suspend fun getDegreesByUniversity(universityCode: String): String =
        mockFabricClient.evaluateTransaction("getDegreesByUniversity", universityCode).data!!

    override suspend fun revokeDegree(certificateNumber: String, reason: String): String =
        "Mock degree revoked: $certificateNumber"

    override suspend fun recordVerification(
        verificationId: String,
        certificateNumber: String,
        verifierOrganization: String,
        verifierEmail: String,
        verificationResult: String,
        confidence: Double
    ): String = "Mock verification recorded: $verificationId"

    override suspend fun getVerification(verificationId: String): String =
        mockFabricClient.evaluateTransaction("getVerification", verificationId).data!!

    override suspend fun getVerificationHistory(certificateNumber: String): String =
        mockFabricClient.evaluateTransaction("getVerificationHistory", certificateNumber).data!!

    override suspend fun processVerificationPayment(
        certificateNumber: String,
        verifierOrganization: String,
        verifierEmail: String,
        paymentAmount: Double
    ): String = "Mock payment processed: $paymentAmount"

    override suspend fun processStakePayment(
        universityCode: String,
        stakeAmount: Double,
        paymentMethod: String
    ): String = "Mock stake payment processed: $stakeAmount"

    override suspend fun withdrawStake(universityCode: String): String =
        "Mock stake withdrawn for: $universityCode"

    override suspend fun confiscateStake(universityCode: String, reason: String): String =
        "Mock stake confiscated for: $universityCode"

    override suspend fun getPaymentHistory(organizationCode: String): String =
        mockFabricClient.evaluateTransaction("getPaymentHistory", organizationCode).data!!

    override suspend fun createProposal(
        proposalId: String,
        proposalType: String,
        description: String,
        proposedChanges: String
    ): String = "Mock proposal created: $proposalId"

    override suspend fun voteOnProposal(
        proposalId: String,
        voterOrganization: String,
        vote: String,
        comments: String?
    ): String = "Mock vote recorded: $proposalId"

    override suspend fun getProposal(proposalId: String): String =
        mockFabricClient.evaluateTransaction("getProposal", proposalId).data!!

    override suspend fun getAllProposals(): String =
        mockFabricClient.evaluateTransaction("getAllProposals").data!!

    override suspend fun finalizeProposal(proposalId: String): String =
        "Mock proposal finalized: $proposalId"

    override suspend fun getSystemStatistics(): String =
        mockFabricClient.evaluateTransaction("getSystemStatistics").data!!

    override suspend fun getVerificationStatistics(organizationCode: String): String =
        mockFabricClient.evaluateTransaction("getVerificationStatistics", organizationCode).data!!

    override suspend fun getRevenueReport(organizationCode: String, startDate: String, endDate: String): String =
        mockFabricClient.evaluateTransaction("getRevenueReport", organizationCode).data!!
}

/**
 * Mock implementation of HealthChecker for testing without actual blockchain
 */
class MockHealthChecker : HealthChecker(MockContractInvoker()) {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    override suspend fun checkHealth(): HealthStatus {
        return HealthStatus(
            isHealthy = true,
            timestamp = LocalDateTime.now().format(dateFormatter),
            checks = mapOf(
                "blockchain_connectivity" to true,
                "system_statistics" to true,
                "university_operations" to true
            ),
            errors = emptyList(),
            metrics = mapOf(
                "response_time_ms" to 5,
                "system_statistics_available" to true
            )
        )
    }

    override suspend fun checkContractMethod(methodName: String, vararg args: String): Boolean = true

    override suspend fun getDetailedStatus(): DetailedHealthStatus {
        return DetailedHealthStatus(
            basicHealth = checkHealth(),
            networkInfo = NetworkInfo(
                channelName = "mock-channel",
                chaincodeVersion = "mock-1.0.0",
                blockHeight = Random.nextLong(1000, 9999),
                connectedPeers = 1,
                isConnected = true
            ),
            contractInfo = ContractInfo(
                contractName = "MockDegreeAttestationContract",
                isDeployed = true,
                lastInteraction = LocalDateTime.now().format(dateFormatter),
                totalTransactions = Random.nextLong(100, 1000)
            ),
            performanceMetrics = PerformanceMetrics(
                averageResponseTime = 5.0,
                minResponseTime = 2,
                maxResponseTime = 10,
                successfulRequests = 5,
                failedRequests = 0,
                uptime = System.currentTimeMillis()
            )
        )
    }
}