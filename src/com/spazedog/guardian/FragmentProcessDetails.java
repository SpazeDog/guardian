package com.spazedog.guardian;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.spazedog.guardian.application.Settings;
import com.spazedog.guardian.backend.xposed.WakeLockService.ProcessLockInfo;
import com.spazedog.guardian.scanner.IProcess.IProcessList;
import com.spazedog.guardian.scanner.IProcessEntity;
import com.spazedog.guardian.scanner.ProcessEntityAndroid;
import com.spazedog.guardian.utils.AbstractFragment;
import com.spazedog.guardian.views.TextboxWidget;
import com.spazedog.lib.rootfw4.RootFW;

public class FragmentProcessDetails extends AbstractFragment {
	
	/*
	 * TODO: 	
	 * 			Add option to update all information using an actionbar update button. 
	 * 			This includes the information in the IProcessEntity interface.
	 */
	
	protected IProcessList mProcesses;
	protected IProcessEntity mEntity;
	protected RunningAppProcessInfo[] mRunningProcesses;
	protected Map<Integer, RunningAppProcessInfo> mRunningProcessesMap;
	protected Snackbar mSnackBar;
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		if (mRunningProcesses != null) {
			outState.putParcelableArray("mRunningProcesses", mRunningProcesses);
		}
		
		super.onSaveInstanceState(outState);
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setHasOptionsMenu(true);
		
		if (savedInstanceState != null) {
			mRunningProcesses = (RunningAppProcessInfo[]) savedInstanceState.getParcelableArray("mRunningProcesses");
		}
		
		if (mRunningProcesses == null) {
			ActivityManager activityManager = (ActivityManager) getActivity().getSystemService(Activity.ACTIVITY_SERVICE);
			List<RunningAppProcessInfo> runningApps = activityManager.getRunningAppProcesses();
			
			mRunningProcesses = runningApps.toArray( new RunningAppProcessInfo[runningApps.size()] );
		}
		
