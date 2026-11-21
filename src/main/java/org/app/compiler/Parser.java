package org.app.compiler;

import org.app.bytecode.Instruction;
import org.app.bytecode.Op;

import java.util.*;

public class Parser {
    // Глобальные переменные размещаем со смещением, чтобы не пересекаться с параметрами функций
    private static final int GLOBALS_BASE = 512;

    private final Lexer lex;
    private final List<Instruction> code;
    private final Map<String, Integer> varIndices;
    private int nextGlobalIndex = GLOBALS_BASE;

    private static class FuncInfo {
        final String name;
        final List<String> params;
        int bodyAddress = -1;
        boolean hasReturn = false;
        FuncInfo(String name, List<String> params) { this.name = name; this.params = params; }
    }
    private final Map<String, FuncInfo> functions = new HashMap<>();
    private final Deque<FuncInfo> functionStack = new ArrayDeque<>();

    public Parser(Lexer lex, List<Instruction> code, Map<String, Integer> varIndices) {
        this.lex = lex;
        this.code = code;
        this.varIndices = varIndices;
    }

    public void parseProgram() {
        lex.skipWhitespace();
        while (!lex.isEof()) {
            parseStatement();
            lex.skipWhitespace();
        }
    }

    // ------------------ STATEMENTS ------------------

    private void parseStatement() {
        lex.skipWhitespace();
        if (lex.isEof()) return;

        // FIX: ключевое слово func (4 символа)
        if (lex.startsWith("func")) {
            parseFunctionDecl();
            return;
        }
        if (lex.startsWith("return")) {
            parseReturn();
            return;
        }

        if (lex.startsWith("int")) {
            lex.advance(3);
            do {
                lex.skipWhitespace();
                String name = lex.parseIdentifier();
                int varIndex = nextGlobalIndex++;
                varIndices.put(name, varIndex);

                lex.skipWhitespace();
                if (lex.peek() == '=') {
                    lex.advance(1);
                    parseExpression();
                } else {
                    code.add(new Instruction(Op.PUSH, 0));
                }
                code.add(new Instruction(Op.STORE, varIndex));
                lex.skipWhitespace();
            } while (lex.match(','));
            lex.expect(';');
        }
        else if (lex.startsWith("print")) {
            lex.advance(5);
            parseExpression();
            code.add(new Instruction(Op.PRINT, -1));
            lex.expect(';');
        }
        else if (lex.startsWith("if")) {
            lex.advance(2);
            parseExpression();

            int jumpFalseIndex = code.size();
            code.add(new Instruction(Op.JMP_FALSE, -1));

            executeBlockContent();
            lex.skipWhitespace();

            if (lex.startsWith("else")) {
                lex.advance(4);
                int jumpElseIndex = code.size();
                code.add(new Instruction(Op.JMP, -1));

                code.get(jumpFalseIndex).arg = code.size();
                executeBlockContent();
                code.get(jumpElseIndex).arg = code.size();
            } else {
                code.get(jumpFalseIndex).arg = code.size();
            }
        }
        else if (lex.startsWith("while")) {
            lex.advance(5);
            int loopStart = code.size();
            parseExpression();
            int jumpExitIndex = code.size();
            code.add(new Instruction(Op.JMP_FALSE, -1));
            executeBlockContent();
            code.add(new Instruction(Op.JMP, loopStart));
            code.get(jumpExitIndex).arg = code.size();
        }
        else {
            String name = lex.parseIdentifier();
            lex.skipWhitespace();

            if (lex.peek() == '(') {
                // вызов как statement — POP только для возвращающих функций
                FuncInfo f = functions.get(name);
                if (f == null) throw new RuntimeException("Неизвестная функция: " + name);
                emitCallByName(name);
                if (f.hasReturn) {
                    code.add(new Instruction(Op.POP, -1));
                }
                lex.expect(';');
                return;
            }

            Integer varIndex = varIndices.get(name);
            if (varIndex == null) throw new RuntimeException("Неизвестная переменная: " + name);
            lex.expect('=');
            parseExpression();
            code.add(new Instruction(Op.STORE, varIndex));
            lex.expect(';');
        }
    }

