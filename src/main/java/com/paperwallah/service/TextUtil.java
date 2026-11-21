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
    public static String normalize(String s) { return tokenize(s).stream().map(Token::text).collect(Collectors.joining(" ")); }
}
