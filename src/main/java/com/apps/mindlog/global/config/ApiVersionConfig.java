package com.apps.mindlog.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class ApiVersionConfig implements WebMvcConfigurer {
    public static final String HEADER = "X-API-Version";
    public static final String VERSION = "1";

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        // Swagger, health checks and other non-API routes do not require a client version.
        configurer.useVersionResolver(request -> {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            return path.equals("/api") || path.startsWith("/api/")
                    ? request.getHeader(HEADER) : VERSION;
        }).setVersionRequired(true).addSupportedVersions(VERSION).detectSupportedVersions(false);
    }
}
