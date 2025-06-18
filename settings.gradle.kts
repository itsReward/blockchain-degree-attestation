// settings.gradle.kts
rootProject.name = "blockchain-degree-attestation"

// Fabric Network
include("fabric-network:chaincode:degree-attestation")

// Applications
include("applications:attestation-authority")
include("applications:university-portal")
include("applications:employer-portal")
include("applications:api-gateway")

// Shared Libraries
include("shared:common")
include("shared:blockchain-client")
include("shared:payment-processor")

// Set project directories
project(":fabric-network:chaincode:degree-attestation").projectDir = file("fabric-network/chaincode/degree-attestation")
project(":applications:attestation-authority").projectDir = file("applications/attestation-authority")
project(":applications:university-portal").projectDir = file("applications/university-portal")
project(":applications:employer-portal").projectDir = file("applications/employer-portal")
project(":applications:api-gateway").projectDir = file("applications/api-gateway")
project(":shared:common").projectDir = file("shared/common")
project(":shared:blockchain-client").projectDir = file("shared/blockchain-client")
project(":shared:payment-processor").projectDir = file("shared/payment-processor")