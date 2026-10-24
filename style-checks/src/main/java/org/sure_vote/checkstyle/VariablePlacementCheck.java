package org.sure_vote.checkstyle;

import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;

public final class VariablePlacementCheck extends AbstractCheck {
    @Override
    public int[] getDefaultTokens() {
        return new int[] {TokenTypes.SLIST};
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
        boolean seenStatement = false;
        DetailAST child = ast.getFirstChild();
        while (child != null) {
            if (child.getType() == TokenTypes.VARIABLE_DEF) {
                if (seenStatement) {
                    this.log(
                        child,
                        "Variables belong at the top of the most enclosing block."
                    );
                }
            }
            else if (child.getType() != TokenTypes.RCURLY && child.getType() != TokenTypes.SEMI) {
                seenStatement = true;
            }
            child = child.getNextSibling();
        }
    }
}
