## Phase 3 Implementation Complete

### 1. Dependencies added (`pom.xml`)
- Spring Cloud AWS SQS (`spring-cloud-aws-starter-sqs` v3.1.0)
- Metadata Extractor (`metadata-extractor` v2.19.0) for EXIF extraction
- Thumbnailator (`thumbnailator` v0.4.20) for thumbnail generation
- Apache Commons Codec (`commons-codec`) for SHA-256 checksums
- AWS SDK SQS client

### 2. Configuration updates
- Updated `application.yml.example` with:
  - SQS queue URL and DLQ URL configuration
  - Spring Cloud AWS SQS listener settings
  - Processing configuration (thumbnail sizes, supported formats)

### 3. AWS configuration
- Added `SqsClient` bean to `AwsConfig`

### 4. S3 event models
- `S3EventMessage` — EventBridge message wrapper
- `S3EventDetail` — Event detail section
- `S3Bucket` — Bucket information
- `S3Object` — Object information (key, size, etag, sequencer)

### 5. Domain events
- `PhotoProcessingCompleted` — Published when processing succeeds
- `PhotoFailed` — Published when processing fails

### 6. Core services
- `PhotoProcessingService` — Handles:
  - Downloading images from S3
  - EXIF metadata extraction
  - Thumbnail generation (256px, 1024px)
  - SHA-256 checksum computation
  - Photo status updates
  - Event publishing via outbox pattern
  - Idempotency (skips if already completed)

- `S3EventListener` — SQS message listener that:
  - Receives S3 ObjectCreated events from EventBridge
  - Parses event messages
  - Skips thumbnail files to avoid loops
  - Delegates processing to `PhotoProcessingService`

- `JobProgressAggregator` — Scheduled service that:
  - Processes outbox events every 5 seconds
  - Updates `UploadJob` progress based on photo completion/failure
  - Updates job status (COMPLETED, COMPLETED_WITH_ERRORS, FAILED)

- `DlqReprocessor` — Manual DLQ reprocessing tool for recovery

### 7. Scheduling configuration
- `SchedulingConfig` — Enables `@Scheduled` annotations

## Next steps

1. Terraform infrastructure: Create the Terraform file (`infrastructure/terraform/processing.tf`) to set up:
   - SQS queue and DLQ
   - EventBridge rule for S3 ObjectCreated events
   - S3 EventBridge notifications
   - IAM policies

2. Testing: Test the end-to-end flow:
   - Upload a photo
   - Verify S3 event triggers processing
   - Check EXIF extraction and thumbnail generation
   - Verify job progress updates

3. Monitoring: Monitor SQS metrics and DLQ depth



## Created Files

### 1. **`infrastructure/terraform/processing.tf`**
   - SQS queue with Dead Letter Queue (DLQ)
   - EventBridge rule for S3 ObjectCreated events
   - S3 EventBridge notification integration
   - IAM policy for app to consume SQS messages
   - Outputs for queue URLs and ARNs

### 2. **Updated `infrastructure/terraform/README.md`**
   - Added Phase 3 infrastructure to the "What's Configured" section
   - Added SQS queue URL outputs
   - Added instructions for using queue URLs in `application.yml`

### 3. **`docs/PHASE3-DEPLOYMENT.md`**
   - Deployment guide
   - Testing procedures
   - Troubleshooting tips
   - Monitoring commands

## Infrastructure Details

The Terraform file creates:

1. **SQS Queue** (`photo-processing-{env}`)
   - Visibility timeout: 5 minutes
   - Message retention: 4 days
   - Long polling: 20 seconds
   - Redrive policy: 3 retries → DLQ

2. **Dead Letter Queue** (`photo-processing-dlq-{env}`)
   - Message retention: 14 days
   - For manual inspection and reprocessing

3. **EventBridge Rule**
   - Filters S3 ObjectCreated events
   - Only matches objects with prefix `{env}/`
   - Sends events to SQS queue

4. **S3 EventBridge Integration**
   - Enables EventBridge notifications on the S3 bucket
   - Automatically sends events to EventBridge

5. **IAM Permissions**
   - Adds SQS permissions to the app role
   - Allows receiving, deleting, and managing SQS messages

## Next Steps

1. Deploy infrastructure:
   ```bash
   cd infrastructure/terraform
   terraform init
   terraform plan
   terraform apply
   ```

