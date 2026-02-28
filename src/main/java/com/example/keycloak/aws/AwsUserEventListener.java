
package com.example.keycloak.aws;

import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.models.*;

public class AwsUserEventListener implements EventListenerProvider {

    private final KeycloakSession session;

    public AwsUserEventListener(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public void onEvent(Event event) {

        if (event.getType().name().contains("UPDATE_PROFILE")) {
            RealmModel realm = session.getContext().getRealm();
            AwsConfig config = AwsConfig.fromRealm(realm);

            // Call IdentityStore API here to update user
            // (example intentionally simplified)
        }
    }

    @Override
    public void close() {}
}
