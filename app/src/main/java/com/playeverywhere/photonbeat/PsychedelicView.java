package com.playeverywhere.photonbeat;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

/** Music-reactive vector renderer. Black background remains stable; motion carries the beat. */
public final class PsychedelicView extends View {
    private static final float PI=(float)Math.PI, TAU=PI*2;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private final float[] hsv={0,1,1};
    private final long[] ripple=new long[7];
    private final float[] rippleHue=new float[7];
    private int rippleIndex, mode, scene, beatCount;
    private float intensity=1, bass, mid, high, pulse, accent, spin, hue, phase;
    private float floorB=.004f,floorM=.003f,floorH=.002f, peakB=.035f,peakM=.025f,peakH=.015f;
    private float prevB, prevM, prevH, fluxAvg=.002f, tempo;
    private long lastBeat, lastAccent, lastFrame;
    private int validIntervals;
    private boolean active;
    public PsychedelicView(Context c){super(c);setClickable(true);setContentDescription("Динамический музыкальный визуализатор");}
    public void setMode(int m){mode=m;invalidate();}
    public void setIntensity(float v){intensity=v;}
    public void setActive(boolean on){
        if(on==active)return;
        active=on;
        if(!on){bass=mid=high=pulse=accent=0;lastBeat=lastAccent=0;tempo=0;validIntervals=beatCount=0;scene=0;prevB=prevM=prevH=0;fluxAvg=.002f;floorB=.004f;floorM=.003f;floorH=.002f;peakB=.035f;peakM=.025f;peakH=.015f;for(int i=0;i<ripple.length;i++)ripple[i]=0;}
        lastFrame=0;invalidate();
    }
    private static float clamp(float v,float lo,float hi){return Math.max(lo,Math.min(hi,v));}
    private static float mix(float a,float b,float k){return a+(b-a)*k;}
    private static float follow(float old,float target){return mix(old,target,target>old?.55f:.14f);}
    public boolean audio(float b,float m,float h,long now){
        if(!active)return false;
        float oldFloor=floorB;
        floorB=mix(floorB,b,.045f);floorM=mix(floorM,m,.035f);floorH=mix(floorH,h,.035f);
        peakB=Math.max(b,peakB*.987f);peakM=Math.max(m,peakM*.987f);peakH=Math.max(h,peakH*.987f);
        bass=follow(bass,clamp(b/Math.max(.009f,peakB)*intensity,0,1.5f));
        mid=follow(mid,clamp(m/Math.max(.007f,peakM)*intensity,0,1.5f));
        high=follow(high,clamp(h/Math.max(.005f,peakH)*intensity,0,1.5f));
        float flux=Math.max(0,b-prevB)+.28f*Math.max(0,m-prevM)+.12f*Math.max(0,h-prevH);
        float rise=b-prevB;
        boolean beat=now-lastBeat>=245 && b>Math.max(.007f,oldFloor*1.18f)
            && rise>Math.max(.0018f,oldFloor*.09f) && flux>Math.max(.0025f,fluxAvg*1.65f);
        boolean treble=now-lastAccent>=110 && h>Math.max(.003f,floorH*1.12f)&&h-prevH>Math.max(.0012f,floorH*.16f);
        if(treble){accent=1;lastAccent=now;}
        if(beat){
            long interval=now-lastBeat;
            if(lastBeat!=0&&interval>=275&&interval<=1200){
                float bpm=60000f/interval;
                while(bpm<75)bpm*=2;while(bpm>170)bpm/=2;
                tempo=tempo==0?bpm:mix(tempo,bpm,.22f);
                validIntervals++;
            }
            lastBeat=now; pulse=1; beatCount++;
            hue=(hue+24+mid*12)%360;
            ripple[rippleIndex]=now;rippleHue[rippleIndex]=hue;
            rippleIndex=(rippleIndex+1)%ripple.length;
            if(beatCount%16==0)scene=(scene+1)%4;
        }
        fluxAvg=mix(fluxAvg,flux,.055f);prevB=b;prevM=m;prevH=h;
        return beat;
    }
    public String getTempoText(){return validIntervals>=3?"~"+Math.round(tempo)+" BPM  •  ритм":"Анализ ритма";}
    private int color(float h,float s,float v,int alpha){
        hsv[0]=(h%360+360)%360;hsv[1]=clamp(s,0,1);hsv[2]=clamp(v,0,1);
        return Color.HSVToColor(alpha,hsv);
    }
    private void line(Canvas c,float w,float h,float v,int a){
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(w);paint.setColor(color(h,1,v,a));
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);c.drawColor(0xff020208);
        long now=SystemClock.elapsedRealtime();
        float dt=lastFrame==0?.016f:clamp((now-lastFrame)*.001f,0,.05f);lastFrame=now;
        if(active){
            pulse*=Math.exp(-dt*5.5);accent*=Math.exp(-dt*9);
            phase+=dt*(.7f+mid*1.5f);spin+=dt*(.16f+mid*.42f+pulse*.8f);
            hue=(hue+dt*(4+high*10))%360;
        }
        float cx=getWidth()*.5f,cy=getHeight()*.5f, r=(float)Math.hypot(cx,cy);
        if(r<1)return;
        c.save();c.translate(cx,cy);
        int current=mode==0?scene:mode-1;
        switch(current){case 1:tunnel(c,r);break;case 2:kaleido(c,r);break;case 3:plasma(c,r);break;default:mandala(c,r);}
        if(active)ripples(c,r,now);
        c.restore();
        if(active)postInvalidateOnAnimation();
    }
    private void mandala(Canvas c,float r){
        float u=r*(.74f+bass*.09f+pulse*.1f);
        for(int layer=0;layer<10;layer++){
            float rr=u*(.12f+layer*.088f);
            int lobes=layer%3==0?12:layer%3==1?8:16;
            float twist=spin*(layer%2==0?1:-1)+layer*.13f;
            path.reset();
            for(int i=0;i<=240;i++){
                float a=TAU*i/240f;
                float wave=(float)(.68+.19*Math.cos(lobes*a+phase+layer*.6)+.13*Math.sin((lobes/2)*a-phase*1.7));
                float radius=rr*wave*(1+.14f*pulse);
                float x=radius*(float)Math.cos(a+twist),y=radius*(float)Math.sin(a+twist);
                if(i==0)path.moveTo(x,y);else path.lineTo(x,y);
            }
            path.close();
            line(c,2.1f+2.6f*bass,hue+layer*36,.38f+.35f*mid,65);c.drawPath(path,paint);
            line(c,.95f+high*1.6f,hue+layer*36,1,235);c.drawPath(path,paint);
        }
        for(int i=0;i<48;i++){
            float a=i*TAU/48-spin;
            float d=u*(.40f+.30f*(float)Math.sin(a*6+phase));
            float x=d*(float)Math.cos(a),y=d*(float)Math.sin(a);
            paint.setStyle(Paint.Style.FILL);paint.setColor(color(hue+180+i*9,.9f,1,165));
            c.drawCircle(x,y,2+high*3+accent*2,paint);
        }
    }
    private void tunnel(Canvas c,float r){
        for(int j=19;j>=0;j--){
            float depth=((j+phase*.32f)%20)/20f;
            float radius=r*(.045f+1.25f*depth*depth)*(1+.12f*pulse);
            float twist=spin+depth*3.4f;
            int sides=8;
            path.reset();
            for(int i=0;i<=sides*5;i++){
                float a=TAU*i/(sides*5f)+twist;
                float k=radius*(1+.16f*(float)Math.sin(sides*a+phase*2+depth*7));
                float x=k*(float)Math.cos(a)+r*.06f*(float)Math.sin(phase+depth*6),
                      y=k*(float)Math.sin(a)+r*.06f*(float)Math.cos(phase+depth*6);
                if(i==0)path.moveTo(x,y);else path.lineTo(x,y);
            }
            path.close();
            line(c,1.3f+depth*3.0f+high,hue+depth*300,1,210);c.drawPath(path,paint);
        }
        for(int i=0;i<16;i++){
            path.reset();float a=TAU*i/16;
            for(int j=0;j<=80;j++){
                float d=j/80f, angle=a+spin+d*3.4f;
                float radius=r*(.045f+1.25f*d*d)*(1+.12f*pulse);
                float x=radius*(float)Math.cos(angle)+r*.06f*(float)Math.sin(phase+d*6);
                float y=radius*(float)Math.sin(angle)+r*.06f*(float)Math.cos(phase+d*6);
                if(j==0)path.moveTo(x,y);else path.lineTo(x,y);
            }
            line(c,.8f+accent,hue+180+i*13,.85f,155);c.drawPath(path,paint);
        }
    }
    private void kaleido(Canvas c,float r){
        float unit=r*.88f*(1+.10f*pulse);
        for(int sector=0;sector<16;sector++){
            c.save();c.rotate(sector*22.5f+spin*28);
            if(sector%2==1)c.scale(1,-1);
            for(int j=0;j<8;j++){
                float f=(j+1)/8f, d=unit*f;
                float wig=(float)Math.sin(phase*1.3+j*.7f)*.075f;
                path.reset();path.moveTo(d*.28f,0);path.lineTo(d*.74f,d*(.11f+wig));
                path.lineTo(d,d*.035f);path.lineTo(d*.74f,-d*(.11f+wig));path.close();
                paint.setStyle(Paint.Style.FILL);paint.setColor(color(hue+j*48+sector%2*180,1,.32f+.28f*mid,115));c.drawPath(path,paint);
                line(c,1+high*1.8f,hue+j*48+sector%2*180,1,230);c.drawPath(path,paint);
                path.reset();path.moveTo(d*.43f,0);path.quadTo(d*.8f,d*.19f,d,0);
                line(c,.8f+accent,hue+j*48+180,1,200);c.drawPath(path,paint);
            }
            c.restore();
        }
    }
    private void plasma(Canvas c,float r){
        for(int j=0;j<24;j++){
            float off=j*TAU/24;
            path.reset();
            for(int i=0;i<=190;i++){
                float t=i*TAU/190f;
                float a=t+spin*.7f;
                float d=r*(.40f+.13f*(float)Math.sin(5*t+off+phase)+.09f*(float)Math.cos(9*t-off-phase*1.6))*(1+.13f*bass+.09f*pulse);
                float x=d*(float)Math.cos(a)+r*.19f*(float)Math.cos(3*t+off);
                float y=d*(float)Math.sin(a)+r*.19f*(float)Math.sin(4*t-off+phase);
                if(i==0)path.moveTo(x,y);else path.lineTo(x,y);
            }
            path.close();
            line(c,1.0f+high*.9f,hue+j*15,1,160);c.drawPath(path,paint);
        }
    }
    private void ripples(Canvas c,float r,long now){
        for(int i=0;i<ripple.length;i++){
            if(ripple[i]==0)continue;
            float age=(now-ripple[i])*.001f;
            if(age>1.15f)continue;
            float f=age/1.15f,rad=r*(.08f+f*1.13f);
            int alpha=(int)(150*(1-f));
            line(c,1.5f+3*(1-f),rippleHue[i],1,alpha);c.drawCircle(0,0,rad,paint);
            line(c,.9f,rippleHue[i]+180,1,alpha/2);c.drawCircle(0,0,rad*.94f,paint);
        }
    }
}
