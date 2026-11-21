package com.paperwallah.model;
public record TagRule(int id, String phrase, String normalized, int tagId, MatchMode mode, boolean enabled, int priority) {}
