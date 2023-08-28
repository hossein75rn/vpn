package com.bandarvpn.ang.data.api;

import com.bandarvpn.ang.data.model.Login;
import com.bandarvpn.ang.data.model.VpnConfigs;

import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.POST;

public interface ApiService {
    @FormUrlEncoded
    @POST("login.php")
    Call<Login> login(@Field("username") String username, @Field("password") String password , @Field("uuid") String uuid);

    @FormUrlEncoded
    @POST("config.php")
    Call<VpnConfigs> config(@Field("uuid") String uuid);
}
