package org.degreechain.authority.models

import org.degreechain.common.models.BaseEntity
import java.time.LocalDate

data class RevenueReport(
    val reportPeriodStart: LocalDate,
    val reportPeriodEnd: LocalDate,
    val totalRevenue: Double,
    val authorityShare: Double,
    val universityShare: Double,
    val totalVerifications: Long,
    val totalStakeAmount: Double,
    val universityRevenues: Map<String, UniversityRevenue>,
    val paymentBreakdown: Map<String, Double>, // Payment type -> amount
    val topUniversitiesByRevenue: List<UniversityRevenue>
) : BaseEntity()

data class UniversityRevenue(
    val universityCode: String,
    val universityName: String,
    val totalRevenue: Double,
    val verificationsCount: Long,
    val averageRevenuePerVerification: Double,
    val stakeAmount: Double,
    val status: String
)