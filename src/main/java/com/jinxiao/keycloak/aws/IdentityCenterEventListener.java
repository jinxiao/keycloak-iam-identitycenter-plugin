
package com.jinxiao.keycloak.aws;

import org.keycloak.events.admin.*;
import org.keycloak.events.*;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.*;
import java.util.logging.Logger;

public class IdentityCenterEventListener implements EventListenerProvider {
    private static final Logger LOG = Logger.getLogger(IdentityCenterEventListener.class.getName());

    private final KeycloakSession session;

    public IdentityCenterEventListener(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public void onEvent(Event event) {}

    @Override
    public void onEvent(AdminEvent event, boolean includeRepresentation) {
        RealmModel realm = session.getContext().getRealm();
        if (realm == null) {
            return;
        }

        if (event == null || (event.getResourceType() != ResourceType.USER && event.getResourceType() != ResourceType.GROUP)) {
            return;
        }

        IdentityCenterSyncManager manager = new IdentityCenterSyncManager();

        switch (event.getOperationType()) {
            case CREATE:
            case UPDATE:
            case DELETE:
                IdentityCenterSyncManager.SyncResult result = manager.fullSync(session, realm);
                if (result.hasFailures()) {
                    LOG.warning(String.format("Event-triggered sync finished with failures. realm=%s usersFailed=%d groupsFailed=%d",
                            realm.getName(), result.usersFailed(), result.groupsFailed()));
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void close() {}
}
