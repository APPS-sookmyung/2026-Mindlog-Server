package com.apps.mindlog.global.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods=false)
public class OnboardingConfig implements WebMvcConfigurer {
    private final OnboardingRequiredInterceptor interceptor;
    public OnboardingConfig(OnboardingRequiredInterceptor interceptor){this.interceptor=interceptor;}
    @Override public void addInterceptors(InterceptorRegistry registry){
        registry.addInterceptor(interceptor).addPathPatterns(
                "/api/before-logs","/api/before-logs/**","/api/before-drafts","/api/before-drafts/**",
                "/api/after-logs","/api/after-logs/**");
    }
}
