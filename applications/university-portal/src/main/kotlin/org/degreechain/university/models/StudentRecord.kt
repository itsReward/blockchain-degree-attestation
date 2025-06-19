package org.degreechain.university.models

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDateTime
import jakarta.validation.constraints.*

data class StudentRecord(
    @field:NotBlank(message = "Student ID is required")
    val studentId: String,

    @field:NotBlank(message = "First name is required")
    val firstName: String,

    @field:NotBlank(message = "Last name is required")
    val lastName: String,

    @field:NotBlank(message = "Program is required")
    val program: String,

    @field:NotBlank(message = "Status is required")
    val status: String,

    @field:Email(message = "Invalid email format")
    val email: String? = null,

    val phone: String? = null,

    @field:Min(value = 1, message = "Year level must be at least 1")
    @field:Max(value = 8, message = "Year level cannot exceed 8")
    val yearLevel: Int? = null,

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val enrollmentDate: LocalDateTime? = null,

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val expectedGraduationDate: LocalDateTime? = null,

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val updatedAt: LocalDateTime = LocalDateTime.now()
)