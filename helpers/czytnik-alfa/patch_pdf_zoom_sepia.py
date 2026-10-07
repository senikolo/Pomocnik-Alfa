from pathlib import Path

rp = Path("project/app/src/main/java/com/ispina/lokalnie/ReaderActivity.java")
s = rp.read_text()

# Imports for PDF zoom/pan and visual filters.
if "import android.graphics.Matrix;" not in s:
    s = s.replace(
        "import android.graphics.Color;\n",
        "import android.graphics.Color;\nimport android.graphics.Matrix;\nimport android.graphics.ColorMatrix;\nimport android.graphics.ColorMatrixColorFilter;\n"
    )
if "import android.view.MotionEvent;" not in s:
    s = s.replace(
        "import android.view.Gravity;\n",
        "import android.view.Gravity;\nimport android.view.MotionEvent;\nimport android.view.ScaleGestureDetector;\n"
    )
if "import android.content.Context;" not in s:
    s = s.replace(
        "import android.content.Intent;\n",
        "import android.content.Intent;\nimport android.content.Context;\n"
    )

# PDF-specific state.
s = s.replace(
    "private ImageView pdfImage;",
    "private PdfZoomImageView pdfImage;\n    private float pdfZoom = 1.0f;"
)
s = s.replace(
    'fontMode = prefs.getInt("font_mode", 0);',
    'fontMode = prefs.getInt("font_mode", 0);\n        pdfZoom = Math.max(1f, Math.min(5f, prefs.getInt("pdf_zoom_100", 100) / 100f));'
)

# A-/A+ control PDF magnification when a PDF is open.
s = s.replace(
    'minusButton.setOnClickListener(v -> { textZoom = Math.max(70, textZoom - 10); savePrefs(); applyReadingPrefs(); });',
    'minusButton.setOnClickListener(v -> { if(pdfRenderer!=null){setPdfZoom(pdfZoom-0.25f);}else{textZoom=Math.max(70,textZoom-10);savePrefs();applyReadingPrefs();} });'
)
s = s.replace(
    'plusButton.setOnClickListener(v -> { textZoom = Math.min(220, textZoom + 10); savePrefs(); applyReadingPrefs(); });',
    'plusButton.setOnClickListener(v -> { if(pdfRenderer!=null){setPdfZoom(pdfZoom+0.25f);}else{textZoom=Math.min(220,textZoom+10);savePrefs();applyReadingPrefs();} });'
)

# Replace static ImageView/ScrollView PDF presentation with a gesture-capable full-stage viewer.
s = s.replace(
    'pdfImage = new ImageView(this);',
    'pdfImage = new PdfZoomImageView(this);'
)
s = s.replace(
    'pdfImage.setAdjustViewBounds(true);\n            pdfImage.setScaleType(ImageView.ScaleType.FIT_CENTER);\n            ScrollView sv = new ScrollView(this); sv.setFillViewport(true); sv.setBackgroundColor(readerBackground()); sv.addView(pdfImage, new ScrollView.LayoutParams(-1, -2));\n            pageInfo = NativeUi.muted(this, "", 12);\n            stage.removeAllViews(); stage.addView(sv, new FrameLayout.LayoutParams(-1, -1));',
    'pdfImage.setBackgroundColor(readerBackground());\n            pageInfo = NativeUi.muted(this, "", 12);\n            stage.removeAllViews(); stage.addView(pdfImage, new FrameLayout.LayoutParams(-1, -1));'
)

# Render a larger bitmap for crisp zooming, but cap memory use.
old_render = '''            int screen = Math.max(getResources().getDisplayMetrics().widthPixels, 720);
            float scale = (float)screen / Math.max(1, pdfPage.getWidth());
            int w = Math.max(1, Math.round(pdfPage.getWidth() * scale));
            int h = Math.max(1, Math.round(pdfPage.getHeight() * scale));
            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            pdfImage.setImageBitmap(bitmap);
            status("PDF · strona " + (pdfIndex + 1) + " z " + pdfRenderer.getPageCount());
'''
new_render = '''            int screen = Math.max(getResources().getDisplayMetrics().widthPixels, 720);
            int targetWidth = Math.min(2600, Math.max(1600, screen * 2));
            float scale = (float)targetWidth / Math.max(1, pdfPage.getWidth());
            int w = Math.max(1, Math.round(pdfPage.getWidth() * scale));
            int h = Math.max(1, Math.round(pdfPage.getHeight() * scale));
            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            pdfImage.setPdfBitmap(bitmap, pdfZoom);
            applyPdfTheme();
            updatePdfStatus();
'''
assert old_render in s, "PDF render block not found"
s = s.replace(old_render, new_render)

# Theme changes should affect PDFs too.
s = s.replace(
    '''        if (web != null) {
            web.setBackgroundColor(readerBackground());
            if (lastRawHtml != null) web.loadDataWithBaseURL(lastBase, themedHtml(lastRawHtml), "text/html", "UTF-8", null);
        }
''',
    '''        if (web != null) {
            web.setBackgroundColor(readerBackground());
            if (lastRawHtml != null) web.loadDataWithBaseURL(lastBase, themedHtml(lastRawHtml), "text/html", "UTF-8", null);
        }
        applyPdfTheme();
'''
)

# Persist PDF zoom together with reader prefs.
s = s.replace(
    '.putInt("font_mode",fontMode).apply();',
    '.putInt("font_mode",fontMode).putInt("pdf_zoom_100",Math.round(pdfZoom*100)).apply();'
)

