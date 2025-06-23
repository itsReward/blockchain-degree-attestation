#!/bin/bash
# setup-blockchain.sh - Complete blockchain network setup script

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
CHANNEL_NAME="degree-channel"
CC_NAME="degree-attestation"
CC_VERSION="1.0"
CC_SRC_PATH="./chaincode/degree-attestation"
CC_RUNTIME_LANGUAGE="java"

function printHeader() {
    echo -e "${BLUE}================================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}================================================${NC}"
}

function printSuccess() {
    echo -e "${GREEN}✅ $1${NC}"
}

function printWarning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

function printError() {
    echo -e "${RED}❌ $1${NC}"
}

function checkPrerequisites() {
    printHeader "Checking Prerequisites"

    # Check if Docker is installed and running
    if ! command -v docker &> /dev/null; then
        printError "Docker is not installed. Please install Docker first."
        exit 1
    fi

    if ! docker info &> /dev/null; then
        printError "Docker is not running. Please start Docker first."
        exit 1
    fi
    printSuccess "Docker is installed and running"

    # Check if Docker Compose is installed
    if ! command -v docker-compose &> /dev/null; then
        printError "Docker Compose is not installed. Please install Docker Compose first."
        exit 1
    fi
    printSuccess "Docker Compose is installed"

    # Check if required directories exist
    if [ ! -d "./fabric-network" ]; then
        printWarning "Fabric network directory not found, creating..."
        mkdir -p ./fabric-network
    fi

    if [ ! -d "./chaincode" ]; then
        printWarning "Chaincode directory not found, creating..."
        mkdir -p ./chaincode
    fi

    printSuccess "Prerequisites check completed"
}

