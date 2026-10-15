package com.ispina.lokalnie;
import java.util.*;
/** Pure geographic selection. Cluster diameter, not user radius, is at most 100 m. */
public final class PANearbyRules {
 private PANearbyRules(){}
 public static final class Stop {
  public final int index,routes; public final String id,provider,name,platform; public final double lat,lon;
  public Stop(int i,String id,String provider,String name,String platform,double lat,double lon,int routes){this.index=i;this.id=id;this.provider=provider;this.name=name;this.platform=platform;this.lat=lat;this.lon=lon;this.routes=routes;}
 }
 public static double meters(double a,double b,double c,double d){double x=Math.toRadians(c-a),y=Math.toRadians(d-b);double h=Math.sin(x/2)*Math.sin(x/2)+Math.cos(Math.toRadians(a))*Math.cos(Math.toRadians(c))*Math.sin(y/2)*Math.sin(y/2);return 12742000*Math.asin(Math.sqrt(Math.min(1,Math.max(0,h))));}
 public static boolean valid(double a,double b){return Double.isFinite(a)&&Double.isFinite(b)&&a>=-90&&a<=90&&b>=-180&&b<=180;}
 public static boolean same(Stop a,Stop b){
  if(a.id.equals(b.id))return true;
  double dist=meters(a.lat,a.lon,b.lat,b.lon);
  return a.name.equalsIgnoreCase(b.name)&&a.platform.equalsIgnoreCase(b.platform)&&dist<=(a.platform.isEmpty()?2:8);
 }
 public static List<Stop> choose(List<Stop> input,double lat,double lon){
  if(!valid(lat,lon))return Collections.emptyList();
  ArrayList<Stop> candidates=new ArrayList<>(),out=new ArrayList<>();
  for(Stop s:input)if(s!=null&&s.routes>0&&valid(s.lat,s.lon))candidates.add(s);
  candidates.sort(Comparator.comparingDouble((Stop s)->meters(lat,lon,s.lat,s.lon)).thenComparing(s->s.id));
  if(candidates.isEmpty())return out;out.add(candidates.remove(0));
  while(out.size()<3){Stop best=null;double score=-Double.MAX_VALUE;
   for(Stop s:candidates){boolean ok=true;for(Stop p:out)if(same(s,p)||meters(s.lat,s.lon,p.lat,p.lon)>100.00001){ok=false;break;}if(!ok)continue;
    // Prefer a different boarding direction/provider and useful route coverage in a larger hub.
    boolean providerNew=true;for(Stop p:out)if(p.provider.equals(s.provider))providerNew=false;
    double value=(providerNew?20:0)+Math.min(12,s.routes)*2-meters(lat,lon,s.lat,s.lon)/10;
    if(value>score){score=value;best=s;}
   }
   if(best==null)break;out.add(best);candidates.remove(best);
  }
  if(out.size()>2){Stop first=out.remove(0);out.sort(Comparator.comparingDouble(s->meters(lat,lon,s.lat,s.lon)));out.add(0,first);}return out;
 }
 public static boolean fresh(long stamp,long now){return stamp>0&&stamp<=now+20&&now-stamp<=120;}
 public static long epoch(String day,int seconds){
  // GTFS service time is elapsed time from local noon minus twelve hours (DST safe).
  java.time.ZonedDateTime noon=java.time.LocalDate.parse(day).atTime(12,0).atZone(java.time.ZoneId.of("Europe/Warsaw"));
  return noon.toEpochSecond()-43200+seconds;
 }
}
