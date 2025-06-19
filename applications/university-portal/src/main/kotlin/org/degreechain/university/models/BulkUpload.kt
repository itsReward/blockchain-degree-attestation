package org.degreechain.university.models

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDateTime

data class BulkUpload(
    val uploadId: String,
    val fileName: String,
    val totalRecords: Int,
    val processedRecords: Int,
    val successfulRecords: Int,
    val failedRecords: Int,
    val status: UploadStatus,
    val errors: List<String> = emptyList(),

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val startedAt: LocalDateTime,

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val completedAt: LocalDateTime? = null
)

enum class UploadStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}