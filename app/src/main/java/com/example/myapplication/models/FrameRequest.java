package com.example.myapplication.models;

import com.google.gson.annotations.SerializedName;

public class FrameRequest {

    @SerializedName("image")  // MUST match Flask server: "image" not "frame"
    public String image;

    public FrameRequest(String image) {
        this.image = image;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }
}