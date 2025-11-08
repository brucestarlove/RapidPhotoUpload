# Phase 1: Foundation &amp; Infrastructure

**Status**: Foundation  
**Duration Estimate**: 2-3 weeks  
**Dependencies**: None

---

## Overview

Establish the foundational architecture for RapidPhotoUpload: project scaffolding with Spring Boot 3.4+, PostgreSQL database schema, core domain model following DDD principles, AWS infrastructure (S3, RDS, IAM), and basic JWT authentication. This phase creates the skeleton upon which all subsequent features will be built.

---

## Goals

1. Set up a production-ready Spring Boot 3.4+ project with Java 21
2. Implement database schema with Flyway migrations
3. Design and code core domain entities with JPA mappings
4. Establish Vertical Slice Architecture (VSA) package structure
5. Implement basic JWT authentication
6. Provision AWS infrastructure (S3, RDS, IAM)

---

## Technical Stack

### Core Dependencies

**Spring Boot 3.4.1** (validated via Context7)
- `spring-boot-starter-web`
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-security`
- `spring-boot-starter-actuator`
- `spring-boot-starter-validation`

**Database**
- PostgreSQL 15+ (via RDS)
- Flyway 9.22+ for migrations
- HikariCP (bundled) for connection pooling

**AWS SDK Java v2** (validated via Context7)
- `software.amazon.awssdk:s3:2.20+`
- `software.amazon.awssdk:sts:2.20+`

**Security**
- `io.jsonwebtoken:jjwt-api:0.12+` (JWT handling)
- `io.jsonwebtoken:jjwt-impl:0.12+`
- `io.jsonwebtoken:jjwt-jackson:0.12+`

**Build Tool**
- Maven 3.9+ or Gradle 8.5+

---

## Deliverables

### 1. Project Scaffolding

#### Maven POM Structure

```xml
<project>
  <groupId>com.starscape</groupId>
  <artifactId>rapidupload</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <packaging>jar</packaging>

  <properties>
    <java.version>21</java.version>
    <spring-boot.version>3.4.1</spring-boot.version>
    <aws-sdk.version>2.20.0</aws-sdk.version>
  </properties>

  <dependencies>
    <!-- Spring Boot Starters -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>

    <!-- Database -->
    <dependency>
      <groupId>org.postgresql</groupId>
      <artifactId>postgresql</artifactId>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-core</artifactId>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-database-postgresql</artifactId>
    </dependency>

    <!-- AWS SDK v2 -->
    <dependency>
      <groupId>software.amazon.awssdk</groupId>
      <artifactId>s3</artifactId>
      <version>${aws-sdk.version}</version>
    </dependency>
    <dependency>
      <groupId>software.amazon.awssdk</groupId>
      <artifactId>sts</artifactId>
      <version>${aws-sdk.version}</version>
    </dependency>

    <!-- JWT -->
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-api</artifactId>
      <version>0.12.3</version>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-impl</artifactId>
      <version>0.12.3</version>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-jackson</artifactId>
      <version>0.12.3</version>
      <scope>runtime</scope>
    </dependency>

    <!-- Test Dependencies -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

#### Application Configuration (`application.yml`)

```yaml
spring:
  application:
    name: rapidupload
  
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:rapidupload}
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD:postgres}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
  
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
        jdbc:
          batch_size: 20
    show-sql: false
  
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: when-authorized

aws:
  region: ${AWS_REGION:us-east-1}
  s3:
    bucket: ${S3_BUCKET:rapidupload-media-dev}
    presign-duration-minutes: 15

app:
  security:
    jwt:
      secret: ${JWT_SECRET:change-this-in-production}
      expiration-ms: 86400000  # 24 hours
      issuer: rapidupload-api
```

---

### 2. Package Structure (Vertical Slice Architecture)

