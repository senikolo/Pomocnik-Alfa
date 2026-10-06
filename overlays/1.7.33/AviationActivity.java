package com.ispina.lokalnie;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Message;
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

public class AviationActivity extends ThemedActivity {
    private WebView engine;
    private TextView status;
    private Button playButton;
    private boolean engineReady = false;
    private boolean autoStartPending = true;
    private int playRetryCount = 0;
    private int enginePollCount = 0;

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
        live.addView(NativeUi.text(this,"✈️ Nasłuch Kraków Airport / EPKK",19,true));
        NativeUi.addSpacer(live,this,5);
        live.addView(NativeUi.muted(this,
                "Bez przekierowania do strony. Pomocnik uruchamia nasłuch bezpośrednio na tym ekranie.",13));
        NativeUi.addSpacer(live,this,12);

        status = NativeUi.muted(this,"Przygotowanie nasłuchu EPKK…",14);
        live.addView(status);
        NativeUi.addSpacer(live,this,10);

        playButton = NativeUi.button(this,"▶ Włącz nasłuch EPKK",false);
        playButton.setOnClickListener(v -> startListening());
        live.addView(playButton,new LinearLayout.LayoutParams(-1,NativeUi.dp(this,52)));
        NativeUi.addSpacer(live,this,8);

        Button stop = NativeUi.button(this,"■ Zatrzymaj nasłuch",true);
        stop.setOnClickListener(v -> stopListening());
        live.addView(stop,new LinearLayout.LayoutParams(-1,NativeUi.dp(this,48)));

        NativeUi.addSpacer(live,this,9);
        live.addView(NativeUi.muted(this,
                "Po wejściu z kafelka Pomocnik spróbuje uruchomić dźwięk automatycznie. Jeśli Android go zablokuje, wystarczy jedno dotknięcie „Włącz nasłuch EPKK”.",12));
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
        root.addView(freq);

        LinearLayout note = NativeUi.card(this);
        note.addView(NativeUi.text(this,"Jak działa",17,true));
        NativeUi.addSpacer(note,this,4);
        note.addView(NativeUi.muted(this,
                "Interfejs jest w całości w Pomocniku Alfa. W tle używany jest osadzany silnik audio dostawcy stacji EPKK, ale żaden element głównego nasłuchu nie otwiera już strony myTuner.",12));
        root.addView(note);

        engine = new WebView(this);
        engine.setAlpha(0.01f);
        engine.setBackgroundColor(0x00000000);
        WebSettings settings = engine.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadsImagesAutomatically(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);

