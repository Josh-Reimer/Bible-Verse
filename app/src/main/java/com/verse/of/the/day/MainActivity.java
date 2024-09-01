package com.verse.of.the.day;

import android.graphics.drawable.Drawable;
import androidx.constraintlayout.widget.ConstraintLayout;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import android.content.Intent;
import android.view.MenuItem;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import android.widget.TextView;
import android.content.res.AssetManager;
import android.content.Context;
import android.util.Log;

import java.util.Objects;
import java.util.Scanner;
import android.content.SharedPreferences;

import android.os.Bundle;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener{
	public DrawerLayout drawerLayout;
	public ActionBarDrawerToggle actionBarDrawerToggle;
    Verse verse_displayed;

	FloatingActionButton menu_fab;
	FloatingActionButton bookmark_fab;
	FloatingActionButton verselookup_fab;
	FloatingActionButton newverse_fab;
	private TextView verseview;
	private ConstraintLayout mainLayoutView;
    private final Scanner mainScanner = new Scanner(System.in);
    private Context thisapp;
    private VerseOfTheDay vod;
    private final Tools tools = new Tools();
    private final Bible bible = new Bible();
    boolean fabs_visible;
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
		SharedPreferences shared_preferences = getSharedPreferences("settings",MODE_PRIVATE);
		boolean theme = shared_preferences.getBoolean("theme",false);
		//true is dark theme on
		//false is light theme on
		//second parameter is the default value if there is no theme value in shaded preferences

		if(theme){
			AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
		} else{
			AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
		}


        setContentView(R.layout.activity_main);
		//drawerLayout.closeDrawers();
		mainLayoutView = findViewById(R.id.mainLayoutView);
		    verseview = findViewById(R.id.verse);
		menu_fab = findViewById(R.id.menu_fab);	
		bookmark_fab = findViewById(R.id.bookmark_fab);
		verselookup_fab = findViewById(R.id.verselookup);
		newverse_fab = findViewById(R.id.newverse);
		
		verselookup_fab.setVisibility(View.GONE);
		newverse_fab.setVisibility(View.GONE);
		bookmark_fab.setVisibility(View.GONE);
		
		fabs_visible = false;
		
		menu_fab.setOnClickListener(View ->{
		if(!fabs_visible){
			
			newverse_fab.show();
			verselookup_fab.show();
			bookmark_fab.show();
			//menu_fab.setImageResource(R.drawable.something);
			fabs_visible = true;
		} else if(fabs_visible){
			verselookup_fab.setVisibility(android.view.View.GONE);
			newverse_fab.setVisibility(android.view.View.GONE);
			bookmark_fab.setVisibility(android.view.View.GONE);
			menu_fab.setImageResource(R.drawable.more_vert_36);
			fabs_visible = false;
		}	
		});
		
		newverse_fab.setOnClickListener(View ->{
			if(fabs_visible){
				verse_displayed = vod.getRandomRef(bible,tools, thisapp);
				verseview.setText(verse_displayed.full_text);
				verselookup_fab.setVisibility(android.view.View.GONE);
				newverse_fab.setVisibility(android.view.View.GONE);
				bookmark_fab.setVisibility(android.view.View.GONE);
				menu_fab.setImageResource(R.drawable.more_vert_36);
				fabs_visible = false;
			}
		});
		bookmark_fab.setOnClickListener(View ->{
		if(fabs_visible){
			bookmark_fab.setImageResource(R.drawable.bookmark_solid_48);
		}	
		});
		verselookup_fab.setOnClickListener(View ->{
			if(fabs_visible){
				goToVerseLookUpActivity(verse_displayed.reference);
				verselookup_fab.setVisibility(android.view.View.GONE);
				newverse_fab.setVisibility(android.view.View.GONE);
				bookmark_fab.setVisibility(android.view.View.GONE);
				menu_fab.setImageResource(R.drawable.more_vert_36);
				fabs_visible = false;
			}
		});
		
		mainLayoutView.setOnClickListener(View ->{
			verselookup_fab.setVisibility(android.view.View.GONE);
				newverse_fab.setVisibility(android.view.View.GONE);
				bookmark_fab.setVisibility(android.view.View.GONE);
				fabs_visible = false;
		});
		
         setNavigationViewListener();
         
         drawerLayout = findViewById(R.id.my_drawer_layout);
		actionBarDrawerToggle = new ActionBarDrawerToggle(this,drawerLayout,R.string.nav_open,R.string.nav_close);

        NavigationView navigationView = findViewById(R.id.nv);
		drawerLayout.addDrawerListener(actionBarDrawerToggle);
		navigationView.setItemIconTintList(null);
actionBarDrawerToggle.syncState();
		Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);



		thisapp = getApplicationContext();
		vod = new VerseOfTheDay(mainScanner,thisapp);
		//String verseofday = vod.getVerseFromFile(mainScanner,thisapp,tools);
		//String verseofday = vod.getRandomVerse(mainScanner,tools,thisapp,bible);
		verse_displayed = vod.getRandomRef(bible,tools,thisapp);
		verseview.setText(verse_displayed.full_text);
	}		//end of oncreate method
	
	@Override
	public void onDestroy(){
		super.onDestroy();
		mainScanner.close();
		Log.i("verse-main","onDestroy method was called!");
	}
	@Override
	public void onPause(){
		super.onPause();
		Log.i("verse-main","onPause method was called!");
	}
	@Override
	public void onResume(){
		super.onResume();
		Log.i("verse-main","onResume method was called!");
	drawerLayout.closeDrawers();
		SharedPreferences shared_preferences = getSharedPreferences("settings",MODE_PRIVATE);
		boolean theme = shared_preferences.getBoolean("theme",false);
		//true is dark theme on
		//false is light theme on	
		//second parameter is the defualt value if there is no theme value in shaded preferences
		
		if(theme){
			AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
		} else {
			AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
		}
		
		
	}
	
	
	@Override
	public boolean onOptionsItemSelected(@NonNull MenuItem item){
		if(actionBarDrawerToggle.onOptionsItemSelected(item)){
		return true;	
		}
		return super.onOptionsItemSelected(item);
	}	
		
		
		@Override
		public boolean onNavigationItemSelected(@NonNull MenuItem item){
            int itemId = item.getItemId();
            if (itemId == R.id.settings) {
                goToSettings();
            } else if (itemId == R.id.bookmarks) {
				Intent i = new Intent(this, bookmarks.class);
				startActivity(i);
            }
		  drawerLayout.closeDrawers();
		  return true;
		}
		
		
		
	
	private void setNavigationViewListener(){
	  NavigationView nv = findViewById(R.id.nv);
	  nv.setNavigationItemSelectedListener(this);
	}
	
	void goToSettings(){
	Intent i = new Intent(this,SettingsActivity.class);
	startActivity(i);	
		
	}
	void goToVerseLookUpActivity(String verse){
		Intent intent = new Intent(this,VerseLookUpActivity.class);
		intent.putExtra("verse_ref",verse);
		startActivity(intent);
	}
	}