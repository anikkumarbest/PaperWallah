package com.paperwallah.model;
import java.util.Set;
/** The question we look for neighbours of. id is 0 for a question that is not saved yet. */
public record RelatedProbe(int id, String text, String answer, String topic, int marks, Difficulty difficulty, Set<String> tags) {}
