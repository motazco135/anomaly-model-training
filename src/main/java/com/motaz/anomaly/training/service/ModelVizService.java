package com.motaz.anomaly.training.service;


import com.motaz.anomaly.training.model.ModelRegistryEntity;
import com.motaz.anomaly.training.model.TransactionFeatureEntity;
import com.motaz.anomaly.training.repository.ModelRegistryRepository;
import com.motaz.anomaly.training.repository.TransactionFeatureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import smile.anomaly.IsolationForest;
import smile.plot.swing.Histogram;
import smile.plot.swing.ScatterPlot;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelVizService {

    private final ModelRegistryRepository modelRegistryRepository;
    private final TransactionFeatureRepository transactionFeatureRepository;

    public void visualize() throws IOException, ClassNotFoundException, InterruptedException, InvocationTargetException {
       List<TransactionFeatureEntity> transactionFeatureEntityList = transactionFeatureRepository.findAll();
        List<double[]> rows = new ArrayList<>();
        transactionFeatureEntityList.forEach(transactionFeatureEntity -> {
            rows.add(new double[]{
                    transactionFeatureEntity.getAmountZScore(),
                    transactionFeatureEntity.getTimeSegmentRatio(),
                    transactionFeatureEntity.getVelocityRatio(),
                    transactionFeatureEntity.getMedianDeviation()
            });
        });

        IsolationForest model = loadLatestModel();
        double[] scores = new double[rows.size()];
        for (int i = 0; i < rows.size(); i++) scores[i] = model.score(rows.get(i));

        double threshold = 0.97; // tune later with evaluation
        // separate normal/anomaly for plotting
        List<Double> xN = new ArrayList<>(), yN = new ArrayList<>();
        List<Double> xA = new ArrayList<>(), yA = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            double[] f = rows.get(i);
            double score = scores[i];
            double x = f[0];  // amountZScore
            double y = f[1];  // timeSegmentRatio
            if (score >= threshold) { xA.add(x); yA.add(y); }
            else { xN.add(x); yN.add(y); }
        }

        // ===== HISTOGRAM of scores =====
        var scoreHist = Histogram.of(scores).figure();
        scoreHist.setAxisLabels("Anomaly score", "Count");
        scoreHist.setTitle("IsolationForest — score distribution (last 90d)");
        int width = 1200;
        int height = 800;
        BufferedImage img = scoreHist.toBufferedImage(width, height);
        File out = new File("/Users/motaz/Work/my-projects/anomaly-model-training//if-score-hist.png");
        ImageIO.write(img, "png", out);
        log.info("Saved histogram to {}", out.getAbsolutePath());


        // ===== SCATTER: amountZScore vs timeSegmentRatio =====
        double[] xNArr = xN.stream().mapToDouble(d->d).toArray();
        double[] yNArr = yN.stream().mapToDouble(d->d).toArray();
        double[] xAArr = xA.stream().mapToDouble(d->d).toArray();
        double[] yAArr = yA.stream().mapToDouble(d->d).toArray();
//        ScatterPlot.of(xNArr, yNArr, 'o', Color.decode("#4C78A8")).figure();
//
//        PlotCanvas canvas = ScatterPlot.of(xNArr, yNArr, 'o', Color.decode("#4C78A8")).canvas();
//        canvas.add(ScatterPlot.of(xAArr, yAArr, 'x', Color.decode("#F58518")));
//        canvas.setAxisLabels("amountZScore", "timeSegmentRatio");
//        canvas.setTitle("Normal (blue) vs Anomaly (orange) @ thr=" + threshold);
//        canvas.window();


    }

    private IsolationForest loadLatestModel() throws IOException, ClassNotFoundException {
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
