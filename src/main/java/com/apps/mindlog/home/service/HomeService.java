package com.apps.mindlog.home.service;

import com.apps.mindlog.global.config.TimeConfig;
import com.apps.mindlog.global.security.AccountAccess;
import com.apps.mindlog.home.dto.HomeResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class HomeService {
    private final AccountAccess accounts;
    private final HomeSectionReader sections;
    private final Clock clock;

    public HomeService(AccountAccess accounts, HomeSectionReader sections, Clock clock) {
        this.accounts = accounts;
        this.sections = sections;
        this.clock = clock;
    }

    public HomeResponse getHome() {
        long userId = accounts.current().id();
        LocalDateTime now = LocalDateTime.now(clock.withZone(TimeConfig.SEOUL));
        int hour = now.getHour();
        String time = hour >= 5 && hour < 12 ? "morning" : hour < 18 && hour >= 12 ? "afternoon" : "evening";
        List<String> failed = new ArrayList<>();
        HomeResponse.Greeting greeting = null;
        com.apps.mindlog.user.dto.UserProfileResponse.CharacterSummary character = null;
        String weekDay = "sun";
        boolean profileLoaded = false;

        try {
            var profile = sections.profile(userId, time);
            greeting = profile.greeting();
            character = profile.character();
            weekDay = profile.weekStartDay();
            profileLoaded = true;
        } catch (RuntimeException exception) {
            failed.add("profile");
        }

        List<HomeResponse.RecentRecord> recent = null;
        try {
            recent = sections.recentRecords(userId);
        } catch (RuntimeException exception) {
            failed.add("recentRecords");
        }

        HomeResponse.WeeklySummary weekly = null;
        try {
            if (!profileLoaded) throw new IllegalStateException("Weekly settings unavailable");
            weekly = sections.weeklySummary(userId, now.toLocalDate(), weekDay);
        } catch (RuntimeException exception) {
            failed.add("weeklySummary");
        }

        return new HomeResponse(greeting, character, recent, weekly, failed);
    }
}
