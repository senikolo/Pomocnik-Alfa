package com.ispina.lokalnie.transit;

import java.text.Normalizer;
import java.util.Locale;

/** Case-/diacritic-insensitive search in the already loaded GTFS departures.
 * No GPS calls, network requests or extra user data are retained.
 */
public final class DepartureQuickFilter {
    private DepartureQuickFilter(){}

    private static String fold(String text){
        if(text==null)return "";
        String noAccents=Normalizer.normalize(text.trim().toLowerCase(Locale.ROOT),Normalizer.Form.NFD)
                .replaceAll("\\p{M}","");
        return noAccents.replace('ł','l');
    }

    public static boolean matches(GtfsNearby.Departure d,String query){
        String normalized=fold(query);
        if(normalized.isEmpty())return true;
        if(d==null)return false;
        String line=fold(d.line);
        String dest=fold(d.headsign);
        String stop=d.stop==null?"":fold(d.stop.name);
        String platform=d.stop==null?"":fold(d.stop.code);
        return line.contains(normalized) || dest.contains(normalized) ||
                stop.contains(normalized) || platform.equals(normalized);
    }

    public static long expectedTime(GtfsNearby.Departure d,long now){
        Integer delay=d.confirmedDelayMinutes(now);
        return d.when+(delay==null?0L:delay*60000L);
    }
}
