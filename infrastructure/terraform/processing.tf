# Phase 3: Async Processing Pipeline Infrastructure
# SQS, EventBridge, and S3 Event Notifications for photo processing

# SQS Dead Letter Queue for failed messages
resource "aws_sqs_queue" "photo_processing_dlq" {
  name                      = "${local.app_name}-photo-processing-dlq-${local.env}"
  message_retention_seconds = 1209600  # 14 days
  
  tags = merge(local.common_tags, {
    Name = "${local.app_name}-photo-processing-dlq-${local.env}"
  })
}

# SQS Queue for photo processing
resource "aws_sqs_queue" "photo_processing" {
  name                       = "${local.app_name}-photo-processing-${local.env}"
  visibility_timeout_seconds = 300  # 5 minutes (enough time for processing)
  message_retention_seconds  = 345600  # 4 days
  receive_wait_time_seconds  = 20  # Long polling
  
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.photo_processing_dlq.arn
    maxReceiveCount     = 3
  })
  
  tags = merge(local.common_tags, {
    Name = "${local.app_name}-photo-processing-${local.env}"
  })
}

# SQS Queue Policy (allow EventBridge to send messages)
resource "aws_sqs_queue_policy" "photo_processing" {
  queue_url = aws_sqs_queue.photo_processing.id
  
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "AllowEventBridgeToSendMessages"
        Effect = "Allow"
        Principal = {
          Service = "events.amazonaws.com"
        }
        Action   = "sqs:SendMessage"
        Resource = aws_sqs_queue.photo_processing.arn
        Condition = {
          ArnEquals = {
            "aws:SourceArn" = aws_cloudwatch_event_rule.s3_object_created.arn
          }
        }
      }
    ]
  })
}

# EventBridge Rule for S3 ObjectCreated events
resource "aws_cloudwatch_event_rule" "s3_object_created" {
  name        = "${local.app_name}-s3-object-created-${local.env}"
  description = "Capture S3 ObjectCreated events for photo processing"
  
  event_pattern = jsonencode({
    source      = ["aws.s3"]
    detail-type = ["Object Created"]
    detail = {
      bucket = {
        name = [aws_s3_bucket.media.id]
      }
      object = {
        key = [{
          prefix = "${local.env}/"
        }]
      }
    }
  })
  
  tags = local.common_tags
}

# EventBridge Target: Send events to SQS
resource "aws_cloudwatch_event_target" "sqs" {
  rule      = aws_cloudwatch_event_rule.s3_object_created.name
  target_id = "SendToSQS"
  arn       = aws_sqs_queue.photo_processing.arn
}

# Enable EventBridge notifications on S3 bucket
# Note: This enables EventBridge integration but doesn't create a notification
# The EventBridge rule will automatically receive events when EventBridge is enabled
resource "aws_s3_bucket_notification" "media_eventbridge" {
  bucket = aws_s3_bucket.media.id
  
  eventbridge = true
  
  depends_on = [
    aws_cloudwatch_event_rule.s3_object_created
  ]
}

# IAM Policy for app to consume SQS messages
resource "aws_iam_role_policy" "app_sqs" {
  name = "${local.app_name}-sqs-policy-${local.env}"
  role = aws_iam_role.app.id
  
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "sqs:ReceiveMessage",
          "sqs:DeleteMessage",
          "sqs:GetQueueAttributes",
          "sqs:ChangeMessageVisibility",
          "sqs:GetQueueUrl"
        ]
        Resource = [
          aws_sqs_queue.photo_processing.arn,
          aws_sqs_queue.photo_processing_dlq.arn
        ]
      }
    ]
  })
}

# Outputs
output "sqs_queue_url" {
  description = "SQS queue URL for photo processing"
  value       = aws_sqs_queue.photo_processing.url
}

output "sqs_queue_arn" {
  description = "SQS queue ARN for photo processing"
  value       = aws_sqs_queue.photo_processing.arn
}

output "sqs_dlq_url" {
  description = "SQS Dead Letter Queue URL"
  value       = aws_sqs_queue.photo_processing_dlq.url
}

output "sqs_dlq_arn" {
  description = "SQS Dead Letter Queue ARN"
  value       = aws_sqs_queue.photo_processing_dlq.arn
}

output "eventbridge_rule_arn" {
  description = "EventBridge rule ARN for S3 ObjectCreated events"
  value       = aws_cloudwatch_event_rule.s3_object_created.arn
}

