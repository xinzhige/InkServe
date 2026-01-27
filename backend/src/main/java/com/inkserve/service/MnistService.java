package com.inkserve.service;

import ai.djl.Application;
import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDArrays;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;
import ai.djl.translate.Batchifier;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.TranslateException;
import ai.djl.MalformedModelException;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class MnistService {
    private final ZooModel<Image, Classifications> model;

    public MnistService() throws ModelNotFoundException, IOException, MalformedModelException {
        List<String> labels = IntStream.range(0, 10)
                .mapToObj(Integer::toString)
                .collect(Collectors.toList());

        Criteria<Image, Classifications> criteria = Criteria.builder()
                .optApplication(Application.CV.IMAGE_CLASSIFICATION)
                .setTypes(Image.class, Classifications.class)
                .optArtifactId("mlp")
                .optFilter("dataset", "mnist")
                .optTranslator(new MnistTranslator(labels))
                .optProgress(new ProgressBar())
                .build();

        this.model = ModelZoo.loadModel(criteria);
    }

    public Classifications predict(Image image) throws TranslateException {
        try (Predictor<Image, Classifications> predictor = model.newPredictor()) {
            return predictor.predict(image);
        }
    }

    public List<Classifications.Classification> topK(Classifications classifications, int k) {
        List<Classifications.Classification> items = new ArrayList<>(classifications.items());
        items.sort((a, b) -> Double.compare(b.getProbability(), a.getProbability()));
        return items.subList(0, Math.min(k, items.size()));
    }

    @PreDestroy
    public void close() {
        if (model != null) {
            model.close();
        }
    }

    public Image loadImage(byte[] bytes) throws IOException {
        return ImageFactory.getInstance().fromInputStream(new java.io.ByteArrayInputStream(bytes));
    }

    private static class MnistTranslator implements Translator<Image, Classifications> {
        private final List<String> labels;

        private MnistTranslator(List<String> labels) {
            this.labels = labels;
        }

        @Override
        public NDList processInput(TranslatorContext ctx, Image input) {
            Image resized = input;
            if (input.getWidth() != 28 || input.getHeight() != 28) {
                resized = input.resize(28, 28, true);
            }
            NDArray array = resized.toNDArray(ctx.getNDManager());
            Shape shape = array.getShape();
            // Convert to HWC float32 explicitly, then reduce to single channel.
            if (shape.dimension() == 2) {
                array = array.expandDims(2);
                shape = array.getShape();
            }
            if (shape.dimension() == 3 && shape.get(2) != 1) {
                // HWC assumed by Image.toNDArray; take mean across channels.
                array = array.toType(DataType.FLOAT32, false).mean(new int[]{2});
                array = array.expandDims(2);
            } else if (shape.dimension() == 3 && shape.get(2) == 1) {
                array = array.toType(DataType.FLOAT32, false);
            } else {
                array = array.toType(DataType.FLOAT32, false);
            }
            // Now convert HWC -> CHW and normalize.
            array = array.transpose(2, 0, 1);
            array = array.div(255f);
            array = array.sub(0.5f).div(0.5f);
            array = array.expandDims(0); // NCHW
            return new NDList(array);
        }

        @Override
        public Classifications processOutput(TranslatorContext ctx, NDList list) {
            NDArray probabilities = list.singletonOrThrow();
            if (probabilities.getShape().dimension() > 1) {
                probabilities = probabilities.squeeze();
            }
            probabilities = probabilities.softmax(0);
            return new Classifications(labels, probabilities);
        }

        @Override
        public Batchifier getBatchifier() {
            return null;
        }
    }
}
