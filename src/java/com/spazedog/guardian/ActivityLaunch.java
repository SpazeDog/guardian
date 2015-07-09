/*
 * This file is part of the Guardian Project: https://github.com/spazedog/guardian
 *  
 * Copyright (c) 2015 Daniel Bergløv
 *
 * Guardian is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * Guardian is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with Guardian. If not, see <http://www.gnu.org/licenses/>
 */

package com.spazedog.guardian;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.design.widget.NavigationView.OnNavigationItemSelectedListener;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.DrawerLayout.DrawerListener;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

import com.spazedog.guardian.utils.AbstractActivity;
import com.spazedog.guardian.utils.AbstractFragmentDialog;

public class ActivityLaunch extends AbstractActivity implements OnNavigationItemSelectedListener {
	
	protected DrawerLayout mDrawerView;
	protected NavigationView mNavigationView;
	protected Toolbar mToolbarView;
	protected Toolbar mToolbarTopView;
	protected ViewGroup mWrapperView;
	
	@SuppressLint("NewApi")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView( Common.resolveAttr(this, R.attr.layout_activityLaunchLayout) );
		
		mDrawerView = (DrawerLayout) findViewById(R.id.drawer);
		mNavigationView = (NavigationView) findViewById(R.id.navigation);
		mToolbarView = (Toolbar) findViewById(R.id.toolbar);
		mToolbarTopView = (Toolbar) findViewById(R.id.toolbarTop);
		mWrapperView = (ViewGroup) findViewById(R.id.wrapper);
		
		if (mToolbarTopView == null) {
			mToolbarTopView = mToolbarView;
		}
		
		setSupportActionBar(mToolbarView);
		
		if (mDrawerView != null) {
			if (android.os.Build.VERSION.SDK_INT < 21) {
				mDrawerView.setDrawerShadow(R.drawable.right_shadow, GravityCompat.START);
			}
			
			mDrawerView.setDrawerListener(new DrawerListener(){
				@Override
				public void onDrawerClosed(View arg0) {
					sendMessage("activity.drawer_opened", false, true);
				}

				@Override
				public void onDrawerOpened(View arg0) {
					sendMessage("activity.drawer_opened", true, true);
				}

				@Override
				public void onDrawerSlide(View drawerView, float slideOffset) {
					mWrapperView.setTranslationX((slideOffset * drawerView.getWidth()) / 2);
				}

				@Override
				public void onDrawerStateChanged(int arg0) {}
			});
		}
		
		mNavigationView.setNavigationItemSelectedListener(this);
	}
	
    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        
        Common.setTypeFace(getWindow().getDecorView(), Common.TYPEFACE.DefaultRegular(this));
        
		FragmentManager fragmentManager = getSupportFragmentManager();
		if (fragmentManager.findFragmentById(R.id.content) == null) {
			Menu navigationMenu = mNavigationView.getMenu();
			
			if (navigationMenu.size() > 0) {
				onNavigationItemSelected(navigationMenu.getItem(0));
			}
		}
    }
    
    @Override
    protected void onResume() {
    	super.onResume();
    	
    	if (mDrawerView != null) {
    		sendMessage("activity.drawer_opened", mDrawerView.isDrawerOpen(GravityCompat.START), true);
    	}
    }
    
    public Fragment getCurrentFragment() {
		return getSupportFragmentManager().findFragmentById(R.id.content);
    }
	
	public void loadFragment(String tag, Fragment fragment, Boolean backStack) {
		FragmentManager manager = getSupportFragmentManager();
		
		if (!backStack || manager.findFragmentByTag(tag) == null) {
			if (backStack && fragment instanceof AbstractFragmentDialog && Common.getDisplaySW(this) >= 600f) {
				Bundle bundle = fragment.getArguments();
				bundle.putBoolean("dialog", true);
				fragment.setArguments(bundle);
				
				((AbstractFragmentDialog) fragment).show(manager, tag);
				
			} else {
				FragmentTransaction transaction = manager.beginTransaction();
				transaction.replace(R.id.content, fragment, tag);
				
				if (backStack) {
					transaction.addToBackStack(null);
					
				} else if (manager.getBackStackEntryCount() > 0) {
					manager.popBackStack(manager.getBackStackEntryAt(0).getId(), FragmentManager.POP_BACK_STACK_INCLUSIVE);
				}
				
				transaction.commit();
			}
			
			if (mDrawerView != null) {
				mDrawerView.closeDrawers();
			}
		}
	}
	
	@Override
	public void setTitle(CharSequence title) {
		if (mToolbarView != null) {
			mToolbarView.setTitle(title);
		}
	}
	
	@Override
	public void onReceiveMessage(String message, Object data, Boolean sticky) {
		if ("internal.fragment_attachment".equals(message) || "internal.backstack_changed".equals(message)) {
			setupNavigation();
		}
	}
	
	protected void onNavigationClick(View view, boolean back) {
		if (!back && mDrawerView != null) {
			if (mDrawerView.isDrawerOpen(GravityCompat.START)) {
				mDrawerView.closeDrawer(GravityCompat.START);
				
			} else {
				mDrawerView.openDrawer(GravityCompat.START);
			}
			
		} else if (back) {
			FragmentManager manager = getSupportFragmentManager();
			manager.popBackStack();
		}
	}

	@Override
	public boolean onNavigationItemSelected(MenuItem item) {
		Fragment fragment = null;
		
		switch (item.getItemId()) {
			case R.id.navigation_process_overview: fragment = new FragmentProcessList(); break;
			case R.id.navigation_recent_alerts: fragment = new FragmentAlertList(); break;
            case R.id.navigation_whitelist: fragment = new FragmentWhiteList(); break;
			case R.id.navigation_configuration: fragment = new FragmentConfiguration(); break;
			default: return false;
		}

		loadFragment("" + item.getTitleCondensed(), fragment, false);
		
		return true;
	}
	
	protected void setupNavigation() {
		FragmentManager fragmentManager = getSupportFragmentManager();
		Menu navigationMenu = mNavigationView.getMenu();
		
		for (int i=0; i < navigationMenu.size(); i++) {
			MenuItem item = navigationMenu.getItem(i);
			boolean isLoaded = fragmentManager.findFragmentByTag("" + item.getTitleCondensed()) != null;
			
			if (isLoaded) {
				item.setChecked(true);
				mToolbarView.setTitle("" + item.getTitle());
				
				break;
			}
		}
		
		if (fragmentManager.getBackStackEntryCount() > 0) {
			mToolbarTopView.setNavigationIcon(R.drawable.ic_arrow_back_white_24dp);
			mToolbarTopView.setNavigationOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					onNavigationClick(v, true);
				}
			});
			
		} else if (mDrawerView != null) {
			mToolbarTopView.setNavigationIcon(R.drawable.ic_menu_white_24dp);
			mToolbarTopView.setNavigationOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					onNavigationClick(v, false);
				}
			});
			
		} else {
			mToolbarTopView.setNavigationIcon(null);
		}
	}
}
