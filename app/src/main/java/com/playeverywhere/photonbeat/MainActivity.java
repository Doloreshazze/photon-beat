package com.playeverywhere.photonbeat;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.camera2.*;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {
    private final Handler h = new Handler(Looper.getMainLooper());
    private PsychedelicView visual;
    private LinearLayout controls;
    private TextView status;
    private Switch flash;
    private SeekBar gain;
    private CameraManager cam;
    private String camera;
    private boolean torch, mic, resumed;
    private long lastTorch, lastAudioSequence=-1, lastStatus;
    private boolean latestBeat;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        visual = new PsychedelicView(this);
        root.addView(visual, new LinearLayout.LayoutParams(-1,0,1f));
        controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(12),dp(4),dp(12),dp(8));
        controls.setBackgroundColor(0xff101018);
        root.addView(controls);
        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(14);
        controls.addView(status);
        LinearLayout row = new LinearLayout(this);
        controls.addView(row);
        button(row,"Медиапоток",()->start(false));
        button(row,"Микрофон",()->start(true));
        button(row,"Стоп",()->{stopService(new Intent(this,CaptureService.class));setTorch(false);});
        LinearLayout options = new LinearLayout(this);
        controls.addView(options);
        Spinner modes = new Spinner(this);
        String[] names={"Авто","Мандала","Туннель","Калейдоскоп","Плазма","Фрактал","Спирали","Сингулярность","Интерференция","Кристаллы","Ксеноморф","Поющие губы","Танцпол"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modes.setAdapter(adapter);
        modes.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> parent,View view,int pos,long id){
                visual.setMode(pos);
                if(view instanceof TextView)((TextView)view).setTextColor(Color.WHITE);
            }
            public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });
        options.addView(modes,new LinearLayout.LayoutParams(0,-2,1));
        button(options,"На весь экран",()->fullscreen(true));
        flash=new Switch(this);
        flash.setTextColor(Color.WHITE);
        flash.setText("Фонарик (не более 2 импульсов/с)");
        controls.addView(flash);
        flash.setOnCheckedChangeListener((v,on)->{
            if(!on)setTorch(false);
            else if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.CAMERA},2);
        });
        Switch dancers=new Switch(this);
        dancers.setTextColor(Color.WHITE);
        dancers.setText("Танцоры в такт");
        dancers.setChecked(true);
        dancers.setOnCheckedChangeListener((button,on)->visual.setDancersEnabled(on));
        LinearLayout characters=new LinearLayout(this);
        controls.addView(characters);
        characters.addView(dancers,new LinearLayout.LayoutParams(0,-2,1));
        Switch lips=new Switch(this);
        lips.setTextColor(Color.WHITE);
        lips.setText("Поющие губы");
        lips.setChecked(true);
        lips.setOnCheckedChangeListener((button,on)->visual.setLipsEnabled(on));
        characters.addView(lips,new LinearLayout.LayoutParams(0,-2,1));
        addLipDelayControl();
        TextView label=new TextView(this);
        label.setTextColor(Color.WHITE);
        label.setText("Чувствительность / интенсивность");
        controls.addView(label);
        gain=new SeekBar(this);
        gain.setMax(100); gain.setProgress(70);
        controls.addView(gain,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout performance=new LinearLayout(this);
        controls.addView(performance);
        Spinner quality=new Spinner(this);
        String[] levels={"FPS: Авто","FPS: Лёгкий","FPS: Баланс","FPS: Детальный"};
        ArrayAdapter<String> qa=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,levels);
        qa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);quality.setAdapter(qa);
        quality.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> parent,View view,int pos,long id){
                visual.setQuality(pos);
                if(view instanceof TextView)((TextView)view).setTextColor(Color.WHITE);
            }
            public void onNothingSelected(AdapterView<?> p){}
        });
        performance.addView(quality,new LinearLayout.LayoutParams(0,-2,1));
        SeekBar speed=new SeekBar(this);speed.setMax(100);speed.setProgress(55);
        speed.setContentDescription("Скорость анимации");
        speed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int progress,boolean user){visual.setSpeed(.65f+progress*.018f);}
            public void onStartTrackingTouch(SeekBar bar){}
            public void onStopTrackingTouch(SeekBar bar){}
        });
        performance.addView(speed,new LinearLayout.LayoutParams(0,-2,1));
        TextView hint=new TextView(this);
        hint.setTextColor(0xffaaaaaa); hint.setTextSize(11);
        hint.setText("Динамические контрастные узоры. При дискомфорте остановите просмотр. Тап — взрыв, свайп — неоновый след, удержание — меню.");
        controls.addView(hint);
        visual.setMenuAction(()->fullscreen(controls.getVisibility()!=View.GONE));
        setContentView(root);
        cam=getSystemService(CameraManager.class);
        try{for(String id:cam.getCameraIdList())if(Boolean.TRUE.equals(cam.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE))){camera=id;break;}}catch(Exception ignored){}
        h.post(tick);
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void addLipDelayControl() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = new TextView(this);
        label.setTextColor(Color.WHITE);
        label.setTextSize(12);
        row.addView(label, new LinearLayout.LayoutParams(dp(140), -2));
        SeekBar delay = new SeekBar(this);
        delay.setContentDescription("Задержка губ в миллисекундах");
        delay.setMax(40);
        int ms = getPreferences(MODE_PRIVATE).getInt("lipDelayMs", 0);
        delay.setProgress(ms / 10);
        label.setText("Задержка губ  " + ms + " мс");
        visual.setLipDelayMs(ms);
        delay.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int steps, boolean user) {
                int value = 10 * steps;
                visual.setLipDelayMs(value);
                label.setText("Задержка губ  " + value + " мс");
            }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {
                getPreferences(MODE_PRIVATE).edit().putInt("lipDelayMs", 10 * bar.getProgress()).apply();
            }
        });
        row.addView(delay, new LinearLayout.LayoutParams(0, -2, 1));
        controls.addView(row);
    }
    private void button(LinearLayout row,String text,Runnable action){
        Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(12);
        b.setOnClickListener(v->action.run());
        row.addView(b,new LinearLayout.LayoutParams(0,-2,1));
    }
    private void fullscreen(boolean yes){
        controls.setVisibility(yes?View.GONE:View.VISIBLE);
        getWindow().getDecorView().setSystemUiVisibility(yes?(
            View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY):0);
    }
    @Override public void onBackPressed(){if(controls.getVisibility()==View.GONE)fullscreen(false);else super.onBackPressed();}
    private void start(boolean m){
        mic=m;
        if(CaptureService.running){Toast.makeText(this,"Сначала нажмите Стоп",Toast.LENGTH_SHORT).show();return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},1);return;
        }
        if(m)startForegroundService(new Intent(this,CaptureService.class).putExtra("mic",true));
        else startActivityForResult(getSystemService(MediaProjectionManager.class).createScreenCaptureIntent(),3);
    }
    @Override public void onRequestPermissionsResult(int q,String[] p,int[] a){
        super.onRequestPermissionsResult(q,p,a);
        if(q==1&&a.length>0&&a[0]==0)start(mic);
        if(q==2&&(a.length==0||a[0]!=0))flash.setChecked(false);
    }
    @Override protected void onActivityResult(int q,int c,Intent d){
        super.onActivityResult(q,c,d);
        if(q==3&&c==RESULT_OK&&d!=null)startForegroundService(new Intent(this,CaptureService.class).putExtra("result",c).putExtra("data",d));
    }
    private void setTorch(boolean v){
        if(camera==null||torch==v)return;
        try{cam.setTorchMode(camera,v);torch=v;}catch(Exception e){if(v)flash.setChecked(false);}
    }
    private final Runnable tick=new Runnable(){public void run(){
        long now=SystemClock.elapsedRealtime();
        boolean active=CaptureService.running&&resumed;
        visual.setActive(active);
        visual.setIntensity(.45f+gain.getProgress()*.018f);
        boolean beat=false;
        CaptureService.Spectrum sample=CaptureService.spectrum;
        if(active&&sample.sequence!=lastAudioSequence){
            lastAudioSequence=sample.sequence;
            beat=visual.audio(sample,now);
        }
        if(!active)lastAudioSequence=-1;
        if(torch&&(!active||now-lastTorch>=90))setTorch(false);
        if(active&&beat&&flash.isChecked()&&hasWindowFocus()&&now-lastTorch>=550&&checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED){
            lastTorch=now;setTorch(true);
        }
        if(now-lastStatus>=500){
            lastStatus=now;
            status.setText(active?visual.getTempoText()+"  •  "+visual.fpsText():"PHOTON BEAT  •  Выберите источник звука");
        }
        h.postDelayed(this,16);
    }};
    @Override protected void onResume(){super.onResume();resumed=true;}
    @Override protected void onPause(){resumed=false;setTorch(false);super.onPause();}
    @Override protected void onDestroy(){h.removeCallbacks(tick);setTorch(false);stopService(new Intent(this,CaptureService.class));super.onDestroy();}
}
