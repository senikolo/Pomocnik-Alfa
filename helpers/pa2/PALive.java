package com.ispina.lokalnie;
import org.json.*;
import java.io.*;
import java.util.*;
/** Minimal bounded GTFS-Realtime reader. Exact trip/date/stop joins only; never GPS-derived ETA. */
final class PALive {
 interface Reader{byte[] read(String url,int max)throws Exception;}
 static final class Field {int wire;long number;byte[] bytes;Field(int w,long n,byte[] b){wire=w;number=n;bytes=b;}}
 static final class Msg{
  final Map<Integer,List<Field>> fields=new HashMap<>();
  Msg(byte[] b)throws IOException{if(b==null||b.length>12*1024*1024)throw new IOException("Niepoprawna wiadomość LIVE");int[] at={0};int count=0;while(at[0]<b.length){long tag=var(b,at);int key=(int)(tag>>>3),wire=(int)(tag&7);if(key==0||++count>1000000)throw new IOException("Niepoprawny GTFS-RT");Field f;
    if(wire==0)f=new Field(wire,var(b,at),null);else if(wire==2){long size=var(b,at);if(size<0||size>b.length-at[0])throw new IOException("Uszkodzony GTFS-RT");byte[] v=Arrays.copyOfRange(b,at[0],at[0]+(int)size);at[0]+=(int)size;f=new Field(wire,0,v);}else if(wire==5||wire==1){int len=wire==5?4:8;if(at[0]+len>b.length)throw new IOException("Uszkodzony GTFS-RT");long n=0;for(int i=0;i<len;i++)n|=((long)b[at[0]++]&255)<<(8*i);f=new Field(wire,n,null);}else throw new IOException("Nieobsługiwany GTFS-RT");fields.computeIfAbsent(key,k->new ArrayList<>()).add(f);
  }}
  static long var(byte[] b,int[] at)throws IOException{long n=0;for(int i=0;i<10;i++){if(at[0]>=b.length)throw new IOException("Uszkodzony GTFS-RT");int v=b[at[0]++]&255;n|=(long)(v&127)<<(i*7);if((v&128)==0)return n;}throw new IOException("Niepoprawny varint");}
  Field one(int k){List<Field> x=fields.get(k);return x==null||x.isEmpty()?null:x.get(0);}
  long number(int k,long fallback){Field f=one(k);return f==null?fallback:f.number;}
  String text(int k){Field f=one(k);return f==null||f.bytes==null?"":new String(f.bytes,java.nio.charset.StandardCharsets.UTF_8);}
  Msg sub(int k)throws IOException{Field f=one(k);return new Msg(f==null||f.bytes==null?new byte[0]:f.bytes);}
  List<Msg> all(int k)throws IOException{List<Msg> out=new ArrayList<>();for(Field f:fields.getOrDefault(k,Collections.emptyList()))if(f.bytes!=null)out.add(new Msg(f.bytes));return out;}
  double decimal(int k){return Float.intBitsToFloat((int)number(k,0));}
 }
 static boolean fresh(long stamp,long now){return stamp>0&&stamp<=now+20&&now-stamp<=120;}
 static JSONObject lookup(JSONObject p,Reader reader)throws Exception{
  JSONArray candidates=p.optJSONArray("departures");if(candidates==null||candidates.length()>12)throw new IOException("Nieprawidłowe kursy do sprawdzenia LIVE.");Map<String,List<Msg>> vehicles=new HashMap<>(),updates=new HashMap<>();Map<String,Long> stamps=new HashMap<>();Set<String> attempted=new HashSet<>();JSONArray results=new JSONArray();long now=System.currentTimeMillis()/1000;
  for(int i=0;i<candidates.length();i++){
   JSONObject c=candidates.getJSONObject(i);String source=c.optString("source"),trip=c.optString("trip"),date=c.optString("serviceDay").replace("-","");JSONObject result=new JSONObject(c.toString());
   String prefix=source.equals("krakow_bus")?"A":source.equals("krakow_tram")?"T":source.equals("krakow_mobilis")?"M":"";
   if(attempted.add(source)){
    try{
     String vu=source.equals("warsaw")?"https://mkuran.pl/gtfs/warsaw/vehicles.pb":!prefix.isEmpty()?"https://gtfs.ztp.krakow.pl/VehiclePositions_"+prefix+".pb":"";
     if(!vu.isEmpty()){Msg feed=new Msg(reader.read(vu,12*1024*1024));long stamp=feed.sub(1).number(3,0);if(fresh(stamp,now)){List<Msg> list=new ArrayList<>();for(Msg entity:feed.all(2))if(entity.one(4)!=null)list.add(entity.sub(4));vehicles.put(source,list);stamps.put(source,stamp);}}
     String tu=!prefix.isEmpty()?"https://gtfs.ztp.krakow.pl/TripUpdates_"+prefix+".pb":source.equals("wkd")?"https://mkuran.pl/gtfs/wkd.pb":source.equals("polishrail")?"https://mkuran.pl/gtfs/polish_trains/updates.pb":"";
     if(!tu.isEmpty()){Msg feed=new Msg(reader.read(tu,12*1024*1024));long stamp=feed.sub(1).number(3,0);if(fresh(stamp,now)){List<Msg> list=new ArrayList<>();for(Msg entity:feed.all(2))if(entity.one(3)!=null)list.add(entity.sub(3));updates.put(source,list);stamps.put(source,stamp);}}
    }catch(Exception ignored){} // Unavailable/stale is returned as scheduled; never reported as LIVE.
   }
   boolean live=false;
   for(Msg v:vehicles.getOrDefault(source,Collections.emptyList())){Msg d=v.sub(1);String sd=d.text(3);long stamp=v.number(5,stamps.getOrDefault(source,0L));if(!d.text(1).equals(trip)||(!sd.isEmpty()&&!sd.equals(date))||!fresh(stamp,now))continue;Msg pos=v.sub(2);double lat=pos.decimal(1),lon=pos.decimal(2);if(lat<48||lat>56||lon<13||lon>25)continue;result.put("position",new JSONObject().put("latitude",lat).put("longitude",lon).put("timestamp",stamp).put("age",now-stamp));live=true;break;}
   for(Msg u:updates.getOrDefault(source,Collections.emptyList())){Msg d=u.sub(1);String sd=d.text(3);long stamp=u.number(4,stamps.getOrDefault(source,0L));if(!d.text(1).equals(trip)||(!sd.isEmpty()&&!sd.equals(date))||!fresh(stamp,now))continue;if(d.number(4,0)==3){result.put("cancelled",true);live=true;break;}
    String stop=c.optString("stopId");int colon=stop.indexOf(':');if(colon>=0)stop=stop.substring(colon+1);
    for(Msg s:u.all(2)){if(!s.text(4).equals(stop))continue;if(s.number(5,0)==1){result.put("skipped",true);live=true;break;}Msg event=s.one(3)!=null?s.sub(3):s.sub(2);long eta=event.number(2,0);if(eta>=now-30&&eta<now+86400){result.put("eta",eta);result.put("liveTimestamp",stamp);live=true;}break;}
   }
   result.put("live",live);result.put("provenance",source.startsWith("krakow")?"ZTP Kraków • GTFS-RT":source.equals("warsaw")?"Miasto Stołeczne Warszawa • api.um.warszawa.pl • GTFS-RT: Mikołaj Kuranowski":"GTFS-RT: Mikołaj Kuranowski / dane przewoźników");results.put(result);
  }
  return new JSONObject().put("results",results).put("checkedAt",now).put("notice","Brak zgodnego i świeżego pomiaru oznacza odjazd rozkładowy. Pozycja GPS nie jest prognozą przyjazdu.");
 }
}
