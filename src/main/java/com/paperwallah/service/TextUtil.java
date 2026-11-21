package com.paperwallah.service;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;
/** Offline text normalisation: lower-case words, punctuation treated as a separator. */
public final class TextUtil {
    private TextUtil() {}
    public record Token(String text, int start, int end) {}
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}_#+]+");
    public static List<Token> tokenize(String s) {
        List<Token> out = new ArrayList<>(); Matcher m = WORD.matcher(s == null ? "" : s.toLowerCase());
        while (m.find()) out.add(new Token(m.group(), m.start(), m.end()));
        return out;
    }
    private static final Set<String> STOP = Set.of("a","an","the","and","or","of","to","in","on","for","with","is","are","was","were","be","by","as","at","it","this","that","these","those","what","which","who","how","why","when","explain","define","describe","write","state","give","list","briefly","example","examples","using","use","used","can","do","does","not","from","into","between","about","each","its","their","also","any","all","if","then","than","so","such","one","two","you","your","we","our");
    /** Very light stemming so that queue/queues and sort/sorting compare equal. */
    public static String stem(String w) {
        if (w.length() > 6 && w.endsWith("ing")) return w.substring(0, w.length() - 3);
        if (w.length() > 5 && w.endsWith("ed")) return w.substring(0, w.length() - 2);
        if (w.length() > 5 && w.endsWith("sses")) return w.substring(0, w.length() - 2);
        if (w.length() > 4 && w.endsWith("ies")) return w.substring(0, w.length() - 3) + "y";
        if (w.length() > 3 && w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("us") && !w.endsWith("is")) return w.substring(0, w.length() - 1);
        return w;
    }
    /** Meaningful terms: lower-case, stop words and tiny or numeric tokens removed, stemmed. */
    public static List<String> terms(String s) {
        List<String> out = new ArrayList<>();
        for (Token t : tokenize(s)) { String w = t.text(); if (w.length() < 2 || STOP.contains(w) || w.chars().allMatch(Character::isDigit)) continue; out.add(stem(w)); }
        return out;
    }
    public static String normalize(String s) { return tokenize(s).stream().map(Token::text).collect(Collectors.joining(" ")); }
}
