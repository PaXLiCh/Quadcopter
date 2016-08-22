package ru.kolotnev.quadcopter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.annotation.Nullable;
import android.util.Base64;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;

public class MjpegInputStream extends DataInputStream {
	private static final int FRAME_MAX_LENGTH = 40100;
	private static final int REQUEST_TIMEOUT = 2000;
	private static final int SO_TIMEOUT = 1000;
	private static final String CONTENT_LENGTH = "Content-Length";
	private static final String USERNAME = "admin";
	private static final String PASSWORD = "admin123";
	private final byte[] EOF_MARKER;
	private final byte[] SOI_MARKER;
	public boolean isConnected = false;
	private int mContentLength;

	public MjpegInputStream(InputStream in) {
		super(new BufferedInputStream(in, 40100));
		this.SOI_MARKER = new byte[] { (byte) -1, (byte) -40 };
		this.EOF_MARKER = new byte[] { (byte) -1, (byte) -39 };
		this.mContentLength = -1;
	}

	@Nullable
	public static MjpegInputStream read(String url) {
		try {
			URL url0 = new URL(url);
			HttpURLConnection con = (HttpURLConnection) url0.openConnection();
			con.setConnectTimeout(REQUEST_TIMEOUT);
			con.setReadTimeout(SO_TIMEOUT);
			con.setRequestProperty("Authorization", "Basic " +
					Base64.encodeToString((USERNAME + ":" + PASSWORD).getBytes(), Base64.NO_WRAP));
			con.setUseCaches(false);
			con.connect();

			MjpegInputStream is = null;
			if (con.getResponseCode() == 200) {
				is = new MjpegInputStream(con.getInputStream());
				is.isConnected = true;
			}
			return is;
		} catch (IOException e2) {
			e2.printStackTrace();
			return null;
		}
	}

	private int getEndOfSequence(DataInputStream in, byte[] sequence) throws IOException {
		int seqIndex = 0;
		for (int i = 0; i < 40100; ++i) {
			if (((byte) in.readUnsignedByte()) == sequence[seqIndex]) {
				++seqIndex;
				if (seqIndex == sequence.length) {
					return i + 1;
				}
			} else {
				seqIndex = 0;
			}
		}
		return -1;
	}

	private int getStartOfSequence(DataInputStream in, byte[] sequence) throws IOException {
		int end = getEndOfSequence(in, sequence);
		return end < 0 ? -1 : end - sequence.length;
	}

	private int parseContentLength(byte[] headerBytes) throws IOException, NumberFormatException {
		ByteArrayInputStream headerIn = new ByteArrayInputStream(headerBytes);
		Properties props = new Properties();
		props.load(headerIn);
		return Integer.parseInt(props.getProperty(CONTENT_LENGTH));
	}

	public Bitmap readMjpegFrame() throws IOException {
		mark(FRAME_MAX_LENGTH);
		int headerLen = getStartOfSequence(this, SOI_MARKER);
		reset();
		byte[] header = new byte[headerLen];
		readFully(header);
		try {
			mContentLength = parseContentLength(header);
		} catch (NumberFormatException e) {
			mContentLength = getEndOfSequence(this, EOF_MARKER);
		}
		reset();
		byte[] bArr = new byte[mContentLength];
		skipBytes(headerLen);
		readFully(bArr);
		return BitmapFactory.decodeStream(new ByteArrayInputStream(bArr));
	}
}
