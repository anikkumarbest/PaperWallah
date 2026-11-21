package com.paperwallah.model;
import java.util.List;
/** related = useful alternatives; duplicates are flagged separately and never recommended. */
public record RelatedResult(List<RelatedSuggestion> related, List<RelatedSuggestion> duplicates) {}