function setupDirectories() {
    printHeader "Setting up Directory Structure"

    # Create necessary directories
    mkdir -p ./fabric-network/{channel-artifacts,organizations,scripts,configtx}
    mkdir -p ./fabric-network/organizations/{ordererOrganizations,peerOrganizations}
    mkdir -p ./fabric-network/wallets/{attestation,university,employer}

    # Create crypto-config.yaml
    cat > ./fabric-network/crypto-config.yaml << EOF
OrdererOrgs:
  - Name: Orderer
    Domain: degree.com
    Specs:
      - Hostname: orderer

PeerOrgs:
  - Name: Attestation
    Domain: attestation.degree.com
    Template:
      Count: 1
    Users:
      Count: 1

  - Name: University
    Domain: university.degree.com
    Template:
      Count: 1
    Users:
      Count: 1

  - Name: Employer
    Domain: employer.degree.com
    Template:
      Count: 1
    Users:
      Count: 1
EOF

    # Create configtx.yaml
    cat > ./fabric-network/configtx/configtx.yaml << EOF
Organizations:
    - &OrdererOrg
        Name: OrdererOrg
        ID: OrdererMSP
        MSPDir: ../organizations/ordererOrganizations/degree.com/msp
        Policies:
            Readers:
                Type: Signature
                Rule: "OR('OrdererMSP.member')"
            Writers:
                Type: Signature
                Rule: "OR('OrdererMSP.member')"
            Admins:
                Type: Signature
                Rule: "OR('OrdererMSP.admin')"

    - &AttestationOrg
        Name: AttestationOrg
        ID: AttestationMSP
        MSPDir: ../organizations/peerOrganizations/attestation.degree.com/msp
        Policies:
            Readers:
                Type: Signature
                Rule: "OR('AttestationMSP.admin', 'AttestationMSP.peer', 'AttestationMSP.client')"
            Writers:
                Type: Signature
                Rule: "OR('AttestationMSP.admin', 'AttestationMSP.client')"
            Admins:
                Type: Signature
                Rule: "OR('AttestationMSP.admin')"
        AnchorPeers:
            - Host: peer0.attestation.degree.com
              Port: 7051

    - &UniversityOrg
        Name: UniversityOrg
        ID: UniversityMSP
        MSPDir: ../organizations/peerOrganizations/university.degree.com/msp
        Policies:
            Readers:
                Type: Signature
                Rule: "OR('UniversityMSP.admin', 'UniversityMSP.peer', 'UniversityMSP.client')"
            Writers:
                Type: Signature
                Rule: "OR('UniversityMSP.admin', 'UniversityMSP.client')"
            Admins:
                Type: Signature
                Rule: "OR('UniversityMSP.admin')"
        AnchorPeers:
            - Host: peer0.university.degree.com
              Port: 8051

    - &EmployerOrg
        Name: EmployerOrg
        ID: EmployerMSP
        MSPDir: ../organizations/peerOrganizations/employer.degree.com/msp
        Policies:
            Readers:
                Type: Signature
                Rule: "OR('EmployerMSP.admin', 'EmployerMSP.peer', 'EmployerMSP.client')"
            Writers:
                Type: Signature
                Rule: "OR('EmployerMSP.admin', 'EmployerMSP.client')"
            Admins:
                Type: Signature
                Rule: "OR('EmployerMSP.admin')"
        AnchorPeers:
            - Host: peer0.employer.degree.com
              Port: 9051

Capabilities:
    Channel: &ChannelCapabilities
        V2_0: true
    Orderer: &OrdererCapabilities
        V2_0: true
    Application: &ApplicationCapabilities
        V2_0: true

Application: &ApplicationDefaults
    Organizations:
    Policies:
        Readers:
            Type: ImplicitMeta
            Rule: "ANY Readers"
        Writers:
            Type: ImplicitMeta
            Rule: "ANY Writers"
        Admins:
            Type: ImplicitMeta
            Rule: "MAJORITY Admins"
    Capabilities:
        <<: *ApplicationCapabilities

Orderer: &OrdererDefaults
    OrdererType: solo
    Addresses:
        - orderer.degree.com:7050
    BatchTimeout: 2s
    BatchSize:
        MaxMessageCount: 10
        AbsoluteMaxBytes: 99 MB
        PreferredMaxBytes: 512 KB
    Organizations:
    Policies:
        Readers:
            Type: ImplicitMeta
            Rule: "ANY Readers"
        Writers:
            Type: ImplicitMeta
            Rule: "ANY Writers"
        Admins:
            Type: ImplicitMeta
            Rule: "MAJORITY Admins"
        BlockValidation:
            Type: ImplicitMeta
            Rule: "ANY Writers"

Channel: &ChannelDefaults
    Policies:
        Readers:
            Type: ImplicitMeta
            Rule: "ANY Readers"
        Writers:
            Type: ImplicitMeta
            Rule: "ANY Writers"
        Admins:
            Type: ImplicitMeta
            Rule: "MAJORITY Admins"
    Capabilities:
        <<: *ChannelCapabilities

Profiles:
    DegreeOrdererGenesis:
        <<: *ChannelDefaults
        Orderer:
            <<: *OrdererDefaults
            Organizations:
                - *OrdererOrg
            Capabilities:
                <<: *OrdererCapabilities
        Consortiums:
            DegreeConsortium:
                Organizations:
                    - *AttestationOrg
                    - *UniversityOrg
                    - *EmployerOrg

    DegreeChannel:
        Consortium: DegreeConsortium
        <<: *ChannelDefaults
        Application:
            <<: *ApplicationDefaults
            Organizations:
                - *AttestationOrg
                - *UniversityOrg
                - *EmployerOrg
            Capabilities:
                <<: *ApplicationCapabilities
EOF

    printSuccess "Directory structure and configuration files created"
}

function generateCrypto() {
    printHeader "Generating Crypto Material"

    cd ./fabric-network

    # Generate crypto material
    if command -v cryptogen &> /dev/null; then
        cryptogen generate --config=./crypto-config.yaml --output="organizations"
        printSuccess "Crypto material generated successfully"
    else
        printError "cryptogen not found. Installing Hyperledger Fabric binaries..."

        # Download Fabric binaries
        curl -sSL https://bit.ly/2ysbOFE | bash -s -- 2.5.0 1.5.0

        # Add to PATH
        export PATH=${PWD}/bin:$PATH

        # Generate crypto material
        cryptogen generate --config=./crypto-config.yaml --output="organizations"
        printSuccess "Crypto material generated successfully"
    fi

    cd ..
}

