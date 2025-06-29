package org.degreechain.employer.models

import org.degreechain.common.models.VerificationStatus
import java.time.LocalDateTime

data class VerificationResult(
    val verificationId: String,
    val certificateNumber: String,
    val verificationStatus: VerificationStatus,
    val confidence: Double,
    val studentName: String?,
    val degreeName: String?,
    val facultyName: String?,
    val degreeClassification: String?,
    val issuanceDate: String?,
    val universityName: String?,
    val universityCode: String?,
    val verifierOrganization: String,
    val verifierEmail: String,
    val verificationTimestamp: LocalDateTime,
    val paymentAmount: Double,
    val paymentId: String?,
    val extractionMethod: String?
)
