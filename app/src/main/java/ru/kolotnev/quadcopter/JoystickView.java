package ru.kolotnev.quadcopter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * Copied from http://code.google.com/p/mobile-anarchy-widgets/
 */
public class JoystickView extends View {
	public static final int INVALID_POINTER_ID = -1;
	// Max range of movement in user coordinate system
	public static final int CONSTRAIN_BOX = 0;
	public static final int CONSTRAIN_CIRCLE = 1;
	public static final int COORDINATE_CARTESIAN = 0;  // Regular cartesian coordinates
	public static final int COORDINATE_DIFFERENTIAL = 1;  // Uses polar rotation of 45 degrees to calc differential drive parameters
	// =========================================
	// Private Members
	// =========================================
	private static final boolean D = false;
	private static final String TAG = "JoystickView";
	private Paint dbgPaint1;
	private Paint dbgPaint2;
	private Paint bgPaint;
	private Paint handlePaint;
	private int innerPadding;
	private int bgRadius;
	private int handleRadius;
	private int movementRadius;
	private JoystickMovedListener moveListener;
	private JoystickClickedListener clickListener;
	//# of pixels movement required between reporting to the listener
	private float moveResolution;
	private boolean yAxisInverted;
	private boolean autoReturnToCenterByX, autoReturnToCenterByY;
	private int movementConstraint = CONSTRAIN_CIRCLE;
	private float movementRange;
	private int userCoordinateSystem;

	// Records touch pressure for click handling
	private float touchPressure;
	private boolean clicked;
	private float clickThreshold;

	// Last touch point in view coordinates
	private int pointerId = INVALID_POINTER_ID;
	private float touchX, touchY;

	// Last reported position in view coordinates (allows different reporting sensitivities)
	private float reportX, reportY;

	// Center of the view in view coordinates
	private int centerX, centerY;

	// Zero coordinates for starting and returning values
	private int zeroX, zeroY;

	// Size of the view in view coordinates
	private int dimX;

	// Polar coordinates of the touch point from joystick center
	private double radial;
	private double angle;

	// User coordinates of last touch point
	private int userX, userY;

	// Offset co-ordinates (used when touch events are received from parent's coordinate origin)
	private int offsetX;
	private int offsetY;

	// =========================================
	// Constructors
	// =========================================
	private int relativeZeroX, relativeZeroY;

	public JoystickView(Context context) {
		super(context);
		initJoystickView();
	}

	public JoystickView(Context context, AttributeSet attrs) {
		super(context, attrs);
		initJoystickView();
	}

	// =========================================
	// Initialization
	// =========================================

