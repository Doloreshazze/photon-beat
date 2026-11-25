package com.playeverywhere.photonbeat;

import android.graphics.*;

/** Reusable vector paths: no bitmaps, shaders or per-frame allocations. */
final class LipLayer {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path upper = new Path(), lower = new Path(), cavity = new Path(), shine = new Path();
    private final float[] hsv = {0, 1, 1};

    private int color(float hue, float saturation, float value, float alpha) {
        hsv[0] = (hue % 360 + 360) % 360; hsv[1] = saturation; hsv[2] = value;
        return Color.HSVToColor(Math.round(255 * alpha), hsv);
    }
    private void fill(Canvas c, Path path, int color) {
        paint.setColor(color); paint.setStyle(Paint.Style.FILL); c.drawPath(path, paint);
    }
    void draw(Canvas c, int width, int height, MouthPose pose, float hue, float alpha, boolean center) {
        float open = pose.jaw, round = pose.round;
        float size = Math.min(width * (center ? .82f : .62f), height * (center ? 1.45f : .72f));
        float spread = .87f + .22f * pose.wide - .42f * round;
        float top = .12f + open * (14 + 11 * round) + 2.7f * pose.bite;
        float bottom = Math.max(.12f, .12f + open * (36 + 15 * round) - 2.4f * pose.bite);
        c.save(); c.translate(0, center ? -height * .04f : -height * .26f);
        c.scale(size / 200f, size / 200f);
        c.scale(spread, 1 + .10f * round);

        cavity.reset(); cavity.moveTo(-98, 0);
        cavity.cubicTo(-54, -top * .75f, -26, -top, 0, -top * .82f);
        cavity.cubicTo(26, -top, 54, -top * .75f, 98, 0);
        cavity.cubicTo(55, bottom * 1.15f, -55, bottom * 1.15f, -98, 0); cavity.close();
        upper.reset(); upper.moveTo(-100, 0);
        upper.cubicTo(-71, -10, -39, -39 - open * 5, -18, -28 - top * .3f);
        upper.cubicTo(-8, -21 - top * .3f, -6, -20 - top * .3f, 0, -23 - top * .3f);
        upper.cubicTo(6, -20 - top * .3f, 8, -21 - top * .3f, 18, -28 - top * .3f);
        upper.cubicTo(39, -39 - open * 5, 71, -10, 100, 0);
        upper.cubicTo(54, -top * .75f, 26, -top, 0, -top * .82f);
        upper.cubicTo(-26, -top, -54, -top * .75f, -100, 0); upper.close();
        lower.reset(); lower.moveTo(-100, 0);
        lower.cubicTo(-55, bottom * 1.15f, 55, bottom * 1.15f, 100, 0);
        lower.cubicTo(68, 24 + bottom * .85f, 39, 38 + bottom * .85f, 0, 38 + bottom * .85f);
        lower.cubicTo(-39, 38 + bottom * .85f, -68, 24 + bottom * .85f, -100, 0); lower.close();

        paint.setStyle(Paint.Style.STROKE); paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(9); paint.setColor(color(hue + 205, 1, 1, alpha * .17f));
        c.drawPath(upper, paint); c.drawPath(lower, paint);
        fill(c, cavity, Color.argb(Math.round(255 * alpha), 9, 0, 15));
        c.save(); c.clipPath(cavity);
        if (open > .035f || pose.bite > .2f) {
            paint.setStyle(Paint.Style.FILL); paint.setColor(color(34, .08f, 1, alpha * .94f));
            float toothWidth = 37 + 31 * pose.wide + 16 * pose.teeth;
            float toothEdge = -top + 1 + 10 * pose.teeth + 3 * pose.bite;
            c.drawRoundRect(-toothWidth, -top - 6, toothWidth, toothEdge, 4, 4, paint);
            paint.setColor(color(332, .72f, .84f, alpha));
            float tip = bottom * .74f - pose.tongue * (bottom + top * .52f);
            float tongueWidth = 32 - 10 * pose.tongue;
            c.drawOval(-tongueWidth, tip, tongueWidth, bottom * 1.5f + 7, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.1f);
            paint.setColor(color(340, .85f, .45f, alpha));
            c.drawLine(0, tip + 2, 0, bottom * .96f, paint);
        }
        c.restore();
        fill(c, upper, color(337 + round * 15, .92f, .82f, alpha));
        fill(c, lower, color(347 + round * 10, .85f, 1, alpha));
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.3f);
        paint.setColor(color(hue + 175, .5f, 1, alpha * .85f));
        c.drawPath(upper, paint); c.drawPath(lower, paint);
        shine.reset(); shine.moveTo(-48, 18 + bottom * .98f);
        shine.cubicTo(-23, 31 + bottom * .92f, 18, 32 + bottom * .92f, 41, 22 + bottom * .96f);
        paint.setStrokeWidth(3.8f); paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(color(338, .14f, 1, alpha * .77f)); c.drawPath(shine, paint);
        shine.reset(); shine.moveTo(-33, -24 - top * .3f);
        shine.quadTo(-25, -28 - top * .3f, -18, -24 - top * .3f);
        paint.setStrokeWidth(2.4f); paint.setColor(color(330, .15f, 1, alpha * .65f)); c.drawPath(shine, paint);
        c.restore();
    }
}
