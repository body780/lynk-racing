package com.lynkracing.dashboard;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteException;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.WindowManager;
import android.widget.Toast;

import java.util.Collections;
import java.util.List;

public final class MainActivity extends Activity implements LocationListener, SensorEventListener {
    private static final int LOCATION_REQUEST = 1;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Telemetry.Sprint sprint = new Telemetry.Sprint();
    private final float[] gravity = new float[3];
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            long now = SystemClock.elapsedRealtime();
            sprint.tick(now);
            if (recording && now - lastSave >= 5_000) saveDrive(now);
            updateUi(now);
            handler.postDelayed(this, 100);
        }
    };

    private Dashboard ui;
    private DriveStore store;
    private LocationManager locations;
    private SensorManager sensors;
    private Sensor accelerometer;
    private Sensor linearSensor;
    private Sensor gravitySensor;
    private boolean fore;
    private boolean gpsEnabled;
    private boolean listening;
    private boolean forceAvailable;
    private boolean gravityReady;
    private boolean recording;
    private boolean saveError;
    private long gravityTime;
    private long sensorTime;
    private long lastFix;
    private long lastSpeedTime;
    private long started;
    private long startedTime;
    private long lastSave;
    private long entryId;
    private long bestSprint;
    private float speed = Float.NaN;
    private float topSpeed = Float.NaN;
    private float force = Float.NaN;
    private float peakG = Float.NaN;
    private double distance;
    private Location distanceAnchor;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        locations = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        sensors = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        linearSensor = sensors.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        gravitySensor = sensors.getDefaultSensor(Sensor.TYPE_GRAVITY);
        store = new DriveStore(this);
        ui = new Dashboard(this);
        setContentView(ui.root);
        ui.show(state == null ? 0 : state.getInt("tab"));
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("tab", ui.tab());
        super.onSaveInstanceState(state);
    }

    @Override protected void onStart() {
        super.onStart();
        fore = true;
        connectGps();
        startSensors();
        handler.post(refresh);
    }

    @Override protected void onStop() {
        fore = false;
        handler.removeCallbacks(refresh);
        disconnectGps();
        sensors.unregisterListener(this);
        resetSensors();
        if (sprint.active()) sprint.cancel();
        if (recording) endDrive();
        super.onStop();
    }

    @Override protected void onDestroy() {
        store.close();
        super.onDestroy();
    }

    private boolean preciseLocation() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    void enableLocation() {
        if (!preciseLocation()) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_REQUEST);
        } else if (!gpsEnabled) {
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        } else {
            connectGps();
        }
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == LOCATION_REQUEST && fore) {
            connectGps();
            if (!preciseLocation()) toast("需要精准位置权限才能读取 GPS 车速");
        }
    }

    private void connectGps() {
        gpsEnabled = locations.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if (!preciseLocation() || !gpsEnabled) {
            disconnectGps();
        } else if (!listening) {
            try {
                locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, this);
                listening = true;
            } catch (SecurityException | IllegalArgumentException exception) {
                gpsEnabled = false;
            }
        }
    }

    private void disconnectGps() {
        if (listening) locations.removeUpdates(this);
        listening = false;
        lastFix = 0;
        lastSpeedTime = 0;
        distanceAnchor = null;
    }

    @Override public void onProviderEnabled(String provider) {
        if (fore && LocationManager.GPS_PROVIDER.equals(provider)) connectGps();
    }

    @Override public void onProviderDisabled(String provider) {
        if (!LocationManager.GPS_PROVIDER.equals(provider)) return;
        gpsEnabled = false;
        lastFix = 0;
        lastSpeedTime = 0;
        distanceAnchor = null;
        sprint.signalLost();
    }

    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}

    @Override public void onLocationChanged(Location location) {
        if (!fore || !listening || !gpsEnabled) return;
        long time = location.getElapsedRealtimeNanos() / 1_000_000;
        long now = SystemClock.elapsedRealtime();
        if (!Telemetry.fresh(now, time) || time <= lastFix) return;
        if (!location.hasAccuracy() || !Telemetry.usableSpeed(0, location.getAccuracy())) return;
        lastFix = time;

        float current = location.getSpeed() * 3.6f;
        boolean speedValid = location.hasSpeed() && Telemetry.usableSpeed(current, location.getAccuracy());
        if (speedValid) {
            speed = current;
            lastSpeedTime = time;
        }

        if (recording && time >= startedTime) {
            if (speedValid) topSpeed = Float.isNaN(topSpeed) ? current : Math.max(topSpeed, current);
            addDistance(location, time, speedValid ? current : Float.NaN);
        }

        if (speedValid && sprint.active() && location.getAccuracy() <= 15f
                && (android.os.Build.VERSION.SDK_INT < 26 || !location.hasSpeedAccuracy()
                || location.getSpeedAccuracyMetersPerSecond() <= 1.5f)) {
            sprint.sample(time, current);
            if (sprint.state == Telemetry.Sprint.State.COMPLETE) saveSprint(now);
        }
    }

    private void addDistance(Location fix, long time, float current) {
        long previous = distanceAnchor == null ? 0 : distanceAnchor.getElapsedRealtimeNanos() / 1_000_000;
        if (!Telemetry.fresh(time, previous) || (!Float.isNaN(current) && current < 2f)) {
            distanceAnchor = fix;
            return;
        }
        float metres = distanceAnchor.distanceTo(fix);
        float noise = (fix.getAccuracy() + distanceAnchor.getAccuracy()) * 0.5f;
        if (Telemetry.plausibleDistance(metres, noise, time - previous)) {
            distance += metres;
            distanceAnchor = fix;
        }
    }

    void toggleRecord() {
        if (recording) {
            endDrive();
        } else if (!preciseLocation() || !gpsEnabled) {
            enableLocation();
        } else {
            started = System.currentTimeMillis();
            startedTime = SystemClock.elapsedRealtime();
            distance = 0;
            topSpeed = Float.NaN;
            peakG = Float.NaN;
            distanceAnchor = null;
            bestSprint = 0;
            entryId = 0;
            lastSave = startedTime;
            saveError = false;
            recording = true;
        }
        updateUi(SystemClock.elapsedRealtime());
    }

    private void endDrive() {
        boolean saved = saveDrive(SystemClock.elapsedRealtime());
        recording = false;
        if (saved) toast("驾驶记录已保存");
    }

    private boolean saveDrive(long now) {
        try {
            DriveStore.Entry entry = new DriveStore.Entry(entryId, started, now - startedTime,
                    distance, Float.isNaN(topSpeed) ? -1 : topSpeed, Float.isNaN(peakG) ? -1 : peakG,
                    bestSprint, false);
            if (entryId == 0) entryId = store.add(entry);
            else store.update(entry);
            saveError = false;
            if (ui.tab() == 2) ui.showHistory();
            return true;
        } catch (SQLiteException exception) {
            if (!saveError) toast("驾驶记录保存失败，请检查设备存储空间");
            saveError = true;
            return false;
        } finally {
            lastSave = now;
        }
    }

    void toggleSprint() {
        if (sprint.active()) {
            sprint.cancel();
            updateUi(SystemClock.elapsedRealtime());
            return;
        }
        if (!preciseLocation() || !gpsEnabled) {
            enableLocation();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("确认在封闭场地")
                .setMessage("0–100 km/h 测试仅限封闭场地。请先固定手机，在车辆静止时确认，行驶中请勿操作手机。")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认并准备", (dialog, which) -> {
                    sprint.arm();
                    updateUi(SystemClock.elapsedRealtime());
                })
                .show();
    }

    private void saveSprint(long now) {
        if (recording) {
            if (bestSprint == 0 || sprint.resultMs < bestSprint) bestSprint = sprint.resultMs;
            saveDrive(now);
            return;
        }
        try {
            store.add(new DriveStore.Entry(0, System.currentTimeMillis() - sprint.resultMs, sprint.resultMs,
                    0, 100, -1, sprint.resultMs, true));
        } catch (SQLiteException exception) {
            toast("测试结果保存失败，请检查设备存储空间");
        }
    }

    List<DriveStore.Entry> history() {
        try {
            return store.entries();
        } catch (SQLiteException exception) {
            toast("无法读取驾驶记录");
            return Collections.emptyList();
        }
    }

    private void startSensors() {
        resetSensors();
        if (linearSensor != null && gravitySensor != null
                && sensors.registerListener(this, gravitySensor, SensorManager.SENSOR_DELAY_GAME)
                && sensors.registerListener(this, linearSensor, SensorManager.SENSOR_DELAY_GAME)) {
            forceAvailable = true;
            return;
        }
        sensors.unregisterListener(this);
        forceAvailable = accelerometer != null
                && sensors.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
    }

    private void resetSensors() {
        gravityReady = false;
        gravityTime = 0;
        sensorTime = 0;
        force = Float.NaN;
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (!fore || event.values.length < 3) return;
        int type = event.sensor.getType();
        float x = event.values[0], y = event.values[1], z = event.values[2];
        if (type == Sensor.TYPE_GRAVITY) {
            setGravity(x, y, z);
            gravityReady = true;
            gravityTime = event.timestamp;
            return;
        }
        if (type == Sensor.TYPE_ACCELEROMETER) {
            if (!gravityReady || event.timestamp - gravityTime > 500_000_000) {
                setGravity(x, y, z);
                gravityReady = true;
            } else {
                float dt = (event.timestamp - gravityTime) / 1_000_000_000f;
                float blend = Math.max(0f, dt) / (1.2f + Math.max(0f, dt));
                setGravity(gravity[0] + blend * (x - gravity[0]), gravity[1] + blend * (y - gravity[1]),
                        gravity[2] + blend * (z - gravity[2]));
            }
            gravityTime = event.timestamp;
            x -= gravity[0];
            y -= gravity[1];
            z -= gravity[2];
        }
        if (!gravityReady || Math.abs(event.timestamp - gravityTime) > 500_000_000) return;
        float reading = Telemetry.horizontalG(x, y, z, gravity[0], gravity[1], gravity[2]);
        if (!Telemetry.finite(reading)) return;
        sensorTime = event.timestamp / 1_000_000;
        force = Float.isNaN(force) ? reading : .75f * force + .25f * reading;
        if (recording) peakG = Float.isNaN(peakG) ? force : Math.max(force, peakG);
    }

    private void setGravity(float x, float y, float z) {
        gravity[0] = x;
        gravity[1] = y;
        gravity[2] = z;
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void updateUi(long now) {
        boolean hasPermission = preciseLocation();
        boolean freshFix = Telemetry.fresh(now, lastFix);
        boolean freshSpeed = Telemetry.fresh(now, lastSpeedTime);
        String gps = !hasPermission ? "等待位置权限" : !gpsEnabled ? "GPS 不可用"
                : !freshFix ? "正在搜索卫星" : !freshSpeed ? "已定位 · 等待车速" : "GPS 已锁定";
        ui.render(gps, freshSpeed, !hasPermission || !gpsEnabled,
                freshSpeed ? speed : Float.NaN, topSpeed, distance, recording,
                Telemetry.fresh(now, sensorTime) ? force : Float.NaN, peakG, forceAvailable,
                sprint.state, sprint.elapsed(now));
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
