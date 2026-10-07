package com.ispina.lokalnie.radio;

import android.text.Html;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import org.json.*;

public final class RadioNowPlaying {
    public static final class Info {
        public String program="";
        public String artist="";
        public String track="";
        public boolean hasAnything(){ return !program.isEmpty() || !track.isEmpty(); }
    }

    public static Info lookup(String station, String streamUrl) {
        Info out=new Info();
        try { merge(out, probeIcy(streamUrl)); } catch(Throwable ignored) {}
        String s=station==null?"":station.toLowerCase(Locale.ROOT);
        try {
            if(s.contains("program 1") || s.contains("jedyn")) out.program=polskieRadioProgram1();
            else if(s.contains("rmf")) out.program=rmfProgram();
            else if(s.contains("eska")) out.program=eskaProgram();
        } catch(Throwable ignored) {}
        return out;
    }

    private static void merge(Info a, Info b){
        if(b==null)return;
        if(a.program.isEmpty())a.program=b.program;
        if(a.track.isEmpty()){a.artist=b.artist;a.track=b.track;}
    }

    private static Info probeIcy(String streamUrl) throws Exception {
        Info r=new Info();
        if(streamUrl==null || streamUrl.isEmpty() || streamUrl.contains(".m3u8")) return r;
        HttpURLConnection c=(HttpURLConnection)new URL(streamUrl).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(5000);
        c.setReadTimeout(6500);
        c.setRequestProperty("User-Agent","Ispina-Lokalnie/1.82.95 RadioInfo");
        c.setRequestProperty("Icy-MetaData","1");
        c.setRequestProperty("Accept","*/*");
        try {
            c.connect();
            int metaInt=parseInt(firstHeader(c,"icy-metaint","Icy-MetaInt"));
            if(metaInt<=0 || metaInt>1024*1024) return r;
            InputStream in=new BufferedInputStream(c.getInputStream(),32768);
            byte[] buf=new byte[Math.min(metaInt,32768)];
            int remain=metaInt;
            while(remain>0){int got=in.read(buf,0,Math.min(buf.length,remain));if(got<0)return r;remain-=got;}
            int lenByte=in.read();
            if(lenByte<=0)return r;
            int len=lenByte*16;
            byte[] data=new byte[len];
            int off=0;
            while(off<len){int got=in.read(data,off,len-off);if(got<0)break;off+=got;}
            String raw=new String(data,0,off,StandardCharsets.ISO_8859_1).replace("\u0000","").trim();
            Matcher m=Pattern.compile("(?i)StreamTitle\\s*=\\s*['\"]([^'\"]+)['\"]").matcher(raw);
            if(m.find()){
                String title=clean(m.group(1));
                String[] song=splitSong(title);
                if(song!=null){r.artist=song[0];r.track=song[1];}
                else r.track=title;
            }
            return r;
        } finally { c.disconnect(); }
    }

    private static String polskieRadioProgram1() throws Exception {
        String json=downloadPr("https://apipr.polskieradio.pl/api/schedule?Program=1");
        if(json.isEmpty()) json=downloadPr("https://apipr.polskieradio.pl/api/mainschedule/?Program=1");
        if(json.isEmpty()) return "";
        JSONObject root=new JSONObject(json);
        JSONArray schedule=root.optJSONArray("Schedule");
        if(schedule==null) return "";
        for(int i=0;i<schedule.length();i++){
            JSONObject item=schedule.optJSONObject(i);
            if(item!=null && item.optBoolean("IsActive",false)){
                String t=clean(item.optString("Title",""));
                if(goodProgram(t))return t;
            }
        }
        int now=minutesNow(), best=-1; String bestTitle="";
        for(int i=0;i<schedule.length();i++){
            JSONObject item=schedule.optJSONObject(i); if(item==null)continue;
            int mins=minutesFromIso(item.optString("StartHour",""));
            String t=clean(item.optString("Title",""));
            if(mins>=0 && mins<=now && mins>=best && goodProgram(t)){best=mins;bestTitle=t;}
        }
        return bestTitle;
    }

    private static String rmfProgram() throws Exception {
        Calendar c=Calendar.getInstance(TimeZone.getTimeZone("Europe/Warsaw"));
        int day=c.get(Calendar.DAY_OF_WEEK)-1;
        String text=htmlText(download("https://www.rmf.fm/ramowka-"+day+".html"));
        String[] lines=text.split("\\r?\\n");
        int now=minutesNow(),best=-1;String title="";
        Pattern p=Pattern.compile("^\\s*([01]?\\d|2[0-3]):([0-5]\\d)(?:\\s+(.+))?$");
        for(int i=0;i<lines.length;i++){
            String line=clean(lines[i]);
            if(line.equalsIgnoreCase("Fakty"))break;
            Matcher m=p.matcher(line);if(!m.find())continue;
            int mins=Integer.parseInt(m.group(1))*60+Integer.parseInt(m.group(2));
            if(mins>now||mins<best)continue;
            String candidate=clean(m.group(3));
            if(candidate.isEmpty())candidate=nextMeaningful(lines,i+1,4);
            if(goodProgram(candidate)){best=mins;title=candidate;}
        }
        return title;
    }

