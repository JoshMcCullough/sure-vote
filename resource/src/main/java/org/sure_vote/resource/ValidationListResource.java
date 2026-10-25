package org.sure_vote.resource;

import java.util.List;

public final class ValidationListResource {
    private List<ValidationResource> validations;

    public List<ValidationResource> getValidations() {
        return this.validations;
    }

    public void setValidations(final List<ValidationResource> validations) {
        this.validations = validations;
    }
}
