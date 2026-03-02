
package com.jinxiao.keycloak.aws;

import org.keycloak.models.RealmModel;

public class AwsConfig {
    public boolean enabled;
    public String region;
    public String roleArn;
    public String identityStoreId;
    public int maxQps;

    public static AwsConfig fromRealm(RealmModel realm) {
        AwsConfig c = new AwsConfig();
        c.enabled = isEnabled(realm);
        if (!c.enabled) {
            return c;
        }
        c.region = required(realm.getAttribute("aws.region"), "aws.region");
        c.roleArn = realm.getAttribute("aws.roleArn");
        c.identityStoreId = required(realm.getAttribute("aws.identityStoreId"), "aws.identityStoreId");
        c.maxQps = parsePositiveInt(realm.getAttribute("aws.maxQps"), 5, "aws.maxQps");
        return c;
    }

    public static boolean isEnabled(RealmModel realm) {
        return parseBoolean(realm.getAttribute("aws.enabled"), false, "aws.enabled");
    }

    private static String required(String value, String key) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing realm attribute: " + key);
        }
        return value.trim();
    }

    private static int parsePositiveInt(String value, int defaultValue, String key) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed <= 0) {
                throw new IllegalArgumentException("Realm attribute must be > 0: " + key);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Realm attribute must be an integer: " + key, e);
        }
    }

    private static boolean parseBoolean(String value, boolean defaultValue, String key) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        String normalized = value.trim().toLowerCase();
        if ("true".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized)) {
            return false;
        }
        throw new IllegalArgumentException("Realm attribute must be true or false: " + key);
    }
}