    private static String eskaProgram(){
        Calendar c=Calendar.getInstance(TimeZone.getTimeZone("Europe/Warsaw"));
        int d=c.get(Calendar.DAY_OF_WEEK), m=c.get(Calendar.HOUR_OF_DAY)*60+c.get(Calendar.MINUTE);
        if(d>=Calendar.MONDAY && d<=Calendar.THURSDAY){
            if(m>=360&&m<540)return "Jankes, Kama i Wiktor";
            if(m>=540&&m<720)return "HITY NA CZASIE — Kasia Węsierska";
            if(m>=720&&m<900){
                if(d==Calendar.MONDAY||d==Calendar.TUESDAY)return "HITY NA CZASIE — Rafał Adamczak";
                return "HITY NA CZASIE — Michu Sobkowski";
            }
            if(m>=900&&m<1080)return "SK w Radiu ESKA";
            if(m>=1080&&m<1380)return "IMPRESKA — Michał Hanczak";
            if(d==Calendar.MONDAY&&m>=1380)return "RAP 20 — Mati Fuczyło";
            if(d==Calendar.THURSDAY&&m>=1380)return "Wszystkie Hity Zaczynają się w ESCE";
        } else if(d==Calendar.FRIDAY){
            if(m>=360&&m<540)return "Jankes, Kama i Wiktor";
            if(m>=540&&m<840)return "Weekend zaczyna się w ESCE";
            if(m>=840&&m<1020)return "SK w Radiu ESKA";
            if(m>=1020&&m<1200)return "GORĄCA 20 — Michu Sobkowski";
            if(m>=1200&&m<1380)return "ESKA Live Remix";
            if(m>=1380)return "A State Of Trance — Armin van Buuren";
        } else if(d==Calendar.SATURDAY){
            if(m<60)return "ESKA Live Remix — Lost Frequencies";
            if(m<540)return "ESKA Live Remix — Dubdogz";
            if(m<840)return "WEEKEND Z HITAMI NA CZASIE — Łukasz Zduńczyk";
            if(m<1080)return "WEEKEND Z HITAMI NA CZASIE — Paweł Karpiński";
            return "SOBOTNIA IMPRESKA — Mati Fuczyło";
        } else if(d==Calendar.SUNDAY){
            if(m<60)return "ESKA Live Remix — Steve Aoki";
            if(m<540)return "ESKA Live Remix — Robin Schulz";
            if(m<780)return "WEEKEND Z HITAMI NA CZASIE — Mati Fuczyło";
            return "WEEKEND Z HITAMI NA CZASIE — Łukasz Zduńczyk";
        }
        return "";
    }

    private static String downloadPr(String url) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setInstanceFollowRedirects(true);c.setConnectTimeout(6000);c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36");
        c.setRequestProperty("Accept","application/json, text/plain, */*");
        c.setRequestProperty("Accept-Language","pl-PL,pl;q=0.9");
        c.setRequestProperty("Origin","https://player.polskieradio.pl");
        c.setRequestProperty("Referer","https://player.polskieradio.pl/");
        return read(c);
    }

    private static String download(String url) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setInstanceFollowRedirects(true);c.setConnectTimeout(6000);c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36");
        c.setRequestProperty("Accept","text/html,application/xhtml+xml,*/*;q=0.8");
        return read(c);
    }

    private static String read(HttpURLConnection c) throws Exception {
        try {
            int code=c.getResponseCode();if(code<200||code>=300)return "";
            BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8));
            StringBuilder sb=new StringBuilder();char[] b=new char[8192];int n,total=0;
            while((n=br.read(b))>0&&total<1000000){sb.append(b,0,n);total+=n;}
            return sb.toString();
        } finally { c.disconnect(); }
    }

    @SuppressWarnings("deprecation")
    private static String htmlText(String html){
        if(html==null||html.isEmpty())return "";
        try{return Html.fromHtml(html).toString();}catch(Throwable e){return html.replaceAll("(?s)<[^>]+>","\\n");}
    }

    private static String nextMeaningful(String[] lines,int start,int limit){
        for(int i=start;i<lines.length&&i<start+limit;i++){
            String s=clean(lines[i]);
            if(!s.isEmpty()&&!s.matches("^([01]?\\d|2[0-3]):[0-5]\\d$"))return s;
        }
        return "";
    }
    private static int minutesNow(){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("Europe/Warsaw"));return c.get(Calendar.HOUR_OF_DAY)*60+c.get(Calendar.MINUTE);}
    private static int minutesFromIso(String s){Matcher m=Pattern.compile("T([01]\\d|2[0-3]):([0-5]\\d)").matcher(s==null?"":s);return m.find()?Integer.parseInt(m.group(1))*60+Integer.parseInt(m.group(2)):-1;}
    private static String firstHeader(HttpURLConnection c,String...keys){for(String k:keys){String v=c.getHeaderField(k);if(v!=null&&!v.trim().isEmpty())return v.trim();}return "";}
    private static int parseInt(String s){try{return Integer.parseInt(s.trim());}catch(Exception e){return -1;}}
    private static String[] splitSong(String title){for(String sep:new String[]{" – "," — "," - "}){int p=title.indexOf(sep);if(p>0&&p<title.length()-sep.length()){String a=clean(title.substring(0,p)),t=clean(title.substring(p+sep.length()));if(!a.isEmpty()&&!t.isEmpty())return new String[]{a,t};}}return null;}
    private static boolean goodProgram(String s){String x=clean(s);String l=x.toLowerCase(Locale.ROOT);return x.length()>2&&!l.equals("programy")&&!l.equals("ramówka")&&!l.equals("ramowka")&&!l.startsWith("fakty:");}
    private static String clean(String s){if(s==null)return "";String x=s.replace('\n',' ').replace('\r',' ').replace('\u00a0',' ').trim();while(x.contains("  "))x=x.replace("  "," ");return x;}
}
