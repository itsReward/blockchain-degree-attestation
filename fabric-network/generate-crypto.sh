#!/bin/bash

# generate-crypto.sh
# Script to generate all cryptographic materials for the Hyperledger Fabric network

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
printHeader() {
    echo -e "${BLUE}===================================="
    echo -e "$1"
    echo -e "====================================${NC}"
}

printSuccess() {
    echo -e "${GREEN}✓ $1${NC}"
}

printWarning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

printError() {
    echo -e "${RED}✗ $1${NC}"
}

printInfo() {
    echo -e "${BLUE}ℹ $1${NC}"
}

# Check if running from fabric-network directory
if [ ! -f "crypto-config.yaml" ]; then
    printError "crypto-config.yaml not found. Please run this script from the fabric-network directory."
    exit 1
fi

# Check if cryptogen is available
if ! command -v cryptogen &> /dev/null; then
    printWarning "cryptogen tool not found. Attempting to download Hyperledger Fabric binaries..."

    # Download Hyperledger Fabric binaries
    curl -sSL https://bit.ly/2ysbOFE | bash -s -- 2.5.0 1.5.0

    # Add to PATH for current session
    export PATH=${PWD}/bin:$PATH

    if ! command -v cryptogen &> /dev/null; then
        printError "Failed to install cryptogen. Please install Hyperledger Fabric binaries manually."
        exit 1
    fi

    printSuccess "Hyperledger Fabric binaries installed successfully"
fi

printHeader "Generating Cryptographic Materials"

# Remove existing organizations directory
if [ -d "organizations" ]; then
    printWarning "Removing existing organizations directory..."
    rm -rf organizations
fi

# Generate crypto materials using cryptogen
printInfo "Generating certificates and keys using cryptogen..."
cryptogen generate --config=./crypto-config.yaml --output="organizations"

if [ $? -eq 0 ]; then
    printSuccess "Cryptographic materials generated successfully"
else
    printError "Failed to generate cryptographic materials"
    exit 1
fi

# Fix directory names to match your structure
printInfo "Renaming directories to match your naming convention..."

# Rename ordererOrganizations to orderer-org
if [ -d "organizations/ordererOrganizations" ]; then
    mv organizations/ordererOrganizations organizations/orderer-org
    printSuccess "Renamed ordererOrganizations to orderer-org"
fi

# Rename peerOrganizations subdirectories
if [ -d "organizations/peerOrganizations" ]; then
    cd organizations/peerOrganizations

    # Rename attestationorg.attestation-org to attestation-org
    if [ -d "attestationorg.attestation-org" ]; then
        mv attestationorg.attestation-org ../attestation-org
        printSuccess "Moved AttestationOrg to attestation-org"
    fi

    # Rename universityorg.university-org to university-org
    if [ -d "universityorg.university-org" ]; then
        mv universityorg.university-org ../university-org
        printSuccess "Moved UniversityOrg to university-org"
    fi

    # Rename employerorg.employer-org to employer-org
    if [ -d "employerorg.employer-org" ]; then
        mv employerorg.employer-org ../employer-org
        printSuccess "Moved EmployerOrg to employer-org"
    fi

    cd ../..

    # Remove empty peerOrganizations directory
    rmdir organizations/peerOrganizations 2>/dev/null || true
fi

# Create CA configurations for each organization
printInfo "Creating Fabric CA server configurations..."

