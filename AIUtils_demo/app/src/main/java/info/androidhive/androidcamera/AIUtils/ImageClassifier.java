package info.androidhive.androidcamera.AIUtils;

import android.annotation.SuppressLint;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

public class ImageClassifier {

    private static final String CLASSIFIER_MODEL_PATH = "classifier.tflite";
    private static final int BATCH_SIZE = 1;
    private static final int PIXEL_SIZE = 3;
    private static final float CLASSIFIER_THRESHOLD = 0.9f;
    private static final boolean NORMALIZED = false;
    private static final boolean QUANTIZED = false;

    private static final int IMAGE_MEAN = 128;
    private static final float IMAGE_STD = 128.0f;
    private static final int CLASSIFIER_INPUT_WIDTH = 224;
    private static final int CLASSIFIER_INPUT_HEIGHT = 224;

    private static Interpreter classifier_interpreter;

    private ImageClassifier() {

    }

    public ImageClassifier(AssetManager assetManager) throws IOException {
        try {
            classifier_interpreter = new Interpreter(loadModelFile(assetManager, CLASSIFIER_MODEL_PATH), new Interpreter.Options());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int classify(Bitmap bitmap) {
        bitmap = Bitmap.createScaledBitmap(bitmap,CLASSIFIER_INPUT_WIDTH,CLASSIFIER_INPUT_HEIGHT,false);
        ByteBuffer byteBuffer = convertBitmapToByteBuffer(bitmap);
        float [][] result = new float[1][3];
        classifier_interpreter.run(byteBuffer, result);
        return getResult(result);
    }

    public void close() {
        classifier_interpreter.close();
        classifier_interpreter = null;
    }

    private MappedByteBuffer loadModelFile(AssetManager assetManager, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = assetManager.openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer;

        if (QUANTIZED) {
            byteBuffer = ByteBuffer.allocateDirect(BATCH_SIZE * CLASSIFIER_INPUT_WIDTH * CLASSIFIER_INPUT_HEIGHT * PIXEL_SIZE);
        } else {
            byteBuffer = ByteBuffer.allocateDirect(4 * BATCH_SIZE * CLASSIFIER_INPUT_WIDTH * CLASSIFIER_INPUT_HEIGHT * PIXEL_SIZE);
        }

        byteBuffer.order(ByteOrder.nativeOrder());
        int[] intValues = new int[CLASSIFIER_INPUT_WIDTH * CLASSIFIER_INPUT_HEIGHT];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        int pixel = 0;
        for (int i = 0; i < CLASSIFIER_INPUT_WIDTH * CLASSIFIER_INPUT_HEIGHT; ++i) {
            final int val = intValues[pixel++];
            if (QUANTIZED){
                byteBuffer.put((byte) ((val >> 16) & 0xFF));
                byteBuffer.put((byte) ((val >> 8) & 0xFF));
                byteBuffer.put((byte) (val & 0xFF));
            } else {
                if (NORMALIZED == true) {
                    byteBuffer.putFloat((((val >> 16) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
                    byteBuffer.putFloat((((val >> 8) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
                    byteBuffer.putFloat((((val) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
                } else {
                    byteBuffer.putFloat((((val >> 16) & 0xFF)));
                    byteBuffer.putFloat((((val >> 8) & 0xFF)));
                    byteBuffer.putFloat((((val) & 0xFF)));
                }
            }
        }
        return byteBuffer;
    }

    private int getResult(float[][] res) {
        int max = 0;
        if (res[0][max] < res[0][1])
            max = 1;
        if (res[0][max] < res[0][2])
            max = 2;
        if (res[0][max] < CLASSIFIER_THRESHOLD)
            return -1;
        return max+2;
    }

}
