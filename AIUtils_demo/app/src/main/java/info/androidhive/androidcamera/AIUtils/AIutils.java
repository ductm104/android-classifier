package info.androidhive.androidcamera.AIUtils;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import info.androidhive.androidcamera.MainActivity;

public class AIutils {


    private int segmentWIDTH = 256;
    private int segmentHEIGHT = 512;

    private List<Bitmap> inputBitmapList;
    private float ef;
    private Bitmap maxImage;
    private Bitmap minImage;
    private List<int[][]> maskList;
    private List<Integer> areaList;
    private List<Integer> chambersList;

    private boolean isDone;

    private ImageSegmentator segmentator;
    private ImageClassifier classifier;


    public AIutils(AssetManager assetManager) throws IOException {
        try {
            this.segmentator = new ImageSegmentator(assetManager);
            this.classifier = new ImageClassifier(assetManager);
        } catch (final Exception e) {
            throw new RuntimeException("Error initializing TensorFlow!", e);
        }

        this.inputBitmapList = new ArrayList<>();
        this.areaList = new ArrayList<>();
        this.chambersList = new ArrayList<>();
        this.maskList = new ArrayList<>();
    }

    public void feed(List<Bitmap> inputList) {
        this.inputBitmapList = inputList;
    }

    public void run() {
        this.isDone = false;
        for (Bitmap img : inputBitmapList) {
            chambersList.add(classifier.classify(img));
            maskList.add(segmentator.segmentImage(img));
        }

        calcS();
        int indexMax = 0, indexMin = 0;
        for (int i = 0; i < inputBitmapList.size(); i++) {
            if (areaList.get(i) > areaList.get(indexMax)) {
                indexMax = i;
            }

            if (areaList.get(i) < areaList.get(indexMin)) {
                indexMin = i;
            }
        }

        this.maxImage = arrayToBitmap(maskList.get(indexMax), inputBitmapList.get(indexMax));
        this.minImage = arrayToBitmap(maskList.get(indexMin));
        this.ef = calcEF(areaList.get(indexMin), areaList.get(indexMax));
        this.isDone = true;
    }

    private float calcEF(int minValue, int maxValue) {
        return 50f;
    }

    private void calcS() {
        for (int[][] mask : maskList) {
            int res = 0;
            for (int i = 0; i < segmentator.getHeight(); i++) {
                for (int j = 0; j < segmentator.getWidth(); j++) {
                    res += mask[i][j];
                }
            }
            areaList.add(res);
        }
    }

    public List<Integer> getChambersList() {
        return chambersList;
    }

    public float getEF() {
        return this.ef;
    }

    public Bitmap getMaxImage() {
        return this.maxImage;
    }

    public Bitmap getMinImage() {
        return this.minImage;
    }

    private Bitmap arrayToBitmap(int[][] mask) {
        Bitmap res = Bitmap.createBitmap(segmentator.getWidth(), segmentator.getHeight(), Bitmap.Config.ARGB_8888);
        for (int i = 0; i < segmentator.getHeight(); ++i) {
            for (int j = 0; j < segmentator.getWidth(); ++j) {
                if (mask[i][j] == 0) {
                    res.setPixel(j, i, Color.BLACK);
                } else {
                    res.setPixel(j, i, Color.WHITE);
                }
            }
        }
        return res;
    }

    private Bitmap arrayToBitmap(int[][] mask, Bitmap res) {
        res = Bitmap.createScaledBitmap(res, segmentWIDTH, segmentHEIGHT, false);
        for (int i = 0; i < segmentator.getHeight(); ++i) {
            for (int j = 0; j < segmentator.getWidth(); ++j) {
                if (mask[i][j] == 0) {
                    res.setPixel(j, i, Color.RED);
                }
            }
        }
        return res;
    }

    public boolean isDone() {
        return this.isDone;
    }
}
