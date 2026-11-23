package com.apps.mindlog.after.port;

import java.time.LocalDate;
import java.util.Optional;

/** Teammate Before service adapter. Only snapshots owned by userId may be returned. */
public interface BeforeAccess {
    Optional<Snapshot> findOwned(long beforeId,long userId);
    /** Called after account lock, inside the existing transaction. Locks the Before row. */
    Optional<Snapshot> lockOwned(long beforeId,long userId);
    record Snapshot(long id,long userId,long situationTypeId,String situationTypeName,
            LocalDate scheduledAt,int expectedScore,String expectedScoreComment,String worstScenario,
            String title,String description) {}
}
