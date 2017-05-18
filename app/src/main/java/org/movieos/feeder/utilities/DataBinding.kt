package org.movieos.feeder.utilities

import android.databinding.BindingAdapter
import android.graphics.Typeface
import android.widget.Button
import android.widget.TextView
import java.text.DateFormat
import java.util.*

@BindingAdapter("dateText")
fun dateText(textView: TextView, date: Date) {
    val format = DateFormat.getDateTimeInstance()
    textView.text = format.format(date)
}

@BindingAdapter("bold")
fun bold(textView: TextView, bold: Boolean) {
    textView.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
}

@BindingAdapter("selected")
fun selected(view: Button, selected: Boolean) {
    view.isSelected = selected
}

