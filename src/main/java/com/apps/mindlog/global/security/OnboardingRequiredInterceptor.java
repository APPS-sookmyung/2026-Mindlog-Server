package com.apps.mindlog.global.security;

import com.apps.mindlog.global.error.ErrorType;
import com.apps.mindlog.global.error.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class OnboardingRequiredInterceptor implements HandlerInterceptor {
    private final ObjectProvider<AccountAccess> accounts;
    public OnboardingRequiredInterceptor(ObjectProvider<AccountAccess> accounts){this.accounts=accounts;}
    @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler){
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if(authentication==null||!authentication.isAuthenticated()||authentication instanceof AnonymousAuthenticationToken)
            throw new ApiException(ErrorType.INVALID_TOKEN);
        var access=accounts.getIfAvailable();
        if(access==null)throw new ApiException(ErrorType.INTERNAL_ERROR);
        requireCompleted(access.current());return true;
    }
    public static void requireCompleted(AccountAccess.Account account){
        if(!account.onboardingCompleted())throw new ApiException(ErrorType.ONBOARDING_REQUIRED,"기초 조사를 먼저 완료해 주세요.");
    }
}
