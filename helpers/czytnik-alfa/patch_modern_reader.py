from pathlib import Path

rp = Path("project/app/src/main/java/com/ispina/lokalnie/ReaderActivity.java")
s = rp.read_text()

s = s.replace(
    'private Button openButton, lastButton, minusButton, plusButton, themeButton, prevButton, nextButton;',
    'private Button openButton, lastButton, minusButton, plusButton, themeButton, readingButton, prevButton, nextButton;'
)
s = s.replace(
    'private int theme = 0;',
    'private int theme = 0;\n    private float lineHeight = 1.58f;\n    private int marginDp = 18;\n    private int fontMode = 0;'
)
s = s.replace(
    'theme = prefs.getInt("theme", 0);',
    'theme = prefs.getInt("theme", 0);\n        lineHeight = prefs.getInt("line_height_100", 158) / 100f;\n        marginDp = prefs.getInt("margin_dp", 18);\n        fontMode = prefs.getInt("font_mode", 0);'
)

oldrow = '''        LinearLayout row2 = new LinearLayout(this);
        row2.setGravity(Gravity.CENTER_VERTICAL);
        minusButton = NativeUi.button(this, "A−", true);
        plusButton = NativeUi.button(this, "A+", true);
        themeButton = NativeUi.button(this, themeLabel(), true);
        prevButton = NativeUi.button(this, "‹", true);
        nextButton = NativeUi.button(this, "›", true);
        row2.addView(minusButton, new LinearLayout.LayoutParams(0, -2, 1));
        addGap(row2, 6);
        row2.addView(plusButton, new LinearLayout.LayoutParams(0, -2, 1));
        addGap(row2, 6);
        row2.addView(themeButton, new LinearLayout.LayoutParams(0, -2, 1.6f));
        addGap(row2, 6);
        row2.addView(prevButton, new LinearLayout.LayoutParams(0, -2, 1));
        addGap(row2, 6);
        row2.addView(nextButton, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams row2p = new LinearLayout.LayoutParams(-1, -2); row2p.topMargin = NativeUi.dp(this, 8);
        controls.addView(row2, row2p);
'''
newrow = '''        LinearLayout row2 = new LinearLayout(this);
        row2.setGravity(Gravity.CENTER_VERTICAL);
        minusButton = NativeUi.button(this, "A−", true);
        plusButton = NativeUi.button(this, "A+", true);
        themeButton = NativeUi.button(this, themeLabel(), true);
        readingButton = NativeUi.button(this, "Aa Czytanie", true);
        row2.addView(minusButton, new LinearLayout.LayoutParams(0, -2, 1));
        addGap(row2, 6);
        row2.addView(plusButton, new LinearLayout.LayoutParams(0, -2, 1));
        addGap(row2, 6);
        row2.addView(themeButton, new LinearLayout.LayoutParams(0, -2, 1.55f));
        addGap(row2, 6);
        row2.addView(readingButton, new LinearLayout.LayoutParams(0, -2, 1.65f));
        LinearLayout.LayoutParams row2p = new LinearLayout.LayoutParams(-1, -2); row2p.topMargin = NativeUi.dp(this, 8);
        controls.addView(row2, row2p);

        LinearLayout row3 = new LinearLayout(this);
        row3.setGravity(Gravity.CENTER_VERTICAL);
        prevButton = NativeUi.button(this, "‹ Poprzednia", true);
        nextButton = NativeUi.button(this, "Następna ›", true);
        row3.addView(prevButton, new LinearLayout.LayoutParams(0, -2, 1));
        addGap(row3, 8);
        row3.addView(nextButton, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams row3p = new LinearLayout.LayoutParams(-1, -2); row3p.topMargin = NativeUi.dp(this, 7);
        controls.addView(row3, row3p);
'''
assert oldrow in s, "old reader toolbar block not found"
s = s.replace(oldrow, newrow)

s = s.replace(
    'minusButton.setOnClickListener(v -> { textZoom = Math.max(70, textZoom - 10); savePrefs(); applyZoom(); });',
    'minusButton.setOnClickListener(v -> { textZoom = Math.max(70, textZoom - 10); savePrefs(); applyReadingPrefs(); });'
)
s = s.replace(
    'plusButton.setOnClickListener(v -> { textZoom = Math.min(190, textZoom + 10); savePrefs(); applyZoom(); });',
    'plusButton.setOnClickListener(v -> { textZoom = Math.min(220, textZoom + 10); savePrefs(); applyReadingPrefs(); });'
)
s = s.replace(
    'themeButton.setOnClickListener(v -> { theme = (theme + 1) % 3; themeButton.setText(themeLabel()); savePrefs(); applyTheme(); });',
    'themeButton.setOnClickListener(v -> { theme = (theme + 1) % 3; themeButton.setText(themeLabel()); savePrefs(); applyTheme(); });\n        readingButton.setOnClickListener(v -> showReadingSettings());'
)
s = s.replace(
    'Otwórz książkę albo dokument z pamięci telefonu. Czytnik działa lokalnie i zapamięta ostatni plik.',
    'Otwórz książkę albo dokument z pamięci telefonu. Czytnik działa lokalnie. Masz sepię, tryb nocny, regulację czcionki, interlinii i marginesów.'
)

