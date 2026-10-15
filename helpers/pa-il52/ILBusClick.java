package com.ispina.lokalnie;

import android.content.Context;
import android.content.Intent;
import android.view.View;

/** Home card: open the same IL planner UI without leaving Pomocnik Alfa. */
public final class ILBusClick implements View.OnClickListener {
    private final Context context;
    public ILBusClick(Context context) { this.context=context; }
    @Override public void onClick(View ignored) {
        context.startActivity(new Intent(context, ILBusPlannerActivity.class));
    }
}
