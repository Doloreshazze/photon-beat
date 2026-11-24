package com.playeverywhere.photonbeat;
import android.app.*;import android.content.*;import android.content.pm.ServiceInfo;import android.os.*;import android.media.*;import android.media.projection.*;
public class CaptureService extends Service {
 public static volatile float bass,mid,high;
 public static final class Spectrum {
    public final float bass,mid,high,bpm,mouthOpen,mouthRound;
    public final long sequence,capturedAt,lastBeat;
    public final int beatCount,validIntervals;
    Spectrum(float b,float m,float h,long s,long at,RhythmTracker rhythm){
        bass=b;mid=m;high=h;sequence=s;capturedAt=at;
        bpm=rhythm.bpm;mouthOpen=rhythm.mouthOpen;mouthRound=rhythm.mouthRound;
        lastBeat=rhythm.lastBeat;beatCount=rhythm.beatCount;validIntervals=rhythm.validIntervals;
    }
 }
 public static volatile Spectrum spectrum=new Spectrum(0,0,0,0,0,new RhythmTracker()); public static volatile boolean running; AudioRecord record; MediaProjection projection; Thread worker;
 public IBinder onBind(Intent i){return null;}
 public int onStartCommand(Intent i,int f,int s){if(i==null||running)return START_NOT_STICKY;NotificationManager notifications=getSystemService(NotificationManager.class);notifications.createNotificationChannel(new NotificationChannel("capture","Audio capture",2));Notification note=new Notification.Builder(this,"capture").setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("Photon Beat").build();boolean mic=i.getBooleanExtra("mic",false);
 try{startForeground(1,note,mic?(Build.VERSION.SDK_INT>=30?ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE:0):ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
 AudioFormat format=new AudioFormat.Builder().setSampleRate(44100).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build();AudioRecord.Builder b=new AudioRecord.Builder().setAudioFormat(format).setBufferSizeInBytes(Math.max(4096,AudioRecord.getMinBufferSize(44100,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)));
 if(mic)b.setAudioSource(MediaRecorder.AudioSource.MIC);else{projection=getSystemService(MediaProjectionManager.class).getMediaProjection(i.getIntExtra("result",0),i.getParcelableExtra("data"));projection.registerCallback(new MediaProjection.Callback(){public void onStop(){stopSelf();}},new Handler(getMainLooper()));b.setAudioPlaybackCaptureConfig(new AudioPlaybackCaptureConfiguration.Builder(projection).addMatchingUsage(AudioAttributes.USAGE_MEDIA).addMatchingUsage(AudioAttributes.USAGE_GAME).build());}
 record=b.build();record.startRecording();running=true;worker=new Thread(()->{
    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
    final int n=1024, hop=256;
    final RhythmTracker rhythm=new RhythmTracker();
    short[] pcm=new short[n], block=new short[hop];
    double[] re=new double[n], im=new double[n], window=new double[n], cs=new double[n/2], sn=new double[n/2];
    int[] rev=new int[n];
    for(int j=0;j<n;j++){window[j]=.5-.5*Math.cos(2*Math.PI*j/(n-1));rev[j]=Integer.reverse(j)>>>22;}
    for(int j=0;j<n/2;j++){cs[j]=Math.cos(-2*Math.PI*j/n);sn[j]=Math.sin(-2*Math.PI*j/n);}
    int filled=0;long seq=spectrum.sequence;
    while(running){
        int k=record.read(block,0,hop);
        if(k<0){stopSelf();break;}if(k==0)continue;
        System.arraycopy(pcm,k,pcm,0,n-k);System.arraycopy(block,0,pcm,n-k,k);
        filled=Math.min(n,filled+k);if(filled<n)continue;
        for(int j=0;j<n;j++){re[rev[j]]=pcm[j]*window[j]/32768.0;im[j]=0;}
        for(int len=2;len<=n;len*=2){
            int step=n/len;
            for(int j=0;j<n;j+=len)for(int q=0;q<len/2;q++){
                int u=j+q,v=u+len/2,idx=q*step;
                double x=re[v]*cs[idx]-im[v]*sn[idx],y=re[v]*sn[idx]+im[v]*cs[idx];
                re[v]=re[u]-x;im[v]=im[u]-y;re[u]+=x;im[u]+=y;
            }
        }
        double lo=0,mi=0,hi=0;
        for(int j=1;j<n/2;j++){
            double power=(re[j]*re[j]+im[j]*im[j])/(n*(double)n),hz=j*44100.0/n;
            if(hz<250)lo+=power;else if(hz<2500)mi+=power;else hi+=power;
        }
        bass=(float)Math.sqrt(lo);mid=(float)Math.sqrt(mi);high=(float)Math.sqrt(hi);
        long now=SystemClock.elapsedRealtime();
        rhythm.update(bass,mid,high,now);
        spectrum=new Spectrum(bass,mid,high,++seq,now,rhythm);
    }
 });worker.start();}catch(Exception e){stopSelf();}return START_NOT_STICKY;}
 public void onDestroy(){running=false;if(record!=null){try{record.stop();}catch(Exception e){}try{if(worker!=null)worker.join(300);}catch(Exception e){}record.release();}if(projection!=null)projection.stop();bass=mid=high=0;spectrum=new Spectrum(0,0,0,spectrum.sequence+1,SystemClock.elapsedRealtime(),new RhythmTracker());super.onDestroy();}
}
