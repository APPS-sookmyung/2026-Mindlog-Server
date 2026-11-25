package com.apps.mindlog.home.dto;

import com.apps.mindlog.user.dto.UserProfileResponse;
import java.time.LocalDate;
import java.util.List;

public record HomeResponse(Greeting greeting, UserProfileResponse.CharacterSummary representativeCharacter,
        List<RecentRecord> recentRecords, WeeklySummary weeklySummary, List<String> failedSections) {
    public record Greeting(String timeOfDay, String nickname) { }
    public record RecentRecord(long beforeLogId, Long afterLogId, String recordStatus, String title,
            String situationTypeName, LocalDate date, int expectedScore, Integer actualScore) { }
    public record WeeklySummary(LocalDate periodStart, LocalDate periodEnd, String weekStartDay,
            Double averageActualScore, List<DailyBar> dailyBars, Double previousWeekAverageActualScore,
            Integer differencePoints, String trend, String trendMessage) { }
    public record DailyBar(LocalDate date, String dayOfWeek, Double averageActualScore, int recordCount) { }
}
