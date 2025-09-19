package com.motaz.anomaly.training.service;

import com.motaz.anomaly.training.model.ModelRegistryEntity;
import com.motaz.anomaly.training.repository.ModelRegistryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import smile.anomaly.IsolationForest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RuntimeScoreExampleService {

    private final ModelRegistryRepository modelRegistryRepository;

    private static int segmentOfHour(int hour) {
        if (hour <= 5)  return 0;
        if (hour <= 11) return 1;
        if (hour <= 17) return 2;
        return 3;
    }

    private  IsolationForest loadLatestModel() throws IOException, ClassNotFoundException {
        IsolationForest iForest = null ;
        Optional<ModelRegistryEntity> optionalModelRegistryEntity = modelRegistryRepository.findLatestIFModel();
        if (optionalModelRegistryEntity.isPresent()) {
            ModelRegistryEntity modelRegistryEntity = optionalModelRegistryEntity.get();
            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(modelRegistryEntity.getModelBytes()));
             iForest  = (IsolationForest) ois.readObject();
            ois.close();
        }
        return iForest;
    }
}
