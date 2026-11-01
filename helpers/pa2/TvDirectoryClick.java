package com.ispina.lokalnie;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Looper;
import android.view.View;
import android.view.ViewParent;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Opens an official TV page inside the same inline frame used by the built-in TV player. */
public final class TvDirectoryClick implements View.OnClickListener {
    private static final String TAG_FULLSCREEN = "il-tv-web-fullscreen";
    private static final Map<WebView, FullScreenState> FULLSCREEN =
        Collections.synchronizedMap(new WeakHashMap<WebView, FullScreenState>());

    private static final String FIT_SCRIPT =
        "(function(){try{" +
        "var d=document.documentElement,b=document.body;" +
        "d.style.setProperty('margin','0','important');" +
        "d.style.setProperty('width','100%','important');" +
        "d.style.setProperty('height','100%','important');" +
        "d.style.setProperty('overflow','hidden','important');" +
        "d.style.setProperty('background','#000','important');" +
        "if(!b)return;" +
        "b.style.setProperty('margin','0','important');" +
        "b.style.setProperty('width','100%','important');" +
        "b.style.setProperty('height','100%','important');" +
        "b.style.setProperty('overflow','hidden','important');" +
        "b.style.setProperty('background','#000','important');" +
        "var a=Array.prototype.slice.call(document.querySelectorAll('video,iframe'));" +
        "a=a.filter(function(e){var r=e.getBoundingClientRect(),s=String(e.src||'')+' '+String(e.className||'');" +
        "return r.width>80&&r.height>45&&(e.tagName==='VIDEO'||/youtube|player|video|live/i.test(s));});" +
        "a.sort(function(x,y){var rx=x.getBoundingClientRect(),ry=y.getBoundingClientRect();" +
        "var sx=rx.width*rx.height+(x.tagName==='VIDEO'?1000000:0);" +
        "var sy=ry.width*ry.height+(y.tagName==='VIDEO'?1000000:0);return sy-sx;});" +
        "var e=a[0];if(!e)return;" +
        "var keep=[],p=e;while(p&&p!==b){keep.push(p);p=p.parentElement;}" +
        "Array.prototype.slice.call(b.querySelectorAll('*')).forEach(function(x){" +
        "if(x!==e&&keep.indexOf(x)<0&&!e.contains(x))x.style.setProperty('display','none','important');});" +
        "p=e;while(p&&p!==b){p.style.setProperty('overflow','visible','important');" +
        "p.style.setProperty('max-width','none','important');p=p.parentElement;}" +
        "e.style.setProperty('position','fixed','important');" +
        "e.style.setProperty('left','0','important');e.style.setProperty('top','0','important');" +
        "e.style.setProperty('width','100vw','important');e.style.setProperty('height','100vh','important');" +
        "e.style.setProperty('max-width','none','important');e.style.setProperty('max-height','none','important');" +
        "e.style.setProperty('z-index','2147483647','important');e.style.setProperty('border','0','important');" +
        "if(e.tagName==='VIDEO'){e.controls=true;e.playsInline=true;var q=e.play();if(q&&q.catch)q.catch(function(){});}" +
        "}catch(x){}})();";

    private final Context context;
    private final String title;
    private final String url;

    public TvDirectoryClick(Context context, String title, String url) {
        this.context = context;
        this.title = title;
        this.url = url;
    }

    @Override
    public void onClick(View view) {
        openInlinePlayer(context, title, url);
    }

    /** Called by patched TvActivity entry points before any native station starts. */
    public static void clearInlinePlayer(Context context) {
        if (!(context instanceof Activity)) return;
        final Activity activity = (Activity) context;
        Runnable task = new Runnable() {
            @Override public void run() { clearInlinePlayerNow(activity); }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) task.run();
        else activity.runOnUiThread(task);
    }

