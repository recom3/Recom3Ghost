package com.recom3.ghost;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import com.reconinstruments.ui.FontUtils;

import java.util.Locale;

final class GhostHud extends View {
    private static final int BACKGROUND = Color.rgb(8, 29, 39);
    private static final int PANEL = Color.rgb(15, 45, 58);
    private static final int WHITE = Color.rgb(241, 249, 252);
    private static final int MUTED = Color.rgb(160, 193, 205);
    private static final int GREEN = Color.rgb(76, 227, 155);
    private static final int AMBER = Color.rgb(255, 191, 112);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF panel = new RectF(12, 42, 416, 211);
    private final Typeface regular;
    private final Typeface bold;

    private String title = "";
    private String gps = "";
    private boolean ready;
    private String action = "";
    private String status = "";
    private Long deltaMs;
    private float speed;
    private double meters;
    private long elapsedMs;
    private Track track;
    private Double progress;

    GhostHud(Context context) {
        super(context);
        regular = FontUtils.getFontFromRes(context, com.reconinstruments.ui.R.raw.opensans_regular);
        bold = FontUtils.getFontFromRes(context, com.reconinstruments.ui.R.raw.opensans_bold);
    }

    void update(String title, String gps, boolean ready, String action, String status, Long deltaMs,
                float speed, double meters, long elapsedMs, Track track, Double progress) {
        this.title = title;
        this.gps = gps;
        this.ready = ready;
        this.action = action;
        this.status = status;
        this.deltaMs = deltaMs;
        this.speed = speed;
        this.meters = meters;
        this.elapsedMs = elapsedMs;
        this.track = track;
        this.progress = progress;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(BACKGROUND);
        float scale = Math.min(getWidth() / 428f, getHeight() / 240f);
        canvas.save();
        canvas.translate((getWidth() - 428f * scale) / 2, (getHeight() - 240f * scale) / 2);
        canvas.scale(scale, scale);

        text(canvas, "RECOM3 GHOST", 17, 27, 17, WHITE, true, Paint.Align.LEFT);
        text(canvas, gps, 411, 27, 13, ready ? GREEN : AMBER, true, Paint.Align.RIGHT);

        paint.setColor(PANEL);
        canvas.drawRoundRect(panel, 13, 13, paint);
        text(canvas, title, 25, 67, 15, MUTED, true, Paint.Align.LEFT);

        String value = "—";
        String label = "";
        int color = MUTED;
        if (deltaMs != null) {
            long seconds = Math.round(Math.abs(deltaMs) / 1000.0);
            String sign = seconds == 0 ? "" : deltaMs < 0 ? "−" : "+";
            value = sign + seconds + " s";
            color = seconds == 0 ? WHITE : deltaMs < 0 ? GREEN : AMBER;
            label = seconds == 0 ? "A LA PAR" : deltaMs < 0 ? "POR DELANTE" : "POR DETRÁS";
        } else if (elapsedMs >= 0 && title.equals("GRABANDO REFERENCIA")) {
            value = String.format(Locale.ROOT, "%d:%02d", elapsedMs / 60000, elapsedMs / 1000 % 60);
            color = WHITE;
        }
        text(canvas, value, 24, 133, 48, color, true, Paint.Align.LEFT);
        text(canvas, label, 26, 155, 14, color, true, Paint.Align.LEFT);
        drawRoute(canvas);

        text(canvas, status, 25, 177, 14, AMBER, false, Paint.Align.LEFT);
        text(canvas, speed < 0 ? "— km/h" : String.format(Locale.getDefault(), "%.0f km/h", speed),
                25, 199, 18, WHITE, true, Paint.Align.LEFT);
        text(canvas, meters < 0 ? "— km" : String.format(Locale.getDefault(), "%.2f km", meters / 1000),
                402, 199, 18, WHITE, true, Paint.Align.RIGHT);
        text(canvas, action, 214, 230, 15, WHITE, false, Paint.Align.CENTER);
        canvas.restore();
    }

    private void drawRoute(Canvas canvas) {
        if (track == null || track.size() < 2) return;
        Track.Point origin = track.point(0);
        double minX = 0, maxX = 0, minY = 0, maxY = 0;
        int last = track.size() - 1;
        int stride = Math.max(1, last / 160);
        for (int i = 0; ; i = Math.min(i + stride, last)) {
            Track.Point point = track.point(i);
            double x = Track.east(origin, point);
            double y = Track.north(origin, point);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            if (i == last) break;
        }
        double scale = Math.min(157 / Math.max(1, maxX - minX), 77 / Math.max(1, maxY - minY));
        double centerX = 318 - (minX + maxX) * scale / 2;
        double centerY = 113 + (minY + maxY) * scale / 2;
        Path path = new Path();
        path.moveTo((float) centerX, (float) centerY);
        for (int i = stride; ; i = Math.min(i + stride, last)) {
            Track.Point point = track.point(i);
            path.lineTo((float) (centerX + Track.east(origin, point) * scale),
                    (float) (centerY - Track.north(origin, point) * scale));
            if (i == last) break;
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f);
        paint.setColor(MUTED);
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);

        if (!ready || progress == null && !title.equals("GRABANDO REFERENCIA")) return;
        Track.Point end = track.point(last);
        double east = Track.east(origin, end);
        double north = Track.north(origin, end);
        if (progress != null) {
            int index = 1;
            while (index < last && track.distanceAt(index) < progress) index++;
            Track.Point start = track.point(index - 1);
            end = track.point(index);
            double distance = track.distanceAt(index) - track.distanceAt(index - 1);
            double fraction = distance > 0 ? (progress - track.distanceAt(index - 1)) / distance : 0;
            east = Track.east(origin, start) + fraction * Track.east(start, end);
            north = Track.north(origin, start) + fraction * Track.north(start, end);
        }
        paint.setColor(GREEN);
        canvas.drawCircle((float) (centerX + east * scale), (float) (centerY - north * scale), 4.5f, paint);
    }

    private void text(Canvas canvas, String text, float x, float y, float size, int color,
                      boolean strong, Paint.Align align) {
        paint.setTextSize(size);
        paint.setColor(color);
        paint.setTypeface(strong ? bold : regular);
        paint.setTextAlign(align);
        canvas.drawText(text, x, y, paint);
    }
}
