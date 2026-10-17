package com.ispina.lokalnie;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;

/** Offline PDF/TXT reader with one rendered page in memory at a time. */
public final class PA6ReaderActivity extends Activity {
 private static final int OPEN=645;
 private ImageView pageImage;
 private TextView textPage,status;
 private PdfRenderer document;
 private ParcelFileDescriptor descriptor;
 private Bitmap bitmap;
 private int page=0,request=0;
 private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
 private TextView label(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(0xff503b70);t.setPadding(dp(6),dp(8),dp(6),dp(8));return t;}
 private Button button(String s){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextColor(0xff68499e);b.setTextSize(16);b.setMinHeight(dp(55));return b;}
 @Override public void onCreate(Bundle b){
  super.onCreate(b);
  ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(0xfff7f4fa);
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(13),dp(12),dp(13),dp(30));
  scroll.addView(root);setContentView(scroll);
  root.addView(label("📖  Czytnik Alfa",26));
  root.addView(label("Otwieraj dokumenty PDF i TXT z pamięci tabletu. PDF wyświetla tylko jedną stronę naraz, by nie przeciążać 2 GB RAM.",14));
  Button open=button("📂  Wybierz plik PDF lub TXT");root.addView(open);
  open.setOnClickListener(v->{
   Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);
   i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/pdf","text/plain"});
   startActivityForResult(i,OPEN);
  });
  LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.HORIZONTAL);root.addView(controls);
  Button prev=button("‹ Poprzednia");Button next=button("Następna ›");
  controls.addView(prev,new LinearLayout.LayoutParams(0,dp(53),1));
  controls.addView(next,new LinearLayout.LayoutParams(0,dp(53),1));
  prev.setOnClickListener(v->render(page-1));next.setOnClickListener(v->render(page+1));
  status=label("Nie otwarto dokumentu.",14);root.addView(status);
  pageImage=new ImageView(this);pageImage.setAdjustViewBounds(true);pageImage.setScaleType(ImageView.ScaleType.FIT_CENTER);pageImage.setBackgroundColor(0xffffffff);
  root.addView(pageImage,new LinearLayout.LayoutParams(-1,-2));
  textPage=label("",17);textPage.setTextIsSelectable(true);root.addView(textPage);
 }
 @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
  super.onActivityResult(requestCode,resultCode,data);
  if(requestCode!=OPEN||resultCode!=RESULT_OK||data==null||data.getData()==null)return;
  Uri uri=data.getData();String mime=getContentResolver().getType(uri);
  closeDocument();textPage.setText("");pageImage.setImageDrawable(null);
  try{
   if("application/pdf".equals(mime)||uri.toString().toLowerCase().endsWith(".pdf")){
    descriptor=getContentResolver().openFileDescriptor(uri,"r");
    if(descriptor==null)throw new IOException("Nie można otworzyć dokumentu.");
    document=new PdfRenderer(descriptor);page=0;render(0);
   }else{
    ByteArrayOutputStream out=new ByteArrayOutputStream();
    try(InputStream input=getContentResolver().openInputStream(uri)){
     if(input==null)throw new IOException("Brak dostępu do pliku.");
     byte[] buf=new byte[4096];int n,total=0;
     while((n=input.read(buf))!=-1){total+=n;if(total>1024*1024)throw new IOException("Za duży plik tekstowy (limit 1 MB).");out.write(buf,0,n);}
    }
    textPage.setText(out.toString("UTF-8"));status.setText("Dokument tekstowy • czytanie offline");
   }
  }catch(Exception e){status.setText("Nie można otworzyć pliku: "+e.getMessage());closeDocument();}
 }
 private void render(int wanted){
  if(document==null||wanted<0||wanted>=document.getPageCount())return;
  try{
   page=wanted;PdfRenderer.Page p=document.openPage(page);
   try{
    int width=Math.max(500,Math.min(1050,getResources().getDisplayMetrics().widthPixels));
    float ratio=(float)p.getHeight()/Math.max(1,p.getWidth());
    int height=Math.max(350,Math.min(1450,(int)(width*ratio)));
    Bitmap newBmp=Bitmap.createBitmap(width,height,Bitmap.Config.RGB_565);
    newBmp.eraseColor(Color.WHITE);p.render(newBmp,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
    pageImage.setImageBitmap(newBmp);
    // Reclaim the previous page after the ImageView has switched.
    if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();
    bitmap=newBmp;
    status.setText("PDF: strona "+(page+1)+" z "+document.getPageCount());
   }finally{p.close();}
  }catch(Exception e){status.setText("Nie udało się wyświetlić strony.");}
 }
 private void closeDocument(){
  try{pageImage.setImageDrawable(null);}catch(Exception ignored){}
  if(bitmap!=null){bitmap.recycle();bitmap=null;}
  if(document!=null){try{document.close();}catch(Exception ignored){}document=null;}
  if(descriptor!=null){try{descriptor.close();}catch(Exception ignored){}descriptor=null;}
 }
 @Override protected void onDestroy(){closeDocument();super.onDestroy();}
}