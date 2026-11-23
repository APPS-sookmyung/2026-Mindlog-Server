package com.apps.mindlog.global.error;

import com.apps.mindlog.global.error.exception.ApiException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import static org.assertj.core.api.Assertions.*;

class ProblemExtensionTest {
    @Test void extensionCannotOverwriteStandardProblemFields(){
        for(var field:new String[]{"type","title","status","detail","instance"})
            assertThatIllegalArgumentException().isThrownBy(()->new ApiException(ErrorType.VALIDATION_ERROR,"safe",Map.of(field,"override")));
    }
    @Test void internalErrorNeverExposesExtensions(){
        var response=new GlobalExceptionHandler().handleApiException(
                new ApiException(ErrorType.INTERNAL_ERROR,"private error",Map.of("secret","private value")),
                new ServletWebRequest(new MockHttpServletRequest("GET","/api/test")));
        var body=(ProblemDetail)response.getBody();
        assertThat(body.getDetail()).isEqualTo("요청을 처리하는 중 오류가 발생했습니다.");
        assertThat(body.getProperties()).isNullOrEmpty();
    }
}
