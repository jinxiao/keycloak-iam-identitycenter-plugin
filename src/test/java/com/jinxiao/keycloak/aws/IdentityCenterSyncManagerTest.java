package com.jinxiao.keycloak.aws;

import org.junit.jupiter.api.Test;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.identitystore.IdentitystoreClient;
import software.amazon.awssdk.services.identitystore.model.ConflictException;
import software.amazon.awssdk.services.identitystore.model.CreateGroupMembershipRequest;
import software.amazon.awssdk.services.identitystore.model.CreateGroupMembershipResponse;
import software.amazon.awssdk.services.identitystore.model.CreateGroupRequest;
import software.amazon.awssdk.services.identitystore.model.CreateGroupResponse;
import software.amazon.awssdk.services.identitystore.model.CreateUserRequest;
import software.amazon.awssdk.services.identitystore.model.CreateUserResponse;
import software.amazon.awssdk.services.identitystore.model.DeleteUserRequest;
import software.amazon.awssdk.services.identitystore.model.DeleteUserResponse;
import software.amazon.awssdk.services.identitystore.model.GetGroupIdResponse;
import software.amazon.awssdk.services.identitystore.model.GetGroupIdRequest;
import software.amazon.awssdk.services.identitystore.model.GetUserIdRequest;
import software.amazon.awssdk.services.identitystore.model.GetUserIdResponse;
import software.amazon.awssdk.services.identitystore.model.UpdateUserRequest;
import software.amazon.awssdk.services.identitystore.model.UpdateUserResponse;

