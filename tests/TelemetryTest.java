package com.lynkracing.dashboard;

public final class TelemetryTest {
    private static int checks;

    public static void main(String[] args) {
        check(Telemetry.usableSpeed(0, 4), "stationary GPS speed");
        check(!Telemetry.usableSpeed(-1, 4), "negative speed rejected");
        check(!Telemetry.usableSpeed(Float.NaN, 4), "missing speed rejected");
        check(!Telemetry.usableSpeed(70, 26), "poor GPS accuracy rejected");
        check(!Telemetry.usableSpeed(900, 4), "impossible speed rejected");
        check(Telemetry.fresh(4_000, 1_000), "maximum valid age");
        check(!Telemetry.fresh(4_001, 1_000), "stale GPS sample");
        check(!Telemetry.fresh(1_000, 1_001), "future GPS sample");
        check(Telemetry.plausibleDistance(8, 5, 1_000), "valid distance");
        check(!Telemetry.plausibleDistance(3, 5, 1_000), "position jitter ignored");
        check(!Telemetry.plausibleDistance(300, 5, 1_000), "GPS jump rejected");
        check(!Telemetry.plausibleDistance(10, 5, 3_001), "GPS outage excluded from distance");
        check(Math.abs(Telemetry.horizontalG(9.80665f, 0, 10, 0, 0, 9.80665f) - 1) < 0.001,
                "vertical acceleration excluded from horizontal G");
        check(Math.abs(Telemetry.horizontalG(3, 4, 0, 3, 4, 0)) < 0.001,
                "projection handles a tilted phone");
        check(Float.isNaN(Telemetry.horizontalG(3, 4, 0, 0, 0, 0)), "waits for gravity");

        Telemetry.Sprint run = new Telemetry.Sprint();
        run.sample(1_000, 10);
        state(run, Telemetry.Sprint.State.IDLE);
        run.arm();
        run.sample(1_000, 7);
        run.sample(2_000, 0);
        run.sample(2_500, 0);
        state(run, Telemetry.Sprint.State.WAITING);
        run.sample(3_000, 0);
        state(run, Telemetry.Sprint.State.READY);
        run.sample(4_000, 20);
        state(run, Telemetry.Sprint.State.RUNNING);
        run.sample(4_000, 110);
        state(run, Telemetry.Sprint.State.RUNNING);
        run.sample(5_000, 80);
        run.sample(6_000, 120);
        state(run, Telemetry.Sprint.State.COMPLETE);
        check(run.resultMs == 2_450, "interpolates 1 and 100 km/h crossings");
        run.sample(7_000, 130);
        check(run.elapsed(8_000) == 2_450, "freezes completed timer");

        run.arm();
        run.sample(1_000, 0);
        run.sample(2_000, 0);
        run.tick(5_001);
        state(run, Telemetry.Sprint.State.WAITING);
        run.sample(6_000, 10);
        state(run, Telemetry.Sprint.State.WAITING);
        run.sample(7_000, 0);
        run.sample(8_000, 0);
        run.sample(9_000, 25);
        run.sample(13_000, 110);
        state(run, Telemetry.Sprint.State.LOST);
        check(run.elapsed(13_000) == 0, "interrupted test has no result");

        run.arm();
        run.sample(1_000, 0);
        run.sample(2_000, 0);
        run.sample(3_000, 5);
        run.sample(4_000, 0);
        state(run, Telemetry.Sprint.State.WAITING);
        run.sample(5_000, 0);
        state(run, Telemetry.Sprint.State.READY);
        run.cancel();
        state(run, Telemetry.Sprint.State.IDLE);
        System.out.println("Telemetry checks passed: " + checks);
    }

    private static void state(Telemetry.Sprint run, Telemetry.Sprint.State expected) {
        check(run.state == expected, "expected " + expected + ", got " + run.state);
    }

    private static void check(boolean result, String label) {
        if (!result) throw new AssertionError(label);
        checks++;
    }
}
