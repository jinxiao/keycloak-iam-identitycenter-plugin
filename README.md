# Keycloak AWS Identity Center Plugin

A **Keycloak 26+** provider that synchronizes Keycloak users, groups, and
memberships with AWS IAM Identity Center through the IdentityStore API.

## Features

- AWS credential auto-detection through AWS SDK v2
- Optional STS AssumeRole, configurable per realm
- Full synchronization with AWS IAM Identity Center
- Sync result statistics in API responses
- API rate limiting support
- Automatic incremental synchronization through Keycloak event listeners
- Realm-level configuration for multi-tenant deployments
- Admin Console configuration tab for AWS Identity Center realm attributes
- Separate `aws-identitycenter` admin theme archive

## Architecture

```text
Keycloak providers directory
  -> keycloak-aws-identitycenter-sync-<version>.jar
       -> RealmResourceProvider
            -> POST /realms/{realm}/aws-identitycenter-sync/full-sync
            -> SyncResource
            -> IdentityCenterSyncManager
       -> EventListenerProvider
            -> IdentityCenterEventListener
            -> IdentityCenterSyncManager
       -> UiTabProviderFactory
            -> AWS Identity Center tab in Realm Settings
            -> aws.* realm attributes
       -> AwsClientFactory
            -> AWS SDK v2 DefaultCredentialsProvider
            -> optional STS AssumeRole
            -> IAM Identity Center IdentityStore API

  -> keycloak-aws-identitycenter-sync-<version>-themes.jar
       -> aws-identitycenter admin theme
       -> parent admin theme: keycloak.v2
       -> META-INF/keycloak-themes.json
```

The provider JAR contains the REST endpoint, event listener, AWS sync logic,
and Admin Console declarative UI tab. The theme JAR is packaged separately and
only contributes the `aws-identitycenter` admin theme that inherits
`keycloak.v2`. Both JARs are deployed to Keycloak's `providers/` directory.

Realm configuration is stored as `aws.*` realm attributes. These attributes can
be edited from the Admin Console tab, `kcadm`, or the Keycloak Admin REST API,
and are read by both full sync and incremental event-based sync.

## AWS Credentials

The plugin uses the AWS SDK v2 `DefaultCredentialsProvider`, which supports:

- EC2 instance roles
- ECS task roles
- EKS IRSA
- Environment variables
- Local `~/.aws/credentials` profiles

STS AssumeRole can also be enabled per realm with `aws.roleArn`.

## Synchronization

### Full Sync

- Reads Keycloak users, groups, and group memberships
- Creates or updates matching IAM Identity Center users and groups
- Creates missing IAM Identity Center group memberships
- Applies configurable API rate limiting

### Event-Based Sync

When users, groups, or group memberships change in Keycloak, the plugin:

- Listens to admin user, group, and group-membership events
- Listens to user lifecycle events, such as registration and profile changes
- Triggers incremental synchronization for the affected user, group, or
  membership

## Realm Configuration

Configuration values are stored as **realm attributes** in Keycloak and are
persisted in the Keycloak database.

| Key | Required | Default | Description |
| --- | --- | --- | --- |
| `aws.enabled` | No | `false` | Enable synchronization for the realm |
| `aws.region` | Yes | - | AWS Region |
| `aws.identityStoreId` | Yes | - | IAM Identity Center identity store ID |
| `aws.roleArn` | No | - | Optional STS AssumeRole ARN |
| `aws.maxQps` | No | `5` | IdentityStore API rate limit |
| `aws.userNameSource` | No | `username` | AWS username source: `username` or `email` |

### UI Support

This provider includes a Keycloak Admin Console extension based on the
declarative UI SPI. The extension adds an **AWS Identity Center** tab under
Realm Settings so administrators can configure the `aws.*` realm attributes
from the Admin Console.

The same configuration values can still be managed through `kcadm` or the
Keycloak Admin REST API.

## Theme Support

The repository includes a separate Keycloak admin theme source tree under
`themes/aws-identitycenter`.

The theme provides:

- An `admin` theme named `aws-identitycenter`
- Inheritance from the built-in `keycloak.v2` admin theme

The theme intentionally inherits the built-in admin theme without copying
Admin Console assets. This keeps it easier to use with the latest Keycloak
26.x releases while letting the provider contribute the AWS Identity Center
configuration tab through the declarative UI SPI.

### Update with `kcadm`

Log in first:

```bash
bin/kcadm.sh config credentials \
  --server http://localhost:8080 \
  --realm master \
  --user admin \
  --password admin
```

Update attributes in realm `myrealm`:

