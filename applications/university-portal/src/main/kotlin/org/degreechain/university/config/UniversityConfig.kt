package org.degreechain.university.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConfigurationProperties(prefix = "university")
@ConstructorBinding
data class UniversityConfig(
    val universityCode: String = "UNI001",
    val universityName: String = "Default University",
    val maxDegreeSubmissionBatchSize: Int = 1000,
    val enableBulkUpload: Boolean = true,
    val defaultDegreeExpiryYears: Int = 0, // 0 means no expiry
    val allowDegreeRevocation: Boolean = true,
    val studentDataRetentionYears: Int = 10
)