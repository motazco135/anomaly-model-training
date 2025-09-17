package com.motaz.anomaly.training.repository;

import com.motaz.anomaly.training.model.TransactionFeatureEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionFeatureRepository extends JpaRepository<TransactionFeatureEntity, Long> {
}
