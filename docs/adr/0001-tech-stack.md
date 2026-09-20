# 0001: Java 21, Spring Boot 4, PostgreSQL, Maven

Status: Accepted

## Context

KeyPass needs a backend stack that's productive to build quickly, well understood by anyone
reviewing the code, and capable of the relational integrity
guarantees the domain needs (foreign keys, row locks, transactions).

## Decision

Java 21 (LTS) with Spring Boot, PostgreSQL 16, Flyway for migrations, and Maven for the build,
laid out as a multi-module project (`keypass-common`, `keypass-server`, `keypass-car-sim`).

## Consequences

Industry-standard, well-documented stack. Spring Boot 4 is new enough that several tutorials
and some Maven Central artifacts assume Boot 3's package layout — see the post-mortem for a
concrete example of where that bit us during this build.

## Alternatives considered

- **Kotlin instead of Java**: nicer null-safety and shorter records, but Java's own records and
  sealed interfaces (used throughout `keypass-common`) already cover most of what motivated
  that. Sticking with Java keeps the codebase approachable to the widest audience.
- **A NoSQL store**: the domain is fundamentally relational (users own vehicles own keys own
  more keys, audit rows reference several other tables), so PostgreSQL's foreign keys and
  transactions do real work here that a document store would have to reimplement in application
  code.
