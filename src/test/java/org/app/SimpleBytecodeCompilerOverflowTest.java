package org.app;

import org.app.bytecode.Instruction;
import org.app.compiler.SimpleBytecodeCompiler;
import org.app.vm.VirtualMachine;
import org.junit.jupiter.api.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Переполнение глубины стека вызовов")
class SimpleBytecodeCompilerStackOverflowTest {

    // memory = 4096, FRAME_SIZE = 32 → максимум 127 одновременных вызовов
    // (fp = 0, 32, ..., 4064), 128-й вызов даёт fpNew = 4096 → выход за границу.
    private static final int FRAME_SIZE = 32;
    private static final int MAX_DEPTH = 4096 / FRAME_SIZE - 1; // 127

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

    @Nested
    @DisplayName("Глубокая, но конечная рекурсия")
    class DeepButFinite {
        @Test
        @DisplayName("Глубина 100 помещается (fp доходит до 3200)")
        void depth100() {
            assertEquals(List.of("5050"), run("""
                    func sum(n) {
                        if n == 0 { return 0; }
                        return n + sum(n - 1);
                    }
                    print sum(100);
                    """));
        }

        @Test
        @DisplayName("Глубина 126 — на границе: 127 вызовов, fp от 0 до 4064")
        void depth126AtLimit() {
            // sum(126) → 127 вызовов (n = 126,125,...,0), ровно помещается
            int expected = 126 * 127 / 2; // 8001
            assertEquals(List.of(String.valueOf(expected)), run("""
                    func sum(n) {
                        if n == 0 { return 0; }
                        return n + sum(n - 1);
                    }
                    print sum(126);
                    """));
        }
    }

    @Nested
    @DisplayName("Переполнение памяти VM")
    class MemoryOverflow {
        @Test
        @DisplayName("Глубина 127 даёт 128 вызовов → fpNew = 4096 → ArrayIndexOutOfBounds")
        void depth127Overflows() {
            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                    run("""
                            func sum(n) {
                                if n == 0 { return 0; }
                                return n + sum(n - 1);
                            }
                            print sum(127);
                            """));
            assertTrue(
                    isArrayOob(ex),
                    "Ожидалось переполнение памяти (ArrayIndexOutOfBounds), получено: " + ex);
        }

        @Test
        @DisplayName("Глубина 200 гарантированно выходит за пределы")
        void depth200Overflows() {
            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                    run("""
                            func sum(n) {
                                if n == 0 { return 0; }
                                return n + sum(n - 1);
                            }
                            print sum(200);
                            """));
            assertTrue(isArrayOob(ex),
                    "Ожидалось ArrayIndexOutOfBounds, получено: " + ex);
        }

        @Test
        @DisplayName("Бесконечная рекурсия обязательно падает, а не зависает")
        void infiniteRecursionFails() {
            assertThrows(RuntimeException.class, () ->
                    run("""
                            func loop(n) {
                                return loop(n + 1);
                            }
                            print loop(0);
                            """));
        }

        private boolean isArrayOob(Throwable t) {
            for (Throwable cur = t; cur != null; cur = cur.getCause()) {
                if (cur instanceof ArrayIndexOutOfBoundsException) return true;
            }
            return false;
        }
    }

    @Nested
    @DisplayName("Слишком много аргументов")
    class TooManyArgs {
        @Test
        @DisplayName("argc > FRAME_SIZE (32) бросает RuntimeException")
        void argcExceedsFrame() {
            StringBuilder params = new StringBuilder();
            for (int i = 0; i < 33; i++) {
                if (i > 0) params.append(", ");
                params.append("p").append(i);
            }
            StringBuilder args = new StringBuilder();
            for (int i = 0; i < 33; i++) {
                if (i > 0) args.append(", ");
                args.append(i);
            }

            String src = "func big(" + params + ") { return p0; }\n" +
                    "print big(" + args + ");";

            RuntimeException ex = assertThrows(RuntimeException.class, () -> run(src));
            assertTrue(ex.getMessage().contains("Слишком много аргументов"),
                    "Ожидалось 'Слишком много аргументов', получено: " + ex.getMessage());
        }

        @Test
        @DisplayName("Ровно 32 аргумента — допустимо")
        void argcAtFrameSize() {
            StringBuilder params = new StringBuilder();
            for (int i = 0; i < 32; i++) {
                if (i > 0) params.append(", ");
                params.append("p").append(i);
            }
            StringBuilder args = new StringBuilder();
            for (int i = 0; i < 32; i++) {
                if (i > 0) args.append(", ");
                args.append(i);
            }

            String src = "func big(" + params + ") { return p0; }\n" +
                    "print big(" + args + ");";

            assertEquals(List.of("0"), run(src));
        }
    }

    @Nested
    @DisplayName("Многоуровневые вложенные вызовы (без рекурсии)")
    class NestedCallChain {
        @Test
        @DisplayName("Цепочка из 50 вложенных вызовов помещается")
        void chainOf50() {
            StringBuilder src = new StringBuilder();
            src.append("func f(n) { return n + 1; }\n");
            src.append("print ");
            for (int i = 0; i < 50; i++) src.append("f(");
            src.append("0");
            for (int i = 0; i < 50; i++) src.append(")");
            src.append(";\n");

            assertEquals(List.of("50"), run(src.toString()));
        }

        @Test
        @DisplayName("Цепочка из 126 вложенных вызовов помещается (ровно под лимит)")
        void chainOf126() {
            StringBuilder src = new StringBuilder();
            src.append("func f(n) { return n + 1; }\n");
            src.append("print ");
            for (int i = 0; i < MAX_DEPTH - 1; i++) src.append("f("); // 126
            src.append("0");
            for (int i = 0; i < MAX_DEPTH - 1; i++) src.append(")");
            src.append(";\n");

            assertEquals(List.of(String.valueOf(MAX_DEPTH - 1)), run(src.toString()));
        }
    }
}