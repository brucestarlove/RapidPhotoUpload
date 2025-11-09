# AWS Configuration Guide

This guide explains how to configure AWS credentials and profiles for the RapidPhotoUpload application.

---

## Quick Setup

### Option 1: Use AWS Profile (Recommended)

Set the `AWS_PROFILE` environment variable to use a specific profile:

```bash
export AWS_PROFILE=gauntlet
mvn spring-boot:run
```

Or set it in `application.yml`:

```yaml
aws:
  profile: gauntlet
```

### Option 2: Use Environment Variables

```bash
export AWS_ACCESS_KEY_ID=your-access-key
export AWS_SECRET_ACCESS_KEY=your-secret-key
export AWS_REGION=us-east-2
mvn spring-boot:run
```

### Option 3: Use Default Credentials

If no profile is specified, the application will use the default AWS credentials chain:
1. Environment variables (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`)
2. Java system properties
3. Default profile from `~/.aws/credentials`
4. EC2 instance profile (if running on EC2)
5. ECS task role (if running on ECS)

---

## AWS Profiles

### Viewing Your Profiles

```bash
# List all profiles
cat ~/.aws/credentials

# Check a specific profile
aws configure list --profile gauntlet
```

### Profile Format

Your `~/.aws/credentials` file should look like:

```ini
[default]
aws_access_key_id = AKIAIOSFODNN7EXAMPLE
aws_secret_access_key = wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
region = us-west-2

[gauntlet]
aws_access_key_id = AKIAI44QH8DHBEXAMPLE
aws_secret_access_key = je7MtGbClwBF/2Zp9Utk/h3yCo8nvbEXAMPLEKEY
region = us-east-2
```

### Updating Profile Credentials

```bash
# Update credentials for a profile
aws configure set aws_access_key_id YOUR_KEY --profile gauntlet
aws configure set aws_secret_access_key YOUR_SECRET --profile gauntlet
aws configure set region us-east-2 --profile gauntlet
```

Or edit `~/.aws/credentials` directly.

---

## Application Configuration

### Environment Variables

```bash
# Set profile
export AWS_PROFILE=gauntlet

# Or set credentials directly
export AWS_ACCESS_KEY_ID=your-key
export AWS_SECRET_ACCESS_KEY=your-secret
export AWS_REGION=us-east-2

# S3 bucket (optional, defaults to starscape-rapidphotoupload)
export S3_BUCKET=your-bucket-name
```

### application.yml

```yaml
aws:
  region: ${AWS_REGION:us-east-2}
  profile: ${AWS_PROFILE:}  # Leave empty to use default, or set to "gauntlet"
  s3:
    bucket: ${S3_BUCKET:starscape-rapidphotoupload}
    presign-duration-minutes: 15
```

---

## Verification

### Test AWS Configuration

```bash
# Test with profile
aws s3 ls s3://starscape-rapidphotoupload --profile gauntlet

# Test with environment variables
AWS_PROFILE=gauntlet aws s3 ls s3://starscape-rapidphotoupload

# Test default credentials
aws s3 ls s3://starscape-rapidphotoupload
```

### Test Application

```bash
# Start with profile
AWS_PROFILE=gauntlet mvn spring-boot:run

# Or set in application.yml
# aws.profile: gauntlet
```

---

## Troubleshooting

### Error: "Unable to load credentials"

**Solution:** Ensure your profile exists and has valid credentials:

```bash
aws configure list --profile gauntlet
```

### Error: "Bucket does not exist"

**Solution:** Verify the bucket name and region:

```bash
aws s3 ls --profile gauntlet
aws s3api get-bucket-location --bucket starscape-rapidphotoupload --profile gauntlet
```

### Error: "Access Denied"

**Solution:** Check IAM permissions. Your profile needs:
- `s3:PutObject` on the bucket
- `s3:CreateMultipartUpload` for multipart uploads
- `s3:UploadPart` for multipart uploads

### Wrong Region

**Solution:** Ensure the region matches your bucket:

```bash
# Check bucket region
aws s3api get-bucket-location --bucket starscape-rapidphotoupload --profile gauntlet

# Set region in profile
aws configure set region us-east-2 --profile gauntlet
```

---

## Security Best Practices

1. **Never commit credentials** - Use environment variables or profiles
2. **Use IAM roles** in production (EC2/ECS) instead of access keys
3. **Rotate credentials** regularly
4. **Use least privilege** - Only grant necessary S3 permissions
5. **Use separate profiles** for different environments (dev/staging/prod)

---

## Production Deployment

In production (EC2/ECS), use IAM roles instead of profiles:

1. **EC2**: Attach an IAM role to the instance
2. **ECS**: Use task role in task definition
3. **Lambda**: Use execution role

The application will automatically use the instance/task role if no profile or credentials are specified.

