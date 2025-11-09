variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID"
  type        = string
}

variable "database_subnet_ids" {
  description = "Database subnet IDs"
  type        = list(string)
}

variable "allowed_cidr_blocks" {
  description = "CIDR blocks allowed to access RDS"
  type        = list(string)
}

variable "allowed_origins" {
  description = "CORS allowed origins for S3"
  type        = list(string)
  default     = ["http://localhost:3000", "http://localhost:8080"]
}

variable "aurora_min_capacity" {
  description = "Aurora Serverless v2 minimum ACU capacity"
  type        = number
  default     = 8
}

variable "aurora_max_capacity" {
  description = "Aurora Serverless v2 maximum ACU capacity"
  type        = number
  default     = 64
}

variable "db_master_username" {
  description = "RDS master username"
  type        = string
  sensitive   = true
}

variable "db_master_password" {
  description = "RDS master password"
  type        = string
  sensitive   = true
}

variable "ec2_ami_id" {
  description = "AMI ID for EC2 instance (e.g., Amazon Linux 2023)"
  type        = string
}

variable "ec2_instance_type" {
  description = "EC2 instance type (use t4g for ARM64, t3 for x86_64)"
  type        = string
  default     = "t4g.micro"  # ARM64 instance type for ARM64 AMI
}

variable "ec2_subnet_id" {
  description = "Subnet ID for EC2 instance"
  type        = string
}

variable "ec2_key_pair_name" {
  description = "EC2 Key Pair name for SSH access (optional)"
  type        = string
  default     = null
}