2. Get queue URLs:
   ```bash
   terraform output sqs_queue_url
   terraform output sqs_dlq_url
   ```

3. Update `application.yml` with the queue URLs

4. Test the end-to-end flow using the deployment guide

The infrastructure is ready to deploy. The application will process photos asynchronously when S3 events are received via SQS.



# Phase 3 Deployment Guide

This guide covers deploying the Phase 3 async processing pipeline infrastructure and testing the end-to-end flow.

## Prerequisites

- Terraform installed (`brew install terraform`)
- AWS credentials configured (profile: `gauntlet`)
- Application code deployed with Phase 3 dependencies

## Step 1: Deploy Terraform Infrastructure

### Navigate to Terraform Directory

```bash
cd infrastructure/terraform
```

### Review and Apply Changes

```bash
# Initialize Terraform (if not already done)
terraform init

# Review what will be created
terraform plan

# Apply the infrastructure
terraform apply
```

### Capture Outputs

After applying, capture the SQS queue URLs:

```bash
# Get SQS queue URL
terraform output sqs_queue_url

# Get DLQ URL
terraform output sqs_dlq_url
```

## Step 2: Update Application Configuration

Update your `application.yml` with the SQS queue URLs:

```yaml
aws:
  sqs:
    queue-url: <value from terraform output>
    dlq-url: <value from terraform output>
```

Or set environment variables:

```bash
export SQS_QUEUE_URL=$(terraform output -raw sqs_queue_url)
export SQS_DLQ_URL=$(terraform output -raw sqs_dlq_url)
```

## Step 3: Verify Infrastructure

### Check SQS Queue

```bash
aws sqs get-queue-attributes \
  --queue-url <sqs_queue_url> \
  --attribute-names All \
  --profile gauntlet
```

### Check EventBridge Rule

```bash
aws events describe-rule \
  --name starscape-rapidphotoupload-s3-object-created-dev \
  --profile gauntlet
```

### Check S3 EventBridge Integration

```bash
aws s3api get-bucket-notification-configuration \
  --bucket starscape-rapidphotoupload \
  --profile gauntlet
```

Should show `EventBridgeConfiguration` enabled.

## Step 4: Test End-to-End Flow

### 1. Create Upload Job

```bash
# Get auth token first
TOKEN=$(curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password"}' \
  | jq -r '.token')

# Create upload job
curl -X POST http://localhost:8080/commands/upload-jobs \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "files": [
      {"filename": "test.jpg", "mimeType": "image/jpeg", "bytes": 1024000}
    ],
    "strategy": "S3_PRESIGNED"
  }'
```

### 2. Upload Photo to S3

Use the presigned URL from the job creation response to upload a photo directly to S3.

curl -X PUT "<presigned_url_from_response>" \
  -H "Content-Type: image/jpeg" \
  --data-binary @test-images/photo1.jpg

### 3. Verify S3 Event Triggered

Check CloudWatch Logs for the application:

```bash
aws logs tail /aws/ec2/rapidupload-app --follow --profile gauntlet
```

You should see:
- `Received SQS message: ...`
- `Processing photo: s3Key=...`
- `Photo processed successfully: ...`

### 4. Verify Processing Results

Check the database:

```sql
-- Check photo status
SELECT photo_id, status, width, height, checksum, exif_json 
FROM photos 
WHERE job_id = '<job_id>';

-- Check job progress
SELECT job_id, status, completed_count, failed_count, total_count 
FROM upload_jobs 
WHERE job_id = '<job_id>';
```

### 5. Verify Thumbnails Generated

```bash
# List thumbnails in S3
aws s3 ls s3://starscape-rapidphotoupload/dev/<user_id>/<job_id>/thumbnails/ \
  --profile gauntlet
```

Should see files like:
- `photoId_256.jpg`
- `photoId_1024.jpg`

## Step 5: Monitor Processing

### Check SQS Metrics

```bash
# Messages in flight
aws cloudwatch get-metric-statistics \
  --namespace AWS/SQS \
  --metric-name ApproximateNumberOfMessagesInFlight \
  --dimensions Name=QueueName,Value=starscape-rapidphotoupload-photo-processing-dev \
  --start-time $(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
  --period 300 \
  --statistics Average \
  --profile gauntlet
```

### Check DLQ Depth

```bash
aws sqs get-queue-attributes \
  --queue-url <dlq_url> \
  --attribute-names ApproximateNumberOfMessages \
  --profile gauntlet
```

