# Install
- Add to `build.gradle`
  - ```java 
  	android {
    	aaptOptions {
        	noCompress "tflite"
        	noCompress "lite"
    	}
	}
  - ```java
	dependencies {
    	implementation 'org.tensorflow:tensorflow-lite:0.0.0-nightly'
	}


- `MainActivity`
  - Import:
  	```java 
	import AIUtils.*;
  - Initialization:
  	```java
  	initTensorFlowAndLoadModel() {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					aiUtils = new AIutils(getAssets());
				} catch (final Exception e) {
					throw new RuntimeException("Error initializing TensorFlow!", e);
				}
			}
		});
	}

  - Methods:
	```java
	// push data into classifier and segmentator
	void feed(List<Bitmap>);

	//run
	void run();

	// get list of chambers correspond to fed images
	int[] getChambersList();

	// get ef value
	int getEF();

	// get image that has maximum/minimum blood area
	Bitmap getMaxImage();
	Bitmap getMinImage();