# Insert PDF controls and the custom gesture viewer before previous().
anchor = "    private void previous() {\n"
helpers = r'''    private void setPdfZoom(float value) {
        pdfZoom = Math.max(1f, Math.min(5f, value));
        if (pdfImage != null) pdfImage.setUserZoom(pdfZoom);
        savePrefs();
        updatePdfStatus();
    }

    private void updatePdfStatus() {
        if (pdfRenderer == null) return;
        status("PDF · strona " + (pdfIndex + 1) + " z " + pdfRenderer.getPageCount() + " · zoom " + Math.round(pdfZoom * 100) + "%");
    }

    private void applyPdfTheme() {
        if (pdfImage == null) return;
        pdfImage.setBackgroundColor(readerBackground());
        if (theme == 1) {
            ColorMatrix sepia = new ColorMatrix(new float[]{
                0.393f,0.769f,0.189f,0,0,
                0.349f,0.686f,0.168f,0,0,
                0.272f,0.534f,0.131f,0,0,
                0,0,0,1,0
            });
            ColorMatrix soften = new ColorMatrix();
            soften.setScale(0.88f,0.88f,0.88f,1f);
            sepia.postConcat(soften);
            pdfImage.setColorFilter(new ColorMatrixColorFilter(sepia));
        } else if (theme == 2) {
            ColorMatrix night = new ColorMatrix(new float[]{
                -0.86f,0,0,0,235,
                0,-0.86f,0,0,230,
                0,0,-0.86f,0,220,
                0,0,0,1,0
            });
            pdfImage.setColorFilter(new ColorMatrixColorFilter(night));
        } else {
            pdfImage.clearColorFilter();
        }
        pdfImage.invalidate();
    }

    private class PdfZoomImageView extends ImageView {
        private final Matrix drawMatrix = new Matrix();
        private final ScaleGestureDetector scaleDetector;
        private Bitmap bitmap;
        private float userScale = 1f;
        private float offsetX = 0f, offsetY = 0f;
        private float lastX, lastY;
        private boolean dragging = false;

        PdfZoomImageView(Context context) {
            super(context);
            setScaleType(ScaleType.MATRIX);
            setClickable(true);
            scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScale(ScaleGestureDetector detector) {
                    float old = userScale;
                    float next = Math.max(1f, Math.min(5f, old * detector.getScaleFactor()));
                    if (Math.abs(next - old) < 0.001f) return true;
                    float ratio = next / old;
                    float cx = getWidth() / 2f, cy = getHeight() / 2f;
                    float fx = detector.getFocusX(), fy = detector.getFocusY();
                    offsetX = (fx - cx) - ((fx - cx) - offsetX) * ratio;
                    offsetY = (fy - cy) - ((fy - cy) - offsetY) * ratio;
                    userScale = next;
                    pdfZoom = next;
                    applyPdfMatrix();
                    return true;
                }
                @Override public void onScaleEnd(ScaleGestureDetector detector) {
                    pdfZoom = userScale;
                    savePrefs();
                    updatePdfStatus();
                }
            });
        }

        void setPdfBitmap(Bitmap b, float zoom) {
            bitmap = b;
            super.setImageBitmap(b);
            userScale = Math.max(1f, Math.min(5f, zoom));
            offsetX = 0f; offsetY = 0f;
            post(this::applyPdfMatrix);
        }

        void setUserZoom(float zoom) {
            userScale = Math.max(1f, Math.min(5f, zoom));
            if (userScale <= 1.001f) { offsetX = 0f; offsetY = 0f; }
            applyPdfMatrix();
        }

        private void applyPdfMatrix() {
            if (bitmap == null || getWidth() <= 0 || getHeight() <= 0) return;
            float fit = Math.min((float)getWidth()/bitmap.getWidth(), (float)getHeight()/bitmap.getHeight());
            float scale = fit * userScale;
            float imageW = bitmap.getWidth() * scale;
            float imageH = bitmap.getHeight() * scale;

            float maxX = Math.max(0f, (imageW - getWidth()) / 2f);
            float maxY = Math.max(0f, (imageH - getHeight()) / 2f);
            offsetX = Math.max(-maxX, Math.min(maxX, offsetX));
            offsetY = Math.max(-maxY, Math.min(maxY, offsetY));

            float dx = (getWidth() - imageW) / 2f + offsetX;
            float dy = (getHeight() - imageH) / 2f + offsetY;
            drawMatrix.reset();
            drawMatrix.postScale(scale, scale);
            drawMatrix.postTranslate(dx, dy);
            setImageMatrix(drawMatrix);
            invalidate();
        }

        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w,h,oldw,oldh);
            applyPdfMatrix();
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            getParent().requestDisallowInterceptTouchEvent(true);
            scaleDetector.onTouchEvent(event);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = event.getX(); lastY = event.getY(); dragging = true; return true;
                case MotionEvent.ACTION_MOVE:
                    if (!scaleDetector.isInProgress() && dragging && userScale > 1f) {
                        float x=event.getX(), y=event.getY();
                        offsetX += x-lastX; offsetY += y-lastY;
                        lastX=x; lastY=y; applyPdfMatrix();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    dragging=false;
                    pdfZoom=userScale; savePrefs(); updatePdfStatus(); return true;
            }
            return true;
        }
    }

'''
assert anchor in s, "previous() anchor missing for PDF helpers"
s = s.replace(anchor, helpers + anchor, 1)

# Version bump for Android 10+ line.
bp = Path("project/app/build.gradle")
b = bp.read_text()
b = b.replace("versionCode 10738", "versionCode 10740")
b = b.replace("versionName '1.7.38'", "versionName '1.7.40'")
bp.write_text(b)

mp = Path("project/app/src/main/java/com/ispina/lokalnie/MainActivity.java")
m = mp.read_text().replace("POMOCNIK ALFA 1.7.38", "POMOCNIK ALFA 1.7.40")
mp.write_text(m)

rp.write_text(s)
print("PDF zoom/sepia patch applied")
