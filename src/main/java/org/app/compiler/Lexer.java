package org.app.compiler;

public class Lexer {
    private final String src;
    private int pos = 0;

    public Lexer(String src) { this.src = src; }

    public boolean isEof() { return pos >= src.length(); }
    public int getPos() { return pos; }
    public void advance(int n) { pos += n; }

    public void skipWhitespace() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
    }

    public char peek() {
        skipWhitespace();
        return pos < src.length() ? src.charAt(pos) : '\0';
    }

    public char charAtRelative(int offset) {
        int p = pos + offset;
        return p >= 0 && p < src.length() ? src.charAt(p) : '\0';
    }

    public boolean startsWith(String prefix) {
        skipWhitespace();
        return src.startsWith(prefix, pos);
    }

    public boolean match(char expected) {
        skipWhitespace();
        if (pos < src.length() && src.charAt(pos) == expected) { pos++; return true; }
        return false;
    }

    public void expect(char expected) {
        skipWhitespace();
        if (pos >= src.length() || src.charAt(pos) != expected) {
            throw new RuntimeException("Ожидался символ: " + expected + ", но найден: " + peek());
        }
        pos++;
    }

    public String parseIdentifier() {
        skipWhitespace();
        if (pos >= src.length() ||
                !(Character.isLetter(src.charAt(pos)) || src.charAt(pos) == '_')) {
            throw new RuntimeException("Ожидался идентификатор на позиции " + pos +
                    ", но найден: '" + peek() + "'");
        }
        int start = pos;
        while (pos < src.length() &&
                (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_')) {
            pos++;
        }
        return src.substring(start, pos);
    }
}