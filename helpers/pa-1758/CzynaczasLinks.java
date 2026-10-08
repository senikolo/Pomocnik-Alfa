package com.ispina.lokalnie.transit;

/** Open public website links; not a private API or a LIVE data feed. */
public final class CzynaczasLinks {
    private static final String WARSAW="https://czynaczas.pl/warsaw";
    private CzynaczasLinks(){}

    /** Warsaw GTFS encodes stop_complex+platform as six numeric digits.
     * The site supports ?przystanek=701306; do not assume identifiers for other networks.
     */
    public static String forStop(String provider,String stopId){
        if(!"warsaw".equals(provider))return null;
        return stopId!=null && stopId.matches("[0-9]{6}")?
            WARSAW+"?przystanek="+stopId:WARSAW;
    }
}
