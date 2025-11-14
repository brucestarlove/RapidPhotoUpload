# CloudFront distribution to provide HTTPS access to backend
resource "aws_cloudfront_distribution" "api" {
  origin {
    domain_name = "ec2-18-222-212-37.us-east-2.compute.amazonaws.com"
    origin_id   = "ec2-backend"
    
    connection_attempts = 3
    connection_timeout  = 10

    custom_origin_config {
      http_port                = 8080
      https_port               = 443
      origin_protocol_policy   = "http-only"
      origin_ssl_protocols      = ["TLSv1.2"]
      origin_read_timeout       = 60
      origin_keepalive_timeout  = 5
    }
  }

  enabled = true
  comment = "RapidPhotoUpload API via CloudFront HTTPS"

  default_cache_behavior {
    allowed_methods  = ["DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT"]
    cached_methods   = ["GET", "HEAD"]
    target_origin_id = "ec2-backend"

    forwarded_values {
      query_string = true
      cookies {
        forward = "all"
      }
      headers = ["*"]
    }

    viewer_protocol_policy = "redirect-to-https"
    default_ttl            = 0
    max_ttl                = 0
    min_ttl                = 0
    compress               = true
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
  }

  tags = merge(local.common_tags, {
    Name = "${local.app_name}-api-cdn"
  })
}

output "cloudfront_domain" {
  description = "CloudFront domain name for frontend to use"
  value       = aws_cloudfront_distribution.api.domain_name
}

output "cloudfront_url" {
  description = "Full CloudFront HTTPS URL"
  value       = "https://${aws_cloudfront_distribution.api.domain_name}"
}

