package com.monthlyblocker;

import java.time.*;
import java.util.*;

public final class ScheduleRulesSelfTest {
    public static void main(String[] args) {
        ZoneId zone = ZoneId.of("Asia/Seoul");
        LocalDate monday = LocalDate.of(2026, 9, 28);
        Map<LocalDate, List<ScheduleRules.Range>> schedules = new HashMap<>();
        schedules.put(monday, List.of(new ScheduleRules.Range(23 * 60, 2 * 60)));

        assert ScheduleRules.activeWindow(monday.atTime(23, 30).atZone(zone), d -> schedules.getOrDefault(d, List.of())).isPresent();
        assert ScheduleRules.activeWindow(monday.plusDays(1).atTime(1, 0).atZone(zone), d -> schedules.getOrDefault(d, List.of())).isPresent();
        assert ScheduleRules.activeWindow(monday.atTime(22, 59).atZone(zone), d -> schedules.getOrDefault(d, List.of())).isEmpty();
        System.out.println("ScheduleRulesSelfTest passed");
    }
}
