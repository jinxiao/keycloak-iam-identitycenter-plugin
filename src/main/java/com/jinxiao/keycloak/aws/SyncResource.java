
package com.jinxiao.keycloak.aws;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.keycloak.models.ClientModel;
import org.keycloak.models.*;
import org.keycloak.services.managers.AppAuthManager;
import org.keycloak.services.managers.AuthenticationManager.AuthResult;
import org.keycloak.models.Constants;
import org.keycloak.models.AdminRoles;

import java.util.LinkedHashMap;
import java.util.Map;

@Path("")
public class SyncResource {

    private final KeycloakSession session;

    public SyncResource(KeycloakSession session) {
        this.session = session;
    }

    @POST
    @Path("full-sync")
    @Produces(MediaType.APPLICATION_JSON)
    public Response fullSync() {
        RealmModel realm = session.getContext().getRealm();
        requireManageUsersPermission(realm);

        IdentityCenterSyncManager.SyncResult result = new IdentityCenterSyncManager().fullSync(session, realm);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", result.hasFailures() ? "partial_success" : "success");
        payload.put("usersProcessed", result.usersProcessed());
        payload.put("groupsProcessed", result.groupsProcessed());
        payload.put("usersFailed", result.usersFailed());
        payload.put("groupsFailed", result.groupsFailed());
        return Response.ok(payload).build();
    }

    private void requireManageUsersPermission(RealmModel realm) {
        AuthResult auth = new AppAuthManager.BearerTokenAuthenticator(session)
                .setRealm(realm)
                .setConnection(session.getContext().getConnection())
                .setHeaders(session.getContext().getRequestHeaders())
                .authenticate();

        if (auth == null) {
            throw new NotAuthorizedException("Bearer");
        }

        ClientModel realmManagement = realm.getClientByClientId(Constants.REALM_MANAGEMENT_CLIENT_ID);
        if (realmManagement == null) {
            throw new ForbiddenException("Missing realm-management client");
        }

        RoleModel manageUsersRole = realmManagement.getRole(AdminRoles.MANAGE_USERS);
        if (manageUsersRole == null || !auth.getUser().hasRole(manageUsersRole)) {
            throw new ForbiddenException("manage-users role is required");
        }
    }
}