        engine.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                return false;
            }
        });

        engine.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if(request==null || request.getUrl()==null) return true;
                String u=request.getUrl().toString();
                if(u.startsWith("about:") || u.startsWith("data:")) return false;
                // Nigdy nie pozwalaj silnikowi przejść na stronę myTuner ani inną stronę.
                return request.isForMainFrame();
            }

            @Override public void onPageStarted(WebView view,String url,Bitmap favicon){
                engineReady=false;
                if(status!=null) status.setText("Łączenie z nasłuchem Kraków-Balice…");
            }

            @Override public void onPageFinished(WebView view,String url){
                engineReady=false;
                enginePollCount=0;
                if(status!=null) status.setText("Inicjalizacja silnika EPKK…");
                view.postDelayed(() -> pollEngineReady(),700);
            }

            @Override public void onReceivedError(WebView view,WebResourceRequest req,WebResourceError err){
                if(req!=null && req.isForMainFrame() && status!=null){
                    status.setText("Nie udało się przygotować nasłuchu. Dotknij „Włącz nasłuch EPKK”, aby spróbować ponownie.");
                }
            }
        });

        LinearLayout.LayoutParams hidden = new LinearLayout.LayoutParams(1,1);
        root.addView(engine,hidden);

        setContentView(scroll);
        loadEngine();
    }

    private void loadEngine(){
        if(engine==null)return;
        engineReady=false;
        autoStartPending=true;
        playRetryCount=0;
        enginePollCount=0;

        String html="<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"+
                "<style>html,body{margin:0;width:1px;height:1px;overflow:hidden;background:transparent;pointer-events:none}.mytuner-widget{width:1px;height:1px;overflow:hidden}.main-play-button{width:1px;height:1px}.volume-controls,#paEpkkWidgetdow-container,#paEpkkWidgetsong-history{display:none}</style></head><body>"+
                "<div id='paEpkkWidget' class='mytuner-widget' data-target='519787' data-requires_initialization='true' data-autoplay='false' data-hidehistory='true'>"+
                "<div id='paEpkkWidgettop-bar'>"+
                "<div id='paEpkkWidgetplay-button' class='main-play-button disabled' data-id='paEpkkWidget'><div></div></div>"+
                "<span class='player-radio-link'><span class='player-radio-name'>Kraków Airport EPKK ATC</span></span>"+
                "<div class='volume-controls'><span id='paEpkkWidgetvolume-indicator'></span><input id='paEpkkWidgetvolume-control' class='volume-control slider' max='100' min='1' type='range' value='100'></div>"+
                "</div><div id='paEpkkWidgetdow-container'></div><ul id='paEpkkWidgetsong-history' data-border='0' data-bordercolor='#000'></ul></div>"+
                "<script>"+
                "window.mytuner_scripts=window.mytuner_scripts||{};"+
                "function paLoad(src,done){var x=document.createElement('script');x.src=src;x.async=false;x.onload=done;x.onerror=function(){document.title='engine_error';};document.head.appendChild(x);}"+
                "paLoad('https://mytuner-radio.com/static/js/widgets/widget-player-v1.js',function(){"+
                "  paLoad('https://mytuner-radio.com/static/js/widgets/player-v1.js',function(){"+
                "    try{"+
                "      if(window.mytuner_scripts&&typeof window.mytuner_scripts['player-v1.js']==='function'){"+
                "        window.mytuner_scripts['player-v1.js']('paEpkkWidget');"+
                "        document.getElementById('paEpkkWidget').dataset.requires_initialization='false';"+
                "        document.title='engine_init';"+
                "      }else{document.title='engine_missing';}"+
                "    }catch(e){document.title='engine_exception';}"+
                "  });"+
                "});"+
                "</script></body></html>";

        engine.loadDataWithBaseURL("https://mytuner-radio.com/",html,"text/html","UTF-8",null);
    }

    private void pollEngineReady(){
        if(engine==null)return;
        engine.evaluateJavascript("(function(){try{var b=document.getElementById('paEpkkWidgetplay-button');return (b&&!b.classList.contains('disabled'))?'ready':document.title||'loading';}catch(e){return 'error';}})();", result -> {
            String r=result==null?"":result.replace("\"","");
            if(r.contains("ready")){
                engineReady=true;
                enginePollCount=0;
                if(status!=null)status.setText("EPKK gotowe do odtwarzania");
                if(autoStartPending){
                    autoStartPending=false;
                    playRetryCount=0;
                    engine.postDelayed(() -> clickHiddenPlay(true),350);
                }
            }else{
                enginePollCount++;
                if(enginePollCount<=15){
                    if(status!=null)status.setText("Łączenie z silnikiem EPKK…");
                    engine.postDelayed(() -> pollEngineReady(),700);
                }else{
                    if(status!=null)status.setText("Silnik EPKK nie uruchomił się. Dotknij „Włącz nasłuch EPKK”, aby spróbować ponownie.");
                }
            }
        });
    }

    private void startListening(){
        if(engine==null)return;
        if(!engineReady){
            if(status!=null)status.setText("Przygotowuję nasłuch EPKK…");
            loadEngine();
            return;
        }
        playRetryCount=0;
        clickHiddenPlay(true);
    }

    private void clickHiddenPlay(boolean report){
        if(engine==null)return;
        String js="(function(){try{"+
                "var b=document.getElementById('paEpkkWidgetplay-button');"+
                "if(!b)return 'no_button';"+
                "if(b.classList.contains('disabled'))return 'loading';"+
                "if(!b.classList.contains('playing')){b.click();return 'started';}"+
                "return 'playing';"+
                "}catch(e){return 'error';}})();";
        engine.evaluateJavascript(js, result -> {
            if(!report || status==null)return;
            String r=result==null?"":result.replace("\"","");
            if(r.contains("started")||r.contains("playing")){
                status.setText("🔴 Nasłuch EPKK włączony");
                if(playButton!=null)playButton.setText("▶ Nasłuch działa");
            }else if(r.contains("loading")){
                playRetryCount++;
                if(playRetryCount<=6){
                    status.setText("Łączenie ze strumieniem EPKK…");
                    engine.postDelayed(() -> clickHiddenPlay(true),1200);
                }else{
                    status.setText("Silnik EPKK nie odpowiedział. Dotknij „Włącz nasłuch EPKK”, aby spróbować ponownie.");
                }
            }else{
                status.setText("Nie udało się uruchomić dźwięku. Dotknij przycisku jeszcze raz.");
            }
        });
    }

    private void stopListening(){
        if(engine==null)return;
        String js="(function(){try{"+
                "var b=document.getElementById('paEpkkWidgetplay-button');"+
                "if(b&&b.classList.contains('playing'))b.click();"+
                "document.querySelectorAll('audio').forEach(function(a){try{a.pause();a.currentTime=0;}catch(e){}});"+
                "return 'stopped';"+
                "}catch(e){return 'error';}})();";
        engine.evaluateJavascript(js,null);
        if(status!=null)status.setText("Nasłuch zatrzymany");
        if(playButton!=null)playButton.setText("▶ Włącz nasłuch EPKK");
    }

    @Override protected void onDestroy(){
        if(engine!=null){
            try{
                engine.evaluateJavascript("(function(){document.querySelectorAll('audio').forEach(function(a){try{a.pause();}catch(e){}});})();",null);
                engine.loadUrl("about:blank");
                engine.stopLoading();
                engine.destroy();
            }catch(Throwable ignored){}
            engine=null;
        }
        super.onDestroy();
    }
}