package com.recom3.ghost;

import java.util.ArrayList;
import java.util.List;

final class Track {
    private static final double EARTH_RADIUS_METERS = 6371000;
    private static final int MAX_POINTS = 14400;
    private static final double MIN_LENGTH_METERS = 30;
    static final long MAX_GAP_MS = 8000;

    static final class Point {
        final double latitude;
        final double longitude;
        final long timeMs;

        Point(double latitude, double longitude, long timeMs) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.timeMs = timeMs;
        }
    }

    private final List<Point> points = new ArrayList<Point>();
    private final List<Double> distances = new ArrayList<Double>();
    private boolean continuous = true;

    boolean add(Point point) {
        if (size() == MAX_POINTS || point.timeMs < 0
                || size() == 0 && point.timeMs != 0
                || Double.isNaN(point.latitude) || Math.abs(point.latitude) > 90
                || Double.isNaN(point.longitude) || Math.abs(point.longitude) > 180) {
            return false;
        }

        double distance = 0;
        if (size() > 0) {
            Point previous = point(size() - 1);
            long interval = point.timeMs - previous.timeMs;
            if (interval <= 0) return false;
            distance = length();
            double step = distance(previous, point);
            if (interval > MAX_GAP_MS || step > 15 + interval * 0.06) {
                continuous = false;
            } else {
                distance += step;
            }
        }
        points.add(point);
        distances.add(distance);
        return true;
    }

    int size() { return points.size(); }
    Point point(int index) { return points.get(index); }
    double distanceAt(int index) { return distances.get(index); }
    double length() { return size() == 0 ? 0 : distanceAt(size() - 1); }
    long duration() { return size() == 0 ? 0 : point(size() - 1).timeMs; }

    boolean usableAsReference() {
        return continuous && size() >= 5 && length() >= MIN_LENGTH_METERS;
    }

    static double distance(Point a, Point b) {
        return Math.hypot(east(a, b), north(a, b));
    }

    static double east(Point origin, Point point) {
        double longitudeDelta = point.longitude - origin.longitude;
        if (longitudeDelta > 180) longitudeDelta -= 360;
        if (longitudeDelta < -180) longitudeDelta += 360;
        return Math.toRadians(longitudeDelta) * EARTH_RADIUS_METERS
                * Math.cos(Math.toRadians((origin.latitude + point.latitude) / 2));
    }

    static double north(Point origin, Point point) {
        return Math.toRadians(point.latitude - origin.latitude) * EARTH_RADIUS_METERS;
    }
}