function generateGenesisBlock() {
    printHeader "Generating Genesis Block and Channel Configuration"

    cd ./fabric-network

    export FABRIC_CFG_PATH=${PWD}/configtx

    # Generate genesis block
    configtxgen -profile DegreeOrdererGenesis -channelID system-channel -outputBlock ./channel-artifacts/genesis.block
    printSuccess "Genesis block generated"

    # Generate channel configuration transaction
    configtxgen -profile DegreeChannel -outputCreateChannelTx ./channel-artifacts/${CHANNEL_NAME}.tx -channelID $CHANNEL_NAME
    printSuccess "Channel configuration generated"

    # Generate anchor peer updates
    configtxgen -profile DegreeChannel -outputAnchorPeersUpdate ./channel-artifacts/AttestationMSPanchors.tx -channelID $CHANNEL_NAME -asOrg AttestationOrg
    configtxgen -profile DegreeChannel -outputAnchorPeersUpdate ./channel-artifacts/UniversityMSPanchors.tx -channelID $CHANNEL_NAME -asOrg UniversityOrg
    configtxgen -profile DegreeChannel -outputAnchorPeersUpdate ./channel-artifacts/EmployerMSPanchors.tx -channelID $CHANNEL_NAME -asOrg EmployerOrg
    printSuccess "Anchor peer updates generated"

    cd ..
}

function createConnectionProfiles() {
    printHeader "Creating Connection Profiles"

    # Create attestation connection profile
    cat > ./fabric-network/organizations/peerOrganizations/attestation.degree.com/connection-attestation.yaml << EOF
name: degree-network-attestation
version: 1.0.0
client:
  organization: AttestationMSP
organizations:
  AttestationMSP:
    mspid: AttestationMSP
    peers:
  peer0.employer.degree.com:
    url: grpc://peer0.employer.degree.com:9051
EOF

    printSuccess "Connection profiles created"
}

function setupWallets() {
    printHeader "Setting up Wallets and Identities"

    # Create wallet setup script
    cat > ./fabric-network/scripts/setup-wallets.js << 'EOF'
const { Wallets, X509Identity } = require('fabric-network');
const FabricCAServices = require('fabric-ca-client');
const fs = require('fs');
const path = require('path');

async function setupWallet(orgName, mspId, walletPath, adminName) {
    try {
        // Create wallet
        const wallet = await Wallets.newFileSystemWallet(walletPath);

        // Check if admin already exists
        const adminIdentity = await wallet.get(adminName);
        if (adminIdentity) {
            console.log(`Admin identity ${adminName} already exists in wallet`);
            return;
        }

        // Read the admin certificate and private key
        const orgPath = path.join(__dirname, '..', 'organizations', 'peerOrganizations', `${orgName.toLowerCase()}.degree.com`);
        const certPath = path.join(orgPath, 'users', `Admin@${orgName.toLowerCase()}.degree.com`, 'msp', 'signcerts');
        const keyPath = path.join(orgPath, 'users', `Admin@${orgName.toLowerCase()}.degree.com`, 'msp', 'keystore');

        const certFiles = fs.readdirSync(certPath);
        const keyFiles = fs.readdirSync(keyPath);

        if (certFiles.length === 0 || keyFiles.length === 0) {
            throw new Error(`Certificate or key files not found for ${orgName}`);
        }

        const cert = fs.readFileSync(path.join(certPath, certFiles[0])).toString();
        const key = fs.readFileSync(path.join(keyPath, keyFiles[0])).toString();

        const identity = {
            credentials: {
                certificate: cert,
                privateKey: key,
            },
            mspId: mspId,
            type: 'X.509',
        };

        await wallet.put(adminName, identity);
        console.log(`Successfully created ${adminName} identity in wallet for ${orgName}`);

    } catch (error) {
        console.error(`Failed to setup wallet for ${orgName}:`, error);
        throw error;
    }
}

async function main() {
    try {
        await setupWallet('attestation', 'AttestationMSP', '../wallets/attestation', 'attestation-admin');
        await setupWallet('university', 'UniversityMSP', '../wallets/university', 'university-admin');
        await setupWallet('employer', 'EmployerMSP', '../wallets/employer', 'employer-admin');
        console.log('All wallets setup successfully');
    } catch (error) {
        console.error('Failed to setup wallets:', error);
        process.exit(1);
    }
}

main();
EOF

    # Create package.json for wallet setup
    cat > ./fabric-network/scripts/package.json << EOF
{
  "name": "wallet-setup",
  "version": "1.0.0",
  "description": "Setup wallets for degree attestation network",
  "main": "setup-wallets.js",
  "dependencies": {
    "fabric-network": "^2.2.0",
    "fabric-ca-client": "^2.2.0"
  }
}
EOF

    cd ./fabric-network/scripts

    # Install dependencies if Node.js is available
    if command -v npm &> /dev/null; then
        npm install
        node setup-wallets.js
        printSuccess "Wallets setup completed"
    else
        printWarning "Node.js not found. Wallets will need to be setup manually."
        printWarning "Install Node.js and run: cd fabric-network/scripts && npm install && node setup-wallets.js"
    fi

    cd ../..
}

