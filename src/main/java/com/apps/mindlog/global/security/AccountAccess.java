package com.apps.mindlog.global.security;

import java.util.Optional;

/** Implemented by the teammate's authenticated User service adapter; no repository crossing. */
public interface AccountAccess {
    /** Resolve from verified authentication, reject inactive/missing accounts with 401. Never parse an unverified header. */
    Account current();
    /** Within caller transaction: SELECT account FOR UPDATE; empty for missing/inactive account. */
    Optional<Account> lock(long userId);
    record Account(long id,long dataGeneration,boolean onboardingCompleted,String nickname){
        public Account {
            if(id<=0||dataGeneration<0)throw new IllegalArgumentException("Invalid account snapshot");
        }
    }
}
