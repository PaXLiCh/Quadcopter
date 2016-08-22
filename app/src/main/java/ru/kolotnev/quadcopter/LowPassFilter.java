package ru.kolotnev.quadcopter;

/**
 * Low-Pass filter for accelerometer data.
 */
public class LowPassFilter {
	// Constants for the low-pass filters
	private static float timeConstant = 0.18f;
	private static float alpha = 0.9f;
	private static float dt = 0;

	// Timestamps for the low-pass filters
	private static long timestamp = System.nanoTime();
	private static long timestampOld = System.nanoTime();

	// Gravity and linear accelerations components for the Wikipedia low-pass filter
	private static float[] gravity = new float[] { 0, 0, 0 };

	private static float[] linearAcceleration = new float[] { 0, 0, 0 };

	// Raw accelerometer data
	private static float[] input = new float[] { 0, 0, 0 };

	private static int count = 0;

	/**
	 * Add a sample.
	 *
	 * @param acceleration
	 * 		The acceleration data.
	 *
	 * @return Returns the output of the filter.
	 */
	public static float[] addSamples(float[] acceleration) {
		// Get a local copy of the sensor values
		System.arraycopy(acceleration, 0, input, 0, acceleration.length);

		timestamp = System.nanoTime();

		// Find the sample period (between updates).
		// Convert from nanoseconds to seconds
		dt = 1 / (count / ((timestamp - timestampOld) * .000000001f));

		++count;

		alpha = timeConstant / (timeConstant + dt);

		gravity[0] = alpha * gravity[0] + (1 - alpha) * input[0];
		gravity[1] = alpha * gravity[1] + (1 - alpha) * input[1];
		gravity[2] = alpha * gravity[2] + (1 - alpha) * input[2];

		linearAcceleration[0] = input[0] - gravity[0];
		linearAcceleration[1] = input[1] - gravity[1];
		linearAcceleration[2] = input[2] - gravity[2];

		return linearAcceleration;
	}

	public static float[] filter(float[] acceleration) {
		System.arraycopy(acceleration, 0, input, 0, acceleration.length);

		timestamp = System.nanoTime();
		// Find the sample period (between updates).
		// Convert from nanoseconds to seconds
		float deltaTime = (timestamp - timestampOld) * .000000001f;
		timestampOld = timestamp;

		float a = 2f * deltaTime;

		linearAcceleration[0] = linearAcceleration[0] + a * (input[0] - linearAcceleration[0]);
		linearAcceleration[1] = linearAcceleration[1] + a * (input[1] - linearAcceleration[1]);
		linearAcceleration[2] = linearAcceleration[2] + a * (input[2] - linearAcceleration[2]);

		return linearAcceleration;
	}

	public static void resetFilter() {
		timestampOld = timestamp = System.nanoTime();
		for (int i = 0; i < linearAcceleration.length; ++i) {
			linearAcceleration[i] = 0;
		}
	}
}
