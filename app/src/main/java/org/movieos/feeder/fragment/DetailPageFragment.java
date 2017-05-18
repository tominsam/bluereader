package org.movieos.feeder.fragment;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.customtabs.CustomTabsIntent;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.jetbrains.annotations.NotNull;
import org.movieos.feeder.R;
import org.movieos.feeder.databinding.DetailPageFragmentBinding;
import org.movieos.feeder.model.Entry;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;

import io.realm.Realm;
import timber.log.Timber;

public class DetailPageFragment extends DataBindingFragment<DetailPageFragmentBinding> {

    private static final String ENTRY_ID = "entry_id";

    static String sTemplate;

    Entry mEntry;

    public static DetailPageFragment create(int entryId) {
        DetailPageFragment fragment = new DetailPageFragment();
        fragment.setArguments(new Bundle());
        fragment.getArguments().putInt(ENTRY_ID, entryId);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setUserVisibleHint(false);
        Realm realm = Realm.getDefaultInstance();
        mEntry = Entry.Companion.byId(getArguments().getInt(ENTRY_ID));
        realm.close();
    }

    @SuppressLint("SetJavaScriptEnabled")
    @NonNull
    @Override
    protected DetailPageFragmentBinding createBinding(@NotNull final LayoutInflater inflater, @org.jetbrains.annotations.Nullable final ViewGroup container) {
        DetailPageFragmentBinding binding = DetailPageFragmentBinding.inflate(inflater, container, false);

        // Needed for youtube embeds to work
        binding.webView.getSettings().setJavaScriptEnabled(true);
        binding.webView.setWebChromeClient(new WebChromeClient() {});
        binding.webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
                builder.setToolbarColor(ContextCompat.getColor(getActivity(), R.color.primary));
                builder.addDefaultShareMenuItem();
                CustomTabsIntent customTabsIntent = builder.build();
                customTabsIntent.launchUrl(getActivity(), request.getUrl());
                return true;
            }
        });

        binding.webView.loadDataWithBaseURL(null,
            getTemplate()
                .replace("{{body}}", mEntry.getContent())
                .replace("{{title}}", mEntry.getTitle())
                .replace("{{link}}", mEntry.getUrl())
                .replace("{{author}}", mEntry.getDisplayAuthor())
                .replace("{{date}}", DateFormat.getDateTimeInstance().format(mEntry.getPublished()))
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

    @Override
    public void onResume() {
        super.onResume();
        if (isVisible()) {
            Timber.i("entry " + mEntry + " is visible");
        }

        if (isVisible() && mEntry.isLocallyUnread()) {
            Realm realm = Realm.getDefaultInstance();
            //Entry.setUnread(getContext(), realm, mEntry, false);
            realm.close();
        }

    }
}