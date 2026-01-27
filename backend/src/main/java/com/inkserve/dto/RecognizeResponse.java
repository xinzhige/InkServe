package com.inkserve.dto;

import java.util.List;

public class RecognizeResponse {
    private String prediction;
    private List<Score> topScores;
    private String normalizedImageBase64;

    public RecognizeResponse(String prediction, List<Score> topScores, String normalizedImageBase64) {
        this.prediction = prediction;
        this.topScores = topScores;
        this.normalizedImageBase64 = normalizedImageBase64;
    }

    public String getPrediction() {
        return prediction;
    }

    public List<Score> getTopScores() {
        return topScores;
    }

    public String getNormalizedImageBase64() {
        return normalizedImageBase64;
    }

    public static class Score {
        private final String label;
        private final double probability;

        public Score(String label, double probability) {
            this.label = label;
            this.probability = probability;
        }

        public String getLabel() {
            return label;
        }

        public double getProbability() {
            return probability;
        }
    }
}
