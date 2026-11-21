package org.app.bytecode;

public class Instruction {
    public Op op;
    public int arg;
    public int arg2; // NEW: второй аргумент (для CALL — количество параметров)

    public Instruction(Op op, int arg) {
        this.op = op;
        this.arg = arg;
    }

    // NEW
    public Instruction(Op op, int arg, int arg2) {
        this.op = op;
        this.arg = arg;
        this.arg2 = arg2;
    }

    @Override
    public String toString() {
        if (op == Op.CALL) {
            return String.format("%-15s body=%d argc=%d", op, arg, arg2);
        }
        return arg != -1 ? String.format("%-15s %d", op, arg) : op.toString();
    }
}