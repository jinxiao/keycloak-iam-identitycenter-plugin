
package com.example.keycloak.aws;

import org.keycloak.models.KeycloakSession;
import org.keycloak.services.resource.RealmResourceProvider;

public class AwsIdentityCenterProvider implements RealmResourceProvider {

    private final KeycloakSession session;

    public AwsIdentityCenterProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public Object getResource() {
        return new SyncResource(session);
    }

    @Override
    public void close() {}
}
