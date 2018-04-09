package com.example.matt2929.strokeappdec2017.Activity;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import com.example.matt2929.strokeappdec2017.ListenersAndTriggers.EndRepTrigger;
import com.example.matt2929.strokeappdec2017.ListenersAndTriggers.OutputWorkoutData;
import com.example.matt2929.strokeappdec2017.ListenersAndTriggers.OutputWorkoutStrings;
import com.example.matt2929.strokeappdec2017.ListenersAndTriggers.SpeechCompleteListener;
import com.example.matt2929.strokeappdec2017.ListenersAndTriggers.SpeechInitListener;
import com.example.matt2929.strokeappdec2017.ListenersAndTriggers.SpeechTrigger;
import com.example.matt2929.strokeappdec2017.SaveAndLoadData.SaveActivitiesDoneToday;
import com.example.matt2929.strokeappdec2017.SaveAndLoadData.SaveHistoricalReps;
import com.example.matt2929.strokeappdec2017.SaveAndLoadData.SaveTouchAndSensor;
import com.example.matt2929.strokeappdec2017.SaveAndLoadData.SaveWorkoutJSON;
import com.example.matt2929.strokeappdec2017.Utilities.SFXPlayer;
import com.example.matt2929.strokeappdec2017.Utilities.Text2Speech;
import com.example.matt2929.strokeappdec2017.Values.WorkoutData;
import com.example.matt2929.strokeappdec2017.Workouts.SensorWorkoutAbstract;
import com.example.matt2929.strokeappdec2017.Workouts.WO_PickUpHorizontal;
import com.example.matt2929.strokeappdec2017.Workouts.WO_PickUpVertical;
import com.example.matt2929.strokeappdec2017.Workouts.WO_Pour;
import com.example.matt2929.strokeappdec2017.Workouts.WO_Sip;
import com.example.matt2929.strokeappdec2017.Workouts.WO_Twist;
import com.example.matt2929.strokeappdec2017.Workouts.WO_Walk;
import com.example.matt2929.strokeappdec2017.Workouts.WorkoutDescription;
import com.example.matt2929.strokeappdec2017.WorkoutsView.WV_JustText;
import com.example.matt2929.strokeappdec2017.WorkoutsView.WV_Pour;
import com.example.matt2929.strokeappdec2017.WorkoutsView.WorkoutViewAbstract;

import java.util.ArrayList;

public class SensorWorkoutRunner extends AppCompatActivity implements SensorEventListener {

