package com.ispina.lokalnie;
import android.content.Context;
import android.webkit.WebResourceResponse;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
/** On-demand OpenStreetMap tiles, 7-day native cache, no bulk fetching. */
final class PAOSMTiles {
 private static final Semaphore slots=new Semaphore(4,true);
 static WebResourceResponse load(Context ctx,String path){
  if(path==null||!path.matches("/osm/(?:[8-9]|1[0-8])/[0-9]{1,6}/[0-9]{1,6}\\.png"))return unavailable();
  try{
   String[] p=path.split("/");
   int z=Integer.parseInt(p[2]),x=Integer.parseInt(p[3]),y=Integer.parseInt(p[4].substring(0,p[4].length()-4));
   int bound=1<<z;if(x<0||y<0||x>=bound||y>=bound)return unavailable();
   File dir=new File(ctx.getCacheDir(),"osm-tiles");if(!dir.isDirectory()&&!dir.mkdirs())return unavailable();
   File target=new File(dir,z+"_"+x+"_"+y+".png");
   long maxAge=7L*24*60*60*1000L,now=System.currentTimeMillis();
   if(!target.isFile()||target.length()<100||now-target.lastModified()>maxAge){
    if(!slots.tryAcquire(10,TimeUnit.SECONDS))return unavailable();
    try{
     if(!target.isFile()||target.length()<100||System.currentTimeMillis()-target.lastModified()>maxAge){
      byte[] bytes=PARegionsActivity.read("https://tile.openstreetmap.org/"+z+"/"+x+"/"+y+".png",600000);
      if(bytes.length<100||bytes[0]!=(byte)0x89||bytes[1]!='P'||bytes[2]!='N'||bytes[3]!='G')throw new IOException("Incorrect PNG");
      File tmp=new File(dir,z+"_"+x+"_"+y+"-partial");
      try(FileOutputStream out=new FileOutputStream(tmp)){out.write(bytes);}
      if(!tmp.renameTo(target)){tmp.delete();throw new IOException("Cache write unavailable");}
      target.setLastModified(System.currentTimeMillis());
      File[] all=dir.listFiles();if(all!=null&&all.length>500)for(File f:all)if(System.currentTimeMillis()-f.lastModified()>maxAge)f.delete();
     }
    }finally{slots.release();}
   }
   Map<String,String> headers=new HashMap<>();
   headers.put("Cache-Control","public,max-age=604800");headers.put("X-Content-Type-Options","nosniff");
   return new WebResourceResponse("image/png",null,200,"OK",headers,new FileInputStream(target));
  }catch(Exception e){return unavailable();}
 }
 private static WebResourceResponse unavailable(){return new WebResourceResponse("image/png",null,404,"Not Found",Collections.<String,String>emptyMap(),new ByteArrayInputStream(new byte[0]));}
}
