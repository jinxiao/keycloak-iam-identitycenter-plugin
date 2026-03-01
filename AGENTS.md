# AGENTS.md

Guidelines for coding agents working in this repository.

## Project Summary

- Project: `keycloak-aws-identitycenter-sync`
- Language: Java 21
- Build tool: Maven
- Packaging: shaded JAR for Keycloak provider deployment
- Target platform: Keycloak 26+ (version can be overridden at build time)

## Repository Layout

- Source code: `src/main/java/com/jinxiao/keycloak/aws`
- Service descriptors: `src/main/resources/META-INF/services`
- Build config: `pom.xml`
- Docs: `README.md`

## Build and Verify

- Compile:
  - `mvn -DskipTests clean compile`
- Package:
  - `mvn -DskipTests clean package`
- Build with specific Keycloak version:
  - `mvn "-Dkc.version=26.1.2" -DskipTests clean package`

Always run at least `clean compile` after code changes.

## Keycloak Compatibility Notes

- Keep Keycloak dependencies in `provided` scope.
- Avoid introducing Keycloak internal API usage unless necessary.
- Current runtime config is read from realm attributes:
  - `aws.region` (required)
  - `aws.identityStoreId` (required)
  - `aws.roleArn` (optional)
  - `aws.maxQps` (optional, default `5`)

## Dependency and Shading Rules

- This project uses `maven-shade-plugin`.
- Do not remove existing overlap filters in `pom.xml` unless there is a clear reason.
- Prefer minimal dependency additions to avoid classpath conflicts inside Keycloak.

## Code Change Rules

- Keep changes minimal and focused on the requested task.
- Preserve existing package structure and naming conventions.
- Use UTF-8 and keep files ASCII unless non-ASCII is required.
- Do not commit generated `target/` content.

## Documentation Expectations

- If behavior or configuration changes, update `README.md` in the same change.
- Keep examples copy-paste runnable.

## Safety Checks Before Finishing

1. `mvn -DskipTests clean compile` passes.
2. No new build warnings introduced unnecessarily.
3. README is consistent with current commands and configuration keys.
