# CloudSync - AWS Free Tier Deployment Guide

Simple, resume-level deployment. One EC2 instance, one RDS MySQL, one S3 bucket. No Docker, no ECS, no Kubernetes complexity.

---

## Architecture

```
User → Route53/CloudFront → EC2 (Spring Boot :8080) → RDS MySQL
                                              ↓
                                           S3 (file storage)
```

---

## Phase 1: AWS Account Setup

### 1.1 Create IAM User (for local deployment only)

If you want to deploy from your local machine instead of directly on EC2.

1. Go to **IAM Console** → Users → Create User
2. Name: `cloudsync-deploy`
3. Attach policies: `AmazonS3FullAccess`, `AmazonRDSFullAccess` (or use inline policies for least privilege)
4. Create access key → save `Access Key ID` and `Secret Access Key`

> **For EC2 deployment (recommended):** EC2 instance profiles handle credentials automatically — no IAM user needed.

---

## Phase 2: S3 Bucket (File Storage)

### 2.1 Create S3 Bucket

1. Go to **S3 Console** → Create bucket
2. Bucket name: `cloudsync-files-YOURNAME` (must be globally unique)
3. Region: `us-east-1`
4. Block Public Access: **uncheck** "Block all public access" → Enable public access for file downloads
5. Enable ACLs: Yes
6. Click Create

### 2.2 Configure Bucket Policy

1. Open the bucket → Permissions → Bucket policy → Edit
2. Paste this policy (replace `BUCKET_NAME` with your bucket name):

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "PublicReadGetObject",
      "Effect": "Allow",
      "Principal": "*",
      "Action": "s3:GetObject",
      "Resource": "arn:aws:s3:::BUCKET_NAME/*"
    },
    {
      "Sid": "EC2ReadWriteAccess",
      "Effect": "Allow",
      "Principal": {
        "AWS": "arn:aws:iam::ACCOUNT_ID:root"
      },
      "Action": [
        "s3:GetObject",
        "s3:PutObject",
        "s3:DeleteObject",
        "s3:ListBucket"
      ],
      "Resource": [
        "arn:aws:s3:::BUCKET_NAME",
        "arn:aws:s3:::BUCKET_NAME/*"
      ]
    }
  ]
}
```

3. Save

### 2.3 Enable S3 Versioning (optional but recommended)

1. Bucket → Properties → Bucket versioning → Enable

---

## Phase 3: RDS MySQL Database

### 3.1 Create RDS Instance

1. Go to **RDS Console** → Create database
2. Choose: **MySQL**
3. Templates: **Free tier**
4. Settings:
   - DB Instance Identifier: `cloudsync-db`
   - Master username: `admin`
   - Master password: `YourSecurePassword123` (save it!)
5. Instance class: `db.t3.micro` (Free tier)
6. Storage: **20 GB** (Free tier limit)
7. Connectivity:
   - Compute resource: **Don't connect to an EC2 compute resource** (we'll configure manually)
   - VPC: Default VPC
   - Public Access: **Yes** (for now, restrict later with SG)
   - VPC Security Group: Create new → name it `cloudsync-rds-sg`
   - Database Port: `3306`
8. Additional Configuration:
   - Initial DB Name: `cloudsync`
   - Backup: **Disable** (save free tier storage) or keep 1 day retention
   - Encryption: **Disable** (Free tier)
9. Click **Create database**

> Wait 5-10 minutes for DB to be created. Note the endpoint: `cloudsync-db.xxxxx.us-east-1.rds.amazonaws.com`

### 3.2 Configure RDS Security Group

1. Go to **EC2 Console** → Security Groups → Find `cloudsync-rds-sg`
2. Edit inbound rules:
   - Type: **MySQL/Aurora**, Port: 3306, Source: **EC2 Security Group** (we'll create this in Phase 4)
   - For now, add your home IP: Type: MySQL, Port: 3306, Source: **My IP**
3. Save rules

---

## Phase 4: EC2 Instance (Spring Boot App)

### 4.1 Create EC2 Instance

1. Go to **EC2 Console** → Instances → Launch instances
2. Name: `cloudsync-server`
3. OS: **Ubuntu 24.04 LTS** (free tier, widely supported)
4. Instance type: `t3.micro` (Free tier eligible)
5. Key pair: Create new → download `.pem` file → **save it somewhere safe**
6. Network settings:
   - VPC: Default VPC
   - Subnet: Any (pick one AZ to avoid NAT costs)
   - Auto-assign public IP: **Enable**
   - Create new security group: `cloudsync-ec2-sg`
   - Inbound rules:
     - SSH (22): My IP (for initial setup only)
     - HTTP (80): Anywhere
     - HTTPS (443): Anywhere
     - Custom TCP (8080): Anywhere (Spring Boot port)
7. Advanced details:
   - IAM instance profile: Create new → name `cloudsync-ec2-role`
   - User data (paste this to auto-install Java 17 on first boot):

```bash
#!/bin/bash
apt update -y
apt install -y openjdk-17-jdk maven nginx certbot python3-certbot-nginx -y
systemctl enable nginx
```

8. Click **Launch Instance**

### 4.2 Create EC2 IAM Role (for S3 access from EC2)

1. Go to **IAM Console** → Roles → Create role
2. Trusted entity: **AWS service** → EC2
3. Policies:
   - `AmazonS3FullAccess` (or create custom policy below)
4. Role name: `cloudsync-ec2-role`
5. Attach to EC2 instance:
   - EC2 Console → Instances → Right-click `cloudsync-server` → Security → Modify IAM role → Select `cloudsync-ec2-role`

**Custom S3 Policy (recommended instead of full access):**
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject",
        "s3:DeleteObject",
        "s3:ListBucket"
      ],
      "Resource": [
        "arn:aws:s3:::cloudsync-files-YOURNAME",
        "arn:aws:s3:::cloudsync-files-YOURNAME/*"
      ]
    }
  ]
}
```