```
com.starscape.rapidupload/
├── RapidUploadApplication.java          # Main entry point
├── common/                               # Shared infrastructure
│   ├── domain/                           # Base domain primitives
│   │   ├── AggregateRoot.java
│   │   ├── DomainEvent.java
│   │   ├── Entity.java
│   │   └── ValueObject.java
│   ├── config/                           # Global config
│   │   ├── AwsConfig.java
│   │   ├── JpaConfig.java
│   │   └── SecurityConfig.java
│   ├── exception/                        # Global exception handling
│   │   ├── GlobalExceptionHandler.java
│   │   ├── BusinessException.java
│   │   └── NotFoundException.java
│   └── security/                         # Security components
│       ├── JwtTokenProvider.java
│       ├── JwtAuthenticationFilter.java
│       └── UserPrincipal.java
└── features/                             # Feature slices
    ├── auth/                             # Authentication slice
    │   ├── api/                          # Controllers
    │   │   ├── AuthController.java
    │   │   └── dto/
    │   │       ├── LoginRequest.java
    │   │       ├── LoginResponse.java
    │   │       └── RegisterRequest.java
    │   ├── app/                          # Application services
    │   │   ├── AuthService.java
    │   │   └── UserService.java
    │   ├── domain/                       # Domain model
    │   │   ├── User.java
    │   │   ├── UserRepository.java
    │   │   └── UserStatus.java
    │   └── infra/                        # Infrastructure
    │       └── JpaUserRepository.java
    ├── uploadphoto/                      # Upload slice (Phase 2)
    │   ├── api/
    │   ├── app/
    │   ├── domain/
    │   └── infra/
    ├── getphotometadata/                 # Query slice (Phase 4)
    │   ├── api/
    │   ├── app/
    │   └── infra/
    ├── listphotos/                       # List/filter slice (Phase 4)
    │   ├── api/
    │   ├── app/
    │   └── infra/
    └── trackprogress/                    # WebSocket slice (Phase 4)
        ├── api/
        ├── app/
        └── infra/
```

---

### 3. Database Schema (Flyway Migrations)

#### Migration: `V1__create_users_table.sql`

```sql
-- Users table
CREATE TABLE users (
    user_id VARCHAR(64) PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    CONSTRAINT users_status_check CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED'))
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status);

-- Add audit trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
```

#### Migration: `V2__create_upload_jobs_table.sql`

```sql
-- Upload Jobs table
CREATE TABLE upload_jobs (
    job_id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL REFERENCES users(user_id),
    total_count INT NOT NULL,
    completed_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    cancelled_count INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    CONSTRAINT upload_jobs_status_check CHECK (
        status IN ('QUEUED', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'CANCELLED', 'COMPLETED_WITH_ERRORS')
    ),
    CONSTRAINT upload_jobs_total_count_check CHECK (total_count > 0),
    CONSTRAINT upload_jobs_counts_check CHECK (
        completed_count >= 0 AND 
        failed_count >= 0 AND 
        cancelled_count >= 0 AND
        (completed_count + failed_count + cancelled_count) <= total_count
    )
);

CREATE INDEX idx_upload_jobs_user_id ON upload_jobs(user_id);
CREATE INDEX idx_upload_jobs_status ON upload_jobs(status);
CREATE INDEX idx_upload_jobs_created_at ON upload_jobs(created_at DESC);
CREATE INDEX idx_upload_jobs_user_created ON upload_jobs(user_id, created_at DESC);

CREATE TRIGGER upload_jobs_updated_at
    BEFORE UPDATE ON upload_jobs
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
```

#### Migration: `V3__create_photos_table.sql`

