package com.ispina.lokalnie;
import android.content.Context;
import android.content.Intent;
import android.view.View;
public final class PARegionsClick implements View.OnClickListener {
    private final Context context;
    public PARegionsClick(Context context){this.context=context;}
    @Override public void onClick(View unused){context.startActivity(new Intent(context,PARegionsActivity.class));}
}
