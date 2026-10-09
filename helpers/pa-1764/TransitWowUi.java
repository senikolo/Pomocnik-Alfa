package com.ispina.lokalnie;

import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.ispina.lokalnie.transit.GtfsNearby;
import com.ispina.lokalnie.transit.DepartureCountdown;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * PA Transit 1.7.64: fixed, restrained transport taxonomy and explicit data provenance.
 * Blue buses, brick trams, violet railway, teal metro, green verified live,
 * amber delays. Color is always accompanied by a text/icon label.
 */
public final class TransitWowUi {
    private TransitWowUi(){}
    private static final Locale PL=new Locale("pl","PL");
    private static final int BUS=0xFF2463A6,TRAM=0xFFB94E48;
    private static final int RAIL=0xFF704FA0,METRO=0xFF087E79;
    private static final int GREEN=0xFF15805B,AMBER=0xFFB86A18;
    public static final class Entry {
        public final LinearLayout card;
        public final TextView countdown,status,clock,delay;
        Entry(LinearLayout c,TextView t,TextView s,TextView w,TextView l){
            card=c;countdown=t;status=s;clock=w;delay=l;
        }
    }
    private static int dp(ThemedActivity a,int size){return NativeUi.dp(a,size);}
    private static boolean dark(ThemedActivity a){
        return (a.getResources().getConfiguration().uiMode&
            Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
    }
    public static int ink(ThemedActivity a){return dark(a)?0xFFF1F3F6:0xFF1B2938;}
    public static int subtle(ThemedActivity a){return dark(a)?0xFFBFC9D4:0xFF586779;}
    private static int surface(ThemedActivity a){return dark(a)?0xFF202A35:0xFFFFFFFF;}
    private static int outline(ThemedActivity a){return dark(a)?0xFF3B4856:0xFFDEE5EB;}
    public static GradientDrawable round(ThemedActivity a,int color,int edge,int radius){
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(color);bg.setCornerRadius(dp(a,radius));
        bg.setStroke(dp(a,1),edge);return bg;
    }
    public static TextView type(ThemedActivity a,String label,int sp,boolean strong,int color){
        TextView t=NativeUi.text(a,label,sp,strong);
        t.setTextColor(color);t.setIncludeFontPadding(true);
        return t;
    }
    private static String mode(String mode){
        if("tram".equals(mode))return "TRAMWAJ";
        if("skm".equals(mode)||"train".equals(mode)||"rail".equals(mode))return "KOLEJ";
        if("metro".equals(mode))return "METRO";
        return "AUTOBUS";
    }
    private static String icon(String mode){
        if("tram".equals(mode))return "🚋";
        if("skm".equals(mode)||"train".equals(mode)||"rail".equals(mode))return "🚆";
        if("metro".equals(mode))return "🚇";
        return "🚌";
    }
    private static int transitColor(String mode){
        if("tram".equals(mode))return TRAM;
        if("skm".equals(mode)||"train".equals(mode)||"rail".equals(mode))return RAIL;
        if("metro".equals(mode))return METRO;
        return BUS;
    }
    public static String stopName(GtfsNearby.Stop stop){
        if(stop==null)return "NIEZNANY PRZYSTANEK";
        String name=stop.name==null?"Przystanek":stop.name.trim();
        // Warsaw platform is the final two DIGITS of the official six-digit ID.
        // This resolves the typographic zero/O ambiguity for platforms 01, 02...
        String number=stop.id!=null&&stop.id.matches("[0-9]{6}")?
            stop.id.substring(4):(stop.code==null?"":stop.code.trim());
        if(number.matches("[0-9]"))number="0"+number;
        return (name+(number.isEmpty()?"":" "+number)).toUpperCase(PL);
    }
    public static String clock(long millis){
        SimpleDateFormat f=new SimpleDateFormat("HH:mm",PL);
        f.setTimeZone(java.util.TimeZone.getTimeZone("Europe/Warsaw"));
        return f.format(new Date(millis));
    }
    public static boolean live(GtfsNearby.Departure d,long now){
        return d!=null && (d.confirmedDelayMinutes(now)!=null||d.hasGpsEstimate(now));
    }
    public static long expected(GtfsNearby.Departure d,long now){
        if(d==null)return Long.MIN_VALUE;
        Integer delay=d.confirmedDelayMinutes(now);
        if(delay!=null)return d.when+delay.longValue()*60000L;
        if(d.hasGpsEstimate(now))return d.gpsEtaWhenMillis;
        return d.when;
    }
    public static boolean shouldDisplay(GtfsNearby.Departure d,long now,boolean onlyLive){
        if(d==null || (onlyLive&&!live(d,now)))return false;
        long estimate=expected(d,now);
        // Scheduled departures that passed do not remain visible unless fresh LIVE
        // still predicts this bus at this stop in the future.
        if(estimate<now-10000L)return false;
        if(d.when<now-10000L && !live(d,now))return false;
        return true;
    }
    public static String countdown(GtfsNearby.Departure d,long now){
        if(d.hasGpsEstimate(now)&&d.confirmedDelayMinutes(now)==null){
            long sec=(d.gpsEtaWhenMillis-now)/1000L;
            if(sec<=20)return "TERAZ · GPS";
            return "za "+Math.max(1,(sec+59L)/60L)+" min · GPS";
        }
        return DepartureCountdown.label(d.when,now,d.confirmedDelayMinutes(now));
    }
    public static String timing(GtfsNearby.Departure d){
        long now=System.currentTimeMillis();
        Integer delay=d.confirmedDelayMinutes(now);
        if(delay!=null)return "LIVE · POTWIERDZONE";
        if(d.hasGpsEstimate(now))return "GPS LIVE · SZACUNEK";
        return "ROZKŁADOWO";
    }
    private static String delayText(GtfsNearby.Departure d,long now){
        if(!live(d,now))return "";
        long minutes=Math.round((expected(d,now)-d.when)/60000.0);
        if(minutes>=2)return "Opóźnienie "+(d.hasGpsEstimate(now)?"ok. ":"")+"+"+minutes+" min";
        if(minutes<=-2)return "Przed czasem "+Math.abs(minutes)+" min";
        return "";
    }
    public static void update(Entry e,GtfsNearby.Departure d){
        long now=System.currentTimeMillis();
        boolean isLive=live(d,now);
        e.countdown.setText(countdown(d,now));
        e.countdown.setTextColor(isLive?GREEN:inkFromEntry(e));
        e.status.setText(timing(d));
        e.status.setTextColor(isLive?GREEN:0xFF687587);
        e.clock.setText(clock(expected(d,now)));
        e.clock.setContentDescription(isLive?"Czas na żywo, prognoza "+clock(expected(d,now)):
            "Odjazd planowy "+clock(d.when));
        String late=delayText(d,now);
        e.delay.setText(late);e.delay.setVisibility(late.isEmpty()?View.GONE:View.VISIBLE);
        e.card.setVisibility(shouldDisplay(d,now,false)?View.VISIBLE:View.GONE);
    }
    private static int inkFromEntry(Entry e){return e.clock.getCurrentTextColor();}
    public static TextView largeDirection(ThemedActivity a,String direction){
        TextView t=type(a,direction,20,true,ink(a));
        t.setMaxLines(3);t.setEllipsize(TextUtils.TruncateAt.END);
        t.setContentDescription("Kierunek "+direction);
        return t;
    }
    public static Entry departure(ThemedActivity a,GtfsNearby.Departure d,
                                  boolean showStop,View.OnClickListener onTap){
        LinearLayout panel=new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(a,13),dp(a,13),dp(a,13),dp(a,12));
        panel.setBackground(round(a,surface(a),outline(a),17));
        panel.setElevation(dp(a,1));
        LinearLayout row=new LinearLayout(a);
        row.setGravity(Gravity.TOP);
        LinearLayout badge=new LinearLayout(a);
        badge.setGravity(Gravity.CENTER);badge.setOrientation(LinearLayout.VERTICAL);
        badge.setPadding(dp(a,3),dp(a,5),dp(a,3),dp(a,7));
        badge.setBackground(round(a,transitColor(d.mode),transitColor(d.mode),12));
        TextView pict=type(a,icon(d.mode),20,true,0xFFFFFFFF);
        pict.setGravity(Gravity.CENTER);badge.addView(pict);
        TextView line=type(a,d.line,23,true,0xFFFFFFFF);
        line.setGravity(Gravity.CENTER);line.setSingleLine(true);
        line.setAutoSizeTextTypeUniformWithConfiguration(15,23,1,
             android.util.TypedValue.COMPLEX_UNIT_SP);
        badge.addView(line,new LinearLayout.LayoutParams(-1,dp(a,31)));
        row.addView(badge,new LinearLayout.LayoutParams(dp(a,68),dp(a,75)));
        LinearLayout main=new LinearLayout(a);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(a,11),dp(a,1),dp(a,4),0);
        TextView eyebrow=type(a,"KIERUNEK  →",11,true,subtle(a));
        eyebrow.setLetterSpacing(0.08f);
        main.addView(eyebrow);
        main.addView(largeDirection(a,d.headsign));
        row.addView(main,new LinearLayout.LayoutParams(0,-2,1f));
        LinearLayout right=new LinearLayout(a);
        right.setGravity(Gravity.RIGHT);
        right.setOrientation(LinearLayout.VERTICAL);
        TextView when=type(a,clock(d.when),24,true,ink(a));
        when.setGravity(Gravity.RIGHT);right.addView(when);
        TextView whenCaption=type(a,"ODJAZD",10,true,subtle(a));
        whenCaption.setGravity(Gravity.RIGHT);right.addView(whenCaption);
        row.addView(right,new LinearLayout.LayoutParams(dp(a,76),-2));
        panel.addView(row);
        NativeUi.addSpacer(panel,a,5);
        TextView type=type(a,mode(d.mode),11,true,transitColor(d.mode));
        type.setLetterSpacing(0.06f);
        panel.addView(type);
        NativeUi.addSpacer(panel,a,8);
        View rule=new View(a);rule.setBackgroundColor(outline(a));
        panel.addView(rule,new LinearLayout.LayoutParams(-1,dp(a,1)));
        NativeUi.addSpacer(panel,a,8);
        if(showStop){
            TextView stop=type(a,stopName(d.stop),14,true,subtle(a));
            stop.setMaxLines(2);
            stop.setContentDescription("Przystanek "+stopName(d.stop));
            panel.addView(stop);
            NativeUi.addSpacer(panel,a,8);
        }
        LinearLayout foot=new LinearLayout(a);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        TextView countdown=type(a,"",17,true,ink(a));
        foot.addView(countdown,new LinearLayout.LayoutParams(0,-2,1f));
        TextView status=type(a,"",12,true,subtle(a));
        status.setGravity(Gravity.RIGHT);
        foot.addView(status);
        panel.addView(foot);
        TextView late=type(a,"",12,true,AMBER);
        late.setGravity(Gravity.RIGHT);
        late.setVisibility(View.GONE);
        panel.addView(late);
        panel.setClickable(true);panel.setFocusable(true);
        panel.setOnClickListener(onTap);
        panel.setContentDescription(mode(d.mode)+". Linia "+d.line+". Kierunek "+
           d.headsign+". Przystanek "+stopName(d.stop)+
           ". Planowy odjazd "+clock(d.when)+". Dotknij, aby zobaczyć trasę.");
        Entry e=new Entry(panel,countdown,status,when,late);
        update(e,d);
        return e;
    }
}
