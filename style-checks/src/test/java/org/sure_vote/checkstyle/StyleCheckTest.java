package org.sure_vote.checkstyle;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import com.puppycrawl.tools.checkstyle.Checker;
import com.puppycrawl.tools.checkstyle.DefaultConfiguration;
import com.puppycrawl.tools.checkstyle.api.AuditEvent;
import com.puppycrawl.tools.checkstyle.api.AuditListener;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StyleCheckTest {
    @Test
    void flagsDanglingIf() throws Exception {
        final var code = """
            class Sample {
                void run(boolean ready) {
                    if (ready) {
                        return;
                    }
                    int leftover = 1;
                }
            }
            """;
        final var messages = violations(code, NoDanglingIfCheck.class.getName());
        assertEquals(1, messages.size());
    }

    @Test
    void allowsIfWithElse() throws Exception {
        final var code = """
            class Sample {
                void run(boolean ready) {
                    if (ready) {
                        return;
                    }
                    else {
                        return;
                    }
                }
            }
            """;
        final var messages = violations(code, NoDanglingIfCheck.class.getName());
        assertEquals(0, messages.size());
    }

    @Test
    void allowsVariablesGroupedAtTop() throws Exception {
        final var code = """
            class Sample {
                void run() {
                    int first = 1;
                    int second = 2;
                    consume(first);
                }
                void consume(int value) {
                }
            }
            """;
        final var messages = violations(code, VariablePlacementCheck.class.getName());
        assertEquals(0, messages.size());
    }

    @Test
    void flagsVariableBelowStatement() throws Exception {
        final var code = """
            class Sample {
                void run() {
                    int first = 1;
                    consume(first);
                    int second = 2;
                }
                void consume(int value) {
                }
            }
            """;
        final var messages = violations(code, VariablePlacementCheck.class.getName());
        assertEquals(1, messages.size());
    }

    @Test
    void flagsLongChainOnOneLine() throws Exception {
        final var code = """
            class Sample {
                void run() {
                    String value = "a".trim().toUpperCase();
                }
            }
            """;
        final var messages = violations(code, MethodChainLayoutCheck.class.getName());
        assertEquals(1, messages.size());
    }

    @Test
    void allowsBrokenChain() throws Exception {
        final var code = """
            class Sample {
                void run() {
                    String value = "a"
                        .trim()
                        .toUpperCase();
                }
            }
            """;
        final var messages = violations(code, MethodChainLayoutCheck.class.getName());
        assertEquals(0, messages.size());
    }

    @Test
    void allowsAnIfAsTheOnlyForStatement() throws Exception {
        final var code = """
            class Sample {
                void run(boolean ready) {
                    for (;;) if (ready) {
                        return;
                    }
                }
            }
            """;
        final var messages = violations(code, NoDanglingIfCheck.class.getName());
        assertEquals(0, messages.size());
    }

    @Test
    void allowsASingleCall() throws Exception {
        final var code = """
            class Sample {
                void run() {
                    String value = "a".trim();
                }
            }
            """;
        final var messages = violations(code, MethodChainLayoutCheck.class.getName());
        assertEquals(0, messages.size());
    }

    @Test
    void flagsAChainedCallThatDoesNotStartWithADot() throws Exception {
        final var code = """
            class Sample {
                void run() {
                    String value = "a"
                        /*note*/ .trim()
                        .toUpperCase();
                }
            }
            """;
        final var messages = violations(code, MethodChainLayoutCheck.class.getName());
        assertEquals(1, messages.size());
    }

    @Test
    void allowsFinalVar() throws Exception {
        final var code = """
            class Sample {
                void run() {
                    final var value = "a";
                    consume(value);
                }
                void consume(String value) {
                }
            }
            """;
        final var messages = violations(code, PreferFinalVarCheck.class.getName());
        assertEquals(0, messages.size());
    }

    @Test
    void ignoresAField() throws Exception {
        final var code = """
            class Sample {
                String name;
            }
            """;
        final var messages = violations(code, PreferFinalVarCheck.class.getName());
        assertEquals(0, messages.size());
    }

    @Test
    void flagsAForEachWithoutVar() throws Exception {
        final var code = """
            class Sample {
                void run(java.util.List<String> items) {
                    for (String item : items) {
                        consume(item);
                    }
                }
                void consume(String value) {
                }
            }
            """;
        final var messages = violations(code, PreferFinalVarCheck.class.getName());
        assertEquals(1, messages.size());
    }

    @Test
    void allowsAForEachVar() throws Exception {
        final var code = """
            class Sample {
                void run(java.util.List<String> items) {
                    for (var item : items) {
                        consume(item);
                    }
                }
                void consume(String value) {
                }
            }
            """;
        final var messages = violations(code, PreferFinalVarCheck.class.getName());
        assertEquals(0, messages.size());
    }

    @Test
    void flagsExplicitLocalType() throws Exception {
        final var code = """
            class Sample {
                void run() {
                    String value = "a";
                }
            }
            """;
        final var messages = violations(code, PreferFinalVarCheck.class.getName());
        assertEquals(1, messages.size());
    }

    private static List<String> violations(final String code, final String checkName) throws Exception {
        final var checker = new Checker();
        checker.setModuleClassLoader(Thread.currentThread().getContextClassLoader());
        final var config = new DefaultConfiguration("Checker");
        final var walker = new DefaultConfiguration("TreeWalker");
        final var module = new DefaultConfiguration(checkName);
        walker.addChild(module);
        config.addChild(walker);
        checker.configure(config);
        final var listener = new CollectingListener();
        checker.addListener(listener);
        final var file = Files.createTempFile("style-sample", ".java");
        Files.writeString(file, code);
        checker.process(List.of(file.toFile()));
        checker.destroy();
        return listener.messages;
    }

    private static final class CollectingListener implements AuditListener {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void auditStarted(final AuditEvent event) {
            return;
        }

        @Override
        public void auditFinished(final AuditEvent event) {
            return;
        }

        @Override
        public void fileStarted(final AuditEvent event) {
            return;
        }

        @Override
        public void fileFinished(final AuditEvent event) {
            return;
        }

        @Override
        public void addError(final AuditEvent event) {
            this.messages.add(event.getMessage());
        }

        @Override
        public void addException(final AuditEvent event, final Throwable throwable) {
            this.messages.add(throwable.toString());
        }
    }
}
