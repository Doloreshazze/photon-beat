package com.playeverywhere.photonbeat;

import android.graphics.*;

/** Articulated, anonymous dance silhouettes; synchronized to detected musical beats. */
final class DanceLayer {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path body=new Path();
    private final float[] hsv={0,1,1};
    private static final float PI=(float)Math.PI;
    private static float sin(float t){return (float)Math.sin(t);}
    private static float cos(float t){return (float)Math.cos(t);}
    private int tint(float hue,float saturation,float value,int alpha){
        hsv[0]=(hue%360+360)%360;hsv[1]=saturation;hsv[2]=value;return Color.HSVToColor(alpha,hsv);
    }
    void draw(Canvas c,int width,int height,int beatCount,float progress,float pulse,float mid,float hue,float alpha,int variant){
        float scale=Math.min(width/345f,height/260f);
        float y=height*.25f;
        float beat=((beatCount%16)+progress)*PI;
        if(variant==0){figure(c,0,y,scale*1.24f,beat,progress,pulse,mid,hue,alpha,0);}
        else if(variant==1){
            figure(c,-width*.23f,y,scale*.98f,beat,progress,pulse,mid,hue,alpha,1);
            figure(c,width*.23f,y,scale*.98f,beat,progress,pulse,mid,hue+160,alpha,2);
        }else{
            figure(c,-width*.30f,y,scale*.83f,beat,progress,pulse,mid,hue+90,alpha*.83f,2);
            figure(c,width*.30f,y,scale*.83f,beat,progress,pulse,mid,hue+205,alpha*.83f,1);
            figure(c,0,y,scale*1.13f,beat,progress,pulse,mid,hue,alpha,0);
        }
    }
    private void figure(Canvas c,float x,float y,float scale,float t,float progress,float pulse,float mid,float hue,float alpha,int style){
        c.save();c.translate(x,y);c.scale(scale,scale);
        // Feet plant and knees compress exactly on the onset; lift happens between kicks.
        float groove=cos(t), cross=cos(t*.5f), lift=Math.max(0,sin(progress*PI));
        float hipX=9*groove, hipY=-50-3*lift+11*pulse;
        float shoulderX=hipX+10*cross-3*groove*pulse, shoulderY=hipY-34-3*pulse;
        float neckX=shoulderX+2*groove,neckY=shoulderY-7;
        float headX=neckX+3*cross,headY=neckY-13;
        float spread=style==1?24:20;
        float lFootX=-spread-8*groove, rFootX=spread-8*groove;
        float lFootY=-3-15*lift*Math.max(0,groove), rFootY=-3-15*lift*Math.max(0,-groove);
        float lKneeX=(hipX-7+lFootX)*.5f-5-5*groove;
        float rKneeX=(hipX+7+rFootX)*.5f+5-5*groove;
        float lKneeY=(hipY+lFootY)*.5f-3,rKneeY=(hipY+rFootY)*.5f-3;
        float armL=(style==0?(-.9f+1.0f*cross+.45f*groove):style==1?(-.35f+.8f*groove):(-1.3f+.65f*cross))-.22f*pulse;
        float armR=(style==0?(.9f+1.0f*cross-.45f*groove):style==1?(1.5f+.65f*cross):(.35f+.85f*groove))+.22f*pulse;
        float lsx=shoulderX-15,rsx=shoulderX+15;
        float lex=lsx+21*sin(armL),ley=shoulderY+21*cos(armL);
        float rex=rsx+21*sin(armR),rey=shoulderY+21*cos(armR);
        float lAngle=armL-1.05f-.50f*cross-.20f*mid;
        float rAngle=armR+1.05f-.50f*cross+.20f*mid;
        float lhx=lex+21*sin(lAngle),lhy=ley+21*cos(lAngle);
        float rhx=rex+21*sin(rAngle),rhy=rey+21*cos(rAngle);
        body.reset();
        body.moveTo(lsx,shoulderY-2);body.quadTo(shoulderX-6,shoulderY-7,neckX-5,neckY);
        body.lineTo(neckX+5,neckY);body.quadTo(shoulderX+7,shoulderY-7,rsx,shoulderY-2);
        body.quadTo(shoulderX+9,shoulderY+17,hipX+10,hipY+2);
        body.quadTo(hipX,hipY+10,hipX-10,hipY+2);
        body.quadTo(shoulderX-9,shoulderY+17,lsx,shoulderY-2);body.close();
        // Two colored echoes, then bright contour and black interior.
        for(int layer=0;layer<4;layer++){
            float offset=layer==0?-4:layer==1?4:0;
            c.save();c.translate(offset,0);
            int a=(int)(alpha*(layer<2?54:layer==2?222:250));
            int col=layer==3?Color.argb(a,3,3,10):tint(hue+(layer==0?55:layer==1?190:0),layer==2?.65f:1,1,a);
            float extra=layer<2?7+pulse*3:layer==2?3.5f:0;
            limb(c,hipX-7,hipY,lKneeX,lKneeY,lFootX,lFootY,10+extra,7+extra,col);
            limb(c,hipX+7,hipY,rKneeX,rKneeY,rFootX,rFootY,10+extra,7+extra,col);
            limb(c,lsx,shoulderY,lex,ley,lhx,lhy,8+extra,5.8f+extra,col);
            limb(c,rsx,shoulderY,rex,rey,rhx,rhy,8+extra,5.8f+extra,col);
            p.setColor(col);p.setStyle(Paint.Style.FILL);c.drawPath(body,p);
            if(extra>0){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(extra);p.setStrokeJoin(Paint.Join.ROUND);c.drawPath(body,p);}
            p.setStyle(Paint.Style.FILL);c.drawOval(headX-8.8f-extra*.5f,headY-11-extra*.5f,headX+8.8f+extra*.5f,headY+10+extra*.5f,p);
            p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(6+extra);p.setStyle(Paint.Style.STROKE);
            c.drawLine(lFootX,lFootY,lFootX-7,lFootY+1,p);
            c.drawLine(rFootX,rFootY,rFootX+7,rFootY+1,p);
            c.restore();
        }
        c.restore();
    }
    private void limb(Canvas c,float x,float y,float jx,float jy,float ex,float ey,float w,float w2,int col){
        p.setColor(col);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(w);c.drawLine(x,y,jx,jy,p);
        p.setStrokeWidth(w2);c.drawLine(jx,jy,ex,ey,p);
    }
}