# Function to create CA config
create_ca_config() {
    local org_name=$1
    local port=$2
    local ca_dir="organizations/${org_name}/ca"

    # Find the CA certificate and private key
    local ca_cert=$(find ${ca_dir} -name "*-cert.pem" | head -1)
    local ca_key=$(find ${ca_dir} -name "*_sk" | head -1)

    if [ -z "$ca_cert" ] || [ -z "$ca_key" ]; then
        printWarning "CA certificate or key not found for ${org_name}"
        return
    fi

    # Get just the filenames
    ca_cert_file=$(basename "$ca_cert")
    ca_key_file=$(basename "$ca_key")

    cat > ${ca_dir}/fabric-ca-server-config.yaml << EOF
version: 1.2.0
port: ${port}
debug: false
crlsizelimit: 512000

tls:
  enabled: false

ca:
  name: ca-${org_name}
  keyfile: ${ca_key_file}
  certfile: ${ca_cert_file}
  chainfile:

registry:
  maxenrollments: -1
  identities:
     - name: admin
       pass: adminpw
       type: admin
       affiliation: ""
       attrs:
          hf.Registrar.Roles: "*"
          hf.Registrar.DelegateRoles: "*"
          hf.Revoker: true
          hf.IntermediateCA: true
          hf.GenCRL: true
          hf.Registrar.Attributes: "*"
          hf.AffiliationMgr: true

db:
  type: sqlite3
  datasource: fabric-ca-server.db
  tls:
    enabled: false

affiliations:
   ${org_name}:
      - department1
      - department2

signing:
  default:
    usage:
      - digital signature
    expiry: 8760h
  profiles:
    ca:
      usage:
        - cert sign
        - crl sign
      expiry: 43800h
      caconstraint:
        isca: true
        maxpathlen: 0

csr:
  cn: ca-${org_name}
  keyrequest:
    algo: ecdsa
    size: 256
  names:
    - C: US
      ST: "North Carolina"
      L: "Durham"
      O: ${org_name}
      OU: Fabric
  hosts:
    - localhost
    - ca.${org_name}

bccsp:
  default: SW
  sw:
    hash: SHA2
    security: 256
    filekeystore:
      keystore: msp/keystore

cacount:

cafiles:

intermediate:
  parentserver:
    url:
    caname:

  enrollment:
    hosts:
    profile:
    label:

  tls:
    certfiles:
    client:
      certfile:
      keyfile:
EOF

    printSuccess "Created CA configuration for ${org_name}"
}

# Create CA configurations
create_ca_config "attestation-org" 7054
create_ca_config "university-org" 8054
create_ca_config "employer-org" 9054

# Create connection profiles
printInfo "Creating connection profiles..."

# Function to create connection profile
create_connection_profile() {
    local org_name=$1
    local msp_id=$2
    local peer_port=$3
    local ca_port=$4

    cat > organizations/${org_name}/connection-${org_name}.yaml << EOF
name: degree-network-${org_name}
version: 1.0.0

client:
  organization: ${msp_id}
  connection:
    timeout:
      peer:
        endorser: '300'
        eventHub: '300'
        eventReg: '300'
      orderer: '300'

organizations:
  ${msp_id}:
    mspid: ${msp_id}
    peers:
      - peer0.${org_name}
    certificateAuthorities:
      - ca.${org_name}
    adminPrivateKeyPEM:
      path: users/Admin@${org_name}/msp/keystore/priv_sk
    signedCertPEM:
      path: users/Admin@${org_name}/msp/signcerts/Admin@${org_name}-cert.pem

orderers:
  orderer.degree.com:
    url: grpc://localhost:7050
    grpcOptions:
      ssl-target-name-override: orderer.degree.com
      hostnameOverride: orderer.degree.com

peers:
  peer0.${org_name}:
    url: grpc://localhost:${peer_port}
    grpcOptions:
      ssl-target-name-override: peer0.${org_name}
      hostnameOverride: peer0.${org_name}

certificateAuthorities:
  ca.${org_name}:
    url: http://localhost:${ca_port}
    caName: ca-${org_name}
    tlsCACerts:
      pem: []
    httpOptions:
      verify: false

channels:
  degree-channel:
    orderers:
      - orderer.degree.com
    peers:
      peer0.${org_name}:
        endorsingPeer: true
        chaincodeQuery: true
        ledgerQuery: true
        eventSource: true
EOF

    printSuccess "Created connection profile for ${org_name}"
}

