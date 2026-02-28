
package com.jinxiao.keycloak.aws;

import com.google.common.util.concurrent.RateLimiter;
import org.keycloak.models.*;
import software.amazon.awssdk.services.identitystore.IdentitystoreClient;
import software.amazon.awssdk.services.identitystore.model.*;

import java.util.*;

public class IdentityCenterSyncManager {

    public void fullSync(KeycloakSession session, RealmModel realm) {

        AwsConfig config = AwsConfig.fromRealm(realm);
        IdentitystoreClient client = AwsClientFactory.create(config);
        RateLimiter limiter = RateLimiter.create(config.maxQps);

        // Sync Users
        session.users().getUsersStream(realm).forEach(user -> {
            limiter.acquire();
            syncUser(client, config.identityStoreId, user);
        });

        // Sync Groups
        realm.getGroupsStream().forEach(group -> {
            limiter.acquire();
            syncGroup(client, config.identityStoreId, group);
        });
    }

    private void syncUser(IdentitystoreClient client, String storeId, UserModel user) {
        // Production implementation should check existence first
        CreateUserRequest request = CreateUserRequest.builder()
                .identityStoreId(storeId)
                .userName(user.getUsername())
                .displayName(user.getFirstName() + " " + user.getLastName())
                .build();
        try {
            client.createUser(request);
        } catch (Exception ignored) {}
    }

    private void syncGroup(IdentitystoreClient client, String storeId, GroupModel group) {
        CreateGroupRequest request = CreateGroupRequest.builder()
                .identityStoreId(storeId)
                .displayName(group.getName())
                .build();
        try {
            client.createGroup(request);
        } catch (Exception ignored) {}
    }
}
