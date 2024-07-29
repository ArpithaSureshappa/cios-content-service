package com.igot.cios.repository;

import com.igot.cios.entity.CornellContentEntity;
import com.igot.cios.entity.UpgradContentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UpgradContentRepository extends JpaRepository<UpgradContentEntity, String> {

    Optional<UpgradContentEntity> findByExternalId(String externalId);

    List<UpgradContentEntity> findByIsActive(boolean b);
}
