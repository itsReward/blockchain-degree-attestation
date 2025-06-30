#!/bin/bash

# final-fix-structure.sh
# Script to move organizations to the root level and fix all configurations

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

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

printInfo "Moving peer organizations to root level..."

# Move each organization from peerOrganizations to root level
if [ -d "organizations/peerOrganizations" ]; then
    cd organizations/peerOrganizations

    for org in */; do
        org_name=$(basename "$org")
        printInfo "Moving $org_name to organizations root level"
        mv "$org" "../$org_name"
        printSuccess "Moved $org_name"
    done

    cd ../..

    # Remove empty peerOrganizations directory
    rmdir organizations/peerOrganizations
    printSuccess "Removed empty peerOrganizations directory"
else
    printWarning "peerOrganizations directory not found"
fi

# Fix orderer organization structure
if [ -d "organizations/orderer-org/degree.com" ]; then
    printInfo "Fixing orderer organization structure..."

    # Move contents from degree.com to orderer-org root
    cd organizations/orderer-org
    mv degree.com/* .
    rmdir degree.com
    cd ../..

    printSuccess "Fixed orderer organization structure"
fi

printInfo "Current structure after move:"
tree organizations/ -L 2 2>/dev/null || find organizations/ -maxdepth 2 -type d | sort

# Now create all the configurations
printInfo "Creating Fabric CA server configurations..."

# Function to create CA config
create_ca_config() {
    local org_name=$1
    local port=$2
    local ca_dir="organizations/${org_name}/ca"

    if [ ! -d "$ca_dir" ]; then
        printWarning "CA directory not found for ${org_name}: $ca_dir"
        return
    fi

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

    printInfo "Creating CA config for $org_name with cert: $ca_cert_file, key: $ca_key_file"

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
EOF

    printSuccess "Created CA configuration for ${org_name}"
}

# Create CA configurations
create_ca_config "attestation-org" 7054
create_ca_config "university-org" 8054
create_ca_config "employer-org" 9054
create_ca_config "orderer-org" 6054

# Create connection profiles
printInfo "Creating connection profiles..."

# Function to create connection profile
create_connection_profile() {
    local org_name=$1
    local msp_id=$2
    local peer_port=$3
    local ca_port=$4

    if [ ! -d "organizations/${org_name}" ]; then
        printWarning "Organization directory not found for ${org_name}"
        return
    fi

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

    if [ -z "$ca_cert_name" ]; then
        printWarning "No CA certificate name provided for $org_path"
        return
    fi

    find "$org_path" -name "msp" -type d | while read msp_dir; do
        printInfo "Creating config.yaml in: $msp_dir"
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
for org_dir in organizations/*/; do
    org_name=$(basename "$org_dir")
    if [ -d "$org_dir/ca" ]; then
        ca_cert=$(find ${org_dir}/ca -name "*-cert.pem" | head -1)
        if [ -n "$ca_cert" ]; then
            ca_cert_name=$(basename "$ca_cert")
            create_msp_config "$org_dir" "$ca_cert_name"
        else
            printWarning "No CA certificate found in $org_dir/ca"
        fi
    else
        printInfo "No CA directory found in $org_dir (this is normal for some structures)"
    fi
done

# Set proper permissions
printInfo "Setting proper file permissions..."
find organizations -type f -name "*_sk" -exec chmod 600 {} \; 2>/dev/null || true
find organizations -type f -name "*.key" -exec chmod 600 {} \; 2>/dev/null || true

echo ""
printSuccess "Directory structure has been completely fixed!"
echo ""
printInfo "Final directory structure:"
tree organizations/ -L 3 2>/dev/null || find organizations/ -type d | head -20

echo ""
printInfo "Summary of generated materials:"
echo ""

for org in organizations/*/; do
    if [ -d "$org" ]; then
        org_name=$(basename "$org")
        echo "=== $org_name ==="

        # Check for CA materials
        if [ -d "$org/ca" ]; then
            ca_files=$(find "$org/ca" -name "*.pem" -o -name "*_sk" | wc -l)
            echo "  ✓ CA materials: $ca_files files"
        fi

        # Check for peer materials
        if [ -d "$org/peers" ]; then
            peer_count=$(find "$org/peers" -name "peer*" -type d | wc -l)
            echo "  ✓ Peers: $peer_count"
        fi

        # Check for users
        if [ -d "$org/users" ]; then
            user_count=$(find "$org/users" -name "*@*" -type d | wc -l)
            echo "  ✓ Users: $user_count"
        fi

        # Check for orderers (for orderer org)
        if [ -d "$org/orderers" ]; then
            orderer_count=$(find "$org/orderers" -name "orderer*" -type d | wc -l)
            echo "  ✓ Orderers: $orderer_count"
        fi

        # Check for connection profile
        if [ -f "$org/connection-$org_name.yaml" ]; then
            echo "  ✓ Connection profile: Yes"
        fi

        echo ""
    fi
done

echo ""
printSuccess "Your Hyperledger Fabric network cryptographic materials are ready!"
echo ""
printInfo "Next steps:"
echo "  1. Update your docker-compose.yml volume paths"
echo "  2. Start your Hyperledger Fabric network"
echo "  3. Create channels and deploy chaincode"
echo ""
printWarning "Remember to update Docker Compose volume paths to:"
echo "  ./organizations/attestation-org/peers/peer0.attestation-org/msp:/etc/hyperledger/fabric/msp"
echo "  ./organizations/university-org/peers/peer0.university-org/msp:/etc/hyperledger/fabric/msp"
echo "  ./organizations/employer-org/peers/peer0.employer-org/msp:/etc/hyperledger/fabric/msp"