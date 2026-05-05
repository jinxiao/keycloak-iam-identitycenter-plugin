package com.jinxiao.keycloak.aws;

import org.junit.jupiter.api.Test;
import org.keycloak.component.ComponentModel;
import org.keycloak.component.ComponentValidationException;
import org.keycloak.models.RealmModel;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AwsIdentityCenterAdminTabTest {

    @Test
    void onCreatePersistsAdminConsoleConfigAsRealmAttributes() {
        AwsIdentityCenterAdminTab tab = new AwsIdentityCenterAdminTab();
        RealmModel realm = mock(RealmModel.class);
        ComponentModel model = validModel();
        when(model.get("aws.roleArn")).thenReturn(" ");

        tab.onCreate(null, realm, model);

        verify(realm).setAttribute("aws.enabled", "true");
        verify(realm).setAttribute("aws.region", "us-east-1");
        verify(realm).setAttribute("aws.identityStoreId", "d-test");
        verify(realm).removeAttribute("aws.roleArn");
        verify(realm).setAttribute("aws.maxQps", "10");
        verify(realm).setAttribute("aws.userNameSource", "email");
    }

    @Test
    void validateConfigurationRejectsInvalidMaxQps() {
        AwsIdentityCenterAdminTab tab = new AwsIdentityCenterAdminTab();
        ComponentModel model = validModel();
        when(model.get("aws.maxQps")).thenReturn("0");

        assertThrows(ComponentValidationException.class,
                () -> tab.validateConfiguration(null, null, model));
    }

    private ComponentModel validModel() {
        ComponentModel model = mock(ComponentModel.class);
        when(model.get("aws.enabled")).thenReturn("true");
        when(model.get("aws.region")).thenReturn("us-east-1");
        when(model.get("aws.identityStoreId")).thenReturn("d-test");
        when(model.get("aws.roleArn")).thenReturn("arn:aws:iam::123456789012:role/KeycloakSyncRole");
        when(model.get("aws.maxQps")).thenReturn("10");
        when(model.get("aws.userNameSource")).thenReturn("email");
        return model;
    }
}