	private final int CHECK_CODE = 0x1;
	//~~~~~~~~~~~~~~~~~~~~~~~
	Long TimeStartWorkout = System.currentTimeMillis();
	Long TimeOfRep = System.currentTimeMillis();
	//Workout Attributes~~~
	String _WorkoutHand; //Which Hand
	String _WorkoutName; //Name of Current Wokrout
	Integer _WorkoutReps;//Number of Repetitions
	private SensorManager _mSensorManager;
	private Sensor _mSensor;
	//Refrence ID for TTS~~~~
	private Text2Speech _Text2Speech;
	private SensorWorkoutAbstract _CurrentWorkout;
	private WorkoutViewAbstract _CurrentWorkoutView;
	private WorkoutDescription _WorkoutDescription = null;
	private SFXPlayer _SFXPlayer;
	private SaveHistoricalReps _SaveHistoricalReps;
	private SaveTouchAndSensor _SaveTouchAndSensor;
	private SaveWorkoutJSON _SaveWorkoutJSON;
	private Boolean _WorkoutInProgress = false;
	private ArrayList<Long> saveDurations = new ArrayList<>();
	private SaveActivitiesDoneToday _SaveActivitiesDoneToday;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Intent intent = getIntent();

		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		_WorkoutHand = intent.getStringExtra("Hand");
		_WorkoutName = intent.getStringExtra("Workout");
		_WorkoutReps = intent.getIntExtra("Reps", 10);
		_SaveHistoricalReps = new SaveHistoricalReps(getApplicationContext(), WorkoutData.UserName);
		_SaveTouchAndSensor = new SaveTouchAndSensor(getApplicationContext(), _WorkoutName, "Time,X,Y,Z");
		_SaveWorkoutJSON = new SaveWorkoutJSON(getApplicationContext());
		_SFXPlayer = new SFXPlayer(getApplicationContext());
		SetupWorkout(_WorkoutName, _WorkoutReps);
		_SaveActivitiesDoneToday = new SaveActivitiesDoneToday(getApplicationContext());
		checkTTS();
	}

	@Override
	protected void onPause() {
		super.onPause();
		_mSensorManager.unregisterListener(this);
		if (_Text2Speech != null) {
			_Text2Speech.destroy();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		_mSensorManager.registerListener(this, _mSensor, SensorManager.SENSOR_DELAY_GAME);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		_mSensorManager.unregisterListener(this);
		if (_Text2Speech != null) {
			_Text2Speech.destroy();
		}
	}

	private void checkTTS() {
		Intent check = new Intent();
		check.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
		startActivityForResult(check, CHECK_CODE);
	}

	//use this to know when tts is done speaking
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == CHECK_CODE) {
			if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
				_Text2Speech = new Text2Speech(this);
				_Text2Speech.addSpeechCompleteListener(new SpeechCompleteListener() {
					@Override
					public void Spoke(String s) {
						Log.e("TTS: ", s);
						if (s.equals(WorkoutData.TTS_WORKOUT_DESCRIPTION)) {
							_Text2Speech.silence(2000);
							_Text2Speech.speak("Start When I Say, Begin", WorkoutData.TTS_WORKOUT_READY);
						} else if (s.equals(WorkoutData.TTS_WORKOUT_READY)) {
							_Text2Speech.silence(2000);
							_Text2Speech.speak("Begin", WorkoutData.TTS_WORKOUT_BEGIN);
						} else if (s.equals(WorkoutData.TTS_WORKOUT_BEGIN)) {
							TimeStartWorkout = System.currentTimeMillis();
							TimeOfRep = System.currentTimeMillis();
							_CurrentWorkout.StartWorkout();
							_WorkoutInProgress = true;
						} else if (s.equals(WorkoutData.TTS_WORKOUT_COMPLETE)) {
							endWorkoutSequence();
						} else if (s.equals(WorkoutData.TTS_WORKOUT_AUDIO_FEEDBACK)) {

						} else if (s.equals(WorkoutData.TEST)) {

						}
					}
				});
				_Text2Speech.addInitListener(new SpeechInitListener() {
					@Override
					public void onInit() {
						String description = "";
						for (int i = 0; i < WorkoutData.WORKOUT_DESCRIPTIONS.length; i++) {
							if (WorkoutData.WORKOUT_DESCRIPTIONS[i].getName().equals(_WorkoutName)) {
								description = WorkoutData.WORKOUT_DESCRIPTIONS[i].getDescription();
							}
						}
						_Text2Speech.speak(description, WorkoutData.TTS_WORKOUT_DESCRIPTION);
					}
				});


			} else {
				Intent install = new Intent();
				install.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
				startActivity(install);
			}
		}
	}

	/**
	 * Set up Linear Sensor i.e. gravity factored out
	 */
	public void setupSensorsLinear() {
		if (_mSensorManager != null) {
			_mSensorManager.unregisterListener(this);
		}
		_mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		if (_mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) != null) {
			_mSensor = _mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

		} else {
			Toast.makeText(getApplicationContext(), "No sensor found", Toast.LENGTH_SHORT).show();
		}
		_mSensorManager.registerListener(this, _mSensor, SensorManager.SENSOR_DELAY_GAME);

	}

	/**
	 *
	 */
	public void setupGravitySensor() {
		if (_mSensorManager != null) {
			_mSensorManager.unregisterListener(this);
		}
		_mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		if (_mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY) != null) {
			_mSensor = _mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);

		} else {
			Toast.makeText(getApplicationContext(), "No sensor found", Toast.LENGTH_SHORT).show();
		}
		_mSensorManager.registerListener(this, _mSensor, SensorManager.SENSOR_DELAY_GAME);

	}
