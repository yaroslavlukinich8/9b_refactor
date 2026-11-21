package org.app.vm;

import org.app.bytecode.Instruction;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class VirtualMachine {
    private static final int FRAME_SIZE = 32;   // FIX: ширина фрейма функции

    private final List<Instruction> code;
    private final int[] memory = new int[4096];  // FIX: больше памяти под глубокую рекурсию
    private final Deque<Integer> stack = new ArrayDeque<>();
    private int ip = 0;

    // Стек вызовов и фреймы
    private final Deque<Integer> returnStack = new ArrayDeque<>();
    private final Deque<Integer> frameStack = new ArrayDeque<>();
    private int fp = 0;          // frame pointer (индекс в memory)

    public VirtualMachine(List<Instruction> code) {
        this.code = code;
    }

    public void run() {
        while (ip < code.size()) {
            Instruction ins = code.get(ip++);
            switch (ins.op) {
                case PUSH: stack.push(ins.arg); break;
                case LOAD: stack.push(memory[fp + ins.arg]); break;
                case STORE: memory[fp + ins.arg] = stack.pop(); break;
                case ADD: { int b = stack.pop(), a = stack.pop(); stack.push(a + b); break; }
                case SUB: { int b = stack.pop(), a = stack.pop(); stack.push(a - b); break; }
                case MUL: { int b = stack.pop(), a = stack.pop(); stack.push(a * b); break; }
                case DIV: {
                    int b = stack.pop(), a = stack.pop();
                    if (b == 0) throw new RuntimeException("Деление на ноль");
                    stack.push(a / b);
                    break;
                }
                case POW: {
                    int b = stack.pop(), a = stack.pop();
                    if (b < 0) throw new RuntimeException("Отрицательная степень: " + b);
                    int r = 1;
                    for (int i = 0; i < b; i++) r *= a;
                    stack.push(r);
                    break;
                }
                case FACT: {
                    int n = stack.pop();
                    if (n < 0) throw new RuntimeException("Факториал отрицательного числа: " + n);
                    int r = 1;
                    for (int i = 2; i <= n; i++) r *= i;
                    stack.push(r);
                    break;
                }
                case LT: { int b = stack.pop(), a = stack.pop(); stack.push(a < b ? 1 : 0); break; }
                case GT: { int b = stack.pop(), a = stack.pop(); stack.push(a > b ? 1 : 0); break; }
                case LE: { int b = stack.pop(), a = stack.pop(); stack.push(a <= b ? 1 : 0); break; }
                case GE: { int b = stack.pop(), a = stack.pop(); stack.push(a >= b ? 1 : 0); break; }
                case EQ: { int b = stack.pop(), a = stack.pop(); stack.push(a == b ? 1 : 0); break; }
                case NE: { int b = stack.pop(), a = stack.pop(); stack.push(a != b ? 1 : 0); break; }
                case DUP: stack.push(stack.peek()); break;
                case POP: stack.pop(); break;
                case JMP_TRUE_KEEP: if (stack.peek() != 0) ip = ins.arg; break;
                case JMP_FALSE_KEEP: if (stack.peek() == 0) ip = ins.arg; break;
                case PRINT: System.out.println(stack.pop()); break;
                case JMP: ip = ins.arg; break;
                case JMP_FALSE: if (stack.pop() == 0) ip = ins.arg; break;
                case HALT: return;

                // FIX: новый фрейм на FRAME_SIZE ячеек; аргументы кладём в
                // memory[fpNew + 0 .. fpNew + argc), туда же потом смотрит LOAD/STORE.
                case CALL: {
                    int argc = ins.arg2;
                    if (argc > FRAME_SIZE) {
                        throw new RuntimeException("Слишком много аргументов: " + argc);
                    }
                    int fpNew = fp + FRAME_SIZE;
                    // аргументы лежат на стеке в обратном порядке (последний сверху)
                    for (int i = argc - 1; i >= 0; i--) {
                        memory[fpNew + i] = stack.pop();
                    }
                    returnStack.push(ip);
                    frameStack.push(fp);
                    fp = fpNew;
                    ip = ins.arg;
                    break;
                }
                case RET: {
                    int result = stack.pop();
                    ip = returnStack.pop();
                    fp = frameStack.pop();
                    stack.push(result);
                    break;
                }
                case RET_VOID: {
                    ip = returnStack.pop();
                    fp = frameStack.pop();
                    break;
                }
            }
        }
    }
}