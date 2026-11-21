package org.app.bytecode;

public enum Op {
    PUSH, LOAD, STORE,
    ADD, SUB, MUL, DIV,
    POW, FACT,
    LT, GT, LE, GE, EQ, NE,
    DUP, POP,
    JMP_TRUE_KEEP, JMP_FALSE_KEEP,
    PRINT, JMP, JMP_FALSE, HALT,
    // NEW: вызовы функций
    CALL,       // arg = адрес тела, arg2 = число аргументов
    RET,        // возврат со значением (значение на стеке)
    RET_VOID    // возврат без значения
}