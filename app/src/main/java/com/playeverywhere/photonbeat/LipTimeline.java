package com.playeverywhere.photonbeat;

/** A single capture writer and the UI reader. Entries are timestamped in elapsed real time. */
final class LipTimeline {
    private static final int CAPACITY = 80;
    private final MouthPose[] poses = new MouthPose[CAPACITY];
    private final long[] times = new long[CAPACITY];
    private int next, count;
    private long received;

    LipTimeline() {
        for (int i = 0; i < CAPACITY; i++) poses[i] = new MouthPose();
    }

    synchronized void clear() { next = count = 0; }

    synchronized void publish(long at, long arrival, MouthPose pose) {
        if (count > 0 && at <= times[(next + CAPACITY - 1) % CAPACITY]) return;
        poses[next].set(pose, pose, 0);
        times[next] = at;
        received = arrival;
        next = (next + 1) % CAPACITY;
        count = Math.min(CAPACITY, count + 1);
    }

    synchronized void sample(long at, MouthPose out) {
        out.rest();
        if (count == 0) return;
        int newer = (next + CAPACITY - 1) % CAPACITY;
        long age = at - times[newer];
        if (age >= 0) {
            age = Math.max(0, at - received);
            if (age >= 250_000_000L) return;
            out.set(poses[newer], poses[newer], 0);
            if (age > 90_000_000L) {
                float fade = (250_000_000L - age) / 160_000_000f;
                out.visible *= fade;
                out.jaw *= fade;
            }
            return;
        }
        for (int i = 1; i < count; i++) {
            int older = (newer + CAPACITY - 1) % CAPACITY;
            if (at >= times[older]) {
                float t = (float)(at - times[older]) / (times[newer] - times[older]);
                out.set(poses[older], poses[newer], t);
                return;
            }
            newer = older;
        }
    }
}
