
package com.jinxiao.keycloak.aws;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.identitystore.IdentitystoreClient;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;

import java.util.logging.Level;
import java.util.logging.Logger;

public class AwsClientFactory {
    private static final Logger LOG = Logger.getLogger(AwsClientFactory.class.getName());

    public static AwsClients create(AwsConfig config) {

        AwsCredentialsProvider base = DefaultCredentialsProvider.create();
        AwsCredentialsProvider provider = base;
        StsClient sts = null;

        if (config.roleArn != null && !config.roleArn.isEmpty()) {
            sts = StsClient.builder()
                    .region(Region.of(config.region))
                    .credentialsProvider(base)
                    .build();

            StsAssumeRoleCredentialsProvider assumeRoleProvider = StsAssumeRoleCredentialsProvider.builder()
                    .stsClient(sts)
                    .refreshRequest(r -> r.roleArn(config.roleArn)
                            .roleSessionName("kc-sync"))
                    .build();
            provider = assumeRoleProvider;
        }

        IdentitystoreClient identitystore = IdentitystoreClient.builder()
                .region(Region.of(config.region))
                .credentialsProvider(provider)
                .build();
        return new AwsClients(
                identitystore,
                closeable(provider),
                sts,
                provider == base ? null : closeable(base)
        );
    }

    public static final class AwsClients implements AutoCloseable {
        private final IdentitystoreClient identitystore;
        private final AutoCloseable credentialsProvider;
        private final StsClient sts;
        private final AutoCloseable baseCredentialsProvider;

        private AwsClients(
                IdentitystoreClient identitystore,
                AutoCloseable credentialsProvider,
                StsClient sts,
                AutoCloseable baseCredentialsProvider
        ) {
            this.identitystore = identitystore;
            this.credentialsProvider = credentialsProvider;
            this.sts = sts;
            this.baseCredentialsProvider = baseCredentialsProvider;
        }

        public IdentitystoreClient identitystore() {
            return identitystore;
        }

        static AwsClients of(IdentitystoreClient identitystore) {
            return new AwsClients(identitystore, null, null, null);
        }

        @Override
        public void close() {
            closeQuietly(identitystore);
            closeQuietly(credentialsProvider);
            closeQuietly(sts);
            closeQuietly(baseCredentialsProvider);
        }
    }

    private static AutoCloseable closeable(AwsCredentialsProvider provider) {
        return provider instanceof AutoCloseable closeable ? closeable : null;
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to close AWS client resource", e);
        }
    }
}
