package com.recom3.ghost;

final class GhostMatcher {
    private static final double CORRIDOR_METERS = 25;
    private static final double BACKTRACK_METERS = 10;
    private static final double FINISH_METERS = 15;

    private final Track reference;
    private int lastSegment;
    private double furthestProgress;
    private long lastMatchMs;

    GhostMatcher(Track reference) {
        this.reference = reference;
    }

    boolean canStart(Track.Point start) {
        return Track.distance(reference.point(0), start) <= CORRIDOR_METERS;
    }

    Match match(Track.Point position) {
        double lookAhead = Math.min(150, 20 + Math.max(0, position.timeMs - lastMatchMs) * 0.05);
        double bestError = CORRIDOR_METERS;
        Match best = null;
        int bestSegment = lastSegment;
        int firstSegment = lastSegment;
        while (firstSegment > 0
                && reference.distanceAt(firstSegment) >= furthestProgress - BACKTRACK_METERS) {
            firstSegment--;
        }

        for (int i = firstSegment; i < reference.size() - 1; i++) {
            double start = reference.distanceAt(i);
            if (start > furthestProgress + lookAhead) break;
            double length = reference.distanceAt(i + 1) - start;
            if (length < 0.5 || start + length < furthestProgress - BACKTRACK_METERS) continue;

            Track.Point a = reference.point(i);
            Track.Point b = reference.point(i + 1);
            double dx = Track.east(a, b);
            double dy = Track.north(a, b);
            double px = Track.east(a, position);
            double py = Track.north(a, position);
            double fraction = Math.max(0, Math.min(1, (px * dx + py * dy) / (dx * dx + dy * dy)));
            double progress = start + length * fraction;
            if (progress < furthestProgress - BACKTRACK_METERS || progress > furthestProgress + lookAhead) {
                continue;
            }
            double error = Math.hypot(px - fraction * dx, py - fraction * dy);
            if (error < bestError) {
                bestError = error;
                bestSegment = i;
                long referenceTime = a.timeMs + Math.round((b.timeMs - a.timeMs) * fraction);
                best = new Match(position.timeMs - referenceTime, progress);
            }
        }
        if (best != null) {
            furthestProgress = Math.max(furthestProgress, best.progressMeters);
            lastSegment = bestSegment;
            lastMatchMs = position.timeMs;
        }
        return best;
    }

    boolean nearFinish(Track.Point position, Match match) {
        return match != null && reference.length() - match.progressMeters <= FINISH_METERS
                && Track.distance(reference.point(reference.size() - 1), position) <= FINISH_METERS;
    }

    static final class Match {
        final long deltaMs;
        final double progressMeters;

        Match(long deltaMs, double progressMeters) {
            this.deltaMs = deltaMs;
            this.progressMeters = progressMeters;
        }
    }
}
