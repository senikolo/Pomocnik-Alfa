package com.ispina.lokalnie;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.ispina.lokalnie.transit.CzynaczasLinks;

/** A clearly attributed website viewer, not a PA real-time prediction engine. */
public final class LiveMapActivity extends ThemedActivity {
    public static final String EXTRA_STOP_ID="live_stop_id";
    public static final String EXTRA_STOP_NAME="live_stop_name";
    public static final String EXTRA_LINE="live_line";
    private WebView website;
    private ProgressBar loading;
    private TextView status;
    private String address;

    private int dp(int n){return NativeUi.dp(this,n);}

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        String id=getIntent().getStringExtra(EXTRA_STOP_ID);
        String name=getIntent().getStringExtra(EXTRA_STOP_NAME);
        String line=getIntent().getStringExtra(EXTRA_LINE);
        address=CzynaczasLinks.forStop("warsaw",id);
        if(address==null)address="https://czynaczas.pl/warsaw";

        LinearLayout page=new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(NativeUi.bg(this));
        setContentView(page);

        LinearLayout title=new LinearLayout(this);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(12),dp(10),dp(12),dp(8));
        Button back=NativeUi.button(this,"‹ Odjazdy",true);
        back.setOnClickListener(v->finish());
        title.addView(back,new LinearLayout.LayoutParams(dp(120),dp(46)));
        TextView header=NativeUi.text(this,"Mapa LIVE",21,true);
        header.setPadding(dp(10),0,0,0);
        title.addView(header,new LinearLayout.LayoutParams(0,-2,1));
        page.addView(title);

        String location=(name==null||name.trim().isEmpty())?
            "Mapa pojazdów WTP Warszawa":name.trim();
        if(line!=null&&!line.trim().isEmpty())location+=" · linia "+line.trim();
        TextView source=NativeUi.muted(this,
            location+"\nCzynaczas.pl — serwis zewnętrzny wyświetlany w PA. "+
            "Odjazdy rozkładowe w PA nie są prognozami LIVE.",13);
        source.setPadding(dp(15),dp(3),dp(15),dp(8));
        page.addView(source);

        LinearLayout tools=new LinearLayout(this);
        tools.setPadding(dp(12),0,dp(12),dp(5));
        Button reload=NativeUi.button(this,"↻ Odśwież mapę",true);
        Button browser=NativeUi.button(this,"Otwórz w przeglądarce",true);
        reload.setOnClickListener(v->{if(website!=null)website.reload();});
        browser.setOnClickListener(v->openExternal(Uri.parse(address)));
        tools.addView(reload,new LinearLayout.LayoutParams(0,dp(49),1));
        tools.addView(browser,new LinearLayout.LayoutParams(0,dp(49),1));
        page.addView(tools);

        loading=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        loading.setIndeterminate(true);
        page.addView(loading,new LinearLayout.LayoutParams(-1,dp(3)));

        status=NativeUi.muted(this,"Łączenie z Czynaczas.pl…",13);
        status.setPadding(dp(15),dp(2),dp(15),dp(4));
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        page.addView(status);

        try{
            website=new WebView(this);
            WebSettings settings=website.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(false);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            website.setWebViewClient(new WebViewClient(){
                private boolean navigate(Uri uri){
                    if(uri==null)return true;
                    String scheme=uri.getScheme(),host=uri.getHost();
                    boolean https="https".equalsIgnoreCase(scheme);
                    boolean provider="czynaczas.pl".equalsIgnoreCase(host)||
                        "www.czynaczas.pl".equalsIgnoreCase(host);
                    if(https&&provider)return false;
                    if(https||"http".equalsIgnoreCase(scheme))openExternal(uri);
                    return true;
                }
                @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){
                    return navigate(r.getUrl());
                }
                @SuppressWarnings("deprecation")
                @Override public boolean shouldOverrideUrlLoading(WebView v,String url){
                    return navigate(Uri.parse(url));
                }
                @Override public void onPageFinished(WebView v,String url){
                    if(status!=null)status.setText(
                        "Widok Czynaczas.pl. Dane LIVE dostarcza serwis zewnętrzny.");
                }
                @Override public void onReceivedError(WebView v,
                        WebResourceRequest request,WebResourceError error){
                    if(request.isForMainFrame()&&status!=null)
                        status.setText("Nie udało się załadować mapy. Spróbuj odświeżyć lub otwórz ją w przeglądarce.");
                }
            });
            website.setWebChromeClient(new WebChromeClient(){
                @Override public void onProgressChanged(WebView v,int progress){
                    if(loading!=null)loading.setVisibility(progress>=100?View.GONE:View.VISIBLE);
                }
            });
            page.addView(website,new LinearLayout.LayoutParams(-1,0,1f));
            website.loadUrl(address);
        }catch(RuntimeException ex){
            status.setText("Brak zgodnego widoku WWW. Otwórz mapę w przeglądarce.");
            if(website!=null){website.destroy();website=null;}
            loading.setVisibility(View.GONE);
        }
    }

    private void openExternal(Uri uri){
        try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}
        catch(ActivityNotFoundException error){
            if(status!=null)status.setText("Brak przeglądarki do otwarcia strony.");
        }
    }
    @Override public void onBackPressed(){
        if(website!=null&&website.canGoBack())website.goBack();
        else super.onBackPressed();
    }
    @Override protected void onDestroy(){
        if(website!=null){
            website.stopLoading();
            website.destroy();
            website=null;
        }
        super.onDestroy();
    }
}
