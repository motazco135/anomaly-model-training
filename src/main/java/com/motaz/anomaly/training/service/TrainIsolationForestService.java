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
        transactionFeatureRepository.findAll().forEach(transactionFeature -> {
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
            int subsample = Math.min(SUBSAMPLE, trainingData.length); // Typical choice
            IsolationForest iforest = IsolationForest.fit(trainingData);
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
            modelRegistryEntity.setSubsample(iforest.getExtensionLevel());
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

            // Display results
            System.out.println("Dataset size: " + trainingData.length + " points");
            System.out.println("Number of trees: " + iforest.trees().length);
            System.out.println("Subsample size: " + iforest.getExtensionLevel());

            // Find top anomalies
            Integer[] indices = new Integer[trainingData.length];
            for (int i = 0; i < indices.length; i++) indices[i] = i;
            Arrays.sort(indices, (i, j) -> Double.compare(scores[j], scores[i]));

            System.out.println("\nTop 10 anomalies (higher scores = more anomalous):");
            for (int i = 0; i < Math.min(10, trainingData.length); i++) {
                int idx = indices[i];
                System.out.printf("Point %d: (%.2f, %.2f) -> Score: %.4f%s%n",
                        idx, trainingData[idx][0], trainingData[idx][1], scores[idx],
                        idx >= 0.97 ? " [Actual Anomaly]" : "");
            }

            // Calculate statistics
            double avgScore = Arrays.stream(scores).average().orElse(0.0);
            double maxScore = Arrays.stream(scores).max().orElse(0.0);
            double minScore = Arrays.stream(scores).min().orElse(0.0);

            System.out.printf("\nScore Statistics: Min=%.4f, Max=%.4f, Avg=%.4f%n",
                    minScore, maxScore, avgScore);

            // Create visualizations
            //plotScatterWithAnomalies(trainingData, scores, normalPoints, "2D Dataset with Anomaly Scores");

        }

    }

//    private static void plotScatterWithAnomalies(double[][] data, double[] scores,
//                                                 int normalPoints, String title) {
//        try {
//            // Extract coordinates
//            double[] x = new double[data.length];
//            double[] y = new double[data.length];
//            for (int i = 0; i < data.length; i++) {
//                x[i] = data[i][0];
//                y[i] = data[i][1];
//            }
//
//            // Create canvas
//            Figure figure = new Figure(600, 500);
//            Canvas canvas = new Canvas(figure);
//
//            // Plot normal points in blue
//            double[] xNormal = Arrays.copyOfRange(x, 0, normalPoints);
//            double[] yNormal = Arrays.copyOfRange(y, 0, normalPoints);
//            canvas.add(ScatterPlot.of(xNormal, yNormal, Color.BLUE, '@'));
//
//            // Plot anomaly points in red
//            if (data.length > normalPoints) {
//                double[] xAnomalies = Arrays.copyOfRange(x, normalPoints, data.length);
//                double[] yAnomalies = Arrays.copyOfRange(y, normalPoints, data.length);
//                canvas.add(ScatterPlot.of(xAnomalies, yAnomalies, Color.RED, 'X'));
//            }
//
//            // Add points colored by anomaly score (high scores in orange/red)
//            for (int i = 0; i < Math.min(10, data.length); i++) {
//                // Find highest scoring points
//                int maxIdx = 0;
//                for (int j = 1; j < scores.length; j++) {
//                    if (scores[j] > scores[maxIdx]) maxIdx = j;
//                }
//
//                if (scores[maxIdx] > 0) {
//                    canvas.add(ScatterPlot.of(new double[]{x[maxIdx]}, new double[]{y[maxIdx]},
//                            Color.ORANGE, 'O'));
//                    scores[maxIdx] = -1; // Mark as processed
//                }
//            }
//
//            canvas.setTitle(title);
//            canvas.setAxisLabels("X Coordinate", "Y Coordinate");
//            canvas.window();
//
//            System.out.println("2D scatter plot created: Blue=Normal, Red=True Anomalies, Orange=High Scores");
//
//        } catch (Exception e) {
//            System.out.println("Plot creation failed: " + e.getMessage());
//            System.out.println("Make sure smile-plot dependency is available");
//        }
//    }

}