    private void parseFunctionDecl() {
        lex.advance(4); // FIX: func
        String name = lex.parseIdentifier();
        lex.expect('(');
        List<String> params = new ArrayList<>();
        if (lex.peek() != ')') {
            do { params.add(lex.parseIdentifier()); } while (lex.match(','));
        }
        lex.expect(')');

        if (functions.containsKey(name)) {
            throw new RuntimeException("Функция уже объявлена: " + name);
        }
        FuncInfo info = new FuncInfo(name, params);
        functions.put(name, info);

        int jumpOverBody = code.size();
        code.add(new Instruction(Op.JMP, -1));

        info.bodyAddress = code.size();

        // параметры — локальные индексы 0..N-1 (в новом фрейме)
        for (int i = 0; i < params.size(); i++) {
            varIndices.put(params.get(i), i);
        }
        functionStack.push(info);

        lex.expect('{');
        lex.skipWhitespace();
        while (!lex.isEof() && lex.peek() != '}') {
            parseStatement();
            lex.skipWhitespace();
        }
        lex.expect('}');

        functionStack.pop();

        // неявный возврат: RET (с PUSH 0) для возвращающих, RET_VOID для void
        if (info.hasReturn) {
            code.add(new Instruction(Op.PUSH, 0));
            code.add(new Instruction(Op.RET, -1));
        } else {
            code.add(new Instruction(Op.RET_VOID, -1));
        }
        code.get(jumpOverBody).arg = code.size();

        // Параметры локальны — убираем из таблицы
        for (String p : params) varIndices.remove(p);
    }

    private void parseReturn() {
        if (functionStack.isEmpty()) {
            throw new RuntimeException("return вне функции");
        }
        lex.advance(6); // return
        if (lex.peek() == ';') {
            code.add(new Instruction(Op.RET_VOID, -1));
        } else {
            parseExpression();
            code.add(new Instruction(Op.RET, -1));
            functionStack.peek().hasReturn = true;
        }
        lex.expect(';');
    }

    private void emitCallByName(String name) {
        FuncInfo f = functions.get(name);
        if (f == null) throw new RuntimeException("Неизвестная функция: " + name);
        lex.expect('(');
        int argc = 0;
        if (lex.peek() != ')') {
            do {
                parseExpression();
                argc++;
            } while (lex.match(','));
        }
        lex.expect(')');
        if (argc != f.params.size()) {
            throw new RuntimeException(
                    "Несовпадение числа аргументов при вызове " + name +
                            ": ожидалось " + f.params.size() + ", передано " + argc);
        }
        code.add(new Instruction(Op.CALL, f.bodyAddress, argc));
    }

    // ------------------ BLOCKS ------------------

    private void executeBlockContent() {
        lex.skipWhitespace();
        if (lex.peek() == '{') lex.advance(1);
        lex.skipWhitespace();
        while (!lex.isEof() && lex.peek() != '}') {
            parseStatement();
            lex.skipWhitespace();
        }
        if (lex.peek() == '}') lex.advance(1);
    }

    // ------------------ EXPRESSIONS ------------------

    private void parseExpression() {
        parseLogicalAnd();
        lex.skipWhitespace();
        while (lex.startsWith("||")) {
            lex.advance(2);
            code.add(new Instruction(Op.DUP, -1));
            int jumpTrue = code.size();
            code.add(new Instruction(Op.JMP_TRUE_KEEP, -1));
            code.add(new Instruction(Op.POP, -1));
            parseLogicalAnd();
            code.get(jumpTrue).arg = code.size();
            lex.skipWhitespace();
        }
    }

    private void parseLogicalAnd() {
        parseEquality();
        lex.skipWhitespace();
        while (lex.startsWith("&&")) {
            lex.advance(2);
            code.add(new Instruction(Op.DUP, -1));
            int jumpFalse = code.size();
            code.add(new Instruction(Op.JMP_FALSE_KEEP, -1));
            code.add(new Instruction(Op.POP, -1));
            parseEquality();
            code.get(jumpFalse).arg = code.size();
            lex.skipWhitespace();
        }
    }

