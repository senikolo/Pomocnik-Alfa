from pathlib import Path

# Version
p=Path("app/build.gradle")
s=p.read_text()
assert "versionCode 10730" in s
s=s.replace("versionCode 10730","versionCode 10731")
s=s.replace("versionName '1.7.30'","versionName '1.7.31'")
p.write_text(s)

# Dashboard aviation tile
p=Path("app/src/main/java/com/ispina/lokalnie/MainActivity.java")
s=p.read_text()
old='addActionCard("Lotnictwo · Kraków","EPKK Tower / Approach przez oficjalny odsłuch LiveATC.",v->startActivity(new Intent(this,AviationActivity.class)));'
new='addActionCard("Lotnictwo · Kraków","EPKK Tower / Approach. Jeden kafelek uruchamia nasłuch w aplikacji.",v->startActivity(new Intent(this,AviationActivity.class)));'
assert old in s
p.write_text(s.replace(old,new))

# RadioService: reject promos/ads and repair broken UTF-8 metadata
p=Path("app/src/main/java/com/ispina/lokalnie/radio/RadioService.java")
s=p.read_text()
old=r'''    private static String cleanText(CharSequence value){
        if(value==null)return "";
        String x=value.toString().replace('\n',' ').replace('\r',' ').trim();
        while(x.contains("  "))x=x.replace("  "," ");
        return x;
    }

    private boolean isUsefulMetadata(String value){
        if(value==null)return false;
        String v=value.trim();
        if(v.isEmpty()||v.equalsIgnoreCase(lastName)||v.equalsIgnoreCase("Radio")||v.equalsIgnoreCase("Pomocnik Alfa"))return false;
        String low=v.toLowerCase(java.util.Locale.ROOT);
        return !(low.equals("radio • pomocnik alfa")||low.contains("null")||low.equals("unknown"));
    }
'''
new=r'''    private static String cleanText(CharSequence value){
        if(value==null)return "";
        String x=value.toString().replace('\n',' ').replace('\r',' ').trim();
        while(x.contains("  "))x=x.replace("  "," ");
        return repairMojibake(x);
    }

    private static String repairMojibake(String value){
        if(value==null||value.isEmpty())return "";
        String x=value;
        if(!(x.contains("Ã")||x.contains("Å")||x.contains("Ä")||x.contains("â")||x.contains("Â")))return x;
        try{
            String fixed=new String(x.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1),java.nio.charset.StandardCharsets.UTF_8);
            return mojibakeScore(fixed)<mojibakeScore(x)?fixed:x;
        }catch(Throwable ignored){return x;}
    }

    private static int mojibakeScore(String value){
        int n=0;
        for(char c:value.toCharArray())if(c=='Ã'||c=='Å'||c=='Ä'||c=='â'||c=='Â'||c=='�')n++;
        return n;
    }

    private static String normalized(String value){
        if(value==null)return "";
        String x=java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(java.util.Locale.ROOT);
        return x.replaceAll("[^a-z0-9]+"," ").trim();
    }

    private boolean isStationPromoOrJunk(String value){
        String low=normalized(value);
        if(low.isEmpty())return true;
        String station=normalized(lastName);
        if(!station.isEmpty()&&low.equals(station))return true;
        if(low.equals("radio")||low.equals("pomocnik alfa")||low.equals("radio pomocnik alfa")||low.equals("unknown"))return true;
        if(low.equals("reklama")||low.startsWith("reklama ")||low.equals("advertisement")||low.equals("werbung"))return true;
        if(low.contains("pobierz apke")||low.contains("pobierz aplikacje")||low.contains("sluchaj wszedzie"))return true;
        if(low.contains("w rytmie hitow")&&station.contains("vox"))return true;
        if(low.contains("mehr 80er")&&low.contains("mehr 90er"))return true;
        if(low.contains("wir lieben bayern")&&low.contains("wir lieben musik"))return true;
        if(low.contains("der beste rock nonstop"))return true;
        if(low.contains("bayerns hitradio nummer 1")&&low.contains("hits von heute"))return true;
        if(station.contains("zlote przeboje")&&(low.equals("zlote przeboje zlote przeboje")||low.equals("zlote przeboje")))return true;
        if(station.contains("rmf maxx")&&low.equals("rmf maxx"))return true;
        if(station.contains("eska rock")&&low.startsWith("eska rock")&&low.contains("pobierz"))return true;
        return false;
    }

    private boolean isUsefulMetadata(String value){
        if(value==null)return false;
        String v=cleanText(value);
        if(v.isEmpty()||v.equalsIgnoreCase(lastName)||v.equalsIgnoreCase("Radio")||v.equalsIgnoreCase("Pomocnik Alfa"))return false;
        String low=v.toLowerCase(java.util.Locale.ROOT);
        return !(low.equals("radio • pomocnik alfa")||low.contains("null")||low.equals("unknown")||isStationPromoOrJunk(v));
    }
'''
assert old in s, "RadioService metadata block not found"
p.write_text(s.replace(old,new))

