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

## Architecture

```text
REST endpoint
  -> RealmResourceProvider
  -> AwsSyncService
  -> AWS SDK v2
  -> IAM Identity Center IdentityStore API
```

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

This provider does not include a Keycloak UI extension. Configuration and sync
operations are supported through `kcadm`, Keycloak Admin REST API, and the
provider REST endpoints only.

The Keycloak Admin Console does not provide a dedicated form for these custom
realm attributes. Use `kcadm` or the Admin REST API to create and update the
`aws.*` attributes.

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
mvn "-Dkc.version=26.1.2" -DskipTests clean package
```

Output:

```text
target/keycloak-aws-identitycenter-sync-<version>.jar
```

GitHub Actions release build:

- Workflow reads the GitHub Release tag, for example `v2.1.0`
- The tag value is passed into Maven as `-Drevision`
- The published JAR version uses this release version, for example `2.1.0`

## Installation

Copy the JAR into the Keycloak providers directory:

```bash
cp target/*.jar /opt/keycloak/providers/
```

Rebuild Keycloak:

```bash
bin/kc.sh build
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
- No Keycloak UI extension
- No Admin Console forms for plugin configuration

## Compatibility

- Keycloak 26+
- AWS SDK v2
- IAM Identity Center IdentityStore API

## License

Apache 2.0
