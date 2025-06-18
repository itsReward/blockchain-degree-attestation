package org.degreechain.authority.models

import org.degreechain.common.models.BaseEntity
import java.time.LocalDate

data class ComplianceReport(
    val reportDate: LocalDate,
    val totalUniversities: Int,
    val activeUniversities: Int,
    val pendingUniversities: Int,
    val blacklistedUniversities: Int,
    val complianceIssues: List<ComplianceIssue>,
    val auditFindings: List<AuditFinding>,
    val recommendedActions: List<String>
) : BaseEntity()

data class ComplianceIssue(
    val universityCode: String,
    val issueType: String,
    val severity: String, // LOW, MEDIUM, HIGH, CRITICAL
    val description: String,
    val detectedDate: LocalDate,
    val status: String, // OPEN, INVESTIGATING, RESOLVED
    val assignedTo: String?
)

data class AuditFinding(
    val findingId: String,
    val category: String,
    val description: String,
    val impact: String,
    val recommendation: String,
    val dueDate: LocalDate?,
    val status: String
)