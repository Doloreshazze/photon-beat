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
    void draw(Canvas c, int width, int height, float open, float round, float pulse,
              float hue, float alpha, boolean center) {
        float size = Math.min(width * (center ? .82f : .62f), height * (center ? 1.45f : .72f));
        float spread = 1 - .23f * round * open;
        float top = 1 + open * (14 + 8 * round), bottom = 1 + open * (31 + 13 * round);
        c.save(); c.translate(0, center ? -height * .04f : -height * .26f);
        c.scale(size / 200f, size / 200f);
        c.rotate((round - .5f) * 5);
        c.scale(spread * (1 + .035f * pulse), 1 + .025f * pulse);

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
        paint.setStrokeWidth(9 + pulse * 3); paint.setColor(color(hue + 205, 1, 1, alpha * .17f));
        c.drawPath(upper, paint); c.drawPath(lower, paint);
        fill(c, cavity, Color.argb(Math.round(255 * alpha), 9, 0, 15));
        c.save(); c.clipPath(cavity);
        if (open > .045f) {
            paint.setStyle(Paint.Style.FILL); paint.setColor(color(34, .08f, 1, alpha * .94f));
            c.drawRoundRect(-65, -top - 6, 65, -top + 7 + open * 7, 5, 5, paint);
            paint.setColor(color(332, .72f, .84f, alpha));
            c.drawOval(-34, bottom * .55f, 34, bottom * 1.25f + 6, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.1f);
            paint.setColor(color(340, .85f, .45f, alpha));
            c.drawLine(0, bottom * .67f, 0, bottom * .96f, paint);
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
