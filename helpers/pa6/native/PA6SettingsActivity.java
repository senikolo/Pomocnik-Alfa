package com.ispina.lokalnie;

import android.os.Bundle;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/** Comfort controls are durable and available from every native module. */
public final class PA6SettingsActivity extends PA6BaseActivity {
 private SharedPreferences prefs;private LinearLayout root;
 private int dp(int n){return PA6Premium.dp(this,n);}
 private LinearLayout card(String title,String hint){
  LinearLayout c=PA6Premium.column(this);c.setPadding(dp(18),dp(16),dp(18),dp(16));
  c.setBackground(PA6Premium.shape(this,PA6Premium.SURFACE,0xffe0e8ef,22));
  c.addView(PA6Premium.text(this,title,20,PA6Premium.INK,true));PA6Premium.space(c,6);
  c.addView(PA6Premium.text(this,hint,14,PA6Premium.MUTED,false));PA6Premium.space(c,14);
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(16));root.addView(c,p);return c;
 }
 private Switch option(LinearLayout card,String title,String key,boolean initial,boolean enabled){
  Switch s=new Switch(this);s.setText(title);s.setTextSize(15);s.setTextColor(PA6Premium.INK);s.setMinHeight(dp(56));s.setPadding(0,dp(6),0,dp(6));
  s.setChecked(prefs.getBoolean(key,initial)&&enabled);s.setEnabled(enabled);card.addView(s,new LinearLayout.LayoutParams(-1,-2));
  s.setOnCheckedChangeListener((v,on)->{prefs.edit().putBoolean(key,on).apply();if(on)PA6Haptics.emit(this,"tap");});return s;
 }
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);prefs=PA6Haptics.prefs(this);
  ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setBackgroundColor(PA6Premium.BG);
  android.widget.FrameLayout frame=new android.widget.FrameLayout(this);root=PA6Premium.column(this);root.setPadding(dp(20),dp(16),dp(20),dp(24));
  int width=(int)(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density);
  frame.addView(root,new android.widget.FrameLayout.LayoutParams(dp(Math.min(width,840)),-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL));sc.addView(frame);setContentView(sc);
  root.addView(PA6Premium.text(this,"Twój komfort",30,PA6Premium.INK,true));
  root.addView(PA6Premium.text(this,"Drobne detale. Przyjemniejszy dzień z Alfą.",15,PA6Premium.MUTED,false));PA6Premium.space(root,22);
  LinearLayout display=card("Układ ekranu","Wybierz przestronny Tablet lub skupiony, jednokolumnowy Smartfon. Zmiana zadziała po powrocie do modułu.");
  RadioGroup modes=new RadioGroup(this);String[] labels={"Automatyczny","Tablet","Smartfon"};
  for(int i=0;i<labels.length;i++){RadioButton r=new RadioButton(this);r.setId(7300+i);r.setText(labels[i]);r.setTextColor(PA6Premium.INK);r.setMinHeight(dp(48));modes.addView(r);if(labels[i].equals(PA6Premium.mode(this)))modes.check(r.getId());}
  display.addView(modes);modes.setOnCheckedChangeListener((g,id)->{int i=id-7300;if(i>=0&&i<labels.length)getSharedPreferences("pa6_display",0).edit().putString("ui_mode",labels[i]).apply();});
  option(display,"Delikatne animacje przejścia","motion",true,true);
  boolean hardware=PA6Haptics.available(this);
  if(!hardware)prefs.edit().putBoolean("vibration",false).apply();
  LinearLayout h=card("Wibracje",hardware?"Długość i rytm impulsów dopasowane do Twoich preferencji.":"To urządzenie nie ma dostępnego wibratora. Wibracje zostały wyłączone.");
  Switch enabled=option(h,"Reakcja przy dotknięciu","vibration",true,hardware);
  Spinner strength=new Spinner(this);String[] names={"Delikatne","Normalne","Mocniejsze"};
  ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names);strength.setAdapter(adapter);strength.setSelection(Math.max(0,Math.min(2,prefs.getInt("strength",0))));strength.setEnabled(hardware&&enabled.isChecked());h.addView(strength,new LinearLayout.LayoutParams(-1,dp(52)));
  strength.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int pos,long id){prefs.edit().putInt("strength",pos).apply();}});
  Switch signals=option(h,"Sygnały sukcesu i błędu","signals",true,hardware);
  Switch reminders=option(h,"Wibracje przypomnień","reminder_vibration",true,hardware);
  Button test=PA6Premium.button(this,"Test wibracji",true);h.addView(test,new LinearLayout.LayoutParams(-1,dp(52)));test.setEnabled(hardware&&enabled.isChecked());
  test.setOnClickListener(v->{PA6Haptics.emit(this,"success");Toast.makeText(this,"Sygnał testowy • "+names[strength.getSelectedItemPosition()].toLowerCase(new java.util.Locale("pl")),Toast.LENGTH_SHORT).show();});
  enabled.setOnCheckedChangeListener((v,on)->{prefs.edit().putBoolean("vibration",on).apply();strength.setEnabled(hardware&&on);signals.setEnabled(hardware&&on);reminders.setEnabled(hardware&&on);test.setEnabled(hardware&&on);});
  signals.setEnabled(hardware&&enabled.isChecked());reminders.setEnabled(hardware&&enabled.isChecked());
  LinearLayout about=card("Pomocnik Alfa 6","2.4.2 Premium • Android 6 i nowszy\nFilmy, radio, telewizja i codzienne sprawy w jednym miejscu.");
  about.addView(PA6Premium.text(this,"Ustawienia zapisują się automatycznie.",13,PA6Premium.MUTED,false));
  if(getIntent().getBooleanExtra("verify_comfort",false))verifyComfort();
 }
 private void verifyComfort(){
  for(int level=0;level<3;level++)for(String event:new String[]{"tap","success","error","reminder"}){
   long[] pattern=PA6Haptics.pattern(level,event);if(pattern.length<2||pattern[0]!=0)throw new AssertionError("Pattern");
   for(long n:pattern)if(n<0||n>1000)throw new AssertionError("Unbounded impulse");
  }
  prefs.edit().putBoolean("vibration",false).apply();
  if(PA6Haptics.enabled(this)||PA6Haptics.reminder(this).length!=1)throw new AssertionError("Disabled vibration");
  PA6Haptics.emit(this,"success");
  android.util.Log.i("PA6_COMFORT","PASS patterns 3 strengths / disabled / hardware="+PA6Haptics.available(this));
 }
}
