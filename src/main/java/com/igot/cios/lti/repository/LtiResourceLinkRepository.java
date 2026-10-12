package com.igot.cios.lti.repository;

import com.igot.cios.lti.entity.LtiResourceLinkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LtiResourceLinkRepository extends JpaRepository<LtiResourceLinkEntity, String> {

    Optional<LtiResourceLinkEntity> findByExternalIdAndPartnerId(String externalId, String partnerId);

    List<LtiResourceLinkEntity> findByRegistrationId(String registrationId);
}
