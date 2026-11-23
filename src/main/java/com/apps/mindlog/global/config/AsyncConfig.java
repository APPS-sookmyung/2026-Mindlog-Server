package com.apps.mindlog.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Spring uses its virtual-thread executor; durable jobs run through JobRunr. */
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncConfig {
}
