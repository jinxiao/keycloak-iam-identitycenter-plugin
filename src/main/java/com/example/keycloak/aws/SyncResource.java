
package com.example.keycloak.aws;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.keycloak.models.*;

@Path("")
public class SyncResource {

    private final KeycloakSession session;
    private static SyncProgress progress = new SyncProgress();

    public SyncResource(KeycloakSession session) {
        this.session = session;
    }

    @POST
    @Path("sync")
    public Response sync() {

        RealmModel realm = session.getContext().getRealm();
        AwsConfig config = AwsConfig.fromRealm(realm);

        new Thread(() -> {
            new AwsSyncService().fullSync(session, realm, config, progress);
        }).start();

        return Response.ok("Sync started").build();
    }

    @GET
    @Path("progress")
    public Response progress() {
        return Response.ok(progress.getProcessed()).build();
    }
}
