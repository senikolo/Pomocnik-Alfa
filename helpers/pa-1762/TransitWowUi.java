package com.ispina.lokalnie;

import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.ispina.lokalnie.transit.GtfsNearby;
import com.ispina.lokalnie.transit.DepartureCountdown;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** One quiet, accessible visual language shared by both departures screens. */
public final class TransitWowUi {
    private TransitWowUi(){}
    public static final class Entry {
        public final LinearLayout card;
        public final TextView countdown;
        public final TextView status;
        Entry(LinearLayout c,TextView t,TextView s){card=c;countdown=t;status=s;}
    }
    private static int dp(ThemedActivity activity,int value){return NativeUi.dp(activity,value);}
    private static boolean dark(ThemedActivity a){
        return (a.getResources().getConfiguration().uiMode&
          Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
    }
    public static int ink(ThemedActivity a){return dark(a)?0xFFF1F3F6:0xFF17212E;}
    public static int subtle(ThemedActivity a){return dark(a)?0xFFB7C2D0:0xFF596777;}
    private static int cardBg(ThemedActivity a){return dark(a)?0xFF202A35:0xFFFFFFFF;}
    private static int hairline(ThemedActivity a){return dark(a)?0xFF394756:0xFFDEE5EB;}
    public static GradientDrawable round(ThemedActivity a,int color,int edge,int radius){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);d.setCornerRadius(dp(a,radius));
        d.setStroke(dp(a,1),edge);
        return d;
    }
    public static TextView type(ThemedActivity a,String label,int sp,boolean strong,int color){
        TextView t=NativeUi.text(a,label,sp,strong);
        t.setTextColor(color);t.setIncludeFontPadding(true);
        return t;
    }
    public static TextView largeDirection(ThemedActivity a,String direction){
        TextView view=type(a,direction,20,true,ink(a));
        view.setMaxLines(3);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setContentDescription("Kierunek "+direction);
        return view;
    }
    private static String clock(long millis){
        SimpleDateFormat f=new SimpleDateFormat("HH:mm",new Locale("pl","PL"));
        f.setTimeZone(java.util.TimeZone.getTimeZone("Europe/Warsaw"));
        return f.format(new Date(millis));
    }
    public static String timing(GtfsNearby.Departure departure){
        Integer delay=departure.confirmedDelayMinutes(System.currentTimeMillis());
        return delay==null?"ROZKŁADOWO":delay==0?"LIVE · bez opóźnienia":
            "LIVE · "+(delay>0?"+":"")+delay+" min";
    }
    public static Entry departure(ThemedActivity a,GtfsNearby.Departure d,
           boolean showStop,View.OnClickListener onTap){
        LinearLayout panel=new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(a,13),dp(a,13),dp(a,13),dp(a,12));
        panel.setBackground(round(a,cardBg(a),hairline(a),17));
        panel.setElevation(dp(a,1));
        LinearLayout row=new LinearLayout(a);
        row.setGravity(Gravity.TOP);
        TextView number=type(a,d.line,24,true,ink(a));
        number.setGravity(Gravity.CENTER);
        number.setMinWidth(dp(a,58));
        number.setMinHeight(dp(a,58));
        number.setPadding(dp(a,6),dp(a,7),dp(a,6),dp(a,7));
        number.setBackground(round(a,dark(a)?0xFF354150:0xFFF0F3F5,hairline(a),12));
        number.setContentDescription("Linia "+d.line);
        row.addView(number,new LinearLayout.LayoutParams(dp(a,65),-2));
        LinearLayout main=new LinearLayout(a);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(a,12),0,dp(a,5),0);
        TextView eyebrow=type(a,"KIERUNEK  →",11,true,subtle(a));
        eyebrow.setLetterSpacing(0.08f);
        main.addView(eyebrow);
        main.addView(largeDirection(a,d.headsign));
        row.addView(main,new LinearLayout.LayoutParams(0,-2,1f));
        LinearLayout clockColumn=new LinearLayout(a);
        clockColumn.setOrientation(LinearLayout.VERTICAL);
        clockColumn.setGravity(Gravity.RIGHT);
        TextView tm=type(a,clock(d.when),25,true,ink(a));
        tm.setGravity(Gravity.RIGHT);
        tm.setContentDescription("Godzina odjazdu "+clock(d.when));
        clockColumn.addView(tm);
        TextView when=type(a,"ODJAZD",10,true,subtle(a));
        when.setGravity(Gravity.RIGHT);
        clockColumn.addView(when);
        row.addView(clockColumn,new LinearLayout.LayoutParams(dp(a,79),-2));
        panel.addView(row);
        NativeUi.addSpacer(panel,a,10);
        View rule=new View(a);rule.setBackgroundColor(hairline(a));
        panel.addView(rule,new LinearLayout.LayoutParams(-1,dp(a,1)));
        NativeUi.addSpacer(panel,a,8);
        if(showStop){
            String code=d.stop.code==null||d.stop.code.trim().isEmpty()?"":" · stan. "+d.stop.code;
            TextView stop=type(a,"PRZYSTANEK   "+d.stop.name+code,13,false,subtle(a));
            stop.setMaxLines(2);panel.addView(stop);
            NativeUi.addSpacer(panel,a,5);
        }
        LinearLayout foot=new LinearLayout(a);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        TextView countdown=type(a,DepartureCountdown.label(d.when,System.currentTimeMillis(),
            d.confirmedDelayMinutes(System.currentTimeMillis())),16,true,ink(a));
        foot.addView(countdown,new LinearLayout.LayoutParams(0,-2,1f));
        TextView live=type(a,timing(d),12,true,subtle(a));
        live.setGravity(Gravity.RIGHT);
        foot.addView(live);
        panel.addView(foot);
        panel.setClickable(true);
        panel.setFocusable(true);
        panel.setOnClickListener(onTap);
        panel.setContentDescription("Linia "+d.line+". Kierunek "+d.headsign+
            ". Odjazd "+clock(d.when)+". "+timing(d)+
            ". "+(d.stop==null?"":"Stanowisko "+d.stop.code)+". Dotknij, aby zobaczyć trasę.");
        return new Entry(panel,countdown,live);
    }
}
