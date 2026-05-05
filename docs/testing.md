# Testing

This project uses two mock-testing layers for AWS IAM Identity Center.

## Test Layers

- Unit tests use Mockito to mock Keycloak models and the AWS SDK
  `IdentitystoreClient`. These tests validate synchronization decisions without
  starting a network server.
- Configuration tests validate realm attribute parsing, defaults, and invalid
  configuration failures.
- Admin Console tab tests validate that declarative UI settings are persisted
  to `aws.*` realm attributes and rejected when invalid.
- Integration tests use WireMock as a local HTTP endpoint for the AWS SDK v2
  IdentityStore client. These tests verify request serialization, endpoint
  override wiring, and the sync manager's behavior against mock AWS responses.

## Why WireMock Instead Of Moto Server

Moto Server is useful for many AWS APIs and can be used by non-Python SDKs via
a custom endpoint. It is not the default test backend here because Moto's
IdentityStore coverage does not fully match this plugin's behavior.

The plugin depends on these IdentityStore operations:

- `CreateUser`, `CreateGroup`, `CreateGroupMembership`
- `GetUserId`, `GetGroupId`
- `UpdateUser`, `UpdateGroup`
- `DeleteUser`, `DeleteGroup`, `DeleteGroupMembership`
- `ListGroupMemberships`

Moto's IdentityStore support has historically missed or lagged on operations
such as `UpdateUser` and `UpdateGroup`. WireMock lets the tests explicitly
simulate all AWS responses the plugin needs, including conflict and update
flows, without Docker, Python, or real AWS credentials.

## Run Tests Locally

Run unit tests only:

```bash
mvn test
```

Run unit tests, integration tests, and packaging checks:

```bash
mvn verify
```

Run one unit test class:

```bash
mvn -Dtest=IdentityCenterSyncManagerTest test
```

Run all configuration tests:

```bash
mvn -Dtest=AwsConfigTest test
```

Run Admin Console configuration tests:

```bash
mvn -Dtest=AwsIdentityCenterAdminTabTest test
```

Run one integration test class:

```bash
mvn -Dit.test=IdentityStoreWireMockIT verify
```

## CI

GitHub Actions runs `mvn -B verify --file pom.xml` on:

- Pull requests targeting `main`
- Pushes to `main`

This means both unit tests and WireMock integration tests must pass before
changes are merged into `main` and after commits land on `main`.