### Check Application Logs

Monitor application logs for processing activity:

```bash
# Via SSM Session Manager (if on EC2)
aws ssm start-session \
  --target <instance-id> \
  --profile gauntlet

# Then tail logs
tail -f /opt/rapidupload/logs/application.log
```

## Troubleshooting

### SQS Messages Not Being Received

1. **Check EventBridge Rule**: Verify the rule is enabled and matches S3 events
2. **Check SQS Policy**: Ensure EventBridge has permission to send messages
3. **Check S3 EventBridge**: Verify EventBridge is enabled on the bucket
4. **Check Queue URL**: Ensure `application.yml` has the correct queue URL

### Processing Failing

1. **Check DLQ**: Messages in DLQ indicate processing failures
2. **Check Logs**: Look for error messages in application logs
3. **Check IAM Permissions**: Ensure app role has S3 GetObject and SQS permissions
4. **Check Photo Status**: Query database to see photo status and error messages

### Thumbnails Not Generated

1. **Check S3 Permissions**: Ensure app role can PutObject to S3
2. **Check Image Format**: Verify image format is supported (JPEG, PNG, GIF, WebP)
3. **Check Logs**: Look for thumbnail generation errors in logs

## Cleanup

To remove Phase 3 infrastructure:

```bash
cd infrastructure/terraform
terraform destroy -target=aws_sqs_queue.photo_processing
terraform destroy -target=aws_sqs_queue.photo_processing_dlq
terraform destroy -target=aws_cloudwatch_event_rule.s3_object_created
terraform destroy -target=aws_cloudwatch_event_target.sqs
terraform destroy -target=aws_s3_bucket_notification.media_eventbridge
terraform destroy -target=aws_iam_role_policy.app_sqs
```

Or destroy all:

```bash
terraform destroy
```

## Next Steps

After Phase 3 is working:

1. **Phase 4**: Implement real-time progress APIs and query endpoints
2. **Monitoring**: Set up CloudWatch alarms for DLQ depth and processing failures
3. **Scaling**: Monitor SQS queue depth and consider auto-scaling if needed
4. **Optimization**: Review thumbnail sizes and processing performance




---

## What is an "Upload Job"?

An Upload Job is a batch container for multiple photo uploads. It groups related photos together and tracks their progress.

### When you create an upload job:

1. You send a request with a list of files you want to upload:
   ```json
   {
     "files": [
       {"filename": "vacation1.jpg", "mimeType": "image/jpeg", "bytes": 2048000},
       {"filename": "vacation2.jpg", "mimeType": "image/jpeg", "bytes": 1536000}
     ],
     "strategy": "S3_PRESIGNED"
   }
   ```

2. The system creates:
   - An `UploadJob` record in the database with:
     - Unique `jobId` (e.g., `job_abc123...`)
     - Your `userId`
     - `totalCount` = number of files (e.g., 2)
     - `status` = `QUEUED`
     - Progress counters (`completedCount`, `failedCount`, etc.)
   
   - Multiple `Photo` records (one per file) with:
     - Unique `photoId` for each photo
     - Filename, MIME type, size
     - `status` = `QUEUED`
     - S3 key path: `dev/userId/jobId/photoId.jpg`

3. The system returns presigned URLs:
   - One presigned URL per photo
   - These URLs allow direct upload to S3 (bypassing your server)
   - URLs are valid for 15 minutes (configurable)

### Why use jobs?

- Batch processing: Upload multiple photos in one request
- Progress tracking: Monitor how many photos completed/failed
- Organization: All photos from one upload session are grouped together
- Asynchronous processing: After upload, photos are processed automatically (EXIF, thumbnails, etc.)

### The complete flow:

```
1. Client: "I want to upload 5 photos"
   ↓
2. Server: Creates job + 5 photo records, returns 5 presigned URLs
   ↓
3. Client: Uploads each photo directly to S3 using presigned URLs
   ↓
4. S3: Sends ObjectCreated event → EventBridge → SQS
   ↓
5. Your App: Processes each photo (EXIF, thumbnails, checksums)
   ↓
6. Job Status: Updates from QUEUED → IN_PROGRESS → COMPLETED
```

In short: creating an upload job prepares the system to receive and process a batch of photos, and gives you the URLs needed to upload them directly to S3.
