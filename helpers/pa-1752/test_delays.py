#!/usr/bin/env python3
"""Exercise the real GtfsNearby.Departure delay freshness model with tiny Android stubs."""
from pathlib import Path
from tempfile import TemporaryDirectory
import subprocess

root=Path(__file__).resolve().parents[1]
source=root/"pa-1749/GtfsNearby.java"
with TemporaryDirectory() as tmp:
    base=Path(tmp)
    def java(path, code):
        dest=base/path
        dest.parent.mkdir(parents=True,exist_ok=True)
        dest.write_text(code,encoding="utf-8")
        return str(dest)
    files=[java("com/ispina/lokalnie/transit/GtfsNearby.java",source.read_text(encoding="utf-8")),
        java("com/ispina/lokalnie/transit/MldRealtime.java", """
package com.ispina.lokalnie.transit;
public final class MldRealtime {
  public static String apply(android.content.Context ctx,java.util.List<GtfsNearby.Departure> list){
    return "test only";
  }
}
"""),
        java("android/content/Context.java", """
package android.content;
public class Context {public java.io.File getCacheDir(){return new java.io.File(".");}}
"""),
        java("android/location/Location.java","""
package android.location;
public class Location {
  public static void distanceBetween(double a,double b,double c,double d,float[] out){out[0]=100f;}
}
"""),
        java("Test.java","""
import com.ispina.lokalnie.transit.GtfsNearby;
public class Test {
    public static void main(String[] ignored) {
      GtfsNearby.Departure d=new GtfsNearby.Departure();
      long now=1_700_000_000_000L;
      if(d.confirmedDelayMinutes(now)!=null)throw new AssertionError("Missing updates must remain unknown");
      d.liveDelaySeconds=180; d.liveReportedAtMillis=now-5000L;
      if(!Integer.valueOf(3).equals(d.confirmedDelayMinutes(now)))throw new AssertionError("180 seconds = +3 minutes");
      d.liveDelaySeconds=0;
      if(!Integer.valueOf(0).equals(d.confirmedDelayMinutes(now)))throw new AssertionError("Zero is confirmed punctuality only when verified");
      d.liveDelaySeconds=-120;
      if(!Integer.valueOf(-2).equals(d.confirmedDelayMinutes(now)))throw new AssertionError("Early service indicator");
      d.liveReportedAtMillis=now-180000L;
      if(d.confirmedDelayMinutes(now)!=null)throw new AssertionError("Stale data cannot be live");
      d.liveReportedAtMillis=now+60000L;
      if(d.confirmedDelayMinutes(now)!=null)throw new AssertionError("Future timestamp invalid");
      d.liveReportedAtMillis=now-1000L; d.liveDelaySeconds=14400;
      if(d.confirmedDelayMinutes(now)!=null)throw new AssertionError("Outlier rejected");
      System.out.println("PASS: +3 min, zero, early, unknown, stale, future, outlier");
    }
}
""")]
    subprocess.run(["javac","-encoding","UTF-8","-d",tmp,*files],check=True)
    subprocess.run(["java","-cp",tmp,"Test"],check=True)