```sql
-- Photos table
CREATE TABLE photos (
    photo_id VARCHAR(64) PRIMARY KEY,
    job_id VARCHAR(64) NOT NULL REFERENCES upload_jobs(job_id),
    user_id VARCHAR(64) NOT NULL REFERENCES users(user_id),
    filename TEXT NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    bytes BIGINT NOT NULL,
    s3_key TEXT,
    s3_bucket VARCHAR(255),
    etag VARCHAR(255),
    checksum TEXT,
    width INT,
    height INT,
    exif_json JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    
    CONSTRAINT photos_status_check CHECK (
        status IN ('QUEUED', 'UPLOADING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT photos_bytes_check CHECK (bytes > 0),
    CONSTRAINT photos_dimensions_check CHECK (
        (width IS NULL AND height IS NULL) OR (width > 0 AND height > 0)
    )
);

CREATE INDEX idx_photos_job_id ON photos(job_id);
CREATE INDEX idx_photos_user_id ON photos(user_id);
CREATE INDEX idx_photos_status ON photos(status);
CREATE INDEX idx_photos_created_at ON photos(created_at DESC);
CREATE INDEX idx_photos_user_created ON photos(user_id, created_at DESC);
CREATE INDEX idx_photos_s3_key ON photos(s3_key) WHERE s3_key IS NOT NULL;
CREATE INDEX idx_photos_completed_at ON photos(completed_at DESC) WHERE completed_at IS NOT NULL;

-- GIN index for EXIF JSON queries (Phase 4)
CREATE INDEX idx_photos_exif_json ON photos USING GIN(exif_json);

CREATE TRIGGER photos_updated_at
    BEFORE UPDATE ON photos
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
```

#### Migration: `V4__create_tags_tables.sql`

```sql
-- Tags table
CREATE TABLE tags (
    tag_id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL REFERENCES users(user_id),
    label VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    CONSTRAINT tags_label_check CHECK (LENGTH(TRIM(label)) > 0),
    CONSTRAINT tags_user_label_unique UNIQUE (user_id, label)
);

CREATE INDEX idx_tags_user_id ON tags(user_id);
CREATE INDEX idx_tags_label ON tags(label);

-- Photo-Tag junction table
CREATE TABLE photo_tags (
    photo_id VARCHAR(64) NOT NULL REFERENCES photos(photo_id) ON DELETE CASCADE,
    tag_id VARCHAR(64) NOT NULL REFERENCES tags(tag_id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    PRIMARY KEY (photo_id, tag_id)
);

CREATE INDEX idx_photo_tags_tag_id ON photo_tags(tag_id);
CREATE INDEX idx_photo_tags_created_at ON photo_tags(created_at DESC);
```

#### Migration: `V5__create_outbox_events_table.sql`

```sql
-- Transactional Outbox for reliable event publishing
CREATE TABLE outbox_events (
    event_id VARCHAR(64) PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ,
    
    CONSTRAINT outbox_events_aggregate_type_check CHECK (
        aggregate_type IN ('User', 'UploadJob', 'Photo', 'Tag')
    )
);

CREATE INDEX idx_outbox_events_processed_at ON outbox_events(processed_at) WHERE processed_at IS NULL;
CREATE INDEX idx_outbox_events_created_at ON outbox_events(created_at);
CREATE INDEX idx_outbox_events_aggregate ON outbox_events(aggregate_type, aggregate_id);
```

---

### 4. Core Domain Model

#### Base Domain Classes

**`common/domain/Entity.java`**
```java
package com.starscape.rapidupload.common.domain;

import java.io.Serializable;
import java.util.Objects;

public abstract class Entity<ID extends Serializable> {
    
    protected ID id;
    
    protected Entity() {}
    
    protected Entity(ID id) {
        this.id = Objects.requireNonNull(id, "Entity ID cannot be null");
    }
    
    public ID getId() {
        return id;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Entity<?> entity = (Entity<?>) o;
        return Objects.equals(id, entity.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
```

**`common/domain/AggregateRoot.java`**
```java
package com.starscape.rapidupload.common.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class AggregateRoot<ID extends Serializable> extends Entity<ID> {
    
    private final transient List<DomainEvent> domainEvents = new ArrayList<>();
    
    protected AggregateRoot() {
        super();
    }
    
    protected AggregateRoot(ID id) {
        super(id);
    }
    
    protected void registerEvent(DomainEvent event) {
        domainEvents.add(event);
    }
    
    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }
    
    public void clearDomainEvents() {
        domainEvents.clear();
    }
}
```

