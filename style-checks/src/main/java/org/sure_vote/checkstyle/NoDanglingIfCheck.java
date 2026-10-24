package org.sure_vote.checkstyle;

import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;

public final class NoDanglingIfCheck extends AbstractCheck {
    @Override
    public int[] getDefaultTokens() {
        return new int[] {TokenTypes.LITERAL_IF};
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
        final DetailAST parent = ast.getParent();
        if (parent == null) {
            return;
        }
        else if (parent.getType() != TokenTypes.SLIST) {
            return;
        }
        else {
            final DetailAST next = nextStatement(ast);
            if (next != null) {
                this.log(
                    ast,
                    "Dangling if. The rest of the block must be inside else."
                );
            }
        }
    }

    private static DetailAST nextStatement(final DetailAST ast) {
        DetailAST next = ast.getNextSibling();
        while (next != null && next.getType() == TokenTypes.SEMI) {
            next = next.getNextSibling();
        }
        if (next == null) {
            return null;
        }
        else if (next.getType() == TokenTypes.RCURLY) {
            return null;
        }
        else {
            return next;
        }
    }
}
