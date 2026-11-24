package com.playeverywhere.photonbeat;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import android.view.MotionEvent;

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
    private float peakB=.035f,peakM=.025f,peakH=.015f;
    private float prevH, tempo, mouthOpen, mouthRound=.5f;
    private long lastBeat=-1, lastAccent, lastFrame, lastAudioAt;
    private int validIntervals;
    private boolean active;
    private final TouchLayer touch=new TouchLayer();
    private float touchTwist,touchShiftX,touchShiftY,touchPulse;
    private Runnable menuAction;
    private boolean multiGesture;
    public void setMenuAction(Runnable action){menuAction=action;}
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public boolean onTouchEvent(MotionEvent e){
        final int action=e.getActionMasked(), idx=e.getActionIndex();
        final long now=SystemClock.elapsedRealtime();
        if(action==MotionEvent.ACTION_DOWN){
            multiGesture=false;touch.down(e.getPointerId(0),e.getX(),e.getY(),hue,now);
            touchPulse=1;targetHue=(targetHue+38)%360;
            getParent().requestDisallowInterceptTouchEvent(true);
        }else if(action==MotionEvent.ACTION_POINTER_DOWN){
            multiGesture=true;
            touch.down(e.getPointerId(idx),e.getX(idx),e.getY(idx),hue+80,now);
        }else if(action==MotionEvent.ACTION_MOVE){
            for(int i=0;i<e.getPointerCount();i++){
                int id=e.getPointerId(i);
                float x=e.getX(i),y=e.getY(i);
                touch.move(id,x,y,hue+id*45,now);
            }
            touchTwist=clamp(touchTwist+touch.consumeSpin()/Math.max(100,getWidth()),-1.3f,1.3f);
            touchShiftX=clamp(touchShiftX+touch.consumeDx()*.26f,-getWidth()*.16f,getWidth()*.16f);
            touchShiftY=clamp(touchShiftY+touch.consumeDy()*.26f,-getHeight()*.13f,getHeight()*.13f);
        }else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP){
            boolean menu=touch.up(e.getPointerId(idx),e.getX(idx),e.getY(idx),hue,now,!multiGesture);
            if(menu&&menuAction!=null){performClick();menuAction.run();}
            if(action==MotionEvent.ACTION_UP)getParent().requestDisallowInterceptTouchEvent(false);
        }else if(action==MotionEvent.ACTION_CANCEL){
            touch.cancel();getParent().requestDisallowInterceptTouchEvent(false);
        }
        postInvalidateOnAnimation();return true;
    }
    private float frameMs=16.7f, speed=1.65f;
    private int quality=1, qualityFrames;
    private boolean automaticQuality=true;
    private final Path[] branches={new Path(),new Path(),new Path(),new Path(),new Path()};
    private static final int LUT_SIZE=4096;
    private static final float[] SIN=new float[LUT_SIZE];
    static {for(int i=0;i<LUT_SIZE;i++)SIN[i]=(float)Math.sin(i*TAU/LUT_SIZE);}
    private static float sin(double a){return SIN[(int)Math.round(a*(LUT_SIZE/TAU))&(LUT_SIZE-1)];}
    private static float cos(double a){return sin(a+PI*.5);}
    public void setSpeed(float value){speed=value;}
    public void setQuality(int value){automaticQuality=value==0;quality=value==0?1:value-1;qualityFrames=0;invalidate();}
    public String fpsText(){return Math.round(1000/Math.max(1,frameMs))+" FPS · "+(quality==0?"Лёгкий":quality==1?"Баланс":"Детальный");}
    private boolean dancersEnabled=true, lipsEnabled=true;
    private long danceStart, lipStart;
    private int danceVariant;
    private final DanceLayer danceLayer=new DanceLayer();
    private final LipLayer lipLayer=new LipLayer();
    public void setDancersEnabled(boolean value){dancersEnabled=value;invalidate();}
    public void setLipsEnabled(boolean value){lipsEnabled=value;invalidate();}
    public PsychedelicView(Context c){super(c);setClickable(true);setContentDescription("Динамический музыкальный визуализатор");
        java.util.Random rng=new java.util.Random(8831);
        for(int i=0;i<stars.length;i++)stars[i]=rng.nextFloat();
        for(int i=0;i<192;i++){float a=TAU*i/192;trigX[i]=(float)cos(a);trigY[i]=(float)sin(a);}
    }
    public void setMode(int m){mode=m;invalidate();}
    public void setIntensity(float v){intensity=v;}
    public void setActive(boolean on){
        if(on==active)return;
        active=on;
        if(!on){frameMs=16.7f;qualityFrames=0;danceStart=lipStart=0;danceVariant=0;bass=mid=high=pulse=accent=mouthOpen=0;mouthRound=.5f;lastBeat=-1;lastAudioAt=lastAccent=0;tempo=0;validIntervals=beatCount=0;scene=0;prevH=0;peakB=.035f;peakM=.025f;peakH=.015f;for(int i=0;i<ripple.length;i++)ripple[i]=0;}
        lastFrame=0;invalidate();
    }
    private static float clamp(float v,float lo,float hi){return Math.max(lo,Math.min(hi,v));}
    private static float mix(float a,float b,float k){return a+(b-a)*k;}
    private static float follow(float old,float target){return mix(old,target,target>old?.82f:.27f);}
    public boolean audio(CaptureService.Spectrum sample,long now){
        if(!active)return false;
        float b=sample.bass,m=sample.mid,h=sample.high;
        lastAudioAt=sample.capturedAt;
        mouthOpen=sample.mouthOpen;mouthRound=sample.mouthRound;
        peakB=Math.max(b,peakB*.987f);peakM=Math.max(m,peakM*.987f);peakH=Math.max(h,peakH*.987f);
        bass=follow(bass,clamp(b/Math.max(.009f,peakB)*intensity,0,1.5f));
        mid=follow(mid,clamp(m/Math.max(.007f,peakM)*intensity,0,1.5f));
        high=follow(high,clamp(h/Math.max(.005f,peakH)*intensity,0,1.5f));
        boolean beat=sample.beatCount!=beatCount && sample.lastBeat>=0 && now-sample.lastBeat<200;
        beatCount=sample.beatCount;lastBeat=sample.lastBeat;tempo=sample.bpm;validIntervals=sample.validIntervals;
        boolean treble=now-lastAccent>=110 && h>.003f&&h-prevH>.0012f;
        if(treble){accent=1;lastAccent=now;}
        if(beat){
            pulse=(float)Math.exp(-Math.max(0,now-lastBeat)*.007f);
            targetHue=(targetHue+32+mid*14)%360;
            ripple[rippleIndex]=lastBeat;rippleHue[rippleIndex]=targetHue;
            rippleIndex=(rippleIndex+1)%ripple.length;
            if(beatCount%8==0)scene=(scene+1)%SCENES;
            if(beatCount%24==1){danceStart=lastBeat;danceVariant=(danceVariant+1)%3;}
            if(beatCount%24==13)lipStart=lastBeat;
        }
        prevH=h;
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
        float raw=lastFrame==0?16.7f:Math.max(1,now-lastFrame);
        lastFrame=now;
        float dt=clamp(raw*.001f,0,.16f);
        boolean interactive=touch.isAlive(now)||Math.abs(touchTwist)+Math.abs(touchShiftX)+Math.abs(touchShiftY)+touchPulse>.025f;
        touchPulse*=Math.exp(-dt*5.5);
        touchTwist*=Math.exp(-dt*2.3);
        touchShiftX*=Math.exp(-dt*3.5);
        touchShiftY*=Math.exp(-dt*3.5);
        if(active||interactive||mode==3){
            frameMs=mix(frameMs,Math.min(raw,180),.08f);
            if(automaticQuality&&++qualityFrames>=45){
                if(frameMs>24&&quality>0)quality--;
                else if(frameMs<15.5f&&quality<2)quality++;
                qualityFrames=0;
            }
        }
        if(active){
            pulse*=Math.exp(-dt*7);accent*=Math.exp(-dt*12);
            phase+=dt*speed*(.9f+mid*2.5f+bass*1.15f+pulse*1.1f);
            spin+=dt*speed*(.25f+mid*.9f+pulse*1.65f);
            targetHue=(targetHue+dt*(5+high*13))%360;
            float delta=(targetHue-hue+540)%360-180;
            hue=(hue+delta*Math.min(1,dt*7)+360)%360;
        }
        if(!active&&(interactive||mode==3)){
            phase+=dt*speed*.75f;
            spin+=dt*speed*.22f;
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
        if(current!=2)backdrop(c,r);
        c.save();
        float zoom=1+.075f*bass+.17f*pulse+.095f*touchPulse;
        c.translate(touchShiftX,touchShiftY);
        c.rotate(touchTwist*32);
        c.scale(zoom,zoom);
        // Single-scene transition: avoids doubling GPU/CPU load at a beat.
        float fade=active&&previousScene>=0?clamp((now-sceneStart)/220f,0,1):1;
        float reveal=.72f+.28f*fade;
        c.scale(reveal,reveal);
        renderScene(c,r,current);
        c.restore();
        if(active){if(current!=2)starField(c,r);ripples(c,r,now);
            float period=tempo>0?60000f/tempo:500f;
            float freshness=clamp(1-Math.max(0,now-lastAudioAt-120)/250f,0,1);
            float music=clamp((bass+mid+high-.025f)*3,0,1)*freshness;
            if(dancersEnabled&&(danceStart!=0||current==11)){
                float duration=period*8;
                float age=now-danceStart;
                float strength=current==11?1:clamp(Math.min(age/150f,(duration-age)/350f),0,1);
                strength*=music;
                if(strength>0){
                    float progress=lastBeat<0?0:clamp((now-lastBeat)/period,0,1);
                    float hit=lastBeat<0?0:(float)Math.exp(-Math.max(0,now-lastBeat)/95f);
                    // All bodies share the actual audio onset, including the paired figures.
                    danceLayer.draw(c,getWidth(),getHeight(),beatCount,progress,hit,mid,hue,strength,current==11?2:danceVariant);
                }
            }
            if(lipsEnabled&&(lipStart!=0||current==10)){
                float age=now-lipStart,duration=period*8;
                float strength=current==10?1:clamp(Math.min(age/200f,(duration-age)/400f),0,1)*music;
                if(strength>0)lipLayer.draw(c,getWidth(),getHeight(),mouthOpen*freshness,mouthRound,pulse,hue,strength,current==10);
            }
        }
        if(!active&&current==10&&lipsEnabled)lipLayer.draw(c,getWidth(),getHeight(),0,.5f,0,hue,1,true);
        c.restore();
        touch.draw(c,now,quality,glowScale);
        if(active||interactive||mode==3)postInvalidateOnAnimation();
    }
    private void renderScene(Canvas c,float r,int s){
        switch(s){
            case 1:tunnel(c,r);break;case 2:kaleido(c,r);break;case 3:plasma(c,r);break;
            case 4:fractal(c,r);break;case 5:helix(c,r);break;case 6:singularity(c,r);break;
            case 7:moire(c,r);break;case 8:crystals(c,r);break;case 9:alien(c,r);break;
            case 10:tunnel(c,r);break;case 11:moire(c,r);break;
            default:mandala(c,r);
        }
    }
    private void neon(Canvas c,float h,float w,int a){
        if(quality==2){line(c,w*glowScale*5,h,.52f,a/8);c.drawPath(path,paint);}
        if(quality>=1){line(c,w*glowScale*2.6f,h,.9f,a/3);c.drawPath(path,paint);}
        line(c,w*glowScale,h,1,a);c.drawPath(path,paint);
    }
    private void backdrop(Canvas c,float r){
        paint.setStyle(Paint.Style.FILL);
        for(int i=5;i>=0;i--){
            paint.setColor(color(hue+240+i*8,.9f,.12f,9+(int)(mid*5)));
            c.drawCircle(0,0,r*(.18f+i*.17f),paint);
        }
        for(int i=0;i<48;i++){
            float a=i*TAU/48+spin*.15f;
            float d=r*(.23f+.08f*(float)sin(i*.9+phase));
            line(c,.7f*glowScale,hue+i*7,.5f,23);
            c.drawLine(d*(float)cos(a),d*(float)sin(a),r*(float)cos(a),r*(float)sin(a),paint);
        }
    }
    private void starField(Canvas c,float r){
        for(int i=0;i<stars.length;i+=(quality==0?12:quality==1?8:4)){
            float a=stars[i]*TAU+spin*(.2f+stars[i+1]*.5f);
            float f=(stars[i+1]+phase*(.055f+stars[i+2]*.07f))%1;
            float rad=r*(.04f+1.12f*f*f);
            float x=rad*(float)cos(a),y=rad*(float)sin(a);
            float trail=(.014f+.055f*pulse+.025f*high)*f;
            float tail=Math.max(0,rad-r*trail);
            line(c,(.6f+stars[i+3]*1.5f)*glowScale,hue+stars[i+2]*280,1,(int)(130*f));
            c.drawLine(tail*(float)cos(a-.045f),tail*(float)sin(a-.045f),x,y,paint);
            paint.setStyle(Paint.Style.FILL);paint.setColor(color(hue+stars[i+2]*280,.6f,1,(int)(170*f)));
            c.drawCircle(x,y,glowScale*(.7f+stars[i+3]*1.6f+accent),paint);
        }
    }
    private void fractal(Canvas c,float r){
        int count=quality==0?8:12;
        int depth=quality==2?4:3;
        for(Path b:branches)b.rewind();
        for(int k=0;k<count;k++)branch(c,r*.48f,0,0,k*TAU/count+spin*.45f,depth,k*30+hue);
        for(int d=0;d<=depth;d++){
            Path b=branches[d];
            if(quality>0){line(c,3.4f*glowScale,hue+d*55,.75f,52);c.drawPath(b,paint);}
            line(c,(.7f+d*.27f+high*.35f)*glowScale,hue+d*55,1,220);c.drawPath(b,paint);
        }
    }
    private void branch(Canvas c,float len,float x,float y,float a,int depth,float h){
        float ex=x+len*(float)cos(a),ey=y+len*(float)sin(a);
        Path b=branches[depth];b.moveTo(x,y);
        b.quadTo(x+len*.55f*(float)cos(a+.12f*(float)sin(phase)),y+len*.55f*(float)sin(a+.12f*(float)sin(phase)),ex,ey);
        if(depth==0)return;
        float spread=.38f+.19f*(float)sin(phase+depth*.7f)+.13f*bass;
        branch(c,len*.52f,ex,ey,a+spread,depth-1,h+38);
        branch(c,len*.52f,ex,ey,a-spread,depth-1,h-38);
    }
    private void helix(Canvas c,float r){
        for(int k=0;k<14;k++){
            float off=k*TAU/14;
            path.reset();
            for(int j=0;j<=180;j+=quality==2?1:2){
                float f=j/180f,a=off+f*TAU*2.4f-spin*1.2f;
                float d=r*(.045f+f*.94f)*(1+.10f*(float)sin(f*17-phase));
                float x=d*(float)cos(a),y=d*(float)sin(a);
                if(j==0)path.moveTo(x,y);else path.lineTo(x,y);
            }
            neon(c,hue+k*26,1.1f+high*.65f,210);
            for(int j=1;j<=12;j++){
                float f=j/13f,a=off+f*TAU*2.4f-spin*1.2f;
                float d=r*(.045f+f*.94f)*(1+.10f*(float)sin(f*17-phase));
                paint.setStyle(Paint.Style.FILL);paint.setColor(color(hue+k*26+160,.8f,1,180));
                c.drawCircle(d*(float)cos(a),d*(float)sin(a),glowScale*(1.2f+high*1.8f),paint);
            }
        }
    }
    private void singularity(Canvas c,float r){
        for(int k=0;k<24;k++){
            float f=(k+phase*.7f)%24/24f;
            float rad=r*(.055f+f*f*1.18f);
            path.reset();
            for(int i=0;i<=144;i+=quality==2?1:2){
                float a=TAU*i/144;
                float d=rad*(1+.16f*(float)sin(a*5+phase+f*12));
                float angle=a+spin+f*4;
                float x=d*(float)cos(angle),y=d*(float)sin(angle)*(.56f+.35f*f);
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
            for(int j=-(quality==0?7:quality==1?10:14);j<=(quality==0?7:quality==1?10:14);j++){
                float y=j*r/15f;
                path.reset();
                for(int i=0;i<=80;i+=quality==2?1:2){
                    float x=r*(i/40f-1);
                    float yy=y+r*.10f*(float)sin(x/r*8+phase+j*.27f)+r*.035f*(float)sin(x/r*17-phase*1.3);
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
                float x=rad*(float)cos(a),y=rad*(float)sin(a);
                if(i==0)path.moveTo(x,y);else path.lineTo(x,y);
            }
            path.close();neon(c,hue+k*38,1.2f+high*.7f,230);
            for(int i=0;i<sides;i++){
                float a=a0+i*TAU/sides,b=a+TAU/sides;
                path.reset();path.moveTo(rad*(float)cos(a),rad*(float)sin(a));
                path.lineTo(rad*.40f*(float)cos(a+bass*.3f),rad*.40f*(float)sin(a+bass*.3f));
                path.lineTo(rad*(float)cos(b),rad*(float)sin(b));path.close();
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
                float wave=.67f+.19f*(float)sin(a*7+phase+f*7)+.12f*(float)cos(a*13-phase*.8f-f*9);
                float d=r*f*wave*(1+.13f*bass);
                float aa=a+spin*(1-f)+.32f*(float)sin(a*3+f*7+phase);
                float x=d*(float)cos(aa)+r*.05f*trigX[idx],y=d*(float)sin(aa)+r*.05f*trigY[idx];
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
            for(int i=0;i<=240;i+=quality==2?1:2){
                float a=TAU*i/240f;
                float wave=(float)(.68+.19*cos(lobes*a+phase+layer*.6)+.13*sin((lobes/2)*a-phase*1.7));
                float radius=rr*wave*(1+.14f*pulse);
                float x=radius*(float)cos(a+twist),y=radius*(float)sin(a+twist);
                if(i==0)path.moveTo(x,y);else path.lineTo(x,y);
            }
            path.close();
            line(c,2.1f+2.6f*bass,hue+layer*36,.38f+.35f*mid,65);c.drawPath(path,paint);
            neon(c,hue+layer*36,.95f+high*.9f,235);
        }
        for(int i=0;i<48;i++){
            float a=i*TAU/48-spin;
            float d=u*(.40f+.30f*(float)sin(a*6+phase));
            float x=d*(float)cos(a),y=d*(float)sin(a);
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
                float k=radius*(1+.16f*(float)sin(sides*a+phase*2+depth*7));
                float x=k*(float)cos(a)+r*.06f*(float)sin(phase+depth*6),
                      y=k*(float)sin(a)+r*.06f*(float)cos(phase+depth*6);
                if(i==0)path.moveTo(x,y);else path.lineTo(x,y);
            }
            path.close();
            neon(c,hue+depth*300,.85f+depth*1.8f+high*.4f,210);
        }
        for(int i=0;i<16;i++){
            path.reset();float a=TAU*i/16;
            for(int j=0;j<=80;j+=quality==2?1:2){
                float d=j/80f, angle=a+spin+d*3.4f;
                float radius=r*(.045f+1.25f*d*d)*(1+.12f*pulse);
                float x=radius*(float)cos(angle)+r*.06f*(float)sin(phase+d*6);
                float y=radius*(float)sin(angle)+r*.06f*(float)cos(phase+d*6);
                if(j==0)path.moveTo(x,y);else path.lineTo(x,y);
            }
            line(c,.8f+accent,hue+180+i*13,.85f,155);c.drawPath(path,paint);
        }
    }
    // Construct one wedge and reflect it into a complete optical kaleidoscope.
    // Batch each layer into one Path to keep Canvas draw calls low.
    private final Path mirrorBatch=new Path();
    private final Matrix mirrorMatrix=new Matrix();
    private void mirrorWedge(int pairs){
        mirrorBatch.rewind();
        for(int i=0;i<pairs;i++){
            float angle=360f*i/pairs;
            mirrorMatrix.setRotate(angle);
            mirrorBatch.addPath(path,mirrorMatrix);
            mirrorMatrix.setScale(1,-1);
            mirrorMatrix.postRotate(angle);
            mirrorBatch.addPath(path,mirrorMatrix);
        }
    }
    private void kaleido(Canvas c,float r){
        int pairs=6+2*((beatCount/16)%4); // 12, 16, 20 or 24 mirrored wedges
        int layers=quality==0?8:quality==1?12:16;
        float edge=PI/pairs;
        float scale=r*1.28f*(1+.08f*bass);
        c.save();
        c.rotate(spin*20f);
        for(int j=layers-1;j>=0;j--){
            float f=(j+1f)/layers;
            float d=scale*f;
            float wave=.5f+.5f*sin(phase*1.55f-f*9);
            float fold=.24f+.56f*wave;
            float a=edge*fold;
            float inner=Math.max(0,d-scale*(.15f+.06f*mid));
            float outer=d*(1+.08f*sin(phase-f*13));
            float peak=d*(.88f+.10f*pulse);
            float h=hue+f*290+40*sin(phase*.45f-f*6);
            // Colored glass facets; the neighboring wedge is an exact reflection.
            path.rewind();
            path.moveTo(inner,0);
            path.lineTo(peak*cos(a),peak*sin(a));
            path.lineTo(outer*cos(edge),outer*sin(edge));
            path.lineTo(d*cos(edge*.67f),d*sin(edge*.67f));
            path.lineTo(d,0);
            path.close();
            mirrorWedge(pairs);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color(h,.88f,.36f+.20f*mid,155));
            c.drawPath(mirrorBatch,paint);
            line(c,.8f+high*.45f,h,1,220);
            c.drawPath(mirrorBatch,paint);
            // Curved filigree bends within the wedge without breaking symmetry.
            path.rewind();
            float v=edge*(.12f+.70f*wave);
            path.moveTo(inner,0);
            path.cubicTo(d*.73f*cos(v),d*.73f*sin(v),
                d*.96f*cos(edge*.90f),d*.96f*sin(edge*.90f),
                outer*cos(edge),outer*sin(edge));
            path.moveTo(d*.78f*cos(edge),d*.78f*sin(edge));
            path.quadTo(d*.85f*cos(v),d*.85f*sin(v),d,0);
            mirrorWedge(pairs);
            if(quality>0){
                line(c,3.4f*glowScale,h+105,.75f,55);
                c.drawPath(mirrorBatch,paint);
            }
            line(c,(.65f+high*.6f)*glowScale,h+105,1,210);
            c.drawPath(mirrorBatch,paint);
            // Small mirrored jewels drift along the facet.
            float jewel=d*(.85f+.11f*sin(phase*2-f*18));
            float ja=edge*(.17f+.65f*wave);
            path.rewind();
            path.addCircle(jewel*cos(ja),jewel*sin(ja),
                glowScale*(1.3f+2.2f*high+1.5f*touchPulse),Path.Direction.CW);
            mirrorWedge(pairs);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color(h+170,.55f,1,205));
            c.drawPath(mirrorBatch,paint);
        }
        // Central jewel: same axes as the surrounding glass.
        path.rewind();
        float core=r*(.028f+.026f*bass+.026f*touchPulse);
        path.moveTo(0,0);
        path.lineTo(core*1.8f,0);
        path.lineTo(core*cos(edge),core*sin(edge));
        path.close();
        mirrorWedge(pairs);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color(hue+60,.62f,.92f,225));
        c.drawPath(mirrorBatch,paint);
        c.restore();
    }
    private void plasma(Canvas c,float r){
        for(int j=0;j<(quality==0?12:quality==1?18:24);j++){
            float off=j*TAU/(quality==0?12:quality==1?18:24);
            path.reset();
            for(int i=0;i<=190;i+=quality==2?1:2){
                float t=i*TAU/190f;
                float a=t+spin*.7f;
                float d=r*(.40f+.13f*(float)sin(5*t+off+phase)+.09f*(float)cos(9*t-off-phase*1.6))*(1+.13f*bass+.09f*pulse);
                float x=d*(float)cos(a)+r*.19f*(float)cos(3*t+off);
                float y=d*(float)sin(a)+r*.19f*(float)sin(4*t-off+phase);
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
            for(int j=0;j<=96;j+=quality==2?1:2){
                float a=TAU*j/96, d=rad*(1+.045f*(float)sin(a*12+f*8));
                float x=d*(float)cos(a),y=d*(float)sin(a);
                if(j==0)path.moveTo(x,y);else path.lineTo(x,y);
            }
            path.close();neon(c,rippleHue[i]+55,1,alpha/2);
            line(c,.9f,rippleHue[i]+180,1,alpha/2);c.drawCircle(0,0,rad*.94f,paint);
        }
    }
}
