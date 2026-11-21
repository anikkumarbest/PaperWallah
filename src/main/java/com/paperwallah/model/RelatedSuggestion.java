package com.paperwallah.model;
import java.util.List;
public record RelatedSuggestion(Question question, double score, List<String> sharedTags, boolean sameTopic, double textSimilarity, List<String> sharedKeywords, List<String> reasons) {}
