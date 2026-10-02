# Zalava contributor guidance

## Bounded contexts and ports

Organize product code by bounded context / feature slice. A context owns its
domain language, use cases, and persistence-facing contracts; it does not reach
into another context's internals. Share a stable contract only when the
relationship is deliberate and versioned.

Keep each context hexagonal. Domain rules must not import Spring, HTTP,
persistence, messaging, or vendor-SDK types. Inbound adapters translate a
transport request into a use-case input. Use cases coordinate domain behavior
through explicit inbound and outbound ports. Outbound adapters implement those
ports and contain framework and infrastructure mapping. Dependencies point
inward, and Spring wiring remains at a composition boundary.

Do not introduce a Gradle subproject merely to represent a context. Split build
modules only when independent compilation, release, ownership, or dependency
isolation requires it. Prefer an incremental vertical-slice migration to a
broad architectural rewrite.

Zalava is a Java 25 Gradle multi-project application with a Spring Boot host,
PostgreSQL persistence, and independently released JVM modules.

Run the Gradle wrapper from the repository root. Use focused tests first and the
documented verification task before review. Keep tests deterministic and test
observable behavior; HTTP behavior uses full-context MockMvc tests and product
journeys use the repository browser acceptance lane when available.

Keep policy and validation at system boundaries. External modules compile against
the released Module API, must not bundle it, and preserve declared identifiers
unless a reviewed compatibility change says otherwise. Do not commit credentials,
user data, local configuration, generated browser output, or build products.


## Verification and delivery

Run `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew check` before review or release.
`check` must enforce Spotless 7.2.1 / google-java-format 1.36.1 and JaCoCo
minimums of 90% line coverage and 74% branch coverage, matching the Zalava host.
Do not lower thresholds or exclude uncovered production classes to pass.
Use focused tests first and retain coverage reports and failure diagnostics.

Test the real built module JAR through the released `org.zalava:module-api-test`
kit, not injected module classes or source-output directories. Cover providers,
typed services, module pages/forms/assets, configuration, validation/failure,
permissions, cleanup and observable effects as applicable. Keep fixtures local
and deterministic. Module UI actions must not require AI mediation.
Contract-kit acceptance is not real-Zalava acceptance: Core owns installation,
configuration, restart, security, persistence and browser journeys against
released artifacts. Record missing prerequisites; never count them as passing.

Production code must remain independent of host internals. Compile against the
released Module API, never bundle it, preserve declared wire identifiers, and
pin compatible API/kit versions. Runtime/client SDKs belong in adapters; domain
rules and use cases communicate through explicit contracts.

Use an explicit step branch, dated plan/evidence, scoped tests, staged-diff review
and a ready-for-review PR. Manage dependent PRs with `gh stack`; never merge
autonomously. Publish immutable versions only from verified merged default-branch
commits through successful publication workflows. A tag alone is not a release.
