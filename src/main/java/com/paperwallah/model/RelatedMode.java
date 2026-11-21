package com.paperwallah.model;
public enum RelatedMode {
    SAME_CONCEPT("Same concept"), ALTERNATIVE("Alternative wording"), EASIER("Easier version"), HARDER("Harder version"), COMPLEMENTARY("Same topic, different tags");
    private final String label; RelatedMode(String l) { label = l; }
    @Override public String toString() { return label; }
}
