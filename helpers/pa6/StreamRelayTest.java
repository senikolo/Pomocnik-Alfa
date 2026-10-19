package com.ispina.lokalnie;

import com.sun.net.httpserver.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** JVM integration test of the exact production HTTP/HLS transport. */
public final class StreamRelayTest {
 private static int checks;
 private static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
 private static byte[] read(InputStream input)throws IOException{try(InputStream in=input){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);return out.toByteArray();}}
 private static HttpURLConnection open(String url)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(2000);c.setReadTimeout(3000);return c;}
 private static String fetch(String url)throws Exception{HttpURLConnection c=open(url);try{return new String(read(c.getInputStream()),StandardCharsets.UTF_8);}finally{c.disconnect();}}
 private static void send(HttpExchange exchange,int code,String type,byte[] bytes)throws IOException{exchange.getResponseHeaders().set("Content-Type",type);if("HEAD".equals(exchange.getRequestMethod())){exchange.getResponseHeaders().set("Content-Length",Integer.toString(bytes.length));exchange.sendResponseHeaders(code,-1);}else{exchange.sendResponseHeaders(code,bytes.length);exchange.getResponseBody().write(bytes);}exchange.close();}
 public static void main(String[] args)throws Exception {
  HttpServer origin=HttpServer.create(new InetSocketAddress("127.0.0.1",0),8);
  ExecutorService serving=Executors.newCachedThreadPool();origin.setExecutor(serving);
  List<String> paths=Collections.synchronizedList(new ArrayList<String>());
  byte[] media=new byte[180000];new Random(243).nextBytes(media);
  CountDownLatch stalled=new CountDownLatch(1),resume=new CountDownLatch(1);
  origin.createContext("/",exchange->{try{
   String path=exchange.getRequestURI().getPath();paths.add(exchange.getRequestURI().toString());
   if(path.equals("/live/master.m3u8"))send(exchange,200,"application/vnd.apple.mpegurl",("#EXTM3U\n#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"aac\",NAME=\"Deutsch\",DEFAULT=YES,URI=\"audio.m3u8?sig=a%2Fb\"\n#EXT-X-STREAM-INF:BANDWIDTH=600000,RESOLUTION=640x360,AUDIO=\"aac\"\nvideo/low.m3u8?sig=abc&x=1\n").getBytes(StandardCharsets.UTF_8));
   else if(path.endsWith("/low.m3u8")||path.endsWith("/audio.m3u8"))send(exchange,200,"application/vnd.apple.mpegurl",("#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXT-X-KEY:METHOD=AES-128,URI=\"/key.bin\"\n#EXTINF:4,\n/segment.ts\n#EXT-X-ENDLIST\n").getBytes(StandardCharsets.UTF_8));
   else if(path.equals("/redirect.m3u8")){exchange.getResponseHeaders().set("Location","/live/master.m3u8");exchange.sendResponseHeaders(302,-1);exchange.close();}
   else if(path.equals("/fail"))send(exchange,403,"text/plain","forbidden".getBytes(StandardCharsets.UTF_8));
   else if(path.equals("/slow.ts")){stalled.countDown();resume.await(10,TimeUnit.SECONDS);send(exchange,200,"video/mp2t",media);}
   else if(path.equals("/gzip.m3u8")){ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(java.util.zip.GZIPOutputStream gzip=new java.util.zip.GZIPOutputStream(bytes)){gzip.write("#EXTM3U\n#EXTINF:4,\n/segment.ts\n".getBytes(StandardCharsets.UTF_8));}exchange.getResponseHeaders().set("Content-Encoding","gzip");send(exchange,200,"application/vnd.apple.mpegurl",bytes.toByteArray());}
   else if(path.equals("/segment.ts")){
    String range=exchange.getRequestHeaders().getFirst("Range");exchange.getResponseHeaders().set("Accept-Ranges","bytes");
    if("bytes=10-29".equals(range)){exchange.getResponseHeaders().set("Content-Range","bytes 10-29/"+media.length);send(exchange,206,"video/mp2t",Arrays.copyOfRange(media,10,30));}
    else send(exchange,200,"video/mp2t",media);
   }else if(path.equals("/key.bin"))send(exchange,200,"application/octet-stream",new byte[16]);
   else send(exchange,404,"text/plain",new byte[0]);
  }catch(Exception e){exchange.close();}});
  origin.start();
  PA6StreamRelay relay=new PA6StreamRelay(address->{URL target=new URL(address);return (HttpURLConnection)new URL("http://127.0.0.1:"+origin.getAddress().getPort()+target.getFile()).openConnection();});
  try{
   String master=fetch(relay.route("https://broadcaster.test/redirect.m3u8"));
   check(master.contains("RESOLUTION=640x360")&&master.contains("AUDIO=\"aac\""),"master retains resolution and separate audio group");
   check(!master.contains("broadcaster.test")&&!master.contains("URI=\"audio"),"all remote HLS references use loopback");
   Matcher audio=Pattern.compile("URI=\"([^\"]+)\"").matcher(master);check(audio.find(),"audio playlist routed");
   String audioList=fetch(audio.group(1));check(audioList.contains("#EXT-X-KEY:"),"audio playlist and key preserved");
   check(paths.contains("/live/audio.m3u8?sig=a%2Fb"),"redirect base and signed query preserved");
   String variant="";for(String line:master.split("\n"))if(line.startsWith("http"))variant=line;
   String segments=fetch(variant);check(paths.contains("/live/video/low.m3u8?sig=abc&x=1"),"nested video playlist and query fetched");
   Matcher key=Pattern.compile("URI=\"([^\"]+)\"").matcher(segments);check(key.find()&&read(new URL(key.group(1)).openStream()).length==16,"AES key relayed byte-for-byte");
   String segment="";for(String line:segments.split("\n"))if(line.startsWith("http"))segment=line;
   check(Arrays.equals(media,read(new URL(segment).openStream())),"binary segment relayed without truncation");
   HttpURLConnection partial=open(segment);partial.setRequestProperty("Range","bytes=10-29");
   check(partial.getResponseCode()==206&&("bytes 10-29/"+media.length).equals(partial.getHeaderField("Content-Range")),"partial HTTP status and Content-Range forwarded");
   check(Arrays.equals(Arrays.copyOfRange(media,10,30),read(partial.getInputStream())),"byte range payload preserved");partial.disconnect();
   HttpURLConnection head=open(segment);head.setRequestMethod("HEAD");check(head.getResponseCode()==200&&head.getContentLength()==media.length&&read(head.getInputStream()).length==0,"HEAD has length and no response body");head.disconnect();
   HttpURLConnection forbidden=open(relay.route("https://broadcaster.test/fail"));check(forbidden.getResponseCode()==403,"origin access denial never reported as successful media");forbidden.disconnect();
   check(relay.lastError().contains("403")&&relay.lastError().contains("broadcaster.test"),"safe diagnostic retains HTTP status and hostname");
   HttpURLConnection invalid=open(segment.replaceFirst("/[0-9a-f-]{36}/","/invalid-token/"));check(invalid.getResponseCode()==404,"unregistered session token rejected");invalid.disconnect();
   check(fetch(relay.playlist("#EXTM3U\n#EXT-X-MEDIA:TYPE=AUDIO,URI=\"https://broadcaster.test/live/audio.m3u8\"\n#EXT-X-STREAM-INF:BANDWIDTH=1\nhttps://broadcaster.test/live/video/low.m3u8\n")).contains("#EXT-X-MEDIA:"),"local quality master retains and relays separate audio");
   check(fetch(relay.route("https://broadcaster.test/gzip.m3u8")).contains("http://127.0.0.1:"),"gzip playlist decoded before rewriting");
   try{relay.route("file:///etc/passwd");throw new AssertionError("local file");}catch(IOException expected){check(true,"local file URLs cannot be exposed");}
   String pending=relay.route("https://broadcaster.test/slow.ts");Thread request=new Thread(()->{try{fetch(pending);}catch(Exception expected){}});request.start();
   check(stalled.await(3,TimeUnit.SECONDS),"slow upstream request started");long before=System.nanoTime();relay.close();
   check(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-before)<250,"stop returns without waiting for blocked upstream I/O");resume.countDown();request.join(3000);
   check(!request.isAlive(),"stop closes active decoder connection");
   try{relay.route("https://broadcaster.test/segment.ts");throw new AssertionError("closed");}catch(IOException expected){check(true,"stopped session cannot restart stale media");}
   System.out.println("PASS ALL "+checks+" transport integration checks");
  }finally{relay.close();resume.countDown();origin.stop(0);serving.shutdownNow();}
 }
}
