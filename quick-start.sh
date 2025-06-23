#!/bin/bash
# quick-start.sh - Quick setup with mock blockchain for immediate testing

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

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

function checkDocker() {
    printHeader "Checking Docker"

    if ! command -v docker &> /dev/null; then
        printError "Docker is not installed. Please install Docker first."
        exit 1
    fi

    if ! docker info &> /dev/null; then
        printError "Docker is not running. Please start Docker first."
        exit 1
    fi

    if ! command -v docker-compose &> /dev/null; then
        printError "Docker Compose is not installed. Please install Docker Compose first."
        exit 1
    fi

    printSuccess "Docker and Docker Compose are ready"
}

function startServices() {
    printHeader "Starting Services"

    # Stop any existing containers
    docker-compose -f docker-compose.mock.yml down > /dev/null 2>&1 || true

    # Start database first
    printWarning "Starting database and cache..."
    docker-compose -f docker-compose.mock.yml up -d postgres redis

    # Wait for database
    sleep 20

    # Start applications
    printWarning "Starting application services..."
    docker-compose -f docker-compose.mock.yml up -d --build

    printSuccess "All services started"
}

function waitForServices() {
    printHeader "Waiting for Services to be Ready"

    local max_attempts=120
    local attempt=1

    while [ $attempt -le $max_attempts ]; do
        echo -e "Attempt $attempt/$max_attempts..."

        # Check attestation authority
        if curl -s http://localhost:8080/actuator/health | grep -q "UP"; then
            printSuccess "Attestation Authority is ready"
            break
        fi

        sleep 5
        ((attempt++))
    done

    if [ $attempt -gt $max_attempts ]; then
        printError "Services did not start within expected time"
        printWarning "Check logs with: docker-compose -f docker-compose.mock.yml logs"
        exit 1
    fi
}

function testEndpoints() {
    printHeader "Testing Endpoints"

    # Test all health endpoints
    endpoints=(
        "8080:Attestation Authority"
        "8081:University Portal"
        "8082:Employer Portal"
        "8000:API Gateway"
    )

    for endpoint in "${endpoints[@]}"; do
        port=$(echo $endpoint | cut -d: -f1)
        name=$(echo $endpoint | cut -d: -f2)

        if curl -s http://localhost:$port/actuator/health | grep -q "UP"; then
            printSuccess "$name is healthy (port $port)"
        else
            printWarning "$name health check failed (port $port)"
        fi
    done
}

function showSystemInfo() {
    printHeader "🎉 Quick Start Complete! 🎉"

    echo -e "${GREEN}Your degree attestation system is running with mock blockchain!${NC}\n"

    echo -e "${BLUE}📋 System Status:${NC}"
    docker-compose -f docker-compose.mock.yml ps
    echo ""

    echo -e "${BLUE}🌐 Application URLs:${NC}"
    echo -e "  • Attestation Authority: http://localhost:8080"
    echo -e "  • University Portal:     http://localhost:8081"
    echo -e "  • Employer Portal:       http://localhost:8082"
    echo -e "  • API Gateway:           http://localhost:8000"
    echo ""

    echo -e "${BLUE}📚 API Documentation:${NC}"
    echo -e "  • Swagger UI (Attestation): http://localhost:8080/swagger-ui.html"
    echo -e "  • Swagger UI (University):  http://localhost:8081/swagger-ui.html"
    echo -e "  • Swagger UI (Employer):    http://localhost:8082/swagger-ui.html"
    echo -e "  • API Gateway Health:       http://localhost:8000/api/v1/health"
    echo ""

    echo -e "${BLUE}🔐 Authentication Credentials:${NC}"
    echo -e "  • Attestation Authority: authority / authority123"
    echo -e "  • University Portal:     university / university123"
    echo -e "  • Employer Portal:       employer / employer123"
    echo ""

    echo -e "${BLUE}🔧 Management Commands:${NC}"
    echo -e "  • View logs:     docker-compose -f docker-compose.mock.yml logs -f [service]"
    echo -e "  • Stop system:   docker-compose -f docker-compose.mock.yml down"
    echo -e "  • Restart:       docker-compose -f docker-compose.mock.yml restart [service]"
    echo -e "  • Full rebuild:  docker-compose -f docker-compose.mock.yml up -d --build"
    echo ""

    echo -e "${YELLOW}💡 Note: This setup uses MOCK blockchain for quick testing.${NC}"
    echo -e "${YELLOW}   For production deployment with real Hyperledger Fabric:${NC}"
    echo -e "${YELLOW}   Run: ./setup-blockchain.sh full${NC}"
    echo ""

    echo -e "${GREEN}✨ Ready for testing! Start by visiting the Swagger UI URLs above. ✨${NC}"
}

function main() {
    case "$1" in
        "start")
            checkDocker
            startServices
            waitForServices
            testEndpoints
            showSystemInfo
            ;;
        "stop")
            docker-compose -f docker-compose.mock.yml down
            printSuccess "Services stopped"
            ;;
        "restart")
            docker-compose -f docker-compose.mock.yml restart
            printSuccess "Services restarted"
            ;;
        "logs")
            docker-compose -f docker-compose.mock.yml logs -f --tail=50
            ;;
        "status")
            echo "Service Status:"
            docker-compose -f docker-compose.mock.yml ps
            echo ""
            testEndpoints
            ;;
        "clean")
            docker-compose -f docker-compose.mock.yml down --volumes
            docker system prune -f
            printSuccess "Cleanup complete"
            ;;
        "rebuild")
            docker-compose -f docker-compose.mock.yml down
            docker-compose -f docker-compose.mock.yml up -d --build
            waitForServices
            testEndpoints
            printSuccess "Rebuild complete"
            ;;
        *)
            printHeader "🚀 Blockchain Degree Attestation - Quick Start"
            echo -e "${BLUE}Fast setup with mock blockchain for immediate testing${NC}\n"
            echo "Usage: $0 {start|stop|restart|logs|status|clean|rebuild}"
            echo ""
            echo "Commands:"
            echo "  start    - Start all services with mock blockchain"
            echo "  stop     - Stop all services"
            echo "  restart  - Restart all services"
            echo "  logs     - Show application logs"
            echo "  status   - Check service status and health"
            echo "  clean    - Stop and remove all containers/volumes"
            echo "  rebuild  - Rebuild and restart all services"
            echo ""
            echo "Examples:"
            echo "  $0 start     # Quick start with mock blockchain"
            echo "  $0 logs      # View real-time logs"
            echo "  $0 status    # Check if everything is working"
            echo "  $0 clean     # Clean shutdown and cleanup"
            echo ""
            echo -e "${YELLOW}💡 This creates a fully functional system for testing without${NC}"
            echo -e "${YELLOW}   requiring Hyperledger Fabric setup. Perfect for development!${NC}"
            exit 1
            ;;
    esac
}

# Run main function with all arguments
main "$@"
