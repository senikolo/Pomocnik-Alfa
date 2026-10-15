package com.ispina.lokalnie;

import android.app.Activity;
import android.os.Bundle;
import android.net.Uri;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import java.io.IOException;
import java.io.InputStream;

/** Isolated, offline-bundled copy of IL 52 transit UI inside Pomocnik Alfa.
 *  Only two known URLs are replaced by bundled content; public feeds retain HTTPS.
 */
public final class ILBusPlannerActivity extends Activity {
    private WebView browser;
    private static final String HOST = "ispinalokalnie.com";
    private static final String SHELL = "/__pa_transit/shell.html";
    private static final String SCRIPT = "/__pa_transit/main_ui.js";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        browser = new WebView(this);
        WebSettings settings = browser.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        browser.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                if (!"https".equals(url.getScheme()) || !HOST.equals(url.getHost())) return null;
                String path = url.getPath();
                String asset;
                String mime;
                if (SHELL.equals(path)) { asset = "pa_il52_shell.html"; mime="text/html"; }
                else if (SCRIPT.equals(path)) { asset = "pa_il52_main_ui.js"; mime="application/javascript"; }
                else return null;
                try {
                    InputStream input = getAssets().open(asset);
                    return new WebResourceResponse(mime, "UTF-8", input);
                } catch (IOException e) { return null; }
            }
        });
        setContentView(browser);
        browser.loadUrl("https://" + HOST + SHELL);
    }

    @Override public void onBackPressed() {
        if (browser != null && browser.canGoBack()) browser.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (browser != null) {
            browser.stopLoading();
            browser.destroy();
            browser = null;
        }
        super.onDestroy();
    }
}
