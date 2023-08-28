package com.bandarvpn.ang.ui;

import static java.lang.Thread.sleep;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatEditText;

import com.bandarvpn.ang.R;
import com.bandarvpn.ang.data.RetrofitInstance;
import com.bandarvpn.ang.data.api.ApiService;
import com.bandarvpn.ang.data.model.Login;
import com.bandarvpn.ang.data.utils.StoreStringPreference;

import java.util.Objects;
import java.util.UUID;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private final String TAG = "hossein75rn";
        private  AppCompatEditText etUserName;
        private  AppCompatEditText etPassword;
        private AppCompatButton cvCountry;
        private  String userName;
        private  String password;

        private ApiService api;
        private StoreStringPreference storeUid;

        private String sh;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        init();
        checkIfSharedPreferenceIsSet();
        cvCountry.setOnClickListener(this::cvClick);
    }

    private String uuidGenerator(){
        UUID uuid = UUID.randomUUID();
        return uuid.toString();
    }
    private void cvClick(View view) {
        readEditTexts();
        String uuid = uuidGenerator();
        Log.w(TAG, "onCreate: clicked");
        view.setClickable(false);
        api.login(userName,password,uuid).enqueue(new Callback<Login>() {
            @Override
            public void onResponse(@NonNull Call<Login> call, @NonNull Response<Login> response) {
                Log.w(TAG, "onResponse: response received");
                if (response.body()!=null){
                    Login responseS = response.body();
                    if (responseS.getStatus().equalsIgnoreCase(responseS.statusFailed)) {
                        Toast.makeText(LoginActivity.this,responseS.getMessage(),Toast.LENGTH_SHORT).show();
                        view.setClickable(true);
                    }
                    else if (responseS.getStatus().equalsIgnoreCase(responseS.statusSuccess)){
                        storeUid.save("uuid",responseS.getUuid());
                        Toast.makeText(LoginActivity.this,responseS.getMessage(),Toast.LENGTH_SHORT).show();
                        startConfigActivity();
                    }else {
                        Toast.makeText(LoginActivity.this,"خطای ناشناخته ای رخ داد",Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<Login> call, @NonNull Throwable t) {
                Log.w(TAG, "onFailure: ", t);
                Toast.makeText(LoginActivity.this,"خطای اینترنت",Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void startConfigActivity() {
        startActivity(new Intent(LoginActivity.this,MainActivity.class));
        LoginActivity.this.finish();
    }

    private void readEditTexts() {
        userName = Objects.requireNonNull(etUserName.getText()).toString().trim();
        password = Objects.requireNonNull(etPassword.getText()).toString().trim();
    }

    private void checkIfSharedPreferenceIsSet() {
        if (sh != null && sh.length()>2) {
            startConfigActivity();
        }
    }

    private void init() {
        etUserName = findViewById(R.id.etUserName);
        etPassword = findViewById(R.id.etPassword);
        cvCountry = findViewById(R.id.cvLogin);
        api = RetrofitInstance.Instance().create(ApiService.class);
        storeUid = new StoreStringPreference(this);
        sh = storeUid.read("uuid");
    }
}
