package com.lynkracing.dashboard;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class Dashboard {
    private static final int BACKGROUND = 0xFF080C10;
    private static final int PANEL = 0xFF101820;
    private static final int BORDER = 0xFF273945;
    private static final int CYAN = GaugeView.CYAN;
    private static final int TEXT = GaugeView.TEXT;
    private static final int MUTED = GaugeView.MUTED;
    private static final int AMBER = 0xFFFFBC7A;
    private static final Typeface DISPLAY = Typeface.create("sans-serif-condensed", Typeface.BOLD);
    private static final String[] TABS = {"仪表盘", "加速测试", "驾驶记录"};
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    final LinearLayout root;
    private final MainActivity activity;
    private final TextView gps;
    private final TextView[] nav = new TextView[TABS.length];
    private final ScrollView scroll;
    private LinearLayout page;
    private GaugeView speedGauge;
    private GaugeView forceGauge;
    private TextView maximum;
    private TextView distance;
    private TextView peakForce;
    private TextView locationButton;
    private TextView recordButton;
    private TextView sprintButton;
    private TextView sprintTime;
    private TextView sprintStatus;
    private int tab;

    Dashboard(MainActivity activity) {
        this.activity = activity;
        root = column();
        root.setBackgroundColor(BACKGROUND);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });

        LinearLayout header = row();
        header.setPadding(dp(18), dp(11), dp(18), dp(12));
        TextView brand = text("LYNK RACING", 24, TEXT, true);
        brand.setLetterSpacing(.065f);
        brand.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
        View slash = new View(activity);
        slash.setBackgroundColor(CYAN);
        slash.setRotation(23);
        LinearLayout.LayoutParams slashParams = new LinearLayout.LayoutParams(dp(4), dp(20));
        slashParams.setMargins(dp(2), 0, dp(9), 0);
        header.addView(slash, slashParams);
        header.addView(brand, new LinearLayout.LayoutParams(0, WRAP, 1f));
        gps = text("正在搜索卫星", 12, MUTED, true);
        gps.setGravity(Gravity.CENTER);
        gps.setPadding(dp(8), dp(5), dp(8), dp(5));
        gps.setOnClickListener(view -> activity.enableLocation());
        header.addView(gps);
        root.addView(header, new LinearLayout.LayoutParams(MATCH, WRAP));
        addDivider(root);

        scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        root.addView(scroll, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        addDivider(root);
        LinearLayout navigation = row();
        navigation.setPadding(dp(10), dp(6), dp(10), dp(6));
        for (int i = 0; i < TABS.length; i++) {
            int target = i;
            nav[i] = action(TABS[i], false, () -> show(target));
            nav[i].setTextSize(14);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, WRAP, 1f);
            params.setMargins(dp(3), 0, dp(3), 0);
            navigation.addView(nav[i], params);
        }
        root.addView(navigation, new LinearLayout.LayoutParams(MATCH, WRAP));
    }

    int tab() {
        return tab;
    }

    void show(int tab) {
        this.tab = tab;
        page = column();
        page.setPadding(dp(16), dp(13), dp(16), dp(16));
        scroll.removeAllViews();
        scroll.addView(page, new ScrollView.LayoutParams(MATCH, WRAP));
        scroll.scrollTo(0, 0);
        for (int i = 0; i < nav.length; i++) {
            nav[i].setSelected(i == tab);
            nav[i].setTextColor(i == tab ? CYAN : MUTED);
            nav[i].setBackground(control(i == tab ? 0xFF142831 : BACKGROUND,
                    i == tab ? 0xFF284E59 : BACKGROUND));
        }
        switch (tab) {
            case 0: showInstruments(); break;
            case 1: showSprint(); break;
            case 2: showHistory(); break;
            default: throw new IllegalArgumentException("Unknown tab " + tab);
        }
    }

    private void showInstruments() {
        LinearLayout speedPanel = panel();
        speedPanel.addView(text("实时车速", 14, MUTED, true));
        speedGauge = new GaugeView(activity);
        speedPanel.addView(speedGauge, new LinearLayout.LayoutParams(MATCH, dp(222)));
        add(page, speedPanel, 0);

        LinearLayout stats = row();
        LinearLayout speedMetric = panel();
        speedMetric.addView(text("最高速度", 13, MUTED, false));
        maximum = value("—", 26);
        speedMetric.addView(maximum);
        speedMetric.addView(text("km/h", 12, CYAN, false));
        stats.addView(speedMetric, new LinearLayout.LayoutParams(0, WRAP, 1f));
        LinearLayout distanceMetric = panel();
        distanceMetric.addView(text("行驶里程", 13, MUTED, false));
        distance = value("0.00", 26);
        distanceMetric.addView(distance);
        distanceMetric.addView(text("km", 12, CYAN, false));
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, WRAP, 1f);
        second.leftMargin = dp(10);
        stats.addView(distanceMetric, second);
        add(page, stats, 10);

        LinearLayout forcePanel = panel();
        LinearLayout forceRow = row();
        LinearLayout forceLabels = column();
        forceLabels.addView(text("水平加速度", 16, TEXT, true));
        peakForce = text("峰值 — G", 13, MUTED, false);
        add(forceLabels, peakForce, 7);
        forceRow.addView(forceLabels, new LinearLayout.LayoutParams(0, WRAP, 1f));
        forceGauge = new GaugeView(activity, true);
        forceRow.addView(forceGauge, new LinearLayout.LayoutParams(dp(132), dp(91)));
        forcePanel.addView(forceRow, new LinearLayout.LayoutParams(MATCH, WRAP));
        add(page, forcePanel, 10);

        locationButton = action("启用 GPS", false, activity::enableLocation);
        locationButton.setVisibility(View.GONE);
        add(page, locationButton, 10);
        recordButton = action("开始记录", true, activity::toggleRecord);
        add(page, recordButton, 10);
    }

    private void showSprint() {
        add(page, text("0–100 km/h", 30, TEXT, true), 4);
        add(page, text("仅限封闭场地", 14, AMBER, true), 4);
        LinearLayout card = panel();
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(16), dp(24), dp(16), dp(24));
        card.addView(text("加速用时", 15, MUTED, true));
        sprintTime = value("—", 64);
        sprintTime.setGravity(Gravity.CENTER);
        card.addView(sprintTime, new LinearLayout.LayoutParams(MATCH, WRAP));
        card.addView(text("秒", 14, CYAN, true));
        sprintStatus = text("等待确认封闭场地", 15, MUTED, true);
        sprintStatus.setGravity(Gravity.CENTER);
        add(card, sprintStatus, 17);
        add(page, card, 16);
        sprintButton = action("准备测试", true, activity::toggleSprint);
        add(page, sprintButton, 12);
        add(page, text("固定手机，保持静止直到显示就绪。测试完成后自动保存结果。", 14, MUTED, false), 14);
    }

    void showHistory() {
        if (tab != 2) return;
        page.removeAllViews();
        add(page, text("驾驶记录", 27, TEXT, true), 4);
        List<DriveStore.Entry> entries = activity.history();
        if (entries.isEmpty()) {
            LinearLayout empty = panel();
            empty.addView(text("暂无记录", 18, TEXT, true));
            add(empty, text("在仪表盘开始记录，或完成一次加速测试。", 14, MUTED, false), 8);
            add(page, empty, 14);
            return;
        }
        SimpleDateFormat date = new SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.SIMPLIFIED_CHINESE);
        for (DriveStore.Entry entry : entries) {
            LinearLayout card = panel();
            card.addView(text(entry.sprintOnly() ? "加速测试" : "驾驶记录", 16, CYAN, true));
            add(card, text(date.format(new Date(entry.started())), 13, MUTED, false), 4);
            if (!entry.sprintOnly()) {
                String speed = entry.topSpeed() < 0 ? "—" : Integer.toString(Math.round(entry.topSpeed()));
                String g = entry.peakG() < 0 ? "—" : decimal(entry.peakG());
                add(card, text(decimal(entry.distance() / 1_000) + " km  ·  " + duration(entry.duration()),
                        21, TEXT, true), 9);
                add(card, text("最高速度 " + speed + " km/h  ·  峰值 " + g + " G", 13, MUTED, false), 5);
            }
            if (entry.sprint() > 0) {
                add(card, text("0–100 km/h  " + seconds(entry.sprint()) + " 秒", 15, TEXT, true), 8);
            }
            add(page, card, 10);
        }
    }

    void render(String gpsStatus, boolean locked, boolean locationNeeded, float speed, float top,
                double metres, boolean recording, float force, float peak, boolean forceAvailable,
                Telemetry.Sprint.State state, long elapsed) {
        if (setText(gps, gpsStatus)) {
            gps.setTextColor(locked ? CYAN : MUTED);
            gps.setBackground(control(locked ? 0xFF102930 : PANEL, locked ? 0xFF296171 : BORDER));
            gps.setClickable(locationNeeded);
            gps.setFocusable(locationNeeded);
        }
        if (tab == 0) {
            speedGauge.setValue(speed);
            forceGauge.setValue(force);
            setText(maximum, Float.isNaN(top) ? "—" : Integer.toString(Math.round(top)));
            setText(distance, decimal(metres / 1_000));
            setText(peakForce, forceAvailable ? "峰值 " + (Float.isNaN(peak) ? "—" : decimal(peak)) + " G"
                    : "设备无可用加速度传感器");
            locationButton.setVisibility(locationNeeded ? View.VISIBLE : View.GONE);
            recordButton.setEnabled(!locationNeeded || recording);
            setText(recordButton, recording ? "结束并保存记录" : "开始记录");
        } else if (tab == 1) {
            renderSprint(state, elapsed, locationNeeded);
        }
    }

    private void renderSprint(Telemetry.Sprint.State state, long elapsed, boolean locationNeeded) {
        String status;
        String button = "准备测试";
        switch (state) {
            case WAITING: status = "等待车辆静止和稳定的 GPS 信号"; button = "取消测试"; break;
            case READY: status = "已就绪 · 起步后自动计时"; button = "取消测试"; break;
            case RUNNING: status = "正在计时"; button = "取消测试"; break;
            case COMPLETE: status = "测试完成"; button = "再次测试"; break;
            case LOST: status = "信号中断或超时 · 本次结果无效"; button = "重新测试"; break;
            default: status = locationNeeded ? "测试前需要启用 GPS" : "等待确认封闭场地"; break;
        }
        setText(sprintTime, state == Telemetry.Sprint.State.RUNNING || elapsed > 0 ? seconds(elapsed) : "—");
        setText(sprintStatus, status);
        setText(sprintButton, locationNeeded && state == Telemetry.Sprint.State.IDLE ? "启用 GPS" : button);
    }

    private boolean setText(TextView view, String text) {
        if (text.contentEquals(view.getText())) return false;
        view.setText(text);
        return true;
    }

    private static String decimal(double number) {
        return String.format(Locale.ROOT, "%.2f", number);
    }

    private static String seconds(long millis) {
        return decimal(millis / 1_000d);
    }

    private static String duration(long millis) {
        long seconds = millis / 1_000;
        return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3_600, seconds / 60 % 60, seconds % 60);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private LinearLayout panel() {
        LinearLayout card = column();
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(shape(PANEL, BORDER));
        return card;
    }

    private GradientDrawable shape(int fill, int border) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(12));
        shape.setStroke(dp(1), border);
        return shape;
    }

    private RippleDrawable control(int fill, int border) {
        return new RippleDrawable(ColorStateList.valueOf(0x3352E7FA), shape(fill, border), null);
    }

    private TextView text(String label, int size, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(label);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private TextView value(String label, int size) {
        TextView view = text(label, size, TEXT, true);
        view.setTypeface(DISPLAY);
        return view;
    }

    private TextView action(String label, boolean primary, Runnable click) {
        TextView view = new android.widget.Button(activity);
        view.setAllCaps(false);
        view.setText(label);
        view.setTextSize(16);
        view.setTextColor(primary ? ColorStateList.valueOf(BACKGROUND) : ColorStateList.valueOf(CYAN));
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(48));
        view.setMinimumHeight(dp(48));
        view.setPadding(dp(6), dp(7), dp(6), dp(7));
        view.setBackground(primary
                ? new RippleDrawable(ColorStateList.valueOf(0x55080C10), shape(CYAN, CYAN), null)
                : control(PANEL, BORDER));
        view.setStateListAnimator(null);
        view.setOnClickListener(widget -> click.run());
        return view;
    }

    private void add(LinearLayout container, View child, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(MATCH, WRAP);
        params.topMargin = dp(margin);
        container.addView(child, params);
    }

    private void addDivider(LinearLayout container) {
        View divider = new View(activity);
        divider.setBackgroundColor(BORDER);
        container.addView(divider, new LinearLayout.LayoutParams(MATCH, dp(1)));
    }
}
