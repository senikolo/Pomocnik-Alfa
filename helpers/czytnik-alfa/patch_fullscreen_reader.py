from pathlib import Path

rp = Path("project/app/src/main/java/com/ispina/lokalnie/ReaderActivity.java")
s = rp.read_text()

# Track the views that need to disappear in fullscreen mode.
s = s.replace(
    'private TextView titleView, statusView;',
    'private TextView titleView, statusView;\n    private View readerTopBar, readerControls;\n    private boolean readerFullscreen = false;'
)

# Capture the top bar and controls container references.
s = s.replace(
    'root.addView(top, new LinearLayout.LayoutParams(-1, -2));',
    'root.addView(top, new LinearLayout.LayoutParams(-1, -2));\n        readerTopBar = top;'
)
s = s.replace(
    'root.addView(controls, new LinearLayout.LayoutParams(-1, -2));',
    'root.addView(controls, new LinearLayout.LayoutParams(-1, -2));\n        readerControls = controls;'
)

# Add fullscreen button next to reading settings.
s = s.replace(
    'readingButton = NativeUi.button(this, "Aa Czytanie", true);',
    'readingButton = NativeUi.button(this, "Aa Czytanie", true);\n        Button fullscreenButton = NativeUi.button(this, "⛶ Pełny ekran", true);'
)
s = s.replace(
    'row2.addView(readingButton, new LinearLayout.LayoutParams(0, -2, 1.65f));',
    'row2.addView(readingButton, new LinearLayout.LayoutParams(0, -2, 1.65f));\n        addGap(row2, 6);\n        row2.addView(fullscreenButton, new LinearLayout.LayoutParams(0, -2, 1.55f));'
)
s = s.replace(
    'readingButton.setOnClickListener(v -> showReadingSettings());',
    'readingButton.setOnClickListener(v -> showReadingSettings());\n        fullscreenButton.setOnClickListener(v -> enterReaderFullscreen());'
)

# Add immersive fullscreen helpers before previous().
anchor = '    private void previous() {\n'
helpers = '''    private void enterReaderFullscreen() {
        readerFullscreen = true;
        if (readerTopBar != null) readerTopBar.setVisibility(View.GONE);
        if (readerControls != null) readerControls.setVisibility(View.GONE);
        if (statusView != null) statusView.setVisibility(View.GONE);
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
        Toast.makeText(this, "Pełny ekran · Wstecz, aby wrócić do sterowania", Toast.LENGTH_SHORT).show();
    }

    private void exitReaderFullscreen() {
        readerFullscreen = false;
        if (readerTopBar != null) readerTopBar.setVisibility(View.VISIBLE);
        if (readerControls != null) readerControls.setVisibility(View.VISIBLE);
        if (statusView != null) statusView.setVisibility(View.VISIBLE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    public void onBackPressed() {
        if (readerFullscreen) {
            exitReaderFullscreen();
            return;
        }
        super.onBackPressed();
    }

'''
assert anchor in s, "previous() anchor missing"
s = s.replace(anchor, helpers + anchor, 1)

# Version bump only.
bp = Path("project/app/build.gradle")
b = bp.read_text()
b = b.replace("versionCode 10737", "versionCode 10738")
b = b.replace("versionName '1.7.37'", "versionName '1.7.38'")
bp.write_text(b)

mp = Path("project/app/src/main/java/com/ispina/lokalnie/MainActivity.java")
m = mp.read_text().replace("POMOCNIK ALFA 1.7.37", "POMOCNIK ALFA 1.7.38")
mp.write_text(m)

rp.write_text(s)
print("Fullscreen reader patch applied")
