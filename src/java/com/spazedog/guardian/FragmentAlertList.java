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

import java.util.ArrayList;
import java.util.List;

import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.LayoutManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.spazedog.guardian.AdapterAlertList.OnItemClickListener;
import com.spazedog.guardian.backend.containers.ThresholdItem;
import com.spazedog.guardian.db.AlertListDB;
import com.spazedog.guardian.utils.AbstractFragment;
import com.spazedog.lib.utilsLib.utils.Conversion;

public class FragmentAlertList extends AbstractFragment implements OnItemClickListener {
	
	protected RecyclerView mRecyclerView;
	protected AdapterAlertList mRecyclerAdapter;
	protected LayoutManager mRecyclerLayoutManager;

	@Override 
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setHasOptionsMenu(true);
	}
	
	@Override 
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(Conversion.attrToRes(getActivity(), R.attr.layout_fragmentAlertListLayout), container, false);
	}
	
	@Override 
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		
		mRecyclerView = (RecyclerView) view.findViewById(R.id.alert_list);
		mRecyclerLayoutManager = new LinearLayoutManager(getActivity());
		mRecyclerAdapter = new AdapterAlertList(getController());
        mRecyclerAdapter.setOnItemClickListener(this);
		mRecyclerView.setLayoutManager(mRecyclerLayoutManager);
		mRecyclerView.setAdapter(mRecyclerAdapter);
	}
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.fragment_alert_list_menu, menu);
		
		super.onCreateOptionsMenu(menu, inflater);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_btn_clear:
				if (mRecyclerAdapter.getItemCount() > 0) {
                    AlertListDB db = getSettings().getAlertListDatabase();
					db.clear();
					
					mRecyclerAdapter.updateDataSet( new ThresholdItem[0] );
				}
				
				return true;
		}
		
		return super.onOptionsItemSelected(item);
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		List<ThresholdItem> list = new ArrayList<ThresholdItem>();
        AlertListDB db = getSettings().getAlertListDatabase();
		
		for (ThresholdItem item : db) {
			if (item != null) {
				list.add(item);
			}
		}
		
		mRecyclerAdapter.updateDataSet( list.toArray(new ThresholdItem[list.size()]) );
	}

    @Override
    public void onItemClick(ThresholdItem item, int position) {
        ActivityLaunch activity = (ActivityLaunch) getActivity();
        AbstractFragment fragment = activity.getFragment(R.id.fragment_process_details);
        Bundle bundle = new Bundle();

        bundle.putParcelable("entity", item.getEntity());
        fragment.setArguments(bundle);

        ((ActivityLaunch) getActivity()).loadFragment("Details", fragment, true);
    }
}
