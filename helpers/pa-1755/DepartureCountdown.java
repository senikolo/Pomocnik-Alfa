package com.ispina.lokalnie.transit;

/** In-memory departure countdown; never confuses a schedule with a prediction. */
public final class DepartureCountdown {
    private DepartureCountdown(){}
    public static String label(long scheduledAtMillis,long nowMillis,Integer confirmedDelayMinutes){
        if(scheduledAtMillis<=0)return "Nieznana godzina";
        long expected=scheduledAtMillis;
        boolean realtime=confirmedDelayMinutes!=null;
        if(realtime){
            if(Math.abs((long)confirmedDelayMinutes)>120)realtime=false;
            else expected+=confirmedDelayMinutes*60000L;
        }
        long difference=expected-nowMillis;
        if(difference < -60000L)
            return realtime?"Po prognozowanej godzinie":"Po godzinie rozkładowej";
        if(difference <= 0L)return realtime?"LIVE · teraz":"Planowo · teraz";
        long minutes=(difference+59999L)/60000L;
        return (realtime?"LIVE za ":"Planowo za ")+minutes+" min";
    }
}