
package com.jinxiao.keycloak.aws;

import org.keycloak.models.RealmModel;

public class AwsConfig {
    public String region;
    public String roleArn;
    public String identityStoreId;
    public int maxQps;

    public static AwsConfig fromRealm(RealmModel realm) {
        AwsConfig c = new AwsConfig();
        c.region = realm.getAttribute("aws.region");
        c.roleArn = realm.getAttribute("aws.roleArn");
        c.identityStoreId = realm.getAttribute("aws.identityStoreId");
        c.maxQps = Integer.parseInt(
                realm.getAttribute("aws.maxQps") == null ? "5" :
                realm.getAttribute("aws.maxQps"));
        return c;
    }
}
