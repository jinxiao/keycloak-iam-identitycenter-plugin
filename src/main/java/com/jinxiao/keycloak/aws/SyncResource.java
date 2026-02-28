
package com.jinxiao.keycloak.aws;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.keycloak.models.*;

@Path("")
public class SyncResource {

    private final KeycloakSession session;

    public SyncResource(KeycloakSession session) {
        this.session = session;
    }

    @POST
    @Path("full-sync")
    public Response fullSync() {

        RealmModel realm = session.getContext().getRealm();
        new IdentityCenterSyncManager().fullSync(session, realm);

        return Response.ok("Full sync completed").build();
    }
}
