#!/usr/bin/env python3
"""PA Transit 1.7.65: higher-contrast cards without noisy, competing colors."""
from pathlib import Path
p=Path("project/app/src/main/java/com/ispina/lokalnie/TransitWowUi.java")
s=p.read_text(encoding="utf-8")
def once(a,b,label):
    global s
    n=s.count(a)
    if n!=1:raise AssertionError(f"{label}: {n}")
    s=s.replace(a,b,1)
once('return dark(a)?0xFFF1F3F6:0xFF1B2938;',
     'return dark(a)?0xFFF5F8FB:0xFF1B2938;',"strong ink")
once('return dark(a)?0xFFBFC9D4:0xFF586779;',
     'return dark(a)?0xFFD6E0E9:0xFF435468;',"strong secondary text")
once('return dark(a)?0xFF202A35:0xFFFFFFFF;',
     'return dark(a)?0xFF202B38:0xFFFFFFFF;',"dark card surface")
once('return dark(a)?0xFF3B4856:0xFFDEE5EB;',
     'return dark(a)?0xFF64768A:0xFFCBD7E2;',"visible card outlines")
once('''    private static final int GREEN=0xFF15805B,AMBER=0xFFB86A18;''',
'''    private static final int GREEN=0xFF15805B,AMBER=0xFFB86A18;
    private static final int GREEN_DARK=0xFF80F0C3,AMBER_DARK=0xFFFFC987;''',
    "high contrast statuses at night")
once('''        e.countdown.setTextColor(isLive?GREEN:inkFromEntry(e));''',
'''        boolean darkMode=(e.card.getResources().getConfiguration().uiMode&
            Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        e.countdown.setTextColor(isLive?(darkMode?GREEN_DARK:GREEN):inkFromEntry(e));''',
     "night live green contrast")
once('''        e.status.setTextColor(isLive?GREEN:0xFF687587);''',
'''        e.status.setTextColor(isLive?(darkMode?GREEN_DARK:GREEN):
            (darkMode?0xFFD6E0E9:0xFF455468));''',
    "status readability")
once('''        e.delay.setText(late);e.delay.setVisibility(late.isEmpty()?View.GONE:View.VISIBLE);''',
'''        e.delay.setText(late);
        e.delay.setTextColor(darkMode?AMBER_DARK:AMBER);
        e.delay.setVisibility(late.isEmpty()?View.GONE:View.VISIBLE);''',
    "night late contrast")
once('''        TextView t=type(a,direction,20,true,ink(a));''',
'''        TextView t=type(a,direction,21,true,ink(a));''',
    "larger direction")
once('''        panel.setBackground(round(a,surface(a),outline(a),17));''',
'''        // Very subtle permanent tint communicates transport type without
        // reducing readability; the line badge remains the strong accent.
        int tint=surface(a);
        int border=outline(a);
        if(!dark(a)){
            if("bus".equals(d.mode)){tint=0xFFF7FAFD;border=0xFFC5D8E9;}
            else if("tram".equals(d.mode)){tint=0xFFFFFAF9;border=0xFFEAD1CD;}
            else if("skm".equals(d.mode)||"rail".equals(d.mode)||
                    "train".equals(d.mode)){tint=0xFFFBF9FF;border=0xFFDED4EE;}
            else if("metro".equals(d.mode)){tint=0xFFF5FCFB;border=0xFFC7E3DD;}
        }
        panel.setBackground(round(a,tint,border,17));''',
    "fixed toned card backgrounds")
once('''        TextView stop=type(a,stopName(d.stop),14,true,subtle(a));''',
'''        TextView stop=type(a,stopName(d.stop),16,true,ink(a));''',
    "station name high contrast")
once('''        TextView type=type(a,mode(d.mode),11,true,transitColor(d.mode));''',
'''        TextView type=type(a,mode(d.mode),12,true,dark(a)?
            (("tram".equals(d.mode))?0xFFFFBCB6:
            ("metro".equals(d.mode))?0xFF9CEBDD:
            ("skm".equals(d.mode))?0xFFD7C6FF:0xFFB8DAFF):
            transitColor(d.mode));''',
    "mode readable on dark")
p.write_text(s,encoding="utf-8")
print("PASS: stable subtle per-mode card tones and legible secondary, stop, night LIVE and amber text")
