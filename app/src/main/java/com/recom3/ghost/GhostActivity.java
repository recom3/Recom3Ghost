package com.recom3.ghost;

import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManager;

import java.io.IOException;

public final class GhostActivity extends Activity implements LocationListener {
    private static final long FIX_TIMEOUT_MS = 4000;
    private static final float MAX_ACCURACY_METERS = 18;

    private final Handler handler = new Handler();
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            render();
            handler.postDelayed(this, 500);
        }
    };

    private GhostHud hud;
    private LocationManager gps;
    private ReferenceStore store;
    private Track reference;
    private Track run;
    private GhostMatcher matcher;
    private GhostMatcher.Match match;
    private Track.Point position;
    private Location fix;
    private long fixMs;
    private long runStartMs;
    private long startRequestedMs;
    private Long resultMs;
    private boolean gpsEnabled;
    private boolean listening;
    private boolean replacing;
    private String message = "";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        gps = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        store = new ReferenceStore(getFilesDir());
        hud = new GhostHud(this);
        setContentView(hud);
        try {
            reference = store.load();
        } catch (IOException exception) {
            message = "Error al leer la referencia";
            Log.e("Recom3Ghost", message, exception);
        }
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        try {
            if (gps != null && gps.getAllProviders().contains(LocationManager.GPS_PROVIDER)) {
                gpsEnabled = gps.isProviderEnabled(LocationManager.GPS_PROVIDER);
                gps.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this);
                listening = true;
            }
        } catch (SecurityException exception) {
            gpsEnabled = false;
            message = "Activa el permiso de ubicación";
        }
        handler.post(refresh);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(refresh);
        if (listening) {
            gps.removeUpdates(this);
            listening = false;
        }
        fix = null;
        if (run != null || startRequestedMs != 0) {
            clearRun();
            message = "Bajada interrumpida";
        }
        super.onPause();
    }

    @Override public void onLocationChanged(Location location) {
        long now = SystemClock.elapsedRealtime();
        long sampleTime = Build.VERSION.SDK_INT >= 17
                ? location.getElapsedRealtimeNanos() / 1000000L : now;
        if (sampleTime <= 0) sampleTime = now;
        if (!location.hasAccuracy() || location.getAccuracy() > MAX_ACCURACY_METERS
                || now - sampleTime > FIX_TIMEOUT_MS || sampleTime > now) {
            fix = null;
            match = null;
            render();
            return;
        }

        fix = location;
        fixMs = sampleTime;
        if (startRequestedMs != 0 && sampleTime >= startRequestedMs) {
            begin(location, sampleTime);
        } else if (run != null) {
            Track.Point next = new Track.Point(location.getLatitude(), location.getLongitude(), sampleTime - runStartMs);
            if (run.add(next)) {
                position = next;
                if (matcher != null) match = matcher.match(position);
            } else {
                match = null;
            }
        }
        render();
    }

    @Override public void onProviderEnabled(String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider)) gpsEnabled = true;
        render();
    }

    @Override public void onProviderDisabled(String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider)) {
            gpsEnabled = false;
            fix = null;
            match = null;
        }
        render();
    }

    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}

    @Override public boolean onKeyUp(int code, KeyEvent event) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                if (event.isCanceled()) return true;
                if (run != null) finishRun();
                else if (startRequestedMs == 0) requestStart();
                render();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (reference != null && run == null && startRequestedMs == 0 && !event.isCanceled()) {
                    replacing = !replacing;
                    resultMs = null;
                    message = "";
                    render();
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (run != null || startRequestedMs != 0 || replacing) {
                    clearRun();
                    message = "Cancelado";
                    render();
                    return true;
                }
                break;
            default:
                break;
        }
        return super.onKeyUp(code, event);
    }

    private void requestStart() {
        resultMs = null;
        if (!hasFix()) {
            message = "Espera a que el GPS esté listo";
            return;
        }
        startRequestedMs = SystemClock.elapsedRealtime();
        message = "";
    }

    private void begin(Location location, long sampleTime) {
        startRequestedMs = 0;
        Track.Point start = new Track.Point(location.getLatitude(), location.getLongitude(), 0);
        GhostMatcher candidate = reference != null && !replacing ? new GhostMatcher(reference) : null;
        if (candidate != null && !candidate.canStart(start)) {
            message = "Acércate a la salida del ghost";
            return;
        }
        run = new Track();
        run.add(start);
        runStartMs = sampleTime;
        position = start;
        matcher = candidate;
        match = matcher == null ? null : matcher.match(start);
        message = "";
    }

    private void finishRun() {
        if (!hasFix()) {
            message = "Recupera la señal para terminar";
            return;
        }
        if (matcher != null) {
            if (!matcher.nearFinish(position, match)) {
                message = "Termina cerca de la meta del ghost";
                return;
            }
            resultMs = match.deltaMs;
            message = "Bajada terminada";
        } else {
            if (!run.usableAsReference()) {
                message = "Recorrido corto o GPS interrumpido";
                return;
            }
            try {
                store.save(run);
                reference = run;
                message = "Referencia guardada";
            } catch (IOException exception) {
                message = "Error al guardar · pulsa para reintentar";
                Log.e("Recom3Ghost", message, exception);
                return;
            }
        }
        clearRun();
    }

    private boolean hasFix() {
        return gpsEnabled && fix != null && SystemClock.elapsedRealtime() - fixMs <= FIX_TIMEOUT_MS;
    }

    private void clearRun() {
        run = null;
        matcher = null;
        match = null;
        position = null;
        startRequestedMs = 0;
        replacing = false;
    }

    private void render() {
        boolean ready = hasFix();
        if (startRequestedMs != 0 && SystemClock.elapsedRealtime() - startRequestedMs > FIX_TIMEOUT_MS) {
            startRequestedMs = 0;
            message = "Pulsa OK cuando vuelva el GPS";
        }
        String title;
        String action;
        Long delta = null;
        String status = message;
        if (run != null) {
            title = matcher == null ? "GRABANDO REFERENCIA" : "VS. TU GHOST";
            action = "OK  Terminar   ·   Atrás  Cancelar";
            if (matcher != null && ready && match != null) delta = match.deltaMs;
            if (!ready) status = "Señal GPS débil";
            else if (matcher != null && match == null) status = "Fuera del recorrido";
        } else {
            title = replacing ? "NUEVA REFERENCIA" : reference == null ? "GRABAR REFERENCIA" : "VS. TU GHOST";
            if (startRequestedMs != 0) action = "Esperando posición   ·   Atrás  Cancelar";
            else if (replacing) action = "OK  Grabar   ·   Atrás  Cancelar";
            else if (reference == null) action = "OK  Grabar";
            else action = "OK  Comparar   ·   →  Nueva referencia";
            delta = resultMs;
        }
        float speed = ready && fix.hasSpeed() ? fix.getSpeed() * 3.6f : -1;
        String gpsStatus = ready ? "GPS LISTO" : gpsEnabled ? "BUSCANDO GPS" : "GPS NO DISPONIBLE";
        hud.update(title, gpsStatus,
                ready, action, status, delta, speed, run == null ? -1 : run.length(),
                run == null ? -1 : run.duration(), matcher == null ? run : reference,
                match == null ? null : match.progressMeters);
    }
}
