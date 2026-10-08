#!/usr/bin/env python3
"""Integration-test actual Android-independent GTFS-RT parsing using a real org.json jar."""
from pathlib import Path
from tempfile import TemporaryDirectory
import subprocess,urllib.request

root=Path(__file__).resolve().parents[1]
with TemporaryDirectory() as directory:
    base=Path(directory)
    def java(path,src):
        dest=base/path
        dest.parent.mkdir(parents=True,exist_ok=True)
        dest.write_text(src,encoding="utf-8")
        return str(dest)
    files=[
        java("com/ispina/lokalnie/transit/GtfsNearby.java",(root/"pa-1749/GtfsNearby.java").read_text(encoding="utf-8")),
        java("com/ispina/lokalnie/transit/MldRealtime.java",(root/"pa-1754/MldRealtime.java").read_text(encoding="utf-8")),
        java("android/content/Context.java","package android.content; public class Context { public java.io.File getCacheDir(){return new java.io.File(\".\");} }"),
        java("android/location/Location.java","package android.location; public class Location { public static void distanceBetween(double a,double b,double c,double d,float[] out){out[0]=100f;} }"),
        java("Test.java",r"""
import java.util.*;
import com.ispina.lokalnie.transit.GtfsNearby;
import com.ispina.lokalnie.transit.MldRealtime;
public class Test {
  private static void test(boolean ok,String note){if(!ok)throw new AssertionError(note);}
  private static GtfsNearby.Departure departure(){
     GtfsNearby.Departure d=new GtfsNearby.Departure();
     d.tripId="A4:original:113";
     d.routeId="A4";
     d.serviceDate="20261008";
     d.sequence=12;
     d.stop=new GtfsNearby.Stop();d.stop.id="Ispina_Most_1";
     return d;
  }
  private static String json(long timestamp,String tripId,String date,String stop,String route,int delay) {
     return "{\"header\":{\"timestamp\":"+timestamp+"},\"entity\":[{\"tripUpdate\":{\"trip\":{\"tripId\":\""+tripId+"\",\"routeId\":\""+route+"\",\"startDate\":\""+date+"\"},\"timestamp\":"+timestamp+",\"stopTimeUpdate\":[{\"stopId\":\""+stop+"\",\"stopSequence\":12,\"departure\":{\"delay\":"+delay+"}}]}}]}";
  }
  public static void main(String[] args)throws Exception{
     long ts=1_790_000_000L,now=ts*1000L+15000L;
     GtfsNearby.Departure ok=departure();
     List<GtfsNearby.Departure> list=Collections.singletonList(ok);
     MldRealtime.applyJson(json(ts,ok.tripId,ok.serviceDate,ok.stop.id,ok.routeId,180),list,now);
     test(Integer.valueOf(3).equals(ok.confirmedDelayMinutes(now)),"Exact trip/stop/date should show +3");
     ok=departure();list=Collections.singletonList(ok);
     MldRealtime.applyJson(json(ts,ok.tripId,"20261009",ok.stop.id,ok.routeId,180),list,now);
     test(ok.confirmedDelayMinutes(now)==null,"Different date must never match");
     ok=departure();list=Collections.singletonList(ok);
     MldRealtime.applyJson(json(ts,ok.tripId,ok.serviceDate,"DIFFERENT_STOP",ok.routeId,180).replace("\"stopSequence\":12,","\"stopSequence\":99,"),list,now);
     test(ok.confirmedDelayMinutes(now)==null,"Different stop must never match");
     ok=departure();list=Collections.singletonList(ok);
     MldRealtime.applyJson(json(ts,ok.tripId,ok.serviceDate,ok.stop.id,"OTHER_ROUTE",180),list,now);
     test(ok.confirmedDelayMinutes(now)==null,"Different route must never match");
     ok=departure();list=Collections.singletonList(ok);
     MldRealtime.applyJson(json(ts-360,ok.tripId,ok.serviceDate,ok.stop.id,ok.routeId,180),list,now);
     test(ok.confirmedDelayMinutes(now)==null,"Stale 6-minute feeds must never match");
     ok=departure();list=Collections.singletonList(ok);
     MldRealtime.applyJson(json(ts,ok.tripId,ok.serviceDate,ok.stop.id,ok.routeId,10800),list,now);
     test(ok.confirmedDelayMinutes(now)==null,"Three-hour outlier rejected");
     System.out.println("PASS: actual GTFS-RT parser strict trip/date/route/stop, +3 min and stale suppression");
  }
}
""")
    ]
    jar=base/"json.jar"
    urllib.request.urlretrieve("https://repo.maven.apache.org/maven2/org/json/json/20250517/json-20250517.jar",jar)
    subprocess.run(["javac","-encoding","UTF-8","-cp",str(jar),"-d",str(base),*files],check=True)
    subprocess.run(["java","-cp",str(base)+":"+str(jar),"Test"],check=True)
