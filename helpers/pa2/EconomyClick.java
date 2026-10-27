package com.ispina.lokalnie;

import android.content.Context;
import android.content.Intent;
import android.view.View;

public final class EconomyClick implements View.OnClickListener {
    private final Context context;
    public EconomyClick(Context context) { this.context = context; }
    @Override public void onClick(View view) {
        context.startActivity(new Intent(context, EconomyActivity.class));
    }
}
