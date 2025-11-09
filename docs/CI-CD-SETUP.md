# CI/CD Setup with GitHub Actions

This guide explains how to set up automated CI/CD for RapidPhotoUpload using GitHub Actions.

## Overview

The CI/CD pipeline will:
1. **Build & Test**: Compile code, run tests on every push/PR
2. **Deploy**: Automatically deploy to EC2 when code is pushed to `main` branch

## Prerequisites

1. **GitHub Repository**: Your code must be in a GitHub repository
2. **AWS Credentials**: Access key and secret key for AWS
3. **EC2 Instance**: Already created via Terraform
4. **SSM Agent**: Pre-installed on Amazon Linux 2023 (automatically configured)

## Step-by-Step Setup

### 1. Get EC2 Instance Details

After running `terraform apply`, get your EC2 instance details:

```bash
cd infrastructure/terraform
terraform output ec2_instance_id
terraform output ec2_public_ip
```

### 2. Configure GitHub Secrets

Go to your GitHub repository → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**

Add these secrets:

| Secret Name | Description | How to Get |
|------------|-------------|------------|
| `AWS_ACCESS_KEY_ID` | AWS access key | AWS Console → IAM → Users → Your User → Security Credentials |
| `AWS_SECRET_ACCESS_KEY` | AWS secret key | Same as above (create new access key if needed) |
| `EC2_INSTANCE_ID` | EC2 instance ID | From `terraform output ec2_instance_id` |

**Important**: 
- Never commit AWS credentials to git
- **No SSH keys needed!** SSM Session Manager handles access securely
- The IAM user/role used by GitHub Actions needs `ssm:SendCommand` and `ssm:GetCommandInvocation` permissions

### 3. Create Systemd Service on EC2

Connect to your EC2 instance via SSM Session Manager and create a systemd service:

**Option A: Via AWS Console**
1. Go to EC2 Console → Instances
2. Select your instance → Connect → Session Manager → Connect

**Option B: Via AWS CLI**
```bash
aws ssm start-session --target <EC2_INSTANCE_ID> --profile gauntlet
```

Then create the service file:

```bash
# Connect via SSM (no SSH key needed!)
aws ssm start-session --target <EC2_INSTANCE_ID> --profile gauntlet

# Once connected, create service file
sudo nano /etc/systemd/system/rapidupload.service
```

Add this content:

```ini
[Unit]
Description=RapidPhotoUpload Spring Boot Application
After=network.target

[Service]
Type=simple
User=ec2-user
WorkingDirectory=/opt/rapidupload
ExecStart=/usr/bin/java -jar /opt/rapidupload/app.jar
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal

Environment="SPRING_PROFILES_ACTIVE=prod"
Environment="DB_HOST=starscape-1.cluster-c1uuigcm4bd1.us-east-2.rds.amazonaws.com"
Environment="DB_PORT=5432"
Environment="DB_NAME=StarscapeRapidPhotoUpload"
Environment="DB_USER=postgres"
Environment="DB_PASSWORD=your_password_here"
Environment="AWS_REGION=us-east-2"
Environment="S3_BUCKET=starscape-rapidphotoupload"
Environment="JWT_SECRET=your_jwt_secret_here"

[Install]
WantedBy=multi-user.target
```

**Important**: Replace `your_password_here` and `your_jwt_secret_here` with actual values!

Then enable and start the service:

```bash
sudo systemctl daemon-reload
sudo systemctl enable rapidupload
# Don't start yet - wait for first deployment
```

### 4. Configure Application Properties

Create `/opt/rapidupload/application-prod.yml` on EC2:

```bash
sudo nano /opt/rapidupload/application-prod.yml
```

```yaml
spring:
  datasource:
    url: jdbc:postgresql://starscape-1.cluster-c1uuigcm4bd1.us-east-2.rds.amazonaws.com:5432/StarscapeRapidPhotoUpload
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD}

aws:
  region: ${AWS_REGION:us-east-2}
  s3:
    bucket: ${S3_BUCKET:starscape-rapidphotoupload}

app:
  security:
    jwt:
      secret: ${JWT_SECRET}
      expiration-ms: 86400000
```

### 5. Set Up IAM Permissions