    private void parseEquality() {
        parseRelational();
        lex.skipWhitespace();
        while (true) {
            if (lex.startsWith("==")) { lex.advance(2); parseRelational(); code.add(new Instruction(Op.EQ, -1)); }
            else if (lex.startsWith("!=")) { lex.advance(2); parseRelational(); code.add(new Instruction(Op.NE, -1)); }
            else if (lex.startsWith("<>")) { lex.advance(2); parseRelational(); code.add(new Instruction(Op.NE, -1)); }
            else break;
            lex.skipWhitespace();
        }
    }

    private void parseRelational() {
        parseAdditive();
        lex.skipWhitespace();
        while (true) {
            if (lex.startsWith("<=")) { lex.advance(2); parseAdditive(); code.add(new Instruction(Op.LE, -1)); }
            else if (lex.startsWith(">=")) { lex.advance(2); parseAdditive(); code.add(new Instruction(Op.GE, -1)); }
            else if (lex.startsWith("==") || lex.startsWith("!=") || lex.startsWith("<>")) break;
            else if (lex.match('<')) { parseAdditive(); code.add(new Instruction(Op.LT, -1)); }
            else if (lex.match('>')) { parseAdditive(); code.add(new Instruction(Op.GT, -1)); }
            else break;
            lex.skipWhitespace();
        }
    }

    private void parseAdditive() {
        parseMultiplicative();
        lex.skipWhitespace();
        while (true) {
            if (lex.match('+')) { parseMultiplicative(); code.add(new Instruction(Op.ADD, -1)); }
            else if (lex.match('-')) { parseMultiplicative(); code.add(new Instruction(Op.SUB, -1)); }
            else break;
            lex.skipWhitespace();
        }
    }

    private void parseMultiplicative() {
        parsePower();
        lex.skipWhitespace();
        while (true) {
            if (lex.startsWith("**")) break;
            if (lex.match('*')) { parsePower(); code.add(new Instruction(Op.MUL, -1)); }
            else if (lex.match('/')) { parsePower(); code.add(new Instruction(Op.DIV, -1)); }
            else break;
            lex.skipWhitespace();
        }
    }

    private void parsePower() {
        parseFactorial();
        lex.skipWhitespace();
        if (lex.startsWith("**")) {
            lex.advance(2);
            parsePower();
            code.add(new Instruction(Op.POW, -1));
        }
    }

    private void parseFactorial() {
        parseTerm();
        lex.skipWhitespace();
        while (!lex.isEof() && lex.peek() == '!') {
            if (lex.charAtRelative(1) == '=') break;
            lex.advance(1);
            code.add(new Instruction(Op.FACT, -1));
            lex.skipWhitespace();
        }
    }

    private void parseTerm() {
        lex.skipWhitespace();
        if (lex.match('(')) {
            parseExpression();
            lex.expect(')');
            return;
        }

        char c = lex.peek();
        if (Character.isDigit(c)) {
            int val = 0;
            while (!lex.isEof() && Character.isDigit(lex.peek())) {
                val = val * 10 + (lex.peek() - '0');
                lex.advance(1);
            }
            code.add(new Instruction(Op.PUSH, val));
        } else if (Character.isLetter(c) || c == '_') {
            String name = lex.parseIdentifier();
            lex.skipWhitespace();

            if (lex.peek() == '(') {
                // вызов в выражении — void-функция недопустима
                FuncInfo f = functions.get(name);
                if (f == null) throw new RuntimeException("Неизвестная функция: " + name);
                if (!f.hasReturn) {
                    throw new RuntimeException(
                            "Функция " + name + " не возвращает значение и не может быть в выражении");
                }
                emitCallByName(name);
                return;
            }

            Integer varIndex = varIndices.get(name);
            if (varIndex == null) {
                throw new RuntimeException("Переменная не найдена: " + name);
            }
            code.add(new Instruction(Op.LOAD, varIndex));
        } else {
            throw new RuntimeException("Неожиданный символ в выражении: '" + c +
                    "' на позиции " + lex.getPos());
        }
    }
}