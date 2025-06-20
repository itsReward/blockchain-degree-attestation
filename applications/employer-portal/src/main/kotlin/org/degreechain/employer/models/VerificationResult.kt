package org.degreechain.employer.models

import com.fasterxml.jackson.annotation.JsonFormat
import org.degreechain.common.models.VerificationStatus
import java.time.LocalDateTime

data class VerificationResult(
    val verificationId: String,
    val certificateNumber: String,
    val verificationStatus: VerificationStatus,
    val confidence: Double,
    val studentName: String? = null,
    val degreeName: String? = null,
    val facultyName: String? = null,
    val degreeClassification: String? = null,
    val issuanceDate: String? = null,
    val universityName: String? = null,
    val universityCode: String? = null,
    val verifierOrganization: String,
    val verifierEmail: String,

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val verificationTimestamp: LocalDateTime,

    val paymentAmount: Double,
    val paymentId: String? = null,
    val extractionMethod: String? = null,
    val additionalInfo: Map<String, Any> = emptyMap(),
    val errorMessage: String? = null
)