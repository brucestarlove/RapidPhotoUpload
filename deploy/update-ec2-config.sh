#!/bin/bash
# Script to update application-prod.yml on EC2 via SSM

INSTANCE_ID="${EC2_INSTANCE_ID:-i-040798390b54b4294}"
PROFILE="${AWS_PROFILE:-gauntlet}"

echo "Updating application-prod.yml on EC2 instance: $INSTANCE_ID"

aws ssm send-command \
  --instance-ids "$INSTANCE_ID" \
  --document-name "AWS-RunShellScript" \
  --parameters "commands=[
    \"# Backup existing config\",
    \"cp /opt/rapidupload/application-prod.yml /opt/rapidupload/application-prod.yml.backup.\$(date +%Y%m%d_%H%M%S) || true\",
    \"\",
    \"# Create new config file\",
    \"cat > /opt/rapidupload/application-prod.yml << 'EOF'\",
    \"spring:\",
    \"  application:\",
    \"    name: starscaperapidphotoupload\",
    \"  \",
    \"  datasource:\",
    \"    url: jdbc:postgresql://starscape-1.cluster-c1uuigcm4bd1.us-east-2.rds.amazonaws.com:5432/StarscapeRapidPhotoUpload\",
    \"    username: \\\${DB_USER:postgres}\",
    \"    password: \\\${DB_PASSWORD}\",
    \"    hikari:\",
    \"      maximum-pool-size: 20\",
    \"      minimum-idle: 5\",
    \"      connection-timeout: 30000\",
    \"      idle-timeout: 600000\",
    \"      max-lifetime: 600000\",
    \"  \",
    \"  jpa:\",
    \"    hibernate:\",
    \"      ddl-auto: validate\",
    \"    properties:\",
    \"      hibernate:\",
    \"        dialect: org.hibernate.dialect.PostgreSQLDialect\",
    \"        format_sql: true\",
    \"        jdbc:\",
    \"          batch_size: 20\",
    \"    show-sql: false\",
    \"  \",
    \"  flyway:\",
    \"    enabled: true\",
    \"    locations: classpath:db/migration\",
    \"    baseline-on-migrate: true\",
    \"  \",
    \"  cloud:\",
    \"    aws:\",
    \"      region:\",
    \"        static: \\\${AWS_REGION:us-east-2}\",
    \"      credentials:\",
    \"        profile:\",
    \"          name: \\\${AWS_PROFILE:}\",
    \"      sqs:\",
    \"        enabled: true\",
    \"        listener:\",
    \"          max-concurrent-messages: 10\",
    \"          max-messages-per-poll: 10\",
    \"          poll-timeout: 20\",
    \"\",
    \"management:\",
    \"  endpoints:\",
    \"    web:\",
    \"      exposure:\",
    \"        include: health,info,metrics\",
    \"  endpoint:\",
    \"    health:\",
    \"      show-details: when-authorized\",
    \"\",
    \"aws:\",
    \"  region: \\\${AWS_REGION:us-east-2}\",
    \"  profile: \\\${AWS_PROFILE:}\",
    \"  s3:\",
    \"    bucket: \\\${S3_BUCKET:starscape-rapidphotoupload}\",
    \"    presign-duration-minutes: 15\",
    \"  sqs:\",
    \"    queue-url: \\\${SQS_QUEUE_URL:}\",
    \"    dlq-url: \\\${SQS_DLQ_URL:}\",
    \"\",
    \"app:\",
    \"  security:\",
    \"    jwt:\",
    \"      secret: \\\${JWT_SECRET}\",
    \"      expiration-ms: 86400000\",
    \"      issuer: starscape-rapidphotoupload-api\",
    \"  processing:\",
    \"    thumbnail-sizes:\",
    \"      - 256\",
    \"      - 1024\",
    \"    supported-formats:\",
    \"      - image/jpeg\",
    \"      - image/png\",
    \"      - image/gif\",
    \"      - image/webp\",
    \"  websocket:\",
    \"    allowed-origins: \\\${WEBSOCKET_ALLOWED_ORIGINS:*}\",
    \"EOF\",
    \"\",
    \"# Set permissions\",
    \"sudo chown ec2-user:ec2-user /opt/rapidupload/application-prod.yml\",
    \"sudo chmod 600 /opt/rapidupload/application-prod.yml\",
    \"\",
    \"# Verify file\",
    \"cat /opt/rapidupload/application-prod.yml\"
  ]" \
  --profile "$PROFILE" \
  --output text \
  --query "Command.CommandId" | tee /tmp/ssm-command-id.txt

COMMAND_ID=$(cat /tmp/ssm-command-id.txt)
echo "Command ID: $COMMAND_ID"
echo "Waiting for command to complete..."

sleep 5

# Get command output
aws ssm list-command-invocations \
  --command-id "$COMMAND_ID" \
  --instance-id "$INSTANCE_ID" \
  --details \
  --profile "$PROFILE" \
  --query "CommandInvocations[0].CommandPlugins[0].Output" \
  --output text

echo ""
echo "✅ Config updated! Restart the service with:"
echo "   aws ssm send-command --instance-ids $INSTANCE_ID --document-name AWS-RunShellScript --parameters 'commands=[\"sudo systemctl restart rapidupload\"]' --profile $PROFILE"