s = s.replace(
    '    private void applyZoom(){if(web!=null)web.getSettings().setTextZoom(textZoom);statusView.setText("Powiększenie tekstu: "+textZoom+"% · "+formatName(extension(currentName)));}\n',
    '    private void applyReadingPrefs(){\n        if(web!=null) web.getSettings().setTextZoom(textZoom);\n        if(web!=null && lastRawHtml!=null) applyTheme();\n        statusView.setText("Tekst "+textZoom+"% · interlinia "+String.format(Locale.US,"%.2f",lineHeight)+"× · margines "+marginDp+" dp");\n    }\n'
)

oldcss = '''        String bg = theme==2?"#121212":theme==1?"#f3ead3":"#ffffff";
        String fg = theme==2?"#e7e2dd":theme==1?"#3c3226":"#242424";
        String link = theme==2?"#8fc7ff":"#2b6895";
        String css = "<style id='alfaTheme'>html,body{background:"+bg+"!important;color:"+fg+"!important;}body{font-family:sans-serif;line-height:1.58;padding:14px;max-width:900px;margin:auto;}a{color:"+link+"!important;}img{max-width:100%!important;height:auto!important;}pre{white-space:pre-wrap;font-family:sans-serif;line-height:1.58}.note{padding:10px 12px;border-radius:10px;background:rgba(127,127,127,.14);margin-bottom:14px}</style>";
'''
newcss = '''        String bg = theme==2?"#121212":theme==1?"#f2e6c9":"#fffdf9";
        String fg = theme==2?"#e8e3dc":theme==1?"#3b3025":"#242321";
        String link = theme==2?"#9bcfff":theme==1?"#74552f":"#2b6895";
        String family = fontMode==0?"Georgia,'Noto Serif',serif":"system-ui,-apple-system,sans-serif";
        String lh = String.format(Locale.US,"%.2f",lineHeight);
        String css = "<style id='alfaTheme'>html,body{background:"+bg+"!important;color:"+fg+"!important;}body{font-family:"+family+";line-height:"+lh+";padding:10px "+marginDp+"px 36px;max-width:820px;margin:auto;text-rendering:optimizeLegibility;hyphens:auto;-webkit-hyphens:auto;}p{margin:.25em 0 .95em;}a{color:"+link+"!important;}img{max-width:100%!important;height:auto!important;border-radius:4px;}pre{white-space:pre-wrap;font-family:"+family+";line-height:"+lh+";overflow-wrap:anywhere}.note{padding:10px 12px;border-radius:10px;background:rgba(127,127,127,.14);margin-bottom:14px}blockquote{margin:1em 0;padding:.1em 1em;border-left:3px solid rgba(127,127,127,.35)}</style>";
'''
assert oldcss in s, "old CSS block not found"
s = s.replace(oldcss, newcss)

