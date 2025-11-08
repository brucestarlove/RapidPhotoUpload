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

