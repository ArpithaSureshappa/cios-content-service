package com.igot.cios.repository;

import com.igot.cios.entity.LtiScoreEntity;
import com.igot.cios.entity.LtiScoreEntityId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LtiScoreRepository extends JpaRepository<LtiScoreEntity, LtiScoreEntityId> {

    Page<LtiScoreEntity> findByLineItemId(String lineItemId, Pageable pageable);

    Optional<LtiScoreEntity> findByLineItemIdAndUserId(String lineItemId, String userId);
}
