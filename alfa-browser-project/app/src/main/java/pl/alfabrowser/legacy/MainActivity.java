package pl.alfabrowser.legacy;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.webkit.*;
import android.widget.*;
import java.net.URLEncoder;
import java.util.*;

public class MainActivity extends Activity {
    private WebView web;
    private EditText address;
    private TextView status;
    private boolean saver=false, desktop=false, js=true, fullscreen=false;
    private final String PREFS="alfa_browser";
    private final String HOME="file:///android_asset/start.html";

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        buildUi();
        configureWebView();
        Intent i=getIntent();
        Uri data=i!=null?i.getData():null;
        if(data!=null && ("http".equals(data.getScheme()) || "https".equals(data.getScheme()))) load(data.toString());
        else web.loadUrl(HOME);
    }

    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}

    private Button btn(String t){
        Button b=new Button(this);
        b.setText(t); b.setTextSize(15); b.setTextColor(Color.rgb(31,82,75));
        b.setBackgroundResource(R.drawable.bg_button); b.setMinHeight(dp(44)); b.setPadding(dp(8),0,dp(8),0);
        return b;
    }

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(6),dp(6),dp(6),dp(4));
        root.setBackgroundColor(Color.rgb(238,244,241));

        LinearLayout top=new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(4),dp(4),dp(4),dp(4));
        top.setBackgroundResource(R.drawable.bg_bar);

        TextView logo=new TextView(this);
        logo.setText("  ALFA"); logo.setTextColor(Color.WHITE); logo.setTextSize(18); logo.setTypeface(null,1);
        top.addView(logo,new LinearLayout.LayoutParams(dp(72),dp(46)));

        address=new EditText(this);
        address.setSingleLine(true); address.setTextSize(16); address.setHint("Adres lub wyszukiwanie");
        address.setSelectAllOnFocus(true); address.setBackgroundColor(Color.WHITE); address.setPadding(dp(10),0,dp(10),0);
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,dp(46),1f);
        ap.setMargins(dp(4),0,dp(4),0); top.addView(address,ap);

        Button go=btn("Idź"); go.setOnClickListener(v->goFromBar());
        top.addView(go,new LinearLayout.LayoutParams(dp(66),dp(46)));
        root.addView(top,new LinearLayout.LayoutParams(-1,dp(54)));

        LinearLayout nav=new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL); nav.setGravity(Gravity.CENTER); nav.setPadding(0,dp(5),0,dp(5));
        String[] names={"◀","▶","⌂","↻","★","☰"};
        for(String n:names){Button b=btn(n); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(46),1f); p.setMargins(dp(2),0,dp(2),0); nav.addView(b,p);}
        ((Button)nav.getChildAt(0)).setOnClickListener(v->{if(web.canGoBack())web.goBack();});
        ((Button)nav.getChildAt(1)).setOnClickListener(v->{if(web.canGoForward())web.goForward();});
        ((Button)nav.getChildAt(2)).setOnClickListener(v->web.loadUrl(HOME));
        ((Button)nav.getChildAt(3)).setOnClickListener(v->web.reload());
        ((Button)nav.getChildAt(4)).setOnClickListener(v->addBookmark());
        ((Button)nav.getChildAt(5)).setOnClickListener(v->showMenu((Button)v));
        root.addView(nav,new LinearLayout.LayoutParams(-1,dp(56)));

        status=new TextView(this);
        status.setText("Gotowy"); status.setTextSize(12); status.setTextColor(Color.DKGRAY); status.setPadding(dp(5),0,0,dp(2));
        root.addView(status,new LinearLayout.LayoutParams(-1,dp(24)));

        web=new WebView(this);
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1f));
        setContentView(root);

        address.setOnEditorActionListener((v,action,event)->{goFromBar();return true;});
    }

    private void configureWebView(){
        WebSettings s=web.getSettings();
        s.setJavaScriptEnabled(js); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true);
        s.setBuiltInZoomControls(true); s.setDisplayZoomControls(false); s.setSupportZoom(true);
        s.setLoadWithOverviewMode(true); s.setUseWideViewPort(true);
        s.setBlockNetworkImage(saver); s.setLoadsImagesAutomatically(!saver);
        if(Build.VERSION.SDK_INT>=21) s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,String url){return handleExternal(url);}
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon){
                address.setText(url.startsWith("file:///android_asset/")?"Start":url); status.setText("Ładowanie…");
            }
            @Override public void onPageFinished(WebView view,String url){
                status.setText(saver?"Saver • obrazy wyłączone":"Gotowy"); saveHistory(view.getTitle(),url);
            }
            @Override public void onReceivedError(WebView view,int errorCode,String description,String failingUrl){
                status.setText("Błąd: "+description);
            }
        });

        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onProgressChanged(WebView v,int p){if(p<100)status.setText("Ładowanie "+p+"%");}
        });
    }

    private boolean handleExternal(String url){
        if(url==null)return false;
        if(url.startsWith("http://")||url.startsWith("https://"))return false;
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
        catch(Exception e){Toast.makeText(this,"Nie można otworzyć tego łącza",Toast.LENGTH_SHORT).show();}
        return true;
    }

    private void goFromBar(){
        String q=address.getText().toString().trim();
        if(q.length()==0)return;
        load(q);
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(),0);
    }

    private void load(String q){
        if(q.startsWith("http://")||q.startsWith("https://")||q.startsWith("file://"))web.loadUrl(q);
        else if(q.contains(".")&&!q.contains(" "))web.loadUrl("https://"+q);
        else try{web.loadUrl("https://www.google.com/search?q="+URLEncoder.encode(q,"UTF-8"));}
        catch(Exception e){web.loadUrl("https://www.google.com");}
    }

    private void showMenu(View anchor){
        PopupMenu p=new PopupMenu(this,anchor);
        p.getMenu().add(1,1,0,"Zakładki");
        p.getMenu().add(1,2,1,"Historia");
        p.getMenu().add(1,3,2,saver?"Saver: włącz obrazy":"Saver: wyłącz obrazy");
        p.getMenu().add(1,4,3,desktop?"Wersja mobilna":"Wersja komputerowa");
        p.getMenu().add(1,5,4,js?"JavaScript: wyłącz":"JavaScript: włącz");
        p.getMenu().add(1,6,5,fullscreen?"Wyjdź z pełnego ekranu":"Pełny ekran");
        p.getMenu().add(1,7,6,"Wyczyść dane przeglądania");
        p.getMenu().add(1,8,7,"O aplikacji");
        p.setOnMenuItemClickListener(i->{
            switch(i.getItemId()){
                case 1:showBookmarks();return true;
                case 2:showHistory();return true;
                case 3:toggleSaver();return true;
                case 4:toggleDesktop();return true;
                case 5:toggleJs();return true;
                case 6:toggleFullscreen();return true;
                case 7:clearBrowsing();return true;
                case 8:about();return true;
            }
            return false;
        });
        p.show();
    }

    private void addBookmark(){
        String u=web.getUrl();
        if(u==null||u.startsWith("file:"))return;
        String title=web.getTitle(); if(title==null||title.length()==0)title=u;
        SharedPreferences sp=getSharedPreferences(PREFS,0);
        Set<String> set=new LinkedHashSet<String>(sp.getStringSet("bookmarks",new LinkedHashSet<String>()));
        set.add(title+"\n"+u); sp.edit().putStringSet("bookmarks",set).apply();
        Toast.makeText(this,"Dodano do zakładek",Toast.LENGTH_SHORT).show();
    }

    private void saveHistory(String title,String url){
        if(url==null||url.startsWith("file:"))return;
        SharedPreferences sp=getSharedPreferences(PREFS,0);
        String old=sp.getString("history","");
        String line=(title==null?url:title)+"\n"+url+"\n---\n";
        String both=line+old;
        if(!old.startsWith(line))sp.edit().putString("history",both.substring(0,Math.min(12000,both.length()))).apply();
    }

    private void showBookmarks(){
        Set<String> set=getSharedPreferences(PREFS,0).getStringSet("bookmarks",new LinkedHashSet<String>());
        showListDialog("Zakładki",new ArrayList<String>(set));
    }

    private void showHistory(){
        String h=getSharedPreferences(PREFS,0).getString("history","");
        ArrayList<String>a=new ArrayList<String>();
        if(h.length()>0)for(String x:h.split("\n---\n"))if(x.trim().length()>0)a.add(x);
        showListDialog("Historia",a);
    }

    private void showListDialog(String title,final ArrayList<String> data){
        if(data.size()==0){Toast.makeText(this,"Brak pozycji",Toast.LENGTH_SHORT).show();return;}
        String[] labels=new String[data.size()];
        for(int i=0;i<data.size();i++){String[] z=data.get(i).split("\n",2);labels[i]=z[0];}
        new AlertDialog.Builder(this).setTitle(title).setItems(labels,(d,w)->{
            String[] z=data.get(w).split("\n",2); if(z.length>1)load(z[1]);
        }).setNegativeButton("Zamknij",null).show();
    }

    private void toggleSaver(){
        saver=!saver;
        WebSettings s=web.getSettings();
        s.setBlockNetworkImage(saver); s.setLoadsImagesAutomatically(!saver);
        if(!saver)web.reload();
        status.setText(saver?"Saver • obrazy wyłączone":"Saver wyłączony");
    }

    private void toggleDesktop(){
        desktop=!desktop;
        WebSettings s=web.getSettings();
        if(desktop)s.setUserAgentString("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/44 Safari/537.36");
        else s.setUserAgentString(null);
        web.reload();
    }

    private void toggleJs(){
        js=!js; web.getSettings().setJavaScriptEnabled(js);
        Toast.makeText(this,"JavaScript "+(js?"włączony":"wyłączony"),Toast.LENGTH_SHORT).show();
        web.reload();
    }

    private void toggleFullscreen(){
        fullscreen=!fullscreen;
        if(fullscreen)getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    private void clearBrowsing(){
        web.clearHistory(); web.clearCache(true); WebStorage.getInstance().deleteAllData();
        getSharedPreferences(PREFS,0).edit().remove("history").apply();
        Toast.makeText(this,"Wyczyszczono historię i cache",Toast.LENGTH_SHORT).show();
    }

    private void about(){
        new AlertDialog.Builder(this).setTitle("Alfa Browser Legacy 1.0")
        .setMessage("Lekka przeglądarka dla Androida 4.2.2.\n\nNie ignoruje błędów certyfikatów. Do bankowości i płatności używaj nowszego urządzenia.")
        .setPositiveButton("OK",null).show();
    }

    @Override public void onBackPressed(){
        if(web!=null&&web.canGoBack())web.goBack(); else super.onBackPressed();
    }

    @Override protected void onDestroy(){
        if(web!=null){web.stopLoading();web.destroy();}
        super.onDestroy();
    }
}
