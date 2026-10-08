#!/usr/bin/env python3
"""PA 1.7.48 reader hardening: swipe PDF, in-fullscreen controls, bounded bitmaps, safe ZIP."""
from pathlib import Path
p=Path("project/app/src/main/java/com/ispina/lokalnie/ReaderActivity.java")
s=p.read_text(encoding="utf-8")
def replace(old,new,count=1):
 global s
 assert s.count(old)==count,("reader anchor count",s.count(old),old[:100])
 s=s.replace(old,new)
replace("    private static final int MAX_MOBI_CHARS = 8_000_000;",
"""    private static final int MAX_MOBI_CHARS = 8_000_000;
    private static final long MAX_READER_SOURCE_BYTES = 180L*1024L*1024L;
    private static final long MAX_EPUB_UNPACKED_BYTES = 450L*1024L*1024L;
    private static final int MAX_EPUB_FILES = 4000;
    private static final long MAX_EPUB_ENTRY_BYTES = 80L*1024L*1024L;
    private static final long MAX_ARCHIVE_WORK_MILLIS = 90000L;""")
replace("    private TextView titleView, statusView, pageInfo;",
"""    private TextView titleView, statusView, pageInfo, pdfFullCounter;
    private LinearLayout pdfFullArrows;""")
replace("            stage.removeAllViews(); stage.addView(pdfImage, new FrameLayout.LayoutParams(-1, -1));",
"""            stage.removeAllViews(); stage.addView(pdfImage, new FrameLayout.LayoutParams(-1, -1));
            attachPdfFullscreenControls();""")
# Minimize disruption to existing renderer: bound total area and aspect ratio before bitmap alloc.
replace("""            int screen = Math.max(getResources().getDisplayMetrics().widthPixels, 720);
            int targetWidth = Math.min(2600, Math.max(1600, screen * 2));
            float scale = (float)targetWidth / Math.max(1, pdfPage.getWidth());
            int w = Math.max(1, Math.round(pdfPage.getWidth() * scale));
            int h = Math.max(1, Math.round(pdfPage.getHeight() * scale));
            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            pdfImage.setPdfBitmap(bitmap, pdfZoom);""",
"""            int screen = Math.max(getResources().getDisplayMetrics().widthPixels, 720);
            int targetWidth = Math.min(2300, Math.max(1200, screen * 2));
            double nativeWidth = Math.max(1.0, (double)pdfPage.getWidth());
            double nativeHeight = Math.max(1.0, (double)pdfPage.getHeight());
            // Prevent giant multi-metre banners from exhausting the heap.
            double heapBytes=Runtime.getRuntime().maxMemory();
            double maxPixels=Math.max(1_100_000.0, Math.min(5_500_000.0,heapBytes/26.0));
            double ratio=Math.min((double)targetWidth/nativeWidth,Math.min(3000.0/nativeHeight,Math.sqrt(maxPixels/(nativeWidth*nativeHeight))));
            int w=Math.max(1,(int)Math.min(2600.0,Math.ceil(nativeWidth*ratio)));
            int h=Math.max(1,(int)Math.min(3000.0,Math.ceil(nativeHeight*ratio)));
            Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            boolean loaded=false;
            try{
                bitmap.eraseColor(Color.WHITE);
                pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                pdfImage.setPdfBitmap(bitmap, pdfZoom);
                loaded=true;
            }finally{
                if(!loaded && !bitmap.isRecycled())bitmap.recycle();
            }""")
replace("""            updatePdfStatus();
            setNavEnabled(pdfIndex > 0, pdfIndex + 1 < pdfRenderer.getPageCount());""",
"""            updatePdfStatus();
            updatePdfFullscreenControls();
            setNavEnabled(pdfIndex > 0, pdfIndex + 1 < pdfRenderer.getPageCount());""")
replace("""        extractZipSafe(zip, dir);
        File container = new File(dir, "META-INF/container.xml");""",
"""        try{extractZipSafe(zip,dir);}
        catch(Exception e){deleteTree(dir);throw e;}
        finally{try{zip.delete();}catch(Exception ignored){}}
        File container = new File(dir, "META-INF/container.xml");""")
