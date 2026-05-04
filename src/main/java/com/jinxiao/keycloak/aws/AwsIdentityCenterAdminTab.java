package com.jinxiao.keycloak.aws;

import org.keycloak.Config;
import org.keycloak.component.ComponentModel;
import org.keycloak.component.ComponentValidationException;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;
import org.keycloak.services.ui.extend.UiTabProvider;
import org.keycloak.services.ui.extend.UiTabProviderFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AwsIdentityCenterAdminTab implements UiTabProvider, UiTabProviderFactory<ComponentModel> {
    private static final String TAB_ID = "AWS Identity Center";
    private static final String ROUTE_PATH = "/:realm/realm-settings/:tab";
    private static final String ROUTE_TAB = "aws-identity-center";

    private static final String ENABLED = "aws.enabled";
    private static final String REGION = "aws.region";
    private static final String IDENTITY_STORE_ID = "aws.identityStoreId";
    private static final String ROLE_ARN = "aws.roleArn";
    private static final String MAX_QPS = "aws.maxQps";
    private static final String USER_NAME_SOURCE = "aws.userNameSource";

    @Override
    public String getId() {
        return TAB_ID;
    }

    @Override
    public String getHelpText() {
        return "Configure AWS IAM Identity Center synchronization for this realm.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return ProviderConfigurationBuilder.create()
                .property()
                .name(ENABLED)
                .label("Enable synchronization")
                .helpText("When enabled, this realm can synchronize users, groups, and memberships to AWS IAM Identity Center.")
                .type(ProviderConfigProperty.BOOLEAN_TYPE)
                .defaultValue(false)
                .add()
                .property()
                .name(REGION)
                .label("AWS Region")
                .helpText("AWS Region for the IAM Identity Center IdentityStore API, for example us-east-1.")
                .type(ProviderConfigProperty.STRING_TYPE)
                .required(true)
                .add()
                .property()
                .name(IDENTITY_STORE_ID)
                .label("Identity store ID")
                .helpText("IAM Identity Center identity store ID, for example d-1234567890.")
                .type(ProviderConfigProperty.STRING_TYPE)
                .required(true)
                .add()
                .property()
                .name(ROLE_ARN)
                .label("AssumeRole ARN")
                .helpText("Optional IAM role ARN to assume before calling the IdentityStore API.")
                .type(ProviderConfigProperty.STRING_TYPE)
                .add()
                .property()
                .name(MAX_QPS)
                .label("Max QPS")
                .helpText("IdentityStore API rate limit. Defaults to 5.")
                .type(ProviderConfigProperty.INTEGER_TYPE)
                .defaultValue(5)
                .add()
                .property()
                .name(USER_NAME_SOURCE)
                .label("AWS username source")
                .helpText("Keycloak user field used as the AWS Identity Center UserName.")
                .type(ProviderConfigProperty.LIST_TYPE)
                .options("username", "email")
                .defaultValue("username")
                .add()
                .build();
    }

    @Override
    public String getPath() {
        return ROUTE_PATH;
    }

    @Override
    public Map<String, String> getParams() {
        Map<String, String> params = new HashMap<>();
        params.put("tab", ROUTE_TAB);
        return params;
    }

    @Override
    public void validateConfiguration(KeycloakSession session, RealmModel realm, ComponentModel model) {
        String region = normalize(model.get(REGION));
        if (region == null) {
            throw new ComponentValidationException("AWS Region is required.");
        }

        String identityStoreId = normalize(model.get(IDENTITY_STORE_ID));
        if (identityStoreId == null) {
            throw new ComponentValidationException("Identity store ID is required.");
        }

        String maxQps = normalize(model.get(MAX_QPS));
        if (maxQps != null) {
            try {
                if (Integer.parseInt(maxQps) <= 0) {
                    throw new ComponentValidationException("Max QPS must be greater than 0.");
                }
            } catch (NumberFormatException e) {
                throw new ComponentValidationException("Max QPS must be an integer.");
            }
        }

        String userNameSource = normalize(model.get(USER_NAME_SOURCE));
        if (userNameSource != null && !"username".equals(userNameSource) && !"email".equals(userNameSource)) {
            throw new ComponentValidationException("AWS username source must be username or email.");
        }
    }

    @Override
    public void onCreate(KeycloakSession session, RealmModel realm, ComponentModel model) {
        saveRealmAttributes(realm, model);
    }

    @Override
    public void onUpdate(KeycloakSession session, RealmModel realm, ComponentModel oldModel, ComponentModel model) {
        saveRealmAttributes(realm, model);
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }

    private void saveRealmAttributes(RealmModel realm, ComponentModel model) {
        setRequiredAttribute(realm, model, ENABLED, "false");
        setRequiredAttribute(realm, model, REGION, null);
        setRequiredAttribute(realm, model, IDENTITY_STORE_ID, null);
        setOptionalAttribute(realm, model, ROLE_ARN);
        setRequiredAttribute(realm, model, MAX_QPS, "5");
        setRequiredAttribute(realm, model, USER_NAME_SOURCE, "username");
    }

    private void setRequiredAttribute(RealmModel realm, ComponentModel model, String key, String defaultValue) {
        String value = normalize(model.get(key));
        if (value == null) {
            value = defaultValue;
        }
        if (value != null) {
            realm.setAttribute(key, value);
        }
    }

    private void setOptionalAttribute(RealmModel realm, ComponentModel model, String key) {
        String value = normalize(model.get(key));
        if (value == null) {
            realm.removeAttribute(key);
            return;
        }
        realm.setAttribute(key, value);
    }

    private String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
