package com.motaz.anomaly.training.service;

import com.motaz.anomaly.training.model.ModelRegistryEntity;
import com.motaz.anomaly.training.repository.ModelRegistryRepository;
import com.motaz.anomaly.training.repository.TransactionFeatureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import smile.anomaly.IsolationForest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class TrainIsolationForestService {

    private final TransactionFeatureRepository transactionFeatureRepository;
    private final ModelRegistryRepository modelRegistryRepository;

    private static final int TREES = 150;
    private static final int SUBSAMPLE = 256;

    public void trainModel(){
        //TODO: Use Pagination
        List<double[]> rows = new ArrayList<>();
        transactionFeatureRepository.findByIsTrainable(true).forEach(transactionFeature ->{
                rows.add(new double[]{
                        transactionFeature.getAmountZScore(),
                        transactionFeature.getTimeSegmentRatio(),
                        transactionFeature.getVelocityRatio(),
                        transactionFeature.getMedianDeviation()
                });
        });

        if(!rows.isEmpty()){
            double[][] trainingData = rows.toArray(new double[0][]);
            log.info("Training Isolation Forest model...");

            // 2) Build options
            // 3) Compute sampling_rate = min(1.0, TARGET_SUBSAMPLE / n)
            double samplingRate = Math.min(1.0, SUBSAMPLE / (double) trainingData.length);
            log.info("sampling rate: {}",  samplingRate);
            IsolationForest.Options options = new IsolationForest.Options(TREES, 0, samplingRate, 0);
            IsolationForest iforest = IsolationForest.fit(trainingData,options);
            log.info("Model trained successfully.");

            // serialize + save
            byte[] bytes;
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                 ObjectOutputStream oos = new ObjectOutputStream(bos)) {
                oos.writeObject(iforest);
                oos.flush();
                bytes = bos.toByteArray();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            //Save model
            String schema ="[amountZScore,timeSegmentRatio,velocityRatio,medianDeviation]";
            ModelRegistryEntity modelRegistryEntity = new ModelRegistryEntity();
            modelRegistryEntity.setTrees(iforest.trees().length);
            modelRegistryEntity.setSubsample((int) samplingRate);
            modelRegistryEntity.setFeatureSchema(schema);
            modelRegistryEntity.setSchemaHash(Integer.toHexString(schema.hashCode()));
            modelRegistryEntity.setTrainedRows(Long.valueOf(trainingData.length));
            modelRegistryEntity.setNotes("Isolation Forest trained from transaction_features");
            modelRegistryEntity.setModelBytes(bytes);
            modelRegistryRepository.save(modelRegistryEntity);

            // Calculate anomaly scores for all points
            double[] scores = new double[trainingData.length];
            for (int i = 0; i < trainingData.length; i++) {
                scores[i] = iforest.score(trainingData[i]);
            }

            Arrays.sort(scores);
            double p95 = scores[(int)Math.floor(0.95 * (scores.length - 1))]; // 95th percentile
            double p98 = scores[(int)Math.floor(0.98 * (scores.length - 1))]; // 98th percentile
            double p99 = scores[(int)Math.floor(0.99 * (scores.length - 1))]; // 99th percentile
            log.info("Calibrated percentiles (higher=worse): p95={}, p98={}, p99={}", p95, p98, p99);

            // Display results
            log.info("Dataset size: {} pints", trainingData.length);
            log.info("Number of trees: {} ", iforest.trees().length);
            log.info("Subsample size: {}", iforest.getExtensionLevel());

            // Find top anomalies
//            Integer[] indices = new Integer[trainingData.length];
//            for (int i = 0; i < indices.length; i++) indices[i] = i;
//            Arrays.sort(indices, (i, j) -> Double.compare(scores[j], scores[i]));
//
//            log.info("\nTop 10 anomalies (higher scores = more anomalous):");
//            for (int i = 0; i < Math.min(10, trainingData.length); i++) {
//                int idx = indices[i];
//                System.out.printf("Point %d: (%.2f, %.2f) -> Score: %.4f%s%n",
//                        idx, trainingData[idx][0], trainingData[idx][1], scores[idx],
//                        idx >= 0.97 ? " [Actual Anomaly]" : "");
//            }

        }

    }
}
