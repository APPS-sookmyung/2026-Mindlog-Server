package com.apps.mindlog.home.service;

import com.apps.mindlog.after.entity.RecordStatus;
import com.apps.mindlog.after.repository.AfterListRepository;
import com.apps.mindlog.after.repository.AfterLogRepository;
import com.apps.mindlog.before.repository.BeforeLogRepository;
import com.apps.mindlog.global.config.TimeConfig;
import com.apps.mindlog.home.dto.HomeResponse;
import com.apps.mindlog.user.dto.UserProfileResponse;
import com.apps.mindlog.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Runs each home section in its own read transaction so one query failure cannot poison the others. */
@Service
public class HomeSectionReader {
    private final UserRepository users;
    private final BeforeLogRepository beforeLogs;
    private final AfterListRepository afterLists;
    private final AfterLogRepository afterLogs;

    public HomeSectionReader(UserRepository users, BeforeLogRepository beforeLogs,
            AfterListRepository afterLists, AfterLogRepository afterLogs) {
        this.users = users;
        this.beforeLogs = beforeLogs;
        this.afterLists = afterLists;
        this.afterLogs = afterLogs;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public ProfileSection profile(long userId, String timeOfDay) {
        var user = users.findProfileById(userId).orElseThrow();
        var character = user.getRepresentativeCharacter();
        return new ProfileSection(new HomeResponse.Greeting(timeOfDay, user.getNickname()),
                new UserProfileResponse.CharacterSummary(character.getId(), character.getCode(), character.getName()),
                user.getWeeklyReportStartDay());
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<HomeResponse.RecentRecord> recentRecords(long userId) {
        return beforeLogs.findRecent(userId, PageRequest.of(0, 3)).stream().map(before -> {
            var after = afterLogs.findByBeforeLogId(before.getId()).orElse(null);
            String status = after == null ? "IN_PROGRESS" : after.getRecordStatus().name();
            Integer actual = after != null && after.getRecordStatus() == RecordStatus.FINALIZED
                    ? after.getActualScore() : null;
            return new HomeResponse.RecentRecord(before.getId(), after == null ? null : after.getId(), status,
                    before.getTitle(), before.getSituationType().getName(),
                    before.getCreatedAt().atZone(TimeConfig.SEOUL).toLocalDate(), before.getExpectedScore(), actual);
        }).toList();
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public HomeResponse.WeeklySummary weeklySummary(long userId, LocalDate today, String weekDay) {
        var firstDay = "sun".equals(weekDay) ? java.time.DayOfWeek.SUNDAY : java.time.DayOfWeek.MONDAY;
        LocalDate start = today.with(TemporalAdjusters.previousOrSame(firstDay));
        LocalDate end = start.plusDays(6);
        Map<LocalDate, List<Integer>> current = scoreMap(userId, start, end);
        Map<LocalDate, List<Integer>> previous = scoreMap(userId, start.minusWeeks(1), start.minusDays(1));
        List<HomeResponse.DailyBar> bars = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = start.plusDays(i);
            List<Integer> scores = current.getOrDefault(date, List.of());
            bars.add(new HomeResponse.DailyBar(date, date.getDayOfWeek().name(), average(scores), scores.size()));
        }
        Double average = average(current.values().stream().flatMap(Collection::stream).toList());
        Double previousAverage = average(previous.values().stream().flatMap(Collection::stream).toList());
        Integer difference = average == null ? null
                : (int) Math.round(average) - (int) Math.round(previousAverage == null ? 0 : previousAverage);
        String trend = average == null ? "NO_RECORD" : previousAverage == null ? "NO_PREVIOUS"
                : difference > 0 ? "UP" : difference < 0 ? "DOWN" : "SAME";
        String message = average == null ? "불안한 상황이 없었어요" : previousAverage == null
                ? "지난 주보다 " + difference + "점 상승했어요"
                : "지난 주보다 " + Math.abs(difference) + "점 " + (difference < 0 ? "떨어졌어요" : "상승했어요");
        return new HomeResponse.WeeklySummary(start, end, weekDay, average, bars, previousAverage,
                difference, trend, message);
    }

    private Map<LocalDate, List<Integer>> scoreMap(long userId, LocalDate from, LocalDate through) {
        Instant lower = from.atStartOfDay(TimeConfig.SEOUL).toInstant();
        Instant upper = through.plusDays(1).atStartOfDay(TimeConfig.SEOUL).toInstant();
        Map<LocalDate, List<Integer>> result = new HashMap<>();
        for (Object[] row : afterLists.findFinalizedScores(userId, lower, upper)) {
            LocalDate date = ((java.sql.Date) row[0]).toLocalDate();
            int score = ((Number) row[1]).intValue();
            result.computeIfAbsent(date, key -> new ArrayList<>()).add(score);
        }
        return result;
    }

    private static Double average(List<Integer> values) {
        return values.isEmpty() ? null : values.stream().mapToInt(Integer::intValue).average().orElseThrow();
    }

    public record ProfileSection(HomeResponse.Greeting greeting,
            UserProfileResponse.CharacterSummary character, String weekStartDay) { }
}