    private static void openInlinePlayer(Context context, final String title, final String url) {
        if (!(context instanceof Activity)) {
            Toast.makeText(context, "Nie można otworzyć odtwarzacza Telewizji.", Toast.LENGTH_LONG).show();
            return;
        }
        final Activity activity = (Activity) context;
        Runnable task = new Runnable() {
            @Override public void run() {
                try {
                    clearInlinePlayerNow(activity);
                    final FrameLayout frame = (FrameLayout) readField(activity, "inlineFrame");
                    final View playerView = (View) readField(activity, "playerView");
                    if (frame == null || playerView == null) throw new IllegalStateException("Brak okna odtwarzacza");

                    Object player = readField(activity, "player");
                    if (player != null) {
                        try { player.getClass().getMethod("pause").invoke(player); }
                        catch (Exception ignored) { }
                    }

                    TextView currentChannel = (TextView) readField(activity, "currentChannel");
                    if (currentChannel != null) currentChannel.setText("📺  " + title);
                    setStatus(activity, "Otwieram transmisję " + title + "…", true);

                    playerView.setVisibility(View.GONE);
                    final WebView webView = new WebView(activity);
                    webView.setTag("il-tv-inline-player");
                    webView.setBackgroundColor(Color.BLACK);
                    WebSettings settings = webView.getSettings();
                    settings.setJavaScriptEnabled(true);
                    settings.setDomStorageEnabled(true);
                    settings.setMediaPlaybackRequiresUserGesture(false);
                    settings.setLoadWithOverviewMode(false);
                    settings.setUseWideViewPort(true);
                    settings.setSupportZoom(false);
                    CookieManager cookies = CookieManager.getInstance();
                    cookies.setAcceptCookie(true);
                    cookies.setAcceptThirdPartyCookies(webView, true);

                    final FullScreenState fullscreen = new FullScreenState();
                    FULLSCREEN.put(webView, fullscreen);

                    webView.setWebViewClient(new WebViewClient() {
                        @Override
                        public void onPageFinished(WebView view, String pageUrl) {
                            fitPlayer(view);
                            view.postDelayed(new Runnable() {
                                @Override public void run() { fitPlayer(view); }
                            }, 1200);
                            view.postDelayed(new Runnable() {
                                @Override public void run() { fitPlayer(view); }
                            }, 3500);
                            setStatus(activity, title + " otwarta w oknie Telewizji.", false);
                        }

                        @Override
                        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                            if (request.isForMainFrame()) {
                                setStatus(activity, "Nie udało się załadować transmisji " + title + ".", false);
                            }
                        }
                    });

                    webView.setWebChromeClient(new WebChromeClient() {
                        @Override
                        public void onShowCustomView(View customView, CustomViewCallback callback) {
                            if (fullscreen.view != null) {
                                callback.onCustomViewHidden();
                                return;
                            }
                            fullscreen.view = customView;
                            fullscreen.callback = callback;
                            customView.setTag(TAG_FULLSCREEN);
                            webView.setVisibility(View.GONE);
                            frame.addView(customView, new FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            ));
                        }

                        @Override
                        public void onHideCustomView() {
                            View customView = fullscreen.view;
                            CustomViewCallback callback = fullscreen.callback;
                            fullscreen.view = null;
                            fullscreen.callback = null;
                            if (customView != null) {
                                ViewParent parent = customView.getParent();
                                if (parent instanceof FrameLayout) ((FrameLayout) parent).removeView(customView);
                            }
                            webView.setVisibility(View.VISIBLE);
                            if (callback != null) callback.onCustomViewHidden();
                        }
                    });

                    frame.addView(webView, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    ));
                    webView.loadUrl(url);
                } catch (Exception error) {
                    setStatus(activity, "Nie udało się otworzyć stacji w oknie Telewizji.", false);
                    Toast.makeText(activity, "Nie udało się otworzyć transmisji.", Toast.LENGTH_LONG).show();
                }
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) task.run();
        else activity.runOnUiThread(task);
    }

    private static void clearInlinePlayerNow(Activity activity) {
        try {
            FrameLayout frame = (FrameLayout) readField(activity, "inlineFrame");
            View playerView = (View) readField(activity, "playerView");
            if (frame == null) return;
            for (int i = frame.getChildCount() - 1; i >= 0; i--) {
                View child = frame.getChildAt(i);
                if (child instanceof WebView) {
                    WebView webView = (WebView) child;
                    FullScreenState state = FULLSCREEN.remove(webView);
                    if (state != null) {
                        View fullscreenView = state.view;
                        WebChromeClient.CustomViewCallback callback = state.callback;
                        state.view = null;
                        state.callback = null;
                        if (fullscreenView != null) {
                            ViewParent parent = fullscreenView.getParent();
                            if (parent instanceof FrameLayout) ((FrameLayout) parent).removeView(fullscreenView);
                        }
                        if (callback != null) {
                            try { callback.onCustomViewHidden(); } catch (Exception ignored) { }
                        }
                    }
                    webView.stopLoading();
                    frame.removeView(webView);
                    webView.destroy();
                } else if (TAG_FULLSCREEN.equals(child.getTag())) {
                    frame.removeViewAt(i);
                }
            }
            if (playerView != null) playerView.setVisibility(View.VISIBLE);
        } catch (Exception ignored) { }
    }

    private static void fitPlayer(WebView view) {
        if (view == null || view.getParent() == null) return;
        try { view.evaluateJavascript(FIT_SCRIPT, null); }
        catch (Exception ignored) { }
    }

    private static Object readField(Activity activity, String name) throws Exception {
        Field field = activity.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(activity);
    }

    private static void setStatus(Activity activity, String text, boolean loading) {
        try {
            Method method = activity.getClass().getDeclaredMethod("setStatus", String.class, boolean.class);
            method.setAccessible(true);
            method.invoke(activity, text, loading);
        } catch (Exception ignored) { }
    }

    private static final class FullScreenState {
        View view;
        WebChromeClient.CustomViewCallback callback;
    }
}
