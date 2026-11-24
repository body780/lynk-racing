package com.lynkracing.dashboard;

final class Telemetry {
    static final long MAX_GAP_MS = 3_000;
    static final float MAX_ACCURACY_M = 25f;
    private static final float MAX_SPEED_KMH = 450f;

    static boolean usableSpeed(float speedKmh, float accuracyM) {
        return finite(speedKmh) && speedKmh >= 0 && speedKmh <= MAX_SPEED_KMH
                && finite(accuracyM) && accuracyM > 0 && accuracyM <= MAX_ACCURACY_M;
    }

    static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    static boolean fresh(long now, long sample) {
        return sample > 0 && now >= sample && now - sample <= MAX_GAP_MS;
    }

    static float horizontalG(float x, float y, float z, float gx, float gy, float gz) {
        double magnitudeSquared = gx * gx + gy * gy + gz * gz;
        if (magnitudeSquared < 25) return Float.NaN;
        double vertical = x * gx + y * gy + z * gz;
        double horizontalSquared = x * x + y * y + z * z - vertical * vertical / magnitudeSquared;
        return (float) (Math.sqrt(Math.max(0, horizontalSquared)) / 9.80665);
    }

    static boolean plausibleDistance(float metres, float positionNoise, long elapsedMs) {
        return elapsedMs > 0 && elapsedMs <= MAX_GAP_MS && finite(metres)
                && metres >= Math.max(3f, positionNoise)
                && metres <= MAX_SPEED_KMH / 3_600f * elapsedMs + positionNoise;
    }

    static final class Sprint {
        enum State { IDLE, WAITING, READY, RUNNING, COMPLETE, LOST }

        private static final float STOPPED = 1f;
        private static final float TARGET = 100f;
        private static final long STATIONARY_MS = 1_000;
        private static final long LIMIT_MS = 60_000;
        State state = State.IDLE;
        long lastTime;
        private float lastSpeed;
        private long stationarySince;
        private double startTime;
        long resultMs;

        void arm() {
            state = State.WAITING;
            lastTime = 0;
            stationarySince = 0;
            resultMs = 0;
        }

        void cancel() {
            state = State.IDLE;
        }

        boolean active() {
            return state == State.WAITING || state == State.READY || state == State.RUNNING;
        }

        void tick(long now) {
            if (active() && lastTime > 0 && !fresh(now, lastTime)) signalLost();
            if (state == State.RUNNING && now - startTime > LIMIT_MS) signalLost();
        }

        void signalLost() {
            if (state == State.RUNNING) {
                state = State.LOST;
            } else if (active()) {
                state = State.WAITING;
                stationarySince = 0;
                lastTime = 0;
            }
        }

        void sample(long time, float speed) {
            if (!active() || time <= lastTime) return;
            tick(time);

            switch (state) {
                case WAITING:
                    if (speed <= STOPPED) {
                        if (stationarySince == 0) stationarySince = time;
                        if (time - stationarySince >= STATIONARY_MS) state = State.READY;
                    } else {
                        stationarySince = 0;
                    }
                    break;
                case READY:
                    if (speed > STOPPED) {
                        startTime = crossing(STOPPED, time, speed);
                        state = State.RUNNING;
                        finishIfReached(time, speed);
                    }
                    break;
                case RUNNING:
                    if (speed <= STOPPED) {
                        state = State.WAITING;
                        stationarySince = time;
                    } else {
                        finishIfReached(time, speed);
                    }
                    break;
                default:
                    break;
            }
            lastTime = time;
            lastSpeed = speed;
        }

        private double crossing(float threshold, long time, float speed) {
            return lastTime + (time - lastTime) * (double) (threshold - lastSpeed) / (speed - lastSpeed);
        }

        private void finishIfReached(long time, float speed) {
            if (speed >= TARGET) {
                resultMs = Math.round(crossing(TARGET, time, speed) - startTime);
                if (resultMs > 0) state = State.COMPLETE;
                else state = State.LOST;
            }
        }

        long elapsed(long now) {
            if (state == State.COMPLETE) return resultMs;
            if (state == State.RUNNING) return Math.max(0, Math.round(now - startTime));
            return 0;
        }
    }
}