		mProcesses = getArguments().getParcelable("processes");
		mEntity = getArguments().getParcelable("entity");
	}

	@Override 
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(Common.resolveAttr(getActivity(), R.attr.layout_fragmentProcessDetailsLayout), container, false);
	}
	
	@Override 
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		
		ImageView iconView = (ImageView) view.findViewById(R.id.process_item_img);
		iconView.setImageBitmap(mEntity.loadPackageBitmap(getActivity(), 60, 60));
		
		String label = mEntity.loadPackageLabel(getActivity());
		if (label != null) {
			TextView titleView = (TextView) view.findViewById(R.id.process_item_label);
			titleView.setText(label);
		}
		
		TextView summaryView = (TextView) view.findViewById(R.id.process_item_name);
		summaryView.setText(mEntity.getProcessName());
		
		TextboxWidget importanceView = (TextboxWidget) view.findViewById(R.id.process_item_importance);
		importanceView.setText(mEntity.loadImportanceLabel(getActivity().getResources()));
		
		TextboxWidget pidView = (TextboxWidget) view.findViewById(R.id.process_item_pid);
		pidView.setText("" + mEntity.getProcessId());
		
		TextboxWidget averageView = (TextboxWidget) view.findViewById(R.id.process_item_usage);
		averageView.setText("" + mEntity.getAverageCpu());
		
		if (mEntity.getImportance() > 0) {
			createAndroidViews(view);
		}
	}
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		Settings settings = getSettings();
		int importance = mEntity.getImportance();
		boolean killable = settings.isRootEnabled() || 
				(importance > 0 && 
						importance != RunningAppProcessInfo.IMPORTANCE_FOREGROUND && 
						importance != RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE && 
						importance != RunningAppProcessInfo.IMPORTANCE_VISIBLE);
		
		if (killable) {
			inflater.inflate(R.menu.fragment_process_details_menu, menu);
		}
		
		super.onCreateOptionsMenu(menu, inflater);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_btn_remove:
				killEntity();
				
				return true;
		}
		
		return super.onOptionsItemSelected(item);
	}
	
	@Override
	public void onPause() {
		super.onPause();
		
		/*
		 * Like most in the support libraries, this does not work properly. 
		 * It should go away by it's own, but it does not.
		 */
		if (mSnackBar != null) {
			mSnackBar.dismiss();
			mSnackBar = null;
		}
	}
	
	/*
	 * TODO: 
	 * 			This information should be build into the IProcessEntity interface.
	 * 
	 * 			Add info about packages contained in this process
	 * 
	 * 			Add into about services contained in this process
	 */
	public void createAndroidViews(View view) {
		int pid = mEntity.getProcessId();
		Map<Integer, RunningAppProcessInfo> processes = getRunningProcesses();
		RunningAppProcessInfo appInfo = processes.get(pid);
		
		if (appInfo != null) {
			if (appInfo.importanceReasonPid > 0) {
				createAndroidCaller(view, appInfo);
			}
		}
		
		createAndroidWakelock(view);
	}
	
	public void createAndroidWakelock(View view) {
		ProcessEntityAndroid androidEntity = (ProcessEntityAndroid) mEntity;
		ProcessLockInfo lockInfo = androidEntity.getProcessLockInfo();
		
		if (lockInfo != null) {
			View groupView = view.findViewById(R.id.process_group_wakelock);
			groupView.setVisibility(View.VISIBLE);
			
			TextboxWidget totalLockView = (TextboxWidget) view.findViewById(R.id.process_item_wakelock_total);
			totalLockView.setText( Common.convertTime(lockInfo.getLockTime()) );
			
			TextboxWidget onLockView = (TextboxWidget) view.findViewById(R.id.process_item_wakelock_on);
			onLockView.setText( Common.convertTime(lockInfo.getLockTimeOn()) );
			
			TextboxWidget offLockView = (TextboxWidget) view.findViewById(R.id.process_item_wakelock_off);
			offLockView.setText( Common.convertTime(lockInfo.getLockTimeOff()) );
		}
	}
	
	public void createAndroidCaller(View view, RunningAppProcessInfo appInfo) {
		IProcessEntity callerEntity = mProcesses.findEntity(appInfo.importanceReasonPid);
		
		if (callerEntity != null) {
			View groupView = view.findViewById(R.id.process_group_caller);
			groupView.setVisibility(View.VISIBLE);
			
			ImageView iconView = (ImageView) view.findViewById(R.id.process_caller_img);
			iconView.setImageBitmap(callerEntity.loadPackageBitmap(getActivity(), 60, 60));
			
			String label = callerEntity.loadPackageLabel(getActivity());
			if (label != null) {
				TextView titleView = (TextView) view.findViewById(R.id.process_caller_label);
				titleView.setText(label);
			}
			
			TextView summaryView = (TextView) view.findViewById(R.id.process_caller_name);
			summaryView.setText(callerEntity.getProcessName());
		}
	}
	
	/*
	 * Return a map of running android processes which can be accessed by their pid's
	 */
	@SuppressLint("UseSparseArrays")
	public Map<Integer, RunningAppProcessInfo> getRunningProcesses() {
		if (mRunningProcessesMap == null) {
			mRunningProcessesMap = new HashMap<Integer, RunningAppProcessInfo>();
			
			for(RunningAppProcessInfo appInfo : mRunningProcesses) {
				mRunningProcessesMap.put(appInfo.pid, appInfo);
			}
		}
		
		return mRunningProcessesMap;
	}
	
	public void killEntity() {
		mSnackBar = Snackbar.make(getView(), "Are you sure that you want to force close this process?", Snackbar.LENGTH_LONG)
			.setAction("Force Close", new OnClickListener(){
				@Override
				public void onClick(View v) {
		        	Settings settings = getSettings();

					if (settings.isRootEnabled() && RootFW.connect() && RootFW.isRoot()) {
						RootFW.getProcess(mEntity.getProcessId()).kill();
						
					} else {
						ActivityManager manager = (ActivityManager) getActivity().getSystemService(Context.ACTIVITY_SERVICE);
						manager.killBackgroundProcesses(mEntity.loadPackageName(getActivity()));
					}
					
					getFragmentManager().popBackStackImmediate();
				}
			});
		
		mSnackBar.show();
	}
}
