package com.apps.mindlog.global.security;

import com.apps.mindlog.global.error.ErrorType;
import com.apps.mindlog.global.error.exception.ApiException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.*;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OnboardingRequiredInterceptorTest.Probe.class)
@Import({SecurityConfig.class,SecurityProblemWriter.class,ProblemDetailsAuthenticationEntryPoint.class,
        ProblemDetailsAccessDeniedHandler.class,OnboardingConfig.class,OnboardingRequiredInterceptor.class,
        OnboardingRequiredInterceptorTest.Probe.class})
class OnboardingRequiredInterceptorTest {
    @Autowired MockMvc mvc;
    @MockitoBean AccountAccess accounts;
    @Test @WithMockUser void incompleteAccountIsBlockedOnAllRecordPaths()throws Exception{
        when(accounts.current()).thenReturn(new AccountAccess.Account(1,0,false,"테스트"));
        for(var path:new String[]{"/api/before-logs","/api/before-drafts/rebuttals/job","/api/after-logs/1/analysis"})
            mvc.perform(get(path).header("X-API-Version","1")).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.type").value("https://api.mindlog.com/problems/onboarding-required"));
    }
    @Test @WithMockUser void completedAccountCanUseRecordHandler()throws Exception{
        when(accounts.current()).thenReturn(new AccountAccess.Account(1,0,true,"테스트"));
        mvc.perform(get("/api/after-logs/1/analysis").header("X-API-Version","1")).andExpect(status().isOk());
    }
    @Test @WithMockUser void masterPathDoesNotRequireOnboarding()throws Exception{
        mvc.perform(get("/api/situation-types").header("X-API-Version","1")).andExpect(status().isOk());
        verifyNoInteractions(accounts);
    }
    @Test void unauthenticatedRequestIs401BeforeAccountLookup()throws Exception{
        mvc.perform(get("/api/after-logs/1/analysis").header("X-API-Version","1")).andExpect(status().isUnauthorized());
        verifyNoInteractions(accounts);
    }
    @Test @WithMockUser void missingAdapterDoesNotSilentlyAllowAccess(){
        var interceptor=new OnboardingRequiredInterceptor(new StaticListableBeanFactory().getBeanProvider(AccountAccess.class));
        assertThatThrownBy(()->interceptor.preHandle(new MockHttpServletRequest(),new MockHttpServletResponse(),new Object()))
                .isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getErrorType()).isEqualTo(ErrorType.INTERNAL_ERROR));
    }
    @RestController @RequestMapping(version="1")
    public static class Probe {
        @GetMapping({"/api/before-logs","/api/before-drafts/rebuttals/job","/api/after-logs/1/analysis","/api/situation-types"})
        Map<String,Boolean> get(){return Map.of("ok",true);}
    }
}
