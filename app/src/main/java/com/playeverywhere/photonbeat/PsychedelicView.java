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
    private static final int SCENES=10;
    private int rippleIndex, mode, scene, beatCount, previousScene=-1, shownScene=-1;
    private long sceneStart;
    private float opacity=1, targetHue, glowScale=1;
    private final float[] stars=new float[120*4];
    private final float[] trigX=new float[192],trigY=new float[192];
    private float intensity=1, bass, mid, high, pulse, accent, spin, hue, phase;
    private float floorB=.004f,floorM=.003f,floorH=.002f, peakB=.035f,peakM=.025f,peakH=.015f;
    private float prevB, prevM, prevH, fluxAvg=.002f, tempo;
    private long lastBeat, lastAccent, lastFrame;
    private int validIntervals;
    private boolean active;
    public PsychedelicView(Context c){super(c);setClickable(true);setContentDescription("Динамический музыкальный визуализатор");
        java.util.Random rng=new java.util.Random(8831);
        for(int i=0;i<stars.length;i++)stars[i]=rng.nextFloat();
        for(int i=0;i<192;i++){float a=TAU*i/192;trigX[i]=(float)Math.cos(a);trigY[i]=(float)Math.sin(a);}
    }
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
            targetHue=(targetHue+32+mid*14)%360;
            ripple[rippleIndex]=now;rippleHue[rippleIndex]=targetHue;
            rippleIndex=(rippleIndex+1)%ripple.length;
            if(beatCount%8==0)scene=(scene+1)%SCENES;
        }
        fluxAvg=mix(fluxAvg,flux,.055f);prevB=b;prevM=m;prevH=h;
        return beat;
    }
    public String getTempoText(){return validIntervals>=3?"~"+Math.round(tempo)+" BPM  •  ритм":"Анализ ритма";}
    private int color(float h,float s,float v,int alpha){
        hsv[0]=(h%360+360)%360;hsv[1]=clamp(s,0,1);hsv[2]=clamp(v,0,1);
        return Color.HSVToColor((int)(clamp(alpha*opacity,0,255)),hsv);
    }
    private void line(Canvas c,float w,float h,float v,int a){
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(w);paint.setColor(color(h,1,v,a));
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);c.drawColor(0xff020208);
        long now=SystemClock.elapsedRealtime();
        float dt=lastFrame==0?.016f:clamp((now-lastFrame)*.001f,0,.05f);lastFrame=now;
        if(active){
            pulse*=Math.exp(-dt*4.6);accent*=Math.exp(-dt*8);
            phase+=dt*(.65f+mid*1.9f+bass*.65f);
            spin+=dt*(.16f+mid*.65f+pulse*1.15f);
            targetHue=(targetHue+dt*(5+high*13))%360;
            float delta=(targetHue-hue+540)%360-180;
            hue=(hue+delta*Math.min(1,dt*7)+360)%360;
        }
        float cx=getWidth()*.5f,cy=getHeight()*.5f, r=(float)Math.hypot(cx,cy);
        if(r<1)return;
        c.save();c.translate(cx,cy);
        int current=mode==0?scene:mode-1;
        if(current!=shownScene){previousScene=shownScene;shownScene=current;sceneStart=now;}
        glowScale=Math.max(1,Math.min(2.5f,r/480f));
        opacity=1;
        backdrop(c,r);
        c.save();
        float zoom=1+.065f*bass+.08f*pulse;
        c.scale(zoom,zoom);
        float fade=active&&previousScene>=0?clamp((now-sceneStart)/700f,0,1):1;
        fade=fade*fade*(3-2*fade);
        if(fade<1){opacity=1-fade;renderScene(c,r,previousScene);}
        opacity=fade;renderScene(c,r,current);opacity=1;
        c.restore();
        if(active){starField(c,r);ripples(c,r,now);}
        c.restore();
        if(active)postInvalidateOnAnimation();
    }
    private void renderScene(Canvas c,float r,int s){
        switch(s){
            case 1:tunnel(c,r);break;case 2:kaleido(c,r);break;case 3:plasma(c,r);break;
            case 4:fractal(c,r);break;case 5:helix(c,r);break;case 6:singularity(c,r);break;
            case 7:moire(c,r);break;case 8:crystals(c,r);break;case 9:alien(c,r);break;
            default:mandala(c,r);
        }
    }
    private void neon(Canvas c,float h,float w,int a){
        line(c,w*glowScale*5,h,.52f,a/8);c.drawPath(path,paint);
        line(c,w*glowScale*2.5f,h,.9f,a/3);c.drawPath(path,paint);
        line(c,w*glowScale,h,1,a);c.drawPath(path,paint);
    }
    private void backdrop(Canvas c,float r){
        paint.setStyle(Paint.Style.FILL);
        for(int i=9;i>=0;i--){
            paint.setColor(color(hue+240+i*8,.9f,.12f,9+(int)(mid*5)));
            c.drawCircle(0,0,r*(.18f+i*.095f),paint);
        }
        for(int i=0;i<48;i++){
            float a=i*TAU/48+spin*.15f;
            float d=r*(.23f+.08f*(float)Math.sin(i*.9+phase));
            line(c,.7f*glowScale,hue+i*7,.5f,23);
            c.drawLine(d*(float)Math.cos(a),d*(float)Math.sin(a),r*(float)Math.cos(a),r*(float)Math.sin(a),paint);
        }
    }
    private void starField(Canvas c,float r){
        for(int i=0;i<stars.length;i+=4){
            float a=stars[i]*TAU+spin*(.2f+stars[i+1]*.5f);
            float f=(stars[i+1]+phase*(.055f+stars[i+2]*.07f))%1;
            float rad=r*(.04f+1.12f*f*f);
            float x=rad*(float)Math.cos(a),y=rad*(float)Math.sin(a);
            float trail=(.014f+.055f*pulse+.025f*high)*f;
            float tail=Math.max(0,rad-r*trail);
            line(c,(.6f+stars[i+3]*1.5f)*glowScale,hue+stars[i+2]*280,1,(int)(130*f));
            c.drawLine(tail*(float)Math.cos(a-.045f),tail*(float)Math.sin(a-.045f),x,y,paint);
            paint.setStyle(Paint.Style.FILL);paint.setColor(color(hue+stars[i+2]*280,.6f,1,(int)(170*f)));
            c.drawCircle(x,y,glowScale*(.7f+stars[i+3]*1.6f+accent),paint);
        }
    }
    private void fractal(Canvas c,float r){
        for(int k=0;k<12;k++){
            float a=k*TAU/12+spin*.45f;
            c.save();c.rotate(a*180/PI);
            branch(c,r*.67f,0,0,0,4,k*30+hue);
            c.restore();
        }
    }
    private void branch(Canvas c,float len,float x,float y,float a,int depth,float h){
        float ex=x+len*(float)Math.cos(a),ey=y+len*(float)Math.sin(a);
        path.reset();path.moveTo(x,y);
        path.quadTo(x+len*.55f*(float)Math.cos(a+.12f*(float)Math.sin(phase)),y+len*.55f*(float)Math.sin(a+.12f*(float)Math.sin(phase)),ex,ey);
        neon(c,h, .55f+depth*.28f+high*.3f,195);
        if(depth==0)return;
        float spread=.38f+.19f*(float)Math.sin(phase+depth*.7f)+.13f*bass;
        branch(c,len*.52f,ex,ey,a+spread,depth-1,h+38);
        branch(c,len*.52f,ex,ey,a-spread,depth-1,h-38);
    }
    private void helix(Canvas c,float r){
        for(int k=0;k<14;k++){
            float off=k*TAU/14;
            path.reset();
            for(int j=0;j<=180;j++){
                float f=j/180f,a=off+f*TAU*2.4f-spin*1.2f;
                float d=r*(.045f+f*.94f)*(1+.10f*(float)Math.sin(f*17-phase));
                float x=d*(float)Math.cos(a),y=d*(float)Math.sin(a);
                if(j==0)path.moveTo(x,y);else path.lineTo(x,y);
            }
            neon(c,hue+k*26,1.1f+high*.65f,210);
            for(int j=1;j<=12;j++){
                float f=j/13f,a=off+f*TAU*2.4f-spin*1.2f;
                float d=r*(.045f+f*.94f)*(1+.10f*(float)Math.sin(f*17-phase));
                paint.setStyle(Paint.Style.FILL);paint.setColor(color(hue+k*26+160,.8f,1,180));
                c.drawCircle(d*(float)Math.cos(a),d*(float)Math.sin(a),glowScale*(1.2f+high*1.8f),paint);
            }
        }
    }
    private void singularity(Canvas c,float r){
        for(int k=0;k<24;k++){
            float f=(k+phase*.7f)%24/24f;
            float rad=r*(.055f+f*f*1.18f);
            path.reset();
            for(int i=0;i<=144;i++){
                float a=TAU*i/144;
                float d=rad*(1+.16f*(float)Math.sin(a*5+phase+f*12));
                float angle=a+spin+f*4;
                float x=d*(float)Math.cos(angle),y=d*(float)Math.sin(angle)*(.56f+.35f*f);
                if(i==0)path.moveTo(x,y);else path.lineTo(x,y);
            }
            path.close();neon(c,hue+f*280, .7f+f*2+high*.3f,(int)(130+f*90));
        }
        for(int k=0;k<8;k++){
            c.save();c.rotate(k*45+spin*15);
            path.reset();path.moveTo(r*.10f,0);
            path.cubicTo(r*.35f,-r*.27f,r*.75f,r*.28f,r*1.15f,0);
            neon(c,hue+k*45+90,1.3f,180);c.restore();
        }
    }
    private void moire(Canvas c,float r){
        for(int k=0;k<3;k++){
            c.save();c.rotate(k*60+spin*(k%2==0?13:-10));
            for(int j=-14;j<=14;j++){
                float y=j*r/15f;
                path.reset();
                for(int i=0;i<=80;i++){
                    float x=r*(i/40f-1);
                    float yy=y+r*.10f*(float)Math.sin(x/r*8+phase+j*.27f)+r*.035f*(float)Math.sin(x/r*17-phase*1.3);
                    if(i==0)path.moveTo(x,yy);else path.lineTo(x,yy);
                }
                neon(c,hue+k*120+j*4,.6f+high*.4f,115);
            }
            c.restore();
        }
    }
    private void crystals(Canvas c,float r){
        for(int k=0;k<9;k++){
            float f=(k+1)/9f,rad=r*f*.92f;
            int sides=6+k%3*2;
            float a0=spin*(k%2==0?1:-1)+k*.31f;
            path.reset();
            for(int i=0;i<=sides;i++){
                float a=a0+i*TAU/sides;
                float x=rad*(float)Math.cos(a),y=rad*(float)Math.sin(a);
                if(i==0)path.moveTo(x,y);else path.lineTo(x,y);
            }
            path.close();neon(c,hue+k*38,1.2f+high*.7f,230);
            for(int i=0;i<sides;i++){
                float a=a0+i*TAU/sides,b=a+TAU/sides;
                path.reset();path.moveTo(rad*(float)Math.cos(a),rad*(float)Math.sin(a));
                path.lineTo(rad*.40f*(float)Math.cos(a+bass*.3f),rad*.40f*(float)Math.sin(a+bass*.3f));
                path.lineTo(rad*(float)Math.cos(b),rad*(float)Math.sin(b));path.close();
                paint.setStyle(Paint.Style.FILL);paint.setColor(color(hue+k*38+i*24,1,.7f,35+(int)(mid*22)));c.drawPath(path,paint);
                line(c,.8f*glowScale,hue+k*38+160,1,140);c.drawPath(path,paint);
            }
        }
    }
    private void alien(Canvas c,float r){
        for(int k=0;k<18;k++){
            float f=(k+1)/18f;
            path.reset();
            for(int i=0;i<=192;i++){
                int idx=i%192;
                float a=TAU*i/192f;
                float wave=.67f+.19f*(float)Math.sin(a*7+phase+f*7)+.12f*(float)Math.cos(a*13-phase*.8f-f*9);
                float d=r*f*wave*(1+.13f*bass);
                float aa=a+spin*(1-f)+.32f*(float)Math.sin(a*3+f*7+phase);
                float x=d*(float)Math.cos(aa)+r*.05f*trigX[idx],y=d*(float)Math.sin(aa)+r*.05f*trigY[idx];
                if(i==0)path.moveTo(x,y);else path.lineTo(x,y);
            }
            path.close();neon(c,hue+f*330, .75f+high*.5f,200);
        }
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
            neon(c,hue+layer*36,.95f+high*.9f,235);
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
            neon(c,hue+depth*300,.85f+depth*1.8f+high*.4f,210);
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
                neon(c,hue+j*48+sector%2*180,.8f+high*.8f,220);
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
            neon(c,hue+j*15,.7f+high*.6f,160);
        }
    }
    private void ripples(Canvas c,float r,long now){
        for(int i=0;i<ripple.length;i++){
            if(ripple[i]==0)continue;
            float age=(now-ripple[i])*.001f;
            if(age>1.15f)continue;
            float f=age/1.15f,rad=r*(.08f+f*1.13f);
            int alpha=(int)(150*(1-f));
            line(c,(4+7*(1-f))*glowScale,rippleHue[i],.65f,alpha/5);c.drawCircle(0,0,rad,paint);
            line(c,(1.5f+2*(1-f))*glowScale,rippleHue[i],1,alpha);c.drawCircle(0,0,rad,paint);
            path.reset();
            for(int j=0;j<=96;j++){
                float a=TAU*j/96, d=rad*(1+.045f*(float)Math.sin(a*12+f*8));
                float x=d*(float)Math.cos(a),y=d*(float)Math.sin(a);
                if(j==0)path.moveTo(x,y);else path.lineTo(x,y);
            }
            path.close();neon(c,rippleHue[i]+55,1,alpha/2);
            line(c,.9f,rippleHue[i]+180,1,alpha/2);c.drawCircle(0,0,rad*.94f,paint);
        }
    }
}
