package com.sribuilds.triage.repository;

import com.sribuilds.triage.model.IncidentReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * JPA repository for the durable incident report audit log.
 */
@Repository
public interface IncidentReportRepository extends JpaRepository<IncidentReportEntity, Long> {

    Optional<IncidentReportEntity> findByErrorHash(String errorHash);

    boolean existsByErrorHash(String errorHash);
}
