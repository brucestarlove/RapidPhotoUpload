# RapidPhotoUpload Backend

High-volume asynchronous media upload backend built with Spring Boot 3.4+, AWS S3, PostgreSQL, and following DDD/CQRS/VSA principles.

## Phase 1: Foundation & Infrastructure ✅

Phase 1 has been successfully implemented with:

- ✅ Spring Boot 3.4+ project structure
- ✅ PostgreSQL database schema (Flyway migrations)
- ✅ Core domain model (User aggregate, value objects)
- ✅ JWT authentication
- ✅ AWS S3 configuration
- ✅ Terraform infrastructure definitions
- ✅ Global exception handling

## Prerequisites

- Java 21+
- Maven 3.9+ or Gradle 8.5+
- PostgreSQL 15+ (local or RDS)
- AWS Account (for S3 and RDS)
- Terraform 1.5+ (for infrastructure provisioning)

## Quick Start

### 1. Database Setup

Create a PostgreSQL database:

```bash
createdb rapidupload
```

Or use environment variables to point to an existing database.

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

## Project Structure

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

## Next Steps

Phase 1 is complete! Proceed to **Phase 2: Core Upload Flow** to implement:
- Upload job creation
- S3 presigned URL generation
- Transactional outbox pattern

## Documentation

- [PRD](docs/00-PRD.md) - Product Requirements Document
- [Phase 1 Plan](docs/plans/PHASE1.md) - Detailed Phase 1 implementation guide
- [Phase 2 Plan](docs/plans/PHASE2.md) - Core Upload Flow
- [Phase 3 Plan](docs/plans/PHASE3.md) - Async Processing Pipeline
- [Phase 4 Plan](docs/plans/PHASE4.md) - Real-time Progress & Query APIs
- [Phase 5 Plan](docs/plans/PHASE5.md) - Observability & Production Readiness

## License

Copyright (c) 2024 Starscape

