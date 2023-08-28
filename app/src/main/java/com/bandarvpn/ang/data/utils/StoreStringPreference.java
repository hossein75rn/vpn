package com.bandarvpn.ang.data.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class StoreStringPreference {
    Context context ;
    public StoreStringPreference(Context context) {
        this.context = context;
    }
    public void save(String key,String sharedPreference){
        SharedPreferences preferences = context.getSharedPreferences("com.bandarvpn.ang", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(key, sharedPreference);
        editor.apply();
    }
    public String read(String sharedPreference){
        SharedPreferences preferences = context.getSharedPreferences("com.bandarvpn.ang", Context.MODE_PRIVATE);
        return preferences.getString(sharedPreference,null);
    }
}
