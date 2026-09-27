package com.monthlyblocker;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import android.content.*;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import java.time.*;
import java.time.format.DateTimeFormatter;

/** Receives the foreground package directly, which is more reliable than usage event polling. */
public final class BlockerAccessibilityService extends AccessibilityService {
    private WindowManager windows;
    private View overlay;
    private String blockedPackage;
    private long blockedStart;
    private long blockedEnd;

    @Override public void onServiceConnected() {
        windows = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null || windows == null) return;
        String pkg = event.getPackageName().toString();
        if (pkg.equals(getPackageName())) return;
        ScheduleStore store = new ScheduleStore(this);
        java.util.Optional<ScheduleRules.Window> active = store.activeWindow(pkg, ZonedDateTime.now());
        if (active.isPresent() && overlay == null && Settings.canDrawOverlays(this)) {
            show(pkg, active.get());
        } else if (overlay != null && (!active.isPresent() || !pkg.equals(blockedPackage))) {
            hide();
        }
    }

    private void show(String pkg, ScheduleRules.Window window) {
        blockedPackage = pkg;
        blockedStart = window.start().toInstant().toEpochMilli();
        blockedEnd = window.end().toInstant().toEpochMilli();
        String label = pkg;
        try { label = getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString(); } catch (Exception ignored) {}
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(28), dp(40), dp(28), dp(40));
        box.setBackgroundColor(Color.rgb(244, 242, 236));
        TextView title = text(label + " 사용을 잠시 멈춰요", 26, true);
        title.setGravity(Gravity.CENTER);
        TextView prompt = text("지금 꼭 열어야 하나요?", 18, false);
        prompt.setGravity(Gravity.CENTER);
        TextView end = text(Instant.ofEpochMilli(blockedEnd).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")) + "까지 차단됩니다.", 16, false);
        end.setGravity(Gravity.CENTER);
        Button home = button("홈으로 돌아가기");
        home.setOnClickListener(v -> { hide(); startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); });
        box.addView(title); box.addView(prompt); box.addView(end);
        box.addView(home, new LinearLayout.LayoutParams(-1, dp(56)));
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, android.graphics.PixelFormat.OPAQUE);
        try { overlay = box; windows.addView(overlay, params); } catch (RuntimeException e) { overlay = null; }
    }

    private void hide() { if (overlay != null) { try { windows.removeView(overlay); } catch (RuntimeException ignored) {} overlay = null; blockedPackage = null; } }
    private TextView text(String value, int size, boolean bold) { TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(Color.rgb(30,37,33)); if (bold) v.setTypeface(v.getTypeface(), Typeface.BOLD); return v; }
    private Button button(String value) { Button b = new Button(this); b.setText(value); b.setAllCaps(false); return b; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override public void onInterrupt() { hide(); }
    @Override public void onDestroy() { hide(); super.onDestroy(); }
}
