package com.example.followthesun;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Activity;
import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.PictureCallback;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.example.followthesun.util.SystemUiHider;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 *
 * @see SystemUiHider
 */
public class CaptureActivity extends Activity implements SensorEventListener {
	private static String TAG = "FollowTheSunActivity";
	private Camera mCamera;
	private CameraPreview mCameraPreview;

	private SensorManager mSensorManager;
	private Sensor mOrientation;
	private float mRoll, mPitch, mYaw;
	private TextView mAnglesText, mNumCapturedText;
	private boolean mIsSaving, mIsPreviewing;
	private float mLastCaptureYaw;
	private int mNumCaptured;
	private File mAppStorageDir, mCurrentStorageDir;
	private File mOrientationsFile;
	private BufferedWriter mOrientationWriter;
	private String mTimestamp;

	private static float ROLL_THRESH = 5;
	private static float PITCH_THRESH = 5;
	private static float NEW_IMAGE_YAW_THRESH = 3;
	private static float IDEAL_ROLL = 0;
	private static float IDEAL_PITCH = -90;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_capture);

		mCamera = getCameraInstance();
		mCameraPreview = new CameraPreview(this, mCamera);
		FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
		preview.addView(mCameraPreview);

		final Button captureButton = (Button) findViewById(R.id.button_capture);
		captureButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mIsSaving) {
					mIsSaving = false;
					try {
						mOrientationWriter.flush();
						mOrientationWriter.close();
					} catch(IOException e) {
						Log.e(TAG, "Could not close orientation writer");
					}
					captureButton.setText("Capture");
				} else {
					createCaptureFiles();
					mNumCaptured = 0;
					mIsSaving = true;
					captureButton.setText("Stop");
				}
			}
		});

		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		mOrientation = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);

		mIsPreviewing = true;
		mRoll = 0;
		mPitch = 0;
		mYaw = 0;
		mNumCaptured = 0;
		mAnglesText = (TextView)findViewById(R.id.angles_text);
		mNumCapturedText = (TextView)findViewById(R.id.num_captured_text);
		mLastCaptureYaw = 0;
		mTimestamp = "";

		// We can run this once because we're fixing the phone in portrait
		determineDisplayOrientation();

		mAppStorageDir = new File(
				Environment
				.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
				"FollowTheSun");
		if (!mAppStorageDir.exists()) {
			if (!mAppStorageDir.mkdirs()) {
				Log.e("FollowTheSun", "failed to create directory");
			}
		}
	}

	/**
	 * Helper method to access the camera returns null if it cannot get the
	 * camera or does not exist
	 * 
	 * @return
	 */
	private Camera getCameraInstance() {
		Camera camera = null;
		try {
			camera = Camera.open();
		} catch (Exception e) {
			// cannot get camera or does not exist
		}
		return camera;
	}

	PictureCallback mPicture = new PictureCallback() {
		@Override
		public void onPictureTaken(byte[] data, Camera camera) {
			File pictureFile = getOutputMediaFile();
			if (pictureFile == null) {
				return;
			}
			Log.d(TAG, "Saving to " + pictureFile);
			try {
				FileOutputStream fos = new FileOutputStream(pictureFile);
				fos.write(data);
				fos.close();
				mCameraPreview.start();
				mIsPreviewing = true;
			} catch (FileNotFoundException e) {

			} catch (IOException e) {
			}
		}
	};

	private File getOutputMediaFile() {
		// Create a media file name
		File mediaFile;
		mediaFile = new File(mCurrentStorageDir.getPath() + File.separator
				+ "IMG_" + mTimestamp + ".jpg");

		return mediaFile;
	}

	private void createCaptureFiles() {
		mCurrentStorageDir = new File(mAppStorageDir, mTimestamp);
		if (!mCurrentStorageDir.exists()) {
			if (!mCurrentStorageDir.mkdirs()) {
				Log.e(TAG, "failed to create directory");
			}
		}

		mOrientationsFile = new File(mCurrentStorageDir, "orientation.csv");
		if (!mOrientationsFile.exists()) {
			try {
				mOrientationsFile.createNewFile();
				mOrientationWriter = new BufferedWriter(new FileWriter(mOrientationsFile));
				mOrientationWriter.append("timestamp,yaw,pitch,roll\n");
			} catch(IOException ioe) {
				Log.e(TAG, "Could not create " + mOrientationsFile);
			}
		} else {
			Log.e(TAG, "mOrientations file, " + mOrientationsFile + ", already exists!");
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		mIsSaving = false;
		Log.e(TAG, "Accuracy changed to " + accuracy);
	}

	@Override
	protected void onResume() {
		super.onResume();
		mSensorManager.registerListener(this, mOrientation, SensorManager.SENSOR_DELAY_NORMAL);
		try {
			mCamera.reconnect();
		} catch (IOException e) {
			Log.e(TAG, "Could not reconnect to camera");
		}
		mCameraPreview.start();
	}

	@Override
	protected void onPause() {
		super.onPause();
		mSensorManager.unregisterListener(this);
		mIsSaving = false;
		mCameraPreview.stop();
		mCamera.release();
		finish(); // Just die
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		mYaw = event.values[0];
		mPitch = event.values[1];
		mRoll = event.values[2];

		mAnglesText.setText("(" + String.format("%.2f", mYaw) + ", " + 
				String.format("%.2f", mPitch) + ", " + 
				String.format("%.2f", mRoll) + ")");

		if (canSave()) {
			try {
				Log.d(TAG, "Starting to take picture");
				mIsPreviewing = false;
				mTimestamp = new SimpleDateFormat("yyyyMMdd_HHmmss")
				.format(new Date());
				mCamera.takePicture(null, null, mPicture);
				saveOrientations();
				Log.d(TAG, "Finished taking picture");
			} catch (Exception e) {
				Log.e(TAG, e.getMessage());
				mIsPreviewing = true;
			}
			mLastCaptureYaw = mYaw;
			mNumCaptured += 1;
			mNumCapturedText.setText("Captured: " + mNumCaptured);
		}
	}

	private boolean canSave() {
		return (mIsSaving && 
				mIsPreviewing &&
				Math.abs(angleDiff(mPitch, IDEAL_PITCH)) < PITCH_THRESH &&
				Math.abs(angleDiff(mRoll, IDEAL_ROLL)) < ROLL_THRESH &&
				Math.abs(angleDiff(mYaw, mLastCaptureYaw)) >= NEW_IMAGE_YAW_THRESH);
	}

	private static float angleDiff(float a, float b) {
		return (a - b) % 360;
	}

	private void saveOrientations() throws IOException {
		mOrientationWriter.append(mTimestamp);
		mOrientationWriter.append(",");
		mOrientationWriter.append(Float.toString(mYaw));
		mOrientationWriter.append(",");
		mOrientationWriter.append(Float.toString(mPitch));
		mOrientationWriter.append(",");
		mOrientationWriter.append(Float.toString(mRoll));
		mOrientationWriter.append("\n");
	}

	private void determineDisplayOrientation() {
		CameraInfo cameraInfo = new CameraInfo();
		Camera.getCameraInfo(0, cameraInfo);

		int rotation = getWindowManager().getDefaultDisplay().getRotation();
		int degrees  = 0;

		switch (rotation) {
		case Surface.ROTATION_0:
			degrees = 0;
			break;

		case Surface.ROTATION_90:
			degrees = 90;
			break;

		case Surface.ROTATION_180:
			degrees = 180;
			break;

		case Surface.ROTATION_270:
			degrees = 270;
			break;
		}

		int displayOrientation;

		if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
			displayOrientation = (cameraInfo.orientation + degrees) % 360;
			displayOrientation = (360 - displayOrientation) % 360;
		} else {
			displayOrientation = (cameraInfo.orientation - degrees + 360) % 360;
		}
		mCamera.setDisplayOrientation(displayOrientation);
	}
}
