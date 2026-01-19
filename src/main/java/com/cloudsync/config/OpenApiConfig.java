package com.cloudsync.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private int serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("CloudSync API")
                        .version("1.0.0")
                        .description("""
                                CloudSync is a scalable, fault-tolerant file storage system with the following features:

                                ## Core Features
                                - **File Upload/Download** with resumable multipart support
                                - **Folder Management** with hierarchical structure
                                - **File Sharing** with expiration and password protection
                                - **User Authentication** with JWT (access + refresh tokens)
                                - **Organization Management** for multi-tenancy
                                - **Redis Caching** for optimized metadata retrieval
                                - **Circuit Breaker & Retry** patterns for fault tolerance
                                - **Consistent Hashing** for data partitioning
                                - **AWS S3 Integration** with pre-signed URLs

                                ## Authentication
                                All authenticated endpoints require a Bearer token in the Authorization header.
                                Obtain tokens via `/api/auth/register` and `/api/auth/login`.

                                ## Rate Limiting
                                - Upload: 100 requests/minute
                                - Download: 200 requests/minute
                                - General API: 1000 requests/minute
                                """)
                        .contact(new Contact()
                                .name("CloudSync Team")
                                .email("support@cloudsync.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:" + serverPort + "/api").description("Local Development Server"),
                        new Server().url("https://api.cloudsync.com").description("Production Server")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter your JWT access token")));
    }
}
