#!/usr/bin/env python3
"""Apply Pomocnik Alfa radio country groups and final menu order (PA 1.7.45)."""
from pathlib import Path
import re

root=Path("project/app/src/main/java/com/ispina/lokalnie")
radio=root/"RadioActivity.java"
s=radio.read_text(encoding="utf-8")
anchor='            {"Deutschlandfunk Kultur","https://st02.sslstream.dlf.de/dlf/02/low/aac/stream.aac?aggregator=web"}'
assert s.count(anchor)==1,"Unknown PA radio station list"
added = '''            {"Москва FM 92.0","https://icecast.vgtrk.cdnvideo.ru/moscowfm128"},
            {"Русское Радио","https://rusradio.hostingradio.ru/rusradio96.aacp"},
            {"Европа Плюс","https://europaplus.hostingradio.ru:8030/ep128"},
            {"Ретро FM","https://retro.hostingradio.ru:8043/retro128"},
            {"Радио Maximum","https://maximum.hostingradio.ru/maximum96.aacp"},
            {"Маруся FM","https://radio-holding.ru:9433/marusya_default"}'''
s=s.replace(anchor,anchor+",\n"+added)
# Keep original player, station metadata, notification, and favorites storage.
start=s.index("    private void renderStations(){")
end=s.index("    private void addStation(",start)
s=s[:start]+'''    private static String countryCode(String name){
        if(name.equals("1.FM Birds")) return "OTHER";
        if(name.equals("Москва FM 92.0") || name.equals("Русское Радио") ||
           name.equals("Европа Плюс") || name.equals("Ретро FM") ||
           name.equals("Радио Maximum") || name.equals("Маруся FM")) return "RU";
        if(name.equals("Radio Arabella München") || name.equals("Antenne Bayern") ||
           name.equals("Bayern 2") || name.equals("Bayern 3") ||
           name.equals("Deutschlandfunk") || name.equals("ROCK ANTENNE") ||
           name.equals("BR24") || name.equals("BR-KLASSIK") ||
           name.equals("BR Heimat") || name.equals("Deutschlandfunk Kultur")) return "DE";
        return "PL";
    }
    private static String countryFlag(String code){
        if("PL".equals(code))return "🇵🇱";
        if("DE".equals(code))return "🇩🇪";
        if("RU".equals(code))return "🇷🇺";
        return "🌐";
    }
    private static String countryName(String code){
        if("PL".equals(code))return "Polska";
        if("DE".equals(code))return "Niemcy";
        if("RU".equals(code))return "Rosja";
        return "Inne";
    }
    private void renderStations(){
        list.removeAllViews();
        favoritesToggle.setText(onlyFavorites?"★ Wszystkie":"☆ Ulubione");
        int shown=0;
        for(String code : new String[]{"PL","DE","RU","OTHER"}){
            int inGroup=0;
            for(String[] station:STATIONS)
                if(code.equals(countryCode(station[0])) && (!onlyFavorites || isFavorite(station[0])))
                    inGroup++;
            if(inGroup==0)continue;
            TextView label=NativeUi.text(this,countryFlag(code)+"  "+countryName(code)+" · "+inGroup,18,true);
            label.setPadding(NativeUi.dp(this,5),NativeUi.dp(this,14),0,NativeUi.dp(this,9));
            list.addView(label);
            for(String[] station:STATIONS){
                if(!code.equals(countryCode(station[0])))continue;
                if(onlyFavorites && !isFavorite(station[0]))continue;
                addStation(list,station[0],station[1]);
                shown++;
            }
        }
        if(shown==0){
            LinearLayout c=NativeUi.card(this);
            c.addView(NativeUi.text(this,"Nie masz jeszcze ulubionych stacji",16,true));
            NativeUi.addSpacer(c,this,4);
            c.addView(NativeUi.muted(this,"Wróć do wszystkich stacji i dotknij ☆ przy wybranej pozycji.",12));
            list.addView(c);
        }
    }

'''+s[end:]
before='''        tx.addView(NativeUi.text(this,name,16,true));
        boolean de=url.contains("rndfnk")||url.contains("antenne")||url.contains("arabella")||name.contains("Deutschland")||name.startsWith("BR");
        tx.addView(NativeUi.muted(this,de?"Niemcy • stream live":"Stream live",11));'''
after='''        String country=countryCode(name);
        tx.addView(NativeUi.text(this,countryFlag(country)+"  "+name,16,true));
        tx.addView(NativeUi.muted(this,countryName(country)+" • stream na żywo",11));'''
assert s.count(before)==1,"Unexpected PA RadioActivity rendering"
s=s.replace(before,after)
radio.write_text(s,encoding="utf-8")

main=root/"MainActivity.java"
m=main.read_text(encoding="utf-8")
airport='        addActionCard("Lotnictwo · Kraków","EPKK Tower / Approach. Dotknij i słuchaj bez przekierowania do strony.",v->startActivity(new Intent(this,AviationActivity.class)));\n'
settings='        addActionCard("Ustawienia","12 odcieni motywów, płynna zmiana kolorów i regulacja wibracji dotyku.",v->startActivity(new Intent(this,AppearanceActivity.class)));\n'
notes='        addActionCard("Notatki","Szybka lokalna notatka bez konta i chmury.",v->startActivity(new Intent(this,NotesActivity.class)));\n'
assert m.count(airport)==1 and m.count(settings)==1 and m.count(notes)==1,"Menu template changed"
m=m.replace(airport,"").replace(settings,"")
m=m.replace(notes,notes+
'        addActionCard("Nasłuch z lotnisk","Kraków-Balice i Warszawa-Chopina. Odtwarzanie w tle, jeśli transmisja jest dostępna.",v->startActivity(new Intent(this,AviationActivity.class)));\n'+settings)
m=m.replace('addActionCard("Radio","Polskie i niemieckie stacje. Odtwarzanie w tle.",',
'addActionCard("Radio","Stacje polskie, niemieckie i rosyjskie, z flagami. Grają również w tle.",')
m=re.sub(r'POMOCNIK ALFA 1\.7\.\d+','POMOCNIK ALFA 1.7.45',m)
main.write_text(m,encoding="utf-8")
print("PASS: 6 Russian stations, country headers + flags, favorites, aviation penultimate, settings last")
