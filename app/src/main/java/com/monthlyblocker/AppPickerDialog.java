package com.monthlyblocker;

import android.app.*;
import android.content.*;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.*;
import android.view.*;
import android.widget.*;

import java.util.*;
import java.util.function.Consumer;

final class AppPickerDialog {
    private record AppItem(String packageName, String label, Drawable icon) {}

    static void show(Activity activity, Set<String> initial, Consumer<Set<String>> onSave) {
        Set<String> chosen = new HashSet<>(initial);
        List<AppItem> all = launcherApps(activity);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(activity, 18), 0, dp(activity, 18), 0);
        EditText search = new EditText(activity);
        search.setHint("앱 이름 또는 패키지 검색");
        search.setSingleLine(true);
        TextView count = new TextView(activity);
        count.setPadding(0, dp(activity, 8), 0, dp(activity, 8));
        ListView list = new ListView(activity);
        AppAdapter adapter = new AppAdapter(activity, all, chosen, () -> count.setText(activity.getString(com.monthlyblocker.R.string.selected_app_count, chosen.size())));
        list.setAdapter(adapter);
        count.setText(activity.getString(com.monthlyblocker.R.string.selected_app_count, chosen.size()));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { adapter.filter(s.toString()); }
            public void afterTextChanged(Editable s) {}
        });
        box.addView(search);
        box.addView(count);
        box.addView(list, new LinearLayout.LayoutParams(-1, dp(activity, 480)));
        new AlertDialog.Builder(activity)
                .setTitle("차단할 앱 선택")
                .setView(box)
                .setNegativeButton("취소", null)
                .setPositiveButton("선택 완료", (d, w) -> onSave.accept(chosen))
                .show();
    }

    private static List<AppItem> launcherApps(Activity activity) {
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<AppItem> result = new ArrayList<>();
        for (ResolveInfo info : activity.getPackageManager().queryIntentActivities(launcher, 0)) {
            String pkg = info.activityInfo.packageName;
            if (!pkg.equals(activity.getPackageName())) {
                result.add(new AppItem(pkg, info.loadLabel(activity.getPackageManager()).toString(), info.loadIcon(activity.getPackageManager())));
            }
        }
        result.sort(Comparator.comparing(AppItem::label, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private static final class AppAdapter extends BaseAdapter {
        private final Activity activity;
        private final List<AppItem> all;
        private final List<AppItem> visible = new ArrayList<>();
        private final Set<String> chosen;
        private final Runnable changed;

        AppAdapter(Activity activity, List<AppItem> all, Set<String> chosen, Runnable changed) {
            this.activity = activity;
            this.all = all;
            this.chosen = chosen;
            this.changed = changed;
            visible.addAll(all);
        }

        void filter(String query) {
            String value = query.trim().toLowerCase(Locale.ROOT);
            visible.clear();
            for (AppItem item : all) {
                if (item.label().toLowerCase(Locale.ROOT).contains(value) || item.packageName().toLowerCase(Locale.ROOT).contains(value)) visible.add(item);
            }
            notifyDataSetChanged();
        }

        public int getCount() { return visible.size(); }
        public AppItem getItem(int position) { return visible.get(position); }
        public long getItemId(int position) { return position; }

        public View getView(int position, View convertView, ViewGroup parent) {
            AppItem item = getItem(position);
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(activity, 7), 0, dp(activity, 7));
            ImageView icon = new ImageView(activity);
            icon.setImageDrawable(item.icon());
            row.addView(icon, new LinearLayout.LayoutParams(dp(activity, 44), dp(activity, 44)));
            LinearLayout names = new LinearLayout(activity);
            names.setOrientation(LinearLayout.VERTICAL);
            names.setPadding(dp(activity, 12), 0, dp(activity, 8), 0);
            TextView label = new TextView(activity);
            label.setText(item.label());
            label.setTextSize(16);
            label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            TextView pkg = new TextView(activity);
            pkg.setText(item.packageName());
            pkg.setTextSize(12);
            names.addView(label);
            names.addView(pkg);
            row.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
            CheckBox check = new CheckBox(activity);
            check.setChecked(chosen.contains(item.packageName()));
            check.setClickable(false);
            row.addView(check);
            row.setOnClickListener(v -> {
                if (!chosen.add(item.packageName())) chosen.remove(item.packageName());
                check.setChecked(chosen.contains(item.packageName()));
                changed.run();
            });
            return row;
        }
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private AppPickerDialog() {}
}