# Full-screen overlays maintain navigation accessibility; swipe still works on PDF image.
anchor="    private void enterReaderFullscreen() {"
assert s.count(anchor)==1
insert="""    private void attachPdfFullscreenControls(){
        pdfFullCounter=NativeUi.muted(this,"",13);
        pdfFullCounter.setGravity(Gravity.CENTER);
        pdfFullCounter.setTextColor(Color.WHITE);
        pdfFullCounter.setBackgroundColor(0xAA222222);
        FrameLayout.LayoutParams counterParams=new FrameLayout.LayoutParams(-2,NativeUi.dp(this,36),Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        counterParams.topMargin=NativeUi.dp(this,18);
        stage.addView(pdfFullCounter,counterParams);
        pdfFullArrows=new LinearLayout(this);
        pdfFullArrows.setGravity(Gravity.CENTER_VERTICAL);
        Button prev=NativeUi.button(this,"‹",true);
        prev.setContentDescription("Poprzednia strona PDF");
        prev.setOnClickListener(v->previous());
        Button next=NativeUi.button(this,"›",true);
        next.setContentDescription("Następna strona PDF");
        next.setOnClickListener(v->next());
        pdfFullArrows.addView(prev,new LinearLayout.LayoutParams(NativeUi.dp(this,48),NativeUi.dp(this,48)));
        android.widget.Space spacer=new android.widget.Space(this);
        pdfFullArrows.addView(spacer,new LinearLayout.LayoutParams(0,1,1));
        pdfFullArrows.addView(next,new LinearLayout.LayoutParams(NativeUi.dp(this,48),NativeUi.dp(this,48)));
        FrameLayout.LayoutParams navParams=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
        navParams.setMargins(NativeUi.dp(this,12),0,NativeUi.dp(this,12),NativeUi.dp(this,14));
        stage.addView(pdfFullArrows,navParams);
        pdfFullCounter.setVisibility(View.GONE);
        pdfFullArrows.setVisibility(View.GONE);
        updatePdfFullscreenControls();
    }
    private void updatePdfFullscreenControls(){
        if(pdfFullCounter!=null && pdfRenderer!=null){
            pdfFullCounter.setText("  "+(pdfIndex+1)+" / "+pdfRenderer.getPageCount()+"  ");
            pdfFullCounter.setVisibility(readerFullscreen?View.VISIBLE:View.GONE);
        }
        if(pdfFullArrows!=null)pdfFullArrows.setVisibility(readerFullscreen && pdfRenderer!=null?View.VISIBLE:View.GONE);
    }

"""
s=s.replace(anchor,insert+anchor)
replace("""        readerFullscreen = true;
        if (readerHeader != null)""",
"""        readerFullscreen = true;
        updatePdfFullscreenControls();
        if (readerHeader != null)""")
replace("""        readerFullscreen = false;
        if (readerHeader != null)""",
"""        readerFullscreen = false;
        updatePdfFullscreenControls();
        if (readerHeader != null)""")
replace("        private float lastX, lastY;","""        private float lastX, lastY, swipeX, swipeY;
        private boolean multipleFingers = false;
        private boolean crossedPinch = false;""")
replace("""            bitmap = b;
            super.setImageBitmap(b);""",
"""            Bitmap old=bitmap;
            bitmap=b;
            super.setImageBitmap(b);
            if(old!=null && old!=b && !old.isRecycled())old.recycle();""")
replace("""        @Override public boolean onTouchEvent(MotionEvent event) {
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
        }""",
"""        @Override public boolean onTouchEvent(MotionEvent event) {
            if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);
            scaleDetector.onTouchEvent(event);
            int action=event.getActionMasked();
            if(event.getPointerCount()>1 || action==MotionEvent.ACTION_POINTER_DOWN){
                multipleFingers=true;crossedPinch=true;
            }
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    swipeX=lastX=event.getX(); swipeY=lastY=event.getY();
                    dragging=true;multipleFingers=false;crossedPinch=false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!scaleDetector.isInProgress() && dragging && userScale > 1.02f && !multipleFingers) {
                        float x=event.getX(),y=event.getY();
                        offsetX+=x-lastX;offsetY+=y-lastY;
                        lastX=x;lastY=y;applyPdfMatrix();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    dragging=false;
                    float dx=event.getX()-swipeX,dy=event.getY()-swipeY;
                    float minSwipe=NativeUi.dp(ReaderActivity.this,72);
                    if(userScale<=1.02f && !multipleFingers && !crossedPinch &&
                        Math.abs(dx)>=minSwipe && Math.abs(dx)>Math.abs(dy)*1.4f){
                        if(dx<0)next();else previous();
                        return true;
                    }
                    pdfZoom=userScale;savePrefs();updatePdfStatus();return true;
                case MotionEvent.ACTION_CANCEL:
                    dragging=false;return true;
            }
            return true;
        }
        void dispose(){
            super.setImageDrawable(null);
            if(bitmap!=null && !bitmap.isRecycled())bitmap.recycle();
            bitmap=null;
        }""")