**EC2 Instance Permissions** (already configured by Terraform):
- ✅ S3 read/write permissions
- ✅ SSM managed instance core (for Session Manager)

**GitHub Actions IAM User/Role Permissions** (you need to add):
The IAM user/role used by GitHub Actions needs these permissions:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject"
      ],
      "Resource": "arn:aws:s3:::starscape-rapidphotoupload/deployments/*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "ssm:SendCommand",
        "ssm:GetCommandInvocation",
        "ssm:WaitCommandExecuted"
      ],
      "Resource": "arn:aws:ec2:us-east-2:*:instance/*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "ssm:DescribeInstanceInformation"
      ],
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "ec2:DescribeInstances"
      ],
      "Resource": "*"
    }
  ]
}
```

Verify EC2 IAM role has S3 permissions:

```bash
aws iam get-role-policy \
  --role-name starscape-rapidphotoupload-app-role-dev \
  --policy-name starscape-rapidphotoupload-s3-policy \
  --profile gauntlet
```

### 6. Test the Pipeline

1. **Push code to a PR**: Should trigger CI (build + test) only
2. **Merge to main**: Should trigger full CI/CD (build + test + deploy)

## Workflow Files

### `.github/workflows/ci-cd.yml`
- Runs on pushes to `main` and `develop`
- Builds, tests, and deploys to EC2

### `.github/workflows/ci-only.yml`
- Runs on pull requests
- Only builds and tests (no deployment)

## Deployment Process

When code is pushed to `main`:

1. **Build**: Compiles Java code with Maven
2. **Test**: Runs all unit and integration tests
3. **Package**: Creates JAR file
4. **Upload to S3**: Uploads JAR to `s3://starscape-rapidphotoupload/deployments/`
5. **Deploy to EC2**: 
   - SSH into EC2
   - Downloads JAR from S3
   - Stops old service
   - Starts new service
6. **Health Check**: Verifies application is running

## Manual Deployment

If you need to deploy manually:

```bash
# Build locally
mvn clean package -DskipTests

# Upload to S3
aws s3 cp target/rapidupload-*.jar s3://starscape-rapidphotoupload/deployments/latest.jar --profile gauntlet

# Connect to EC2 via SSM and deploy
aws ssm start-session --target <EC2_INSTANCE_ID> --profile gauntlet

# Once connected, run:
aws s3 cp s3://starscape-rapidphotoupload/deployments/latest.jar /opt/rapidupload/app.jar
sudo systemctl restart rapidupload
```

Or use SSM command directly:

```bash
aws ssm send-command \
  --instance-ids <EC2_INSTANCE_ID> \
  --document-name "AWS-RunShellScript" \
  --parameters 'commands=["aws s3 cp s3://starscape-rapidphotoupload/deployments/latest.jar /opt/rapidupload/app.jar", "sudo systemctl restart rapidupload"]' \
  --profile gauntlet
```

## Troubleshooting

### Deployment fails with "AccessDenied" for SSM
- Verify IAM user/role has `ssm:SendCommand` permission
- Check EC2 instance has `AmazonSSMManagedInstanceCore` policy attached
- Ensure SSM agent is running: `sudo systemctl status amazon-ssm-agent`

### Application won't start
- Check logs: `sudo journalctl -u rapidupload -f`
- Verify environment variables in systemd service
- Check database connectivity

### Health check fails
- Application might need more time to start
- Check security group allows port 8080
- Verify application is listening on 0.0.0.0:8080 (not just localhost)

## Security Best Practices

1. ✅ Use GitHub Secrets (never commit credentials)
2. ✅ Use IAM roles instead of access keys when possible
3. ✅ **No SSH keys needed** - SSM Session Manager is more secure
4. ✅ Use AWS Secrets Manager for database passwords (future improvement)
5. ✅ Enable CloudWatch logging for audit trail
6. ✅ SSM commands are logged in CloudTrail automatically

## Next Steps

- [ ] Set up AWS Secrets Manager for sensitive configs
- [ ] Add deployment notifications (Slack, email)
- [ ] Set up blue/green deployments for zero downtime
- [ ] Add automated rollback on health check failure
- [ ] Set up staging environment