# Create connection profiles for each organization
create_connection_profile "attestation-org" "AttestationMSP" 7051 7054
create_connection_profile "university-org" "UniversityMSP" 8051 8054
create_connection_profile "employer-org" "EmployerMSP" 9051 9054

# Create MSP config.yaml files
printInfo "Creating MSP configuration files..."

create_msp_config() {
    local org_path=$1
    local ca_cert_name=$2

    find "$org_path" -name "msp" -type d | while read msp_dir; do
        cat > "${msp_dir}/config.yaml" << EOF
NodeOUs:
  Enable: true
  ClientOUIdentifier:
    Certificate: cacerts/${ca_cert_name}
    OrganizationalUnitIdentifier: client
  PeerOUIdentifier:
    Certificate: cacerts/${ca_cert_name}
    OrganizationalUnitIdentifier: peer
  AdminOUIdentifier:
    Certificate: cacerts/${ca_cert_name}
    OrganizationalUnitIdentifier: admin
  OrdererOUIdentifier:
    Certificate: cacerts/${ca_cert_name}
    OrganizationalUnitIdentifier: orderer
EOF
    done

    printSuccess "Created MSP configs for $org_path"
}

# Create MSP configs for each organization
if [ -d "organizations/attestation-org" ]; then
    ca_cert=$(find organizations/attestation-org/ca -name "*-cert.pem" | head -1)
    ca_cert_name=$(basename "$ca_cert")
    create_msp_config "organizations/attestation-org" "$ca_cert_name"
fi

if [ -d "organizations/university-org" ]; then
    ca_cert=$(find organizations/university-org/ca -name "*-cert.pem" | head -1)
    ca_cert_name=$(basename "$ca_cert")
    create_msp_config "organizations/university-org" "$ca_cert_name"
fi

if [ -d "organizations/employer-org" ]; then
    ca_cert=$(find organizations/employer-org/ca -name "*-cert.pem" | head -1)
    ca_cert_name=$(basename "$ca_cert")
    create_msp_config "organizations/employer-org" "$ca_cert_name"
fi

if [ -d "organizations/orderer-org" ]; then
    ca_cert=$(find organizations/orderer-org/ca -name "*-cert.pem" | head -1)
    ca_cert_name=$(basename "$ca_cert")
    create_msp_config "organizations/orderer-org" "$ca_cert_name"
fi

# Set proper permissions
printInfo "Setting proper file permissions..."
find organizations -type f -name "*_sk" -exec chmod 600 {} \;
find organizations -type f -name "*.key" -exec chmod 600 {} \;

printHeader "Cryptographic Material Generation Complete"

echo ""
printSuccess "All cryptographic materials have been generated successfully!"
echo ""
printInfo "Directory structure created:"
echo "  organizations/"
echo "  ├── attestation-org/"
echo "  │   ├── ca/"
echo "  │   ├── peers/"
echo "  │   └── users/"
echo "  ├── employer-org/"
echo "  │   ├── ca/"
echo "  │   ├── peers/"
echo "  │   └── users/"
echo "  ├── university-org/"
echo "  │   ├── ca/"
echo "  │   ├── peers/"
echo "  │   └── users/"
echo "  └── orderer-org/"
echo "      ├── ca/"
echo "      ├── orderers/"
echo "      └── users/"
echo ""
printInfo "Files created:"
echo "  ✓ X.509 certificates for all entities"
echo "  ✓ Private keys for all entities"
echo "  ✓ MSP configurations"
echo "  ✓ TLS certificates"
echo "  ✓ Fabric CA server configurations"
echo "  ✓ Connection profiles"
echo ""
printSuccess "Your Hyperledger Fabric network is now ready for deployment!"
echo ""
printWarning "Next steps:"
echo "  1. Review the generated certificates and configurations"
echo "  2. Start your Fabric CA servers (optional)"
echo "  3. Configure your docker-compose.yml to use these materials"
echo "  4. Start your Hyperledger Fabric network"