# GitHub Actions IAM Setup

This guide walks you through setting up IAM permissions for GitHub Actions to deploy to EC2.

## Quick Setup (5 minutes)

### Option 1: Create a New IAM User (Recommended for GitHub Actions)

1. **Create IAM User:**
   ```bash
   aws iam create-user \
     --user-name github-actions-rapidupload \
     --profile gauntlet
   ```

2. **Create and Attach Policy:**
   ```bash
   # Create the policy
   aws iam create-policy \
     --policy-name GitHubActionsRapidUploadDeploy \
     --policy-document file://infrastructure/iam/github-actions-policy.json \
     --profile gauntlet
   
   # Note the Policy ARN from output (e.g., arn:aws:iam::123456789012:policy/GitHubActionsRapidUploadDeploy)
   # Attach it to the user
   aws iam attach-user-policy \
     --user-name github-actions-rapidupload \
     --policy-arn <POLICY_ARN_FROM_ABOVE> \
     --profile gauntlet
   ```

3. **Create Access Keys:**
   ```bash
   aws iam create-access-key \
     --user-name github-actions-rapidupload \
     --profile gauntlet
   ```

4. **Save the credentials** - You'll need these for GitHub Secrets:
   - `AccessKeyId` → GitHub Secret: `AWS_ACCESS_KEY_ID`
   - `SecretAccessKey` → GitHub Secret: `AWS_SECRET_ACCESS_KEY`

### Option 2: Use Existing IAM User

If you already have an IAM user (like the one you use with `--profile gauntlet`):

1. **Attach the policy to your existing user:**
   ```bash
   # First, create the policy (if not already created)
   aws iam create-policy \
     --policy-name GitHubActionsRapidUploadDeploy \
     --policy-document file://infrastructure/iam/github-actions-policy.json \
     --profile gauntlet
   
   # Get your IAM user name
   aws sts get-caller-identity --profile gauntlet
   
   # Attach policy (replace <YOUR_USER_NAME>)
   aws iam attach-user-policy \
     --user-name <YOUR_USER_NAME> \
     --policy-arn <POLICY_ARN> \
     --profile gauntlet
   ```

2. **Get your access keys** (or create new ones):
   ```bash
   # List existing access keys
   aws iam list-access-keys --user-name <YOUR_USER_NAME> --profile gauntlet
   
   # Or create new access key
   aws iam create-access-key --user-name <YOUR_USER_NAME> --profile gauntlet
   ```

## Required GitHub Secrets

After setting up IAM, add these secrets to your GitHub repository:

1. Go to: **GitHub Repo → Settings → Secrets and variables → Actions → New repository secret**

2. Add these secrets:

| Secret Name | Value | Description |
|------------|-------|-------------|
| `AWS_ACCESS_KEY_ID` | From IAM access key | AWS access key ID |
| `AWS_SECRET_ACCESS_KEY` | From IAM access key | AWS secret access key |
| `EC2_INSTANCE_ID` | `i-040798390b54b4294` (or your instance ID) | EC2 instance ID to deploy to |

## Verify Setup

Test that the IAM user has the right permissions:

```bash
# Test S3 access
aws s3 ls s3://starscape-rapidphotoupload/deployments/ \
  --profile <YOUR_PROFILE_OR_USE_CREDENTIALS>

# Test SSM access (replace with your instance ID)
aws ssm describe-instance-information \
  --filters "Key=InstanceIds,Values=i-040798390b54b4294" \
  --profile <YOUR_PROFILE_OR_USE_CREDENTIALS>
```

## Security Best Practices

✅ **Use a dedicated IAM user for GitHub Actions** (not your personal AWS account)
✅ **Limit permissions** to only what's needed (S3 deployments bucket, SSM commands)
✅ **Rotate access keys** regularly (every 90 days)
✅ **Use IAM roles** instead of access keys when possible (requires OIDC setup - advanced)

## Troubleshooting

### "AccessDenied" when uploading to S3
- Verify the IAM user has `s3:PutObject` permission on `s3://starscape-rapidphotoupload/deployments/*`
- Check the bucket name matches exactly

### "AccessDenied" when sending SSM commands
- Verify the IAM user has `ssm:SendCommand` permission
- Check the EC2 instance ID is correct
- Ensure the EC2 instance has the SSM agent running: `sudo systemctl status amazon-ssm-agent`

### "User is not authorized to perform: ssm:SendCommand"
- The IAM user needs `ssm:SendCommand` permission
- The EC2 instance's IAM role needs `AmazonSSMManagedInstanceCore` policy (already configured by Terraform)

## Next Steps

After setting up IAM and GitHub Secrets:
1. ✅ Create GitHub Secrets (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, EC2_INSTANCE_ID)
2. ✅ Create GitHub Actions workflow files (`.github/workflows/ci-cd.yml`)
3. ✅ Push code to trigger the pipeline

