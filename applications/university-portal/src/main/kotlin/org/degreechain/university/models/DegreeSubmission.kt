package org.degreechain.university.models

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDateTime
import jakarta.validation.constraints.*

data class DegreeSubmission(
    @field:NotBlank(message = "Certificate number is required")
    val certificateNumber: String,

    @field:NotBlank(message = "Student ID is required")
    val studentId: String,

    @field:NotBlank(message = "Student name is required")
    val studentName: String,

    @field:NotBlank(message = "Degree name is required")
    val degreeName: String,

    @field:NotBlank(message = "Faculty name is required")
    val facultyName: String,

    @field:NotBlank(message = "Degree classification is required")
    val degreeClassification: String,

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val issuanceDate: LocalDateTime,

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val expiryDate: LocalDateTime? = null,

    val additionalNotes: String? = null,

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val createdAt: LocalDateTime = LocalDateTime.now()
)