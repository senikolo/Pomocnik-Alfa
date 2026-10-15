package com.ispina.lokalnie;
import android.app.Activity;
import android.os.Bundle;
import android.net.Uri;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebChromeClient;
import java.io.InputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.util.Collections;

/** Separate in-app route planner with actual Warsaw and MLD schedule indexes. */
public final class PARegionsActivity extends Activity {
    private WebView web;
    private static final String HOST = "pa-bus.invalid";
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        web=new WebView(this);
        web.setBackgroundColor(0xfff3f7fc);
        WebSettings s=web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                Uri uri=request.getUrl();
                if(!"https".equals(uri.getScheme()) || !HOST.equals(uri.getHost()))return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
                String name=uri.getPath();
                if(name==null || name.length()<2)return error();
                name=name.substring(1);
                if(!(name.equals("app.html")||name.equals("app.js")
                    ||name.matches("index_(warsaw|mld)\\.json")
                    ||name.matches("route_(warsaw|mld)_[0-9]+\\.json"))) return error();
                String mime=name.endsWith(".html")?"text/html":name.endsWith(".js")?"application/javascript":"application/json";
                try{
                    InputStream input=getAssets().open("pa_regions/"+name);
                    return new WebResourceResponse(mime,"UTF-8",input);
                }catch(IOException e){return error();}
            }
            private WebResourceResponse error(){
                return new WebResourceResponse("text/plain","UTF-8",404,
                    "Not Found",Collections.<String,String>emptyMap(),
                    new ByteArrayInputStream("Brak danych rozkladu".getBytes()));
            }
        });
        setContentView(web);
        web.loadUrl("https://"+HOST+"/app.html");
    }
    @Override public void onBackPressed(){
        if(web!=null&&web.canGoBack())web.goBack();else super.onBackPressed();
    }
    @Override public void onDestroy(){
        if(web!=null){web.stopLoading();web.destroy();web=null;}
        super.onDestroy();
    }
}
