package com.java2nb.novel.service.search;

import com.java2nb.novel.vo.BookVO;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Xác nhận và xếp hạng ứng viên sửa lỗi chính tả sau khi MySQL ngram đã thu hẹp tập tìm kiếm.
 */
public final class VietnameseSearchMatcher {

    private VietnameseSearchMatcher() {
    }

    public static List<BookVO> rank(String keyword, List<BookVO> candidates) {
        String query = normalize(keyword);
        if (query.isEmpty() || candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        int maximumDistance = maximumDistance(query);
        List<RankedBook> ranked = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            BookVO book = candidates.get(index);
            MatchScore title = score(query, book.getBookName());
            MatchScore author = score(query, book.getAuthorName());
            MatchScore best = MatchScore.BEST_ORDER.compare(title, author) <= 0 ? title : author;
            if (best.distance() <= maximumDistance) {
                ranked.add(new RankedBook(book, best.distance(), best.fullLengthGap(), index));
            }
        }

        ranked.sort(Comparator.comparingInt(RankedBook::distance)
            .thenComparingInt(RankedBook::fullLengthGap)
            .thenComparingInt(RankedBook::sourceOrder));
        return ranked.stream().map(RankedBook::book).toList();
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
            .toLowerCase(Locale.ROOT)
            .replace('đ', 'd');
        StringBuilder normalized = new StringBuilder(decomposed.length());
        boolean previousSpace = true;
        for (int offset = 0; offset < decomposed.length();) {
            int codePoint = decomposed.codePointAt(offset);
            offset += Character.charCount(codePoint);
            int type = Character.getType(codePoint);
            if (type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK) {
                continue;
            }
            if (Character.isLetterOrDigit(codePoint)) {
                normalized.appendCodePoint(codePoint);
                previousSpace = false;
            } else if (!previousSpace) {
                normalized.append(' ');
                previousSpace = true;
            }
        }
        int length = normalized.length();
        if (length > 0 && normalized.charAt(length - 1) == ' ') {
            normalized.setLength(length - 1);
        }
        return normalized.toString();
    }

    private static MatchScore score(String query, String targetValue) {
        String target = normalize(targetValue);
        if (target.isEmpty()) {
            return MatchScore.NO_MATCH;
        }

        String[] queryTokens = query.split(" ");
        String[] targetTokens = target.split(" ");
        int minimumWindow = Math.max(1, queryTokens.length - 1);
        int maximumWindow = Math.min(targetTokens.length, queryTokens.length + 1);
        if (minimumWindow > maximumWindow) {
            minimumWindow = maximumWindow;
        }

        int bestDistance = Integer.MAX_VALUE;
        for (int window = minimumWindow; window <= maximumWindow; window++) {
            for (int start = 0; start + window <= targetTokens.length; start++) {
                String candidate = String.join(" ", java.util.Arrays.copyOfRange(targetTokens, start, start + window));
                bestDistance = Math.min(bestDistance, damerauLevenshtein(query, candidate));
            }
        }
        return new MatchScore(bestDistance, Math.abs(target.length() - query.length()));
    }

    private static int maximumDistance(String query) {
        int letters = query.replace(" ", "").length();
        if (letters <= 4) {
            return 1;
        }
        if (letters <= 8) {
            return 2;
        }
        if (letters <= 16) {
            return 3;
        }
        return Math.min(6, Math.max(3, (int) Math.ceil(letters * 0.2)));
    }

    private static int damerauLevenshtein(String left, String right) {
        int[][] distance = new int[left.length() + 1][right.length() + 1];
        for (int i = 0; i <= left.length(); i++) {
            distance[i][0] = i;
        }
        for (int j = 0; j <= right.length(); j++) {
            distance[0][j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            for (int j = 1; j <= right.length(); j++) {
                int substitutionCost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                distance[i][j] = Math.min(
                    Math.min(distance[i - 1][j] + 1, distance[i][j - 1] + 1),
                    distance[i - 1][j - 1] + substitutionCost
                );
                if (i > 1 && j > 1
                    && left.charAt(i - 1) == right.charAt(j - 2)
                    && left.charAt(i - 2) == right.charAt(j - 1)) {
                    distance[i][j] = Math.min(distance[i][j], distance[i - 2][j - 2] + 1);
                }
            }
        }
        return distance[left.length()][right.length()];
    }

    private record RankedBook(BookVO book, int distance, int fullLengthGap, int sourceOrder) {
    }

    private record MatchScore(int distance, int fullLengthGap) {
        private static final MatchScore NO_MATCH = new MatchScore(Integer.MAX_VALUE, Integer.MAX_VALUE);
        private static final Comparator<MatchScore> BEST_ORDER = Comparator.comparingInt(MatchScore::distance)
            .thenComparingInt(MatchScore::fullLengthGap);
    }
}

