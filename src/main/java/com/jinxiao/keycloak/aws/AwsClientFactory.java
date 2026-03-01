
package com.jinxiao.keycloak.aws;

import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.identitystore.IdentitystoreClient;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;

public class AwsClientFactory {

    public static AwsClients create(AwsConfig config) {

        AwsCredentialsProvider base = DefaultCredentialsProvider.create();
        AwsCredentialsProvider provider = base;
        StsClient sts = null;

        if (config.roleArn != null && !config.roleArn.isEmpty()) {
            sts = StsClient.builder()
                    .region(Region.of(config.region))
                    .credentialsProvider(base)
                    .build();

            provider = StsAssumeRoleCredentialsProvider.builder()
                    .stsClient(sts)
                    .refreshRequest(r -> r.roleArn(config.roleArn)
                            .roleSessionName("kc-sync"))
                    .build();
        }

        IdentitystoreClient identitystore = IdentitystoreClient.builder()
                .region(Region.of(config.region))
                .credentialsProvider(provider)
                .build();
        return new AwsClients(identitystore, sts);
    }

    public static final class AwsClients implements AutoCloseable {
        private final IdentitystoreClient identitystore;
        private final StsClient sts;

        private AwsClients(IdentitystoreClient identitystore, StsClient sts) {
            this.identitystore = identitystore;
            this.sts = sts;
        }

        public IdentitystoreClient identitystore() {
            return identitystore;
        }

        @Override
        public void close() {
            identitystore.close();
            if (sts != null) {
                sts.close();
            }
        }
    }
}
