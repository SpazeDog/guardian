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

import android.os.Bundle;
import android.os.Message;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.LayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

import com.spazedog.guardian.AdapterProcessList.OnItemClickListener;
import com.spazedog.guardian.application.Controller;
import com.spazedog.guardian.application.Settings;
import com.spazedog.guardian.scanner.IProcess.IProcessList;
import com.spazedog.guardian.scanner.ProcessScanner;
import com.spazedog.guardian.scanner.ProcessScanner.ScanMode;
import com.spazedog.guardian.utils.AbstractFragment;
import com.spazedog.guardian.utils.AbstractHandler;
import com.spazedog.guardian.utils.AbstractThread;

public class FragmentProcessList extends AbstractFragment implements OnItemClickListener {
	
	private static class UsageWorker extends AbstractThread<FragmentProcessList> {
		IProcessList mCachedScanner;
		
		public UsageWorker(FragmentProcessList reference) {
			super(reference);
		}
		
		@Override 
		public void onRun() {
			FragmentProcessList fragment = getReference();
			Settings settings = fragment.getSettings();
			Controller controller = fragment.getController();
			Integer timeout = fragment.mRecyclerAdapter.getItemCount() == 0 ? 1000 : 8000;
			ScanMode mode = settings.monitorLinux() ? ScanMode.COLLECT_PROCESSES : ScanMode.COLLECT_APPLICATIONS;
			
			if (mCachedScanner != null || (fragment.mRecyclerAdapter.getItemCount() == 0 && fragment.mSystemProcess != null && fragment.mSystemProcess.getEntitySize() > 0)) {
				if (mCachedScanner == null) {
					mCachedScanner = fragment.mSystemProcess;
					fragment.mSystemProcess = null;
				}
				
				fragment.mUsageHandler.obtainMessage(0, mCachedScanner).sendToTarget(); mCachedScanner = null;
				
			} else {
				IProcessList scanner = ProcessScanner.execute(controller, mode, fragment.mSystemProcess);
				
				if (scanner != null && (!isInterrupted() || fragment.mRecyclerAdapter.getItemCount() == 0)) {
					if (!isInterrupted() && (!isLocked() || fragment.mRecyclerAdapter.getItemCount() == 0)) {
						fragment.mUsageHandler.obtainMessage(0, scanner.sortEntities()).sendToTarget();
						
					} else {
						mCachedScanner = scanner.sortEntities();
					}
				}
			}
			
			if (!isInterrupted() && !isLocked()) {
				do {
					try {
						sleep(100); timeout -= 100;
						
					} catch (Throwable e) {}
					
				} while (timeout > 0 && !isInterrupted() && !isLocked());
			}
		}
	}
	
	private static class UsageHandler extends AbstractHandler<FragmentProcessList> {
		public UsageHandler(FragmentProcessList activity) {
			super(activity);
		}
		
		@Override
		public void handleMessage(Message msg) {
			FragmentProcessList fragment = getReference();
			IProcessList scanner = (IProcessList) msg.obj;
			
			if (fragment != null && fragment.mRecyclerView != null && scanner != null) {		
				fragment.mRecyclerAdapter.updateDataSet(scanner);
				fragment.mSystemProcess = scanner;
			}
		}
	}
	
    private RecyclerView mRecyclerView;
    private AdapterProcessList mRecyclerAdapter;
    private LayoutManager mRecyclerLayoutManager;
    
    private UsageWorker mUsageWorker;
    private UsageHandler mUsageHandler;
    
    private IProcessList mSystemProcess;
    
    private ViewGroup mActionBarMenu;
    private View mMenuItemPlayPause;
    
    @Override
    public void onSaveInstanceState(Bundle outState) {
    	outState.putParcelable("mSystemProcess", mSystemProcess);
    	
    	if (mMenuItemPlayPause != null) {
    		outState.putBoolean("mMenuItemPlayPause", mMenuItemPlayPause.isActivated());
    	}
    	
    	super.onSaveInstanceState(outState);
    }
	
	@Override 
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		if (savedInstanceState != null) {
			mSystemProcess = savedInstanceState.getParcelable("mSystemProcess");
		}
	}
	
	@Override 
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(Common.resolveAttr(getActivity(), R.attr.layout_fragmentProcessListLayout), container, false);
	}
	
	@Override 
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		
		mRecyclerView = (RecyclerView) view.findViewById(R.id.process_list);
		mRecyclerLayoutManager = new LinearLayoutManager(getActivity());
		mRecyclerAdapter = new AdapterProcessList(getController());
		mRecyclerAdapter.setOnItemClickListener(this);
		mRecyclerView.setLayoutManager(mRecyclerLayoutManager);
		mRecyclerView.setAdapter(mRecyclerAdapter);
		
		ActivityLaunch activity = (ActivityLaunch) getActivity();
		LayoutInflater inflater = activity.getLayoutInflater();
		mActionBarMenu = (ViewGroup) inflater.inflate(Common.resolveAttr(getActivity(), R.attr.layout_fragmentProcessListMenu), null);
		mMenuItemPlayPause = mActionBarMenu.findViewById(R.id.menu_playpause);
		
		mMenuItemPlayPause.setActivated( savedInstanceState != null && savedInstanceState.getBoolean("mMenuItemPlayPause", false) );
		mMenuItemPlayPause.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				v.setActivated(!v.isActivated());
				toggleWorkerLock("pause", v.isActivated());
			}
		});
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		mUsageHandler = new UsageHandler(this);
		mUsageWorker = new UsageWorker(this);
		mUsageWorker.start();
		
		if (mMenuItemPlayPause.isActivated()) {
			toggleWorkerLock("pause", true);
		}
	}
	
	@Override
	public void onPause() {
		super.onPause();
		
		if (mUsageWorker != null) {
			try {
				mUsageWorker.interrupt();
				mUsageWorker.join();
				mUsageWorker = null;
				mUsageHandler = null;
				
			} catch (InterruptedException e) {}
		}
	}
	
	@Override
	public void onStart() {
		super.onStart();
		
		((ActivityLaunch) getActivity()).addMenuItem(mActionBarMenu);
	}
	
	@Override
	public void onStop() {
		super.onStop();
		
		((ActivityLaunch) getActivity()).removeMenuItem(mActionBarMenu);
	}
	
	@Override
	public void onReceiveMessage(String message, Object data, Boolean sticky) {
		if ("activity.drawer_opened".equals(message)) {
			toggleWorkerLock("drawer", (Boolean) data);
		}
	}
	
	public void toggleWorkerLock(String id, Boolean lock) {
		if (mUsageWorker != null && lock) {
			mUsageWorker.lock(id);
			
		} else if (mUsageWorker != null) {
			mUsageWorker.release(id);
		}
	}

	@Override
	public void onItemClick(View view, Integer position) {
		FragmentProcessDetails fragment = new FragmentProcessDetails();
		Bundle bundle = new Bundle();
		
		bundle.putParcelable("entity", mSystemProcess.getEntity(position));
		bundle.putParcelable("processes", mSystemProcess);
		fragment.setArguments(bundle);
		
		((ActivityLaunch) getActivity()).loadFragment(fragment, true);
	}
}
