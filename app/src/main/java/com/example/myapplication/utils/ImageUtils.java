package com.example.myapplication.utils;

import android.media.Image;

public class ImageUtils {
    public static byte[] convertImageToNV21(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();

        byte[] nv21 = new byte[width * height * 3 / 2];
        byte[] y = new byte[width * height];
        byte[] u = new byte[width * height / 4];
        byte[] v = new byte[width * height / 4];

        image.getPlanes()[0].getBuffer().get(y);
        image.getPlanes()[1].getBuffer().get(u);
        image.getPlanes()[2].getBuffer().get(v);

        System.arraycopy(y, 0, nv21, 0, y.length);

        for (int i = 0; i < u.length; i++) {
            nv21[y.length + (i * 2)] = v[i];
            nv21[y.length + (i * 2) + 1] = u[i];
        }

        return nv21;
    }
}
