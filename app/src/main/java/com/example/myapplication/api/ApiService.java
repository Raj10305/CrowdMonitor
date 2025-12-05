package com.example.myapplication.api;

import com.example.myapplication.models.CrowdResponse;
import com.example.myapplication.models.FrameRequest;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface ApiService {

    @POST("detect")
    Call<CrowdResponse> detectCrowd(@Body FrameRequest frame);
}
