package com.apps.mindlog.home.service;

import com.apps.mindlog.after.entity.RecordStatus;
import com.apps.mindlog.after.repository.AfterListRepository;
import com.apps.mindlog.after.repository.AfterLogRepository;
import com.apps.mindlog.before.repository.BeforeLogRepository;
import com.apps.mindlog.global.config.TimeConfig;
import com.apps.mindlog.global.security.AccountAccess;
import com.apps.mindlog.home.dto.HomeResponse;
import com.apps.mindlog.user.dto.UserProfileResponse;
import com.apps.mindlog.user.repository.UserRepository;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HomeService {
    private final AccountAccess accounts;
    private final UserRepository users;
    private final BeforeLogRepository beforeLogs;
    private final AfterListRepository afterLists;
    private final AfterLogRepository afterLogs;
    private final Clock clock;

    public HomeService(AccountAccess accounts, UserRepository users, BeforeLogRepository beforeLogs,
            AfterListRepository afterLists, AfterLogRepository afterLogs, Clock clock) {
        this.accounts=accounts;
        this.users=users;
        this.beforeLogs=beforeLogs;
        this.afterLists=afterLists;
        this.afterLogs=afterLogs;
        this.clock=clock;
    }

    @Transactional(readOnly=true)
    public HomeResponse getHome() {
        long userId=accounts.current().id();
        LocalDateTime now=LocalDateTime.now(clock.withZone(TimeConfig.SEOUL));
        int hour=now.getHour();
        String time=hour>=5&&hour<12?"morning":hour<18&&hour>=12?"afternoon":"evening";
        List<String> failed=new ArrayList<>();
        HomeResponse.Greeting greeting=null;
        UserProfileResponse.CharacterSummary character=null;
        boolean weeklySettingsLoaded=false;
        String weekDay="sun";
        try {
            var user=users.findProfileById(userId).orElseThrow();
            greeting=new HomeResponse.Greeting(time,user.getNickname());
            weekDay=user.getWeeklyReportStartDay();
            weeklySettingsLoaded=true;
            var c=user.getRepresentativeCharacter();
            character=new UserProfileResponse.CharacterSummary(c.getId(),c.getCode(),c.getName());
        } catch(RuntimeException ex) {
            failed.add("profile");
        }

        List<HomeResponse.RecentRecord> recent=null;
        try {
            recent=beforeLogs.findRecent(userId,PageRequest.of(0,3)).stream().map(b->{
                var a=afterLogs.findByBeforeLogId(b.getId()).orElse(null);
                String status=a==null?"IN_PROGRESS":a.getRecordStatus().name();
                Integer actual=a!=null&&a.getRecordStatus()==RecordStatus.FINALIZED?a.getActualScore():null;
                return new HomeResponse.RecentRecord(b.getId(),a==null?null:a.getId(),status,b.getTitle(),
                        b.getSituationType().getName(),b.getCreatedAt().atZone(TimeConfig.SEOUL).toLocalDate(),
                        b.getExpectedScore(),actual);
            }).toList();
        } catch(RuntimeException ex) {
            failed.add("recentRecords");
        }

        HomeResponse.WeeklySummary weekly=null;
        try {
            if(!weeklySettingsLoaded) throw new IllegalStateException("Weekly settings unavailable");
            weekly=weekly(userId,now.toLocalDate(),weekDay);
        } catch(RuntimeException ex) {
            failed.add("weeklySummary");
        }
        return new HomeResponse(greeting,character,recent,weekly,failed);
    }

    private HomeResponse.WeeklySummary weekly(long userId,LocalDate today,String weekDay) {
        DayOfWeek first="sun".equals(weekDay)?DayOfWeek.SUNDAY:DayOfWeek.MONDAY;
        LocalDate start=today.with(TemporalAdjusters.previousOrSame(first));
        LocalDate end=start.plusDays(6);
        Map<LocalDate,List<Integer>> current=scoreMap(userId,start,end);
        Map<LocalDate,List<Integer>> previous=scoreMap(userId,start.minusWeeks(1),start.minusDays(1));
        List<HomeResponse.DailyBar> bars=new ArrayList<>();
        for(int i=0;i<7;i++) {
            LocalDate date=start.plusDays(i);
            List<Integer> scores=current.getOrDefault(date,List.of());
            bars.add(new HomeResponse.DailyBar(date,date.getDayOfWeek().name(),average(scores),scores.size()));
        }
        Double average=average(current.values().stream().flatMap(Collection::stream).toList());
        Double previousAverage=average(previous.values().stream().flatMap(Collection::stream).toList());
        Integer difference=average==null?null:(int)Math.round(average)-(int)Math.round(previousAverage==null?0:previousAverage);
        String trend=average==null?"NO_RECORD":previousAverage==null?"NO_PREVIOUS":
                difference>0?"UP":difference<0?"DOWN":"SAME";
        String message=average==null?"불안한 상황이 없었어요":previousAverage==null
                ?"지난 주보다 "+difference+"점 상승했어요"
                :"지난 주보다 "+Math.abs(difference)+"점 "+(difference<0?"떨어졌어요":"상승했어요");
        return new HomeResponse.WeeklySummary(start,end,weekDay,average,bars,previousAverage,difference,trend,message);
    }

    private Map<LocalDate,List<Integer>> scoreMap(long userId,LocalDate from,LocalDate through) {
        Instant lower=from.atStartOfDay(TimeConfig.SEOUL).toInstant();
        Instant upper=through.plusDays(1).atStartOfDay(TimeConfig.SEOUL).toInstant();
        Map<LocalDate,List<Integer>> result=new HashMap<>();
        for(Object[] row:afterLists.findFinalizedScores(userId,lower,upper)) {
            LocalDate date=((java.sql.Date)row[0]).toLocalDate();
            int score=((Number)row[1]).intValue();
            result.computeIfAbsent(date,key->new ArrayList<>()).add(score);
        }
        return result;
    }

    private static Double average(List<Integer> values) {
        return values.isEmpty()?null:values.stream().mapToInt(Integer::intValue).average().orElseThrow();
    }
}

