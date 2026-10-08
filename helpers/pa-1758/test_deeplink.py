#!/usr/bin/env python3
"""Test safe stop-number URLs and offline validation of manual GTFS methods."""
from pathlib import Path
from tempfile import TemporaryDirectory
import subprocess
root=Path(__file__).resolve().parents[1]
with TemporaryDirectory() as directory:
    base=Path(directory)
    def java(path,source):
        target=base/path
        target.parent.mkdir(parents=True,exist_ok=True)
        target.write_text(source,encoding="utf-8")
        return str(target)
    source=[
        java("com/ispina/lokalnie/transit/GtfsNearby.java",(root/"pa-1749/GtfsNearby.java").read_text(encoding="utf-8")),
        java("com/ispina/lokalnie/transit/CzynaczasLinks.java",(root/"pa-1758/CzynaczasLinks.java").read_text(encoding="utf-8")),
        java("com/ispina/lokalnie/transit/MldRealtime.java","""
package com.ispina.lokalnie.transit;
public final class MldRealtime {
 public static String apply(android.content.Context c,java.util.List<GtfsNearby.Departure> rows){return "disabled";}
}
"""),
        java("android/content/Context.java","""
package android.content;
public class Context {public java.io.File getCacheDir(){return new java.io.File(".");}}
"""),
        java("android/location/Location.java","""
package android.location;
public class Location {
 public static void distanceBetween(double a,double b,double c,double d,float[] out){out[0]=100f;}
}
"""),
        java("Test.java",r"""
import com.ispina.lokalnie.transit.*;
public class Test {
 static void yes(boolean expression,String note){if(!expression)throw new AssertionError(note);}
 public static void main(String[] args)throws Exception {
   yes(CzynaczasLinks.forStop("warsaw","701306")
         .equals("https://czynaczas.pl/warsaw?przystanek=701306"),"Warsaw stop deep link");
   yes(CzynaczasLinks.forStop("warsaw","abcd") .equals("https://czynaczas.pl/warsaw"),"Invalid ID fallback");
   yes(CzynaczasLinks.forStop("mld","701306")==null,"Never confuse MLD stops with Warsaw");
   yes(CzynaczasLinks.forStop("warsaw","701306&hack=1")
         .equals("https://czynaczas.pl/warsaw"),"Injection-resistant link");
   boolean rejected=false;
   try{GtfsNearby.lineStops(null,"warsaw","?");}
   catch(java.io.IOException e){rejected=true;}
   yes(rejected,"Reject invalid line before downloading");
   rejected=false;
   try{GtfsNearby.lineStopDepartures(null,"warsaw","A7","");}
   catch(java.io.IOException e){rejected=true;}
   yes(rejected,"Reject missing platform before downloading");
   rejected=false;
   try{GtfsNearby.lineStops(null,"unknown","517");}
   catch(java.io.IOException e){rejected=true;}
   yes(rejected,"Reject unsupported operator");
   System.out.println("PASS: Warsaw stop deep links, anti-injection and manual GTFS inputs");
 }
}
""")
    ]
    subprocess.run(["javac","-encoding","UTF-8","-d",str(base),*source],check=True)
    subprocess.run(["java","-cp",str(base),"Test"],check=True)
