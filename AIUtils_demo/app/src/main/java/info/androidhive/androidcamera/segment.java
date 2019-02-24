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
import android.graphics.Color;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.File;
import android.os.Environment;
import java.io.Writer;

public class segment {

    private static final int BATCH_SIZE = 1;
    private static final int PIXEL_SIZE = 3;
    private static final float THRESHOLD = 0.5f;
    private static final int IMAGE_MEAN = 128;
    private static final float IMAGE_STD = 128.0f;

    private Interpreter interpreter;

    private int inputSizeX;
    private int inputSizeY;
    private boolean isNormalized;
    private boolean quant;

    public segment(AssetManager assetManager,
                   String modelPath,
                   int _inputSizeX,
                   int _inputSizeY,
                   boolean _quant,
                   boolean _isNormalized) throws IOException {
        try {
            interpreter = new Interpreter(loadModelFile(assetManager, modelPath), new Interpreter.Options());
        } catch (IOException e) {
            e.printStackTrace();
        }
        inputSizeX = _inputSizeX;
        inputSizeY = _inputSizeY;
        isNormalized = _isNormalized;
        quant = _quant;
    }

    public Bitmap segmentImage(Bitmap bitmap) {
        ByteBuffer bytebuffer = convertBitmapToByteBuffer(bitmap);

        //1 * 512 * 256 * 1
        float result[][][][] = new float[1][inputSizeX][inputSizeY][1];

        interpreter.run(bytebuffer, result);

        return arrayToBitmap(result);
    }

    private Bitmap arrayToBitmap(float result[][][][]) {
        Bitmap res = Bitmap.createBitmap(inputSizeY, inputSizeX, Bitmap.Config.ARGB_8888);
        for (int i = 0; i < inputSizeX; ++i) {
            for (int j = 0; j < inputSizeY; ++j) {
                if (result[0][i][j][0] < THRESHOLD) {
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

        if(quant) {
            byteBuffer = ByteBuffer.allocateDirect(BATCH_SIZE * inputSizeX * inputSizeY * PIXEL_SIZE);
        } else {
            byteBuffer = ByteBuffer.allocateDirect(4 * BATCH_SIZE * inputSizeX * inputSizeY * PIXEL_SIZE);
        }

        byteBuffer.order(ByteOrder.nativeOrder());
        int[] intValues = new int[inputSizeX * inputSizeY];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        int pixel = 0;
        for (int i = 0; i < inputSizeX; ++i) {
            for (int j = 0; j < inputSizeY; ++j) {
                final int val = intValues[pixel++];
                if(quant){
                    byteBuffer.put((byte) ((val >> 16) & 0xFF));
                    byteBuffer.put((byte) ((val >> 8) & 0xFF));
                    byteBuffer.put((byte) (val & 0xFF));
                } else {
                    if (isNormalized) {
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
        }
        return byteBuffer;
    }

    public void arrayToFile(float[][][][] res){
        File root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File folder = new File(root, "text");
        if (!folder.exists()){
            folder.mkdirs();
        }
        Writer output = null;
        File json = new File(folder, "result.txt");

        try {
            output = new BufferedWriter(new FileWriter(json));
            for (int i = 0; i < 512; i++) {
                for (int j = 0; j < 256; j++) {
                    if (res[0][i][j][0] < THRESHOLD)
                        output.write("0 ");
                    else
                        output.write("1 ");
                }
                output.write("\n");
            }
            output.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void arrayToFile(int[] res){
        File root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File folder = new File(root, "text");
        if (!folder.exists()){
            folder.mkdirs();
        }
        Writer output = null;
        File json = new File(folder, "result.txt");

        try {
            output = new BufferedWriter(new FileWriter(json));
            for (int i = 0; i < inputSizeX * inputSizeY; ++i)
                output.write(Integer.toString(res[i]) + " ");
            output.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