anchor = '''    private int readerBackground(){return theme==2?0xFF121212:theme==1?0xFFF3EAD3:Color.WHITE;}
    private String themeLabel(){return theme==0?"☀ Jasny":theme==1?"◐ Sepia":"☾ Noc";}

    private void previous() {
'''
settings = '''    private int readerBackground(){return theme==2?0xFF121212:theme==1?0xFFF2E6C9:0xFFFFFDF9;}
    private String themeLabel(){return theme==0?"☀ Jasny":theme==1?"◐ Sepia":"☾ Noc";}

    private void showReadingSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = NativeUi.dp(this, 18);
        box.setPadding(pad, NativeUi.dp(this, 8), pad, NativeUi.dp(this, 4));

        TextView zoomLabel = NativeUi.text(this, "Wielkość tekstu: " + textZoom + "%", 14, true);
        android.widget.SeekBar zoom = new android.widget.SeekBar(this);
        zoom.setMax(150); zoom.setProgress(textZoom - 70);
        box.addView(zoomLabel); box.addView(zoom);

        TextView lineLabel = NativeUi.text(this, "Interlinia: " + String.format(Locale.US,"%.2f", lineHeight) + "×", 14, true);
        android.widget.SeekBar line = new android.widget.SeekBar(this);
        line.setMax(100); line.setProgress(Math.round(lineHeight * 100) - 120);
        NativeUi.addSpacer(box, this, 8); box.addView(lineLabel); box.addView(line);

        TextView marginLabel = NativeUi.text(this, "Marginesy tekstu: " + marginDp + " dp", 14, true);
        android.widget.SeekBar margin = new android.widget.SeekBar(this);
        margin.setMax(42); margin.setProgress(marginDp - 6);
        NativeUi.addSpacer(box, this, 8); box.addView(marginLabel); box.addView(margin);

        final int[] fontChoice = {fontMode};
        Button fontButton = NativeUi.button(this, fontChoice[0]==0 ? "Krój: książkowy" : "Krój: prosty", true);
        NativeUi.addSpacer(box, this, 8); box.addView(fontButton);
        fontButton.setOnClickListener(v -> {
            fontChoice[0] = fontChoice[0]==0 ? 1 : 0;
            fontButton.setText(fontChoice[0]==0 ? "Krój: książkowy" : "Krój: prosty");
        });

        zoom.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(android.widget.SeekBar s,int p,boolean from){zoomLabel.setText("Wielkość tekstu: "+(70+p)+"%");}
            public void onStartTrackingTouch(android.widget.SeekBar s){}
            public void onStopTrackingTouch(android.widget.SeekBar s){}
        });
        line.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(android.widget.SeekBar s,int p,boolean from){lineLabel.setText("Interlinia: "+String.format(Locale.US,"%.2f",(120+p)/100f)+"×");}
            public void onStartTrackingTouch(android.widget.SeekBar s){}
            public void onStopTrackingTouch(android.widget.SeekBar s){}
        });
        margin.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(android.widget.SeekBar s,int p,boolean from){marginLabel.setText("Marginesy tekstu: "+(6+p)+" dp");}
            public void onStartTrackingTouch(android.widget.SeekBar s){}
            public void onStopTrackingTouch(android.widget.SeekBar s){}
        });

        new AlertDialog.Builder(this)
            .setTitle("Aa · Ustawienia czytania")
            .setView(box)
            .setNegativeButton("Anuluj", null)
            .setNeutralButton("Domyślne", (d,w) -> {
                textZoom=105; lineHeight=1.58f; marginDp=18; fontMode=0; savePrefs(); applyReadingPrefs();
            })
            .setPositiveButton("Zastosuj", (d,w) -> {
                textZoom=70+zoom.getProgress();
                lineHeight=(120+line.getProgress())/100f;
                marginDp=6+margin.getProgress();
                fontMode=fontChoice[0];
                savePrefs(); applyReadingPrefs();
            })
            .show();
    }

    private void previous() {
'''
assert anchor in s, "theme/previous anchor not found"
s = s.replace(anchor, settings)

s = s.replace(
    '    private void savePrefs(){getSharedPreferences("alfa_reader",0).edit().putInt("zoom",textZoom).putInt("theme",theme).apply();}\n',
    '    private void savePrefs(){getSharedPreferences("alfa_reader",0).edit().putInt("zoom",textZoom).putInt("theme",theme).putInt("line_height_100",Math.round(lineHeight*100)).putInt("margin_dp",marginDp).putInt("font_mode",fontMode).apply();}\n'
)
rp.write_text(s)

bp = Path("project/app/build.gradle")
b = bp.read_text()
b = b.replace("minSdk 23", "minSdk 29")
b = b.replace("versionCode 10736", "versionCode 10737")
b = b.replace("versionName '1.7.36'", "versionName '1.7.37'")
bp.write_text(b)

mp = Path("project/app/src/main/java/com/ispina/lokalnie/MainActivity.java")
m = mp.read_text()
reader = '        addActionCard("Czytnik Alfa","PDF, EPUB, MOBI/AZW, DOC/DOCX, ODT, TXT, RTF, HTML, FB2 i Markdown.",v->startActivity(new Intent(this,ReaderActivity.class)));\n'
while reader in m:
    m = m.replace(reader, "", 1)
chill = '        addActionCard("Chwila spokoju","Mruczenie kota, ptaki, deszcz, szumy i fale. Dźwięki offline z timerem.",v->startActivity(new Intent(this,RainActivity.class)));\n'
assert chill in m, "Chwila spokoju card not found"
m = m.replace(chill, chill + reader)
m = m.replace("POMOCNIK ALFA 1.7.36", "POMOCNIK ALFA 1.7.37")
mp.write_text(m)

print("Modern reader patch applied")
