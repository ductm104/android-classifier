package info.androidhive.androidcamera.Sevice;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.converter.scalars.ScalarsConverterFactory;

public class RetrofitClient {

    private static Retrofit retrofit = null;

    public static Retrofit getClient(String baseURL){

        Gson gson = new GsonBuilder().setLenient().create();
        retrofit = new Retrofit.Builder()
                    .baseUrl(baseURL)
                    .addConverterFactory(GsonConverterFactory.create())//gson))
                    .addConverterFactory(ScalarsConverterFactory.create())
                    .client(new OkHttpClient())
                    .build();
        return retrofit;
    }
}
