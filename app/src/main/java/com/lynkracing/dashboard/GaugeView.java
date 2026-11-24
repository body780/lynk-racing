package com.lynkracing.dashboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.util.Locale;

final class GaugeView extends View {
    static final int CYAN = 0xFF52E7FA;
    static final int TEXT = 0xFFF3F7F9;
    static final int MUTED = 0xFFA6B7C1;
    private static final int LINE = 0xFF263742;
    private static final Typeface NUMBERS = Typeface.create("sans-serif-condensed", Typeface.BOLD);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();
    private final boolean force;
    private float value = Float.NaN;

    GaugeView(Context context) {
        this(context, false);
    }

    GaugeView(Context context, boolean force) {
        super(context);
        this.force = force;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        describe();
    }

    void setValue(float value) {
        if (Float.compare(this.value, value) == 0) return;
        this.value = value;
        describe();
        invalidate();
    }

    private void describe() {
        String reading = Float.isNaN(value) ? "暂无读数" : force
                ? String.format(Locale.ROOT, "%.2f G", value)
                : Math.round(value) + " 公里每小时";
        setContentDescription((force ? "实时水平加速度，" : "实时车速，") + reading);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (force) drawForce(canvas);
        else drawSpeed(canvas);
    }

    private void drawSpeed(Canvas canvas) {
        float scale = Math.min(getWidth() / 320f, getHeight() / 245f);
        canvas.save();
        canvas.translate((getWidth() - 320 * scale) / 2f, (getHeight() - 245 * scale) / 2f);
        canvas.scale(scale, scale);
        float cx = 160, cy = 128, radius = 104;
        arc.set(cx - radius, cy - radius, cx + radius, cy + radius);
        stroke(LINE, 5);
        canvas.drawArc(arc, 148, 244, false, paint);
        float shown = Float.isNaN(value) ? 0 : Math.min(value, 260f);
        float angle = 148 + shown / 260f * 244;
        if (!Float.isNaN(value)) {
            stroke(CYAN, 5);
            canvas.drawArc(arc, 148, angle - 148, false, paint);
        }
        for (int tick = 0; tick <= 26; tick++) {
            float degrees = 148 + 244f * tick / 26;
            double radians = Math.toRadians(degrees);
            float x = (float) Math.cos(radians), y = (float) Math.sin(radians);
            boolean major = tick % 5 == 0 || tick == 26;
            stroke(degrees <= angle && !Float.isNaN(value) ? CYAN : LINE, major ? 2 : 1);
            int inner = major ? 88 : 92;
            canvas.drawLine(cx + x * inner, cy + y * inner, cx + x * 96, cy + y * 96, paint);
        }
        if (!Float.isNaN(value)) {
            double radians = Math.toRadians(angle);
            fill(CYAN);
            canvas.drawCircle(cx + (float) Math.cos(radians) * radius,
                    cy + (float) Math.sin(radians) * radius, 5, paint);
        }
        label(canvas, Float.isNaN(value) ? "—" : Integer.toString(Math.round(value)),
                cx, 151, 74, TEXT, NUMBERS);
        label(canvas, "km/h", cx, 177, 16, CYAN, Typeface.DEFAULT_BOLD);
        label(canvas, "0", 59, 204, 11, MUTED, NUMBERS);
        label(canvas, "260", 261, 204, 11, MUTED, NUMBERS);
        canvas.restore();
    }

    private void drawForce(Canvas canvas) {
        float scale = Math.min(getWidth() / 140f, getHeight() / 96f);
        canvas.save();
        canvas.translate((getWidth() - 140 * scale) / 2f, (getHeight() - 96 * scale) / 2f);
        canvas.scale(scale, scale);
        arc.set(20, 4, 120, 104);
        stroke(LINE, 5);
        canvas.drawArc(arc, 165, 210, false, paint);
        if (!Float.isNaN(value)) {
            stroke(CYAN, 5);
            canvas.drawArc(arc, 165, 210 * Math.min(value / 1.5f, 1f), false, paint);
        }
        label(canvas, Float.isNaN(value) ? "—" : String.format(Locale.ROOT, "%.2f", value),
                70, 62, 33, TEXT, NUMBERS);
        label(canvas, "G", 70, 81, 13, CYAN, Typeface.DEFAULT_BOLD);
        canvas.restore();
    }

    private void stroke(int color, float width) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(width);
        paint.setColor(color);
    }

    private void fill(int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
    }

    private void label(Canvas canvas, String text, float x, float y, float size, int color, Typeface face) {
        fill(color);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(face);
        paint.setTextSize(size);
        canvas.drawText(text, x, y, paint);
    }
}
