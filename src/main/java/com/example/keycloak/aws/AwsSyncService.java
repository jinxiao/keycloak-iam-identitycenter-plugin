
package com.example.keycloak.aws;

import com.google.common.util.concurrent.RateLimiter;
import org.keycloak.models.*;
import software.amazon.awssdk.services.identitystore.IdentitystoreClient;
import software.amazon.awssdk.services.identitystore.model.*;

import java.util.ArrayList;
import java.util.List;

public class AwsSyncService {

    public void fullSync(KeycloakSession session, RealmModel realm, AwsConfig config,
                         SyncProgress progress) {

        IdentitystoreClient client = AwsClientFactory.create(config);
        RateLimiter limiter = RateLimiter.create(config.maxQps);

        ListUsersRequest request = ListUsersRequest.builder()
                .identityStoreId(config.identityStoreId)
                .build();

        List<User> batch = new ArrayList<>();

        for (User user : client.listUsersPaginator(request).users()) {

            limiter.acquire();
            batch.add(user);

            if (batch.size() == 30) {
                processBatch(session, realm, batch);
                progress.increment(batch.size());
                batch.clear();
            }
        }

        if (!batch.isEmpty()) {
            processBatch(session, realm, batch);
            progress.increment(batch.size());
        }
    }

    private void processBatch(KeycloakSession session, RealmModel realm,
                              List<User> batch) {

        for (User user : batch) {

            UserModel kcUser = session.users().getUserByUsername(realm, user.userName());
            if (kcUser == null) {
                kcUser = session.users().addUser(realm, user.userName());
            }

            if (!user.emails().isEmpty()) {
                kcUser.setEmail(user.emails().get(0).value());
            }
        }
    }
}
