package org.movieos.feeder.api;

import android.content.Context;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import org.movieos.feeder.BuildConfig;
import org.movieos.feeder.model.Entry;
import org.movieos.feeder.model.Subscription;
import org.movieos.feeder.utilities.Settings;

import java.lang.reflect.Type;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;
import retrofit2.http.Url;
import timber.log.Timber;

@SuppressWarnings("WeakerAccess")
public class Feedbin {

    protected static final int CACHE_SIZE_BYTES = 1024 * 1024 * 2;
    protected static final String UTC_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    RawApi mApi;

    public static void authenticate(Context context, String credentials, Callback<Void> callback) {
        api(context, credentials).authentication().enqueue(callback);
    }

    public Feedbin(Context context) {
        mApi = api(context, Settings.getCredentials(context));
    }

    public Call<List<Subscription>> subscriptions(Date since) {
        return mApi.subscriptions(formatDate(since));
    }

    public Call<List<Subscription>> subscriptionsPaginate(String next) {
        return mApi.subscriptionsPaginate(next);
    }

    public Call<List<Entry>> entries(Date since) {
        return mApi.entries(formatDate(since));
    }

    public Call<List<Entry>> entriesPaginate(String url) {
        return mApi.entriesPaginate(url);
    }

    public Call<List<Integer>> starred() {
        return mApi.starred();
    }

    public Call<List<Integer>> unread() {
        return mApi.unread();
    }

    public Call<Void> addStarred(Iterable<Integer> ids) {
        JsonObject base = new JsonObject();
        JsonArray jsonIds = new JsonArray();
        for (Integer id : ids) {
            jsonIds.add(id);
        }
        base.add("starred_entries", jsonIds);
        return mApi.addStarred(base);
    }

    public Call<Void> removeStarred(Iterable<Integer> ids) {
        JsonObject base = new JsonObject();
        JsonArray jsonIds = new JsonArray();
        for (Integer id : ids) {
            jsonIds.add(id);
        }
        base.add("starred_entries", jsonIds);
        return mApi.removeStarred(base);
    }

    public Call<Void> addUnread(Iterable<Integer> ids) {
        JsonObject base = new JsonObject();
        JsonArray jsonIds = new JsonArray();
        for (Integer id : ids) {
            jsonIds.add(id);
        }
        base.add("unread_entries", jsonIds);
        return mApi.addUnread(base);
    }

    public Call<Void> removeUnread(Iterable<Integer> ids) {
        JsonObject base = new JsonObject();
        JsonArray jsonIds = new JsonArray();
        for (Integer id : ids) {
            jsonIds.add(id);
        }
        base.add("unread_entries", jsonIds);
        return mApi.removeUnread(base);
    }

    public Call<List<Entry>> entries(List<Integer> ids) {
        return mApi.entriesById(TextUtils.join(",", ids));
    }


    private static String formatDate(Date date) {
        if (date == null) {
            return null;
        }
        DateFormat localFormat = new SimpleDateFormat(UTC_DATE_FORMAT, Locale.US);
        localFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        return localFormat.format(date);
    }

    private static RawApi api(Context context, String credentials) {

        Gson gson = new GsonBuilder()
                .setDateFormat(UTC_DATE_FORMAT)
                .registerTypeAdapter(Date.class, new JavaDateDeserializer())
                .create();

        OkHttpClient client = new OkHttpClient.Builder()
                .cache(new Cache(context.getCacheDir(), CACHE_SIZE_BYTES))
                .addInterceptor(chain -> {
                    Request request = chain.request().newBuilder()
                            .addHeader("Authorization", credentials)
                            .build();
                    return chain.proceed(request);
                })
                .sslSocketFactory(dangerousSocketFactory())
                .build();

        Retrofit sRetrofit = new Retrofit.Builder()
                .baseUrl("https://api.feedbin.com/v2/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        return sRetrofit.create(RawApi.class);
    }

    private static SSLSocketFactory dangerousSocketFactory() {
        if (!BuildConfig.DEBUG) {
            return null;
        }
        final TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[]{};
                    }
                }
        };

        try {
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            return sslContext.getSocketFactory();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            Timber.e(e);
        }
        return null;
    }

    private interface RawApi {
        @GET("authentication.json")
        Call<Void> authentication();

        @GET("subscriptions.json")
        Call<List<Subscription>> subscriptions(@Query("since") String since);

        @GET
        Call<List<Subscription>> subscriptionsPaginate(@Url String url);

        @GET("entries.json")
        Call<List<Entry>> entries(@Query("since") String since);

        @GET
        Call<List<Entry>> entriesPaginate(@Url String url);

        @GET("starred_entries.json")
        Call<List<Integer>> starred();

        @GET("unread_entries.json")
        Call<List<Integer>> unread();

        @POST("starred_entries.json")
        Call<Void> addStarred(@Body JsonObject json);

        @POST("starred_entries/delete.json")
        Call<Void> removeStarred(@Body JsonObject json);

        @POST("unread_entries.json")
        Call<Void> addUnread(@Body JsonObject json);

        @POST("unread_entries/delete.json")
        Call<Void> removeUnread(@Body JsonObject json);

        @GET("entries.json")
        Call<List<Entry>> entriesById(@Query("ids") String ids);
    }

    private static class JavaDateDeserializer implements JsonDeserializer<Date> {

        @Override
        public Date deserialize(JsonElement je, Type type, JsonDeserializationContext jdc) throws JsonParseException {
            String myDate = je.getAsString();
            try {
                DateFormat localFormat = new SimpleDateFormat(UTC_DATE_FORMAT, Locale.US);
                localFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                return localFormat.parse(myDate);
            } catch (ParseException e) {
                throw new JsonParseException(e);
            }
        }
    }


}
