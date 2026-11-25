package com.playeverywhere.photonbeat;

/** Independent controls for the vector rig, in the range 0..1. */
final class MouthPose {
    float visible, jaw, round, wide, bite, tongue, teeth;

    void set(MouthPose a, MouthPose b, float t) {
        visible = blend(a.visible, b.visible, t);
        jaw = blend(a.jaw, b.jaw, t);
        round = blend(a.round, b.round, t);
        wide = blend(a.wide, b.wide, t);
        bite = blend(a.bite, b.bite, t);
        tongue = blend(a.tongue, b.tongue, t);
        teeth = blend(a.teeth, b.teeth, t);
    }

    private static float blend(float a, float b, float t) { return a + t * (b - a); }

    void rest() { visible = jaw = round = wide = bite = tongue = teeth = 0; }
}
