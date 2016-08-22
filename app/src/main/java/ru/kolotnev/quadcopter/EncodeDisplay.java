package ru.kolotnev.quadcopter;

import android.graphics.Bitmap;
import android.graphics.Canvas;

import java.io.IOException;
import java.nio.ByteBuffer;

public final class EncodeDisplay {
	private static final String URL_STREAMING_DEFAULT = "http://192.168.10.1:8080/?action=stream";
	private static EncodeDisplay instance;
	public Bitmap bmp0, bmp1, bmp2;
	public StreamingListener listener;
	private MjpegInputStream mIn;
	private String urlStreaming = null;
	private int width, height, length;
	private Canvas canvasTemp;
	private MjpegViewThread thread;

	public boolean isStreamingEnabled() {
		return isStreamingEnabled;
	}

	private EncodeDisplay() {
		// do nothing
	}

	private boolean isStreamingEnabled;
	private byte[] mPixel;
	private ByteBuffer buffer0;

	public static EncodeDisplay getInstance(StreamingListener listener) {
		if (instance == null) {
			synchronized (EncodeDisplay.class) {
				if (instance == null) {
					instance = new EncodeDisplay();
				}
			}
		}
		instance.listener = listener;
		return instance;
	}

	public void startPlayback() {
		urlStreaming = URL_STREAMING_DEFAULT;
		if (mIn != null) {
			try {
				mIn.close();
				mIn = null;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		if (mIn == null || urlStreaming != null) {
			isStreamingEnabled = true;
			thread = new MjpegViewThread();
			try {
				thread.start();
			} catch (IllegalThreadStateException e2) {
				e2.printStackTrace();
			}
		}
	}

	public void stopPlayback() {
		isStreamingEnabled = false;
		boolean retry = true;
		while (retry) {
			try {
				if (thread != null && thread.isAlive()) {
					thread.join(10);
				}
				retry = false;
				if (listener != null) {
					listener.streamingStopped();
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	public interface StreamingListener {
		void streamingStarted();

		void streamingStopped();

		void gotFrame(Bitmap bmp);

		void cantConnect();

		void exception();
	}

	public class MjpegViewThread extends Thread {
		public void run() {
			boolean flag1 = true;
			while (isStreamingEnabled) {
				try {
					if (mIn != null) {
						EncodeDisplay.this.bmp0 = mIn.readMjpegFrame();
						if (EncodeDisplay.this.bmp0 != null) {
							if (flag1) {
								EncodeDisplay.this.width = EncodeDisplay.this.bmp0.getWidth();
								EncodeDisplay.this.height = EncodeDisplay.this.bmp0.getHeight();
								EncodeDisplay.this.length = (EncodeDisplay.this.width * EncodeDisplay.this.height) * 2;
								if (EncodeDisplay.this.bmp0.getConfig().compareTo(Bitmap.Config.RGB_565) != 0) {
									EncodeDisplay.this.bmp1 = Bitmap.createBitmap(EncodeDisplay.this.width, EncodeDisplay.this.height, Bitmap.Config.RGB_565);
									canvasTemp = new Canvas(EncodeDisplay.this.bmp1);
								}
								EncodeDisplay.this.mPixel = new byte[EncodeDisplay.this.length];
								for (int j = 0; j < EncodeDisplay.this.length; ++j) {
									EncodeDisplay.this.mPixel[j] = (byte) 0;
								}
								EncodeDisplay.this.buffer0 = ByteBuffer.wrap(EncodeDisplay.this.mPixel);
								flag1 = false;
							}
							if (EncodeDisplay.this.bmp0.getConfig().compareTo(Bitmap.Config.RGB_565) != 0) {
								EncodeDisplay.this.bmp2 = EncodeDisplay.this.bmp0;
								canvasTemp.drawBitmap(EncodeDisplay.this.bmp0, 0.0f, 0.0f, null);
							} else {
								EncodeDisplay.this.bmp1 = EncodeDisplay.this.bmp0;
							}
							/*if(photoFlag) {
								photoFlag = false;
								new TakePicThread().start();
							}
							if(videoFlag) {
								EncodeDisplay.this.buffer0.clear();
								EncodeDisplay.this.bmp1.copyPixelsToBuffer(EncodeDisplay.this.buffer0);
								EncodeDisplay.EncodeBitmap(EncodeDisplay.this.buffer0.array());
							}*/
							if (listener != null)
								listener.gotFrame(bmp0);
						}
					} else {
						mIn = MjpegInputStream.read(urlStreaming);
						if (mIn != null) {
							if (listener != null)
								listener.streamingStarted();
						} else {
							isStreamingEnabled = false;
							if (listener != null)
								listener.cantConnect();
						}
					}
				} catch (Exception e) {
					if (mIn != null) {
						try {
							mIn.close();
							mIn = null;
						} catch (IOException e1) {
							e1.printStackTrace();
						}
					}
					e.printStackTrace();
					if (listener != null)
						listener.exception();
				}
			}
		}
	}
}
