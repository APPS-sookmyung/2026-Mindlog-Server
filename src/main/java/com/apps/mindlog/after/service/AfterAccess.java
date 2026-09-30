package com.apps.mindlog.after.service;

import com.apps.mindlog.after.port.BeforeAccess;
import com.apps.mindlog.global.error.ErrorType;
import com.apps.mindlog.global.error.exception.ApiException;
import com.apps.mindlog.global.security.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class AfterAccess {
    private final ObjectProvider<AccountAccess> accounts;
    private final ObjectProvider<BeforeAccess> before;
    public AfterAccess(ObjectProvider<AccountAccess> accounts,ObjectProvider<BeforeAccess> before){this.accounts=accounts;this.before=before;}
    public AccountAccess.Account current(){return accounts().current();}
    public AccountAccess.Account lockCurrent(){
        var current=current();
        var account=accounts().lock(current.id()).orElseThrow(()->new ApiException(ErrorType.INVALID_TOKEN));
        OnboardingRequiredInterceptor.requireCompleted(account);return account;
    }
    public BeforeAccess.Snapshot lockBeforeBody(long beforeId,long userId){
        return before().lockOwned(beforeId,userId).filter(value->value.userId()==userId)
                .orElseThrow(()->new ApiException(ErrorType.VALIDATION_ERROR,"연결할 Before 기록을 확인해 주세요."));
    }
    public BeforeAccess.Snapshot beforePath(long beforeId,long userId){
        return before().findOwned(beforeId,userId).filter(value->value.userId()==userId)
                .orElseThrow(AfterAccess::notFound);
    }
    public BeforeAccess.Snapshot lockBeforePath(long beforeId,long userId){
        return before().lockOwned(beforeId,userId).filter(value->value.userId()==userId)
                .orElseThrow(AfterAccess::notFound);
    }
    public AccountAccess accounts(){var value=accounts.getIfAvailable();if(value==null)throw unavailable();return value;}
    private BeforeAccess before(){var value=before.getIfAvailable();if(value==null)throw unavailable();return value;}
    private static ApiException unavailable(){return new ApiException(ErrorType.INTERNAL_ERROR);}
    public static ApiException notFound(){return new ApiException(ErrorType.RESOURCE_NOT_FOUND,"요청한 리소스를 찾을 수 없습니다.");}
}
