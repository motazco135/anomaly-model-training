package com.motaz.anomaly.training;

import com.motaz.anomaly.training.repository.CustomerBaseLineRepository;
import com.motaz.anomaly.training.repository.ModelRegistryRepository;
import com.motaz.anomaly.training.repository.TransactionFeatureRepository;
import com.motaz.anomaly.training.repository.TransactionRepository;
import com.motaz.anomaly.training.service.AnomalyFeatureFillService;
import com.motaz.anomaly.training.service.DataPreparationService;
import com.motaz.anomaly.training.service.TrainIsolationForestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@Slf4j
@SpringBootApplication
public class AnomalyModelTrainingApplication{

    public static void main(String[] args) {
        SpringApplication.run(AnomalyModelTrainingApplication.class, args);
    }


    @Bean
    public CommandLineRunner initDatabase(TransactionRepository transactionRepository,
                                          TransactionFeatureRepository transactionFeatureRepository,
                                          CustomerBaseLineRepository customerBaseLineRepository,
                                          ModelRegistryRepository modelRegistryRepository) {
        return args -> {
            System.out.println("Initializing database with employee data...");
            log.info("Initializing database...");
            DataPreparationService dataPreparationService = new DataPreparationService(transactionRepository);
            dataPreparationService.prepareData();
            log.info("Initializing database Completed...");

            log.info("Fill Anomaly Feature Data...");
            AnomalyFeatureFillService anomalyFeatureFillService = new AnomalyFeatureFillService(transactionRepository,transactionFeatureRepository,customerBaseLineRepository);
            anomalyFeatureFillService.doFeatureFill();
            log.info("Fill Anomaly Feature Data Completed...");

            TrainIsolationForestService trainIsolationForestService = new TrainIsolationForestService(transactionFeatureRepository,modelRegistryRepository);
            trainIsolationForestService.trainModel();
            log.info("Train Isolation Forest Model Completed...");
        };
    }
}
