package org.sure_vote.api;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import org.sure_vote.entity.PrincipalKind;

public enum Surface {
    ALL(EnumSet.allOf(RouteGroup.class), EnumSet.allOf(PrincipalKind.class)),
    PUBLIC(EnumSet.of(RouteGroup.HEALTH, RouteGroup.PUBLIC), EnumSet.noneOf(PrincipalKind.class)),
    WORKSPACE(
        EnumSet.of(RouteGroup.HEALTH, RouteGroup.OFFICIAL, RouteGroup.SESSION, RouteGroup.SIGN_IN, RouteGroup.SYSTEM),
        EnumSet.of(PrincipalKind.OFFICIAL, PrincipalKind.SYSTEM_ADMIN)
    ),
    DEMO(
        EnumSet.of(RouteGroup.HEALTH, RouteGroup.OFFICIAL, RouteGroup.SESSION, RouteGroup.SHOWCASE),
        EnumSet.of(PrincipalKind.VISITOR)
    ),
    INGEST(EnumSet.of(RouteGroup.HEALTH, RouteGroup.MACHINE), EnumSet.noneOf(PrincipalKind.class));

    private final Set<RouteGroup> groups;
    private final Set<PrincipalKind> principals;

    Surface(final Set<RouteGroup> groups, final Set<PrincipalKind> principals) {
        this.groups = groups;
        this.principals = principals;
    }

    public static Surface parse(final String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        else {
            final var name = value.trim();
            return valueOf(name.toUpperCase(Locale.ROOT));
        }
    }

    /** Only the workspace runs Flyway and the role grants; {@code all} does too, for development and tests. */
    public boolean migrates() {
        return this == ALL || this == WORKSPACE;
    }

    public String wire() {
        final var name = this.name();
        return name.toLowerCase(Locale.ROOT);
    }

    boolean serves(final RouteGroup group) {
        return this.groups.contains(group);
    }

    boolean servesWorkspaceApp() {
        return this == ALL || this == WORKSPACE || this == DEMO;
    }

    boolean servesVoterApp() {
        return this == ALL || this == PUBLIC;
    }

    boolean accepts(final PrincipalKind kind) {
        return this.principals.contains(kind);
    }
}
