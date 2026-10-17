package com.ispina.lokalnie;

import android.content.Context;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Android 6 trust-store compatibility for public HTTPS metadata endpoints.
 * Uses the operating system roots PLUS a current Debian/Ubuntu CA bundle,
 * packaged by the verified build workflow. Certificate paths and hostnames
 * remain validated: do NOT implement trust-all or disable hostname checks.
 */
public final class PA6Https {
 private PA6Https(){}
 private static volatile SSLSocketFactory factory;
 private static volatile String diagnostic="";
 private static X509TrustManager trust(TrustManagerFactory f)throws Exception {
  for(TrustManager manager:f.getTrustManagers())if(manager instanceof X509TrustManager)
   return (X509TrustManager)manager;
  throw new Exception("X509 trust manager missing");
 }
 private static synchronized SSLSocketFactory factory(Context context)throws Exception {
  if(factory!=null)return factory;
  TrustManagerFactory system=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
  system.init((KeyStore)null);
  final X509TrustManager defaultTrust=trust(system);
  KeyStore modern=KeyStore.getInstance(KeyStore.getDefaultType());
  modern.load(null,null);
  int installed=0;
  try(InputStream is=context.getApplicationContext().getAssets().open("pa6-ca-bundle.pem")){
   CertificateFactory cf=CertificateFactory.getInstance("X.509");
   Collection<? extends Certificate> certificates=cf.generateCertificates(is);
   for(Certificate cert:certificates){
    if(!(cert instanceof X509Certificate))continue;
    X509Certificate x=(X509Certificate)cert;
    try{x.checkValidity();}catch(CertificateException expired){continue;}
    modern.setCertificateEntry("modern-ca-"+(++installed),x);
   }
  }
  if(installed<25)throw new IOException("Niepełny magazyn certyfikatów");
  TrustManagerFactory extra=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
  extra.init(modern);
  final X509TrustManager modernTrust=trust(extra);
  X509TrustManager combined=new X509TrustManager(){
   @Override public void checkClientTrusted(X509Certificate[] chain,String authType)throws CertificateException {
    defaultTrust.checkClientTrusted(chain,authType);
   }
   @Override public void checkServerTrusted(X509Certificate[] chain,String authType)throws CertificateException {
    try{defaultTrust.checkServerTrusted(chain,authType);}
    catch(CertificateException systemFailure){modernTrust.checkServerTrusted(chain,authType);}
   }
   @Override public X509Certificate[] getAcceptedIssuers(){
    X509Certificate[] one=defaultTrust.getAcceptedIssuers(),two=modernTrust.getAcceptedIssuers();
    X509Certificate[] both=new X509Certificate[one.length+two.length];
    System.arraycopy(one,0,both,0,one.length);System.arraycopy(two,0,both,one.length,two.length);
    return both;
   }
  };
  SSLContext contextTls=SSLContext.getInstance("TLS");
  contextTls.init(null,new TrustManager[]{combined},new SecureRandom());
  factory=contextTls.getSocketFactory();
  diagnostic="Dodatkowe certyfikaty urzędów: "+installed;
  return factory;
 }
 public static HttpURLConnection open(Context context,String uri)throws IOException {
  URL url=new URL(uri);
  String protocol=url.getProtocol();
  if(!"https".equalsIgnoreCase(protocol)&&!"http".equalsIgnoreCase(protocol))
   throw new IOException("Nieobsługiwany protokół");
  HttpURLConnection c=(HttpURLConnection)url.openConnection();
  if(c instanceof HttpsURLConnection)try{
   ((HttpsURLConnection)c).setSSLSocketFactory(factory(context));
   // Keep the platform's strict hostname verifier unchanged.
  }catch(Exception e){c.disconnect();throw new IOException("Nie mogę sprawdzić certyfikatu HTTPS",e);}
  return c;
 }
 public static String diagnostic(){return diagnostic;}
}
