#!/bin/bash

# Phase 2 Manual Testing Script
# Usage: ./test-phase2.sh [base-url]
#
# Prerequisites:
#   1. Application running (mvn spring-boot:run)
#   2. Test images created (./create-test-images.sh) - optional but recommended
#   3. jq installed for JSON parsing (brew install jq)

BASE_URL=${1:-http://localhost:8080}
echo "Testing Phase 2 API at: $BASE_URL"

# Check for jq
if ! command -v jq &> /dev/null; then
    echo -e "${RED}Error: jq is required. Install with: brew install jq${NC}"
    exit 1
fi

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Step 1: Register a user
echo -e "\n${YELLOW}Step 1: Registering user...${NC}"
REGISTER_RESPONSE=$(curl -s -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test222@example.com",
    "password": "password123"
  }')

echo "$REGISTER_RESPONSE" | jq '.'

TOKEN=$(echo "$REGISTER_RESPONSE" | jq -r '.token')
USER_ID=$(echo "$REGISTER_RESPONSE" | jq -r '.userId')

if [ "$TOKEN" == "null" ] || [ -z "$TOKEN" ]; then
  echo -e "${RED}Failed to get token. Trying login...${NC}"
  LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/api/auth/login" \
    -H "Content-Type: application/json" \
    -d '{
      "email": "test222@example.com",
      "password": "password123"
    }')
  TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.token')
  USER_ID=$(echo "$LOGIN_RESPONSE" | jq -r '.userId')
fi

if [ "$TOKEN" == "null" ] || [ -z "$TOKEN" ]; then
  echo -e "${RED}Failed to authenticate. Exiting.${NC}"
  exit 1
fi

echo -e "${GREEN}✓ Authenticated. Token: ${TOKEN:0:20}...${NC}"
echo -e "${GREEN}✓ User ID: $USER_ID${NC}"

# Step 2: Create upload job (single-part)
echo -e "\n${YELLOW}Step 2: Creating upload job (single-part)...${NC}"
JOB_RESPONSE=$(curl -s -X POST "$BASE_URL/commands/upload-jobs" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "files": [
      {
        "filename": "photo1.jpg",
        "mimeType": "image/jpeg",
        "bytes": 1048576
      },
      {
        "filename": "photo2.png",
        "mimeType": "image/png",
        "bytes": 2097152
      }
    ],
    "strategy": "S3_PRESIGNED"
  }')

echo "$JOB_RESPONSE" | jq '.'

JOB_ID=$(echo "$JOB_RESPONSE" | jq -r '.jobId')
PHOTO_ID=$(echo "$JOB_RESPONSE" | jq -r '.items[0].photoId')
PRESIGNED_URL=$(echo "$JOB_RESPONSE" | jq -r '.items[0].presignedUrl')
FILE_SIZE=$(echo "$JOB_RESPONSE" | jq -r '.items[0] | if .presignedUrl then 1048576 else empty end')  # 1MB for photo1.jpg

if [ "$JOB_ID" != "null" ] && [ -n "$JOB_ID" ]; then
  echo -e "${GREEN}✓ Job created: $JOB_ID${NC}"
  echo -e "${GREEN}✓ Photo ID: $PHOTO_ID${NC}"
  echo -e "${GREEN}✓ Presigned URL: ${PRESIGNED_URL:0:50}...${NC}"
else
  echo -e "${RED}✗ Failed to create job${NC}"
  exit 1
fi

# Step 3: Create upload job (multipart)
echo -e "\n${YELLOW}Step 3: Creating upload job (multipart for large file)...${NC}"
MULTIPART_RESPONSE=$(curl -s -X POST "$BASE_URL/commands/upload-jobs" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "files": [
      {
        "filename": "large-photo.jpg",
        "mimeType": "image/jpeg",
        "bytes": 10485760
      }
    ],
    "strategy": "S3_MULTIPART"
  }')

echo "$MULTIPART_RESPONSE" | jq '.'

MULTIPART_JOB_ID=$(echo "$MULTIPART_RESPONSE" | jq -r '.jobId')
MULTIPART_PHOTO_ID=$(echo "$MULTIPART_RESPONSE" | jq -r '.items[0].photoId')
UPLOAD_ID=$(echo "$MULTIPART_RESPONSE" | jq -r '.items[0].multipart.uploadId')

if [ "$MULTIPART_JOB_ID" != "null" ] && [ -n "$MULTIPART_JOB_ID" ]; then
  echo -e "${GREEN}✓ Multipart job created: $MULTIPART_JOB_ID${NC}"
  echo -e "${GREEN}✓ Upload ID: $UPLOAD_ID${NC}"
  PART_COUNT=$(echo "$MULTIPART_RESPONSE" | jq '.items[0].multipart.parts | length')
  echo -e "${GREEN}✓ Parts: $PART_COUNT${NC}"
else
  echo -e "${RED}✗ Failed to create multipart job${NC}"
fi