### 4.3 Connect to EC2

```bash
# Get your public IP from EC2 console
ssh -i "path/to/your-key.pem" ubuntu@YOUR_EC2_PUBLIC_IP
```

### 4.4 Build the JAR on EC2

On your EC2 instance:

```bash
# Install git and clone (or upload JAR directly)
sudo apt update -y
sudo apt install -y openjdk-17-jdk maven git

# Clone from GitHub
git clone https://github.com/AlgoArena2965/CloudSync.git
cd CloudSync

# Build JAR
./mvnw package -DskipTests -B

# JAR will be at: target/*.jar
ls target/*.jar
```

### 4.5 Configure Environment Variables

Create a `.env` file:

```bash
nano /home/ubuntu/cloudsync.env
```

Paste:
```
SPRING_PROFILES_ACTIVE=prod
DB_HOST=cloudsync-db.xxxxx.us-east-1.rds.amazonaws.com
DB_PORT=3306
DB_NAME=cloudsync
DB_USERNAME=admin
DB_PASSWORD=YourSecurePassword123
AWS_REGION=us-east-1
AWS_S3_BUCKET=cloudsync-files-YOURNAME
JWT_SECRET=YourSecureJwtSecretKeyThatIsAtLeast64CharactersLongBase64Encoded
UPLOAD_TEMP_DIR=/tmp/cloudsync-uploads
SERVER_PORT=8080
```

### 4.6 Run the Application

```bash
# Create upload directory
sudo mkdir -p /tmp/cloudsync-uploads
sudo chown -R ubuntu:ubuntu /tmp/cloudsync-uploads

# Run (using screen or nohup to keep it running)
cd CloudSync
nohup java -jar target/*.jar --spring.config.additional-location=optional:/home/ubuntu/cloudsync.env &
```

Or better, create a **systemd service**:

```bash
sudo nano /etc/systemd/system/cloudsync.service
```

Paste:
```ini
[Unit]
Description=CloudSync Spring Boot Application
After=network.target

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/home/ubuntu/CloudSync
ExecStart=/usr/bin/java -jar target/*.jar
EnvironmentFile=/home/ubuntu/cloudsync.env
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

Then:
```bash
sudo systemctl daemon-reload
sudo systemctl enable cloudsync
sudo systemctl start cloudsync
sudo systemctl status cloudsync
```

### 4.7 Check if Running

```bash
curl http://localhost:8080/api/actuator/health
# Should return: {"status":"UP"}
```

---

## Phase 5: Nginx Reverse Proxy (HTTPS + Domain)

### 5.1 Point Domain to EC2 (Optional)

If you have a domain (e.g., from Namecheap/GoDaddy):
1. Create an **A record**: `cloudsync.yourdomain.com` → Your EC2 Public IP
2. Create an **A record**: `www.cloudsync.yourdomain.com` → Your EC2 Public IP

### 5.2 Configure Nginx

```bash
sudo nano /etc/nginx/sites-available/cloudsync
```

Paste:
```nginx
server {
    listen 80;
    server_name cloudsync.yourdomain.com www.cloudsync.yourdomain.com YOUR_EC2_PUBLIC_IP;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        client_max_body_size 10G;
        proxy_read_timeout 300s;
        proxy_connect_timeout 75s;
    }
}
```

```bash
sudo ln -s /etc/nginx/sites-available/cloudsync /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

