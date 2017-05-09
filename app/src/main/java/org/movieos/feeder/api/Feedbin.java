package org.movieos.feeder.api;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import org.movieos.feeder.BuildConfig;
import org.movieos.feeder.Settings;
import org.movieos.feeder.sync.Subscription;

import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Query;

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

    public Call<List<Subscription>> subscriptions(Date since, int page) {
        return mApi.subscriptions(formatDate(since), page);
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
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(BuildConfig.DEBUG ? HttpLoggingInterceptor.Level.BODY : HttpLoggingInterceptor.Level.NONE);

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
                .addInterceptor(logging)
                .build();

        Retrofit sRetrofit = new Retrofit.Builder()
                .baseUrl("https://api.feedbin.com/v2/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        return sRetrofit.create(RawApi.class);
    }

    private interface RawApi {
        @GET("authentication.json")
        Call<Void> authentication();

        @GET("subscriptions.json")
        Call<List<Subscription>> subscriptions(@Query("since") String since, @Query("page") int page);
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
