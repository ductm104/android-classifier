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

import android.graphics.Color;

public class ImageSegmentator {
    private static final String MODEL_PATH = "segmentator.tflite";
    private static final int BATCH_SIZE = 1;
    private static final int PIXEL_SIZE = 3;
    private static final float THRESHOLD = 0.5f;
    private static final int IMAGE_MEAN = 128;
    private static final float IMAGE_STD = 128.0f;
    private static final boolean NORMALIZED = false;
    private static final boolean QUANTIZED = false;

    private static final int INPUT_WIDTH = 256;
    private static final int INPUT_HEIGHT = 512;

    private static Interpreter interpreter;

    private List<Bitmap> imageList;
    private List<int[][]> maskList;
    private List<Integer> areaList;
    private float ef;
    private Bitmap maxMask;
    private Bitmap minMask;

    private ImageSegmentator() {

    }

    public ImageSegmentator(AssetManager assetManager) throws IOException {
        try {
            interpreter = new Interpreter(loadModelFile(assetManager, MODEL_PATH), new Interpreter.Options());
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.imageList = new ArrayList<>();
        this.maskList = new ArrayList<>();
        this.areaList = new ArrayList<>();
        this.maxMask = null;
        this.minMask = null;
    }

    public void feed(List<Bitmap> inputList) {
        this.imageList = inputList;
        this.areaList = new ArrayList<>();
        this.maskList = new ArrayList<>();
    }

    public void run() {
        for (Bitmap input : imageList) {
            input = Bitmap.createScaledBitmap(input, INPUT_WIDTH, INPUT_HEIGHT, false);
            ByteBuffer bytebuffer = convertBitmapToByteBuffer(input);

            float result[][][][] = new float[1][INPUT_HEIGHT][INPUT_WIDTH][1];
            interpreter.run(bytebuffer, result);
            addMask(result);
        }
        calcFinalResult();
    }

    private void addMask(float[][][][] result) {
        int[][] mask = new int[INPUT_HEIGHT][INPUT_WIDTH];
        int area = 0;

        for (int i = 0; i < INPUT_HEIGHT; i++) {
            for (int j = 0; j < INPUT_WIDTH; j++) {
                if (result[0][i][j][0] < THRESHOLD) {
                    mask[i][j] = 0;
                    area++;
                } else {
                    mask[i][j] = 1;
                }
            }
        }
        maskList.add(mask);
        areaList.add(area);
    }

    private void calcFinalResult() {
        if (imageList.size() == 0) {
            return;
        }

        int indexMax = 0;
        int indexMin = 0;
        for (int i = 0; i < areaList.size(); i++) {
            if (areaList.get(i) > areaList.get(indexMax)) {
                indexMax = i;
            }
            if (areaList.get(i) < areaList.get(indexMin)) {
                indexMin = i;
            }
        }

        this.ef = (areaList.get(indexMax) - areaList.get(indexMin));
        this.maxMask = arrayToBitmap(maskList.get(indexMax));
        this.minMask = arrayToBitmap(maskList.get(indexMin));
    }

    public float getEF() {
        return this.ef;
    }

    public Bitmap getMaxImage() {
        return this.maxMask;
    }

    public Bitmap getMinImage() {
        return this.minMask;
    }

    private Bitmap arrayToBitmap(int[][] result) {
        Bitmap res = Bitmap.createBitmap(INPUT_WIDTH, INPUT_HEIGHT, Bitmap.Config.ARGB_8888);
        for (int i = 0; i < INPUT_HEIGHT; ++i) {
            for (int j = 0; j < INPUT_WIDTH; ++j) {
                if (result[i][j] == 0) {
                    res.setPixel(j, i, Color.BLACK);
                } else {
                    res.setPixel(j, i, Color.WHITE);
                }
            }
        }
        return res;
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
}