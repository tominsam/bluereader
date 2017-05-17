package org.movieos.feeder.utilities;

import android.databinding.BindingAdapter;
import android.graphics.Typeface;
import android.widget.Button;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;

public class DataBinding {

    @BindingAdapter({"dateText"})
    public static void dateText(TextView textView, Date date) {
        DateFormat format = DateFormat.getDateTimeInstance();
        textView.setText(format.format(date));
    }

    @BindingAdapter({"bold"})
    public static void bold(TextView textView, boolean bold) {
        textView.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    }

    @BindingAdapter({"selected"})
    public static void selected(Button view, boolean selected) {
        view.setSelected(selected);
    }

}
