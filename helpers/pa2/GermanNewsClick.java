package com.ispina.lokalnie;

import android.content.Context;
import android.content.Intent;
import android.view.View;

public final class GermanNewsClick implements View.OnClickListener {
    private final Context context;
    public GermanNewsClick(Context context) { this.context = context; }
    @Override public void onClick(View view) {
        context.startActivity(new Intent(context, GermanNewsActivity.class));
    }
}
