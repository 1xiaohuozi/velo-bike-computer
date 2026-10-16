package local.velo.bridge;
import android.content.*;import android.media.*;import android.os.*;
/** Respects system silent mode and notification stream volume. */
public final class PhoneWarnings {
 final Context context;ToneGenerator tone;boolean closed;
 public PhoneWarnings(Context c){context=c;}
 public synchronized String warn(){
  if(closed)return "stopped";
  String mode=context.getSharedPreferences("navigation",0).getString("wrong_way_alert","both");if("off".equals(mode))return "disabled";
  AudioManager audio=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);if(audio==null||audio.getRingerMode()==AudioManager.RINGER_MODE_SILENT)return "system_silent";
  boolean beep=false,vibrate=false;
  if("both".equals(mode)&&audio.getRingerMode()==AudioManager.RINGER_MODE_NORMAL&&audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION)>0)try{if(tone==null)tone=new ToneGenerator(AudioManager.STREAM_NOTIFICATION,85);beep=tone.startTone(ToneGenerator.TONE_PROP_BEEP2,450);}catch(Exception ignored){}
  try{Vibrator v=(Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE);if(v!=null&&v.hasVibrator()){v.vibrate(VibrationEffect.createWaveform(new long[]{0,250,130,250},-1),new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());vibrate=true;}}catch(Exception ignored){}
  return "sound="+beep+",vibration="+vibrate;
 }
 public synchronized void close(){closed=true;if(tone!=null){tone.release();tone=null;}}
}
