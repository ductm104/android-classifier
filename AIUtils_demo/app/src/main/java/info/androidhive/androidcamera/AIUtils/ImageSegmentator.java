package info.androidhive.androidcamera.AIUtils;

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

public class ImageSegmentator {
    private static final String SEGMENTATOR_MODEL_PATH = "segmentator.tflite";

    private static final int BATCH_SIZE = 1;
    private static final int PIXEL_SIZE = 3;
    private static final int IMAGE_MEAN = 128;
    private static final float IMAGE_STD = 128.0f;

    private static final float SEGMENTATOR_THRESHOLD = 0.5f;

    private static Interpreter segmentator_interpreter;

    private static final int SEGMENTATOR_INPUT_WIDTH = 256;
    private static final int SEGMENTATOR_INPUT_HEIGHT = 512;
    private static final boolean NORMALIZED = false;
    private static final boolean QUANTIZED = false;

    private ImageSegmentator() {

    }

    public ImageSegmentator(AssetManager assetManager) throws IOException {
        try {
            segmentator_interpreter = new Interpreter(loadModelFile(assetManager, SEGMENTATOR_MODEL_PATH), new Interpreter.Options());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int[][] segmentImage(Bitmap bitmap) {
        bitmap = Bitmap.createScaledBitmap(bitmap, SEGMENTATOR_INPUT_WIDTH, SEGMENTATOR_INPUT_HEIGHT, false);
        ByteBuffer bytebuffer = convertBitmapToByteBuffer(bitmap);

        float result[][][][] = new float[1][SEGMENTATOR_INPUT_HEIGHT][SEGMENTATOR_INPUT_WIDTH][1];

        segmentator_interpreter.run(bytebuffer, result);

        return toBinaryArray(result);
    }

    private int[][] toBinaryArray(float[][][][] res) {
        int[][] mask = new int[SEGMENTATOR_INPUT_HEIGHT][SEGMENTATOR_INPUT_WIDTH];
        for (int i = 0; i < SEGMENTATOR_INPUT_HEIGHT; i++) {
            for (int j = 0; j < SEGMENTATOR_INPUT_WIDTH; j++) {
                if (res[0][i][j][0] < SEGMENTATOR_THRESHOLD) {
                    mask[i][j] = 0;
                } else {
                    mask[i][j] = 1;
                }
            }
        }
        return mask;
    }

    public void close() {
        segmentator_interpreter.close();
        segmentator_interpreter = null;
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
            byteBuffer = ByteBuffer.allocateDirect(BATCH_SIZE * SEGMENTATOR_INPUT_WIDTH * SEGMENTATOR_INPUT_HEIGHT * PIXEL_SIZE);
        } else {
            byteBuffer = ByteBuffer.allocateDirect(4 * BATCH_SIZE * SEGMENTATOR_INPUT_WIDTH * SEGMENTATOR_INPUT_HEIGHT * PIXEL_SIZE);
        }

        byteBuffer.order(ByteOrder.nativeOrder());
        int[] intValues = new int[SEGMENTATOR_INPUT_WIDTH * SEGMENTATOR_INPUT_HEIGHT];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        for (int i = 0; i < SEGMENTATOR_INPUT_WIDTH * SEGMENTATOR_INPUT_HEIGHT; i++) {
            final int val = intValues[i];
            if (QUANTIZED){
                byteBuffer.put((byte) ((val >> 16) & 0xFF));
                byteBuffer.put((byte) ((val >> 8) & 0xFF));
                byteBuffer.put((byte) (val & 0xFF));
            } else {
                if (NORMALIZED) {
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

    public int getWidth() {
        return SEGMENTATOR_INPUT_WIDTH;
    }

    public int getHeight() {
        return SEGMENTATOR_INPUT_HEIGHT;
    }
}
