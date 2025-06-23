#!/bin/bash

echo "🐳 Docker Rebuild Script for Fixed API Gateway"
echo "=============================================="

# Navigate to project root
cd ~/Development/blockchain-degree-attestation

echo "🧹 Step 1: Cleaning up previous failed builds..."
docker system prune -f
docker builder prune -f

echo "🔨 Step 2: Building only the API Gateway service..."
docker-compose build api-gateway

if [ $? -eq 0 ]; then
    echo "✅ API Gateway Docker build successful!"

    echo "🏗️ Step 3: Building all remaining services..."
    docker-compose build

    if [ $? -eq 0 ]; then
        echo "✅ All services built successfully!"

        echo "🚀 Step 4: Starting the services..."
        docker-compose up -d

        echo "📋 Service status:"
        docker-compose ps

        echo ""
        echo "🎉 Success! Your blockchain degree attestation system is now running."
        echo ""
        echo "📍 Service URLs:"
        echo "   🚪 API Gateway: http://localhost:8080"
        echo "   🏛️ Attestation Authority: http://localhost:8081"
        echo "   🏫 University Portal: http://localhost:8082"
        echo "   🏢 Employer Portal: http://localhost:8083"
        echo ""
        echo "🔍 To check logs:"
        echo "   docker-compose logs -f api-gateway"
        echo "   docker-compose logs -f [service-name]"

    else
        echo "❌ Some services failed to build. Check individual service logs:"
        docker-compose build 2>&1 | grep -E "(ERROR|error|Error|FAILED|failed|Failed)"
    fi

else
    echo "❌ API Gateway build failed. Checking for remaining compilation issues..."

    echo "📋 Build log:"
    docker-compose build api-gateway 2>&1 | tail -30

    echo ""
    echo "🔧 Potential next steps:"
    echo "   1. Check if all Kotlin files compile individually"
    echo "   2. Verify Gradle dependencies are correct"
    echo "   3. Ensure shared modules are built first"
    echo "   4. Check for any missing imports or typos"
fi

echo ""
echo "💡 Additional debugging commands:"
echo "   • Check container logs: docker-compose logs api-gateway"
echo "   • Shell into container: docker-compose exec api-gateway bash"
echo "   • Rebuild specific service: docker-compose build --no-cache api-gateway"
echo "   • View build context: docker-compose build --progress=plain api-gateway"