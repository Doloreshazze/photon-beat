package com.playeverywhere.photonbeat;

import android.graphics.*;

/** Fixed-size touch buffers: no allocations on touch or frame hot paths. */
final class TouchLayer {
    private static final int N=144, R=10, T=96, F=10;
    private static final float TAU=(float)(Math.PI*2);
    private final float[] px=new float[N],py=new float[N],vx=new float[N],vy=new float[N],ph=new float[N],ps=new float[N];
    private final long[] born=new long[N];
    private int pn,rn,tn;
    private final float[] rx=new float[R],ry=new float[R],rh=new float[R],rs=new float[R];
    private final long[] rb=new long[R];
    private final float[] tx=new float[T],ty=new float[T],th=new float[T],tw=new float[T];
    private final long[] tb=new long[T];
    private final int[] strand=new int[T], ids=new int[F], serial=new int[F];
    private final float[] fx=new float[F],fy=new float[F],sx=new float[F],sy=new float[F],travel=new float[F];
    private final long[] downAt=new long[F], emitAt=new long[F];
    private int nextSerial=1;
    private float dxTotal,dyTotal,spinTotal;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape=new Path();
    private final float[] hsv={0,1,1};
    private final java.util.Random random=new java.util.Random(18);
    TouchLayer(){java.util.Arrays.fill(ids,-1);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);}
    private static float clamp(float a,float l,float h){return Math.max(l,Math.min(h,a));}
    private int color(float h,float s,float v,int a){hsv[0]=(h%360+360)%360;hsv[1]=s;hsv[2]=v;return Color.HSVToColor((int)clamp(a,0,255),hsv);}
    private int find(int id){for(int i=0;i<F;i++)if(ids[i]==id)return i;return -1;}
    void down(int id,float x,float y,float hue,long now){
        int slot=find(id);
        if(slot<0)for(int i=0;i<F;i++)if(ids[i]<0){slot=i;break;}
        if(slot<0)return;
        ids[slot]=id;serial[slot]=nextSerial++;fx[slot]=sx[slot]=x;fy[slot]=sy[slot]=y;
        travel[slot]=0;downAt[slot]=emitAt[slot]=now;
        trail(slot,x,y,hue,6,now);
        burst(x,y,hue,now,24,1);
        ring(x,y,hue,now,1);
    }
    void move(int id,float x,float y,float hue,long now){
        int s=find(id);if(s<0)return;
        float dx=x-fx[s],dy=y-fy[s],dist=(float)Math.hypot(dx,dy);
        if(dist<2)return;
        travel[s]+=dist;dxTotal+=dx;dyTotal+=dy;spinTotal+=dx-dy*.35f;
        if(now-emitAt[s]>=12||dist>24){
            int steps=Math.min(5,Math.max(1,(int)(dist/16)));
            for(int j=1;j<=steps;j++){float t=j/(float)steps;trail(s,fx[s]+dx*t,fy[s]+dy*t,hue+travel[s]*.15f,clamp(4+dist*.20f,5,19),now);}
            burst(x,y,hue+travel[s]*.15f,now,Math.min(5,steps+1),.5f);
            emitAt[s]=now;
        }
        fx[s]=x;fy[s]=y;
    }
    boolean up(int id,float x,float y,float hue,long now,boolean allowMenu){
        int s=find(id);if(s<0)return false;
        boolean longPress=allowMenu&&now-downAt[s]>=650&&travel[s]<24&&Math.hypot(x-sx[s],y-sy[s])<24;
        if(!longPress){
            if(travel[s]>22){ring(x,y,hue+90,now,.72f);burst(x,y,hue+90,now,12,.8f);}
            else if(now-downAt[s]>250){ring(x,y,hue,now,1.2f);}
        }
        ids[s]=-1;return longPress;
    }
    void cancel(){java.util.Arrays.fill(ids,-1);}
    float consumeDx(){float v=dxTotal;dxTotal=0;return v;}
    float consumeDy(){float v=dyTotal;dyTotal=0;return v;}
    float consumeSpin(){float v=spinTotal;spinTotal=0;return v;}
    private void trail(int slot,float x,float y,float h,float width,long now){
        int i=tn++%T;tx[i]=x;ty[i]=y;th[i]=h;tw[i]=width;tb[i]=now;strand[i]=serial[slot];
    }
    private void burst(float x,float y,float h,long now,int count,float power){
        for(int j=0;j<count;j++){
            int i=pn++%N;float a=random.nextFloat()*TAU,s=(75+random.nextFloat()*270)*power;
            px[i]=x;py[i]=y;vx[i]=(float)Math.cos(a)*s;vy[i]=(float)Math.sin(a)*s;ph[i]=h+random.nextFloat()*110;ps[i]=2+random.nextFloat()*5;born[i]=now;
        }
    }
    private void ring(float x,float y,float h,long now,float size){int i=rn++%R;rx[i]=x;ry[i]=y;rh[i]=h;rs[i]=size;rb[i]=now;}
    boolean isAlive(long now){
        for(int i=0;i<F;i++)if(ids[i]>=0)return true;
        for(long t:born)if(t!=0&&now-t<850)return true;
        for(long t:rb)if(t!=0&&now-t<1000)return true;
        for(long t:tb)if(t!=0&&now-t<700)return true;
        return false;
    }
    void draw(Canvas c,long now,int quality,float scale){
        int count=Math.min(tn,T),first=tn-count;
        p.setStyle(Paint.Style.STROKE);
        for(int j=1;j<count;j++){
            int a=(first+j-1)%T,b=(first+j)%T;
            float fade=1-(now-tb[a])/700f;
            if(fade<=0||strand[a]!=strand[b])continue;
            if(quality>0){p.setStrokeWidth(tw[b]*scale*2.7f*fade);p.setColor(color(th[b],1,1,(int)(48*fade)));c.drawLine(tx[a],ty[a],tx[b],ty[b],p);}
            p.setStrokeWidth(Math.max(1,tw[b]*scale*.75f*fade));p.setColor(color(th[b],.8f,1,(int)(220*fade)));c.drawLine(tx[a],ty[a],tx[b],ty[b],p);
        }
        for(int i=0;i<R;i++){
            if(rb[i]==0)continue;
            float age=(now-rb[i])/1000f;if(age>=1)continue;
            float radius=(12+age*240)*scale*rs[i],f=1-age;
            p.setStyle(Paint.Style.STROKE);
            p.setColor(color(rh[i],1,1,(int)(150*f)));p.setStrokeWidth((2+4*f)*scale);
            c.drawCircle(rx[i],ry[i],radius,p);
            p.setColor(color(rh[i]+120,.8f,1,(int)(115*f)));p.setStrokeWidth(1.5f*scale);
            c.drawCircle(rx[i],ry[i],radius*.70f,p);
            if(quality>0){
                shape.rewind();int petals=6;
                for(int j=0;j<=48;j++){
                    float a=j*TAU/48, d=radius*(.88f+.12f*(float)Math.sin(petals*a+age*6));
                    float x=rx[i]+d*(float)Math.cos(a),y=ry[i]+d*(float)Math.sin(a);
                    if(j==0)shape.moveTo(x,y);else shape.lineTo(x,y);
                }
                shape.close();c.drawPath(shape,p);
            }
        }
        for(int i=0;i<N;i++){
            if(born[i]==0)continue;
            float age=(now-born[i])/1000f,f=1-age/.85f;if(f<=0)continue;
            float drag=age*(1-.38f*age),x=px[i]+vx[i]*drag*scale,y=py[i]+vy[i]*drag*scale+age*age*35*scale;
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1,ps[i]*f*.55f*scale));
            p.setColor(color(ph[i],.8f,1,(int)(215*f)));
            c.drawLine(x-vx[i]*.055f*scale,y-vy[i]*.055f*scale,x,y,p);
            p.setStyle(Paint.Style.FILL);p.setColor(color(ph[i]+30,.4f,1,(int)(210*f)));
            c.drawCircle(x,y,Math.max(1,ps[i]*f*.4f*scale),p);
        }
        for(int i=0;i<F;i++)if(ids[i]>=0){
            float age=(now-downAt[i])/1000f,r=(11+3*(float)Math.sin(age*5))*scale;
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.7f*scale);p.setColor(color(35+i*60+age*45,.75f,1,185));c.drawCircle(fx[i],fy[i],r,p);
        }
    }
}
