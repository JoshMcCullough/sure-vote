package org.sure_vote.checkstyle;

import java.util.ArrayList;
import java.util.List;

import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;

public final class MethodChainLayoutCheck extends AbstractCheck {
    @Override
    public int[] getDefaultTokens() {
        return new int[] {TokenTypes.METHOD_CALL};
    }

    @Override
    public int[] getAcceptableTokens() {
        return this.getDefaultTokens();
    }

    @Override
    public int[] getRequiredTokens() {
        return this.getDefaultTokens();
    }

    @Override
    public void visitToken(final DetailAST ast) {
        if (!isOutermost(ast)) {
            return;
        }
        else {
            final var calls = new ArrayList<DetailAST>();
            collect(ast, calls);
            if (calls.size() < 2) {
                return;
            }
            else {
                this.checkLines(ast, calls);
            }
        }
    }

    private void checkLines(final DetailAST outermost, final List<DetailAST> calls) {
        final DetailAST receiver = receiver(calls.get(calls.size() - 1));
        final var lines = new ArrayList<Integer>();
        lines.add(receiver.getLineNo());
        for (final var call : calls) {
            lines.add(call.getLineNo());
        }
        if (!distinct(lines)) {
            this.log(
                outermost,
                "Break a chain of two or more calls onto separate lines."
            );
        }
        else {
            this.checkDots(calls);
        }
    }

    private void checkDots(final List<DetailAST> calls) {
        for (final var call : calls) {
            final String line = this.getLine(call.getLineNo() - 1);
            final String trimmed = line.stripLeading();
            if (!trimmed.startsWith(".")) {
                this.log(call, "Each chained call goes on its own line, starting with '.'.");
            }
        }
    }

    private static boolean isOutermost(final DetailAST ast) {
        final DetailAST parent = ast.getParent();
        if (parent == null) {
            return true;
        }
        else if (parent.getType() != TokenTypes.DOT) {
            return true;
        }
        else {
            final DetailAST grand = parent.getParent();
            return grand == null || grand.getType() != TokenTypes.METHOD_CALL;
        }
    }

    private static void collect(final DetailAST call, final List<DetailAST> calls) {
        calls.add(call);
        final DetailAST first = call.getFirstChild();
        if (first != null && first.getType() == TokenTypes.DOT) {
            final DetailAST target = first.getFirstChild();
            if (target.getType() == TokenTypes.METHOD_CALL) {
                collect(target, calls);
            }
        }
    }

    private static DetailAST receiver(final DetailAST innermostCall) {
        final DetailAST dot = innermostCall.getFirstChild();
        return dot.getFirstChild();
    }

    private static boolean distinct(final List<Integer> lines) {
        boolean unique = true;
        for (int index = 0; index < lines.size(); index = index + 1) {
            for (int later = index + 1; later < lines.size(); later = later + 1) {
                if (lines.get(index).equals(lines.get(later))) {
                    unique = false;
                }
            }
        }
        return unique;
    }
}
