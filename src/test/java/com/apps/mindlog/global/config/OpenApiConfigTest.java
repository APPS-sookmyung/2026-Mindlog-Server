package com.apps.mindlog.global.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {
    @Test void describesVersionAndBearerWithoutRequiringAuthForPublicRoutes() {
        var config = new OpenApiConfig();
        var api = config.mindlogOpenApi();
        var operation = new Operation();
        var docsOperation = new Operation();
        api.paths(new Paths().addPathItem("/api/auth/sessions", new PathItem().post(operation))
                .addPathItem("/docs", new PathItem().get(docsOperation)));
        config.apiVersionHeader().customise(api);
        config.apiVersionHeader().customise(api);
        assertThat(api.getInfo().getVersion()).isEqualTo("1");
        assertThat(api.getComponents().getSecuritySchemes().get("bearerAuth").getType()).isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(api.getSecurity()).isNullOrEmpty();
        assertThat(operation.getParameters()).hasSize(1);
        assertThat(operation.getParameters().getFirst().getRequired()).isTrue();
        assertThat(operation.getParameters().getFirst().getExample()).isEqualTo("1");
        assertThat(docsOperation.getParameters()).isNullOrEmpty();
    }
}
