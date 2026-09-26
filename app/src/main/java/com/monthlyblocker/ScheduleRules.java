package com.monthlyblocker;

import java.time.*;
import java.util.*;
import java.util.function.Function;

final class ScheduleRules {
    record Range(int startMinute, int endMinute) {
        Range {
            if (startMinute < 0 || startMinute >= 1440 || endMinute < 0 || endMinute >= 1440 || startMinute == endMinute) {
                throw new IllegalArgumentException("잘못된 시간 범위");
            }
        }

        boolean crossesMidnight() { return endMinute < startMinute; }
        String display() { return time(startMinute) + "–" + time(endMinute) + (crossesMidnight() ? " (다음 날)" : ""); }
        private static String time(int minute) { return String.format(Locale.KOREA, "%02d:%02d", minute / 60, minute % 60); }
    }

    record Window(ZonedDateTime start, ZonedDateTime end) {}

    static Optional<Window> activeWindow(
            ZonedDateTime now,
            Function<LocalDate, List<Range>> rangesForDate) {
        LocalDate today = now.toLocalDate();
        List<Window> windows = new ArrayList<>();
        for (int offset = -1; offset <= 1; offset++) {
            LocalDate date = today.plusDays(offset);
            for (Range range : rangesForDate.apply(date)) {
                ZonedDateTime start = date.atStartOfDay(now.getZone()).plusMinutes(range.startMinute());
                ZonedDateTime end = date.atStartOfDay(now.getZone()).plusDays(range.crossesMidnight() ? 1 : 0).plusMinutes(range.endMinute());
                if (end.isAfter(start)) windows.add(new Window(start, end));
            }
        }
        windows.sort(Comparator.comparing(Window::start));
        for (int i = 0; i < windows.size(); i++) {
            Window current = windows.get(i);
            if (now.isBefore(current.start()) || !now.isBefore(current.end())) continue;
            ZonedDateTime end = current.end();
            for (int j = i + 1; j < windows.size() && !windows.get(j).start().isAfter(end); j++) {
                if (windows.get(j).end().isAfter(end)) end = windows.get(j).end();
            }
            return Optional.of(new Window(current.start(), end));
        }
        return Optional.empty();
    }

    private ScheduleRules() {}
}
