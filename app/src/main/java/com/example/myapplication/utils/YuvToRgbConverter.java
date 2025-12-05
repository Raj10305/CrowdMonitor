package com.example.myapplication.utils;

import android.content.Context;
import android.graphics.Bitmap;
import androidx.camera.core.ImageProxy;
import android.media.Image;
import android.renderscript.*;

public class YuvToRgbConverter {
    private static RenderScript rs;
    private static ScriptIntrinsicYuvToRGB yuvToRgb;

    public static Bitmap convert(Context context, Image image) {
        if (rs == null) {
            rs = RenderScript.create(context);
            yuvToRgb = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs));
        }

        byte[] yuvBytes = ImageUtils.convertImageToNV21(image);
        int width = image.getWidth();
        int height = image.getHeight();

        Allocation in = Allocation.createSized(rs, Element.U8(rs), yuvBytes.length);
        Allocation out = Allocation.createFromBitmap(rs, Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888));

        in.copyFrom(yuvBytes);
        yuvToRgb.setInput(in);
        yuvToRgb.forEach(out);

        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        out.copyTo(bmp);
        return bmp;
    }
}
