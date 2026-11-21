package org.app.compiler;

import org.app.bytecode.Instruction;
import org.app.bytecode.Op;

import java.util.*;

public class SimpleBytecodeCompiler {
    private final String src;
    public SimpleBytecodeCompiler(String src) { this.src = src; }

    public List<Instruction> compile() {
        List<Instruction> code = new ArrayList<>();
        Map<String, Integer> varIndices = new HashMap<>();
        Lexer lex = new Lexer(src);
        Parser parser = new Parser(lex, code, varIndices);
        parser.parseProgram();
        code.add(new Instruction(Op.HALT, -1));
        return code;
    }
}