# Code style

Rules for Java in this repository. Indent with 4 spaces. Do not use tab characters.

## Names

Variables use camelCase.

## Variables

Declare each variable at the top of the most enclosing block that contains every use, before any other statement in that block. A variable used in more than one nested block belongs at the top of the outer block. A variable used in only one branch belongs at the top of that branch.

Use `final var` when the variable is initialized there and is not reassigned.

```java
void accept(final boolean ready) {
    final var now = Instant.now();

    if (ready) {
        final var status = Status.ACCEPTED;

        issuance.setStatus(status);
        issuance.setAcceptedAt(now);
    }
    else {
        final var status = Status.VOIDED;

        issuance.setStatus(status);
    }
}
```

## Braces

The opening brace stays on the same line as the declaration or the control statement. `else if` and `else` each start on a new line.

An `if` does not leave the rest of the block dangling after it. The other path goes in `else`.

```java
if (ready) {
    issuance.setStatus(Status.ACCEPTED);
}
else if (voided) {
    issuance.setStatus(Status.VOIDED);
}
else {
    issuance.setStatus(Status.ACCEPTED);
}
```

## Method chains

A chain of two calls may stay on one line. A longer chain puts the receiver on its own line and each call on a following line, indented 4 spaces.

```java
a.b();

a
    .b()
    .c();
```
