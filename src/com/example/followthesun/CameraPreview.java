package com.example.followthesun;

import java.io.IOException;

import android.content.Context;
import android.hardware.Camera;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;

public class CameraPreview extends SurfaceView implements Callback {
	private SurfaceHolder mSurfaceHolder;
	private Camera mCamera;

	public CameraPreview(Context context, Camera camera) {
		super(context);
		this.mCamera = camera;
		this.mSurfaceHolder = this.getHolder();
		this.mSurfaceHolder.addCallback(this);
	}

	@Override
	public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
		start();
	}

	@Override
	public void surfaceCreated(SurfaceHolder arg0) {
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder arg0) {}

	public void stop() {
		mCamera.stopPreview();
	}

	public void start() {
		try {
			mCamera.setPreviewDisplay(mSurfaceHolder);
			mCamera.startPreview();
		} catch (IOException e) {
			// left blank for now
		}	
	}
}
