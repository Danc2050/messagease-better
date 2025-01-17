package nz.felle.messageasebetter;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

public final class InputMethodView extends View {
	//region Constructor Boilerplate
	public InputMethodView(final @Nullable Context context) {
		super(context);
	}

	public InputMethodView(final @Nullable Context context, final @Nullable AttributeSet attrs) {
		super(context, attrs);
	}

	public InputMethodView(final @Nullable Context context, final @Nullable AttributeSet attrs, final int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	public InputMethodView(final @Nullable Context context, final @Nullable AttributeSet attrs, final int defStyleAttr, final int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
	}
	//endregion

	private final KeyboardPaints paints = new KeyboardPaints(getResources(), getContext().getTheme());

	private float buttonWidth() {
		return this.getWidth() / 4.0f;
	}

	private float buttonHeight() {
		return this.getHeight() / 4.0f;
	}

	private InputConnection conn;

	public void updateInputConnection(@NonNull InputConnection conn) {
		this.conn = conn;
	}

	private boolean neverUseCodepoints = false;
	void updateQuirks(final String packageName) {
		neverUseCodepoints = false;

		Log.i("nz.felle.messageasebetter", "package name is " + packageName);
		if (packageName.equals("com.sonelli.juicessh")) {
			Log.w("nz.felle.messageasebetter", "applying quirks for JuiceSSH");
			neverUseCodepoints = true;
		}
	}

	public void performContextMenuAction(final int action) {
		conn.performContextMenuAction(action);
	}

	public void performActAction() {
		final int actAction = getActAction();
		if (actAction == EditorInfo.IME_ACTION_NONE) {
			insert('\n');
		} else {
			conn.performEditorAction(actAction);
		}
	}

	public void goToStartOfLine() {
		goToStartOfLine(false);
	}

	public void goToStartOfLine(final boolean keepOtherEnd) {
		final int CHUNK = 100;
		int position = selection.start;
		final int oldEnd = selection.end;
		while (true) {
			final CharSequence chs = conn.getTextBeforeCursor(CHUNK, 0);
			if (chs == null) {
				return;
			}
			final int len = chs.length();
			// start at 1 to allow going to start of previous line if we are at start of current line
			if (len >= 1) {
				for (int i = 1; i < len; ++i) {
					if (chs.charAt(len - i - 1) == '\n') {
						position -= i;
						conn.setSelection(position, keepOtherEnd ? oldEnd : position);
						return;
					}
				}
			}
			if (len < CHUNK) {
				conn.setSelection(0, keepOtherEnd ? oldEnd : 0);
				return;
			}
			position -= CHUNK;
			conn.setSelection(position, keepOtherEnd ? oldEnd : position);
		}
	}

	public void goToEndOfLine() {
		goToEndOfLine(false);
	}

	public void goToEndOfLine(final boolean keepOtherEnd) {
		final int CHUNK = 100;
		int position = selection.end;
		final int oldStart = selection.start;
		while (true) {
			final CharSequence chs = conn.getTextAfterCursor(CHUNK, 0);
			if (chs == null) {
				return;
			}
			final int len = chs.length();
			// start at 1 to allow going to end of next line if we are at end of current line
			if (len >= 1) {
				for (int i = 1; i < len; ++i) {
					if (chs.charAt(i) == '\n') {
						position += i;
						conn.setSelection(keepOtherEnd ? oldStart : position, position);
						return;
					}
				}
			}
			if (len < CHUNK) {
				position += len;
				conn.setSelection(keepOtherEnd ? oldStart : position, position);
				return;
			}
			position += CHUNK;
			conn.setSelection(keepOtherEnd ? oldStart : position, position);
		}
	}

	private boolean _numMode = false;

	boolean getNumMode() {
		return _numMode;
	}

	void setNumMode(final boolean value) {
		final boolean needsUpdate = value != _numMode;
		_numMode = value;
		if (needsUpdate) {
			this.postInvalidate();
		}
	}

	private CapsMode _caps = CapsMode.LOWER;

	CapsMode getCaps() {
		return _caps;
	}

	void setCaps(final CapsMode value) {
		final boolean needsUpdate = value != _caps;
		_caps = value;
		if (needsUpdate) {
			this.postInvalidate();
		}
	}

	private int _actAction = EditorInfo.IME_ACTION_UNSPECIFIED;

	int getActAction() {
		return _actAction;
	}

	void setActAction(final int value) {
		final boolean needsUpdate = value != _actAction;
		_actAction = value;
		if (needsUpdate) {
			updateActShower();
			this.postInvalidate();
		}
	}

	private @NonNull
	final TextShower _actShower = new TextShower("Enter");

	private void updateActShower() {
		String actString = "Enter";

		switch (_actAction) {
			case EditorInfo.IME_ACTION_DONE:
				actString = "Done";
				break;
			case EditorInfo.IME_ACTION_GO:
				actString = "Go";
				break;
			case EditorInfo.IME_ACTION_NEXT:
				actString = "Next";
				break;
			case EditorInfo.IME_ACTION_PREVIOUS:
				actString = "Prev";
				break;
			case EditorInfo.IME_ACTION_SEARCH:
				actString = "Search";
				break;
			case EditorInfo.IME_ACTION_SEND:
				actString = "Send";
				break;
			case EditorInfo.IME_ACTION_NONE:
				actString = "";
				break;
		}

		_actShower.text = actString;
	}

	@NonNull
	ActionShower getActShower() {
		return _actShower;
	}

	private @Nullable
	Line line = null;

	final @NonNull
	Selection selection = new Selection(0, 0);

	private final @NonNull
	Repeater repeater = new Repeater(this);

	private void complainAboutMissingAction(final int row, final int col, final @NonNull Motion motion) {
		Toast.makeText(getContext(), String.format("no action for row %s, col %s, motion %s", row + 1, col + 1, motion), Toast.LENGTH_SHORT).show();
	}

	void insert(final char ch) {
		setCaps(getCaps().next());

		conn.commitText(Character.toString(ch), 1);
	}

	void setSelection(int start, int end) {
		conn.setSelection(start, end);
	}

	private boolean deleteSelection() {
		if (!selection.isNonCursor()) {
			return false;
		}

		final int selectionLen = selection.length();
		conn.beginBatchEdit();
		conn.setSelection(selection.end, selection.end);
		conn.deleteSurroundingText(selectionLen, 0);
		conn.endBatchEdit();
		return true;
	}

	void delete(final int amount) {
		if (deleteSelection()) {
			return;
		}

		final Selection oldSelection = selection.clone();
		if (amount < 0) {
			if (neverUseCodepoints || !conn.deleteSurroundingTextInCodePoints(-amount, 0)) {
				// assume at this point that they probably don't have surrogates
				conn.deleteSurroundingText(-amount, 0);
			}
		} else {
			if (neverUseCodepoints || !conn.deleteSurroundingTextInCodePoints(0, amount)) {
				// ditto
				conn.deleteSurroundingText(0, amount);
			}
		}
	}

	void moveCursor(final int offset) {
		int newPosition;
		if (selection.isNonCursor()) {
			if (offset > 0) {
				newPosition = selection.end;
			} else {
				newPosition = selection.start;
			}
		} else {
			newPosition = selection.start + offset;
		}

		if (newPosition >= 0) {
			conn.setSelection(newPosition, newPosition);
		}
	}

	private boolean isDark() {
		return getResources().getConfiguration().isNightModeActive();
	}

	void takeVoiceInput() {
		final Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
		intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
		intent.putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY);
		intent.putExtra(RecognizerIntent.EXTRA_HIDE_PARTIAL_TRAILING_PUNCTUATION, false);
		intent.putExtra(RecognizerIntent.EXTRA_MASK_OFFENSIVE_WORDS, false);

		final SpeechRecognizer recognizer = SpeechRecognizer.createSpeechRecognizer(getContext());

		final InputMethodView view = this;

		recognizer.setRecognitionListener(new RecognitionListener() {
			@Override
			public void onResults(final Bundle resultsBundle) {
				final ArrayList<String> results = resultsBundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
				if (results.size() > 0) {
					String text = results.get(0);
					if (!text.isEmpty()) {
						switch (view.getCaps()) {
							case LOWER:
								break;
							case UPPER:
								text = Character.toUpperCase(text.charAt(0)) + text.substring(1);
								break;
							case UPPER_PERMANENT:
								text = text.toUpperCase();
								break;
						}
						view.setCaps(view.getCaps().next());
					}
					view.conn.commitText(text, 1);
				}
			}

			@Override
			public void onError(final int error) {
				Log.e("nz.felle.messageasebetter takeVoiceInput", "onError " + Integer.toString(error));
				Toast.makeText(getContext(), String.format("voice input returned error %s", error), Toast.LENGTH_SHORT).show();
			}

			@Override
			public void onEvent(final int event, final Bundle params) {}

			@Override
			public void onReadyForSpeech(final Bundle params) {}

			@Override
			public void onPartialResults(final Bundle params) {}

			@Override
			public void onBufferReceived(final byte[] _data) {}

			@Override
			public void onEndOfSpeech() {}

			@Override
			public void onBeginningOfSpeech() {}

			@Override
			public void onRmsChanged(final float _dbms) {}
		});

		recognizer.startListening(intent);
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		final @Nullable FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) getLayoutParams();
		if (params != null) {
			params.gravity = Gravity.BOTTOM;
			params.height = Math.round(getResources().getDisplayMetrics().density * 360.0f);
		}
		setLayoutParams(params);
	}

	private void drawButton(final @NonNull Canvas canvas, final float x, final float y, final float width, final float height) {
		final float strokeWidth = KeyboardPaints.STROKE_WIDTH;
		final float radius = KeyboardPaints.RADIUS;
		canvas.drawRoundRect(x + strokeWidth, y + strokeWidth, x + width - strokeWidth, y + height - strokeWidth, radius, radius, paints.buttonPaint);
	}

	private void drawButton(final @NonNull Canvas canvas, final float x, final float y, final float width, final float height, @Nullable ActionShower key) {
		drawButton(canvas, x, y, width, height);
		if (key != null) {
			key.show(canvas, x + (width / 2), y + (height / 2), paints.keyTextPaint, isDark());
		}
	}

	private void drawButton(final @NonNull Canvas canvas, final float x, final float y, final float width, final float height, @NonNull Map<Motion, Action> keys) {
		final @Nullable Action noneAction = keys.get(Motion.NONE);
		@Nullable ActionShower noneShower = null;
		if (noneAction != null) {
			noneShower = noneAction.show(this);
		}
		drawButton(canvas, x, y, width, height, noneShower);

		final float centerX = x + (width / 2);
		final float centerY = y + (height / 2);
		final float offsetX = (centerX - x) - 40;
		final float offsetY = (centerY - y) - 40;

		for (final Map.Entry<Motion, Action> entry : keys.entrySet()) {
			final @NonNull Motion motion = entry.getKey();
			final @NonNull Action action = entry.getValue();

			if (motion == Motion.NONE) {
				continue;
			}

			final @Nullable ActionShower actionShower = action.show(this);
			if (actionShower != null) {
				@Nullable Paint paint = paints.smallKeyTextPaint;
				if (action.secondary()) {
					paint = paints.secondaryKeyTextPaint;
				}

				final @NonNull PointF thisLetter = motion.offset(centerX, centerY, offsetX, offsetY);

				actionShower.show(canvas, thisLetter.x, thisLetter.y, paint, isDark());
			}
		}
	}

	boolean processLine() {
		final float motionThresholdX = this.buttonWidth() / 4;
		final float motionThresholdY = this.buttonHeight() / 4;
		final @NonNull Line line = Objects.requireNonNull(this.line);
		final @NonNull Motion motion = line.asMotion(motionThresholdX, motionThresholdY);

		final int actionRow = (int) Math.floor((line.startY - this.getY()) / this.buttonHeight());
		final float colFrac = (line.startX - this.getX()) / this.buttonWidth();
		int actionCol = (int) Math.floor(colFrac);

		if (actionRow == 3) {
			if (_numMode && colFrac < 1.5) {
				actionCol = 0;
			} else if (colFrac < 3) {
				actionCol = 1;
			} else {
				actionCol = 2;
			}
		}

		final @Nullable Action action = KeyboardActions.ACTIONS
			.get(actionRow)
			.get(actionCol)
			.get(motion);
		if (action != null) {
			action.execute(this);
			return true;
		} else {
			complainAboutMissingAction(actionRow, actionCol, motion);
			return false;
		}
	}

	@Override
	public boolean onTouchEvent(final @Nullable MotionEvent event) {
		if (event == null) {
			return super.onTouchEvent(null);
		}

		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
				line = new Line(event.getX(), event.getY(), event.getX(), event.getY());
				repeater.start();
				postInvalidate();
				return true;
			case MotionEvent.ACTION_MOVE:
				assert line != null;
				line.endX = event.getX();
				line.endY = event.getY();
				postInvalidate();
				return true;
			case MotionEvent.ACTION_UP:
				assert line != null;
				final boolean hasExecuted = repeater.hasExecuted();
				repeater.stop();
				if (!hasExecuted) {
					processLine();
				}
				line = null;
				postInvalidate();
				return true;
			default:
				return false;
		}
	}

	@Override
	protected void onDraw(final @Nullable Canvas canvas) {
		super.onDraw(canvas);

		if (canvas == null) {
			return;
		}

		canvas.drawColor(paints.backgroundColor);

		final float buttonHeight = buttonHeight();
		final float buttonWidth = buttonWidth();
		for (int row = 0; row < 3; ++row) {
			final float y = row * buttonHeight;
			for (int col = 0; col < 4; ++col) {
				final float x = col * buttonWidth;
				drawButton(canvas, x, y, buttonWidth, buttonHeight, KeyboardActions.ACTIONS.get(row).get(col));
			}
		}

		// render the fourth row manually
		final float lastRowY = 3 * buttonHeight;
		final float threeWidth = buttonWidth * 3;
		if (_numMode) {
			final float theseWidth = buttonWidth * 1.5f;
			drawButton(canvas, 0.0f, lastRowY, theseWidth, buttonHeight, Objects.requireNonNull(KeyboardActions.ACTIONS.get(3).get(0).get(Motion.NONE)).show(this));
			drawButton(canvas, theseWidth, lastRowY, theseWidth, buttonHeight);
		} else {
			drawButton(canvas, 0.0f, lastRowY, threeWidth, buttonHeight);
		}

		drawButton(canvas, threeWidth, lastRowY, buttonWidth, buttonHeight, _actShower);

		final @Nullable Line line = this.line;
		if (line != null && line.length() > 1.0f) {
			canvas.drawLine(line.startX, line.startY, line.endX, line.endY, paints.linePaint);
		}
	}
}
