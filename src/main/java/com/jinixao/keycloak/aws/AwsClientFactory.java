
package com.jinxiao.keycloak.aws;

import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.identitystore.IdentitystoreClient;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;

public class AwsClientFactory {

    public static IdentitystoreClient create(AwsConfig config) {

        AwsCredentialsProvider base = DefaultCredentialsProvider.create();
        AwsCredentialsProvider finalProvider = base;

        if (config.roleArn != null && !config.roleArn.isEmpty()) {

            StsClient sts = StsClient.builder()
                    .region(Region.of(config.region))
                    .credentialsProvider(base)
                    .build();

            finalProvider = StsAssumeRoleCredentialsProvider.builder()
                    .stsClient(sts)
                    .refreshRequest(r -> r.roleArn(config.roleArn)
                            .roleSessionName("kc-sync"))
                    .build();
        }

        return IdentitystoreClient.builder()
                .region(Region.of(config.region))
                .credentialsProvider(finalProvider)
                .build();
    }
}
