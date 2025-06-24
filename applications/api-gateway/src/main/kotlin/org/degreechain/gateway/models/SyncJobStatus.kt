package org.degreechain.gateway.models

import java.time.LocalDateTime

data class SyncJobStatus(
    val jobId: String,
    val universityCode: String,
    var status: String,
    val startTime: LocalDateTime,
    var endTime: LocalDateTime? = null,
    val initiatedBy: String,
    var totalRecords: Int,
    var processedRecords: Int,
    val errors: MutableList<String>
)
