package com.starscape.rapidupload.features.tags.infra;

import com.starscape.rapidupload.features.tags.domain.Tag;
import com.starscape.rapidupload.features.tags.domain.TagRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository implementation for Tag entity.
 * Spring Data JPA automatically provides implementations for methods declared in TagRepository
 * that match JpaRepository methods (save, findById, delete).
 */
@Repository
public interface JpaTagRepository extends JpaRepository<Tag, String>, TagRepository {
    
    @Override
    Optional<Tag> findByUserIdAndLabel(String userId, String label);
    
    @Override
    List<Tag> findByUserId(String userId);
    
    @Query("SELECT t FROM Tag t WHERE t.userId = :userId ORDER BY t.label ASC")
    List<Tag> findByUserIdOrderByLabelAsc(@Param("userId") String userId);
}

