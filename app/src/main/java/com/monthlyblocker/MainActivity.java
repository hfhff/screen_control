package com.monthlyblocker;

import android.Manifest;
import android.app.*;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.AccessibilityManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MainActivity extends Activity {
    private ScheduleStore store;
    private TextView status;
    private Button protection;
    private TextView selectedDateText;
    private LocalDate selectedDate = LocalDate.now();
    private YearMonth displayedMonth = YearMonth.from(selectedDate);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new ScheduleStore(this);
        buildUi();
    }

    @Override protected void onResume() { super.onResume(); if (status != null) refresh(); }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(18), dp(20), dp(32));
        content.setBackgroundColor(Color.rgb(244, 242, 236));
        scroll.addView(content);

        content.addView(text("월간 차단", 30, true));
        content.addView(text("템플릿을 만든 뒤 원하는 날짜에 적용하거나, 날짜마다 앱과 시간을 다르게 설정하세요.", 15, false));
        status = text("", 17, true);
        status.setPadding(0, dp(24), 0, dp(12));
        content.addView(status);

        protection = button("차단 설정");
        protection.setOnClickListener(v -> handleProtection());
        content.addView(protection);

        Button template = button("기본 템플릿 편집");
        template.setOnClickListener(v -> showPlanEditor("기본 템플릿", store.template(), value -> { store.saveTemplate(value); refresh(); }));
        content.addView(template);

        TextView calendarTitle = text("월간 일정", 22, true);
        calendarTitle.setPadding(0, dp(26), 0, dp(4));
        content.addView(calendarTitle);
        LinearLayout calendar = new LinearLayout(this);
        calendar.setOrientation(LinearLayout.VERTICAL);
        content.addView(calendar, new LinearLayout.LayoutParams(-1, dp(390)));
        renderCalendar(calendar, displayedMonth);

        selectedDateText = text("", 16, true);
        selectedDateText.setPadding(0, dp(8), 0, dp(8));
        content.addView(selectedDateText);
        Button apply = button("기본 템플릿 적용");
        Button applyWeekdays = button("이번 달 일~목에 템플릿 적용");
        Button clearMonth = button("당월 차단 일정 모두 삭제");
        Button edit = button("이 날짜 직접 설정");
        Button delete = button("이 날짜 일정 삭제");
        content.addView(apply);
        content.addView(applyWeekdays);
        content.addView(clearMonth);
        content.addView(edit);
        content.addView(delete);
        apply.setOnClickListener(v -> {
            if (!store.applyTemplate(selectedDate)) Toast.makeText(this, "먼저 기본 템플릿에 앱과 시간대를 설정하세요.", Toast.LENGTH_SHORT).show();
            refresh();
        });
        applyWeekdays.setOnClickListener(v -> applyTemplateToWeekdays());
        clearMonth.setOnClickListener(v -> confirmClearMonth());
        edit.setOnClickListener(v -> showPlanEditor(selectedDate + " 일정", store.schedule(selectedDate), value -> { store.saveSchedule(selectedDate, value); refresh(); }));
        delete.setOnClickListener(v -> { store.deleteSchedule(selectedDate); refresh(); });

        setContentView(scroll);
        refresh();
    }

    private void refresh() {
        boolean usage = hasUsageAccess();
        boolean overlay = Settings.canDrawOverlays(this);
        boolean accessibility = hasAccessibilityAccess();
        boolean enabled = BlockerService.running;
        ScheduleStore.Plan today = store.schedule(LocalDate.now());
        String state = !usage || !overlay || !accessibility ? "차단 권한 설정 필요" : enabled ? "차단 실행 중" : store.hasAnyPlan() ? "차단 시작 대기" : "일정 설정 대기";
        status.setText(getString(R.string.home_status, state, today.apps().size(), describe(today.ranges())));
        protection.setText(!usage ? "1/3 사용 정보 접근 허용" : !overlay ? "2/3 다른 앱 위에 표시 허용" : !accessibility ? "3/3 차단 서비스 허용" : enabled ? "차단 중지" : "차단 시작");
        protection.setVisibility(View.VISIBLE);
        refreshDate();
    }

    private void renderCalendar(LinearLayout host, YearMonth month) {
        host.removeAllViews();
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        Button previous = button("‹");
        Button next = button("›");
        TextView title = text(month.getYear() + "년 " + month.getMonthValue() + "월", 18, true);
        title.setGravity(Gravity.CENTER);
        nav.addView(previous, new LinearLayout.LayoutParams(dp(52), -2));
        nav.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        nav.addView(next, new LinearLayout.LayoutParams(dp(52), -2));
        host.addView(nav);
        previous.setOnClickListener(v -> { displayedMonth = month.minusMonths(1); renderCalendar(host, displayedMonth); });
        next.setOnClickListener(v -> { displayedMonth = month.plusMonths(1); renderCalendar(host, displayedMonth); });

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(7);
        String[] weekdays = {"일", "월", "화", "수", "목", "금", "토"};
        for (int i = 0; i < 7; i++) {
            TextView day = text(weekdays[i], 13, true);
            day.setGravity(Gravity.CENTER);
            day.setTextColor(i == 0 ? Color.rgb(190, 45, 45) : i == 6 ? Color.rgb(45, 95, 180) : Color.DKGRAY);
            grid.addView(day, cellParams());
        }
        LocalDate first = month.atDay(1);
        int offset = first.getDayOfWeek().getValue() % 7; // Sunday = 0
        for (int i = 0; i < offset; i++) grid.addView(text("", 14, false), cellParams());
        for (int dayNumber = 1; dayNumber <= month.lengthOfMonth(); dayNumber++) {
            LocalDate date = month.atDay(dayNumber);
            boolean scheduled = store.hasSchedule(date);
            int weekday = date.getDayOfWeek().getValue() % 7;
            boolean holiday = isKoreanHoliday(date);
            Button cell = button(dayNumber + (scheduled ? "\n차단됨" : ""));
            cell.setTextSize(scheduled ? 11 : 14);
            cell.setGravity(Gravity.CENTER);
            cell.setTextColor(holiday || weekday == 0 ? Color.rgb(190, 45, 45) : weekday == 6 ? Color.rgb(45, 95, 180) : Color.rgb(30, 37, 33));
            if (date.equals(selectedDate)) cell.setBackgroundColor(Color.rgb(210, 226, 217));
            cell.setOnClickListener(v -> { selectedDate = date; displayedMonth = month; renderCalendar(host, displayedMonth); refreshDate(); });
            grid.addView(cell, cellParams());
        }
        host.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private GridLayout.LayoutParams cellParams() {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dp(52);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        return params;
    }

    private boolean isKoreanHoliday(LocalDate date) {
        int month = date.getMonthValue(), day = date.getDayOfMonth();
        if ((month == 1 && day == 1) || (month == 3 && day == 1) || (month == 5 && day == 5)
                || (month == 6 && day == 6) || (month == 8 && day == 15)
                || (month == 10 && (day == 3 || day == 9)) || (month == 12 && day == 25)) return true;
        // Lunar holidays for the current and adjacent planning years.
        String key = date.toString();
        return Set.of("2026-02-16", "2026-02-17", "2026-02-18", "2026-05-24", "2026-09-24", "2026-09-25", "2026-09-26",
                "2027-02-06", "2027-02-07", "2027-02-08", "2027-05-13", "2027-09-14", "2027-09-15", "2027-09-16").contains(key);
    }

    private void applyTemplateToWeekdays() {
        ScheduleStore.Plan template = store.template();
        if (!template.isComplete()) {
            Toast.makeText(this, "먼저 기본 템플릿을 완성하세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        YearMonth month = displayedMonth;
        for (LocalDate date = month.atDay(1); !date.isAfter(month.atEndOfMonth()); date = date.plusDays(1)) {
            int weekday = date.getDayOfWeek().getValue();
            if ((weekday == 7 || weekday <= 4) && !isKoreanHoliday(date)) store.saveSchedule(date, template);
        }
        Toast.makeText(this, "이번 달 평일에 템플릿을 적용했습니다.", Toast.LENGTH_SHORT).show();
        refresh();
    }

    private void confirmClearMonth() {
        YearMonth month = displayedMonth;
        new AlertDialog.Builder(this)
                .setTitle(month.getYear() + "년 " + month.getMonthValue() + "월 일정 삭제")
                .setMessage("이 달의 날짜별 차단 일정만 모두 삭제합니다. 기본 템플릿은 유지됩니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton("모두 삭제", (dialog, which) -> {
                    for (LocalDate date = month.atDay(1); !date.isAfter(month.atEndOfMonth()); date = date.plusDays(1)) {
                        store.deleteSchedule(date);
                    }
                    refresh();
                    Toast.makeText(this, "당월 차단 일정을 모두 삭제했습니다.", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void refreshDate() {
        ScheduleStore.Plan plan = store.schedule(selectedDate);
        String detail = store.hasSchedule(selectedDate)
                ? describe(plan.ranges()) + "\n앱: " + describeApps(plan.apps())
                : "등록된 일정 없음";
        selectedDateText.setText(getString(R.string.selected_date_status,
                selectedDate.format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)),
                store.hasSchedule(selectedDate) ? "설정됨" : "미설정", detail));
    }

    private void handleProtection() {
        if (!hasUsageAccess()) {
            new AlertDialog.Builder(this)
                    .setTitle("사용 정보 접근이 필요한 이유")
                    .setMessage("월간 차단은 현재 화면에 열린 앱의 이름과 열린 시각을 확인해 차단 일정을 적용합니다. 이 정보는 기기 안에서만 처리하며 저장하거나 외부로 전송하지 않습니다. 허용하지 않으면 앱 차단이 작동하지 않습니다.")
                    .setNegativeButton("취소", null)
                    .setPositiveButton("동의하고 설정 열기", (d, w) -> startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)))
                    .show();
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("다른 앱 위에 표시가 필요한 이유")
                    .setMessage("차단 시간에 선택한 앱 위로 차단 안내를 표시하기 위해 필요합니다. 화면 내용이나 입력을 읽지 않으며, 차단이 실행 중일 때만 사용합니다.")
                    .setNegativeButton("취소", null)
                    .setPositiveButton("동의하고 설정 열기", (d, w) -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))))
                    .show();
            return;
        }
        if (!hasAccessibilityAccess()) {
            new AlertDialog.Builder(this)
                    .setTitle("차단 서비스 권한이 필요합니다")
                    .setMessage("앱이 화면에 열리는 순간을 감지해 차단 화면을 표시하려면 접근성 서비스를 허용해야 합니다.")
                    .setNegativeButton("취소", null)
                    .setPositiveButton("설정 열기", (d, w) -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                    .show();
            return;
        }
        if (BlockerService.running) {
            getSharedPreferences("schedule", MODE_PRIVATE).edit().putBoolean(BlockerService.PREF_RUNNING, false).apply();
            stopService(new Intent(this, BlockerService.class));
            refresh();
            return;
        }
        getSharedPreferences("schedule", MODE_PRIVATE).edit().putBoolean(BlockerService.PREF_RUNNING, true).apply();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        startForegroundService(new Intent(this, BlockerService.class));
        refresh();
    }

    private boolean hasUsageAccess() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
        int mode = Build.VERSION.SDK_INT >= 29
                ? appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), getPackageName())
                : appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private boolean hasAccessibilityAccess() {
        AccessibilityManager manager = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        if (manager == null) return false;
        String expected = getPackageName() + "/" + BlockerAccessibilityService.class.getName();
        for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (info.getId().equals(expected)) return true;
        }
        return false;
    }

    private interface PlanSaver { void save(ScheduleStore.Plan plan); }

    private void showPlanEditor(String title, ScheduleStore.Plan initial, PlanSaver saver) {
        List<ScheduleRules.Range> ranges = new ArrayList<>(initial.ranges());
        Set<String> apps = new HashSet<>(initial.apps());
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), 0, dp(20), 0);
        TextView appSummary = text("", 15, false);
        Button chooseApps = button("앱 선택");
        LinearLayout rangeList = new LinearLayout(this);
        rangeList.setOrientation(LinearLayout.VERTICAL);
        Button addRange = button("+ 차단 시간대 직접 추가");
        box.addView(text("차단할 앱", 17, true));
        box.addView(appSummary);
        box.addView(chooseApps);
        TextView rangeTitle = text("차단 시간대", 17, true);
        rangeTitle.setPadding(0, dp(18), 0, 0);
        box.addView(rangeTitle);
        box.addView(rangeList);
        box.addView(addRange);

        Runnable redrawApps = () -> appSummary.setText(apps.isEmpty() ? "선택한 앱 없음" : apps.size() + "개 선택\n" + describeApps(apps));
        Runnable redrawRanges = () -> {
            rangeList.removeAllViews();
            if (ranges.isEmpty()) rangeList.addView(text("설정한 시간대 없음", 15, false));
            for (ScheduleRules.Range range : new ArrayList<>(ranges)) {
                rangeList.addView(rangeRow(range, ranges, rangeList));
            }
        };
        redrawApps.run();
        redrawRanges.run();
        chooseApps.setOnClickListener(v -> AppPickerDialog.show(this, apps, selected -> { apps.clear(); apps.addAll(selected); redrawApps.run(); }));
        addRange.setOnClickListener(v -> pickTime("차단 시작 시간", 9 * 60, start -> pickTime("차단 종료 시간", 18 * 60, end -> {
            if (start == end) { Toast.makeText(this, "시작과 종료 시간은 달라야 합니다.", Toast.LENGTH_SHORT).show(); return; }
            ranges.add(new ScheduleRules.Range(start, end));
            ranges.sort(Comparator.comparingInt(ScheduleRules.Range::startMinute));
            redrawRangeList(rangeList, ranges);
        })));

        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title).setView(box)
                .setNegativeButton("취소", null).setPositiveButton("저장", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (apps.isEmpty()) { Toast.makeText(this, "차단할 앱을 하나 이상 선택하세요.", Toast.LENGTH_SHORT).show(); return; }
            if (ranges.isEmpty()) { Toast.makeText(this, "차단 시간대를 하나 이상 추가하세요.", Toast.LENGTH_SHORT).show(); return; }
            saver.save(new ScheduleStore.Plan(ranges, apps));
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void redrawRangeList(LinearLayout list, List<ScheduleRules.Range> ranges) {
        list.removeAllViews();
        if (ranges.isEmpty()) list.addView(text("설정한 시간대 없음", 15, false));
        for (ScheduleRules.Range range : new ArrayList<>(ranges)) {
            list.addView(rangeRow(range, ranges, list));
        }
    }

    private View rangeRow(ScheduleRules.Range range, List<ScheduleRules.Range> ranges, LinearLayout list) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button edit = button(range.display() + " (수정)");
        Button remove = button("삭제");
        row.addView(edit, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(remove, new LinearLayout.LayoutParams(-2, -2));
        edit.setOnClickListener(v -> pickTime("차단 시작 시간", range.startMinute(), start -> pickTime("차단 종료 시간", range.endMinute(), end -> {
            if (start == end) {
                Toast.makeText(this, "시작과 종료 시간은 달라야 합니다.", Toast.LENGTH_SHORT).show();
                return;
            }
            ranges.remove(range);
            ranges.add(new ScheduleRules.Range(start, end));
            ranges.sort(Comparator.comparingInt(ScheduleRules.Range::startMinute));
            redrawRangeList(list, ranges);
        })));
        remove.setOnClickListener(v -> { ranges.remove(range); redrawRangeList(list, ranges); });
        return row;
    }

    private interface TimeResult { void accept(int minute); }
    private void pickTime(String title, int initial, TimeResult result) {
        TimePickerDialog dialog = new TimePickerDialog(this, (v, hour, minute) -> result.accept(hour * 60 + minute), initial / 60, initial % 60, true);
        dialog.setTitle(title);
        dialog.show();
    }

    private String describe(List<ScheduleRules.Range> ranges) {
        if (ranges.isEmpty()) return "차단 일정 없음";
        StringJoiner result = new StringJoiner(", ");
        for (ScheduleRules.Range range : ranges) result.add(range.display());
        return result.toString();
    }

    private String describeApps(Set<String> packages) {
        List<String> labels = new ArrayList<>();
        for (String pkg : packages) {
            try { labels.add(getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString()); }
            catch (Exception ignored) { labels.add(pkg); }
        }
        labels.sort(String.CASE_INSENSITIVE_ORDER);
        return String.join(", ", labels);
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(Color.rgb(30, 37, 33));
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }
    private Button button(String label) { Button button = new Button(this); button.setText(label); button.setAllCaps(false); return button; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