# Step 4: Update progress
echo -e "\n${YELLOW}Step 4: Updating progress...${NC}"
PROGRESS_RESPONSE=$(curl -s -w "\nHTTP Status: %{http_code}\n" -X POST "$BASE_URL/commands/upload/progress" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"photoId\": \"$PHOTO_ID\",
    \"bytesSent\": 524288,
    \"bytesTotal\": 1048576,
    \"percent\": 50
  }")

echo "$PROGRESS_RESPONSE"

if echo "$PROGRESS_RESPONSE" | grep -q "202"; then
  echo -e "${GREEN}✓ Progress updated successfully${NC}"
else
  echo -e "${RED}✗ Failed to update progress${NC}"
fi

# Step 5: Test validation errors
echo -e "\n${YELLOW}Step 5: Testing validation (should fail)...${NC}"
VALIDATION_RESPONSE=$(curl -s -w "\nHTTP Status: %{http_code}\n" -X POST "$BASE_URL/commands/upload-jobs" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "files": [
      {
        "filename": "invalid.exe",
        "mimeType": "application/x-executable",
        "bytes": 1024
      }
    ],
    "strategy": "S3_PRESIGNED"
  }')

echo "$VALIDATION_RESPONSE"

if echo "$VALIDATION_RESPONSE" | grep -q "400"; then
  echo -e "${GREEN}✓ Validation correctly rejected invalid file${NC}"
else
  echo -e "${RED}✗ Validation should have rejected invalid file${NC}"
fi

# Step 6: Test authentication
echo -e "\n${YELLOW}Step 6: Testing authentication (should fail)...${NC}"
AUTH_RESPONSE=$(curl -s -w "\nHTTP Status: %{http_code}\n" -X POST "$BASE_URL/commands/upload-jobs" \
  -H "Content-Type: application/json" \
  -d '{
    "files": [
      {
        "filename": "photo.jpg",
        "mimeType": "image/jpeg",
        "bytes": 1024
      }
    ],
    "strategy": "S3_PRESIGNED"
  }')

echo "$AUTH_RESPONSE"

if echo "$AUTH_RESPONSE" | grep -q "401\|403"; then
  echo -e "${GREEN}✓ Authentication correctly required (401/403)${NC}"
else
  echo -e "${RED}✗ Authentication should have been required (got different status)${NC}"
fi

# Step 7: Test actual S3 upload (if test images exist)
echo -e "\n${YELLOW}Step 7: Testing actual S3 upload...${NC}"
if [ -f "./test-images/photo1.jpg" ]; then
    echo "Uploading test-images/photo1.jpg to S3..."
    
    # Get actual file size
    ACTUAL_SIZE=$(stat -f%z "./test-images/photo1.jpg" 2>/dev/null || stat -c%s "./test-images/photo1.jpg" 2>/dev/null)
    
    echo "File size: $ACTUAL_SIZE bytes"
    
    # Upload with Content-Length header (contentLength is now optional in presigned URL)
    UPLOAD_RESPONSE=$(curl -s -w "\nHTTP Status: %{http_code}\n" -X PUT "$PRESIGNED_URL" \
      -H "Content-Type: image/jpeg" \
      -H "Content-Length: $ACTUAL_SIZE" \
      --data-binary @./test-images/photo1.jpg)
    
    HTTP_STATUS=$(echo "$UPLOAD_RESPONSE" | tail -n 1 | grep -o '[0-9]*$')
    
    if [ "$HTTP_STATUS" == "200" ]; then
        echo -e "${GREEN}✓ File uploaded successfully to S3!${NC}"
    else
        echo -e "${RED}✗ Upload failed with status: $HTTP_STATUS${NC}"
        echo "$UPLOAD_RESPONSE" | head -n -1  # Show error without HTTP status line
        echo -e "\n${YELLOW}Note: If SignatureDoesNotMatch, ensure:${NC}"
        echo -e "  1. AWS credentials match the profile used to generate URL"
        echo -e "  2. Content-Type header matches exactly (image/jpeg)"
        echo -e "  3. Check AWS profile configuration (aws.profile=gauntlet)"
    fi
else
    echo -e "${YELLOW}⚠ Test image not found. Create test images first:${NC}"
    echo -e "  ./create-test-images.sh"
    echo -e "\nOr upload your own image:"
    echo -e "${YELLOW}curl -X PUT \"$PRESIGNED_URL\" -H \"Content-Type: image/jpeg\" -H \"Content-Length: <file-size>\" --data-binary @/path/to/photo.jpg${NC}"
fi

echo -e "\n${GREEN}=== Testing Complete ===${NC}"
echo -e "Job ID: $JOB_ID"
echo -e "Photo ID: $PHOTO_ID"
echo -e "\nTo test actual S3 upload manually, use:"
echo -e "${YELLOW}curl -X PUT \"$PRESIGNED_URL\" \\${NC}"
echo -e "${YELLOW}  -H \"Content-Type: image/jpeg\" \\${NC}"
echo -e "${YELLOW}  -H \"Content-Length: <actual-file-size>\" \\${NC}"
echo -e "${YELLOW}  --data-binary @/path/to/photo.jpg${NC}"

