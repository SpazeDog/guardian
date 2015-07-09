package com.spazedog.guardian;

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
import com.spazedog.guardian.scanner.EntityAndroid;
import com.spazedog.guardian.scanner.EntityAndroid.AndroidDataLoader;
import com.spazedog.guardian.scanner.containers.ProcEntity;
import com.spazedog.guardian.scanner.containers.ProcEntity.DataLoader;
import com.spazedog.guardian.scanner.containers.ProcList;
import com.spazedog.guardian.utils.AbstractFragment;
import com.spazedog.guardian.views.TextboxWidget;
import com.spazedog.lib.rootfw4.RootFW;

public class FragmentProcessDetails extends AbstractFragment {
	
	/*
	 * TODO: 	
	 * 			Add option to update all information using an actionbar update button. 
	 * 			This includes the information in the IProcessEntity interface.
	 */
	
	protected ProcList<?> mProcesses;
	protected ProcEntity<?> mEntity;
	protected Snackbar mSnackBar;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setHasOptionsMenu(true);

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

        DataLoader entityData = mEntity.getDataLoader(getActivity());

		ImageView iconView = (ImageView) view.findViewById(R.id.process_item_img);
		iconView.setImageBitmap(entityData.getPackageBitmap(60, 60));
		
		String label = entityData.getPackageLabel();
		if (label != null) {
			TextView titleView = (TextView) view.findViewById(R.id.process_item_label);
			titleView.setText(label);
		}
		
		TextView summaryView = (TextView) view.findViewById(R.id.process_item_name);
		summaryView.setText(mEntity.getProcessName());
		
		TextboxWidget importanceView = (TextboxWidget) view.findViewById(R.id.process_item_importance);
		importanceView.setText(entityData.getImportanceLabel());
		
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
        EntityAndroid entity = EntityAndroid.cast(mEntity);

        if (entity != null) {
            AndroidDataLoader entityData = entity.getDataLoader(getActivity());

            if (entityData.getCallingProcessId() > 0) {
                createAndroidCaller(view, entityData);
            }

            createAndroidWakelock(view, entity);
        }
	}
	
	public void createAndroidWakelock(View view, EntityAndroid entity) {
		ProcessLockInfo lockInfo = entity.getProcessLockInfo();
		
		if (lockInfo != null) {
			View groupView = view.findViewById(R.id.process_group_wakelock);
			groupView.setVisibility(View.VISIBLE);
			
			TextboxWidget totalLockView = (TextboxWidget) view.findViewById(R.id.process_item_wakelock_total);
			totalLockView.setText(Common.convertTime(lockInfo.getLockTime()));
			
			TextboxWidget onLockView = (TextboxWidget) view.findViewById(R.id.process_item_wakelock_on);
			onLockView.setText( Common.convertTime(lockInfo.getLockTimeOn()) );
			
			TextboxWidget offLockView = (TextboxWidget) view.findViewById(R.id.process_item_wakelock_off);
			offLockView.setText( Common.convertTime(lockInfo.getLockTimeOff()) );
		}
	}
	
	public void createAndroidCaller(View view, AndroidDataLoader entityData) {
        if (mProcesses != null) {
            ProcEntity<?> callerEntity = mProcesses.findEntity(entityData.getCallingProcessId());

            if (callerEntity != null) {
                DataLoader callerData = callerEntity.getDataLoader(getActivity());
                View groupView = view.findViewById(R.id.process_group_caller);
                groupView.setVisibility(View.VISIBLE);

                ImageView iconView = (ImageView) view.findViewById(R.id.process_caller_img);
                iconView.setImageBitmap(callerData.getPackageBitmap(60, 60));

                String label = callerData.getPackageLabel();
                if (label != null) {
                    TextView titleView = (TextView) view.findViewById(R.id.process_caller_label);
                    titleView.setText(label);
                }

                TextView summaryView = (TextView) view.findViewById(R.id.process_caller_name);
                summaryView.setText(callerEntity.getProcessName());
            }
        }
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
						manager.killBackgroundProcesses(mEntity.getDataLoader(getActivity()).getPackageName());
					}
					
					getFragmentManager().popBackStackImmediate();
				}
			});
		
		mSnackBar.show();
	}
}
