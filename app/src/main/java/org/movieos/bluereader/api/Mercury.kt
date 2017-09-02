package org.movieos.bluereader.api

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.movieos.bluereader.model.Entry
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import timber.log.Timber

val HEADER = "x-api-key"
val TOKEN = "QJGAkc1Vi6mxyifpqO5t6WNfbWS2odszpl8WfiRY"

val content = mutableMapOf<Int, String?>()

class Mercury(context: Context) {

    private var api = api(context)

    fun contentFor(entry: Entry?) : String? {
        return content[entry?.id]
    }

    fun clearContent(entry: Entry?) {
        if (entry != null) {
            content[entry.id] = null
        }
    }

    fun parser(entry: Entry?, done: () -> Unit) {
        val url = entry?.url ?: return
        api.parser(url).enqueue(object: Callback<MercuryResponse?> {
            override fun onResponse(call: Call<MercuryResponse?>?, response: Response<MercuryResponse?>?) {
                content[entry.id] = response?.body()?.content
                Timber.i("content received: ${content[entry.id]} for $entry")
                done()
            }

            override fun onFailure(call: Call<MercuryResponse?>?, t: Throwable?) {
                Timber.i(t, "content failed")
                content[entry.id] = null
                done()
            }
        })
    }

    private fun api(context: Context): RawApi {
        val gson = GsonBuilder()
                .create()

        var builder = OkHttpClient.Builder()
                .cache(Cache(context.cacheDir, Feedbin.CACHE_SIZE_BYTES.toLong()))
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                    request.addHeader(HEADER, TOKEN)
                    chain.proceed(request.build())
                }

        val dangerousSocketFactory = Feedbin.dangerousSocketFactory()
        if (dangerousSocketFactory != null) {
            builder = builder.sslSocketFactory(dangerousSocketFactory.first, dangerousSocketFactory.second)
        }

        return Retrofit.Builder()
                .baseUrl("https://mercury.postlight.com/")
                .addConverterFactory(GsonConverterFactory.create(gson))
                .client(builder.build())
                .build()
                .create(RawApi::class.java)
    }

    private interface RawApi {
        @GET("parser")
        fun parser(@Query("url") url: String): Call<MercuryResponse>

    }

    class MercuryResponse {
        @SerializedName("content")
        val content: String = ""
    }

}
