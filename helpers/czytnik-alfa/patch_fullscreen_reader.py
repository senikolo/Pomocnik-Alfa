from pathlib import Path

rp = Path("project/app/src/main/java/com/ispina/lokalnie/ReaderActivity.java")
s = rp.read_text()

s = s.replace(
    'private LinearLayout root;',
    'private LinearLayout root, header, controls;'
)
s = s.replace(
    'private Button openButton, lastButton, minusButton, plusButton, themeButton, readingButton, prevButton, nextButton;',
    'private Button openButton, lastButton, fullscreenButton, minusButton, plusButton, themeButton, readingButton, prevButton, nextButton;'
)
s = s.replace(
    'private int fontMode = 0;',
    'private int fontMode = 0;\n    private boolean readerFullscreen = false;\n    private float gestureDownX = 0f, gestureDownY = 0f;'
)

s = s.replace(
    '        LinearLayout header = new LinearLayout(this);',
    '        header = new LinearLayout(this);'
)
s = s.replace(
    '        LinearLayout controls = new LinearLayout(this);',
    '        controls = new LinearLayout(this);'
)

old_row1 = '''        openButton = NativeUi.button(this, "📂 Otwórz plik", false);
        lastButton = NativeUi.button(this, "Ostatni", true);
        row1.addView(openButton, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams lpLast = new LinearLayout.LayoutParams(NativeUi.dp(this, 105), -2); lpLast.leftMargin = NativeUi.dp(this, 8);
        row1.addView(lastButton, lpLast);
        controls.addView(row1);
'''
new_row1 = '''        openButton = NativeUi.button(this, "📂 Otwórz plik", false);
        lastButton = NativeUi.button(this, "Ostatni", true);
        fullscreenButton = NativeUi.button(this, "⛶ Pełny ekran", true);
        row1.addView(openButton, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams lpLast = new LinearLayout.LayoutParams(NativeUi.dp(this, 92), -2); lpLast.leftMargin = NativeUi.dp(this, 7);
        row1.addView(lastButton, lpLast);
        LinearLayout.LayoutParams lpFull = new LinearLayout.LayoutParams(NativeUi.dp(this, 132), -2); lpFull.leftMargin = NativeUi.dp(this, 7);
        row1.addView(fullscreenButton, lpFull);
        controls.addView(row1);
'''
assert old_row1 in s, "reader row1 block not found"
s = s.replace(old_row1, new_row1)

s = s.replace(
    '        readingButton.setOnClickListener(v -> showReadingSettings());',
    '        readingButton.setOnClickListener(v -> showReadingSettings());\n        fullscreenButton.setOnClickListener(v -> setReaderFullscreen(true));'
)

s = s.replace(
    '            pdfImage = new ImageView(this);',
    '            pdfImage = new ImageView(this);\n            attachReaderGestures(pdfImage);'
)
s = s.replace(
    '            web.setWebViewClient(new WebViewClient());',
    '            web.setWebViewClient(new WebViewClient());\n            attachReaderGestures(web);'
)

anchor = '''    private void addGap(LinearLayout row, int dp) {
        View gap = new View(this); row.addView(gap, new LinearLayout.LayoutParams(NativeUi.dp(this, dp), 1));
    }

    private void showWelcome() {
'''
fullscreen_methods = '''    private void addGap(LinearLayout row, int dp) {
        View gap = new View(this); row.addView(gap, new LinearLayout.LayoutParams(NativeUi.dp(this, dp), 1));
    }

    private void setReaderFullscreen(boolean full) {
        readerFullscreen = full;
        if (header != null) header.setVisibility(full ? View.GONE : View.VISIBLE);
        if (controls != null) controls.setVisibility(full ? View.GONE : View.VISIBLE);

        View decor = getWindow().getDecorView();
        if (full) {
            decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(readerBackground());
            Toast.makeText(this, "Pełny ekran · Wstecz wraca do ustawień czytnika · przesuń w lewo/prawo, aby zmienić stronę", Toast.LENGTH_LONG).show();
        } else {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            getWindow().setStatusBarColor(NativeUi.green(this));
            getWindow().setNavigationBarColor(Color.BLACK);
        }
    }

    private void attachReaderGestures(View target) {
        target.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == android.view.MotionEvent.ACTION_DOWN) {
                gestureDownX = event.getX();
                gestureDownY = event.getY();
            } else if (action == android.view.MotionEvent.ACTION_UP && readerFullscreen) {
                float dx = event.getX() - gestureDownX;
                float dy = event.getY() - gestureDownY;
                float threshold = NativeUi.dp(this, 70);
                if (Math.abs(dx) > threshold && Math.abs(dx) > Math.abs(dy) * 1.25f) {
                    if (dx < 0) next(); else previous();
                    return true;
                }
            }
            return false;
        });
    }

    @Override public void onBackPressed() {
        if (readerFullscreen) {
            setReaderFullscreen(false);
            return;
        }
        super.onBackPressed();
    }

    private void showWelcome() {
'''
assert anchor in s, "addGap/showWelcome anchor not found"
s = s.replace(anchor, fullscreen_methods)

s = s.replace(
    'Otwórz książkę albo dokument z pamięci telefonu. Czytnik działa lokalnie. Masz sepię, tryb nocny, regulację czcionki, interlinii i marginesów.',
    'Otwórz książkę albo dokument z pamięci telefonu. Czytnik działa lokalnie. Masz sepię, tryb nocny, regulację czcionki, interlinii, marginesów i pełny ekran.'
)

rp.write_text(s)

bp = Path("project/app/build.gradle")
b = bp.read_text()
b = b.replace("versionCode 10737", "versionCode 10738")
b = b.replace("versionName '1.7.37'", "versionName '1.7.38'")
bp.write_text(b)

mp = Path("project/app/src/main/java/com/ispina/lokalnie/MainActivity.java")
m = mp.read_text()
m = m.replace("POMOCNIK ALFA 1.7.37", "POMOCNIK ALFA 1.7.38")
mp.write_text(m)

print("Fullscreen reader patch applied")
