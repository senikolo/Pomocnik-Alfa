package com.ispina.lokalnie;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** Large controls for 10-inch Android 6 tablet; sound generation happens in a Service. */
public final class PA6SoundsActivity extends Activity {
 private final String[] ids={"white","pink","brown","rain","fan","waves"};
 private final String[] names={"Biały szum","Różowy szum","Brązowy szum","Szum deszczu","Szum wentylatora","Fale morskie"};
 private final String[] emojis={"⚪","🌷","🌙","🌧","🌀","🌊"};
 private final int[] timers={0,15,30,60,120};
 private final String[] timerNames={"Bez limitu","15 minut","30 minut","1 godzina","2 godziny"};
 private RadioGroup choices;
 private SeekBar volume;
 private Spinner timer;
 private TextView status;
 private int violet=PA6Ui.PRIMARY;
 private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
 private TextView text(String value,int size,boolean bold){
  TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(PA6Ui.INK);
  if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
  t.setPadding(dp(6),dp(8),dp(6),dp(8));return t;
 }
 private GradientDrawable surface(){
  GradientDrawable g=new GradientDrawable();g.setColor(Color.WHITE);g.setCornerRadius(dp(14));g.setStroke(dp(1),PA6Ui.BORDER);return g;
 }
 private Button button(String name){
  Button b=new Button(this);b.setAllCaps(false);b.setTextSize(17);b.setMinHeight(dp(55));
  b.setText(name);b.setTextColor(violet);b.setBackground(PA6Ui.surface(this,PA6Ui.WHITE,PA6Ui.BORDER,13));return b;
 }
 @Override public void onCreate(Bundle b){
  super.onCreate(b);
  android.content.SharedPreferences prefs=getSharedPreferences(PA6SoundsService.PREF,0);
  ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(PA6Ui.BG);scroll.setFillViewport(true);
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(14),dp(18),dp(24));
  scroll.addView(root);setContentView(scroll);
  root.addView(text("🎵  Dźwięki spokoju • Alfa",25,true));
  root.addView(text("Wybierz szum lub dźwięk do odpoczynku. Działają bez internetu, także przy zablokowanym ekranie.",15,false));
  root.addView(text("Wybierz dźwięk",20,true));
  choices=new RadioGroup(this);choices.setOrientation(RadioGroup.VERTICAL);root.addView(choices);
  String last=prefs.getString("mode","white");
  for(int i=0;i<ids.length;i++){
   RadioButton item=new RadioButton(this);
   item.setId(300+i);item.setText(emojis[i]+"   "+names[i]);item.setTextSize(18);
   item.setTextColor(PA6Ui.INK);item.setPadding(dp(14),dp(7),dp(12),dp(7));
   item.setMinHeight(dp(53));item.setBackground(surface());
   RadioGroup.LayoutParams lp=new RadioGroup.LayoutParams(-1,dp(58));lp.setMargins(0,dp(3),0,dp(3));
   choices.addView(item,lp);if(ids[i].equals(last))choices.check(item.getId());
  }
  root.addView(text("Głośność dźwięku",18,true));
  volume=new SeekBar(this);volume.setMax(100);volume.setProgress(prefs.getInt("volume",45));
  root.addView(volume,new LinearLayout.LayoutParams(-1,dp(45)));
  root.addView(text("Wyłącznik czasowy",18,true));
  timer=new Spinner(this);
  ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,timerNames);
  adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
  timer.setAdapter(adapter);
  int currentTimer=prefs.getInt("minutes",30);for(int i=0;i<timers.length;i++)if(timers[i]==currentTimer)timer.setSelection(i);
  root.addView(timer,new LinearLayout.LayoutParams(-1,dp(57)));
  LinearLayout buttons=new LinearLayout(this);buttons.setOrientation(LinearLayout.HORIZONTAL);
  Button play=button("▶  Odtwarzaj"),stop=button("■  Zatrzymaj");
  buttons.addView(play,new LinearLayout.LayoutParams(0,dp(63),1));
  buttons.addView(stop,new LinearLayout.LayoutParams(0,dp(63),1));
  play.setTextColor(PA6Ui.WHITE);play.setBackground(PA6Ui.surface(this,PA6Ui.PRIMARY,PA6Ui.PRIMARY,13));
  root.addView(buttons);
  status=text("",15,false);root.addView(status);
  play.setOnClickListener(v->{
   int chosen=choices.getCheckedRadioButtonId()-300;if(chosen<0||chosen>=ids.length)chosen=0;
   int selectedTime=timers[timer.getSelectedItemPosition()];
   Intent intent=new Intent(this,PA6SoundsService.class).setAction(PA6SoundsService.PLAY);
   intent.putExtra("mode",ids[chosen]);intent.putExtra("volume",volume.getProgress());
   intent.putExtra("minutes",selectedTime);startService(intent);
   status.setText("Odtwarzanie: "+names[chosen]+" • dźwięk w tle • "+timerNames[timer.getSelectedItemPosition()]);
  });
  stop.setOnClickListener(v->{startService(new Intent(this,PA6SoundsService.class).setAction(PA6SoundsService.STOP));status.setText("Dźwięk zatrzymany.");});
  root.addView(text("Szumy są wytwarzane przez tablet, a deszcz, wentylator i morze to ich syntetyczne imitacje. Działają całkowicie offline. Na początek ustaw umiarkowaną głośność.",13,false));
  refreshState();
 }
 private void refreshState(){
  android.content.SharedPreferences preferences=getSharedPreferences(PA6SoundsService.PREF,0);
  if(preferences.getBoolean("playing",false))
   status.setText("Odtwarzanie w tle: "+PA6SoundsService.label(preferences.getString("mode","white")));
  else status.setText("Odtwarzacz gotowy. Wybierz dźwięk i naciśnij Odtwarzaj.");
 }
 @Override protected void onResume(){super.onResume();if(status!=null)refreshState();}
}