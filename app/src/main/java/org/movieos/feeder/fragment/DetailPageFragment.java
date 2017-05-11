package org.movieos.feeder.fragment;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;

import org.movieos.feeder.R;
import org.movieos.feeder.databinding.DetailPageFragmentBinding;
import org.movieos.feeder.model.Entry;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class DetailPageFragment extends DataBindingFragment<DetailPageFragmentBinding> {

    private static final String INDEX = "index";

    static String sTemplate;

    public static DetailPageFragment create(int index) {
        DetailPageFragment fragment = new DetailPageFragment();
        fragment.setArguments(new Bundle());
        fragment.getArguments().putInt(INDEX, index);
        return fragment;
    }

    public Entry getEntry() {
        return ((DetailFragment) getParentFragment()).getEntry(getArguments().getInt(INDEX));
    }

    @SuppressLint("SetJavaScriptEnabled")
    @NonNull
    @Override
    protected DetailPageFragmentBinding createBinding(final LayoutInflater inflater, final ViewGroup container) {
        DetailPageFragmentBinding binding = DetailPageFragmentBinding.inflate(inflater, container, false);

        // Needed for youtube embeds to work
        binding.webView.getSettings().setJavaScriptEnabled(true);
        binding.webView.setWebChromeClient(new WebChromeClient() {});

        binding.webView.loadDataWithBaseURL(null,
            getTemplate()
                .replace("{{body}}", getEntry().getContent())
                .replace("{{title}}", getEntry().getTitle())
            , "text/html", "utf-8", "");
        return binding;
    }

    private String getTemplate() {
        if (sTemplate == null) {
            try {
                InputStream inputStream = getResources().openRawResource(R.raw.template);

                StringBuilder inputStringBuilder = new StringBuilder();
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
                String line = bufferedReader.readLine();

                while (line != null) {
                    inputStringBuilder.append(line);
                    inputStringBuilder.append('\n');
                    line = bufferedReader.readLine();
                }

                sTemplate = inputStringBuilder.toString();

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return sTemplate;
    }

}