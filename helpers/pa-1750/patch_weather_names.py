#!/usr/bin/env python3
"""Weather labels: named area and street, never latitude/longitude in the UI."""
from pathlib import Path

base=Path("project/app/src/main/java/com/ispina/lokalnie")
weather=base/"WeatherActivity.java"
s=weather.read_text(encoding="utf-8")
old='String quick=String.format(java.util.Locale.US,"GPS %.4f, %.4f",fix.getLatitude(),fix.getLongitude());'
assert s.count(old)==1,"GPS quick label changed unexpectedly"
s=s.replace(old,'String quick="Twoja okolica";')
assert 'gpsDetailsView.setText("Okolica: "+details.label+' in s
assert 'cityStatus.setText("Pogoda dla: "+details.label+' in s
weather.write_text(s,encoding="utf-8")

geo=base/"GpsMicroArea.java"
s=geo.read_text(encoding="utf-8")
a=s.index('        String coordinates=String.format(Locale.US,"GPS %.4f, %.4f",location.getLatitude(),location.getLongitude());')
b=s.index('\n    }',a)
s=s[:a]+'''        return new Result("Twoja okolica",
            "Nie udało się ustalić nazwy osiedla ani ulicy. Sprawdź lokalizację i spróbuj ponownie.",
            "nazwa miejsca niedostępna");'''+s[b:]
old='''                String human=!micro.isEmpty()?micro:(!district.isEmpty()?district:(!street.isEmpty()?"ul. "+street:""));
                String label=combine(human,city,"");'''
new='''                // Prioritize a verified neighborhood and the nearest named street.
                // Never infer a crossroad or building address from reverse geocoding.
                String human=!micro.isEmpty()?micro:district;
                String road=street.isEmpty()?"":"ul. "+street;
                String label=combine(human,road,city);'''
assert s.count(old)==1
s=s.replace(old,new)
old='''                if(city.isEmpty() && !fallback.label.startsWith("GPS ")){
                    label=combine(fallback.label,"",micro);
                }'''
new='''                if(label.isEmpty())label=fallback.label;'''
assert s.count(old)==1
s=s.replace(old,new)
assert '"GPS %.4f' not in s
geo.write_text(s,encoding="utf-8")
main=base/"MainActivity.java"
m=main.read_text(encoding="utf-8")
assert "POMOCNIK ALFA 1.7.49" in m
main.write_text(m.replace("POMOCNIK ALFA 1.7.49","POMOCNIK ALFA 1.7.50"),encoding="utf-8")
print("PASS: weather uses place/estate/road labels; no GPS coordinates displayed")