import java.util.Collections;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdentityCenterSyncManagerTest {

    @Test
    void fullSyncCreatesUsersGroupsAndMemberships() {
        RealmModel realm = enabledRealm();
        UserModel user = user("kc-user-1", "alice", "alice@example.com", "Alice", "Example");
        GroupModel group = group("kc-group-1", "Developers");
        KeycloakSession session = sessionWithUsers(realm, user);

        when(realm.getGroupsStream()).thenReturn(Stream.of(group));
        when(group.getSubGroupsStream()).thenReturn(Stream.empty());
        when(user.getGroupsStream()).thenReturn(Stream.of(group));

        IdentitystoreClient client = mock(IdentitystoreClient.class);
        when(client.createUser(any(CreateUserRequest.class)))
                .thenReturn(CreateUserResponse.builder().userId("aws-user-1").build());
        when(client.createGroup(any(CreateGroupRequest.class)))
                .thenReturn(CreateGroupResponse.builder().groupId("aws-group-1").build());
        when(client.getUserId(any(GetUserIdRequest.class)))
                .thenReturn(GetUserIdResponse.builder().userId("aws-user-1").build());
        when(client.getGroupId(any(GetGroupIdRequest.class)))
                .thenReturn(GetGroupIdResponse.builder().groupId("aws-group-1").build());
        when(client.createGroupMembership(any(CreateGroupMembershipRequest.class)))
                .thenReturn(CreateGroupMembershipResponse.builder().membershipId("aws-membership-1").build());

        IdentityCenterSyncManager manager = managerWith(client);
        IdentityCenterSyncManager.SyncResult result = manager.fullSync(session, realm);

        assertEquals(1, result.usersProcessed());
        assertEquals(1, result.groupsProcessed());
        assertEquals(1, result.membershipsProcessed());
        assertEquals(0, result.usersFailed());
        assertEquals(0, result.groupsFailed());
        assertEquals(0, result.membershipsFailed());
        assertFalse(result.hasFailures());

        ArgumentCaptor<CreateUserRequest> userRequest = ArgumentCaptor.forClass(CreateUserRequest.class);
        verify(client).createUser(userRequest.capture());
        assertEquals("d-test", userRequest.getValue().identityStoreId());
        assertEquals("alice", userRequest.getValue().userName());
        assertEquals("Alice Example", userRequest.getValue().displayName());

        ArgumentCaptor<CreateGroupMembershipRequest> membershipRequest =
                ArgumentCaptor.forClass(CreateGroupMembershipRequest.class);
        verify(client).createGroupMembership(membershipRequest.capture());
        assertEquals("aws-group-1", membershipRequest.getValue().groupId());
        assertEquals("aws-user-1", membershipRequest.getValue().memberId().userId());
    }

    @Test
    void syncSingleUserUpdatesExistingUserAfterCreateConflict() {
        RealmModel realm = enabledRealm();
        UserModel user = user("kc-user-1", "alice", "alice@example.com", "Alice", "Example");
        KeycloakSession session = mock(KeycloakSession.class);
        UserProvider userProvider = mock(UserProvider.class);
        when(session.users()).thenReturn(userProvider);
        when(userProvider.getUserById(realm, "kc-user-1")).thenReturn(user);

        IdentitystoreClient client = mock(IdentitystoreClient.class);
        when(client.createUser(any(CreateUserRequest.class)))
                .thenThrow(ConflictException.builder().message("user exists").build());
        when(client.getUserId(any(GetUserIdRequest.class)))
                .thenReturn(GetUserIdResponse.builder().userId("aws-user-1").build());
        when(client.updateUser(any(UpdateUserRequest.class)))
                .thenReturn(UpdateUserResponse.builder().build());
        RecordingLimiter limiter = new RecordingLimiter();

        boolean ok = managerWith(client, limiter).syncSingleUser(session, realm, "kc-user-1");

        assertTrue(ok);
        assertEquals(3, limiter.acquireCount());
        ArgumentCaptor<UpdateUserRequest> updateRequest = ArgumentCaptor.forClass(UpdateUserRequest.class);
        verify(client).updateUser(updateRequest.capture());
        assertEquals("d-test", updateRequest.getValue().identityStoreId());
        assertEquals("aws-user-1", updateRequest.getValue().userId());
        assertEquals("DisplayName", updateRequest.getValue().operations().get(0).attributePath());
    }

    @Test
    void deleteSingleUserUsesHintWhenKeycloakUserIsAlreadyGone() {
        RealmModel realm = enabledRealm();
        KeycloakSession session = mock(KeycloakSession.class);
        UserProvider userProvider = mock(UserProvider.class);
        when(session.users()).thenReturn(userProvider);
        when(userProvider.getUserById(realm, "kc-user-1")).thenReturn(null);

        IdentitystoreClient client = mock(IdentitystoreClient.class);
        when(client.getUserId(any(GetUserIdRequest.class)))
                .thenReturn(GetUserIdResponse.builder().userId("aws-user-1").build());
        when(client.deleteUser(any(DeleteUserRequest.class)))
                .thenReturn(DeleteUserResponse.builder().build());

        boolean ok = managerWith(client).deleteSingleUser(session, realm, "kc-user-1", "alice");

        assertTrue(ok);
        ArgumentCaptor<DeleteUserRequest> deleteRequest = ArgumentCaptor.forClass(DeleteUserRequest.class);
        verify(client).deleteUser(deleteRequest.capture());
        assertEquals("d-test", deleteRequest.getValue().identityStoreId());
        assertEquals("aws-user-1", deleteRequest.getValue().userId());
    }

    private IdentityCenterSyncManager managerWith(IdentitystoreClient client) {
        return new IdentityCenterSyncManager(config -> AwsClientFactory.AwsClients.of(client));
    }

    private IdentityCenterSyncManager managerWith(IdentitystoreClient client, AwsApiLimiter limiter) {
        return new IdentityCenterSyncManager(
                config -> AwsClientFactory.AwsClients.of(client),
                maxQps -> limiter
        );
    }

    private RealmModel enabledRealm() {
        RealmModel realm = mock(RealmModel.class);
        when(realm.getName()).thenReturn("test");
        when(realm.getAttribute("aws.enabled")).thenReturn("true");
        when(realm.getAttribute("aws.region")).thenReturn("us-east-1");
        when(realm.getAttribute("aws.identityStoreId")).thenReturn("d-test");
        when(realm.getAttribute("aws.roleArn")).thenReturn(null);
        when(realm.getAttribute("aws.maxQps")).thenReturn("1000");
        when(realm.getAttribute("aws.userNameSource")).thenReturn("username");
        return realm;
    }

    private KeycloakSession sessionWithUsers(RealmModel realm, UserModel user) {
        KeycloakSession session = mock(KeycloakSession.class);
        UserProvider userProvider = mock(UserProvider.class);
        when(session.users()).thenReturn(userProvider);
        when(userProvider.searchForUserStream(
                eq(realm),
                eq(Collections.<String, String>emptyMap()),
                isNull(),
                isNull()
        )).thenReturn(Stream.of(user));
        return session;
    }

    private UserModel user(String id, String username, String email, String firstName, String lastName) {
        UserModel user = mock(UserModel.class);
        when(user.getId()).thenReturn(id);
        when(user.getUsername()).thenReturn(username);
        when(user.getEmail()).thenReturn(email);
        when(user.getFirstName()).thenReturn(firstName);
        when(user.getLastName()).thenReturn(lastName);
        return user;
    }

    private GroupModel group(String id, String name) {
        GroupModel group = mock(GroupModel.class);
        when(group.getId()).thenReturn(id);
        when(group.getName()).thenReturn(name);
        return group;
    }

    private static final class RecordingLimiter implements AwsApiLimiter {
        private int acquireCount;

        @Override
        public void acquire() {
            acquireCount++;
        }

        int acquireCount() {
            return acquireCount;
        }
    }
}
