package com.ispina.lokalnie;

import android.content.Context;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import javax.net.ssl.*;

/**
 * Android 6 HTTPS compatibility without disabling certificate verification.
 * Trusts the platform's original roots and the bundled Mozilla/certifi public CA set.
 * Default HTTPS hostname verification remains mandatory.
 */
public final class PA6ModernTls {
 private static Context context;
 private static SSLSocketFactory socketFactory;
 private PA6ModernTls(){}
 public static void install(Context app){context=app.getApplicationContext();}
 private static X509TrustManager getTrustManager(KeyStore keyStore)throws Exception{
  TrustManagerFactory f=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
  f.init(keyStore);
  for(TrustManager tm:f.getTrustManagers())
   if(tm instanceof X509TrustManager)return (X509TrustManager)tm;
  throw new GeneralSecurityException("X509 trust manager unavailable");
 }
 private static synchronized SSLSocketFactory factory()throws Exception{
  if(socketFactory!=null)return socketFactory;
  if(context==null)throw new GeneralSecurityException("SSL context not initialized");
  final X509TrustManager system=getTrustManager(null);
  KeyStore roots=KeyStore.getInstance(KeyStore.getDefaultType());roots.load(null,null);
  CertificateFactory parser=CertificateFactory.getInstance("X.509");
  InputStream input=context.getAssets().open("pa6-ca-bundle.pem");
  int count=0;
  try(BufferedReader reader=new BufferedReader(new InputStreamReader(input,"US-ASCII"))){
   String line;StringBuilder block=null;
   while((line=reader.readLine())!=null){
    if(line.startsWith("-----BEGIN CERTIFICATE-----")){
     block=new StringBuilder();block.append(line).append('\n');
    }else if(block!=null){
     block.append(line).append('\n');
     if(line.startsWith("-----END CERTIFICATE-----")){
      java.security.cert.Certificate cert=parser.generateCertificate(
       new ByteArrayInputStream(block.toString().getBytes("US-ASCII")));
      roots.setCertificateEntry("certifi-"+(++count),cert);
      block=null;
     }
    }
   }
  }
  if(count<70)throw new CertificateException("CA root bundle incomplete");
  final X509TrustManager mozilla=getTrustManager(roots);
  X509TrustManager combined=new X509TrustManager(){
   @Override public void checkClientTrusted(X509Certificate[] chain,String auth)throws CertificateException{
    system.checkClientTrusted(chain,auth);
   }
   @Override public void checkServerTrusted(X509Certificate[] chain,String auth)throws CertificateException{
    try{system.checkServerTrusted(chain,auth);}
    catch(CertificateException original){
     try{mozilla.checkServerTrusted(chain,auth);}
     catch(CertificateException alternative){throw original;}
    }
   }
   @Override public X509Certificate[] getAcceptedIssuers(){
    X509Certificate[] a=system.getAcceptedIssuers(),b=mozilla.getAcceptedIssuers();
    X509Certificate[] result=Arrays.copyOf(a,a.length+b.length);
    System.arraycopy(b,0,result,a.length,b.length);return result;
   }
  };
  SSLContext ssl=SSLContext.getInstance("TLS");
  ssl.init(null,new TrustManager[]{combined},new SecureRandom());
  socketFactory=ssl.getSocketFactory();
  return socketFactory;
 }
 public static HttpURLConnection open(String address)throws Exception{
  URL url=new URL(address);
  URLConnection conn=url.openConnection();
  if(!(conn instanceof HttpURLConnection))throw new IOException("Only HTTP or HTTPS permitted");
  if(conn instanceof HttpsURLConnection)
   ((HttpsURLConnection)conn).setSSLSocketFactory(factory());
  return (HttpURLConnection)conn;
 }
}
