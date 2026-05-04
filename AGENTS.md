# AGENTS.md

This file gives coding agents the repository-specific context needed to work
on `keycloak-aws-identitycenter-sync`. Instructions apply to the whole
repository unless a more specific `AGENTS.md` exists in a subdirectory.

## Project Overview

- Project: `keycloak-aws-identitycenter-sync`
- Language/runtime: Java 21
- Build tool: Maven
- Package type: shaded JAR for Keycloak provider deployment
- Target platform: Keycloak 26+
- Main package: `com.jinxiao.keycloak.aws`

## Repository Layout

- `src/main/java/com/jinxiao/keycloak/aws`: provider, event listener, REST
  resource, AWS client/config, and sync implementation
- `src/main/resources/META-INF/services`: Keycloak service descriptors
- `pom.xml`: Maven build, dependency, profile, and shade configuration
- `README.md`: user-facing setup, configuration, build, and deployment docs
- `target/`: generated Maven output; do not commit

## Build And Verification

Use these commands from the repository root:

```bash
mvn -DskipTests clean compile
```

```bash
mvn -DskipTests clean package
```

```bash
mvn "-Dkc.version=26.1.2" -DskipTests clean package
```

Always run at least `mvn -DskipTests clean compile` after Java or Maven changes.
For documentation-only changes, explain if no build was run.

## Runtime Configuration

Runtime configuration is stored in Keycloak realm attributes:

- `aws.enabled`: optional, default `false`
- `aws.region`: required
- `aws.identityStoreId`: required
- `aws.roleArn`: optional
- `aws.maxQps`: optional, default `5`
- `aws.userNameSource`: optional, default `username`; supported values are
  `username` and `email`

If configuration behavior or keys change, update `README.md` in the same
change.

## Keycloak Compatibility

- Keep Keycloak dependencies in `provided` scope.
- Avoid Keycloak internal APIs unless there is no suitable public API.
- Preserve existing service descriptor files under `META-INF/services`.
- Keep the provider compatible with Keycloak 26+ unless the task explicitly
  changes the support target.

## Dependency And Shading Rules

- The project uses `maven-shade-plugin` to produce a deployable provider JAR.
- Do not remove existing shade filters or Keycloak exclusions without a clear
  compatibility reason.
- Prefer minimal dependency additions because this JAR runs inside Keycloak and
  can conflict with the server classpath.
- Keep AWS SDK and other third-party versions centralized in `pom.xml`
  properties when practical.

## Coding Guidelines

- Keep changes focused on the requested task.
- Preserve the existing package structure and naming conventions.
- Use Java 21-compatible language features.
- Prefer clear, small methods over broad refactors.
- Keep files UTF-8 and ASCII unless non-ASCII text is required.
- Add comments only when they clarify non-obvious behavior or integration
  constraints.

## Documentation Guidelines

- Update `README.md` when behavior, configuration, endpoints, build commands,
  installation steps, or compatibility notes change.
- Keep examples copy-paste runnable.
- Keep user-facing docs aligned with the actual Maven coordinates, artifact
  name, realm attributes, and REST endpoints.

## Git And Generated Files

- Do not commit generated `target/` content.
- Do not revert unrelated user changes.
- Keep commits and pull requests scoped to the requested change.

## Final Checklist

Before handing work back, confirm:

- Relevant Maven verification passed, usually
  `mvn -DskipTests clean compile`.
- No unnecessary new build warnings were introduced.
- `README.md` matches current behavior and configuration keys.
- Generated files such as `target/` are not included.