function startNetwork() {
    printHeader "Starting Blockchain Network"

    # Start the blockchain network
    docker-compose up -d orderer.degree.com peer0.attestation.degree.com peer0.university.degree.com peer0.employer.degree.com cli

    # Wait for network to be ready
    sleep 10

    printSuccess "Blockchain network started"
}

function createChannel() {
    printHeader "Creating and Joining Channel"

    # Create channel
    docker exec cli peer channel create -o orderer.degree.com:7050 -c $CHANNEL_NAME -f ./channel-artifacts/${CHANNEL_NAME}.tx --outputBlock ./channel-artifacts/${CHANNEL_NAME}.block

    # Join attestation peer to channel
    docker exec cli peer channel join -b ./channel-artifacts/${CHANNEL_NAME}.block

    # Join university peer to channel
    docker exec -e CORE_PEER_LOCALMSPID=UniversityMSP -e CORE_PEER_ADDRESS=peer0.university.degree.com:8051 -e CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/university.degree.com/users/Admin@university.degree.com/msp cli peer channel join -b ./channel-artifacts/${CHANNEL_NAME}.block

    # Join employer peer to channel
    docker exec -e CORE_PEER_LOCALMSPID=EmployerMSP -e CORE_PEER_ADDRESS=peer0.employer.degree.com:9051 -e CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/employer.degree.com/users/Admin@employer.degree.com/msp cli peer channel join -b ./channel-artifacts/${CHANNEL_NAME}.block

    printSuccess "Channel created and peers joined"
}

