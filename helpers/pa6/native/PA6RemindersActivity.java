package com.ispina.lokalnie;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.text.*;
import java.util.*;

/** Time picker, list and cancel for native reminders. */
public final class PA6RemindersActivity extends PA6BaseActivity {
 private LinearLayout rows;
 private EditText note;
 private TextView chosen;
 private Calendar calendar=Calendar.getInstance(TimeZone.getTimeZone("Europe/Warsaw"));
 private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
 private Button button(String label){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextColor(PA6Premium.BLUE);b.setTextSize(16);b.setMinHeight(dp(56));return b;}
 private TextView label(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(PA6Premium.INK);t.setPadding(dp(4),dp(8),dp(4),dp(8));return t;}
 private String format(long when){return new SimpleDateFormat("dd.MM.yyyy HH:mm",new Locale("pl","PL")).format(new Date(when));}
 @Override public void onCreate(Bundle b){
  super.onCreate(b);
  ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(PA6Premium.BG);
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(17),dp(12),dp(17),dp(24));scroll.addView(root);setContentView(scroll);
  root.addView(label("Przypomnienia",25));
  root.addView(label("Ustaw termin. Powiadomienie działa bez internetu, ale oszczędzanie baterii może je opóźnić.",14));
  note=new EditText(this);note.setSingleLine(false);note.setMinLines(2);note.setHint("O czym mam przypomnieć?");
  root.addView(note,new LinearLayout.LayoutParams(-1,dp(86)));
  calendar.add(Calendar.HOUR_OF_DAY,1);
  calendar.set(Calendar.MINUTE,0);
  chosen=label("Termin: "+format(calendar.getTimeInMillis()),16);root.addView(chosen);
  Button date=button("Wybierz datę");root.addView(date);
  date.setOnClickListener(v->new DatePickerDialog(this,(view,y,m,d)->{
   calendar.set(Calendar.YEAR,y);calendar.set(Calendar.MONTH,m);calendar.set(Calendar.DAY_OF_MONTH,d);chosen.setText("Termin: "+format(calendar.getTimeInMillis()));
  },calendar.get(Calendar.YEAR),calendar.get(Calendar.MONTH),calendar.get(Calendar.DAY_OF_MONTH)).show());
  Button time=button("Wybierz godzinę");root.addView(time);
  time.setOnClickListener(v->new TimePickerDialog(this,(view,h,m)->{
   calendar.set(Calendar.HOUR_OF_DAY,h);calendar.set(Calendar.MINUTE,m);calendar.set(Calendar.SECOND,0);chosen.setText("Termin: "+format(calendar.getTimeInMillis()));
  },calendar.get(Calendar.HOUR_OF_DAY),calendar.get(Calendar.MINUTE),true).show());
  Button save=button("✓  Zapisz przypomnienie");root.addView(save);
  save.setOnClickListener(v->{
   String t=note.getText().toString().trim();
   if(t.isEmpty()||t.length()>200){Toast.makeText(this,"Wpisz krótką treść przypomnienia",Toast.LENGTH_SHORT).show();return;}
   if(calendar.getTimeInMillis()<=System.currentTimeMillis()){Toast.makeText(this,"Wybierz przyszłą datę",Toast.LENGTH_SHORT).show();return;}
   boolean ok=PA6ReminderStore.saveAndSchedule(this,UUID.randomUUID().toString(),t,t,calendar.getTimeInMillis(),"once");
   PA6Haptics.emit(this,ok?"success":"error");
   Toast.makeText(this,ok?"Przypomnienie zapisane":"Nie udało się zapisać",Toast.LENGTH_LONG).show();
   if(ok){note.setText("");refresh();}
  });
  root.addView(label("Zapisane przypomnienia",20));
  rows=new LinearLayout(this);rows.setOrientation(1);root.addView(rows);
  refresh();
 }
 private void refresh(){
  rows.removeAllViews();
  JSONArray source=PA6ReminderStore.list(this);int shown=0;
  for(int i=0;i<source.length();i++){
   JSONObject entry=source.optJSONObject(i);
   if(entry==null||entry.optLong("when")<System.currentTimeMillis())continue;
   shown++;
   String id=entry.optString("id");
   String title=entry.optString("title","Przypomnienie");
   LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
   TextView item=label(format(entry.optLong("when"))+"\n"+title,16);
   row.addView(item,new LinearLayout.LayoutParams(0,-2,1));
   Button remove=button("Usuń");remove.setTextSize(14);row.addView(remove,new LinearLayout.LayoutParams(dp(95),dp(55)));
   remove.setOnClickListener(v->new AlertDialog.Builder(this).setMessage("Usunąć przypomnienie?").setNegativeButton("Nie",null).setPositiveButton("Usuń",(d,w)->{PA6ReminderStore.cancel(this,id);refresh();}).show());
   rows.addView(row);
  }
  if(shown==0)rows.addView(label("Nie masz jeszcze zaplanowanych przypomnień.",14));
 }
}