**`common/domain/DomainEvent.java`**
```java
package com.starscape.rapidupload.common.domain;

import java.time.Instant;

public interface DomainEvent {
    String getEventType();
    String getAggregateId();
    Instant getOccurredOn();
}
```

**`common/domain/ValueObject.java`**
```java
package com.starscape.rapidupload.common.domain;

public interface ValueObject {
    // Marker interface for value objects
    // Value objects should be immutable and compared by value
}
```

#### User Aggregate

**`features/auth/domain/User.java`**
```java
package com.starscape.rapidupload.features.auth.domain;

import com.starscape.rapidupload.common.domain.AggregateRoot;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "users")
public class User extends AggregateRoot<String> {
    
    @Id
    @Column(name = "user_id")
    private String userId;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    protected User() {
        // JPA constructor
    }
    
    public User(String userId, String email, String passwordHash) {
        super(userId);
        this.userId = Objects.requireNonNull(userId);
        this.email = Objects.requireNonNull(email);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.status = UserStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
    
    @Override
    public String getId() {
        return userId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public String getEmail() {
        return email;
    }
    
    public String getPasswordHash() {
        return passwordHash;
    }
    
    public UserStatus getStatus() {
        return status;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    
    public void suspend() {
        this.status = UserStatus.SUSPENDED;
        this.updatedAt = Instant.now();
    }
    
    public void activate() {
        this.status = UserStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }
    
    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }
    
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
```

**`features/auth/domain/UserStatus.java`**
```java
package com.starscape.rapidupload.features.auth.domain;

public enum UserStatus {
    ACTIVE,
    SUSPENDED,
    DELETED
}
```

**`features/auth/domain/UserRepository.java`**
```java
package com.starscape.rapidupload.features.auth.domain;

import java.util.Optional;

public interface UserRepository {
    User save(User user);
    Optional<User> findById(String userId);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

#### Value Objects (Phase 1 skeleton, used in Phase 2+)

**`features/uploadphoto/domain/Progress.java`**
```java
package com.starscape.rapidupload.features.uploadphoto.domain;

import com.starscape.rapidupload.common.domain.ValueObject;

public record Progress(
    int percent,
    long bytesSent,
    long bytesTotal
) implements ValueObject {
    
    public Progress {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("Percent must be between 0 and 100");
        }
        if (bytesSent < 0 || bytesTotal < 0) {
            throw new IllegalArgumentException("Bytes cannot be negative");
        }
        if (bytesSent > bytesTotal) {
            throw new IllegalArgumentException("Bytes sent cannot exceed total");
        }
    }
    
    public static Progress of(long bytesSent, long bytesTotal) {
        int percent = bytesTotal > 0 ? (int) ((bytesSent * 100) / bytesTotal) : 0;
        return new Progress(percent, bytesSent, bytesTotal);
    }
}
```

**`features/uploadphoto/domain/ObjectRef.java`**
```java
package com.starscape.rapidupload.features.uploadphoto.domain;

import com.starscape.rapidupload.common.domain.ValueObject;

public record ObjectRef(
    String bucket,
    String key,
    String region
) implements ValueObject {
    
    public ObjectRef {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("Bucket cannot be blank");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Key cannot be blank");
        }
        if (region == null || region.isBlank()) {
            throw new IllegalArgumentException("Region cannot be blank");
        }
    }
}
```

**`features/uploadphoto/domain/Checksum.java`**
```java
package com.starscape.rapidupload.features.uploadphoto.domain;

import com.starscape.rapidupload.common.domain.ValueObject;

public record Checksum(
    String algorithm,
    String value
) implements ValueObject {
    
    public Checksum {
        if (algorithm == null || algorithm.isBlank()) {
            throw new IllegalArgumentException("Algorithm cannot be blank");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Value cannot be blank");
        }
    }
    
    public static Checksum sha256(String value) {
        return new Checksum("SHA-256", value);
    }
}
```

---

### 5. Security Implementation

#### JWT Token Provider

**`common/security/JwtTokenProvider.java`**
```java
package com.starscape.rapidupload.common.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Component
public class JwtTokenProvider {
    
