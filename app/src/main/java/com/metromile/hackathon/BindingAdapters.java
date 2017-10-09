package com.metromile.hackathon;

import android.databinding.BindingAdapter;
import android.widget.ImageView;

import com.squareup.picasso.Picasso;

public class BindingAdapters {
    @BindingAdapter("imageSrc")
    public static void setImageSrc(ImageView imageView, String url) {
        Picasso.with(imageView.getContext())
                .load(url)
                .into(imageView);
    }
}
