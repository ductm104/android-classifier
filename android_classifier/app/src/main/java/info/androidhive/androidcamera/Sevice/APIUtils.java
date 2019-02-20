package info.androidhive.androidcamera.Sevice;

public class APIUtils {
    public static String baseURL = "";

    public APIUtils(String serverIP) {
        baseURL = serverIP;// "http://" + serverIP + "/";
    }

    public static DataClient getData(){

        return RetrofitClient.getClient(baseURL).create(DataClient.class);
    }
}
