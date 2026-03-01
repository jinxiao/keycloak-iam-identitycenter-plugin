
package com.jinxiao.keycloak.aws;

import com.google.common.util.concurrent.RateLimiter;
import org.keycloak.models.*;
import software.amazon.awssdk.services.identitystore.model.ConflictException;
import software.amazon.awssdk.services.identitystore.model.*;
import java.util.Collections;
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
            // Sync Users
            for (UserModel user : session.users().searchForUserStream(realm, Collections.emptyMap(), null, null).toList()) {
                limiter.acquire();
                usersProcessed++;
                if (!syncUser(clients.identitystore(), config.identityStoreId, user)) {
                    usersFailed++;
                }
            }

            // Sync Groups
            for (GroupModel group : realm.getGroupsStream().toList()) {
                limiter.acquire();
                groupsProcessed++;
                if (!syncGroup(clients.identitystore(), config.identityStoreId, group)) {
                    groupsFailed++;
                }
            }
        }

        return new SyncResult(usersProcessed, groupsProcessed, usersFailed, groupsFailed);
    }

    private boolean syncUser(software.amazon.awssdk.services.identitystore.IdentitystoreClient client, String storeId, UserModel user) {
        CreateUserRequest request = CreateUserRequest.builder()
                .identityStoreId(storeId)
                .userName(user.getUsername())
                .displayName(user.getFirstName() + " " + user.getLastName())
                .build();
        try {
            client.createUser(request);
            return true;
        } catch (ConflictException e) {
            // Idempotent behavior: existing users are treated as already synchronized.
            LOG.fine(String.format("User already exists in Identity Center: %s", user.getUsername()));
            return true;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, String.format("Failed to sync user to Identity Center: %s", user.getUsername()), e);
            return false;
        }
    }

    private boolean syncGroup(software.amazon.awssdk.services.identitystore.IdentitystoreClient client, String storeId, GroupModel group) {
        CreateGroupRequest request = CreateGroupRequest.builder()
                .identityStoreId(storeId)
                .displayName(group.getName())
                .build();
        try {
            client.createGroup(request);
            return true;
        } catch (ConflictException e) {
            LOG.fine(String.format("Group already exists in Identity Center: %s", group.getName()));
            return true;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, String.format("Failed to sync group to Identity Center: %s", group.getName()), e);
            return false;
        }
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
