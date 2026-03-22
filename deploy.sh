#!/bin/bash
# ==============================================
# CloudSync EC2 Deployment Script
# Run this ON your EC2 instance
# ==============================================

set -e

echo "========================================"
echo "CloudSync EC2 Deployment"
echo "========================================"

# Check if running as ec2-user
if [ "$(whoami)" != "ec2-user" ]; then
    echo "Please run as ec2-user, or add ec2-user to sudoers"
    exit 1
fi

# ---- Configuration (Update these values) ----
APP_DIR="/opt/cloudsync"
GIT_REPO="https://github.com/YOUR_USERNAME/CloudSync.git"  # <-- UPDATE THIS
S3_BUCKET="cloudsync-files-YOURNAME"                        # <-- UPDATE THIS
RDS_HOST="cloudsync-db.xxxxx.us-east-1.rds.amazonaws.com"  # <-- UPDATE THIS
DB_PASSWORD="YourSecurePassword123"                         # <-- UPDATE THIS
AWS_ACCESS_KEY="AKIAXXXXXXXXXXXXXXXXX"                     # <-- UPDATE THIS
AWS_SECRET_KEY="xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"       # <-- UPDATE THIS
JWT_SECRET="YourSuperSecretJWTKeyAtLeast256BitsLongForHS256Algorithm2024"  # <-- UPDATE THIS

# ---- Step 1: Install dependencies ----
echo "[1/6] Installing dependencies..."
sudo yum update -y
sudo amazon-linux-extras install docker nginx -y
sudo systemctl start docker
sudo systemctl enable docker
sudo systemctl start nginx
sudo systemctl enable nginx

# ---- Step 2: Setup directory ----
echo "[2/6] Setting up directories..."
sudo mkdir -p $APP_DIR
sudo chown -R ec2-user:ec2-user $APP_DIR

# ---- Step 3: Clone or copy source code ----
echo "[3/6] Getting source code..."
if [ -d "$APP_DIR/.git" ]; then
    echo "Git repo exists, pulling latest..."
    cd $APP_DIR && git pull
else
    echo "Cloning repository..."
    rm -rf $APP_DIR
    git clone $GIT_REPO $APP_DIR
fi

# ---- Step 4: Build the JAR ----
echo "[4/6] Building Spring Boot application..."
cd $APP_DIR
./mvnw package -DskipTests -B

# ---- Step 5: Configure Docker Compose ----
echo "[5/6] Configuring Docker Compose..."
cat > $APP_DIR/.env << EOF
DB_HOST=$RDS_HOST
DB_PORT=3306
DB_NAME=cloudsync
DB_USERNAME=cloudsync
DB_PASSWORD=$DB_PASSWORD
REDIS_HOST=
REDIS_PORT=6379
REDIS_PASSWORD=
AWS_REGION=us-east-1
AWS_S3_BUCKET=$S3_BUCKET
AWS_ACCESS_KEY=$AWS_ACCESS_KEY
AWS_SECRET_KEY=$AWS_SECRET_KEY
JWT_SECRET=$JWT_SECRET
EOF

# ---- Step 6: Deploy with Docker ----
echo "[6/6] Starting CloudSync with Docker..."
cd $APP_DIR
docker build -t cloudsync:latest .
docker stop cloudsync-backend 2>/dev/null || true
docker rm cloudsync-backend 2>/dev/null || true
docker run -d \
  --name cloudsync-backend \
  -p 8080:8080 \
  --env-file $APP_DIR/.env \
  --restart unless-stopped \
  --memory=1G \
  --memory-reservation=512M \
  cloudsync:latest

# ---- Verify ----
echo ""
echo "========================================"
echo "Deployment complete!"
echo "========================================"
sleep 5

# Health check
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/actuator/health)

if [ "$HTTP_CODE" = "200" ]; then
    echo "✅ Backend is UP and running!"
    echo "   Health: http://localhost:8080/api/actuator/health"
    echo "   Swagger: http://localhost:8080/api/swagger-ui.html"
else
    echo "❌ Backend health check failed (HTTP $HTTP_CODE)"
    echo "   Run 'docker logs cloudsync-backend' to debug"
fi

echo ""
echo "Next steps:"
echo "  1. Configure Nginx: sudo nano /etc/nginx/conf.d/cloudsync.conf"
echo "  2. Build frontend: cd $APP_DIR/frontend && npm install && npm run build"
echo "  3. Setup free domain: noip.com or Cloudflare"