### 5.3 Setup SSL (Let's Encrypt - Free)

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d cloudsync.yourdomain.com -d www.cloudsync.yourdomain.com
# Follow prompts, choose redirect HTTP to HTTPS
```

SSL auto-renews. Test with:
```bash
curl https://cloudsync.yourdomain.com/api/actuator/health
```

---

## Phase 6: Security Hardening

### 6.1 Update EC2 Security Group

After confirming SSH works, restrict SSH access:
- SSH (22): My IP only (or disable entirely and use Session Manager)

### 6.2 Update RDS Security Group

Change MySQL inbound to only accept from EC2 security group:
- Type: MySQL (3306), Source: `cloudsync-ec2-sg` (select from dropdown)

### 6.3 Disable DB Public Access (Recommended)

1. RDS Console → Databases → `cloudsync-db` → Modify
2. Public Access: **No**
3. Continue → Apply immediately
4. Now the DB is accessible ONLY from within the VPC (EC2 can reach it)

---

## Phase 7: Cost Optimization (Stay in Free Tier)

| Service | Config | Monthly Cost |
|---------|--------|-------------|
| EC2 t3.micro | 750h free | $0.00 |
| RDS db.t3.micro | 750h free, 20GB | $0.00 |
| S3 | 5GB storage | ~$0.00 |
| Nginx | n/a | $0.00 |
| Route53 | $0.50/zone/mo | ~$0.50 |
| Data Transfer | varies | ~$0-5 |
| **Total** | | **~$0-5/mo** |

**Keep costs at $0:**
- Stay within 20GB RDS storage
- Use same AZ for EC2 and RDS (no inter-AZ data transfer charges)
- S3 requests are free within limits
- **Disable CloudWatch detailed monitoring** (use basic)

---

## Phase 8: Deployment Checklist

```bash
# 1. Verify DB connectivity
mysql -h cloudsync-db.xxxxx.us-east-1.rds.amazonaws.com -u admin -p cloudsync

# 2. Verify S3 from EC2
aws s3 ls s3://cloudsync-files-YOURNAME/ --region us-east-1

# 3. Check app health
curl https://YOUR_DOMAIN/api/actuator/health

# 4. View app logs
sudo journalctl -u cloudsync -f

# 5. Check disk space (ensure no accumulation)
df -h

# 6. Set up log rotation
sudo apt install -y logrotate
sudo nano /etc/logrotate.d/cloudsync
```
```
/home/ubuntu/CloudSync/*.log {
    daily
    rotate 7
    compress
    delaycompress
    missingok
    notifempty
}
```

---

## Quick Reference - All Commands

```bash
# Connect to server
ssh -i "cloudsync-key.pem" ubuntu@EC2_PUBLIC_IP

# Deploy new version
cd CloudSync
git pull
./mvnw package -DskipTests -B
sudo systemctl restart cloudsync
sudo systemctl status cloudsync

# Check logs
sudo journalctl -u cloudsync -f --lines=50

# Restart app
sudo systemctl restart cloudsync

# Check SSL expiry
sudo certbot certificates

# Renew SSL (auto-renews, but manual)
sudo certbot renew

# Free tier check
aws logs describe-log-groups  # CloudWatch
```

---

## Free Tier Gotchas

1. **RDS stops after 7 days of inactivity** on free tier → wake it up manually
2. **t3.micro CPU credits** can run out → monitor with `aws cloudwatch get-metric-statistics`
3. **S3 request limits** — for a resume project, you're fine. Don't build a file-sharing service for thousands of users
4. **Data transfer** — if you serve lots of files through CloudFront, charges apply. Direct downloads from S3 are mostly free
5. **Never commit `.env` files** to GitHub. The `.env.prod` in this repo is an example template only

---

## Future Improvements (when you want more)

- Add CloudFront CDN for faster global file downloads
- Move to ECS/EKS with Docker containers
- Add AWS ElastiCache (Redis) for caching
- Set up Auto Scaling with Load Balancer
- Add AWS Cognito for better auth
- Enable AWS CloudWatch alarms for monitoring
