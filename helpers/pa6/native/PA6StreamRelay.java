package com.ispina.lokalnie;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** Session-local transport: Java verifies remote HTTPS; VLC reads loopback HTTP.
 * No remote listener, arbitrary URL parameter, persistent media cache or TLS bypass.
 */
final class PA6StreamRelay implements Closeable {
 interface Connector { HttpURLConnection open(String url)throws Exception; }
 private static final Pattern URI=Pattern.compile("URI=\"([^\"]+)\"");
 private final Connector connector;
 private final ServerSocket server;
 private final String prefix;
 private volatile boolean closed;
 private final Set<Socket> sockets=Collections.synchronizedSet(new HashSet<Socket>());
 private final Set<HttpURLConnection> upstream=Collections.synchronizedSet(new HashSet<HttpURLConnection>());
 private final LinkedHashMap<String,String> routes=new LinkedHashMap<String,String>(32,.75f,true){
  protected boolean removeEldestEntry(Map.Entry<String,String> entry){return size()>2048;}
 };
 private long nextRoute;
 private final Map<String,byte[]> localPlaylists=new HashMap<>();
 private volatile String lastFailure="";
 String lastError(){return lastFailure;}
 private final ThreadPoolExecutor workers=new ThreadPoolExecutor(4,6,15,TimeUnit.SECONDS,
  new ArrayBlockingQueue<Runnable>(12),r->{Thread t=new Thread(r,"pa6-stream-http");t.setDaemon(true);return t;});
 PA6StreamRelay(Connector transport)throws IOException {
  connector=transport;server=new ServerSocket();
  server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),0),8);
  prefix="/"+UUID.randomUUID().toString()+"/";
  Thread accept=new Thread(()->acceptLoop(),"pa6-stream-accept");accept.setDaemon(true);accept.start();
 }
 synchronized String route(String address)throws IOException {
  if(closed)throw new IOException("Transmisja została zatrzymana.");
  URL url=new URL(address);
  if(!"http".equals(url.getProtocol())&&!"https".equals(url.getProtocol()))throw new IOException("Niedozwolony adres transmisji.");
  String key=null;
  for(Map.Entry<String,String> entry:routes.entrySet())if(entry.getValue().equals(address)){key=entry.getKey();break;}
  if(key==null){key=Long.toString(++nextRoute,36);routes.put(key,address);}else routes.get(key);
  // Retain the suffix: VLC uses it when the broadcaster omits Content-Type.
  String path=url.getPath();int dot=path.lastIndexOf('.');String suffix=dot<0?"":path.substring(dot);
  if(!suffix.matches("\\.[A-Za-z0-9]{1,6}"))suffix="";
  return "http://127.0.0.1:"+server.getLocalPort()+prefix+key+suffix;
 }
 synchronized String playlist(String body)throws IOException {
  String key=Long.toString(++nextRoute,36);
  byte[] bytes=rewrite("https://localhost/",body).getBytes("UTF-8");
  localPlaylists.put(key,bytes);
  return "http://127.0.0.1:"+server.getLocalPort()+prefix+key+".m3u8";
 }
 private void acceptLoop(){
  while(!closed)try{
   final Socket socket=server.accept();socket.setSoTimeout(12000);sockets.add(socket);
   try{workers.execute(()->serve(socket));}catch(RejectedExecutionException full){sockets.remove(socket);socket.close();}
  }catch(IOException e){if(!closed)close();}
 }
 private static String line(InputStream input)throws IOException {
  ByteArrayOutputStream out=new ByteArrayOutputStream();int c;
  while((c=input.read())!=-1){if(c=='\n')break;if(c!='\r')out.write(c);if(out.size()>8192)throw new IOException("HTTP line too long");}
  return out.toString("ISO-8859-1");
 }
 private static void head(OutputStream out,int status,String contentType,long size,Map<String,String> headers)throws IOException {
  StringBuilder h=new StringBuilder("HTTP/1.1 ").append(status).append(status==200?" OK\r\n":" Response\r\n");
  h.append("Connection: close\r\nCache-Control: no-store\r\n");
  if(contentType!=null&&!contentType.contains("\r")&&!contentType.contains("\n"))h.append("Content-Type: ").append(contentType).append("\r\n");
  if(size>=0)h.append("Content-Length: ").append(size).append("\r\n");
  for(Map.Entry<String,String> entry:headers.entrySet())if(entry.getValue()!=null&&!entry.getValue().contains("\r")&&!entry.getValue().contains("\n"))h.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
  out.write(h.append("\r\n").toString().getBytes("ISO-8859-1"));out.flush();
 }
 private void serve(Socket socket){
  HttpURLConnection connection=null;boolean committed=false;
  try{
   Socket client=socket;
   BufferedInputStream input=new BufferedInputStream(client.getInputStream());OutputStream output=client.getOutputStream();
   String[] request=line(input).split(" ");
   if(request.length!=3||(!"GET".equals(request[0])&&!"HEAD".equals(request[0]))){head(output,405,"text/plain",0,Collections.emptyMap());return;}
   String target=request[1];
   if(!target.startsWith(prefix)){head(output,404,"text/plain",0,Collections.emptyMap());return;}
   String key=target.substring(prefix.length());int dot=key.indexOf('.');if(dot>=0)key=key.substring(0,dot);
   String remote;byte[] local;synchronized(this){remote=routes.get(key);local=localPlaylists.get(key);}
   if(local!=null){head(output,200,"application/vnd.apple.mpegurl",local.length,Collections.emptyMap());committed=true;if(!"HEAD".equals(request[0]))output.write(local);return;}
   if(remote==null){head(output,404,"text/plain",0,Collections.emptyMap());return;}
   String range=null;
   for(int n=0;n<40;n++){String header=line(input);if(header.isEmpty())break;int colon=header.indexOf(':');if(colon>0&&header.substring(0,colon).equalsIgnoreCase("Range"))range=header.substring(colon+1).trim();if(n==39)throw new IOException("Too many headers");}
   if(range!=null&&!range.matches("bytes=[0-9]*-[0-9]*")){head(output,416,"text/plain",0,Collections.emptyMap());return;}
   for(int redirects=0;redirects<=5;redirects++){
    if(closed)return;
    connection=connector.open(remote);upstream.add(connection);
    connection.setConnectTimeout(6500);connection.setReadTimeout(9000);connection.setInstanceFollowRedirects(false);
    connection.setRequestProperty("User-Agent","Mozilla/5.0");connection.setRequestProperty("Accept-Encoding","identity");
    if(range!=null)connection.setRequestProperty("Range",range);
    if("HEAD".equals(request[0]))connection.setRequestMethod("HEAD");
    int status=connection.getResponseCode();
    if(status>=400)lastFailure="HTTP "+status+" • "+new URL(remote).getHost();
    if(status==301||status==302||status==303||status==307||status==308){
     String location=connection.getHeaderField("Location");
     if(location==null||redirects==5)throw new IOException("Nieprawidłowe przekierowanie transmisji.");
     String next=new URL(new URL(remote),location).toString();
     if(!next.startsWith("http://")&&!next.startsWith("https://"))throw new IOException("Niedozwolone przekierowanie.");
     upstream.remove(connection);connection.disconnect();connection=null;remote=next;continue;
    }
    String type=connection.getContentType();String path=new URL(remote).getPath().toLowerCase(Locale.ROOT);
    boolean playlist=path.endsWith(".m3u8")||(type!=null&&(type.toLowerCase(Locale.ROOT).contains("mpegurl")));
    if(status==200&&playlist&&!"HEAD".equals(request[0])){
     byte[] body;
     long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
     try(InputStream stream=connection.getInputStream()){
      InputStream decoded="gzip".equalsIgnoreCase(connection.getContentEncoding())?new java.util.zip.GZIPInputStream(stream):stream;
      ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
      while((count=decoded.read(buffer))!=-1){if(closed)return;if(System.nanoTime()>deadline)throw new SocketTimeoutException("Lista transmisji nie odpowiada.");if(bytes.size()+count>2000000)throw new IOException("Lista transmisji jest za duża.");bytes.write(buffer,0,count);}body=bytes.toByteArray();
     }
     byte[] rewritten=rewrite(remote,new String(body,"UTF-8")).getBytes("UTF-8");
     head(output,200,"application/vnd.apple.mpegurl",rewritten.length,Collections.emptyMap());committed=true;output.write(rewritten);
    }else{
     Map<String,String> headers=new LinkedHashMap<>();
     for(String name:new String[]{"Content-Range","Accept-Ranges","Content-Encoding"}){String value=connection.getHeaderField(name);if(value!=null)headers.put(name,value);}
     long size=-1;try{size=Long.parseLong(connection.getHeaderField("Content-Length"));}catch(Exception ignored){}
     head(output,status,type,size,headers);committed=true;
     if(!"HEAD".equals(request[0])){
      InputStream stream=status>=400?connection.getErrorStream():connection.getInputStream();
      if(stream!=null)try(InputStream data=stream){byte[] buffer=new byte[32768];int count;while(!closed&&(count=data.read(buffer))!=-1)output.write(buffer,0,count);}
     }
    }
    output.flush();return;
   }
  }catch(Exception error){
   if(!closed)lastFailure=error.getClass().getSimpleName();
   // A closed player cancels its sockets. Never send a second HTTP status after media bytes.
   if(!closed&&!committed)try{head(socket.getOutputStream(),502,"text/plain",0,Collections.emptyMap());}catch(IOException ignored){}
  }finally{try{socket.close();}catch(IOException ignored){}if(connection!=null){upstream.remove(connection);connection.disconnect();}sockets.remove(socket);}
 }
 /**
  * Das Erste's live HLS master can advertise an unsupported external subtitles
  * rendition (mastersubs.m3u8). On legacy decoders this optional track can abort
  * the entire live programme. Suppress only this optional rendition; keep AUDIO
  * groups and video variants exactly as offered by the broadcaster.
  *
  * Apply both before selecting a fixed-quality variant and when relaying
  * the automatic-quality master. Never change unrelated channels.
  */
 static String normalizeLegacyArdMaster(String source,String playlist) {
  try {
   if(!"daserste-live.ard-mcdn.de".equalsIgnoreCase(new URL(source).getHost())
       ||!playlist.contains("#EXT-X-MEDIA:TYPE=SUBTITLES"))return playlist;
  }catch(Exception invalid){return playlist;}
  StringBuilder fixed=new StringBuilder(playlist.length());
  for(String line:playlist.split("\\r?\\n",-1)){
   if(line.startsWith("#EXT-X-MEDIA:TYPE=SUBTITLES"))continue;
   if(line.startsWith("#EXT-X-STREAM-INF:")){
    // Remove references to a non-existent subtitles group, preserving AUDIO.
    line=line.replaceAll(",SUBTITLES=\\\"[^\\\"]*\\\"","")
             .replaceAll("SUBTITLES=\\\"[^\\\"]*\\\",","");
   }
   fixed.append(line).append('\\n');
  }
  return fixed.toString();
 }
 String rewrite(String source,String body)throws IOException {
  if(!body.trim().startsWith("#EXTM3U"))throw new IOException("Nieprawidłowa lista transmisji.");
  body=normalizeLegacyArdMaster(source,body);
  StringBuilder result=new StringBuilder(body.length()+512);URL base=new URL(source);
  for(String raw:body.split("\\r?\\n")){
   String text=raw.trim();
   if(text.startsWith("#")){
    Matcher matcher=URI.matcher(text);StringBuffer replaced=new StringBuffer();
    while(matcher.find())matcher.appendReplacement(replaced,Matcher.quoteReplacement("URI=\""+route(new URL(base,matcher.group(1)).toString())+"\""));
    matcher.appendTail(replaced);result.append(replaced);
   }else if(!text.isEmpty())result.append(route(new URL(base,text).toString()));
   result.append('\n');
  }
  return result.toString();
 }
 @Override public void close(){
  if(closed)return;closed=true;try{server.close();}catch(IOException ignored){}
  synchronized(sockets){for(Socket socket:sockets)try{socket.close();}catch(IOException ignored){}sockets.clear();}
  workers.shutdownNow();
  // disconnect can wait on a provider lock. Keep even cancellation off Android's UI thread.
  Thread cleanup=new Thread(()->{synchronized(upstream){for(HttpURLConnection c:upstream)c.disconnect();upstream.clear();}},"pa6-stream-close");cleanup.setDaemon(true);cleanup.start();
  synchronized(this){routes.clear();localPlaylists.clear();}
 }
}
