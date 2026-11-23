package com.apps.mindlog.ai.provider.openai;

import com.apps.mindlog.ai.common.LlmClient;
import com.apps.mindlog.ai.common.LlmTransport;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class OpenAiConfigurationTest {
    @Test void disabledByDefaultAndSelectedProviderRequiresExplicitSettings() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var context = new ApplicationContextRunner().withUserConfiguration(OpenAiConfiguration.class)
                    .withBean(Validator.class, factory::getValidator);
            context.run(app -> assertThat(app).doesNotHaveBean(LlmClient.class).doesNotHaveBean(LlmTransport.class));
            context.withPropertyValues("mindlog.ai.provider=openai").run(app -> assertThat(app).hasFailed());
            context.withPropertyValues("mindlog.ai.provider=openai", "mindlog.ai.openai.api-key=test-key",
                    "mindlog.ai.openai.model=chosen-model").run(app -> {
                        assertThat(app).hasSingleBean(LlmClient.class).hasSingleBean(LlmTransport.class);
                        assertThat(app.getBean(LlmClient.class).modelVersion()).isEqualTo("openai/chosen-model");
                    });
        }
    }

    @Test void anotherProviderCanUseTheExistingTransportAndClientPorts() {
        var replacement = org.mockito.Mockito.mock(LlmClient.class);
        new ApplicationContextRunner().withUserConfiguration(OpenAiConfiguration.class)
                .withPropertyValues("mindlog.ai.provider=replacement")
                .withBean(LlmClient.class, () -> replacement)
                .run(app -> {
                    assertThat(app.getBean(LlmClient.class)).isSameAs(replacement);
                    assertThat(app).doesNotHaveBean(OpenAiLlmTransport.class);
                });
    }
}
