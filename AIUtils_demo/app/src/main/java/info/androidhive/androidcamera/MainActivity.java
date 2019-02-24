package info.androidhive.androidcamera;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.graphics.Bitmap;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import android.support.v4.app.ActivityCompat;
import info.androidhive.androidcamera.AIUtils.*;

public class MainActivity extends AppCompatActivity {

    private static final String MODEL_PATH = "model_segment.tflite";
    private static final boolean QUANT = false;
    private static final int INPUT_SIZE_X = 512;
    private static final int INPUT_SIZE_Y = 256;

    // Activity request codes
    private static final int CAMERA_CAPTURE_IMAGE_REQUEST_CODE = 100;

    // key to store image path in savedInstance state
    public static final String KEY_VIDEO_STORAGE_PATH = "image_path";

    public static final int MEDIA_TYPE_IMAGE = 1;

    // Bitmap sampling size
    public static final int BITMAP_SAMPLE_SIZE = 8;

    // Gallery directory name to store the images or videos
    public static final String GALLERY_DIRECTORY_NAME = "Camera";

    // Image and Video file extensions
    public static final String IMAGE_EXTENSION = "jpg";

    private static String videoStoragePath;

    private ImageView imgPreview;
    private ImageView imgMask;
    private Button btnRequestUpload;
    private TextView txtPrediction;
    private AlertDialog ad;

    private Executor executor = Executors.newSingleThreadExecutor();
    private AIutils aiUtils;
    private segment segmentator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActivityCompat.requestPermissions(MainActivity.this,
                new String[]{ Manifest.permission.WRITE_EXTERNAL_STORAGE},
                0);

        // Checking availability of the camera
        if (!CameraUtils.isDeviceSupportCamera(getApplicationContext())) {
            Toast.makeText(getApplicationContext(),
                    "Sorry! Your device doesn't support camera",
                    Toast.LENGTH_LONG).show();
            // will close the app if the device doesn't have camera
            finish();
        }

        imgMask = findViewById(R.id.imgMask);
        imgPreview = findViewById(R.id.imgPreview);
        btnRequestUpload = findViewById(R.id.btnRequestUpload);
        txtPrediction = findViewById(R.id.web_url);

        /**
         * Capture image on button click
         */
        btnRequestUpload.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (CameraUtils.checkPermissions(getApplicationContext())) {
                    captureImage();
                } else {
                    requestCameraPermission(MEDIA_TYPE_IMAGE);
                }
            }
        });

        initTensorFlowAndLoadModel();
        restoreFromBundle(savedInstanceState);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == 0){
            if(grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "App need connect internet.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Restoring store image path from saved instance state
     */
    private void restoreFromBundle(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(KEY_VIDEO_STORAGE_PATH)) {
                videoStoragePath = savedInstanceState.getString(KEY_VIDEO_STORAGE_PATH);
                if (!TextUtils.isEmpty(videoStoragePath)) {
                    if(videoStoragePath.substring(videoStoragePath.lastIndexOf(".")).equals("." + IMAGE_EXTENSION)) {
                        previewCapturedImage();
                    }
                }
            }
        }
    }

    /**
     * Requesting permissions using Dexter library
     */
    private void requestCameraPermission(final int type) {
        Dexter.withActivity(this)
                .withPermissions(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .withListener(new MultiplePermissionsListener() {
                    @Override
                    public void onPermissionsChecked(MultiplePermissionsReport report) {
                        if (report.areAllPermissionsGranted()) {
                            if (type == MEDIA_TYPE_IMAGE) {
                                captureImage();
                            }
                        } else if (report.isAnyPermissionPermanentlyDenied()) {
                            showPermissionsAlert();
                        }
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(List<PermissionRequest> permissions, PermissionToken token) {
                        token.continuePermissionRequest();
                    }
                }).check();
    }

    /**
     * Saving stored image path to saved instance state
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // save file url in bundle as it will be null on screen orientation
        // changes
        outState.putString(KEY_VIDEO_STORAGE_PATH, videoStoragePath);
    }

    /**
     * Restoring image path from saved instance state
     */
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // get the file url
        videoStoragePath = savedInstanceState.getString(KEY_VIDEO_STORAGE_PATH);
    }

    /**
     * Capturing Camera Image will launch camera app requested image capture
     */
    private void captureImage() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        File file = CameraUtils.getOutputMediaFile(MEDIA_TYPE_IMAGE);
        if (file != null) {
            videoStoragePath = file.getAbsolutePath();
        }

        Uri fileUri = CameraUtils.getOutputMediaFileUri(getApplicationContext(), file);

        intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);

        // start the image capture Intent
        startActivityForResult(intent, CAMERA_CAPTURE_IMAGE_REQUEST_CODE);
    }

    /**
     * Activity result method will be called after closing the camera
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CAMERA_CAPTURE_IMAGE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // Refreshing the gallery
                CameraUtils.refreshGallery(getApplicationContext(), videoStoragePath);
                previewCapturedImage();
            } else if (resultCode == RESULT_CANCELED) {
                // user cancelled Image capture
            } else {
                // failed to capture image
                Toast.makeText(getApplicationContext(), "Sorry! Failed to capture image", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Display image from gallery
     */
    private void previewCapturedImage() {
        try {
            imgPreview.setVisibility(View.VISIBLE);
            Bitmap bitmap = CameraUtils.optimizeBitmap(BITMAP_SAMPLE_SIZE, videoStoragePath);
            imgPreview.setImageBitmap(bitmap);
            predict(bitmap);
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    /**
     * Alert dialog to navigate to app settings
     * to enable necessary permissions
     */
    private void showPermissionsAlert() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Permissions required!")
                .setMessage("Camera needs few permissions to work properly. Grant them in settings.")
                .setPositiveButton("GOTO SETTINGS", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        CameraUtils.openSettings(MainActivity.this);
                    }
                })
                .setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                    }
                }).show();
    }

    private void initTensorFlowAndLoadModel() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
//                    segmentator = new segment(getAssets(),
//                            MODEL_PATH,
//                            INPUT_SIZE_X,
//                            INPUT_SIZE_Y,
//                            QUANT,
//                            false);
                    aiUtils = new AIutils(getAssets());
                } catch (final Exception e) {
                    throw new RuntimeException("Error initializing TensorFlow!", e);
                }
            }
        });
    }

//    private void predict(Bitmap bitmap) {
//        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE_Y, INPUT_SIZE_X, false);
//
//        imgPreview.setImageBitmap(resizedBitmap);
//        imgMask.setImageBitmap(segmentator.segmentImage(resizedBitmap));
//    }

    private void predict(Bitmap bitmap) {
        imgPreview.setImageBitmap(bitmap);

        List<Bitmap> list = new ArrayList<>();
        list.add(bitmap);

        aiUtils.feed(list);
        aiUtils.run();

        imgMask.setImageBitmap(aiUtils.getMaxImage());
        txtPrediction.setText(aiUtils.getChambersList().get(0).toString());

//        Thread thread = new Thread(){
//            @Override
//            public void run() {
//                try {
//                    synchronized (this) {
//                        wait(0);
//
//                        runOnUiThread(new Runnable() {
//                            @Override
//                            public void run() {
//                                aiUtils.run();
//                                imgMask.setImageBitmap(aiUtils.getMaxImage());
//                            }
//                        });
//
//                    }
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//                //Intent mainActivity = new Intent(getApplicationContext(),MainActivity.class);
//                //startActivity(mainActivity);
//            };
//        };
//        thread.start();
    }
}