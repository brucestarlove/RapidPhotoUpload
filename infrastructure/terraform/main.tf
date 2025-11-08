terraform {
  required_version = ">= 1.5"
  
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

locals {
  app_name = "rapidupload"
  env      = var.environment
  
  common_tags = {
    Project     = "RapidPhotoUpload"
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

# S3 Bucket for media storage
resource "aws_s3_bucket" "media" {
  bucket = "${local.app_name}-media-${local.env}"
  
  tags = merge(local.common_tags, {
    Name = "${local.app_name}-media-${local.env}"
  })
}

resource "aws_s3_bucket_versioning" "media" {
  bucket = aws_s3_bucket.media.id
  
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "media" {
  bucket = aws_s3_bucket.media.id
  
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_cors_configuration" "media" {
  bucket = aws_s3_bucket.media.id
  
  cors_rule {
    allowed_headers = ["*"]
    allowed_methods = ["PUT", "POST", "GET", "HEAD"]
    allowed_origins = var.allowed_origins
    expose_headers  = ["ETag"]
    max_age_seconds = 3600
  }
}

resource "aws_s3_bucket_public_access_block" "media" {
  bucket = aws_s3_bucket.media.id
  
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# IAM Role for EC2/ECS (application role)
resource "aws_iam_role" "app" {
  name = "${local.app_name}-app-role-${local.env}"
  
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = ["ec2.amazonaws.com", "ecs-tasks.amazonaws.com"]
        }
        Action = "sts:AssumeRole"
      }
    ]
  })
  
  tags = local.common_tags
}

resource "aws_iam_role_policy" "app_s3" {
  name = "${local.app_name}-s3-policy"
  role = aws_iam_role.app.id
  
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:PutObject",
          "s3:GetObject",
          "s3:DeleteObject",
          "s3:HeadObject"
        ]
        Resource = "${aws_s3_bucket.media.arn}/*"
      },
      {
        Effect = "Allow"
        Action = [
          "s3:ListBucket"
        ]
        Resource = aws_s3_bucket.media.arn
      }
    ]
  })
}

# RDS PostgreSQL
resource "aws_db_subnet_group" "main" {
  name       = "${local.app_name}-db-subnet-${local.env}"
  subnet_ids = var.database_subnet_ids
  
  tags = local.common_tags
}

resource "aws_security_group" "rds" {
  name_prefix = "${local.app_name}-rds-${local.env}-"
  vpc_id      = var.vpc_id
  
  ingress {
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = var.allowed_cidr_blocks
  }
  
  tags = local.common_tags
}

resource "aws_db_instance" "postgres" {
  identifier     = "${local.app_name}-db-${local.env}"
  engine         = "postgres"
  engine_version = "15.4"
  instance_class = var.db_instance_class
  
  allocated_storage     = 20
  max_allocated_storage = 100
  storage_type          = "gp3"
  storage_encrypted     = true
  
  db_name  = "rapidupload"
  username = var.db_master_username
  password = var.db_master_password
  
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  
  backup_retention_period = 7
  backup_window          = "03:00-04:00"
  maintenance_window     = "mon:04:00-mon:05:00"
  
  skip_final_snapshot       = var.environment == "dev" ? true : false
  final_snapshot_identifier = "${local.app_name}-final-${local.env}-${formatdate("YYYYMMDDhhmmss", timestamp())}"
  
  tags = merge(local.common_tags, {
    Name = "${local.app_name}-db-${local.env}"
  })
}

# Outputs
output "s3_bucket_name" {
  value = aws_s3_bucket.media.id
}

output "s3_bucket_arn" {
  value = aws_s3_bucket.media.arn
}

output "app_role_arn" {
  value = aws_iam_role.app.arn
}

output "rds_endpoint" {
  value = aws_db_instance.postgres.endpoint
}

output "rds_database_name" {
  value = aws_db_instance.postgres.db_name
}

