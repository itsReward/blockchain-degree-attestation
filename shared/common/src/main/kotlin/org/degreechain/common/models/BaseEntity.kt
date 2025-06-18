package org.degreechain.common.models

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDateTime
import java.util.*

abstract class BaseEntity {
    open val id: String = UUID.randomUUID().toString()

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    open val createdAt: LocalDateTime = LocalDateTime.now()

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    open val updatedAt: LocalDateTime = LocalDateTime.now()

    open val version: Long = 1L
}