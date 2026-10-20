package com.ispina.lokalnie;
import org.json.*;import java.util.*;
/** Consumes the SAME schema-2 index, route patterns, calendars and active generation as the planner. */
final class PANearbyData {
 interface Reader {JSONObject route(int id)throws Exception;}
 static final class Departure {
  String line,headsign,source,trip,day,stopId,operator,train;int mode,pickup,seconds,headway;long planned,frequencyEnd;boolean estimated;JSONObject live;
  JSONObject query()throws Exception{return new JSONObject().put("source",source).put("trip",trip).put("serviceDay",day).put("stopId",stopId).put("plannedEpoch",planned).put("dep",seconds).put("line",line).put("headsign",headsign);}
  String key(){return source+"|"+trip+"|"+day+"|"+stopId;}
 }
 static final class Board {PANearbyRules.Stop stop;double distance;boolean covered;final List<Departure> departures=new ArrayList<>();}
 static final class Snapshot {final List<Board> boards=new ArrayList<>();String day;long checked;}
 static String platform(JSONArray row){String p=row.optString(2),id=row.optString(0);return p.isEmpty()&&id.matches("warsaw:[0-9]{6}")?id.substring(id.length()-2):p;}
 static List<PANearbyRules.Stop> stops(JSONObject index)throws Exception{
  JSONArray rows=index.getJSONArray("stops");ArrayList<PANearbyRules.Stop> all=new ArrayList<>();
  for(int i=0;i<rows.length();i++){JSONArray s=rows.getJSONArray(i);if(s.getJSONArray(5).length()==0)continue;all.add(new PANearbyRules.Stop(i,s.getString(0),s.optString(6),s.optString(1),platform(s),s.getDouble(3),s.getDouble(4),s.getJSONArray(5).length()));}return all;
 }
 static Snapshot load(JSONObject index,Reader reader,List<PANearbyRules.Stop> selected,double lat,double lon,long now)throws Exception{
  Snapshot out=new Snapshot();out.checked=now;out.day=java.time.Instant.ofEpochSecond(now).atZone(java.time.ZoneId.of("Europe/Warsaw")).toLocalDate().toString();
  JSONArray days=index.getJSONArray("days"),rows=index.getJSONArray("stops"),routes=index.getJSONArray("routes");
  Map<Integer,JSONObject> cache=new HashMap<>();List<PANearbyRules.Stop> all=stops(index);
  for(PANearbyRules.Stop selectedStop:selected){
   Board b=new Board();b.stop=selectedStop;b.distance=PANearbyRules.valid(lat,lon)?PANearbyRules.meters(lat,lon,selectedStop.lat,selectedStop.lon):Double.NaN;out.boards.add(b);
   Set<Integer> members=new HashSet<>(),rids=new TreeSet<>();Set<String> providers=new HashSet<>();
   for(PANearbyRules.Stop s:all)if(PANearbyRules.same(s,selectedStop)){members.add(s.index);providers.add(s.provider);JSONArray ids=rows.getJSONArray(s.index).getJSONArray(5);for(int i=0;i<ids.length();i++)rids.add(ids.getInt(i));}
   JSONArray sources=index.optJSONArray("sources");int today=-1;for(int i=0;i<days.length();i++)if(out.day.equals(days.getString(i)))today=i;
   if(today>=0&&sources!=null)for(int i=0;i<sources.length();i++){JSONObject src=sources.getJSONObject(i);if(providers.contains(src.optString("id"))&&src.getJSONArray("covered").optBoolean(today))b.covered=true;}
   Set<String> seen=new HashSet<>();
   for(int rid:rids){JSONObject data=cache.get(rid);if(data==null){data=reader.route(rid);cache.put(rid,data);}JSONArray meta=routes.getJSONArray(rid),runs=data.getJSONArray("runs"),patterns=data.getJSONArray("patterns");
    for(int ri=0;ri<runs.length();ri++){JSONArray run=runs.getJSONArray(ri),pattern=patterns.getJSONArray(run.getInt(2));
     for(int di=0;di<days.length();di++){if((run.getInt(0)&(1<<di))==0)continue;String day=days.getString(di);long base=PANearbyRules.epoch(day,run.getInt(3));
      if(base>now+86400||base+172800<now-1800)continue;
      for(int pi=0;pi<pattern.length();pi++){JSONArray point=pattern.getJSONArray(pi);int sid=point.getInt(0),pickup=point.getInt(3);if(!members.contains(sid)||pickup==1)continue;long planned=base+point.getInt(1);int headway=run.optInt(6);long frequencyEnd=headway>0?PANearbyRules.epoch(day,run.optInt(7)+point.getInt(1)):0;if(headway>0&&frequencyEnd<now)continue;if(headway==0&&planned<now-1800||planned>now+86400)continue;
       Departure d=new Departure();d.line=meta.optString(1);d.operator=meta.optString(6,meta.optString(4));d.train=run.optString(5);d.mode=meta.optInt(3);d.source=meta.optString(5);d.headsign=run.optString(1);d.trip=run.optString(4);d.day=day;d.stopId=rows.getJSONArray(sid).getString(0);d.seconds=run.getInt(3)+point.getInt(1);d.planned=planned;d.headway=headway;d.frequencyEnd=frequencyEnd;d.pickup=pickup;d.estimated=point.optInt(5)==1;
       if(seen.add(d.key()))b.departures.add(d);
      }
     }
    }
   }
   b.departures.sort(Comparator.comparingLong(d->d.planned));
   // Six future rows plus recent courses that may still be delayed. Bounded LIVE request below.
   ArrayList<Departure> keep=new ArrayList<>();int past=0,future=0;for(int i=b.departures.size()-1;i>=0;i--){Departure d=b.departures.get(i);if(d.headway==0&&d.planned<now&&past++<3)keep.add(d);}
   for(Departure d:b.departures)if((d.planned>=now||d.headway>0)&&future++<8)keep.add(d);keep.sort(Comparator.comparingLong(d->d.planned));b.departures.clear();b.departures.addAll(keep);
  }return out;
 }
 static void enrich(Snapshot s,PALive.Reader reader)throws Exception{JSONArray query=new JSONArray();ArrayList<Departure> list=new ArrayList<>();for(Board b:s.boards)for(Departure d:b.departures){if(d.headway==0){list.add(d);query.put(d.query());}}if(query.length()==0)return;
  JSONObject response=PALive.lookup(new JSONObject().put("departures",query),reader);JSONArray answers=response.getJSONArray("results");Map<String,JSONObject> matched=new HashMap<>();for(int i=0;i<answers.length();i++){JSONObject a=answers.getJSONObject(i);matched.put(a.optString("source")+"|"+a.optString("trip")+"|"+a.optString("serviceDay")+"|"+a.optString("stopId"),a);}for(Departure d:list)d.live=matched.get(d.key());s.checked=response.optLong("checkedAt",s.checked);
 }
 static boolean confirmed(Departure d,long now){return d.live!=null&&PANearbyRules.fresh(d.live.optLong("liveTimestamp"),now);}
 static boolean cancelled(Departure d,long now){return confirmed(d,now)&&(d.live.optBoolean("cancelled")||d.live.optBoolean("skipped"));}
 static boolean eta(Departure d,long now){return confirmed(d,now)&&!cancelled(d,now)&&d.live.has("eta")&&d.live.optLong("eta")>=now-30&&d.live.optLong("eta")<now+86400;}
 static long time(Departure d,long now){return eta(d,now)?d.live.optLong("eta"):d.planned;}
 static List<Departure> visible(Board b,long now){ArrayList<Departure> out=new ArrayList<>();for(Departure d:b.departures)if(d.headway>0&&d.frequencyEnd>=now||time(d,now)>=now-30||cancelled(d,now)&&d.planned>=now-300)out.add(d);out.sort(Comparator.comparingLong(d->time(d,now)));return out;}
}