function deployChaincode() {
    printHeader "Deploying Chaincode"

    # Package chaincode (if source exists)
    if [ -d "$CC_SRC_PATH" ]; then
        # For Java chaincode, we need to build it first
        if [ "$CC_RUNTIME_LANGUAGE" = "java" ]; then
            printWarning "Java chaincode detected. Building..."
            cd $CC_SRC_PATH
            gradle build
            cd - > /dev/null
        fi

        # Package the chaincode
        docker exec cli peer lifecycle chaincode package ${CC_NAME}.tar.gz --path ${CC_SRC_PATH} --lang ${CC_RUNTIME_LANGUAGE} --label ${CC_NAME}_${CC_VERSION}

        # Install on attestation peer
        docker exec cli peer lifecycle chaincode install ${CC_NAME}.tar.gz

        # Install on university peer
        docker exec -e CORE_PEER_LOCALMSPID=UniversityMSP -e CORE_PEER_ADDRESS=peer0.university.degree.com:8051 -e CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/university.degree.com/users/Admin@university.degree.com/msp cli peer lifecycle chaincode install ${CC_NAME}.tar.gz

        # Install on employer peer
        docker exec -e CORE_PEER_LOCALMSPID=EmployerMSP -e CORE_PEER_ADDRESS=peer0.employer.degree.com:9051 -e CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/employer.degree.com/users/Admin@employer.degree.com/msp cli peer lifecycle chaincode install ${CC_NAME}.tar.gz

        # Get package ID
        PACKAGE_ID=$(docker exec cli peer lifecycle chaincode queryinstalled --output json | jq -r '.installed_chaincodes[0].package_id')

        # Approve chaincode for each org
        docker exec cli peer lifecycle chaincode approveformyorg -o orderer.degree.com:7050 --channelID $CHANNEL_NAME --name $CC_NAME --version $CC_VERSION --package-id $PACKAGE_ID --sequence 1

        docker exec -e CORE_PEER_LOCALMSPID=UniversityMSP -e CORE_PEER_ADDRESS=peer0.university.degree.com:8051 -e CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/university.degree.com/users/Admin@university.degree.com/msp cli peer lifecycle chaincode approveformyorg -o orderer.degree.com:7050 --channelID $CHANNEL_NAME --name $CC_NAME --version $CC_VERSION --package-id $PACKAGE_ID --sequence 1

        docker exec -e CORE_PEER_LOCALMSPID=EmployerMSP -e CORE_PEER_ADDRESS=peer0.employer.degree.com:9051 -e CORE_PEER_MSPCONFIGPATH=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/employer.degree.com/users/Admin@employer.degree.com/msp cli peer lifecycle chaincode approveformyorg -o orderer.degree.com:7050 --channelID $CHANNEL_NAME --name $CC_NAME --version $CC_VERSION --package-id $PACKAGE_ID --sequence 1

        # Commit chaincode
        docker exec cli peer lifecycle chaincode commit -o orderer.degree.com:7050 --channelID $CHANNEL_NAME --name $CC_NAME --version $CC_VERSION --sequence 1 --peerAddresses peer0.attestation.degree.com:7051 --peerAddresses peer0.university.degree.com:8051 --peerAddresses peer0.employer.degree.com:9051

        # Initialize chaincode
        docker exec cli peer chaincode invoke -o orderer.degree.com:7050 --channelID $CHANNEL_NAME --name $CC_NAME -c '{"function":"initLedger","Args":[]}'

        printSuccess "Chaincode deployed and initialized"
    else
        printWarning "Chaincode source not found at $CC_SRC_PATH. Skipping chaincode deployment."
        printWarning "You can deploy chaincode manually later."
    fi
}

function startApplications() {
    printHeader "Starting Application Services"

    # Start database and cache first
    docker-compose up -d postgres redis

    # Wait for database to be ready
    sleep 15

    # Start application services
    docker-compose up -d attestation-authority university-portal employer-portal api-gateway

    printSuccess "Application services started"
}

function verifyDeployment() {
    printHeader "Verifying Deployment"

    # Check if all containers are running
    echo "Checking container status..."
    docker-compose ps

    # Test application endpoints
    sleep 30

    echo -e "\nTesting application endpoints..."

    # Test health endpoints
    if curl -s http://localhost:8080/actuator/health | grep -q "UP"; then
        printSuccess "Attestation Authority is healthy (port 8080)"
    else
        printWarning "Attestation Authority health check failed"
    fi

    if curl -s http://localhost:8081/actuator/health | grep -q "UP"; then
        printSuccess "University Portal is healthy (port 8081)"
    else
        printWarning "University Portal health check failed"
    fi

    if curl -s http://localhost:8082/actuator/health | grep -q "UP"; then
        printSuccess "Employer Portal is healthy (port 8082)"
    else
        printWarning "Employer Portal health check failed"
    fi

    if curl -s http://localhost:8000/api/v1/health | grep -q "UP"; then
        printSuccess "API Gateway is healthy (port 8000)"
    else
        printWarning "API Gateway health check failed"
    fi
}

