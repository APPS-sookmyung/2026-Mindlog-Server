package com.apps.mindlog.before.service;

import com.apps.mindlog.global.error.ErrorType;
import com.apps.mindlog.global.error.exception.ApiException;
import com.apps.mindlog.global.security.AccountAccess;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** The single seam to replace when authenticated account lookup is completed. */
@Component
public class BeforeLogAccess {
    private final ObjectProvider<AccountAccess> accounts;
    public BeforeLogAccess(ObjectProvider<AccountAccess> accounts) { this.accounts = accounts; }
    public AccountAccess.Account current() {
        AccountAccess access = accounts.getIfAvailable();
        if (access == null) throw new ApiException(ErrorType.INTERNAL_ERROR);
        return access.current();
    }

    public Optional<AccountAccess.Account> lock(long userId) {
        AccountAccess access = accounts.getIfAvailable();
        if (access == null) throw new ApiException(ErrorType.INTERNAL_ERROR);
        return access.lock(userId);
    }
}
