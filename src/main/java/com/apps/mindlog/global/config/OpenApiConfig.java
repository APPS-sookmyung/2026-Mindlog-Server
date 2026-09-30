package com.apps.mindlog.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {
    @Bean
    OpenAPI mindlogOpenApi() {
        return new OpenAPI().info(new Info().title("Mindlog API").version(ApiVersionConfig.VERSION))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }

    @Bean
    OpenApiCustomizer apiVersionHeader() {
        return api -> {
            if (api.getPaths() == null) return;
            api.getPaths().forEach((path, item) -> {
                if (!path.equals("/api") && !path.startsWith("/api/")) return;
                item.readOperations().forEach(operation -> {
                    if (operation.getParameters() != null && operation.getParameters().stream()
                            .anyMatch(p -> "header".equals(p.getIn()) && ApiVersionConfig.HEADER.equalsIgnoreCase(p.getName()))) return;
                    operation.addParametersItem(new Parameter().in("header").name(ApiVersionConfig.HEADER)
                            .required(true).schema(new StringSchema()._enum(java.util.List.of(ApiVersionConfig.VERSION)))
                            .example(ApiVersionConfig.VERSION));
                });
            });
        };
    }
}
