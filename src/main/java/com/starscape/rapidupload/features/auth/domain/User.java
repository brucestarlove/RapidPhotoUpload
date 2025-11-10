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
    
    /**
     * Returns the user ID. Delegates to getId() for consistency with Entity contract.
     * @return the user ID
     */
    public String getUserId() {
        return getId();
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