function printCompletionInfo() {
    printHeader "🎉 Deployment Complete! 🎉"

    echo -e "${GREEN}Your blockchain degree attestation system is now running!${NC}\n"

    echo -e "${BLUE}Application URLs:${NC}"
    echo -e "  • Attestation Authority: http://localhost:8080"
    echo -e "  • University Portal:     http://localhost:8081"
    echo -e "  • Employer Portal:       http://localhost:8082"
    echo -e "  • API Gateway:           http://localhost:8000"
    echo ""

    echo -e "${BLUE}API Documentation:${NC}"
    echo -e "  • Swagger UI: http://localhost:8080/swagger-ui.html"
    echo -e "  • Health Checks: http://localhost:8080/actuator/health"
    echo ""

    echo -e "${BLUE}Authentication Credentials:${NC}"
    echo -e "  • Attestation Authority: authority / authority123"
    echo -e "  • University Portal: university / university123"
    echo -e "  • Employer Portal: employer / employer123"
    echo ""

    echo -e "${BLUE}Blockchain Network:${NC}"
    echo -e "  • Channel: ${CHANNEL_NAME}"
    echo -e "  • Chaincode: ${CC_NAME}"
    echo -e "  • Organizations: AttestationMSP, UniversityMSP, EmployerMSP"
    echo ""

    echo -e "${YELLOW}Management Commands:${NC}"
    echo -e "  • View logs: docker-compose logs -f [service-name]"
    echo -e "  • Stop system: docker-compose down"
    echo -e "  • Restart: docker-compose restart [service-name]"
    echo -e "  • Blockchain CLI: docker exec -it cli bash"
    echo ""

    echo -e "${GREEN}Happy degree attestation! 🎓⛓️${NC}"
}

# Main execution
function main() {
    case "$1" in
        "full")
            checkPrerequisites
            setupDirectories
            generateCrypto
            generateGenesisBlock
            createConnectionProfiles
            setupWallets
            startNetwork
            createChannel
            deployChaincode
            startApplications
            verifyDeployment
            printCompletionInfo
            ;;
        "network-only")
            checkPrerequisites
            setupDirectories
            generateCrypto
            generateGenesisBlock
            createConnectionProfiles
            setupWallets
            startNetwork
            createChannel
            deployChaincode
            printSuccess "Blockchain network setup complete"
            ;;
        "apps-only")
            startApplications
            verifyDeployment
            printCompletionInfo
            ;;
        "verify")
            verifyDeployment
            ;;
        *)
            printHeader "Blockchain Degree Attestation Setup"
            echo "Usage: $0 {full|network-only|apps-only|verify}"
            echo ""
            echo "Commands:"
            echo "  full         - Complete setup (blockchain + applications)"
            echo "  network-only - Setup only the blockchain network"
            echo "  apps-only    - Start only the application services"
            echo "  verify       - Verify current deployment status"
            echo ""
            echo "Examples:"
            echo "  $0 full              # Complete setup"
            echo "  $0 network-only      # Setup blockchain first"
            echo "  $0 apps-only         # Then start apps"
            exit 1
            ;;
    esac
}

# Run main function with all arguments
main "$@"
      - peer0.attestation.degree.com
channels:
  degree-channel:
    peers:
      peer0.attestation.degree.com:
        endorsingPeer: true
        chaincodeQuery: true
        ledgerQuery: true
        eventSource: true
peers:
  peer0.attestation.degree.com:
    url: grpc://peer0.attestation.degree.com:7051
EOF

    # Create university connection profile
    cat > ./fabric-network/organizations/peerOrganizations/university.degree.com/connection-university.yaml << EOF
name: degree-network-university
version: 1.0.0
client:
  organization: UniversityMSP
organizations:
  UniversityMSP:
    mspid: UniversityMSP
    peers:
      - peer0.university.degree.com
channels:
  degree-channel:
    peers:
      peer0.university.degree.com:
        endorsingPeer: true
        chaincodeQuery: true
        ledgerQuery: true
        eventSource: true
peers:
  peer0.university.degree.com:
    url: grpc://peer0.university.degree.com:8051
EOF

    # Create employer connection profile
    cat > ./fabric-network/organizations/peerOrganizations/employer.degree.com/connection-employer.yaml << EOF
name: degree-network-employer
version: 1.0.0
client:
  organization: EmployerMSP
organizations:
  EmployerMSP:
    mspid: EmployerMSP
    peers:
      - peer0.employer.degree.com
channels:
  degree-channel:
    peers:
      peer0.employer.degree.com:
        endorsingPeer: true
        chaincodeQuery: true
        ledgerQuery: true
        eventSource: true
peers: