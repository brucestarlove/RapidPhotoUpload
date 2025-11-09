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
  region  = var.aws_region
  profile = "gauntlet"  # AWS profile for RapidPhotoUpload account
}

locals {
  app_name = "starscape-rapidphotoupload"
  env      = var.environment
  
  common_tags = {
    Project     = "StarscapeRapidPhotoUpload"
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

# S3 Bucket for media storage (shared across dev/prod)
resource "aws_s3_bucket" "media" {
  bucket = "starscape-rapidphotoupload"
  
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

# Attach AWS managed policy for SSM access (allows Session Manager)
resource "aws_iam_role_policy_attachment" "app_ssm" {
  role       = aws_iam_role.app.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# Aurora Serverless v2 PostgreSQL Cluster
# Using existing "default" subnet group to avoid cluster replacement
# If you want a dedicated subnet group, uncomment below and update cluster config
# resource "aws_db_subnet_group" "main" {
#   name       = "${local.app_name}-db-subnet-${local.env}"
#   subnet_ids = var.database_subnet_ids
#   
#   tags = local.common_tags
# }

# Using existing RDS security group (sg-0e34638c2e6dab8ed) directly
# No need to create a new one since we're referencing the existing SG by ID

resource "aws_rds_cluster" "aurora" {
  cluster_identifier      = "starscape-1"  # Match existing cluster name
  engine                  = "aurora-postgresql"
  engine_mode             = "provisioned"
  engine_version          = "17.4"  # Match existing version
  database_name           = "StarscapeRapidPhotoUpload"
  master_username         = var.db_master_username
  # Note: Password is managed by AWS Secrets Manager (ManageMasterUserPassword)
  # Don't set master_password when Secrets Manager is enabled
  
  db_subnet_group_name   = "default"  # Using existing default subnet group
  vpc_security_group_ids = ["sg-0e34638c2e6dab8ed"]  # Use existing security group
  
  serverlessv2_scaling_configuration {
    max_capacity = var.aurora_max_capacity
    min_capacity = var.aurora_min_capacity
  }
  
  backup_retention_period = 7
  preferred_backup_window = "03:00-04:00"
  preferred_maintenance_window = "mon:04:00-mon:05:00"
  
  deletion_protection = true  # Keep enabled for safety
  
  skip_final_snapshot       = var.environment == "dev" ? true : false
  final_snapshot_identifier = var.environment == "dev" ? null : "${local.app_name}-final-${local.env}-${formatdate("YYYYMMDDhhmmss", timestamp())}"
  
  enabled_cloudwatch_logs_exports = ["postgresql", "iam-db-auth-error", "instance"]  # Keep existing exports
  
  # Keep existing settings
  copy_tags_to_snapshot = true
  enable_http_endpoint  = true  # Keep if you're using Data API
  
  # Ignore settings that are managed by Database Insights Advanced mode
  # Database Insights Advanced requires Performance Insights, so we can't change it
  lifecycle {
    ignore_changes = [
      performance_insights_enabled,
      performance_insights_kms_key_id,
      performance_insights_retention_period,
      database_insights_mode
    ]
  }
  
  tags = merge(local.common_tags, {
    Name = "${local.app_name}-cluster-${local.env}"
  })
}

# Note: You have 2 existing instances. Import them instead of creating:
# terraform import aws_rds_cluster_instance.aurora_instance_1 starscape-1-instance-1
# terraform import aws_rds_cluster_instance.aurora_instance_2 starscape-1-instance-1-us-east-2c

resource "aws_rds_cluster_instance" "aurora_instance_1" {
  identifier         = "starscape-1-instance-1"
  cluster_identifier = aws_rds_cluster.aurora.id
  instance_class     = "db.serverless"
  engine             = aws_rds_cluster.aurora.engine
  engine_version     = "17.4"
  
  performance_insights_enabled = true
  monitoring_interval          = 60  # Keep Enhanced Monitoring enabled
  promotion_tier                = 1   # Keep existing failover priority
  
  tags = merge(local.common_tags, {
    Name = "starscape-1-instance-1"
  })
}

resource "aws_rds_cluster_instance" "aurora_instance_2" {
  identifier         = "starscape-1-instance-1-us-east-2c"
  cluster_identifier = aws_rds_cluster.aurora.id
  instance_class     = "db.serverless"
  engine             = aws_rds_cluster.aurora.engine
  engine_version     = "17.4"
  
  performance_insights_enabled = true
  monitoring_interval          = 60  # Keep Enhanced Monitoring enabled
  promotion_tier                = 1   # Keep existing failover priority
  
  tags = merge(local.common_tags, {
    Name = "starscape-1-instance-1-us-east-2c"
  })
}

# EC2 Instance Profile (allows EC2 to assume IAM role)
resource "aws_iam_instance_profile" "app" {
  name = "${local.app_name}-app-profile-${local.env}"
  role = aws_iam_role.app.name
  
  tags = local.common_tags
}

# Security Group for EC2
resource "aws_security_group" "ec2" {
  name_prefix = "${local.app_name}-ec2-${local.env}-"
  vpc_id      = var.vpc_id
  
  ingress {
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = var.allowed_cidr_blocks
    description = "Spring Boot application"
  }
  
  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = var.allowed_cidr_blocks
    description = "SSH access"
  }
  
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
    description = "Allow all outbound"
  }
  
  tags = local.common_tags
}

# EC2 Instance
resource "aws_instance" "app" {
  ami           = var.ec2_ami_id  # Amazon Linux 2023 ARM64
  instance_type = var.ec2_instance_type
  
  vpc_security_group_ids = [aws_security_group.ec2.id]
  subnet_id              = var.ec2_subnet_id
  
  iam_instance_profile = aws_iam_instance_profile.app.name
  
  # SSH key pair (optional - set in terraform.tfvars if you want SSH access)
  # Note: SSM Session Manager works without SSH keys
  key_name = var.ec2_key_pair_name
  
  user_data = <<-EOF
              #!/bin/bash
              set -e
              
              # Update system
              sudo dnf update -y
              
              # Install Java 21
              sudo dnf install -y java-21-amazon-corretto-headless
              
              # Install Maven (for local builds if needed)
              sudo dnf install -y maven
              
              # Install Git
              sudo dnf install -y git
              
              # Install AWS CLI v2 (if not already installed)
              if ! command -v aws &> /dev/null; then
                curl "https://awscli.amazonaws.com/awscli-exe-linux-aarch64.zip" -o "awscliv2.zip"
                unzip awscliv2.zip
                sudo ./aws/install
                rm -rf aws awscliv2.zip
              fi
              
              # Install SSM Agent (usually pre-installed on Amazon Linux 2023)
              sudo systemctl enable amazon-ssm-agent
              sudo systemctl start amazon-ssm-agent || true
              
              # Set JAVA_HOME
              echo 'export JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto' >> /etc/profile
              
              # Create app directory
              sudo mkdir -p /opt/rapidupload
              sudo chown ec2-user:ec2-user /opt/rapidupload
              
              # Note: Application deployment will be done via:
              # 1. CI/CD pipeline (GitHub Actions using SSM)
              # 2. Manual deployment via SSM Session Manager
              # 3. AWS CodeDeploy / Elastic Beanstalk
              EOF
  
  tags = merge(local.common_tags, {
    Name = "${local.app_name}-app-${local.env}"
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
  value = aws_rds_cluster.aurora.endpoint
}

output "rds_reader_endpoint" {
  value = aws_rds_cluster.aurora.reader_endpoint
}

output "rds_database_name" {
  value = aws_rds_cluster.aurora.database_name
}

output "ec2_instance_id" {
  value = aws_instance.app.id
}

output "ec2_public_ip" {
  value = aws_instance.app.public_ip
}

output "ec2_public_dns" {
  value = aws_instance.app.public_dns
}

