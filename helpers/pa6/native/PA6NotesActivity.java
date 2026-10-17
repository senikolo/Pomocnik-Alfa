package com.ispina.lokalnie;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

/** Same native_notes key as the PA transport assistant. */
public final class PA6NotesActivity extends Activity {
 private EditText editor;
 private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
 private Button button(String title){
  Button b=new Button(this);b.setText(title);b.setTextSize(17);b.setAllCaps(false);b.setTextColor(0xff63449b);b.setMinHeight(dp(56));return b;
 }
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
  ScrollView sc=new ScrollView(this);sc.setBackgroundColor(0xfff6f3fa);
  LinearLayout root=new LinearLayout(this);root.setPadding(dp(18),dp(17),dp(18),dp(26));root.setOrientation(1);
  sc.addView(root);setContentView(sc);
  TextView heading=new TextView(this);heading.setText("📝  Notatnik Alfa");heading.setTextSize(25);heading.setTextColor(0xff493078);
  root.addView(heading);
  TextView hint=new TextView(this);hint.setText("Notatki zapisują się w pamięci tabletu. Ten sam notatnik jest dostępny dla Asystenta Alfa.");
  hint.setTextSize(15);hint.setPadding(0,dp(10),0,dp(8));root.addView(hint);
  editor=new EditText(this);editor.setTextSize(18);editor.setGravity(Gravity.TOP|Gravity.LEFT);
  editor.setHint("Zapisz ważną informację…");editor.setSingleLine(false);editor.setMinLines(10);
  editor.setText(getSharedPreferences("native_notes",0).getString("note",""));
  root.addView(editor,new LinearLayout.LayoutParams(-1,dp(340)));
  Button save=button("✓ Zapisz notatkę");root.addView(save);
  save.setOnClickListener(v->save());
  Button share=button("Udostępnij tekst");root.addView(share);
  share.setOnClickListener(v->{String content=editor.getText().toString();if(content.isEmpty()){Toast.makeText(this,"Notatka jest pusta",Toast.LENGTH_SHORT).show();return;}Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,content);startActivity(Intent.createChooser(i,"Udostępnij notatkę"));});
  Button clear=button("Wyczyść notatnik");root.addView(clear);
  clear.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Wyczyścić notatnik?")
    .setMessage("Ta czynność usunie wszystkie zapisane tu informacje.")
    .setNegativeButton("Anuluj",null).setPositiveButton("Wyczyść",(d,w)->{editor.setText("");save();}).show());
 }
 private void save(){
  String value=editor.getText().toString();
  if(value.length()>180000){Toast.makeText(this,"Notatka jest zbyt długa. Zrób kopię i skróć tekst.",Toast.LENGTH_LONG).show();return;}
  boolean ok=getSharedPreferences("native_notes",0).edit().putString("note",value).commit();
  Toast.makeText(this,ok?"Notatka zapisana":"Nie udało się zapisać notatki",Toast.LENGTH_SHORT).show();
 }
 @Override public void onBackPressed(){save();super.onBackPressed();}
}