    private final SecretKey secretKey;
    private final long expirationMs;
    private final String issuer;
    
    public JwtTokenProvider(
            @Value("${app.security.jwt.secret}") String secret,
            @Value("${app.security.jwt.expiration-ms}") long expirationMs,
            @Value("${app.security.jwt.issuer}") String issuer) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.issuer = issuer;
    }
    
    public String generateToken(String userId, String email, List<String> scopes) {
        Instant now = Instant.now();
        Instant expiration = now.plusMillis(expirationMs);
        
        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .claim("scopes", String.join(",", scopes))
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }
    
    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    
    public String getUserIdFromToken(String token) {
        return validateToken(token).getSubject();
    }
    
    public boolean isTokenValid(String token) {
        try {
            validateToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
```

**`common/security/UserPrincipal.java`**
```java
package com.starscape.rapidupload.common.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class UserPrincipal implements UserDetails {
    
    private final String userId;
    private final String email;
    private final List<String> scopes;
    
    public UserPrincipal(String userId, String email, List<String> scopes) {
        this.userId = userId;
        this.email = email;
        this.scopes = scopes;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public String getEmail() {
        return email;
    }
    
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return scopes.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }
    
    @Override
    public String getPassword() {
        return null; // Not used with JWT
    }
    
    @Override
    public String getUsername() {
        return email;
    }
    
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }
    
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }
    
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
    
    @Override
    public boolean isEnabled() {
        return true;
    }
}
```

**`common/security/JwtAuthenticationFilter.java`**
```java
package com.starscape.rapidupload.common.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private final JwtTokenProvider tokenProvider;
    
    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }
    
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        
        try {
            String jwt = getJwtFromRequest(request);
            
            if (jwt != null && tokenProvider.isTokenValid(jwt)) {
                Claims claims = tokenProvider.validateToken(jwt);
                String userId = claims.getSubject();
                String email = claims.get("email", String.class);
                String scopesStr = claims.get("scopes", String.class);
                List<String> scopes = scopesStr != null ? 
                    Arrays.asList(scopesStr.split(",")) : List.of();
                
                UserPrincipal principal = new UserPrincipal(userId, email, scopes);
                UsernamePasswordAuthenticationToken authentication = 
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ex) {
            logger.error("Could not set user authentication in security context", ex);
        }
        
        filterChain.doFilter(request, response);
    }
    
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
```

**`common/config/SecurityConfig.java`**
```java
package com.starscape.rapidupload.common.config;

