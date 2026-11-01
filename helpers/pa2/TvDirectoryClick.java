package com.ispina.lokalnie;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

/** Opens one official channel page inside the TV app window. */
public final class TvDirectoryClick implements View.OnClickListener {
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
        openInAppWindow(context, title, url);
    }

    private static void openInAppWindow(Context context, String title, String url) {
        WebView webView = new WebView(context);
        webView.setBackgroundColor(Color.BLACK);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(false);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        FrameLayout container = new FrameLayout(context);
        container.setBackgroundColor(Color.BLACK);
        container.addView(webView, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ));

        final View[] customView = new View[1];
        final WebChromeClient.CustomViewCallback[] customViewCallback = new WebChromeClient.CustomViewCallback[1];

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    Toast.makeText(context, "Nie udało się załadować transmisji. Spróbuj ponownie później.", Toast.LENGTH_LONG).show();
                }
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView[0] != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView[0] = view;
                customViewCallback[0] = callback;
                webView.setVisibility(View.GONE);
                container.addView(view, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                ));
            }

            @Override
            public void onHideCustomView() {
                if (customView[0] != null) {
                    container.removeView(customView[0]);
                    customView[0] = null;
                }
                webView.setVisibility(View.VISIBLE);
                if (customViewCallback[0] != null) {
                    customViewCallback[0].onCustomViewHidden();
                    customViewCallback[0] = null;
                }
            }
        });

        AlertDialog player = new AlertDialog.Builder(context)
            .setTitle(title + " · na żywo")
            .setView(container)
            .setPositiveButton("Zamknij", null)
            .create();
        player.setOnKeyListener((dialog, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP && webView.canGoBack()) {
                webView.goBack();
                return true;
            }
            return false;
        });
        player.setOnDismissListener(dialog -> {
            webView.stopLoading();
            if (customView[0] != null) {
                container.removeView(customView[0]);
                customView[0] = null;
            }
            if (customViewCallback[0] != null) {
                customViewCallback[0].onCustomViewHidden();
                customViewCallback[0] = null;
            }
            webView.destroy();
        });
        player.show();

        Window window = player.getWindow();
        if (window != null) {
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
            window.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            window.setLayout((int) (screenWidth * 0.96f), (int) (screenHeight * 0.86f));
        }
        webView.loadUrl(url);
    }
}