replace("""        try(InputStream in=getContentResolver().openInputStream(uri);BufferedOutputStream out=new BufferedOutputStream(new FileOutputStream(f))){if(in==null)throw new IllegalStateException("Nie mogę czytać pliku");byte[] b=new byte[65536];int n;while((n=in.read(b))>0)out.write(b,0,n);}return f;""",
"""        long written=0;
        try(InputStream in=getContentResolver().openInputStream(uri);
            BufferedOutputStream out=new BufferedOutputStream(new FileOutputStream(f))){
            if(in==null)throw new IllegalStateException("Nie mogę czytać pliku");
            byte[] b=new byte[65536];int n;
            while((n=in.read(b))!=-1){
                if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("Odczyt anulowany");
                written+=n;
                if(written>MAX_READER_SOURCE_BYTES)throw new java.io.IOException("Dokument jest za duży (limit 180 MB)");
                out.write(b,0,n);
            }
        }catch(Exception e){try{f.delete();}catch(Exception ignored){}throw e;}
        return f;""")
replace("""    private void extractZipSafe(File zip, File dir) throws Exception {
        String base=dir.getCanonicalPath()+File.separator;
        try(ZipInputStream zin=new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))){
            ZipEntry e;byte[] buf=new byte[65536];
            while((e=zin.getNextEntry())!=null){File out=new File(dir,e.getName());String cp=out.getCanonicalPath();if(!cp.startsWith(base))throw new SecurityException("Nieprawidłowa ścieżka w archiwum");if(e.isDirectory()){out.mkdirs();continue;}File parent=out.getParentFile();if(parent!=null)parent.mkdirs();try(BufferedOutputStream bos=new BufferedOutputStream(new FileOutputStream(out))){int n;while((n=zin.read(buf))>0)bos.write(buf,0,n);}}
        }
    }""",
"""    private void extractZipSafe(File zip, File dir) throws Exception {
        String base=dir.getCanonicalPath()+File.separator;
        long total=0,started=android.os.SystemClock.elapsedRealtime();
        int entries=0;
        try(ZipInputStream zin=new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))){
            ZipEntry e;byte[] buf=new byte[65536];
            while((e=zin.getNextEntry())!=null){
                if(++entries>MAX_EPUB_FILES)throw new java.io.IOException("EPUB zawiera zbyt wiele plików");
                if(Thread.currentThread().isInterrupted()||android.os.SystemClock.elapsedRealtime()-started>MAX_ARCHIVE_WORK_MILLIS)
                    throw new java.io.InterruptedIOException("Przetwarzanie EPUB przekroczyło limit czasu");
                File out=new File(dir,e.getName());String cp=out.getCanonicalPath();
                if(!cp.startsWith(base))throw new SecurityException("Nieprawidłowa ścieżka w archiwum");
                if(e.isDirectory()){if(!out.isDirectory() && !out.mkdirs())throw new java.io.IOException("Nie mogę utworzyć katalogu");continue;}
                File parent=out.getParentFile();
                if(parent!=null&&!parent.isDirectory()&&!parent.mkdirs())throw new java.io.IOException("Nie mogę utworzyć katalogu");
                long entrySize=0;
                try(BufferedOutputStream bos=new BufferedOutputStream(new FileOutputStream(out))){
                    int n;
                    while((n=zin.read(buf))!=-1){
                        entrySize+=n;total+=n;
                        if(entrySize>MAX_EPUB_ENTRY_BYTES||total>MAX_EPUB_UNPACKED_BYTES)
                            throw new java.io.IOException("EPUB przekracza limit rozpakowania");
                        if(Thread.currentThread().isInterrupted()||android.os.SystemClock.elapsedRealtime()-started>MAX_ARCHIVE_WORK_MILLIS)
                            throw new java.io.InterruptedIOException("Przetwarzanie EPUB anulowane");
                        bos.write(buf,0,n);
                    }
                }
                zin.closeEntry();
            }
        }
    }""")
replace("""private void closePdf(){try{if(pdfPage!=null)pdfPage.close();}catch(Throwable ignored){}pdfPage=null;try{if(pdfRenderer!=null)pdfRenderer.close();}catch(Throwable ignored){}pdfRenderer=null;try{if(pdfPfd!=null)pdfPfd.close();}catch(Throwable ignored){}pdfPfd=null;pdfImage=null;epubChapters.clear();}""",
"""private void closePdf(){try{if(pdfPage!=null)pdfPage.close();}catch(Throwable ignored){}pdfPage=null;try{if(pdfRenderer!=null)pdfRenderer.close();}catch(Throwable ignored){}pdfRenderer=null;try{if(pdfPfd!=null)pdfPfd.close();}catch(Throwable ignored){}pdfPfd=null;if(pdfImage!=null)pdfImage.dispose();pdfImage=null;pdfFullCounter=null;pdfFullArrows=null;epubChapters.clear();}""")
p.write_text(s,encoding="utf-8")
print("PASS Reader PDF swipe/control, bounded rendering, ZIP limits and cleanup")
