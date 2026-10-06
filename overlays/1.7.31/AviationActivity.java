package com.ispina.lokalnie;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class AviationActivity extends ThemedActivity {
    private static final String EPKK_MYTUNER = "https://mytuner-radio.com/radio/krakow-airport-atc-pl-519787/";
    private static final String EPKK_SOURCE = "https://krakow.airtower.pl/";
    private static final String EPKK_LIVEATC_INFO = "https://www.liveatc.net/search/?icao=krk";

    private WebView player;
    private TextView status;
    private boolean firstLoad = true;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(NativeUi.bg(this));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(NativeUi.dp(this,14), NativeUi.dp(this,14), NativeUi.dp(this,14), NativeUi.dp(this,28));
        scroll.addView(root);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        Button back = NativeUi.button(this,"‹",true);
        back.setTextSize(24);
        back.setOnClickListener(v -> finish());
        head.addView(back,new LinearLayout.LayoutParams(NativeUi.dp(this,48),NativeUi.dp(this,46)));
        TextView title = NativeUi.text(this,"Lotnictwo · Kraków",24,true);
        title.setPadding(NativeUi.dp(this,12),0,0,0);
        head.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(head);

        LinearLayout live = NativeUi.card(this);
        live.addView(NativeUi.text(this,"✈️ Kraków Airport / EPKK",19,true));
        TextView d = NativeUi.muted(this,"Nasłuch uruchamia się tutaj, w Pomocniku Alfa. Kafelek nie przekierowuje już do zewnętrznej przeglądarki.",13);
        NativeUi.addSpacer(live,this,5);
        live.addView(d);
        NativeUi.addSpacer(live,this,10);

        status = NativeUi.muted(this,"Łączenie z odtwarzaczem EPKK…",13);
        live.addView(status);
        NativeUi.addSpacer(live,this,8);

        player = new WebView(this);
        player.setBackgroundColor(NativeUi.soft(this));
        player.setWebChromeClient(new WebChromeClient());
        WebSettings settings = player.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        player.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                Uri uri=request==null?null:request.getUrl();
                if(uri==null)return false;
                String url=uri.toString();
                if(url.startsWith("about:")||url.startsWith("data:"))return false;
                if(url.startsWith("https://mytuner-radio.com/static/")||url.startsWith("https://mytuner.global.ssl.fastly.net/"))return false;
                open(url);
                return true;
            }
            @Override public void onPageStarted(WebView view,String url,Bitmap favicon){
                if(status!=null)status.setText("Łączenie z nasłuchem Kraków-Balice…");
            }
            @Override public void onPageFinished(WebView view,String url){
                if(status!=null)status.setText("EPKK ATC · odtwarzacz w aplikacji");
                if(firstLoad){
                    firstLoad=false;
                    view.postDelayed(() -> view.evaluateJavascript(
                            "(function(){try{var b=document.querySelector('.main-play-button:not(.playing), .play-button:not(.playing), button[aria-label*=\\\"play\\\" i], [title*=\\\"play\\\" i]');if(b){b.click();return 'clicked';}var a=document.querySelector('audio');if(a){a.play();return 'audio';}return 'ready';}catch(e){return 'err';}})();",
                            null),700);
                }
            }
            @Override public void onReceivedError(WebView view,WebResourceRequest req,WebResourceError err){
                if(req!=null && req.isForMainFrame() && status!=null)status.setText("Nie udało się załadować odtwarzacza. Użyj przycisku awaryjnego poniżej.");
            }
        });
        live.addView(player,new LinearLayout.LayoutParams(-1,NativeUi.dp(this,210)));

        NativeUi.addSpacer(live,this,10);
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        Button reload = NativeUi.button(this,"▶ Włącz / odśwież",false);
        reload.setOnClickListener(v -> loadPlayer(true));
        Button stop = NativeUi.button(this,"■ Zatrzymaj",true);
        stop.setOnClickListener(v -> stopPlayer());
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0,NativeUi.dp(this,48),1);
        half.rightMargin=NativeUi.dp(this,7);
        controls.addView(reload,half);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0,NativeUi.dp(this,48),1);
        half2.leftMargin=NativeUi.dp(this,7);
        controls.addView(stop,half2);
        live.addView(controls);
        root.addView(live);

        LinearLayout freq = NativeUi.card(this);
        freq.addView(NativeUi.text(this,"Częstotliwości EPKK",18,true));
        TextView f = NativeUi.text(this,
                "Ground: 118.105 MHz\n" +
                "Tower: 123.255 MHz\n" +
                "Approach: 121.075 MHz\n" +
                "Approach: 126.975 MHz\n" +
                "Director / Approach: 126.530 MHz\n" +
                "Delivery: 121.980 MHz\n" +
                "ATIS: 126.130 MHz",14,false);
        f.setLineSpacing(0,1.18f);
        NativeUi.addSpacer(freq,this,7);
        freq.addView(f);
        NativeUi.addSpacer(freq,this,10);
        Button source = NativeUi.button(this,"Źródło i status stacji",true);
        source.setOnClickListener(v -> open(EPKK_SOURCE));
        freq.addView(source,new LinearLayout.LayoutParams(-1,NativeUi.dp(this,48)));
        NativeUi.addSpacer(freq,this,8);
        Button liveAtcInfo = NativeUi.button(this,"Informacje LiveATC o EPKK",true);
        liveAtcInfo.setOnClickListener(v -> open(EPKK_LIVEATC_INFO));
        freq.addView(liveAtcInfo,new LinearLayout.LayoutParams(-1,NativeUi.dp(this,48)));
        root.addView(freq);

        LinearLayout note = NativeUi.card(this);
        note.addView(NativeUi.text(this,"Jak działa nasłuch",17,true));
        TextView n = NativeUi.muted(this,
                "Pomocnik Alfa nie kopiuje ani nie retransmituje dźwięku. Wbudowany player korzysta z osadzanego odtwarzacza myTuner dla stacji „Krakow Airport EPKK ATC PL”. Jeśli dostawca zmieni źródło lub feed będzie chwilowo offline, dostępność może się zmieniać.",12);
        NativeUi.addSpacer(note,this,4);
        note.addView(n);
        root.addView(note);

        setContentView(scroll);
        loadPlayer(true);
    }

    private void loadPlayer(boolean autoplay){
        if(player==null)return;
        firstLoad=autoplay;
        String auto=autoplay?"true":"false";
        String html="<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"+
                "<style>html,body{margin:0;padding:0;background:#111827;color:#fff;font-family:sans-serif}#wrap{padding:10px}.mytuner-widget{display:block;width:100%;overflow:hidden;border:0;border-radius:14px;background:#f5c00a;color:#222}.bar{height:78px;display:flex;align-items:center;padding:5px 10px;box-sizing:border-box}.main-play-button{width:48px;height:48px;border-radius:24px;background:#fff;box-shadow:0 2px 8px #0004;margin-right:12px;cursor:pointer;display:flex;align-items:center;justify-content:center}.main-play-button div:before{content:'▶';font-size:22px}.main-play-button.playing div:before{content:'Ⅱ';font-weight:bold}.player-radio-link{font-weight:bold;font-size:17px;line-height:1.25;flex:1;color:#222;text-decoration:none;overflow:hidden}.player-radio-name{display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.sub{font-size:12px;opacity:.72;margin-top:3px}.volume-controls{width:1px;height:1px;overflow:hidden;opacity:0}.history{display:none}</style></head><body><div id='wrap'>"+
                "<div id='paEpkkWidget' class='mytuner-widget' data-target='519787' data-requires_initialization='true' data-autoplay='"+auto+"' data-hidehistory='true'>"+
                "<div id='paEpkkWidgettop-bar' class='bar'><div id='paEpkkWidgetplay-button' class='main-play-button disabled' data-id='paEpkkWidget'><div></div></div>"+
                "<a class='player-radio-link' href='"+EPKK_MYTUNER+"' target='_blank' rel='noopener'><span class='player-radio-name'>Kraków Airport EPKK ATC</span><div class='sub'>Tower / Approach · odtwarzacz myTuner</div></a>"+
                "<div class='volume-controls'><input id='paEpkkWidgetvolume-control' class='volume-control slider' max='100' min='1' type='range' value='100'><div id='paEpkkWidgetvolume-indicator'></div></div></div>"+
                "<ul id='paEpkkWidgetsong-history' class='history' data-border='0'></ul></div></div>"+
                "<script>var mytuner_scripts=mytuner_scripts||{};mytuner_scripts['player-v1.js_queue']=mytuner_scripts['player-v1.js_queue']||[];function paRun(){mytuner_scripts['player-v1.js_queue'].forEach(function(f){f();});}"+
                "if(mytuner_scripts['player-v1.js-imported']==undefined){mytuner_scripts['player-v1.js-imported']=false;mytuner_scripts['player-v1.js']=function(){};var s=document.createElement('script');s.src='https://mytuner-radio.com/static/js/widgets/player-v1.js';s.defer=true;s.onload=paRun;document.head.appendChild(s);mytuner_scripts['player-v1.js_queue'].push(function(){mytuner_scripts['player-v1.js']('paEpkkWidget');document.getElementById('paEpkkWidget').dataset.requires_initialization='false';});}else{mytuner_scripts['player-v1.js_queue'].push(function(){mytuner_scripts['player-v1.js']('paEpkkWidget');});}</script>"+
                "<script src='https://mytuner-radio.com/static/js/widgets/widget-player-v1.js' defer></script>"+
                "</body></html>";
        player.loadDataWithBaseURL("https://mytuner-radio.com/",html,"text/html","UTF-8",null);
    }

    private void stopPlayer(){
        if(player==null)return;
        try{player.evaluateJavascript("(function(){try{document.querySelectorAll('audio').forEach(function(a){a.pause();a.currentTime=0;});var b=document.querySelector('.main-play-button.playing,.play-button.playing');if(b)b.click();}catch(e){}})();",null);}catch(Throwable ignored){}
        if(status!=null)status.setText("Nasłuch zatrzymany");
    }

    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Throwable e) {
            Toast.makeText(this,"Nie udało się otworzyć strony.",Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onDestroy(){
        if(player!=null){
            try{player.loadUrl("about:blank");player.stopLoading();player.destroy();}catch(Throwable ignored){}
            player=null;
        }
        super.onDestroy();
    }
}