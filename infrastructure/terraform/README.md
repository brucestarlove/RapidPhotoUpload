# Terraform Infrastructure

This directory contains Terraform configurations for provisioning AWS infrastructure for RapidPhotoUpload.

## What's Configured

- **S3 Bucket**: Media storage with versioning, encryption, CORS, and public access blocked
- **IAM Role**: Application role with S3 and SQS permissions
- **Aurora Serverless v2**: PostgreSQL cluster with auto-scaling
- **EC2 Instance**: Application server with security groups
- **SQS Queue**: Photo processing queue with Dead Letter Queue (DLQ)
- **EventBridge Rule**: Captures S3 ObjectCreated events and sends to SQS
- **S3 EventBridge Integration**: Enables EventBridge notifications on S3 bucket

## Setup

### 1. Install Terraform

```bash
brew install terraform
```

### 2. Configure Variables

Create `terraform.tfvars` (this file is gitignored):

```hcl
aws_region = "us-east-2"
environment = "dev"

# VPC Configuration
vpc_id = "vpc-xxxxx"
database_subnet_ids = ["subnet-xxxxx", "subnet-yyyyy"]
ec2_subnet_id = "subnet-xxxxx"

# Network Access
allowed_cidr_blocks = ["162.206.172.65/32"]  # Your IP
allowed_origins = ["http://localhost:3000", "http://localhost:8080"]

# Database
db_master_username = "postgres"
db_master_password = "your-secure-password"

# Aurora Serverless v2
aurora_min_capacity = 0.5
aurora_max_capacity = 16

# EC2
ec2_ami_id = "ami-xxxxx"  # Amazon Linux 2023 in us-east-2
ec2_instance_type = "t3.micro"
```

### 3. Initialize Terraform

```bash
terraform init
```

### 4. Plan Changes

```bash
terraform plan
```

### 5. Apply Changes

```bash
terraform apply
```

## Importing Existing Resources

Since you've already created Aurora and S3 manually, you have two options:

### Option A: Import Existing Resources (Recommended)

Import your existing Aurora cluster:

```bash
# Import Aurora cluster
terraform import aws_rds_cluster.aurora starscape-1

# Import Aurora instance
terraform import aws_rds_cluster_instance.aurora_instance starscape-1-instance-1

# Import S3 bucket (if you want Terraform to manage it)
terraform import aws_s3_bucket.media starscaperapidphotoupload--use2-az1--x-s3
```

Then run `terraform plan` to see if there are any differences.

### Option B: Use Terraform Only for New Resources

- Keep Aurora and S3 as manually managed
- Use Terraform only for EC2 and future resources
- Document existing resources in `MANUAL_RESOURCES.md`

## Outputs

After applying, Terraform will output:

- `s3_bucket_name`: S3 bucket name
- `rds_endpoint`: Aurora writer endpoint
- `rds_reader_endpoint`: Aurora reader endpoint
- `ec2_public_ip`: EC2 instance public IP
- `ec2_public_dns`: EC2 instance public DNS
- `sqs_queue_url`: SQS queue URL for photo processing (use this in `application.yml`)
- `sqs_queue_arn`: SQS queue ARN
- `sqs_dlq_url`: Dead Letter Queue URL
- `eventbridge_rule_arn`: EventBridge rule ARN for S3 events

### Using SQS Queue URL in Application

After applying Terraform, update your `application.yml`:

```yaml
aws:
  sqs:
    queue-url: <value from terraform output sqs_queue_url>
    dlq-url: <value from terraform output sqs_dlq_url>
```

## Destroying Resources

⚠️ **Warning**: This will delete all resources!

```bash
terraform destroy
```

## Notes

- Terraform state is stored locally in `terraform.tfstate` (gitignored)
- For team collaboration, consider using [Terraform Cloud](https://app.terraform.io) or S3 backend for remote state
- Always run `terraform plan` before `terraform apply` to review changes

