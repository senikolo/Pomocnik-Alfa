package com.ispina.lokalnie.radio;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.Icon;
import android.media.*;
import android.os.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class RadioService extends Service {
    public static final String ACTION_PLAY="com.ispina.lokalnie.radio.PLAY";
    public static final String ACTION_TOGGLE="com.ispina.lokalnie.radio.TOGGLE";
    public static final String ACTION_STOP="com.ispina.lokalnie.radio.STOP";
    private static final String CHANNEL="ispina_lokalnie_radio";
    private static final int NOTIF_ID=18295;

    private MediaPlayer player;
    private AudioManager audioManager;
    private String lastName="Radio", lastUrl="", lastProgram="", lastArtist="", lastTrack="";
    private boolean preparing=false;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AtomicBoolean infoRunning=new AtomicBoolean(false);

    private final Runnable infoLoop=new Runnable(){
        @Override public void run(){
            lookupInfo();
            main.postDelayed(this,60000L);
        }
    };

    private final AudioManager.OnAudioFocusChangeListener focusListener=change -> {
        if(player==null)return;
        if(change==AudioManager.AUDIOFOCUS_LOSS || change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) pausePlayback();
    };

    @Override public void onCreate(){
        super.onCreate();
        audioManager=(AudioManager)getSystemService(AUDIO_SERVICE);
        createChannel();
        SharedPreferences p=getSharedPreferences("il_radio_state",MODE_PRIVATE);
        lastName=p.getString("name","Radio");lastUrl=p.getString("url","");
        lastProgram=p.getString("program","");lastArtist=p.getString("artist","");lastTrack=p.getString("track","");
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null)return lastUrl.isEmpty()?START_NOT_STICKY:START_STICKY;
        String action=intent.getAction();
        if(ACTION_PLAY.equals(action)){
            String name=intent.getStringExtra("name"),url=intent.getStringExtra("url");
            if(name!=null&&!name.trim().isEmpty())lastName=name.trim();
            if(url!=null&&!url.trim().isEmpty()){
                lastUrl=url.trim();lastProgram="";lastArtist="";lastTrack="";
                saveInfo();
                startForeground(NOTIF_ID,buildNotification("Łączenie…"));
                play(lastUrl);
                startInfoLoop();
            }
        } else if(ACTION_TOGGLE.equals(action)) toggle();
        else if(ACTION_STOP.equals(action)){stopRadio();return START_NOT_STICKY;}
        return lastUrl.isEmpty()?START_NOT_STICKY:START_STICKY;
    }

    private void play(String url){
        releasePlayer();requestFocus();preparing=true;updateNotification(null);
        try{
            player=new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            player.setWakeMode(getApplicationContext(),PowerManager.PARTIAL_WAKE_LOCK);
            player.setOnPreparedListener(mp->{preparing=false;try{mp.start();}catch(Throwable ignored){}updateNotification(null);lookupInfo();});
            player.setOnErrorListener((mp,what,extra)->{preparing=false;updateNotification("Błąd połączenia");return true;});
            player.setOnCompletionListener(mp->updateNotification("Strumień zakończony"));
            player.setDataSource(url);player.prepareAsync();
        }catch(Throwable e){preparing=false;updateNotification("Nie udało się połączyć");}
    }

    private void toggle(){
        if(player!=null){
            try{
                if(player.isPlaying())pausePlayback();
                else if(!preparing){requestFocus();player.start();updateNotification(null);startInfoLoop();lookupInfo();}
                return;
            }catch(Throwable ignored){}
        }
        if(!lastUrl.isEmpty()){startForeground(NOTIF_ID,buildNotification("Łączenie…"));play(lastUrl);startInfoLoop();}
    }

    private void pausePlayback(){
        preparing=false;
        if(player!=null){try{if(player.isPlaying())player.pause();}catch(Throwable ignored){}}
        updateNotification(null);
    }

    private void stopRadio(){
        preparing=false;main.removeCallbacks(infoLoop);releasePlayer();abandonFocus();
        if(Build.VERSION.SDK_INT>=24)stopForeground(STOP_FOREGROUND_REMOVE);else stopForeground(true);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancel(NOTIF_ID);stopSelf();
    }

    private void startInfoLoop(){main.removeCallbacks(infoLoop);main.post(infoLoop);}

    private void lookupInfo(){
        if(lastUrl==null||lastUrl.isEmpty()||!infoRunning.compareAndSet(false,true))return;
        final String station=lastName,url=lastUrl;
        new Thread(()->{
            RadioNowPlaying.Info i=null;
            try{i=RadioNowPlaying.lookup(station,url);}catch(Throwable ignored){}
            final RadioNowPlaying.Info out=i;
            main.post(()->{
                try{
                    if(out!=null && station.equals(lastName) && url.equals(lastUrl)){
                        if(out.program!=null&&!out.program.trim().isEmpty())lastProgram=out.program.trim();
                        if(out.artist!=null)lastArtist=out.artist.trim();
                        if(out.track!=null&&!out.track.trim().isEmpty())lastTrack=out.track.trim();
                        saveInfo();updateNotification(null);
                    }
                }finally{infoRunning.set(false);}
            });
        },"IL-RadioInfo").start();
    }

    private void saveInfo(){getSharedPreferences("il_radio_state",MODE_PRIVATE).edit().putString("name",lastName).putString("url",lastUrl).putString("program",lastProgram).putString("artist",lastArtist).putString("track",lastTrack).apply();}
    private void releasePlayer(){if(player!=null){try{player.stop();}catch(Throwable ignored){}try{player.reset();}catch(Throwable ignored){}try{player.release();}catch(Throwable ignored){}player=null;}}
    private void requestFocus(){try{audioManager.requestAudioFocus(focusListener,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN);}catch(Throwable ignored){}}
    private void abandonFocus(){try{audioManager.abandonAudioFocus(focusListener);}catch(Throwable ignored){}}

    private void createChannel(){
        if(Build.VERSION.SDK_INT<26)return;
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel ch=new NotificationChannel(CHANNEL,"Ispina Lokalnie – Radio",NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Radio w tle, nazwa audycji i sterowanie");ch.setSound(null,null);ch.enableVibration(false);ch.setShowBadge(false);nm.createNotificationChannel(ch);
    }

    private PendingIntent serviceCommand(String action,int code){Intent i=new Intent(this,RadioService.class).setAction(action);return PendingIntent.getService(this,code,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    private PendingIntent openApp(){Intent i=new Intent();i.setClassName(this,"com.ispina.lokalnie.HomeActivity");i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);return PendingIntent.getActivity(this,18290,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}

    private Icon makeRadioIcon(){
        try{
            Bitmap b=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.WHITE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(7f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            c.drawLine(26,30,72,10,p);c.drawRoundRect(10,30,86,82,10,10,p);c.drawCircle(34,56,12,p);c.drawLine(56,47,76,47,p);c.drawLine(56,59,76,59,p);c.drawLine(56,70,70,70,p);
            return Icon.createWithBitmap(b);
        }catch(Throwable e){return Icon.createWithResource(this,android.R.drawable.ic_media_play);}
    }

    private Notification buildNotification(String forced){
        boolean playing=false;try{playing=player!=null&&player.isPlaying();}catch(Throwable ignored){}
        String state=forced!=null?forced:(preparing?"Łączenie…":(playing?"Odtwarzanie na żywo":"Pauza"));
        String song=lastTrack.isEmpty()?"":(lastArtist.isEmpty()?lastTrack:lastArtist+" – "+lastTrack);
        String content=!lastProgram.isEmpty()?"Audycja: "+lastProgram:(!song.isEmpty()?"Teraz gra: "+song:state);
        StringBuilder big=new StringBuilder();
        if(!lastProgram.isEmpty())big.append("🎙 Audycja: ").append(lastProgram);
        if(!song.isEmpty()){if(big.length()>0)big.append('
');big.append("♫ Teraz gra: ").append(song);}
        if(big.length()>0)big.append('
').append(state);

        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        b.setSmallIcon(makeRadioIcon()).setContentTitle(lastName==null||lastName.isEmpty()?"Radio":lastName).setContentText(content).setSubText("Ispina Lokalnie • Radio").setContentIntent(openApp()).setCategory(Notification.CATEGORY_TRANSPORT).setOnlyAlertOnce(true).setShowWhen(false).setOngoing(playing||preparing).setVisibility(Notification.VISIBILITY_PUBLIC)
         .addAction(new Notification.Action.Builder(playing?android.R.drawable.ic_media_pause:android.R.drawable.ic_media_play,playing?"Pauza":"Wznów",serviceCommand(ACTION_TOGGLE,18291)).build())
         .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"Stop",serviceCommand(ACTION_STOP,18292)).build());
        if(big.length()>0)b.setStyle(new Notification.BigTextStyle().bigText(big.toString()));
        else b.setStyle(new Notification.MediaStyle().setShowActionsInCompactView(0,1));
        return b.build();
    }

    private void updateNotification(String forced){if(lastUrl==null||lastUrl.isEmpty())return;((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIF_ID,buildNotification(forced));}
    @Override public void onDestroy(){main.removeCallbacks(infoLoop);releasePlayer();abandonFocus();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
