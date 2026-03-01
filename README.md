# Keycloak AWS Identity Center Plugin

A **Keycloak 26+** extension that enables:

-   AWS credential auto-detection (EC2, ECS, IRSA, local profiles)
-   Optional STS AssumeRole (configurable per realm)
-   Full synchronization with AWS IAM Identity Center via IdentityStore
    API
-   Real-time sync progress tracking
-   Batch processing (30 users per batch)
-   API rate limiting support
-   Automatic user update synchronization via Keycloak event listener
-   Realm-level configuration (multi-tenant ready)

------------------------------------------------------------------------

## Architecture Overview

Admin UI / REST\
↓\
RealmResourceProvider\
↓\
AwsSyncService\
↓\
AWS SDK v2\
↓\
IAM Identity Center (IdentityStore API)

------------------------------------------------------------------------

## Features

### 1. AWS Credentials Support

Uses AWS SDK v2 DefaultCredentialsProvider:

-   EC2 Instance Role
-   ECS Task Role
-   EKS IRSA
-   Environment variables
-   \~/.aws/credentials

Optional:

-   STS AssumeRole (configured per realm)

------------------------------------------------------------------------

### 2. Full Synchronization

-   Lists users from Identity Center
-   Creates or updates Keycloak users
-   Processes in batches of 30 users
-   Applies configurable rate limiting
-   Exposes real-time progress endpoint

------------------------------------------------------------------------

### 3. Event-Based Sync

When a user profile is updated in Keycloak, the plugin:

-   Listens to update events
-   Calls IdentityStore API to propagate changes

------------------------------------------------------------------------

### 4. Realm-Level Configuration

Each realm can configure independently:

  Key                   Description
  --------------------- -----------------------------
  aws.region            AWS Region
  aws.roleArn           Optional AssumeRole ARN
  aws.identityStoreId   Identity Center Instance ID
  aws.maxQps            API rate limit

------------------------------------------------------------------------

## Required AWS Permissions

``` json
{
  "Effect": "Allow",
  "Action": [
    "identitystore:CreateUser",
    "identitystore:UpdateUser",
    "identitystore:CreateGroup",
    "identitystore:UpdateGroup",
    "identitystore:CreateGroupMembership",
    "identitystore:DeleteGroupMembership",
    "identitystore:ListUsers",
    "identitystore:ListGroups",
    "identitystore:ListGroupMemberships"
  ],
  "Resource": "*"
}
```

------------------------------------------------------------------------

## Build Instructions

Requirements:

-   Java 17+
-   Maven 3.8+
-   Keycloak 26+

Build:

``` bash
mvn clean package
```

Output:

    target/keycloak-aws-identitycenter-plugin-1.0.0.jar

------------------------------------------------------------------------

## Installation

1.  Copy the JAR into your Keycloak providers directory:

``` bash
cp target/*.jar /opt/keycloak/providers/
```

2.  Rebuild Keycloak:

``` bash
bin/kc.sh build
```

3.  Start Keycloak:

``` bash
bin/kc.sh start
```

------------------------------------------------------------------------

## REST Endpoints

### Trigger Full Sync

POST /realms/{realm}/aws-sync/sync

### Check Sync Progress

GET /realms/{realm}/aws-sync/progress

Returns:

Number of processed users

------------------------------------------------------------------------

## Sync Behavior

-   Users processed in batches of 30
-   Rate limiting via Guava RateLimiter
-   Runs in background thread
-   Progress stored in memory

------------------------------------------------------------------------

## Limitations (Current Version)

-   No persistent job storage
-   No UI extension included (REST only)
-   IdentityStore does not provide true batch APIs (logical batching
    implemented)

------------------------------------------------------------------------

## Compatibility

-   Keycloak 26+
-   AWS SDK v2
-   IAM Identity Center IdentityStore API

------------------------------------------------------------------------

## License

Apache 2.0

------------------------------------------------------------------------
