package org.degreechain.common.models

enum class ErrorCode(val code: String, val description: String) {
    // General errors
    VALIDATION_ERROR("VALIDATION_001", "Input validation failed"),
    UNAUTHORIZED_ACCESS("AUTH_001", "Unauthorized access"),
    FORBIDDEN_OPERATION("AUTH_002", "Operation forbidden"),
    RESOURCE_NOT_FOUND("RESOURCE_001", "Resource not found"),

    // Blockchain errors
    BLOCKCHAIN_CONNECTION_ERROR("BLOCKCHAIN_001", "Failed to connect to blockchain network"),
    CHAINCODE_EXECUTION_ERROR("BLOCKCHAIN_002", "Chaincode execution failed"),
    TRANSACTION_TIMEOUT("BLOCKCHAIN_003", "Transaction timeout"),
    INSUFFICIENT_PERMISSIONS("BLOCKCHAIN_004", "Insufficient blockchain permissions"),

    // University errors
    UNIVERSITY_NOT_REGISTERED("UNIVERSITY_001", "University not registered in network"),
    UNIVERSITY_BLACKLISTED("UNIVERSITY_002", "University is blacklisted"),
    INSUFFICIENT_STAKE("UNIVERSITY_003", "Insufficient stake amount"),
    DEGREE_ALREADY_EXISTS("UNIVERSITY_004", "Degree record already exists"),

    // Payment errors
    PAYMENT_FAILED("PAYMENT_001", "Payment processing failed"),
    INSUFFICIENT_FUNDS("PAYMENT_002", "Insufficient funds"),
    PAYMENT_TIMEOUT("PAYMENT_003", "Payment timeout"),
    INVALID_PAYMENT_METHOD("PAYMENT_004", "Invalid payment method"),

    // Verification errors
    VERIFICATION_FAILED("VERIFICATION_001", "Degree verification failed"),
    INVALID_CERTIFICATE("VERIFICATION_002", "Invalid certificate format"),
    CERTIFICATE_EXPIRED("VERIFICATION_003", "Certificate has expired"),
    HASH_MISMATCH("VERIFICATION_004", "Certificate hash mismatch"),

    // System errors
    INTERNAL_SERVER_ERROR("SYSTEM_001", "Internal server error"),
    SERVICE_UNAVAILABLE("SYSTEM_002", "Service temporarily unavailable"),
    RATE_LIMIT_EXCEEDED("SYSTEM_003", "Rate limit exceeded"),
    MAINTENANCE_MODE("SYSTEM_004", "System under maintenance")
}