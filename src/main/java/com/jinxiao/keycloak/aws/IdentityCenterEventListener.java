package com.jinxiao.keycloak.aws;

import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;

import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IdentityCenterEventListener implements EventListenerProvider {
    private static final Logger LOG = Logger.getLogger(IdentityCenterEventListener.class.getName());
    private static final Set<String> USER_UPSERT_EVENT_TYPES = Set.of(
            "REGISTER",
            "UPDATE_PROFILE",
            "UPDATE_EMAIL",
            "UPDATE_PASSWORD"
    );

    private final KeycloakSession session;

    public IdentityCenterEventListener(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public void onEvent(Event event) {
        if (event == null || event.getType() == null) {
            return;
        }

        RealmModel realm = resolveRealm(event.getRealmId());
        if (!isSyncEnabled(realm)) {
            return;
        }

        IdentityCenterSyncManager manager = new IdentityCenterSyncManager();
        String eventType = event.getType().name();

        if (USER_UPSERT_EVENT_TYPES.contains(eventType)) {
            boolean ok = manager.syncSingleUser(session, realm, event.getUserId());
            logFailure(ok, realm, "user-event", eventType, event.getUserId());
            return;
        }

        if ("DELETE_ACCOUNT".equals(eventType)) {
            String usernameHint = extractUsername(event.getDetails());
            boolean ok = manager.deleteSingleUser(session, realm, event.getUserId(), usernameHint);
            logFailure(ok, realm, "user-event", eventType, event.getUserId());
        }
    }

    @Override
    public void onEvent(AdminEvent event, boolean includeRepresentation) {
        if (event == null || event.getOperationType() == null) {
            return;
        }
        if (event.getResourceType() != ResourceType.USER
                && event.getResourceType() != ResourceType.GROUP
                && event.getResourceType() != ResourceType.GROUP_MEMBERSHIP) {
            return;
        }

        RealmModel realm = resolveRealm(event.getRealmId());
        if (!isSyncEnabled(realm)) {
            return;
        }

        IdentityCenterSyncManager manager = new IdentityCenterSyncManager();

        if (event.getResourceType() == ResourceType.USER) {
            String resourceId = extractResourceId(event.getResourcePath());
            String usernameHint = extractFieldFromRepresentation(event.getRepresentation(), "username");
            boolean ok = event.getOperationType() == OperationType.DELETE
                    ? manager.deleteSingleUser(session, realm, resourceId, usernameHint)
                    : manager.syncSingleUser(session, realm, resourceId);
            logFailure(ok, realm, "admin-event", event.getOperationType().name(), resourceId);
            return;
        }

        if (event.getResourceType() == ResourceType.GROUP) {
            String resourceId = extractResourceId(event.getResourcePath());
            String groupNameHint = extractFieldFromRepresentation(event.getRepresentation(), "name");
            boolean ok = event.getOperationType() == OperationType.DELETE
                    ? manager.deleteSingleGroup(realm, resourceId, groupNameHint)
                    : manager.syncSingleGroup(realm, resourceId);
            logFailure(ok, realm, "admin-event", event.getOperationType().name(), resourceId);
            return;
        }

        MembershipRef membershipRef = extractGroupMembershipRef(event.getResourcePath());
        if (membershipRef == null) {
            LOG.warning(String.format("Cannot parse group membership resource path. realm=%s path=%s",
                    realm.getName(), event.getResourcePath()));
            return;
        }

        boolean ok;
        if (event.getOperationType() == OperationType.DELETE) {
            ok = manager.deleteSingleGroupMembership(session, realm, membershipRef.userId(), membershipRef.groupId());
        } else if (event.getOperationType() == OperationType.CREATE || event.getOperationType() == OperationType.ACTION) {
            ok = manager.syncSingleGroupMembership(session, realm, membershipRef.userId(), membershipRef.groupId());
        } else {
            return;
        }

        logFailure(ok, realm, "admin-event", "GROUP_MEMBERSHIP_" + event.getOperationType().name(),
                membershipRef.userId() + ":" + membershipRef.groupId());
    }

    private RealmModel resolveRealm(String realmId) {
        if (realmId != null) {
            RealmModel byId = session.realms().getRealm(realmId);
            if (byId != null) {
                return byId;
            }
        }
        return session.getContext().getRealm();
    }

    private boolean isSyncEnabled(RealmModel realm) {
        return realm != null && AwsConfig.isEnabled(realm);
    }

    private void logFailure(boolean ok, RealmModel realm, String source, String eventType, String resourceId) {
        if (!ok) {
            LOG.warning(String.format("%s-triggered incremental sync failed. realm=%s event=%s resourceId=%s",
                    source,
                    realm == null ? "unknown" : realm.getName(),
                    eventType,
                    resourceId));
        }
    }

    private String extractResourceId(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            return null;
        }
        String[] segments = resourcePath.split("/");
        if (segments.length < 2) {
            return null;
        }
        return segments[1].isBlank() ? null : segments[1];
    }

    private MembershipRef extractGroupMembershipRef(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            return null;
        }

        String[] segments = resourcePath.split("/");
        String userId = null;
        String groupId = null;

        for (int i = 0; i < segments.length - 1; i++) {
            String segment = segments[i];
            String next = segments[i + 1];
            if (next == null || next.isBlank()) {
                continue;
            }
            if ("users".equals(segment)) {
                userId = next;
            } else if ("groups".equals(segment)) {
                groupId = next;
            } else if ("members".equals(segment)) {
                userId = next;
            }
        }

        if (userId == null || groupId == null) {
            return null;
        }
        return new MembershipRef(userId, groupId);
    }

    private String extractUsername(Map<String, String> details) {
        if (details == null) {
            return null;
        }
        String username = details.get("username");
        return (username == null || username.isBlank()) ? null : username;
    }

    private String extractFieldFromRepresentation(String representation, String fieldName) {
        if (representation == null || representation.isBlank()) {
            return null;
        }
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(representation);
        if (!matcher.find()) {
            return null;
        }
        String value = matcher.group(1);
        return value == null || value.isBlank() ? null : value;
    }

    private record MembershipRef(String userId, String groupId) {}

    @Override
    public void close() {}
}
