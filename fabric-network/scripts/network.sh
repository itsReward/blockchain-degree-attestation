#!/bin/bash

set -e

# Set environment variables
export FABRIC_CFG_PATH=${PWD}/configtx
export VERBOSE=false

# Function to print messages
function printHelp() {
  echo "Usage: "
  echo "  network.sh <Mode> [Flags]"
  echo "    Modes:"
  echo "      up - Bring up the network"
  echo "      down - Clear the network"
  echo "      restart - Restart the network"
  echo "      generate - Generate required certificates and genesis block"
  echo "      deployCC - Deploy chaincode"
  echo "    Flags:"
  echo "    -c <channel name> - Name of the channel (default: degree-channel)"
  echo "    -ccn <chaincode name> - Name of the chaincode (default: degree-attestation)"
  echo "    -ccv <chaincode version> - Version of the chaincode (default: 1.0)"
  echo "    -ccp <chaincode path> - Path to the chaincode"
  echo "    -ccl <chaincode language> - Language of the chaincode (default: kotlin)"
  echo "    -v - Verbose mode"
}

# Parse commandline args
MODE=$1
shift

# Set defaults
CHANNEL_NAME="degree-channel"
CC_NAME="degree-attestation"
CC_VERSION="1.0"
CC_SRC_PATH="../chaincode/degree-attestation"
CC_RUNTIME_LANGUAGE="java"

# Parse remaining arguments
while [[ $# -ge 1 ]] ; do
  key="$1"
  case $key in
  -c )
    CHANNEL_NAME="$2"
    shift
    ;;
  -ccn )
    CC_NAME="$2"
    shift
    ;;
  -ccv )
    CC_VERSION="$2"
    shift
    ;;
  -ccp )
    CC_SRC_PATH="$2"
    shift
    ;;
  -ccl )
    CC_RUNTIME_LANGUAGE="$2"
    shift
    ;;
  -v )
    VERBOSE=true
    ;;
  * )
    echo "Unknown flag: $key"
    printHelp
    exit 1
    ;;
  esac
  shift
done

function clearContainers() {
  CONTAINER_IDS=$(docker ps -a | awk '($2 ~ /hyperledger\/fabric-peer.*/) {print $1}')
  if [ -z "$CONTAINER_IDS" -o "$CONTAINER_IDS" == " " ]; then
    echo "---- No containers available for deletion ----"
  else
    docker rm -f $CONTAINER_IDS
  fi
}

function removeUnwantedImages() {
  DOCKER_IMAGE_IDS=$(docker images | awk '($1 ~ /dev-peer.*/) {print $3}')
  if [ -z "$DOCKER_IMAGE_IDS" -o "$DOCKER_IMAGE_IDS" == " " ]; then
    echo "---- No images available for deletion ----"
  else
    docker rmi -f $DOCKER_IMAGE_IDS
  fi
}

function networkDown() {
  docker-compose -f docker-compose-network.yml down --volumes --remove-orphans
  clearContainers
  removeUnwantedImages

  # Remove generated artifacts
  if [ -d "channel-artifacts" ]; then
    rm -rf channel-artifacts
  fi

  if [ -d "organizations" ]; then
    rm -rf organizations
  fi

  echo "Network down completed"
}

function generateCerts() {
  which cryptogen
  if [ "$?" -ne 0 ]; then
    echo "cryptogen tool not found. exiting"
    exit 1
  fi

  echo "Generating certificates using cryptogen tool"

  if [ -d "organizations/peerOrganizations" ]; then
    rm -Rf organizations/peerOrganizations && rm -Rf organizations/orderer
  fi

  set -x
  cryptogen generate --config=./crypto-config.yaml --output="organizations"
  { set +x; } 2>/dev/null

  echo "Generating Orderer Genesis block"

  set -x
  configtxgen -profile DegreeOrdererGenesis -channelID system-channel -outputBlock ./channel-artifacts/genesis.block
  { set +x; } 2>/dev/null

  echo "Generating channel configuration transaction"

  set -x
  configtxgen -profile DegreeChannel -outputCreateChannelTx ./channel-artifacts/${CHANNEL_NAME}.tx -channelID $CHANNEL_NAME
  { set +x; } 2>/dev/null

  echo "Generating anchor peer update for AttestationOrg"
  set -x
  configtxgen -profile DegreeChannel -outputAnchorPeersUpdate ./channel-artifacts/AttestationOrgMSPanchors.tx -channelID $CHANNEL_NAME -asOrg AttestationOrg
  { set +x; } 2>/dev/null

  echo "Generating anchor peer update for UniversityOrg"
  set -x
  configtxgen -profile DegreeChannel -outputAnchorPeersUpdate ./channel-artifacts/UniversityOrgMSPanchors.tx -channelID $CHANNEL_NAME -asOrg UniversityOrg
  { set +x; } 2>/dev/null
}

function networkUp() {
  if [ ! -d "organizations/peerOrganizations" ]; then
    generateCerts
  fi

  docker-compose -f docker-compose-network.yml up -d

  if [ $? -ne 0 ]; then
    echo "ERROR !!!! Unable to start network"
    exit 1
  fi

  echo "Network started successfully"
}

# Main execution
if [ "${MODE}" == "up" ]; then
  networkUp
elif [ "${MODE}" == "down" ]; then
  networkDown
elif [ "${MODE}" == "restart" ]; then
  networkDown
  networkUp
elif [ "${MODE}" == "generate" ]; then
  generateCerts
elif [ "${MODE}" == "deployCC" ]; then
  ./deployCC.sh $CHANNEL_NAME $CC_NAME $CC_VERSION $CC_SRC_PATH $CC_RUNTIME_LANGUAGE
else
  printHelp
  exit 1
fi