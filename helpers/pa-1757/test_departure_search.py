#!/usr/bin/env python3
"""Compile the real GTFS and search classes and exercise location-free search."""
from pathlib import Path
from tempfile import TemporaryDirectory
import subprocess

root=Path(__file__).resolve().parents[1]
with TemporaryDirectory() as temp:
    base=Path(temp)
    def src(path,text):
        file=base/path
        file.parent.mkdir(parents=True,exist_ok=True)
        file.write_text(text,encoding="utf-8")
        return str(file)
    files=[
        src("com/ispina/lokalnie/transit/GtfsNearby.java",
            (root/"pa-1749/GtfsNearby.java").read_text(encoding="utf-8")),
        src("com/ispina/lokalnie/transit/DepartureQuickFilter.java",
            (root/"pa-1757/DepartureQuickFilter.java").read_text(encoding="utf-8")),
        src("com/ispina/lokalnie/transit/MldRealtime.java","""
package com.ispina.lokalnie.transit;
public final class MldRealtime {
  public static String apply(android.content.Context c,java.util.List<GtfsNearby.Departure> list){
    return "disabled in test";
  }
}
"""),
        src("android/content/Context.java","""
package android.content;
public class Context {public java.io.File getCacheDir(){return new java.io.File(".");}}
"""),
        src("android/location/Location.java","""
package android.location;
public class Location {
 public static void distanceBetween(double a,double b,double c,double d,float[] out){out[0]=100f;}
}
"""),
        src("Test.java",r"""
import com.ispina.lokalnie.transit.GtfsNearby;
import com.ispina.lokalnie.transit.DepartureQuickFilter;
public class Test {
  static void expect(boolean actual,boolean expected,String what){
    if(actual!=expected)throw new AssertionError(what);
  }
  public static void main(String[] args){
    GtfsNearby.Departure d=new GtfsNearby.Departure();
    d.stop=new GtfsNearby.Stop();
    d.line="A7";
    d.headsign="Niepołomice / Podłęże";
    d.stop.name="Praga-Południe";
    d.stop.code="03";
    expect(DepartureQuickFilter.matches(d,"A7"),true,"line");
    expect(DepartureQuickFilter.matches(d,"7"),true,"partial line");
    expect(DepartureQuickFilter.matches(d,"PODLEZE"),true,"diacritic fold");
    expect(DepartureQuickFilter.matches(d,"poludnie"),true,"stop diacritic");
    expect(DepartureQuickFilter.matches(d,"03"),true,"platform code");
    expect(DepartureQuickFilter.matches(d,"METRO"),false,"unrelated");
    expect(DepartureQuickFilter.matches(d,""),true,"empty search");
    expect(DepartureQuickFilter.matches(d,"   "),true,"space only");
    expect(DepartureQuickFilter.matches(d,"a7"),true,"case insensitive");
    expect(DepartureQuickFilter.matches(null,"A7"),false,"null");
    long now=1800000000000L;
    d.when=now+3*60000L;
    if(DepartureQuickFilter.expectedTime(d,now)!=d.when)throw new AssertionError("Scheduled unchanged");
    d.liveDelaySeconds=180;d.liveReportedAtMillis=now-15000L;
    if(DepartureQuickFilter.expectedTime(d,now)!=d.when+180000L)throw new AssertionError("Live +3");
    if(DepartureQuickFilter.expectedTime(d,now+200000L)!=d.when)throw new AssertionError("Stale live rejected");
    System.out.println("PASS: local matching Polish directions/stops, scheduled and fresh/stale LIVE times");
  }
}
""")
    ]
    subprocess.run(["javac","-encoding","UTF-8","-d",str(base),*files],check=True)
    subprocess.run(["java","-cp",str(base),"Test"],check=True)
