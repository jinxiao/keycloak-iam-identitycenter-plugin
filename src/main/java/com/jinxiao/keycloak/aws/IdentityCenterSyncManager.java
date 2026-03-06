package com.jinxiao.keycloak.aws;

import com.google.common.util.concurrent.RateLimiter;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.identitystore.IdentitystoreClient;
import software.amazon.awssdk.services.identitystore.model.AttributeOperation;
import software.amazon.awssdk.services.identitystore.model.ConflictException;
import software.amazon.awssdk.services.identitystore.model.CreateGroupMembershipRequest;
import software.amazon.awssdk.services.identitystore.model.CreateGroupRequest;
import software.amazon.awssdk.services.identitystore.model.CreateUserRequest;
import software.amazon.awssdk.services.identitystore.model.DeleteGroupMembershipRequest;
import software.amazon.awssdk.services.identitystore.model.DeleteGroupRequest;
import software.amazon.awssdk.services.identitystore.model.DeleteUserRequest;
import software.amazon.awssdk.services.identitystore.model.GetGroupIdRequest;
import software.amazon.awssdk.services.identitystore.model.GetUserIdRequest;
import software.amazon.awssdk.services.identitystore.model.Group;
import software.amazon.awssdk.services.identitystore.model.GroupMembership;
import software.amazon.awssdk.services.identitystore.model.ListGroupMembershipsRequest;
import software.amazon.awssdk.services.identitystore.model.ListGroupMembershipsResponse;
import software.amazon.awssdk.services.identitystore.model.MemberId;
import software.amazon.awssdk.services.identitystore.model.ResourceNotFoundException;
import software.amazon.awssdk.services.identitystore.model.UpdateGroupRequest;
import software.amazon.awssdk.services.identitystore.model.UpdateUserRequest;
import software.amazon.awssdk.services.identitystore.model.UniqueAttribute;
import software.amazon.awssdk.services.identitystore.model.User;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IdentityCenterSyncManager {
    private static final Logger LOG = Logger.getLogger(IdentityCenterSyncManager.class.getName());

    public SyncResult fullSync(KeycloakSession session, RealmModel realm) {
        int usersProcessed = 0;
        int groupsProcessed = 0;
        int usersFailed = 0;
        int groupsFailed = 0;
        AwsConfig config = AwsConfig.fromRealm(realm);
        RateLimiter limiter = RateLimiter.create((double) config.maxQps);

        try (AwsClientFactory.AwsClients clients = AwsClientFactory.create(config)) {
            for (UserModel user : session.users().searchForUserStream(realm, Collections.emptyMap(), null, null).toList()) {
                limiter.acquire();
                usersProcessed++;
                if (!upsertUser(clients.identitystore(), config, user)) {
                    usersFailed++;
                }
            }

            for (GroupModel group : realm.getGroupsStream().toList()) {
                limiter.acquire();
                groupsProcessed++;
                if (!upsertGroup(clients.identitystore(), config.identityStoreId, group)) {
                    groupsFailed++;
                }
            }
        }

        return new SyncResult(usersProcessed, groupsProcessed, usersFailed, groupsFailed);
    }

    public boolean syncSingleUser(KeycloakSession session, RealmModel realm, String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        UserModel user = session.users().getUserById(realm, userId);
        if (user == null) {
            LOG.fine(String.format("User not found in realm, skip user sync. realm=%s userId=%s", realm.getName(), userId));
            return false;
        }

        AwsConfig config = AwsConfig.fromRealm(realm);
        try (AwsClientFactory.AwsClients clients = AwsClientFactory.create(config)) {
            return upsertUser(clients.identitystore(), config, user);
        }
    }

    public boolean syncSingleGroup(RealmModel realm, String groupId) {
        if (groupId == null || groupId.isBlank()) {
            return false;
        }
        GroupModel group = realm.getGroupById(groupId);
        if (group == null) {
            LOG.fine(String.format("Group not found in realm, skip group sync. realm=%s groupId=%s", realm.getName(), groupId));
            return false;
        }

        AwsConfig config = AwsConfig.fromRealm(realm);
        try (AwsClientFactory.AwsClients clients = AwsClientFactory.create(config)) {
            return upsertGroup(clients.identitystore(), config.identityStoreId, group);
        }
    }

    public boolean syncSingleGroupMembership(KeycloakSession session, RealmModel realm, String userId, String groupId) {
        if (isBlank(userId) || isBlank(groupId)) {
            return false;
        }

        UserModel user = session.users().getUserById(realm, userId);
        GroupModel group = realm.getGroupById(groupId);
        if (user == null || group == null) {
            LOG.warning(String.format("Cannot resolve membership objects in realm. realm=%s userId=%s groupId=%s",
                    realm.getName(), userId, groupId));
            return false;
        }

        AwsConfig config = AwsConfig.fromRealm(realm);
        try (AwsClientFactory.AwsClients clients = AwsClientFactory.create(config)) {
            String awsUserId = resolveAwsUserId(clients.identitystore(), config, user);
            String awsGroupId = findGroupIdByDisplayName(clients.identitystore(), config.identityStoreId, group.getName());
            if (awsUserId == null || awsGroupId == null) {
                LOG.warning(String.format("Cannot resolve AWS membership targets. realm=%s userId=%s groupId=%s",
                        realm.getName(), userId, groupId));
                return false;
            }

            try {
                clients.identitystore().createGroupMembership(CreateGroupMembershipRequest.builder()
                        .identityStoreId(config.identityStoreId)
                        .groupId(awsGroupId)
                        .memberId(MemberId.builder().userId(awsUserId).build())
                        .build());
                return true;
            } catch (ConflictException e) {
                return true;
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, String.format("Failed to sync membership to Identity Center. realm=%s userId=%s groupId=%s",
                    realm.getName(), userId, groupId), e);
            return false;
        }
    }

    public boolean deleteSingleGroupMembership(KeycloakSession session, RealmModel realm, String userId, String groupId) {
        if (isBlank(userId) || isBlank(groupId)) {
            return false;
        }

        UserModel user = session.users().getUserById(realm, userId);
        GroupModel group = realm.getGroupById(groupId);
        if (user == null || group == null) {
            LOG.warning(String.format("Cannot resolve membership objects for delete in realm. realm=%s userId=%s groupId=%s",
                    realm.getName(), userId, groupId));
            return false;
        }

        AwsConfig config = AwsConfig.fromRealm(realm);
        try (AwsClientFactory.AwsClients clients = AwsClientFactory.create(config)) {
            String awsUserId = resolveAwsUserId(clients.identitystore(), config, user);
            String awsGroupId = findGroupIdByDisplayName(clients.identitystore(), config.identityStoreId, group.getName());
            if (awsUserId == null || awsGroupId == null) {
                LOG.warning(String.format("Cannot resolve AWS membership targets for delete. realm=%s userId=%s groupId=%s",
                        realm.getName(), userId, groupId));
                return false;
            }

            String membershipId = findMembershipId(clients.identitystore(), config.identityStoreId, awsGroupId, awsUserId);
            if (membershipId == null) {
                return true;
            }

            clients.identitystore().deleteGroupMembership(DeleteGroupMembershipRequest.builder()
                    .identityStoreId(config.identityStoreId)
                    .membershipId(membershipId)
                    .build());
            return true;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, String.format("Failed to delete membership in Identity Center. realm=%s userId=%s groupId=%s",
                    realm.getName(), userId, groupId), e);
            return false;
        }
    }

    public boolean deleteSingleUser(KeycloakSession session, RealmModel realm, String userId, String usernameHint) {
        AwsConfig config = AwsConfig.fromRealm(realm);

        UserModel user = null;
        if (userId != null && !userId.isBlank()) {
            user = session.users().getUserById(realm, userId);
        }

        Set<String> userNameCandidates = new LinkedHashSet<>();
        if (user != null) {
            String preferred = resolveAwsUserName(user, config.userNameSource);
            if (preferred != null) {
                userNameCandidates.add(preferred);
            }
            addCandidate(userNameCandidates, user.getUsername());
            addCandidate(userNameCandidates, user.getEmail());
        }
        addCandidate(userNameCandidates, usernameHint);

        if (userNameCandidates.isEmpty()) {
            LOG.warning(String.format("Cannot resolve username for delete event. realm=%s userId=%s", realm.getName(), userId));
            return false;
        }

        try (AwsClientFactory.AwsClients clients = AwsClientFactory.create(config)) {
            String existingUserId = null;
            String matchedUserName = null;
            for (String candidate : userNameCandidates) {
                existingUserId = findUserIdByUserName(clients.identitystore(), config.identityStoreId, candidate);
                if (existingUserId != null) {
                    matchedUserName = candidate;
                    break;
                }
            }

            if (existingUserId == null) {
                LOG.fine(String.format("User not found in Identity Center, skip delete. realm=%s userId=%s", realm.getName(), userId));
                return true;
            }

            clients.identitystore().deleteUser(DeleteUserRequest.builder()
                    .identityStoreId(config.identityStoreId)
                    .userId(existingUserId)
                    .build());
            LOG.fine(String.format("Deleted user in Identity Center. realm=%s username=%s", realm.getName(), matchedUserName));
            return true;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, String.format("Failed to delete user from Identity Center. realm=%s userId=%s", realm.getName(), userId), e);
            return false;
        }
    }

    public boolean deleteSingleGroup(RealmModel realm, String groupId, String groupNameHint) {
        String groupName = groupNameHint;
        if ((groupName == null || groupName.isBlank()) && groupId != null && !groupId.isBlank()) {
            GroupModel group = realm.getGroupById(groupId);
            if (group != null) {
                groupName = group.getName();
            }
        }
        if (groupName == null || groupName.isBlank()) {
            LOG.warning(String.format("Cannot resolve group name for delete event. realm=%s groupId=%s", realm.getName(), groupId));
            return false;
        }

        AwsConfig config = AwsConfig.fromRealm(realm);
        try (AwsClientFactory.AwsClients clients = AwsClientFactory.create(config)) {
            String existingGroupId = findGroupIdByDisplayName(clients.identitystore(), config.identityStoreId, groupName);
            if (existingGroupId == null) {
                LOG.fine(String.format("Group not found in Identity Center, skip delete. realm=%s group=%s", realm.getName(), groupName));
                return true;
            }
            clients.identitystore().deleteGroup(DeleteGroupRequest.builder()
                    .identityStoreId(config.identityStoreId)
                    .groupId(existingGroupId)
                    .build());
            return true;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, String.format("Failed to delete group from Identity Center: %s", groupName), e);
            return false;
        }
    }

    private boolean upsertUser(IdentitystoreClient client, AwsConfig config, UserModel user) {
        String awsUserName = resolveAwsUserName(user, config.userNameSource);
        if (awsUserName == null) {
            LOG.warning(String.format("Cannot resolve aws username. realmUserId=%s source=%s", user.getId(), config.userNameSource));
            return false;
        }

        String displayName = buildDisplayName(user);
        CreateUserRequest request = CreateUserRequest.builder()
                .identityStoreId(config.identityStoreId)
                .userName(awsUserName)
                .displayName(displayName)
                .build();
        try {
            client.createUser(request);
            return true;
        } catch (ConflictException e) {
            return updateExistingUser(client, config.identityStoreId, awsUserName, displayName);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, String.format("Failed to sync user to Identity Center: %s", awsUserName), e);
            return false;
        }
    }

    private boolean upsertGroup(IdentitystoreClient client, String storeId, GroupModel group) {
        CreateGroupRequest request = CreateGroupRequest.builder()
                .identityStoreId(storeId)
                .displayName(group.getName())
                .build();
        try {
            client.createGroup(request);
            return true;
        } catch (ConflictException e) {
            return updateExistingGroup(client, storeId, group.getName());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, String.format("Failed to sync group to Identity Center: %s", group.getName()), e);
            return false;
        }
    }

    private boolean updateExistingUser(IdentitystoreClient client, String storeId, String username, String displayName) {
        String existingUserId = findUserIdByUserName(client, storeId, username);
        if (existingUserId == null) {
            LOG.warning(String.format("User conflict but no existing user found. username=%s", username));
            return false;
        }

        try {
            client.updateUser(UpdateUserRequest.builder()
                    .identityStoreId(storeId)
                    .userId(existingUserId)
                    .operations(
                            AttributeOperation.builder()
                                    .attributePath("DisplayName")
                                    .attributeValue(Document.fromString(displayName))
                                    .build()
                    )
                    .build());
            return true;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, String.format("Failed to update existing user in Identity Center: %s", username), e);
            return false;
        }
    }

    private boolean updateExistingGroup(IdentitystoreClient client, String storeId, String groupName) {
        String existingGroupId = findGroupIdByDisplayName(client, storeId, groupName);
        if (existingGroupId == null) {
            LOG.warning(String.format("Group conflict but no existing group found. group=%s", groupName));
            return false;
        }

        try {
            client.updateGroup(UpdateGroupRequest.builder()
                    .identityStoreId(storeId)
                    .groupId(existingGroupId)
                    .operations(
                            AttributeOperation.builder()
                                    .attributePath("DisplayName")
                                    .attributeValue(Document.fromString(groupName))
                                    .build()
                    )
                    .build());
            return true;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, String.format("Failed to update existing group in Identity Center: %s", groupName), e);
            return false;
        }
    }

    private String resolveAwsUserId(IdentitystoreClient client, AwsConfig config, UserModel user) {
        String userName = resolveAwsUserName(user, config.userNameSource);
        if (isBlank(userName)) {
            return null;
        }
        return findUserIdByUserName(client, config.identityStoreId, userName);
    }

    private String findUserIdByUserName(IdentitystoreClient client, String storeId, String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        try {
            return client.getUserId(GetUserIdRequest.builder()
                            .identityStoreId(storeId)
                            .alternateIdentifier(builder -> builder.uniqueAttribute(UniqueAttribute.builder()
                                    .attributePath("UserName")
                                    .attributeValue(Document.fromString(username))
                                    .build()))
                            .build())
                    .userId();
        } catch (ResourceNotFoundException e) {
            return null;
        }
    }

    private String findGroupIdByDisplayName(IdentitystoreClient client, String storeId, String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return null;
        }
        try {
            return client.getGroupId(GetGroupIdRequest.builder()
                            .identityStoreId(storeId)
                            .alternateIdentifier(builder -> builder.uniqueAttribute(UniqueAttribute.builder()
                                    .attributePath("DisplayName")
                                    .attributeValue(Document.fromString(displayName))
                                    .build()))
                            .build())
                    .groupId();
        } catch (ResourceNotFoundException e) {
            return null;
        }
    }

    private String findMembershipId(IdentitystoreClient client, String storeId, String groupId, String userId) {
        ListGroupMembershipsRequest request = ListGroupMembershipsRequest.builder()
                .identityStoreId(storeId)
                .groupId(groupId)
                .build();

        for (ListGroupMembershipsResponse page : client.listGroupMembershipsPaginator(request)) {
            for (GroupMembership membership : page.groupMemberships()) {
                MemberId member = membership.memberId();
                if (member != null && userId.equals(member.userId())) {
                    return membership.membershipId();
                }
            }
        }
        return null;
    }

    private String resolveAwsUserName(UserModel user, AwsConfig.UserNameSource source) {
        String primary = source == AwsConfig.UserNameSource.EMAIL ? user.getEmail() : user.getUsername();
        String fallback = source == AwsConfig.UserNameSource.EMAIL ? user.getUsername() : user.getEmail();

        if (isBlank(primary)) {
            if (!isBlank(fallback)) {
                LOG.fine(String.format("Primary username source is empty, fallback to alternate field. userId=%s source=%s",
                        user.getId(), source));
                return fallback.trim();
            }
            return null;
        }
        return primary.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void addCandidate(Set<String> values, String candidate) {
        if (!isBlank(candidate)) {
            values.add(candidate.trim());
        }
    }

    private String buildDisplayName(UserModel user) {
        String firstName = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String lastName = user.getLastName() == null ? "" : user.getLastName().trim();
        String full = (firstName + " " + lastName).trim();
        return full.isEmpty() ? user.getUsername() : full;
    }

    public record SyncResult(
            int usersProcessed,
            int groupsProcessed,
            int usersFailed,
            int groupsFailed
    ) {
        public boolean hasFailures() {
            return usersFailed > 0 || groupsFailed > 0;
        }
    }
}
