package com.jinxiao.keycloak.aws;

import org.junit.jupiter.api.Test;
import org.keycloak.models.RealmModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AwsConfigTest {

    @Test
    void disabledRealmDoesNotRequireAwsAttributes() {
        RealmModel realm = mock(RealmModel.class);
        when(realm.getAttribute("aws.enabled")).thenReturn(null);

        AwsConfig config = AwsConfig.fromRealm(realm);

        assertFalse(config.enabled);
    }

    @Test
    void enabledRealmParsesRequiredAndDefaultAttributes() {
        RealmModel realm = mock(RealmModel.class);
        when(realm.getAttribute("aws.enabled")).thenReturn("true");
        when(realm.getAttribute("aws.region")).thenReturn(" us-east-1 ");
        when(realm.getAttribute("aws.identityStoreId")).thenReturn(" d-test ");
        when(realm.getAttribute("aws.roleArn")).thenReturn(null);
        when(realm.getAttribute("aws.maxQps")).thenReturn(null);
        when(realm.getAttribute("aws.userNameSource")).thenReturn(null);

        AwsConfig config = AwsConfig.fromRealm(realm);

        assertTrue(config.enabled);
        assertEquals("us-east-1", config.region);
        assertEquals("d-test", config.identityStoreId);
        assertEquals(5, config.maxQps);
        assertEquals(AwsConfig.UserNameSource.USERNAME, config.userNameSource);
    }

    @Test
    void invalidMaxQpsFailsFast() {
        RealmModel realm = enabledRealm();
        when(realm.getAttribute("aws.maxQps")).thenReturn("0");

        assertThrows(IllegalArgumentException.class, () -> AwsConfig.fromRealm(realm));
    }

    @Test
    void invalidUserNameSourceFailsFast() {
        RealmModel realm = enabledRealm();
        when(realm.getAttribute("aws.userNameSource")).thenReturn("displayName");

        assertThrows(IllegalArgumentException.class, () -> AwsConfig.fromRealm(realm));
    }

    private RealmModel enabledRealm() {
        RealmModel realm = mock(RealmModel.class);
        when(realm.getAttribute("aws.enabled")).thenReturn("true");
        when(realm.getAttribute("aws.region")).thenReturn("us-east-1");
        when(realm.getAttribute("aws.identityStoreId")).thenReturn("d-test");
        when(realm.getAttribute("aws.roleArn")).thenReturn(null);
        when(realm.getAttribute("aws.maxQps")).thenReturn("5");
        when(realm.getAttribute("aws.userNameSource")).thenReturn("username");
        return realm;
    }
}