import com.starscape.rapidupload.common.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    private final JwtAuthenticationFilter jwtAuthFilter;
    
    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/commands/**").hasAuthority("photos:write")
                .requestMatchers("/queries/**").hasAuthority("photos:read")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

---

### 6. Auth Feature Implementation

**`features/auth/api/AuthController.java`**
```java
package com.starscape.rapidupload.features.auth.api;

import com.starscape.rapidupload.features.auth.api.dto.LoginRequest;
import com.starscape.rapidupload.features.auth.api.dto.LoginResponse;
import com.starscape.rapidupload.features.auth.api.dto.RegisterRequest;
import com.starscape.rapidupload.features.auth.app.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    private final AuthService authService;
    
    public AuthController(AuthService authService) {
        this.authService = authService;
    }
    
    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        LoginResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}
```

**`features/auth/api/dto/LoginRequest.java`**
```java
package com.starscape.rapidupload.features.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    String email,
    
    @NotBlank(message = "Password is required")
    String password
) {}
```

**`features/auth/api/dto/LoginResponse.java`**
```java
package com.starscape.rapidupload.features.auth.api.dto;

public record LoginResponse(
    String userId,
    String email,
    String token,
    String tokenType,
    long expiresIn
) {
    public static LoginResponse of(String userId, String email, String token, long expiresInMs) {
        return new LoginResponse(userId, email, token, "Bearer", expiresInMs);
    }
}
```

**`features/auth/api/dto/RegisterRequest.java`**
```java
package com.starscape.rapidupload.features.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    String email,
    
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    String password
) {}
```

**`features/auth/app/AuthService.java`**
```java
package com.starscape.rapidupload.features.auth.app;

import com.starscape.rapidupload.common.security.JwtTokenProvider;
import com.starscape.rapidupload.features.auth.api.dto.LoginRequest;
import com.starscape.rapidupload.features.auth.api.dto.LoginResponse;
import com.starscape.rapidupload.features.auth.api.dto.RegisterRequest;
import com.starscape.rapidupload.features.auth.domain.User;
import com.starscape.rapidupload.features.auth.domain.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AuthService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final long tokenExpirationMs;
    
    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider,
            @Value("${app.security.jwt.expiration-ms}") long tokenExpirationMs) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.tokenExpirationMs = tokenExpirationMs;
    }
    
    @Transactional
    public LoginResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already registered");
        }
        
        String userId = "user_" + UUID.randomUUID().toString().replace("-", "");
        String passwordHash = passwordEncoder.encode(request.password());
        
        User user = new User(userId, request.email(), passwordHash);
        userRepository.save(user);
        
        List<String> scopes = List.of("photos:read", "photos:write");
        String token = tokenProvider.generateToken(userId, request.email(), scopes);
        
        return LoginResponse.of(userId, request.email(), token, tokenExpirationMs);
    }
    
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        
        if (!user.isActive()) {
            throw new IllegalStateException("Account is not active");
        }
        
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        
        List<String> scopes = List.of("photos:read", "photos:write");
        String token = tokenProvider.generateToken(user.getUserId(), user.getEmail(), scopes);
        
        return LoginResponse.of(user.getUserId(), user.getEmail(), token, tokenExpirationMs);
    }
}
```

**`features/auth/infra/JpaUserRepository.java`**
```java
package com.starscape.rapidupload.features.auth.infra;

import com.starscape.rapidupload.features.auth.domain.User;
import com.starscape.rapidupload.features.auth.domain.UserRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JpaUserRepository extends JpaRepository<User, String>, UserRepository {
    
    @Override
    Optional<User> findByEmail(String email);
    
    @Override
    boolean existsByEmail(String email);
}
```

---

### 7. AWS Configuration

**`common/config/AwsConfig.java`**
```java
package com.starscape.rapidupload.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class AwsConfig {
    
    @Value("${aws.region}")
    private String region;
    
    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
    
    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
```

---

### 8. AWS Infrastructure (Terraform)

**`infrastructure/terraform/main.tf`**
```hcl
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
  region = var.aws_region
}

locals {
  app_name = "rapidupload"
  env      = var.environment
  
  common_tags = {
    Project     = "RapidPhotoUpload"
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

# S3 Bucket for media storage
resource "aws_s3_bucket" "media" {
  bucket = "${local.app_name}-media-${local.env}"
  
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

# RDS PostgreSQL
resource "aws_db_subnet_group" "main" {
  name       = "${local.app_name}-db-subnet-${local.env}"
  subnet_ids = var.database_subnet_ids
  
  tags = local.common_tags
}

resource "aws_security_group" "rds" {
  name_prefix = "${local.app_name}-rds-${local.env}-"
  vpc_id      = var.vpc_id
  
  ingress {
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = var.allowed_cidr_blocks
  }
  
  tags = local.common_tags
}

resource "aws_db_instance" "postgres" {
  identifier     = "${local.app_name}-db-${local.env}"
  engine         = "postgres"
  engine_version = "15.4"
  instance_class = var.db_instance_class
  
  allocated_storage     = 20
  max_allocated_storage = 100
  storage_type          = "gp3"
  storage_encrypted     = true
  
  db_name  = "rapidupload"
  username = var.db_master_username
  password = var.db_master_password
  
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  
  backup_retention_period = 7
  backup_window          = "03:00-04:00"
  maintenance_window     = "mon:04:00-mon:05:00"
  
  skip_final_snapshot       = var.environment == "dev" ? true : false
  final_snapshot_identifier = "${local.app_name}-final-${local.env}-${formatdate("YYYYMMDDhhmmss", timestamp())}"
  
  tags = merge(local.common_tags, {
    Name = "${local.app_name}-db-${local.env}"
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
  value = aws_db_instance.postgres.endpoint
}

output "rds_database_name" {
  value = aws_db_instance.postgres.db_name
}
```

**`infrastructure/terraform/variables.tf`**
```hcl
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
  default     = ["http://localhost:3000"]
}

variable "db_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t3.micro"
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
```

---

### 9. Global Exception Handling

**`common/exception/GlobalExceptionHandler.java`**
```java
package com.starscape.rapidupload.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        ErrorResponse response = new ErrorResponse(
            "VALIDATION_ERROR",
            "Validation failed",
            errors,
            Instant.now()
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        ErrorResponse response = new ErrorResponse(
            "BAD_REQUEST",
            ex.getMessage(),
            null,
            Instant.now()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        ErrorResponse response = new ErrorResponse(
            "ILLEGAL_STATE",
            ex.getMessage(),
            null,
            Instant.now()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }
    
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {
        ErrorResponse response = new ErrorResponse(
            "NOT_FOUND",
            ex.getMessage(),
            null,
            Instant.now()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
    
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        ErrorResponse response = new ErrorResponse(
            "FORBIDDEN",
            "Access denied",
            null,
            Instant.now()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        ErrorResponse response = new ErrorResponse(
            "INTERNAL_SERVER_ERROR",
            "An unexpected error occurred",
            null,
            Instant.now()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    public record ErrorResponse(
        String code,
        String message,
        Map<String, String> details,
        Instant timestamp
    ) {}
}
```

**`common/exception/NotFoundException.java`**
```java
package com.starscape.rapidupload.common.exception;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
```

**`common/exception/BusinessException.java`**
```java
package com.starscape.rapidupload.common.exception;

public class BusinessException extends RuntimeException {
    private final String code;
    
    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }
    
    public String getCode() {
        return code;
    }
}
```

---

## Acceptance Criteria

### ✓ Application Startup
- [ ] Application starts successfully with `mvn spring-boot:run` or `gradle bootRun`
- [ ] All beans initialized without errors
- [ ] Health endpoint `/actuator/health` returns 200 OK

### ✓ Database
- [ ] Flyway migrations execute successfully
- [ ] All tables created with correct schema
- [ ] Indexes and constraints in place
- [ ] Can connect to PostgreSQL (local or RDS)

### ✓ Authentication
- [ ] POST `/api/auth/register` creates user and returns JWT
- [ ] POST `/api/auth/login` validates credentials and returns JWT
- [ ] JWT token can be validated and contains correct claims
- [ ] Protected endpoints reject requests without valid JWT

### ✓ AWS Infrastructure
- [ ] S3 bucket created with CORS configured
- [ ] RDS PostgreSQL instance provisioned
- [ ] IAM role has correct S3 permissions
- [ ] Application can authenticate to AWS via IAM role

### ✓ Code Quality
- [ ] Domain model follows DDD principles (aggregates, value objects)
- [ ] Vertical slice architecture implemented
- [ ] Exception handling comprehensive
- [ ] Code compiles with no warnings

---

## Next Steps

Upon completion of Phase 1:
1. **Verify** all acceptance criteria
2. **Deploy** to development environment
3. **Test** authentication flow end-to-end
4. **Proceed** to Phase 2: Core Upload Flow

---

## References

- **Spring Boot 3.4 Documentation**: https://spring.io/projects/spring-boot
- **AWS SDK Java v2**: https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/
- **Flyway**: https://flywaydb.org/documentation/
- **JJWT**: https://github.com/jwtk/jjwt

---

**Phase 1 Complete** → Ready for Phase 2 (Core Upload Flow)

