package com.paperwallah.model;
/** Why a tag matched: the rule, the text found, where, and the character span. */
public record TagMatch(int tagId, int ruleId, String phrase, String where, int start, int end, double confidence) {}
