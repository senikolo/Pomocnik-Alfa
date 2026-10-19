package com.ispina.lokalnie;
import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;
/** HLS renditions with original audio groups retained at every resolution. */
public final class PA6HlsQuality {
 private static final Pattern HEIGHT=Pattern.compile("RESOLUTION=[0-9]+x([0-9]+)",Pattern.CASE_INSENSITIVE);
 private static final Pattern URI=Pattern.compile("URI=\"([^\"]+)\"");
 public static final class Variant {
  public final int height;final String metadata,url;
  Variant(int h,String m,String u){height=h;metadata=m;url=u;}
 }
 public static final class Playlist {
  public final String source;public final ArrayList<Variant> variants=new ArrayList<>();
  final ArrayList<String> groups=new ArrayList<>();
  Playlist(String s){source=s;}
  public String[] labels(){
   String[] labels=new String[variants.size()+1];labels[0]="Auto • dopasuj do łącza";
   for(int i=0;i<variants.size();i++)labels[i+1]=variants.get(i).height+"p";
   return labels;
  }
  public String selected(Context context,int preferred)throws IOException {
   if(preferred<=0||variants.isEmpty())return source;
   Variant choice=variants.get(0);
   for(Variant v:variants)if(Math.abs(v.height-preferred)<Math.abs(choice.height-preferred))choice=v;
   if(groups.isEmpty())return choice.url;
   // Local master, remote video and audio; selecting video alone would mute ARD.
   File file=File.createTempFile("pa6-tv-",".m3u8",context.getCacheDir());
   try(Writer out=new OutputStreamWriter(new FileOutputStream(file),"UTF-8")){
    out.write("#EXTM3U\n#EXT-X-VERSION:6\n#EXT-X-INDEPENDENT-SEGMENTS\n");
    for(String line:groups)out.write(line+"\n");
    out.write(choice.metadata+"\n"+choice.url+"\n");
   }
   return Uri.fromFile(file).toString();
  }
 }
 private PA6HlsQuality(){}
 private static String absolute(String base,String line)throws MalformedURLException{
  Matcher m=URI.matcher(line);StringBuffer out=new StringBuffer();
  while(m.find())m.appendReplacement(out,Matcher.quoteReplacement("URI=\""+new URL(new URL(base),m.group(1))+"\""));
  m.appendTail(out);return out.toString();
 }
 static Playlist parse(String source,String body)throws IOException{
  Playlist p=new Playlist(source);
  if(!body.trim().startsWith("#EXTM3U"))throw new IOException("Serwer nie udostępnił transmisji. Spróbuj później.");
  String metadata="";TreeMap<Integer,Variant> unique=new TreeMap<>();
  for(String raw:body.split("\\r?\\n")){
   String line=raw.trim();
   if(line.startsWith("#EXT-X-MEDIA:")){p.groups.add(absolute(source,line));continue;}
   if(line.startsWith("#EXT-X-STREAM-INF:")){metadata=line;continue;}
   if(line.isEmpty()||line.startsWith("#")||metadata.isEmpty())continue;
   String info=metadata;metadata="";Matcher height=HEIGHT.matcher(info);
   if(!height.find()||info.contains("hvc1")||info.contains("hev1")||info.contains("av01"))continue;
   int h=Integer.parseInt(height.group(1));String u=new URL(new URL(source),line).toString();
   if(h>0&&(u.startsWith("https://")||u.startsWith("http://")))unique.put(h,new Variant(h,info,u));
  }
  p.variants.addAll(unique.values());return p;
 }
 public static Playlist inspect(String source)throws Exception{
  if(source==null||(!source.startsWith("https://")&&!source.startsWith("http://")))throw new IOException("Nieprawidłowy adres transmisji.");
  if(source.matches("(?i).*\\.(mp4|mp3|m4a|webm|mkv|mpd)(\\?.*)?"))return new Playlist(source);
  HttpURLConnection c=PA6ModernTls.open(source);
  long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(14);
  try{
   c.setConnectTimeout(6000);c.setReadTimeout(7000);c.setRequestProperty("User-Agent","Mozilla/5.0");
   int code=c.getResponseCode();
   if(code==403||code==451)throw new IOException("Nadawca ograniczył dostęp w Twojej sieci lub kraju.");
   if(code!=200)throw new IOException("Transmisja chwilowo niedostępna ("+code+").");
   ByteArrayOutputStream bytes=new ByteArrayOutputStream();
   try(InputStream in=c.getInputStream()){
    byte[] b=new byte[8192];int n;
    while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Zmieniono kanał.");if(System.nanoTime()>deadline)throw new java.net.SocketTimeoutException("Sprawdzanie transmisji trwa zbyt długo.");if(bytes.size()+n>2000000)throw new IOException("Nieprawidłowa lista transmisji.");bytes.write(b,0,n);}
   }
   return parse(c.getURL().toString(),bytes.toString("UTF-8"));
  }finally{c.disconnect();}
 }
 public static String preferLow(String source){return source;}
}
