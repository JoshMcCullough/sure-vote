package org.sure_vote.service;

import org.sure_vote.entity.TenantOfficial;

public record OfficialEnrollment(TenantOfficial official, String enrollmentToken) {
}
