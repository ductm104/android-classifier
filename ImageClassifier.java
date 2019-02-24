package info.androidhive.androidcamera;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import org.tensorflow.lite.Interpreter;
import java.io.FileInputStream;
import java.io.IOException;
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
    private static final int INPUT_WIDTH = 224;
    private static final int INPUT_HEIGHT = 224;

    private static Interpreter interpreter;

    private ImageClassifier() {

    }
    private List<Integer> chamberList;
    private List<Bitmap> imageList;

    public ImageClassifier(AssetManager assetManager) throws IOException {
        try {
            interpreter = new Interpreter(loadModelFile(assetManager, CLASSIFIER_MODEL_PATH), new Interpreter.Options());
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.chamberList = new ArrayList<>();
        this.imageList = new ArrayList<>();
    }

    public void feed(List<Bitmap> inputList) {
        this.imageList = inputList;
        this.chamberList = new ArrayList<>();
    }

    public void run() {
        for (Bitmap input : imageList) {
            input = Bitmap.createScaledBitmap(input, INPUT_WIDTH, INPUT_HEIGHT, false);
            ByteBuffer byteBuffer = convertBitmapToByteBuffer(input);

            float[][] result = new float[1][3];
            interpreter.run(byteBuffer, result);

            chamberList.add(getResult(result));
        }
    }

    public List<Integer> getChamberList() {
        return this.chamberList;
    }

    public void close() {
        interpreter.close();
        interpreter = null;
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
            byteBuffer = ByteBuffer.allocateDirect(BATCH_SIZE * INPUT_HEIGHT * INPUT_WIDTH * PIXEL_SIZE);
        } else {
            byteBuffer = ByteBuffer.allocateDirect(4 * BATCH_SIZE * INPUT_HEIGHT * INPUT_WIDTH * PIXEL_SIZE);
        }

        byteBuffer.order(ByteOrder.nativeOrder());
        int[] intValues = new int[INPUT_HEIGHT * INPUT_WIDTH];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        int pixel = 0;
        for (int i = 0; i < INPUT_HEIGHT * INPUT_WIDTH; ++i) {
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