//There is an update from the Sensor

	@Override
	public void onSensorChanged(SensorEvent sensorEvent) {
		if (_CurrentWorkout != null) {
			_CurrentWorkout.SensorDataIn(sensorEvent.values);
			_CurrentWorkoutView.invalidate();

		}

		if (_WorkoutInProgress) {
			float[] data = new float[sensorEvent.values.length + 1];
			data[0] = Math.abs(TimeStartWorkout - System.currentTimeMillis());
			for (int i = 0; i < sensorEvent.values.length; i++) {
				data[i + 1] = sensorEvent.values[i];
			}
			TimeStartWorkout = System.currentTimeMillis();
			_SaveTouchAndSensor.addData(data);
		}

		if (_CurrentWorkout.isWorkoutComplete() && _WorkoutInProgress) {
			_Text2Speech.speak("Activity Complete", WorkoutData.TTS_WORKOUT_COMPLETE);
			_WorkoutInProgress = false;
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int i) {

	}

	public void SetupWorkout(String WorkoutName, int reps) {
		SpeechTrigger speechTrigger = new SpeechTrigger() {
			@Override
			public void speak(String s) {
				_Text2Speech.speak(s, WorkoutData.TTS_WORKOUT_AUDIO_FEEDBACK);
			}
		};

		EndRepTrigger endRepTrigger = new EndRepTrigger() {
			@Override
			public void endRep() {
				saveDurations.add(System.currentTimeMillis() - TimeOfRep);
				TimeOfRep = System.currentTimeMillis();
			}
		};

		OutputWorkoutData outputWorkoutData = new OutputWorkoutData() {
			@Override
			public void getData(float[] f) {
				_CurrentWorkoutView.dataIn(f);
			}
		};
		OutputWorkoutStrings outputWorkoutStrings = new OutputWorkoutStrings() {
			@Override
			public void getStrings(String[] s) {
				_CurrentWorkoutView.stringIn(s);
			}
		};
		for (int i = 0; i < WorkoutData.WORKOUT_DESCRIPTIONS.length; i++) {
			if (WorkoutName.equals(WorkoutData.WORKOUT_DESCRIPTIONS[i].getName())) {
				_WorkoutDescription = WorkoutData.WORKOUT_DESCRIPTIONS[i];
				break;
			}
		}
		reps = 3;
		if (_WorkoutDescription.getName().equals("Horizontal Bowl")) {
			setupSensorsLinear();
			_CurrentWorkout = new WO_PickUpHorizontal(WorkoutName, reps, speechTrigger, endRepTrigger, _SFXPlayer, outputWorkoutData, outputWorkoutStrings);
			_CurrentWorkoutView = new WV_JustText(getApplicationContext());
		} else if (_WorkoutDescription.getName().equals("Vertical Bowl")) {
			setupSensorsLinear();
			_CurrentWorkout = new WO_PickUpVertical(WorkoutName, reps, speechTrigger, endRepTrigger, _SFXPlayer, outputWorkoutData, outputWorkoutStrings);
			_CurrentWorkoutView = new WV_JustText(getApplicationContext());
		} else if (_WorkoutDescription.getName().equals("Horizontal Mug")) {
			setupSensorsLinear();
			_CurrentWorkout = new WO_PickUpHorizontal(WorkoutName, reps, speechTrigger, endRepTrigger, _SFXPlayer, outputWorkoutData, outputWorkoutStrings);
			_CurrentWorkoutView = new WV_JustText(getApplicationContext());
		} else if (_WorkoutDescription.getName().equals("Vertical Mug")) {
			setupSensorsLinear();
			_CurrentWorkout = new WO_PickUpVertical(WorkoutName, reps, speechTrigger, endRepTrigger, _SFXPlayer, outputWorkoutData, outputWorkoutStrings);
			_CurrentWorkoutView = new WV_JustText(getApplicationContext());
		} else if (_WorkoutDescription.getName().equals("Walk with mug")) {
			setupSensorsLinear();
			_CurrentWorkout = new WO_Walk(WorkoutName, reps, speechTrigger, endRepTrigger, _SFXPlayer, outputWorkoutData, outputWorkoutStrings);
			_CurrentWorkoutView = new WV_JustText(getApplicationContext());
		} else if (_WorkoutDescription.getName().equals("Quick Twist Mug")) {
			setupSensorsLinear();
			_CurrentWorkout = new WO_Twist(WorkoutName, reps, speechTrigger, endRepTrigger, _SFXPlayer, outputWorkoutData, outputWorkoutStrings);
			_CurrentWorkoutView = new WV_JustText(getApplicationContext());
		} else if (_WorkoutDescription.getName().equals("Slow Pour")) {
			setupGravitySensor();
			_CurrentWorkout = new WO_Pour(WorkoutName, reps, speechTrigger, endRepTrigger, _SFXPlayer, outputWorkoutData, outputWorkoutStrings);
			_CurrentWorkoutView = new WV_Pour(getApplicationContext());
			_CurrentWorkoutView.invalidate();
		} else if (_WorkoutDescription.getName().equals("Sip From The Mug")) {
			setupGravitySensor();
			_CurrentWorkout = new WO_Sip(WorkoutName, reps, speechTrigger, endRepTrigger, _SFXPlayer, outputWorkoutData, outputWorkoutStrings);
			_CurrentWorkoutView = new WV_Pour(getApplicationContext());
		}
		setContentView(_CurrentWorkoutView);
	}

	public void endWorkoutSequence() {
		_SFXPlayer.killAll();
		_SaveHistoricalReps.updateWorkout(_CurrentWorkout.getName(), _WorkoutReps);
		Float duration = averageTime(saveDurations) / (float) (1000);
		_SaveTouchAndSensor.saveAllData(duration, _CurrentWorkout.getScore().getScore(), _CurrentWorkout.getReps(), _WorkoutHand);
		_SaveWorkoutJSON.addNewWorkout(_CurrentWorkout.getName(), _WorkoutHand, duration, _CurrentWorkout.getScore().getScore(), _CurrentWorkout.getReps());
		_SaveActivitiesDoneToday.updateWorkout(_WorkoutName);
		Intent intent = getIntent();
		intent.setClass(getApplicationContext(), LoadingScreenActivity.class);
		startActivity(intent);

	}

	public float averageTime(ArrayList<Long> longs) {
		float sum = 0L;
		for (int i = 0; i < longs.size(); i++) {
			sum += longs.get(i);
		}
		float value = ((sum / (Long.valueOf(longs.size()))));
		return value;
	}
}
