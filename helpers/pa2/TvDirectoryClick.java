package com.ispina.lokalnie;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
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

/** Opens official Polish channel sources, keeping TV Republika inside the app. */
public final class TvDirectoryClick implements View.OnClickListener {
    private static final String REPUBLIKA_URL = "https://tvrepublika.pl/live";
    private final Context context;

    public TvDirectoryClick(Context context) {
        this.context = context;
    }

    @Override
    public void onClick(View view) {
        new AlertDialog.Builder(context)
            .setTitle("Polskie kanały online")
            .setMessage("TV Republika otworzy się w oknie Pomocnika Alfa. wPolsce24 i Polsat Box Go prowadzą do oficjalnych serwisów. Dostępność może zależeć od pakietu i logowania.")
            .setPositiveButton("Republika", new OpenUrl(context, REPUBLIKA_URL))
            .setNeutralButton("wPolsce24", new OpenUrl(context, "https://wpolsce24.tv/video"))
            .setNegativeButton("Polsat Box Go", new OpenUrl(context, "https://polsatboxgo.pl/kanaly-tv?epg=true"))
            .show();
    }

    private static final class OpenUrl implements DialogInterface.OnClickListener {
        private final Context context;
        private final String url;

        OpenUrl(Context context, String url) {
            this.context = context;
            this.url = url;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (REPUBLIKA_URL.equals(url)) {
                openRepublikaWindow(context);
                return;
            }
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(context, "Nie znaleziono przeglądarki internetowej.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private static void openRepublikaWindow(Context context) {
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
            .setTitle("TV Republika · na żywo")
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
        webView.loadUrl(REPUBLIKA_URL);
    }
}
