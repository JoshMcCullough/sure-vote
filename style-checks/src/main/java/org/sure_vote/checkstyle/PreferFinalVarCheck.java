package org.sure_vote.checkstyle;

import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;

public final class PreferFinalVarCheck extends AbstractCheck {
    @Override
    public int[] getDefaultTokens() {
        return new int[] {TokenTypes.VARIABLE_DEF};
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
        if (!isLocal(ast)) {
            return;
        }
        else if (!canUseVar(ast)) {
            return;
        }
        else if (!usesVar(ast)) {
            this.log(ast, "Use final var where the variable can be initialized.");
        }
    }

    private static boolean isLocal(final DetailAST ast) {
        final DetailAST parent = ast.getParent();
        if (parent == null) {
            return false;
        }
        else if (parent.getType() == TokenTypes.SLIST) {
            return true;
        }
        else {
            return parent.getType() == TokenTypes.FOR_EACH_CLAUSE;
        }
    }

    private static boolean canUseVar(final DetailAST ast) {
        if (ast.getParent().getType() == TokenTypes.FOR_EACH_CLAUSE) {
            return true;
        }
        else {
            return ast.findFirstToken(TokenTypes.ASSIGN) != null;
        }
    }

    private static boolean usesVar(final DetailAST ast) {
        final DetailAST type = ast.findFirstToken(TokenTypes.TYPE);
        final DetailAST ident = type.getFirstChild();
        return ident != null
            && ident.getType() == TokenTypes.IDENT
            && "var".equals(ident.getText());
    }
}
