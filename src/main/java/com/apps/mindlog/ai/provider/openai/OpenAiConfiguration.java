package com.apps.mindlog.ai.provider.openai;

import com.apps.mindlog.ai.common.LlmClient;
import com.apps.mindlog.ai.common.LlmTransport;
import com.apps.mindlog.ai.common.ValidatingLlmClient;
import jakarta.validation.Validator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Another provider can supply the same two ports without changing any domain worker. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "mindlog.ai.provider", havingValue = "openai")
@EnableConfigurationProperties(OpenAiProperties.class)
public class OpenAiConfiguration {
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(LlmTransport.class)
    OpenAiLlmTransport openAiTransport(OpenAiProperties properties) {
        return new OpenAiLlmTransport(properties);
    }

    @Bean
    @ConditionalOnMissingBean(LlmClient.class)
    LlmClient llmClient(LlmTransport transport, Validator validator, OpenAiProperties properties) {
        properties.validate();
        return new ValidatingLlmClient(transport, validator, "openai/" + properties.getModel());
    }
}
