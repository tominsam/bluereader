package org.movieos.feeder.utilities;

import android.databinding.BindingAdapter;
import android.graphics.Typeface;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;

public class DataBinding {

    @BindingAdapter({"dateText"})
    public static void dateText(TextView textView, Date date) {
//        Format dateFormat = android.text.format.DateFormat.getDateFormat(textView.getContext());
//        String pattern = ((SimpleDateFormat) dateFormat).toLocalizedPattern();
//        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.getDefault());
        DateFormat format = DateFormat.getDateInstance(DateFormat.LONG);
        textView.setText(format.format(date));
    }

    @BindingAdapter({"bold"})
    public static void bold(TextView textView, boolean bold) {
        textView.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    }

}
