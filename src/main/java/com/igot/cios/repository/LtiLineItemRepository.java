package com.igot.cios.repository;

import com.igot.cios.entity.LtiLineItemEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LtiLineItemRepository extends JpaRepository<LtiLineItemEntity, String> {

    Optional<LtiLineItemEntity> findByIdAndPartnerCode(String id, String partnerCode);

    @Query(value = "SELECT * FROM public.lti_line_item_entity l " +
            "WHERE l.partner_code = :partnerCode " +
            "AND (:resourceLinkId IS NULL OR l.resource_link_id = :resourceLinkId) " +
            "AND (:resourceId IS NULL OR l.resource_id = :resourceId) " +
            "AND (:tag IS NULL OR l.tag = :tag)",
            countQuery = "SELECT count(*) FROM public.lti_line_item_entity l " +
                    "WHERE l.partner_code = :partnerCode " +
                    "AND (:resourceLinkId IS NULL OR l.resource_link_id = :resourceLinkId) " +
                    "AND (:resourceId IS NULL OR l.resource_id = :resourceId) " +
                    "AND (:tag IS NULL OR l.tag = :tag)",
            nativeQuery = true)
    Page<LtiLineItemEntity> findByFilters(@Param("partnerCode") String partnerCode,
                                           @Param("resourceLinkId") String resourceLinkId,
                                           @Param("resourceId") String resourceId,
                                           @Param("tag") String tag,
                                           Pageable pageable);
}
