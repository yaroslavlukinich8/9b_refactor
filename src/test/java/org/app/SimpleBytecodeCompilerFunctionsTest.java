package org.app;

import org.app.bytecode.Instruction;
import org.app.bytecode.Op;
import org.app.compiler.SimpleBytecodeCompiler;
import org.app.vm.VirtualMachine;
import org.junit.jupiter.api.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SimpleBytecodeCompilerFunctionsTest {

    private ByteArrayOutputStream out;
    private PrintStream oldOut;

    @BeforeEach
    void capture() {
        oldOut = System.out;
        out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));
    }

    @AfterEach
    void restore() { System.setOut(oldOut); }

    private List<String> run(String src) {
        new VirtualMachine(new SimpleBytecodeCompiler(src).compile()).run();
        return out.toString().lines().toList();
    }

    private List<Instruction> compile(String src) {
        return new SimpleBytecodeCompiler(src).compile();
    }

    private int indexOf(List<Instruction> code, Op op) {
        for (int i = 0; i < code.size(); i++) if (code.get(i).op == op) return i;
        return -1;
    }

    @Nested
    @DisplayName("Объявление и вызов функций")
    class Declaration {
        @Test
        @DisplayName("Простая функция без параметров")
        void noParams() {
            assertEquals(List.of("42"), run("""
                    func answer() { return 42; }
                    print answer();
                    """));
        }

        @Test
        @DisplayName("Функция с одним параметром")
        void oneParam() {
            assertEquals(List.of("25"), run("""
                    func square(x) { return x * x; }
                    print square(5);
                    """));
        }

        @Test
        @DisplayName("Функция с несколькими параметрами")
        void manyParams() {
            assertEquals(List.of("12"), run("""
                    func add3(a, b, c) { return a + b + c; }
                    print add3(3, 4, 5);
                    """));
        }

        @Test
        @DisplayName("Вложенные вызовы")
        void nestedCalls() {
            assertEquals(List.of("81"), run("""
                    func square(x) { return x * x; }
                    print square(square(3));
                    """));
        }

        @Test
        @DisplayName("Функция без return (void)")
        void voidFunction() {
            assertEquals(List.of("7"), run("""
                    func noop() { }
                    int x = 7;
                    noop();
                    print x;
                    """));
        }

        @Test
        @DisplayName("Генерируются CALL и RET")
        void emitsCallAndRet() {
            List<Instruction> code = compile("""
                    func f(x) { return x + 1; }
                    print f(2);
                    """);
            assertTrue(indexOf(code, Op.CALL) >= 0);
            assertTrue(indexOf(code, Op.RET) >= 0);
            assertTrue(indexOf(code, Op.JMP) >= 0, "Нужен JMP через тело функции");
        }

        @Test
        @DisplayName("Тело функции не исполняется при обычном ходе программы")
        void bodySkippedAtTopLevel() {
            assertEquals(List.of("1"), run("""
                    func boom() { print 999; return 0; }
                    print 1;
                    """));
        }

        @Test
        @DisplayName("Вызов функции как statement без return")
        void callAsStatement() {
            assertEquals(List.of("1"), run("""
                    func noop() { }
                    noop();
                    print 1;
                    """));
        }
    }

    @Nested
    @DisplayName("Рекурсия")
    class Recursion {
        @Test
        @DisplayName("Факториал через рекурсию")
        void recursiveFactorial() {
            assertEquals(List.of("120"), run("""
                    func fact(n) {
                        if n <= 1 { return 1; }
                        return n * fact(n - 1);
                    }
                    print fact(5);
                    """));
        }

        @Test
        @DisplayName("Числа Фибоначчи")
        void fibonacci() {
            assertEquals(List.of("832040"), run("""
                    func fib(n) {
                        if n < 2 { return n; }
                        return fib(n - 1) + fib(n - 2);
                    }
                    print fib(40);
                    """));
        }

        @Test
        @DisplayName("Числа Фибоначчи")
        void fibonacci_loop() {
            assertEquals(List.of("832040"), run("""
                    func fib(n) {
                        int a = 0, b = 1, k = 1;
                        
                        while  k < n {
                            b = a + b;
                            a = b - a;
                            k = k + 1;
                        }
                      
                        return b;
                    }
                        print fib(100);
                    """));
        }

        @Test
        @DisplayName("Сумма через рекурсию")
        void recursiveSum() {
            assertEquals(List.of("15"), run("""
                    func sum(n) {
                        if n == 0 { return 0; }
                        return n + sum(n - 1);
                    }
                    print sum(5);
                    """));
        }
    }

    @Nested
    @DisplayName("Локальные переменные и область видимости")
    class Locals {
        @Test
        @DisplayName("Локальная переменная внутри функции")
        void localVar() {
            assertEquals(List.of("9"), run("""
                    func f(x) { int y = x + 4; return y; }
                    print f(5);
                    """));
        }

        @Test
        @DisplayName("Параметры не затирают глобальные переменные")
        void paramsShadowSafe() {
            assertEquals(List.of("10", "3"), run("""
                    func inc(x) { return x + 1; }
                    int a = 10;
                    int b = inc(2);
                    print a;
                    print b;
                    """));
        }
    }

    @Nested
    @DisplayName("Ошибки")
    class Errors {
        @Test
        @DisplayName("Вызов неизвестной функции")
        void unknownFunction() {
            assertThrows(RuntimeException.class, () ->
                    run("print missing(1);"));
        }

        @Test
        @DisplayName("Несовпадение числа аргументов")
        void wrongArgc() {
            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                    run("""
                            func f(x) { return x; }
                            print f(1, 2);
                            """));
            assertTrue(ex.getMessage().contains("Несовпадение числа аргументов"));
        }

        @Test
        @DisplayName("return вне функции")
        void returnOutsideFunction() {
            assertThrows(RuntimeException.class, () ->
                    run("return 1;"));
        }
    }

    @Nested
    @DisplayName("Проверка байт-кода")
    class BytecodeChecks {
        @Test
        @DisplayName("CALL содержит правильный argc")
        void callHasArgc() {
            List<Instruction> code = compile("""
                    func add3(a, b, c) { return a + b + c; }
                    print add3(1, 2, 3);
                    """);
            int callIdx = indexOf(code, Op.CALL);
            assertTrue(callIdx >= 0);
            assertEquals(3, code.get(callIdx).arg2);
        }

        @Test
        @DisplayName("Тело функции предварено JMP")
        void bodyPrefixedByJump() {
            List<Instruction> code = compile("""
                    func f() { return 1; }
                    print f();
                    """);
            int jmpIdx = indexOf(code, Op.JMP);
            assertTrue(jmpIdx >= 0);
            int target = code.get(jmpIdx).arg;
            assertTrue(target > jmpIdx);
            assertTrue(target <= code.size());
        }
    }
}