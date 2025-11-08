package com.starscape.rapidupload.features.auth.domain;

import java.util.Optional;

public interface UserRepository {
    User save(User user);
    Optional<User> findById(String userId);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}

