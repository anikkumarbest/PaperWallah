package com.paperwallah.model;
/** Points available per signal (default total 100) and the penalties. The score is a transparent weighted sum, not an AI probability. */
public record RelatedWeights(double tags, double topic, double keywords, double text, double bloom, double marksPenalty, double unrelatedPenalty, double minScore) {
    public static RelatedWeights defaults() { return new RelatedWeights(50, 20, 15, 10, 5, 3, 30, 10); }
}
