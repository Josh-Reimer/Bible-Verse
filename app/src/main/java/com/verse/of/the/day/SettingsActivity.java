package com.verse.of.the.day;

import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Switch;
import androidx.appcompat.app.ActionBar;
import android.view.MenuItem;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.CompoundButton;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.NavUtils;

public class SettingsActivity extends AppCompatActivity{

Switch darkThemeSwitch;

boolean darkThemeOn = false;
   

public void saveDarkThemePref(SharedPreferences.Editor spE,boolean onoff){
	spE.putBoolean("theme",onoff).commit();
	Log.i("verse-settings","dark theme pref saved");
}

public Tools tools = new Tools();


@Override
protected void onCreate(Bundle savedInstanceState) {
	
super.onCreate(savedInstanceState);	
	setContentView(R.layout.settings_activity);
	SharedPreferences sp = getSharedPreferences("settings",MODE_PRIVATE);   
// Creating an Editor object to edit(write to the file) 
SharedPreferences.Editor spEditor = sp.edit();
	
	

	darkThemeSwitch = findViewById(R.id.darkThemeOn);
	if(tools.isNightMode(this)){
		darkThemeSwitch.setChecked(true);

	} else if(!tools.isNightMode(this)){
		darkThemeSwitch.setChecked(false);

	}
	darkThemeSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		
       if(darkThemeSwitch.isChecked()){
				darkThemeOn = true;
		   AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
			} else {
		   AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
	   }
saveDarkThemePref(spEditor,darkThemeOn);
    }
});

ActionBar ab = this.getSupportActionBar();
if(ab != null){
	ab.setDisplayHomeAsUpEnabled(true);
}

} 



@Override
public boolean onOptionsItemSelected(MenuItem item){
	int id = item.getItemId();
	if(id == android.R.id.home){
		NavUtils.navigateUpFromSameTask(this);
	}
	return super.onOptionsItemSelected(item);
}

}