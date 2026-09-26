package com.monthlyblocker;

import android.app.*;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class BlockerService extends Service {
    static final String PREF_RUNNING = "monitor_running";
    static volatile boolean running;
    private static final String CHANNEL = "blocking_status";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private View overlay;
    private String blockedPackage;
    private String lastForegroundPackage;
    private boolean hasForegroundSample;
    private final Runnable monitor = new Runnable() {
        @Override public void run() {
            checkCurrentApp();
            handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        running = true;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createChannel();
        Notification notification = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.app_icon)
                .setContentTitle("월간 차단 실행 중")
                .setContentText("설정한 일정에 따라 선택한 앱을 확인합니다.")
                .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE))
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(1, notification);
        handler.post(monitor);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        getSharedPreferences("schedule", MODE_PRIVATE).edit().putBoolean(PREF_RUNNING, true).apply();
        return START_STICKY;
    }

    private void checkCurrentApp() {
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }
        ScheduleStore store = new ScheduleStore(this);
        if (overlay != null) {
            if (store.activeWindow(blockedPackage, ZonedDateTime.now()).isEmpty()) hideOverlay();
            return;
        }
        String packageName = foregroundPackage();
        if (packageName == null || packageName.equals(getPackageName())) return;
        Optional<ScheduleRules.Window> window = store.activeWindow(packageName, ZonedDateTime.now());
        window.ifPresent(value -> showOverlay(packageName, value));
    }

    private String foregroundPackage() {
        UsageStatsManager manager = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
        long end = System.currentTimeMillis();
        // 일부 제조사는 앱 전환 이벤트를 수 초 늦게 기록하므로 짧은 창만
        // 조회하면 대상 앱을 놓칠 수 있다. 최근 하루의 이벤트에서 최신 전환을 찾는다.
        long lookback = 24 * 60 * 60 * 1000L;
        UsageEvents events = manager.queryEvents(end - lookback, end);
        UsageEvents.Event event = new UsageEvents.Event();
        String latest = lastForegroundPackage;
        long latestTimestamp = 0;
        while (events != null && events.hasNextEvent()) {
            events.getNextEvent(event);
            int type = event.getEventType();
            if ((type == UsageEvents.Event.ACTIVITY_RESUMED || type == UsageEvents.Event.MOVE_TO_FOREGROUND)
                    && event.getPackageName() != null && event.getTimeStamp() >= latestTimestamp) {
                latest = event.getPackageName();
                latestTimestamp = event.getTimeStamp();
            }
        }
        List<android.app.usage.UsageStats> stats = manager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, end - 24 * 60 * 60 * 1000L, end);
        for (android.app.usage.UsageStats stat : stats) {
            if (stat.getLastTimeUsed() > latestTimestamp) {
                latestTimestamp = stat.getLastTimeUsed();
                latest = stat.getPackageName();
            }
        }
        lastForegroundPackage = latest;
        hasForegroundSample = true;
        return lastForegroundPackage;
    }

    private void showOverlay(String packageName, ScheduleRules.Window window) {
        if (!Settings.canDrawOverlays(this) || overlay != null) return;
        blockedPackage = packageName;
        long start = window.start().toInstant().toEpochMilli();
        long end = window.end().toInstant().toEpochMilli();
        String label = packageName;
        try { label = getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(packageName, 0)).toString(); } catch (Exception ignored) {}

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(28), dp(40), dp(28), dp(40));
        box.setBackgroundColor(Color.rgb(244, 242, 236));
        TextView title = text(label + " 사용을 잠시 멈췄어요", 26, true);
        title.setGravity(Gravity.CENTER);
        TextView prompt = text("지금 꼭 열어야 하나요?", 18, false);
        prompt.setGravity(Gravity.CENTER);
        prompt.setPadding(0, dp(16), 0, dp(8));
        TextView endText = text(Instant.ofEpochMilli(end).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")) + "에 일정이 끝납니다.", 16, false);
        endText.setGravity(Gravity.CENTER);
        endText.setPadding(0, 0, 0, dp(30));
        Button home = button("홈으로 돌아가기");
        Button unlock = button("현재 일정 동안 해제");
        home.setOnClickListener(v -> { hideOverlay(); startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); });
        unlock.setOnClickListener(v -> { new ScheduleStore(this).bypass(packageName, start, end); hideOverlay(); });
        box.addView(title);
        box.addView(prompt);
        box.addView(endText);
        box.addView(home, new LinearLayout.LayoutParams(-1, dp(56)));
        box.addView(unlock, new LinearLayout.LayoutParams(-1, dp(56)));

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE);
        try {
            overlay = box;
            windowManager.addView(overlay, params);
        } catch (RuntimeException error) {
            overlay = null;
            blockedPackage = null;
        }
    }

    private void hideOverlay() {
        if (overlay != null) {
            windowManager.removeView(overlay);
            overlay = null;
            blockedPackage = null;
        }
    }

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL, "차단 상태", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("일정 기반 앱 차단이 실행 중임을 알립니다.");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
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

    @Override public void onDestroy() {
        handler.removeCallbacks(monitor);
        hideOverlay();
        running = false;
        super.onDestroy();
    }
    @Override public android.os.IBinder onBind(Intent intent) { return null; }
}