	public JoystickView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initJoystickView();
	}

	// =========================================
	// Public Methods
	// =========================================

	private void initJoystickView() {
		setFocusable(true);

		dbgPaint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
		dbgPaint1.setColor(Color.RED);
		dbgPaint1.setStrokeWidth(1);
		dbgPaint1.setStyle(Paint.Style.STROKE);

		dbgPaint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
		dbgPaint2.setColor(Color.GREEN);
		dbgPaint2.setStrokeWidth(1);
		dbgPaint2.setStyle(Paint.Style.STROKE);

		bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		bgPaint.setColor(Color.GRAY);
		bgPaint.setStrokeWidth(1);
		bgPaint.setStyle(Paint.Style.STROKE);

		handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		handlePaint.setColor(Color.DKGRAY);
		handlePaint.setStrokeWidth(1);
		handlePaint.setStyle(Paint.Style.FILL_AND_STROKE);

		innerPadding = 10;

		setMovementRange(10);
		setMoveResolution(1.0f);
		setClickThreshold(0.4f);
		//setYAxisInverted(true);
		setUserCoordinateSystem(COORDINATE_CARTESIAN);
		setAutoReturnToCenter(true, true);
	}

	/**
	 * Set current touch position and move handle to this position.
	 *
	 * @param x
	 * 		position by X.
	 * @param y
	 * 		position by Y.
	 */
	public void setCurrentCoordinate(int x, int y) {
		touchX = movementRadius == .0 ? x : (x / movementRange * movementRadius);
		touchY = movementRadius == .0 ? y : (y / movementRange * movementRadius);
		if (!yAxisInverted)
			touchY *= -1;
		reportOnMoved();
		invalidate();
	}

	public void setZero(int x, int y) {
		zeroX = x;
		zeroY = yAxisInverted ? y : -y;

		relativeZeroX = (int) (zeroX / movementRange * movementRadius);
		relativeZeroY = (int) (zeroY / movementRange * movementRadius);
	}

	public boolean isAutoReturnToCenter() {
		return autoReturnToCenterByX && autoReturnToCenterByY;
	}

	public boolean isAutoReturnToCenterByX() { return autoReturnToCenterByX; }

	public boolean isAutoReturnToCenterByY() { return autoReturnToCenterByY; }

	public void setAutoReturnToCenter(boolean autoReturnToCenterByX, boolean autoReturnToCenterByY) {
		this.autoReturnToCenterByX = autoReturnToCenterByX;
		this.autoReturnToCenterByY = autoReturnToCenterByY;
	}

	public int getUserCoordinateSystem() {
		return userCoordinateSystem;
	}

	public void setUserCoordinateSystem(int userCoordinateSystem) {
		if (userCoordinateSystem < COORDINATE_CARTESIAN || movementConstraint > COORDINATE_DIFFERENTIAL)
			Log.e(TAG, "invalid value for userCoordinateSystem");
		else
			this.userCoordinateSystem = userCoordinateSystem;
	}

	public int getMovementConstraint() {
		return movementConstraint;
	}

	public void setMovementConstraint(int movementConstraint) {
		if (movementConstraint < CONSTRAIN_BOX || movementConstraint > CONSTRAIN_CIRCLE)
			Log.e(TAG, "invalid value for movementConstraint");
		else
			this.movementConstraint = movementConstraint;
	}

	public boolean isYAxisInverted() {
		return yAxisInverted;
	}

	public void setYAxisInverted(boolean yAxisInverted) {
		this.yAxisInverted = yAxisInverted;
	}

	public float getClickThreshold() {
		return clickThreshold;
	}

	/**
	 * Set the pressure sensitivity for registering a click
	 *
	 * @param clickThreshold
	 * 		threshold 0...1.0f inclusive. 0 will cause clicks to never be reported, 1.0 is a very hard click
	 */
	public void setClickThreshold(float clickThreshold) {
		if (clickThreshold < 0 || clickThreshold > 1.0f)
			Log.e(TAG, "clickThreshold must range from 0...1.0f inclusive");
		else
			this.clickThreshold = clickThreshold;
	}

	public float getMovementRange() {
		return movementRange;
	}

	public void setMovementRange(float movementRange) {
		this.movementRange = movementRange;
	}

	public float getMoveResolution() {
		return moveResolution;
	}

	public void setMoveResolution(float moveResolution) {
		this.moveResolution = moveResolution;
	}

	public void setOnJostickMovedListener(JoystickMovedListener listener) {
		this.moveListener = listener;
	}

	public void setOnJostickClickedListener(JoystickClickedListener listener) {
		this.clickListener = listener;
	}

	public void setTouchOffset(int x, int y) {
		offsetX = x;
		offsetY = y;
	}

	public int getPointerId() {
		return pointerId;
	}

	public void setPointerId(int id) {
		this.pointerId = id;
	}

	// =========================================
	// Drawing Functionality
	// =========================================

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		// Here we make sure that we have a perfect circle
		int measuredWidth = measure(widthMeasureSpec);
		int measuredHeight = measure(heightMeasureSpec);
		// Setting the measured values to resize the view to a certain width and height
		int d = Math.min(measuredWidth, measuredHeight);
		setMeasuredDimension(d, d);
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);

		int d = Math.min(getMeasuredWidth(), getMeasuredHeight());

		dimX = d;

		centerX = d / 2;
		centerY = d / 2;

		bgRadius = dimX / 2 - innerPadding;
		handleRadius = (int) (d * 0.25 * .5);
		movementRadius = Math.min(centerX, centerY) - handleRadius;

		if (movementRange != 0) {
			relativeZeroX = (int) (zeroX / movementRange * movementRadius);
			relativeZeroY = (int) (zeroY / movementRange * movementRadius);
		}

		reportOnMoved();
	}

	private int measure(int measureSpec) {
		// Decode the measurement specifications.
		int specMode = MeasureSpec.getMode(measureSpec);
		int specSize = MeasureSpec.getSize(measureSpec);

		int result;
		if (specMode == MeasureSpec.UNSPECIFIED) {
			// Return a default size of 200 if no bounds are specified.
			result = 300;
		} else {
			// As you want to fill the available space
			// always return the full available bounds.
			result = specSize;
		}
		return result;
	}

	@Override
	protected void onDraw(Canvas canvas) {
		canvas.save();
		// Draw the background
		canvas.drawCircle(centerX, centerY, bgRadius, bgPaint);

		// Draw the handle by center in view coordinates
		float handleX = touchX + centerX;
		float handleY = touchY + centerY;
		canvas.drawCircle(handleX, handleY, handleRadius, handlePaint);

		if (D) {
			canvas.drawRect(1, 1, getMeasuredWidth() - 1, getMeasuredHeight() - 1, dbgPaint1);

			canvas.drawCircle(handleX, handleY, 3, dbgPaint1);

			if (movementConstraint == CONSTRAIN_CIRCLE) {
				canvas.drawCircle(centerX, centerY, movementRadius, dbgPaint1);
			} else {
				canvas.drawRect(centerX - movementRadius, centerY - movementRadius, centerX + movementRadius, centerY + movementRadius, dbgPaint1);
			}

			// Origin to touch point
			canvas.drawLine(centerX, centerY, handleX, handleY, dbgPaint2);

			int baseY = touchY < 0 ? centerY + handleRadius : centerY - handleRadius;
			canvas.drawText(String.format("%s (%.0f, %.0f)", TAG, touchX, touchY), handleX - 20, baseY - 7, dbgPaint2);
			canvas.drawText("(" + String.format("%.0f, %.1f", radial, angle * 57.2957795) + (char) 0x00B0 + ")", handleX - 20, baseY + 15, dbgPaint2);
		}

		//Log.d(TAG, String.format("touch(%f,%f)", touchX, touchY));
		//Log.d(TAG, String.format("onDraw(%.1f,%.1f)\n\n", handleX, handleY));
		canvas.restore();
	}

	@Override
	public boolean onTouchEvent(@NonNull MotionEvent event) {
		final int action = event.getAction();
		switch (action & MotionEvent.ACTION_MASK) {
			case MotionEvent.ACTION_MOVE:
				return processMoveEvent(event);
			case MotionEvent.ACTION_CANCEL:
			case MotionEvent.ACTION_UP: {
				if (pointerId != INVALID_POINTER_ID) {
					//Log.d(TAG, "ACTION_UP");
					returnHandleToCenter();
					setPointerId(INVALID_POINTER_ID);
				}
				break;
			}
			case MotionEvent.ACTION_POINTER_UP: {
				if (pointerId != INVALID_POINTER_ID) {
					final int pointerIndex = (action & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
					final int pointerId = event.getPointerId(pointerIndex);
					if (pointerId == this.pointerId) {
						//Log.d(TAG, "ACTION_POINTER_UP: " + pointerId);
						returnHandleToCenter();
						setPointerId(INVALID_POINTER_ID);
						return true;
					}
				}
				break;
			}
			case MotionEvent.ACTION_DOWN: {
				if (pointerId == INVALID_POINTER_ID) {
					int x = (int) event.getX();
					if (x >= offsetX && x < offsetX + dimX) {
						setPointerId(event.getPointerId(0));
						//Log.d(TAG, "ACTION_DOWN: " + getPointerId());
						return true;
					}
				}
				break;
			}
			case MotionEvent.ACTION_POINTER_DOWN: {
				if (pointerId == INVALID_POINTER_ID) {
					final int pointerIndex = (action & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
					final int pointerId = event.getPointerId(pointerIndex);
					int x = (int) event.getX(pointerId);
					if (x >= offsetX && x < offsetX + dimX) {
						//Log.d(TAG, "ACTION_POINTER_DOWN: " + pointerId);
						setPointerId(pointerId);
						return true;
					}
				}
				break;
			}
		}
		return false;
	}

	private boolean processMoveEvent(MotionEvent event) {
		if (pointerId == INVALID_POINTER_ID) return false;

		final int pointerIndex = event.findPointerIndex(pointerId);

		// Translate touch position to center of view
		float x = event.getX(pointerIndex);
		touchX = x - centerX - offsetX;
		float y = event.getY(pointerIndex);
		touchY = y - centerY - offsetY;

		//Log.d(TAG, String.format("ACTION_MOVE: (%03.0f, %03.0f) => (%03.0f, %03.0f)", x, y, touchX, touchY));

		reportOnMoved();
		invalidate();

		touchPressure = event.getPressure(pointerIndex);
		reportOnPressure();

		return true;
	}

	private void reportOnMoved() {
		if (movementConstraint == CONSTRAIN_CIRCLE)
			constrainCircle();
		else
			constrainBox();

		calcUserCoordinates();

		if (moveListener != null) {
			boolean rx = Math.abs(touchX - reportX) >= moveResolution;
			boolean ry = Math.abs(touchY - reportY) >= moveResolution;
			if (rx || ry) {
				reportX = touchX;
				reportY = touchY;

				//Log.d(TAG, String.format("moveListener.OnMoved(%d,%d)", (int)userX, (int)userY));
				moveListener.OnMoved(userX, userY);
			}
		}
	}

	/**
	 * Constrain touch within a box.
	 */
	private void constrainBox() {
		touchX = Math.max(Math.min(touchX, movementRadius), -movementRadius);
		touchY = Math.max(Math.min(touchY, movementRadius), -movementRadius);
	}

	/**
	 * Constrain touch within a circle.
	 */
	private void constrainCircle() {
		float diffX = touchX;
		float diffY = touchY;
		double radial = Math.sqrt((diffX * diffX) + (diffY * diffY));
		if (radial > movementRadius && movementRadius > 0) {
			touchX = (int) ((diffX / radial) * movementRadius);
			touchY = (int) ((diffY / radial) * movementRadius);
		}
	}

	private void calcUserCoordinates() {
		// First convert to cartesian coordinates
		int cartX = (int) (touchX / movementRadius * movementRange);
		int cartY = (int) (touchY / movementRadius * movementRange);

		radial = Math.sqrt((cartX * cartX) + (cartY * cartY));
		angle = Math.atan2(cartY, cartX);

		// Invert Y axis if requested
		if (!yAxisInverted)
			cartY *= -1;

		if (userCoordinateSystem == COORDINATE_CARTESIAN) {
			userX = cartX;
			userY = cartY;
		} else if (userCoordinateSystem == COORDINATE_DIFFERENTIAL) {
			userX = cartY + cartX / 4;
			userY = cartY - cartX / 4;

			if (userX < -movementRange)
				userX = (int) -movementRange;
			if (userX > movementRange)
				userX = (int) movementRange;

			if (userY < -movementRange)
				userY = (int) -movementRange;
			if (userY > movementRange)
				userY = (int) movementRange;
		}

	}

	/**
	 * Simple pressure click.
	 */
	private void reportOnPressure() {
		//Log.d(TAG, String.format("touchPressure=%.2f", this.touchPressure));
		if (clickListener != null) {
			if (clicked && touchPressure < clickThreshold) {
				clickListener.OnReleased();
				this.clicked = false;
				//Log.d(TAG, "reset click");
				invalidate();
			} else if (!clicked && touchPressure >= clickThreshold) {
				clicked = true;
				clickListener.OnClicked();
				//Log.d(TAG, "click");
				invalidate();
				performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
			}
		}
	}

	private void returnHandleToCenter() {
		if (!autoReturnToCenterByX && !autoReturnToCenterByY) return;
		final int numberOfFrames = 5;
		final double intervalsX = autoReturnToCenterByX ? ((relativeZeroX - touchX) / numberOfFrames) : .0;
		final double intervalsY = autoReturnToCenterByY ? ((relativeZeroY - touchY) / numberOfFrames) : .0;

		for (int i = 0; i < numberOfFrames; ++i) {
			final int j = i;
			postDelayed(new Runnable() {
				@Override
				public void run() {
					touchX += intervalsX;
					touchY += intervalsY;

					reportOnMoved();
					invalidate();

					if (moveListener != null && j == numberOfFrames - 1) {
						moveListener.OnReturnedToCenter();
					}
				}
			}, i * 40);
		}

		if (moveListener != null) {
			moveListener.OnReleased();
		}
	}

	public interface JoystickClickedListener {
		void OnClicked();

		void OnReleased();
	}

	public interface JoystickMovedListener {
		void OnMoved(int pan, int tilt);

		void OnReleased();

		void OnReturnedToCenter();
	}
}
