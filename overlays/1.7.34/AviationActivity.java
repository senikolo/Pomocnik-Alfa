package com.ispina.lokalnie;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.webkit.CookieManager;
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
import android.view.Gravity;

public class AviationActivity extends ThemedActivity {
    private static final String EPKK_EMBED = "http://e.mytuner-radio.com/embed/krakow-airport-atc-pl-519787";

    private WebView engine;
    private TextView status;
    private Button playButton;
    private boolean engineReady = false;
    private boolean autoplayPending = true;
    private int readyPolls = 0;

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
        root.setPadding(NativeUi.dp(this,14),NativeUi.dp(this,14),NativeUi.dp(this,14),NativeUi.dp(this,28));
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
                "Bez przekierowania do strony. Dźwięk uruchamiany jest przez ukryty odtwarzacz EPKK, a cały interfejs pozostaje w Pomocniku Alfa.",13));
        NativeUi.addSpacer(live,this,12);

        status = NativeUi.muted(this,"Przygotowanie nasłuchu EPKK…",14);
        live.addView(status);
        NativeUi.addSpacer(live,this,10);

        playButton = NativeUi.button(this,"▶ Włącz nasłuch EPKK",false);
        playButton.setOnClickListener(v -> startListening(true));
        live.addView(playButton,new LinearLayout.LayoutParams(-1,NativeUi.dp(this,52)));
        NativeUi.addSpacer(live,this,8);

        Button stop = NativeUi.button(this,"■ Zatrzymaj nasłuch",true);
        stop.setOnClickListener(v -> stopListening());
        live.addView(stop,new LinearLayout.LayoutParams(-1,NativeUi.dp(this,48)));

        NativeUi.addSpacer(live,this,9);
        live.addView(NativeUi.muted(this,
                "Pomocnik próbuje wystartować automatycznie. Jeśli Android nie pozwoli na autostart dźwięku, dotknij „Włącz nasłuch EPKK”.",12));
        root.addView(live);

        LinearLayout freq = NativeUi.card(this);
        freq.addView(NativeUi.text(this,"Częstotliwości EPKK",18,true));
        TextView f = NativeUi.text(this,
                "Ground: 118.105 MHz\n"+
                "Tower: 123.255 MHz\n"+
                "Approach: 121.075 MHz\n"+
                "Approach: 126.975 MHz\n"+
                "Director / Approach: 126.530 MHz\n"+
                "Delivery: 121.980 MHz\n"+
                "ATIS: 126.130 MHz",14,false);
        f.setLineSpacing(0,1.18f);
        NativeUi.addSpacer(freq,this,7);
        freq.addView(f);
        root.addView(freq);

        LinearLayout note = NativeUi.card(this);
        note.addView(NativeUi.text(this,"Jak działa",17,true));
        NativeUi.addSpacer(note,this,4);
        note.addView(NativeUi.muted(this,
                "Widoczny ekran jest w całości częścią Pomocnika Alfa. Ukryty WebView ładuje wyłącznie oficjalny embed EPKK. Nawigacja do stron zewnętrznych jest zablokowana.",12));
        root.addView(note);

        createEngine(root);
        setContentView(scroll);
        loadEngine();
    }

    private void createEngine(LinearLayout root) {
        engine = new WebView(this);
        engine.setAlpha(0.01f);
        engine.setBackgroundColor(0x00000000);

        WebSettings s = engine.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadsImagesAutomatically(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        try { cm.setAcceptThirdPartyCookies(engine,true); } catch(Throwable ignored) {}

        engine.setWebChromeClient(new WebChromeClient());

        engine.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                if(req==null || req.getUrl()==null) return true;
                String u=req.getUrl().toString();
                // Dopuszczamy tylko właściwy embed EPKK jako stronę główną.
                return !u.startsWith(EPKK_EMBED);
            }

            @Override public void onPageStarted(WebView view,String url,Bitmap icon) {
                engineReady=false;
                readyPolls=0;
                if(status!=null) status.setText("Łączenie z EPKK…");
            }

            @Override public void onPageFinished(WebView view,String url) {
                engineReady=false;
                readyPolls=0;
                if(status!=null) status.setText("Uruchamianie odtwarzacza EPKK…");
                view.postDelayed(() -> pollReady(),500);
            }

            @Override public void onReceivedError(WebView view,WebResourceRequest req,WebResourceError err) {
                if(req!=null && req.isForMainFrame() && status!=null) {
                    engineReady=false;
                    status.setText("Nie udało się połączyć z odtwarzaczem EPKK. Dotknij „Włącz nasłuch EPKK”, aby spróbować ponownie.");
                }
            }
        });

        root.addView(engine,new LinearLayout.LayoutParams(2,2));
    }

    private void loadEngine() {
        if(engine==null)return;
        engineReady=false;
        autoplayPending=true;
        readyPolls=0;
        if(status!=null)status.setText("Przygotowanie nasłuchu EPKK…");
        engine.loadUrl(EPKK_EMBED);
    }

    private void pollReady() {
        if(engine==null)return;
        String js="(function(){try{"+
                "var a=document.getElementById('radio-player');"+
                "return (typeof playRadio==='function' && a)?'ready':document.readyState;"+
                "}catch(e){return 'error';}})();";
        engine.evaluateJavascript(js,res -> {
            String r=res==null?"":res.replace("\"","");
            if(r.contains("ready")) {
                engineReady=true;
                readyPolls=0;
                if(status!=null)status.setText("EPKK gotowe do odtwarzania");
                if(autoplayPending) {
                    autoplayPending=false;
                    engine.postDelayed(() -> startListening(false),350);
                }
            } else {
                readyPolls++;
                if(readyPolls<=18) {
                    if(status!=null)status.setText("Łączenie z odtwarzaczem EPKK…");
                    engine.postDelayed(() -> pollReady(),500);
                } else {
                    engineReady=false;
                    if(status!=null)status.setText("Odtwarzacz EPKK nie odpowiedział. Dotknij „Włącz nasłuch EPKK”, aby ponowić połączenie.");
                }
            }
        });
    }

    private void startListening(boolean userAction) {
        if(engine==null)return;
        if(!engineReady) {
            if(userAction) {
                autoplayPending=true;
                loadEngine();
            }
            return;
        }

        if(status!=null)status.setText("Uruchamianie nasłuchu EPKK…");
        String js="(function(){try{"+
                "if(typeof playRadio!=='function')return 'no_function';"+
                "playRadio();return 'called';"+
                "}catch(e){return 'error';}})();";
        engine.evaluateJavascript(js,res -> engine.postDelayed(() -> verifyPlayback(),1200));
    }

    private void verifyPlayback() {
        if(engine==null)return;
        String js="(function(){try{"+
                "var a=document.getElementById('radio-player');"+
                "if(a && !a.paused)return 'playing';"+
                "if(typeof mtPlayer!=='undefined' && mtPlayer && typeof mtPlayer.isPlaying==='function' && mtPlayer.isPlaying())return 'playing';"+
                "return 'paused';"+
                "}catch(e){return 'error';}})();";
        engine.evaluateJavascript(js,res -> {
            String r=res==null?"":res.replace("\"","");
            if(r.contains("playing")) {
                if(status!=null)status.setText("🔴 Nasłuch EPKK włączony");
                if(playButton!=null)playButton.setText("▶ Nasłuch działa");
            } else {
                if(status!=null)status.setText("Połączenie gotowe, ale dźwięk nie wystartował. Dotknij „Włącz nasłuch EPKK”.");
                if(playButton!=null)playButton.setText("▶ Włącz nasłuch EPKK");
            }
        });
    }

    private void stopListening() {
        if(engine==null)return;
        String js="(function(){try{"+
                "if(typeof mtPlayer!=='undefined' && mtPlayer && typeof mtPlayer.stop==='function')mtPlayer.stop();"+
                "var a=document.getElementById('radio-player');if(a){a.pause();a.currentTime=0;}"+
                "return 'stopped';"+
                "}catch(e){return 'error';}})();";
        engine.evaluateJavascript(js,null);
        if(status!=null)status.setText("Nasłuch zatrzymany");
        if(playButton!=null)playButton.setText("▶ Włącz nasłuch EPKK");
    }

    @Override protected void onDestroy() {
        if(engine!=null) {
            try {
                stopListening();
                engine.loadUrl("about:blank");
                engine.stopLoading();
                engine.destroy();
            } catch(Throwable ignored) {}
            engine=null;
        }
        super.onDestroy();
    }
}
