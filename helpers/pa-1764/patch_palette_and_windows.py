#!/usr/bin/env python3
"""PA 1.7.64: fixed colors, real platform names, do not show departed vehicles.
A time-window includes a small portion of past schedule to MATCH delayed buses
from fresh live GPS; it never displays past buses absent this proof.
"""
from pathlib import Path
from shutil import copyfile
root=Path("project/app/src/main/java/com/ispina/lokalnie")
transit=root/"transit"
copyfile("helpers/pa-1764/TransitWowUi.java",root/"TransitWowUi.java")
def once(path,old,new,label):
    content=path.read_text(encoding="utf-8")
    n=content.count(old)
    if n!=1:raise AssertionError(f"{label}: expected exactly one marker, got {n}")
    path.write_text(content.replace(old,new,1),encoding="utf-8")

gtfs=transit/"GtfsNearby.java"
once(gtfs,
'''        public String label(){
            return name+(code==null||code.isEmpty()?"":" · stan. "+code);
        }''',
'''        public String label(){
            String number=id!=null&&id.matches("[0-9]{6}")?id.substring(4):
                (code==null?"":code.trim());
            if(number.matches("[0-9]"))number="0"+number;
            return (name+(number.isEmpty()?"":" "+number))
                .toUpperCase(new java.util.Locale("pl","PL"));
        }''',"full station/stand in picker")
once(gtfs,"if(departure<moment-120000L||departure>moment+120L*60000L)continue;",
          "if(departure<moment-25L*60000L||departure>moment+120L*60000L)continue;",
          "nearby late transit candidate window")
once(gtfs,
'''            // Do not allow plentiful bus courses to hide the tram/SKM sections.
            Map<String,Integer> byMode=new HashMap<>();
            Iterator<Departure> cursor=result.departures.iterator();
            while(cursor.hasNext()){
                Departure d=cursor.next();
                int n=byMode.getOrDefault(d.mode,0);
                if(n>=30)cursor.remove(); else byMode.put(d.mode,n+1);
            }''',
'''            // Include recent scheduled departures for GPS proof of late service,
            // while reserving enough upcoming departures for each transit type.
            Map<String,Integer> recentByMode=new HashMap<>();
            Map<String,Integer> futureByMode=new HashMap<>();
            Iterator<Departure> cursor=result.departures.iterator();
            while(cursor.hasNext()){
                Departure d=cursor.next();
                boolean past=d.when<moment;
                Map<String,Integer> counts=past?recentByMode:futureByMode;
                int n=counts.getOrDefault(d.mode,0);
                if(n>=(past?24:36))cursor.remove();
                else counts.put(d.mode,n+1);
            }''',"fair old-vs-upcoming limits")
once(gtfs,"if(when<at-60000L||when>at+120L*60000L)continue;",
          "if(when<at-25L*60000L||when>at+120L*60000L)continue;",
          "manual late candidate window")
once(gtfs,
'''            if(out.departures.size()>30)
                out.departures.subList(30,out.departures.size()).clear();''',
'''            int pastCount=0,futureCount=0;
            Iterator<Departure> departuresIt=out.departures.iterator();
            while(departuresIt.hasNext()){
                Departure d=departuresIt.next();
                if(d.when<at){
                    if(pastCount++>=18)departuresIt.remove();
                }else{
                    if(futureCount++>=32)departuresIt.remove();
                }
            }''',"manual future capacity")
main=root/"MainActivity.java"
once(main,"POMOCNIK ALFA 1.7.63","POMOCNIK ALFA 1.7.64","version label")
gradle=Path("project/app/build.gradle")
once(gradle,"versionCode 10764","versionCode 10765","version code")
once(gradle,"versionName '1.7.63-A10'","versionName '1.7.64-A10'","version name")
print("PASS: mode colors applied, Warsaw stop names show full 02, preserve live-confirmable late runs")