```bash
bin/kcadm.sh update realms/myrealm \
  -s 'attributes."aws.enabled"=true' \
  -s 'attributes."aws.region"=us-east-1' \
  -s 'attributes."aws.identityStoreId"=d-1234567890' \
  -s 'attributes."aws.roleArn"=arn:aws:iam::123456789012:role/KeycloakSyncRole' \
  -s 'attributes."aws.maxQps"=5' \
  -s 'attributes."aws.userNameSource"=username'
```

Verify the stored attributes:

```bash
bin/kcadm.sh get realms/myrealm --fields attributes
```

### Update with JSON on Windows

Create `update-realm.json`:

```json
{
  "attributes": {
    "aws.enabled": true,
    "aws.region": "us-east-1",
    "aws.identityStoreId": "d-1234567890",
    "aws.roleArn": "arn:aws:iam::123456789012:role/KeycloakSyncRole",
    "aws.maxQps": 5,
    "aws.userNameSource": "username"
  }
}
```

Apply it:

```cmd
kcadm.bat update realms/myrealm -f update-realm.json
```

## Required AWS Permissions

```json
{
  "Effect": "Allow",
  "Action": [
    "identitystore:CreateUser",
    "identitystore:UpdateUser",
    "identitystore:DeleteUser",
    "identitystore:CreateGroup",
    "identitystore:UpdateGroup",
    "identitystore:DeleteGroup",
    "identitystore:CreateGroupMembership",
    "identitystore:DeleteGroupMembership",
    "identitystore:GetUserId",
    "identitystore:GetGroupId",
    "identitystore:ListUsers",
    "identitystore:ListGroups",
    "identitystore:ListGroupMemberships"
  ],
  "Resource": "*"
}
```

## Build

Requirements:

- Java 21+
- Maven 3.8+
- Keycloak 26+

Compile:

```bash
mvn -DskipTests clean compile
```

Package:

```bash
mvn -DskipTests clean package
```

Package with a specific Keycloak version:

```bash
mvn "-Dkc.version=26.6.1" -DskipTests clean package
```

Outputs:

```text
target/keycloak-aws-identitycenter-sync-<version>.jar
target/keycloak-aws-identitycenter-sync-<version>-themes.jar
```

GitHub Actions release build:

- Workflow reads the GitHub Release tag, for example `v2.1.0`
- The tag value is passed into Maven as `-Drevision`
- The published JAR version uses this release version, for example `2.1.0`

## Installation

Copy the provider and theme JARs into the Keycloak providers directory:

```bash
cp target/keycloak-aws-identitycenter-sync-*.jar /opt/keycloak/providers/
```

Rebuild Keycloak:

```bash
bin/kc.sh build --features=declarative-ui
```

Start Keycloak:

```bash
bin/kc.sh start
```

Enable the event listener for a realm:

```bash
bin/kcadm.sh update events/config -r myrealm \
  -s 'eventsListeners=["aws-identitycenter-sync"]' \
  -s eventsEnabled=true \
  -s adminEventsEnabled=true
```

If the realm already has event listeners, include the existing listener IDs in
the `eventsListeners` array so they remain enabled.

Enable the admin theme for the realm used by the Admin Console, commonly the
`master` realm:

```bash
bin/kcadm.sh update realms/master \
  -s adminTheme=aws-identitycenter
```

For local theme development, copy `themes/aws-identitycenter` to
`$KEYCLOAK_HOME/themes/aws-identitycenter` and start Keycloak with theme caches
disabled:

```bash
bin/kc.sh start-dev \
  --features=declarative-ui \
  --spi-theme--static-max-age=-1 \
  --spi-theme--cache-themes=false \
  --spi-theme--cache-templates=false
```

## REST Endpoints

### Trigger Full Sync

```http
POST /realms/{realm}/aws-identitycenter-sync/full-sync
```

Requires a bearer token with the `realm-management/manage-users` role in the
target realm.

Response fields:

- `status`: `success` or `partial_success`
- `usersProcessed`
- `groupsProcessed`
- `membershipsProcessed`
- `usersFailed`
- `groupsFailed`
- `membershipsFailed`

## Sync Behavior

- Rate limiting is implemented with Guava `RateLimiter`
- Full sync uses a synchronous request/response flow
- Conflict errors are treated as already synchronized

## Limitations

- No persistent job storage
- The Admin Console configuration tab requires Keycloak's `declarative-ui`
  feature to be enabled

## Compatibility

- Keycloak 26+; default build target is Keycloak 26.6.1
- AWS SDK v2
- IAM Identity Center IdentityStore API

## License

Apache 2.0
