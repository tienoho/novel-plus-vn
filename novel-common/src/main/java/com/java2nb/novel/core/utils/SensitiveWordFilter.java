package com.java2nb.novel.core.utils;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Lọc từ ngữ nhạy cảm dựa trên giải thuật DFA (Deterministic Finite Automaton)
 * Hỗ trợ chuẩn hóa dấu tiếng Việt và thay thế từ vi phạm.
 */
public class SensitiveWordFilter {

    // Các từ nhạy cảm mặc định để khởi tạo hệ thống
    private static final List<String> DEFAULT_SENSITIVE_WORDS = Arrays.asList(
        "đảo chính", "bạo động", "khiêu dâm", "đánh bạc", "lừa đảo",
        "phản động", "mại dâm", "thuốc phiện", "ma túy", "khủng bố"
    );

    private static final Pattern DIACRITICAL_MARKS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private static final SensitiveWordFilter INSTANCE = new SensitiveWordFilter();

    private final DfaNode root = new DfaNode();

    public SensitiveWordFilter() {
        addWords(DEFAULT_SENSITIVE_WORDS);
    }

    public static SensitiveWordFilter getInstance() {
        return INSTANCE;
    }

    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        String temp = Normalizer.normalize(input, Normalizer.Form.NFD);
        temp = DIACRITICAL_MARKS.matcher(temp).replaceAll("");
        return temp.replace('đ', 'd').replace('Đ', 'D').toLowerCase();
    }

    public synchronized void addWord(String word) {
        if (word == null || word.trim().isEmpty()) {
            return;
        }
        String cleanWord = word.trim().toLowerCase();
        insertToTrie(cleanWord, word.trim());
        String normalizedWord = normalize(cleanWord);
        if (!normalizedWord.equals(cleanWord)) {
            insertToTrie(normalizedWord, word.trim());
        }
        // Thêm key đã loại bỏ khoảng trắng & ký tự đặc biệt để matching không bị gián đoạn bởi space/punctuation
        String noSpaceNorm = removeNonAlphanumeric(normalizedWord);
        if (!noSpaceNorm.isEmpty()) {
            insertToTrie(noSpaceNorm, word.trim());
        }
    }

    private static String removeNonAlphanumeric(String str) {
        if (str == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public synchronized void addWords(Collection<String> words) {
        if (words != null) {
            for (String word : words) {
                addWord(word);
            }
        }
    }

    private void insertToTrie(String wordKey, String originalWord) {
        DfaNode current = root;
        for (int i = 0; i < wordKey.length(); i++) {
            char c = wordKey.charAt(i);
            current = current.children.computeIfAbsent(c, k -> new DfaNode());
        }
        current.isEnd = true;
        current.originalWord = originalWord;
    }

    public boolean containsSensitiveWord(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        return !getFoundWords(text).isEmpty();
    }

    public Set<String> getFoundWords(String text) {
        Set<String> found = new LinkedHashSet<>();
        if (text == null || text.trim().isEmpty()) {
            return found;
        }

        searchInText(text.toLowerCase(), found);
        String normalizedText = normalize(text);
        if (!normalizedText.equals(text.toLowerCase())) {
            searchInText(normalizedText, found);
        }

        return found;
    }

    private void searchInText(String searchText, Set<String> found) {
        int len = searchText.length();
        for (int i = 0; i < len; i++) {
            DfaNode current = root;
            for (int j = i; j < len; j++) {
                char c = searchText.charAt(j);
                DfaNode next = current.children.get(c);
                if (next == null && (Character.isWhitespace(c) || !Character.isLetterOrDigit(c))) {
                    if (current != root) {
                        continue;
                    } else {
                        break;
                    }
                }
                current = next;
                if (current == null) {
                    break;
                }
                if (current.isEnd) {
                    found.add(current.originalWord);
                }
            }
        }
    }

    public String filter(String text, String replacement) {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }
        String rep = (replacement != null) ? replacement : "***";
        List<int[]> spans = findSensitiveSpans(text);
        if (spans.isEmpty()) {
            return text;
        }
        List<int[]> mergedSpans = mergeSpans(spans);
        StringBuilder sb = new StringBuilder();
        int lastIndex = 0;
        for (int[] span : mergedSpans) {
            sb.append(text, lastIndex, span[0]);
            sb.append(rep);
            lastIndex = span[1] + 1;
        }
        if (lastIndex < text.length()) {
            sb.append(text.substring(lastIndex));
        }
        return sb.toString();
    }

    public String replaceSensitiveWord(String text, char maskChar) {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }
        List<int[]> spans = findSensitiveSpans(text);
        if (spans.isEmpty()) {
            return text;
        }
        List<int[]> mergedSpans = mergeSpans(spans);
        char[] chars = text.toCharArray();
        for (int[] span : mergedSpans) {
            for (int k = span[0]; k <= span[1]; k++) {
                chars[k] = maskChar;
            }
        }
        return new String(chars);
    }

    public String replaceSensitiveWord(String text) {
        return replaceSensitiveWord(text, '*');
    }

    private List<int[]> findSensitiveSpans(String text) {
        List<int[]> spans = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return spans;
        }
        int len = text.length();
        char[] normChars = new char[len];
        for (int k = 0; k < len; k++) {
            String n = normalize(String.valueOf(text.charAt(k)));
            normChars[k] = !n.isEmpty() ? n.charAt(0) : Character.toLowerCase(text.charAt(k));
        }

        for (int i = 0; i < len; i++) {
            DfaNode current = root;
            for (int j = i; j < len; j++) {
                char c = normChars[j];
                DfaNode next = current.children.get(c);
                if (next == null && (Character.isWhitespace(c) || !Character.isLetterOrDigit(c))) {
                    if (current != root) {
                        continue;
                    } else {
                        break;
                    }
                }
                current = next;
                if (current == null) {
                    break;
                }
                if (current.isEnd) {
                    spans.add(new int[]{i, j});
                }
            }
        }
        return spans;
    }

    private List<int[]> mergeSpans(List<int[]> spans) {
        if (spans.size() <= 1) {
            return spans;
        }
        spans.sort(Comparator.comparingInt(a -> a[0]));
        List<int[]> merged = new ArrayList<>();
        int[] current = spans.get(0);
        for (int i = 1; i < spans.size(); i++) {
            int[] next = spans.get(i);
            if (next[0] <= current[1] + 1) {
                current[1] = Math.max(current[1], next[1]);
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

    private static class DfaNode {
        Map<Character, DfaNode> children = new HashMap<>();
        boolean isEnd = false;
        String originalWord;
    }
}
