package com.jinxiao.keycloak.aws;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.Test;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.identitystore.IdentitystoreClient;

import java.net.URI;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdentityStoreWireMockIT {

    @Test
    void syncSingleMembershipUsesAwsSdkClientAgainstMockIdentityStoreEndpoint() {
        WireMockServer server = new WireMockServer(options().dynamicPort());
        server.start();
        try {
            server.stubFor(post(urlEqualTo("/"))
                    .withRequestBody(containing("\"AttributePath\":\"UserName\""))
                    .willReturn(okJson("{\"UserId\":\"aws-user-1\"}")));
            server.stubFor(post(urlEqualTo("/"))
                    .withRequestBody(containing("\"AttributePath\":\"DisplayName\""))
                    .willReturn(okJson("{\"GroupId\":\"aws-group-1\"}")));
            server.stubFor(post(urlEqualTo("/"))
                    .withRequestBody(containing("\"MemberId\""))
                    .willReturn(okJson("{\"MembershipId\":\"aws-membership-1\"}")));

            try (IdentitystoreClient client = IdentitystoreClient.builder()
                    .endpointOverride(URI.create(server.baseUrl()))
                    .region(Region.US_EAST_1)
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("test-access-key", "test-secret-key")))
                    .build()) {
                RealmModel realm = enabledRealm();
                boolean ok = managerWith(client).syncSingleGroupMembership(
                        sessionWithUser(realm, "kc-user-1", "kc-group-1"),
                        realm,
                        "kc-user-1",
                        "kc-group-1"
                );

                assertTrue(ok);
            }

            server.verify(postRequestedFor(urlEqualTo("/"))
                    .withRequestBody(containing("\"IdentityStoreId\":\"d-test\""))
                    .withRequestBody(containing("\"AttributePath\":\"UserName\""))
                    .withRequestBody(containing("\"AttributeValue\":\"alice\"")));
            server.verify(postRequestedFor(urlEqualTo("/"))
                    .withRequestBody(containing("\"AttributePath\":\"DisplayName\""))
                    .withRequestBody(containing("\"AttributeValue\":\"Developers\"")));
            server.verify(postRequestedFor(urlEqualTo("/"))
                    .withRequestBody(containing("\"GroupId\":\"aws-group-1\""))
                    .withRequestBody(containing("\"UserId\":\"aws-user-1\"")));
        } finally {
            server.stop();
        }
    }

    private IdentityCenterSyncManager managerWith(IdentitystoreClient client) {
        return new IdentityCenterSyncManager(config -> AwsClientFactory.AwsClients.of(client));
    }

    private RealmModel enabledRealm() {
        RealmModel realm = mock(RealmModel.class);
        when(realm.getName()).thenReturn("test");
        when(realm.getAttribute("aws.enabled")).thenReturn("true");
        when(realm.getAttribute("aws.region")).thenReturn("us-east-1");
        when(realm.getAttribute("aws.identityStoreId")).thenReturn("d-test");
        when(realm.getAttribute("aws.roleArn")).thenReturn(null);
        when(realm.getAttribute("aws.maxQps")).thenReturn("1000");
        when(realm.getAttribute("aws.userNameSource")).thenReturn("username");
        return realm;
    }

    private KeycloakSession sessionWithUser(RealmModel realm, String userId, String groupId) {
        KeycloakSession session = mock(KeycloakSession.class);
        UserProvider userProvider = mock(UserProvider.class);
        UserModel user = mock(UserModel.class);
        GroupModel group = mock(GroupModel.class);

        when(session.users()).thenReturn(userProvider);
        when(userProvider.getUserById(org.mockito.ArgumentMatchers.any(RealmModel.class), org.mockito.ArgumentMatchers.eq(userId)))
                .thenReturn(user);
        when(user.getId()).thenReturn(userId);
        when(user.getUsername()).thenReturn("alice");
        when(user.getEmail()).thenReturn("alice@example.com");
        when(user.getFirstName()).thenReturn("Alice");
        when(user.getLastName()).thenReturn("Example");

        when(realm.getGroupById(groupId)).thenReturn(group);
        when(group.getId()).thenReturn(groupId);
        when(group.getName()).thenReturn("Developers");
        return session;
    }
}
