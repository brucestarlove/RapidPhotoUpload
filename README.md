# RapidPhotoUpload Backend

High-volume asynchronous media upload backend built with Spring Boot 3.4+, AWS S3, PostgreSQL, and following DDD/CQRS/VSA principles.

## Dev & Deploy

`mvn clean install`

`mvn spring-boot:run`

some first time things for AWS:
`terraform plan` with a lot of LLM's refinement for reasons including me manually creating the S3 and Aurora PostgreSQL, to eventually `terraform apply`

using AWS Systems Manager Session Manager (SSM) instead of SSH keys

`aws ssm start-session --target i-040798390b54b4294 --profile gauntlet`

created `/etc/systemd/system/rapidupload.service` and `/opt/rapidupload/application-prod.yml`

`sudo chown ec2-user:ec2-user /opt/rapidupload/application-prod.yml`
`sudo chmod 600 /opt/rapidupload/application-prod.yml`

`sudo chown -R ec2-user:ec2-user /opt/rapidupload`
`sudo chmod 755 /opt/rapidupload`

### Reload systemd
`sudo systemctl daemon-reload`

### Enable service (starts on boot)
`sudo systemctl enable rapidupload`

### Check status (will show as inactive since we haven't deployed the JAR yet)
`sudo systemctl status rapidupload`

1. Build JAR: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn clean package -DskipTests`
2. Deploy JAR: `aws s3 cp target/rapidupload-*.jar s3://starscape-rapidphotoupload/deployments/latest.jar --profile gauntlet` (gauntlet = my ~/.aws/config profile)

3. ### Download JAR from S3 to home directory, then move
  1. `aws s3 cp s3://starscape-rapidphotoupload/deployments/latest.jar ~/app.jar`
  2. `sudo mv ~/app.jar /opt/rapidupload/app.jar`
  3. `sudo chown ec2-user:ec2-user /opt/rapidupload/app.jar`

#### Start the service
`sudo systemctl start rapidupload`

#### Check status
`sudo systemctl status rapidupload`

#### Watch logs (optional ofc)
`sudo journalctl -u rapidupload -f`

### Now with Github Actions CI/CD
On pull requests:
1. Runs tests
1. Builds JAR
1. No deployment

On push to main:
1. Runs tests
1. Builds JAR
1. Uploads to S3
1. Deploys to EC2 via SSM
1. Runs health check

## Prerequisites

- Java 21+
- Maven 3.9+ or Gradle 8.5+
- PostgreSQL 15+ (local or RDS)
- AWS Account (for S3, RDS, EC2, SQS)
- Terraform 1.5+ (for infrastructure provisioning)

## Quick Start

### 1. Database Setup

AWS Aurora PostgreSQL Serverless v2.

### 2. Configure Application

Set environment variables or update `src/main/resources/application.yml`:

```bash
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=rapidupload
export DB_USER=postgres
export DB_PASSWORD=your_password
export JWT_SECRET=your-secret-key-minimum-256-bits-required
export AWS_REGION=us-east-1
export S3_BUCKET=rapidupload-media-dev
```

### 3. Run Database Migrations

Flyway will automatically run migrations on application startup, or run manually:

```bash
mvn flyway:migrate
```

### 4. Build and Run

```bash
mvn clean install
mvn spring-boot:run
```

The application will start on `http://localhost:8080`

### 5. Test Authentication

Register a new user:

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "password123"
  }'
```

Login:

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "password123"
  }'
```

## API Endpoints

### Authentication

- `POST /api/auth/register` - Register a new user
- `POST /api/auth/login` - Login and get JWT token

### Health Check

- `GET /actuator/health` - Application health status
curl -X GET http://

## Project Structure (init)

```
com.starscape.rapidupload/
├── RapidUploadApplication.java          # Main entry point
├── common/                               # Shared infrastructure
│   ├── domain/                           # Base domain primitives
│   ├── config/                           # Global config
│   ├── exception/                        # Global exception handling
│   └── security/                         # Security components
└── features/                             # Feature slices
    └── auth/                             # Authentication slice
        ├── api/                          # Controllers
        ├── app/                          # Application services
        ├── domain/                       # Domain model
        └── infra/                        # Infrastructure
```

## Infrastructure

Terraform configurations are in `infrastructure/terraform/`:

```bash
cd infrastructure/terraform
terraform init
terraform plan -var-file=environments/dev.tfvars
terraform apply -var-file=environments/dev.tfvars
```

## Documentation

- [PRD](docs/00-PRD.md) - Product Requirements Document
- [Phase 1 Plan](docs/plans/PHASE1.md) - Detailed Phase 1 implementation guide
- [Phase 2 Plan](docs/plans/PHASE2.md) - Core Upload Flow
- [Phase 3 Plan](docs/plans/PHASE3.md) - Async Processing Pipeline
- [Phase 4 Plan](docs/plans/PHASE4.md) - Real-time Progress & Query APIs
- [Phase 5 Plan](docs/plans/PHASE5.md) - Observability & Production Readiness
