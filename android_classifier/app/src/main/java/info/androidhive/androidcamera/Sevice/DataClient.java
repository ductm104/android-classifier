package info.androidhive.androidcamera.Sevice;

import com.google.gson.JsonObject;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface DataClient {

    @Multipart()
    @POST("pusher")
    Call<MultipartBody> pusher(@Part MultipartBody.Part file, @Part("chambers") RequestBody chambers);

    @Multipart()
    @POST("pusher")
    Call<ResponseBody> uploadVideo(@Part MultipartBody.Part file, @Part("chambers") RequestBody chambers);
}