package org.sure_vote.resource;

import java.util.List;

public final class ValidatorListResource {
    private List<ValidatorResource> validators;

    public List<ValidatorResource> getValidators() {
        return this.validators;
    }

    public void setValidators(final List<ValidatorResource> validators) {
        this.validators = validators;
    }
}
