# Editing Files on EC2 via SSM

## Method 1: Heredoc (Best for multi-line files)

```bash
aws ssm send-command \
  --instance-ids i-040798390b54b4294 \
  --document-name "AWS-RunShellScript" \
  --parameters 'commands=[
    "cat > /opt/rapidupload/application-prod.yml << '\''EOF'\''
spring:
  datasource:
    url: jdbc:postgresql://starscape-1.cluster-c1uuigcm4bd1.us-east-2.rds.amazonaws.com:5432/StarscapeRapidPhotoUpload
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD}
aws:
  region: ${AWS_REGION:us-east-2}
  s3:
    bucket: ${S3_BUCKET:starscape-rapidphotoupload}
app:
  security:
    jwt:
      secret: ${JWT_SECRET}
      expiration-ms: 86400000
EOF
",
    "sudo chown ec2-user:ec2-user /opt/rapidupload/application-prod.yml",
    "sudo chmod 600 /opt/rapidupload/application-prod.yml"
  ]' \
  --profile gauntlet
```

## Method 2: Using sed to replace specific lines

```bash
# Replace a specific value
aws ssm send-command \
  --instance-ids i-040798390b54b4294 \
  --document-name "AWS-RunShellScript" \
  --parameters 'commands=[
    "sed -i '\''s/old-value/new-value/g'\'' /opt/rapidupload/application-prod.yml"
  ]' \
  --profile gauntlet
```

## Method 3: Using echo with append (for single lines)

```bash
# Overwrite entire file
aws ssm send-command \
  --instance-ids i-040798390b54b4294 \
  --document-name "AWS-RunShellScript" \
  --parameters 'commands=[
    "echo '\''spring:\n  datasource:\n    url: jdbc:postgresql://...'\'' > /opt/rapidupload/application-prod.yml"
  ]' \
  --profile gauntlet
```

## Method 4: Copy from local file (if you have the file locally)

```bash
# First, upload file to S3
aws s3 cp application-prod.yml s3://starscape-rapidphotoupload/config/application-prod.yml --profile gauntlet

# Then download on EC2
aws ssm send-command \
  --instance-ids i-040798390b54b4294 \
  --document-name "AWS-RunShellScript" \
  --parameters 'commands=[
    "aws s3 cp s3://starscape-rapidphotoupload/config/application-prod.yml /opt/rapidupload/application-prod.yml",
    "sudo chown ec2-user:ec2-user /opt/rapidupload/application-prod.yml",
    "sudo chmod 600 /opt/rapidupload/application-prod.yml"
  ]' \
  --profile gauntlet
```

## Method 5: Interactive SSM Session (Easiest for editing)

```bash
# Connect interactively
aws ssm start-session --target i-040798390b54b4294 --profile gauntlet

# Then in the session:
sudo vi /opt/rapidupload/application-prod.yml
# or
sudo nano /opt/rapidupload/application-prod.yml
```

## Common sed patterns

```bash
# Replace password
sed -i 's/password:.*/password: NEW_PASSWORD/' file.yml

# Replace URL
sed -i 's|url:.*|url: jdbc:postgresql://new-host:5432/dbname|' file.yml

# Add a line after a pattern
sed -i '/pattern/a\new line here' file.yml

# Replace entire section (between two patterns)
sed -i '/^spring:$/,/^---$/c\new content' file.yml
```

## Best Practices

1. **Backup first:**
   ```bash
   cp /opt/rapidupload/application-prod.yml /opt/rapidupload/application-prod.yml.backup.$(date +%Y%m%d_%H%M%S)
   ```

2. **Verify after editing:**
   ```bash
   cat /opt/rapidupload/application-prod.yml
   ```

3. **Restart service after changes:**
   ```bash
   sudo systemctl restart rapidupload
   sudo systemctl status rapidupload
   ```

4. **Check logs if service fails:**
   ```bash
   sudo journalctl -u rapidupload -n 50 --no-pager
   ```