# RadioInfoPlus: sanitize ICY before merge so an ad/slogan cannot block better official info
p=Path("app/src/main/java/com/ispina/lokalnie/radio/RadioInfoPlus.java")
s=p.read_text()
old=r'''    public void lookup(String station,String streamUrl,Callback callback){
        if(closed)return;
        final String n=station==null?"":station.trim();
        final String u=streamUrl==null?"":streamUrl.trim();
        executor.execute(() -> {
            Result out=new Result();
            try{out.merge(probeIcy(u));}catch(Throwable ignored){}
            try{out.merge(fetchOfficial(n));}catch(Throwable ignored){}
            if(closed)return;
            main.post(() -> {if(!closed&&callback!=null)callback.onResult(out);});
        });
    }
'''
new=r'''    public void lookup(String station,String streamUrl,Callback callback){
        if(closed)return;
        final String n=station==null?"":station.trim();
        final String u=streamUrl==null?"":streamUrl.trim();
        executor.execute(() -> {
            Result out=new Result();
            try{
                Result icy=probeIcy(u);
                sanitize(n,icy);
                out.merge(icy);
            }catch(Throwable ignored){}
            try{
                Result official=fetchOfficial(n);
                sanitize(n,official);
                out.merge(official);
            }catch(Throwable ignored){}
            if(closed)return;
            main.post(() -> {if(!closed&&callback!=null)callback.onResult(out);});
        });
    }
'''
assert old in s, "RadioInfoPlus lookup block not found"
s=s.replace(old,new)
s=s.replace('Pomocnik-Alfa/1.7.29 RadioInfo+','Pomocnik-Alfa/1.7.31 RadioInfo++')

anchor='''    private static boolean eq(String a,String b){return a!=null&&a.equalsIgnoreCase(b);}
'''
helpers=r'''    private static void sanitize(String station,Result r){
        if(r==null)return;
        r.program=clean(r.program);
        r.artist=clean(r.artist);
        r.track=clean(r.track);
        if(isLowQuality(station,r.track)){r.track="";r.artist="";}
        if(!r.artist.isEmpty()&&isLowQuality(station,r.artist))r.artist="";
        if(isLowQuality(station,r.program))r.program="";
        if(r.track.isEmpty()&&r.program.isEmpty())r.source="";
    }

    private static boolean isLowQuality(String station,String value){
        String low=normalized(value),st=normalized(station);
        if(low.isEmpty())return false;
        if(!st.isEmpty()&&low.equals(st))return true;
        if(low.equals("radio")||low.equals("unknown")||low.equals("reklama")||low.startsWith("reklama ")||low.equals("advertisement")||low.equals("werbung"))return true;
        if(low.contains("pobierz apke")||low.contains("pobierz aplikacje")||low.contains("sluchaj wszedzie"))return true;
        if(st.contains("vox")&&low.contains("w rytmie hitow"))return true;
        if(low.contains("mehr 80er")&&low.contains("mehr 90er"))return true;
        if(low.contains("wir lieben bayern")&&low.contains("wir lieben musik"))return true;
        if(low.contains("der beste rock nonstop"))return true;
        if(low.contains("bayerns hitradio nummer 1")&&low.contains("hits von heute"))return true;
        if(st.contains("zlote przeboje")&&(low.equals("zlote przeboje")||low.equals("zlote przeboje zlote przeboje")))return true;
        if(st.contains("rmf maxx")&&low.equals("rmf maxx"))return true;
        if(st.contains("eska rock")&&low.startsWith("eska rock")&&low.contains("pobierz"))return true;
        return false;
    }

    private static String normalized(String value){
        if(value==null)return "";
        String x=java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT);
        return x.replaceAll("[^a-z0-9]+"," ").trim();
    }

    private static String repairMojibake(String value){
        if(value==null||value.isEmpty())return "";
        String x=value;
        if(!(x.contains("Ã")||x.contains("Å")||x.contains("Ä")||x.contains("â")||x.contains("Â")))return x;
        try{
            String fixed=new String(x.getBytes(StandardCharsets.ISO_8859_1),StandardCharsets.UTF_8);
            return mojibakeScore(fixed)<mojibakeScore(x)?fixed:x;
        }catch(Throwable ignored){return x;}
    }

    private static int mojibakeScore(String value){
        int n=0;
        for(char c:value.toCharArray())if(c=='Ã'||c=='Å'||c=='Ä'||c=='â'||c=='Â'||c=='�')n++;
        return n;
    }

'''
assert anchor in s, "RadioInfoPlus helper anchor not found"
s=s.replace(anchor,helpers+anchor)
old_clean=r'''    private static String clean(CharSequence s){if(s==null)return "";String x=s.toString().replace('\n',' ').replace('\r',' ').trim();while(x.contains("  "))x=x.replace("  "," ");return x;}
'''
new_clean=r'''    private static String clean(CharSequence s){if(s==null)return "";String x=s.toString().replace('\n',' ').replace('\r',' ').trim();while(x.contains("  "))x=x.replace("  "," ");return repairMojibake(x);}
'''
assert old_clean in s, "RadioInfoPlus clean method not found"
p.write_text(s.replace(old_clean,new_clean))

print("1.7.31 source transformations applied")
