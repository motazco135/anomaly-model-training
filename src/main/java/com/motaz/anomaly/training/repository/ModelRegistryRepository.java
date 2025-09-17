package com.motaz.anomaly.training.repository;

import com.motaz.anomaly.training.model.ModelRegistryEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModelRegistryRepository extends CrudRepository<ModelRegistryEntity, Long> {
}
