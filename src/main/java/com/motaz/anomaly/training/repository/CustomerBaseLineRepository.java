package com.motaz.anomaly.training.repository;

import com.motaz.anomaly.training.model.CustomerBaselineEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerBaseLineRepository extends JpaRepository<CustomerBaselineEntity, Long> {
}
