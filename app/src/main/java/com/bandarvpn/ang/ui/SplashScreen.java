package com.bandarvpn.ang.ui;

import android.animation.Animator;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;
import com.bandarvpn.ang.R;

public class SplashScreen extends AppCompatActivity {

    LottieAnimationView rocket;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spash_screen);
        init();
        rocket.addAnimatorListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {

            }

            @Override
            public void onAnimationEnd(Animator animator) {
                SplashScreen.this.finish();
                startActivity(new Intent(SplashScreen.this,LoginActivity.class));
            }

            @Override
            public void onAnimationCancel(Animator animator) {

            }

            @Override
            public void onAnimationRepeat(Animator animator) {

            }
        });
    }
    private void init(){
        rocket = findViewById(R.id.lottie_rocket);
    }
}
