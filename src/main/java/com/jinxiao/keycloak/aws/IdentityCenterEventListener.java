
package com.jinxiao.keycloak.aws;

import org.keycloak.events.admin.*;
import org.keycloak.events.*;
import org.keycloak.models.*;

public class IdentityCenterEventListener implements EventListenerProvider {

    private final KeycloakSession session;

    public IdentityCenterEventListener(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public void onEvent(Event event) {}

    @Override
    public void onEvent(AdminEvent event, boolean includeRepresentation) {

        RealmModel realm = session.getContext().getRealm();
        IdentityCenterSyncManager manager = new IdentityCenterSyncManager();

        switch (event.getOperationType()) {
            case CREATE:
            case UPDATE:
            case DELETE:
                manager.fullSync(session, realm);
                break;
        }
    }

    @Override
    public void close() {}
}
