package com.ispina.lokalnie;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.Toast;

/** Opens the Polish channels and official viewing catalogues from the TV screen. */
public final class TvDirectoryClick implements View.OnClickListener {
    private final Context context;

    public TvDirectoryClick(Context context) {
        this.context = context;
    }

    @Override
    public void onClick(View view) {
        new AlertDialog.Builder(context)
            .setTitle("Polskie kanały online")
            .setMessage("Telewizja Republika i wPolsce24: transmisje na oficjalnych stronach. Polsat Box Go: Polsat, Polsat 2, Super Polsat, Polsat News, Polsat News Polityka, Polsat Play, Polsat Cafe oraz kanały sportowe. Dostępność zależy od pakietu i logowania.")
            .setPositiveButton("Republika", new OpenUrl(context, "https://tvrepublika.pl/live"))
            .setNeutralButton("wPolsce24", new OpenUrl(context, "https://wpolsce24.tv/video"))
            .setNegativeButton("Polsat Box Go", new OpenUrl(context, "https://polsatboxgo.pl/kanaly-tv?epg=true"))
            .show();
    }

    private static final class OpenUrl implements DialogInterface.OnClickListener {
        private final Context context;
        private final String url;

        OpenUrl(Context context, String url) {
            this.context = context;
            this.url = url;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(context, "Nie znaleziono przeglądarki internetowej.", Toast.LENGTH_LONG).show();
            }
        }
    }
}
