package com.monthlyblocker;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;

import java.time.*;
import java.util.*;

final class ScheduleStore {
    record Plan(List<ScheduleRules.Range> ranges, Set<String> apps) {
        Plan { ranges = new ArrayList<>(ranges); apps = new HashSet<>(apps); }
        boolean isComplete() { return !ranges.isEmpty() && !apps.isEmpty(); }
    }

    private static final String PREFS = "schedule";
    private static final String TEMPLATE = "template_v2";
    private static final String SCHEDULES = "date_schedules_v2";
    private static final String BYPASS = "bypass_window_";
    private final SharedPreferences prefs;

    ScheduleStore(Context context) { prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    Plan template() { return plan(object(prefs.getString(TEMPLATE, "{}"))); }
    void saveTemplate(Plan value) { prefs.edit().putString(TEMPLATE, json(value).toString()).apply(); }
    boolean hasAnyPlan() { return template().isComplete() || schedules().length() > 0; }

    boolean hasSchedule(LocalDate date) { return schedules().has(date.toString()); }
    Plan schedule(LocalDate date) {
        JSONObject value = schedules().optJSONObject(date.toString());
        return value == null ? new Plan(List.of(), Set.of()) : plan(value);
    }

    void saveSchedule(LocalDate date, Plan value) {
        JSONObject root = schedules();
        try { root.put(date.toString(), json(value)); } catch (JSONException ignored) {}
        saveSchedules(root);
    }

    boolean applyTemplate(LocalDate date) {
        Plan value = template();
        if (!value.isComplete()) return false;
        saveSchedule(date, value);
        return true;
    }

    void deleteSchedule(LocalDate date) {
        JSONObject root = schedules();
        root.remove(date.toString());
        saveSchedules(root);
    }

    Optional<ScheduleRules.Window> activeWindow(String packageName, ZonedDateTime now) {
        Optional<ScheduleRules.Window> window = ScheduleRules.activeWindow(now, date -> {
            Plan value = schedule(date);
            return selectedPackage(value.apps(), packageName) ? value.ranges() : List.of();
        });
        if (window.isPresent()) {
            String id = window.get().start().toInstant().toEpochMilli() + ":" + window.get().end().toInstant().toEpochMilli();
            if (id.equals(prefs.getString(BYPASS + packageName, ""))) return Optional.empty();
        }
        return window;
    }

    // Gemini on some Samsung/Google builds opens its UI inside the Google app.
    // Treat that host package as Gemini when the user selected Gemini.
    private static boolean selectedPackage(Set<String> apps, String packageName) {
        if (apps.contains(packageName)) return true;
        return "com.google.android.googlequicksearchbox".equals(packageName)
                && apps.contains("com.google.android.apps.bard");
    }

    void bypass(String packageName, long startMillis, long endMillis) {
        prefs.edit().putString(BYPASS + packageName, startMillis + ":" + endMillis).apply();
    }

    private JSONObject schedules() { return object(prefs.getString(SCHEDULES, "{}")); }
    private void saveSchedules(JSONObject value) { prefs.edit().putString(SCHEDULES, value.toString()).apply(); }
    private static JSONObject object(String value) { try { return new JSONObject(value); } catch (JSONException e) { return new JSONObject(); } }

    private static JSONObject json(Plan plan) {
        JSONObject value = new JSONObject();
        JSONArray ranges = new JSONArray();
        for (ScheduleRules.Range range : plan.ranges()) ranges.put(new JSONArray().put(range.startMinute()).put(range.endMinute()));
        JSONArray apps = new JSONArray();
        for (String app : plan.apps()) apps.put(app);
        try { value.put("ranges", ranges).put("apps", apps); } catch (JSONException ignored) {}
        return value;
    }

    private static Plan plan(JSONObject value) {
        List<ScheduleRules.Range> ranges = new ArrayList<>();
        JSONArray rangeArray = value.optJSONArray("ranges");
        if (rangeArray != null) for (int i = 0; i < rangeArray.length(); i++) {
            JSONArray item = rangeArray.optJSONArray(i);
            if (item != null) try { ranges.add(new ScheduleRules.Range(item.getInt(0), item.getInt(1))); } catch (Exception ignored) {}
        }
        ranges.sort(Comparator.comparingInt(ScheduleRules.Range::startMinute));
        Set<String> apps = new HashSet<>();
        JSONArray appArray = value.optJSONArray("apps");
        if (appArray != null) for (int i = 0; i < appArray.length(); i++) {
            String app = appArray.optString(i, "");
            if (!app.isEmpty()) apps.add(app);
        }
        return new Plan(ranges, apps);
    }
}
