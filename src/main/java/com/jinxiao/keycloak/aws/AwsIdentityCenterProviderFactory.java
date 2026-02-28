
package com.jinxiao.keycloak.aws;

import org.keycloak.Config;
import org.keycloak.models.*;
import org.keycloak.services.resource.*;

public class AwsIdentityCenterProviderFactory
        implements RealmResourceProviderFactory {

    @Override
    public String getId() {
        return "aws-identitycenter-sync";
    }

    @Override
    public RealmResourceProvider create(KeycloakSession session) {
        return new AwsIdentityCenterProvider(session);
    }

    @Override public void init(Config.Scope config) {}
    @Override public void postInit(KeycloakSessionFactory factory) {}
    @Override public void close() {}
}
