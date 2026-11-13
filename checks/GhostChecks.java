package com.recom3.ghost;

import java.io.File;

public final class GhostChecks {
    private static Track.Point point(double east, double north, long time) {
        return new Track.Point(45 + north / 111195, 6 + east / (111195 * Math.cos(Math.toRadians(45))), time);
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }

    public static void main(String[] args) throws Exception {
        Track straight = new Track();
        for (int i = 0; i <= 20; i++) check(straight.add(point(i * 10, 0, i * 1000)), "sample accepted");
        check(straight.usableAsReference(), "continuous reference");
        GhostMatcher matcher = new GhostMatcher(straight);
        check(!matcher.canStart(point(40, 0, 0)), "distant start");
        check(matcher.canStart(point(0, 0, 0)), "same start");
        check(Math.abs(matcher.match(point(15, 0, 1000)).deltaMs + 500) <= 1, "interpolated lead");
        check(Math.abs(matcher.match(point(30, 0, 4000)).deltaMs - 1000) <= 1, "lag");
        check(matcher.match(point(40, 60, 5000)) == null, "off course");
        check(matcher.match(point(45, 0, 6000)) != null, "reacquisition");
        check(matcher.match(point(175, 0, 7000)) == null, "no forward jump");
        for (int i = 5; i < 20; i++) check(matcher.match(point(i * 10, 0, 2000 + i * 1000)) != null, "progress");
        Track.Point finish = point(200, 0, 22000);
        check(matcher.nearFinish(finish, matcher.match(finish)), "finish");

        Track hairpin = new Track();
        for (int i = 0; i <= 10; i++) hairpin.add(point(i * 10, 0, i * 1000));
        for (int i = 0; i <= 10; i++) hairpin.add(point(100 - i * 10, 8, (i + 11) * 1000));
        check(new GhostMatcher(hairpin).match(point(10, 6, 1000)).progressMeters < 15, "outbound hairpin leg");

        Track gap = new Track();
        for (int i = 0; i < 5; i++) gap.add(point(i * 10, 0, i * 1000));
        gap.add(point(50, 0, 15000));
        check(!gap.usableAsReference(), "reference rejects a long outage");

        File directory = new File(args[0]);
        ReferenceStore store = new ReferenceStore(directory);
        check(store.load() == null, "no reference yet");
        store.save(straight);
        check(store.load().size() == 21, "round trip");
        store.save(hairpin);
        check(store.load().duration() == hairpin.duration(), "atomic replacement");
        System.out.println("Ghost checks passed");
    }
}
