package com.bisioneers.medica.medical.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * Configuración del cliente S3 apuntando a Cloudflare R2.
 *
 * Cloudflare R2 es 100% compatible con S3 API:
 *   - Endpoint personalizado (https://<account>.r2.cloudflarestorage.com)
 *   - Region "auto" o "us-east-1"
 *   - Path-style addressing
 *
 * Activado si: medica.storage.type es 's3' o 'hybrid'
 */
@Configuration
@ConditionalOnExpression(
		"'${medica.storage.type:local}' == 's3' || '${medica.storage.type:local}' == 'hybrid'"
		)
public class R2StorageConfig {

	@Value("${medica.storage.s3.endpoint}")
	private String endpoint;

	@Value("${medica.storage.s3.access-key}")
	private String accessKey;

	@Value("${medica.storage.s3.secret-key}")
	private String secretKey;

	@Value("${medica.storage.s3.region:auto}")
	private String region;

	@Bean
	public S3Client s3Client() {
		return S3Client.builder()
				.endpointOverride(URI.create(endpoint))
				.region(Region.of(region))
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create(accessKey, secretKey)))
				.serviceConfiguration(S3Configuration.builder()
						.pathStyleAccessEnabled(true)
						.build())
				.build();
	}

	@Bean
	public S3Presigner s3Presigner() {
		return S3Presigner.builder()
				.endpointOverride(URI.create(endpoint))
				.region(Region.of(region))
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create(accessKey, secretKey)))
				.serviceConfiguration(S3Configuration.builder()
						.pathStyleAccessEnabled(true)
						.build())
				.build();
	}
}
