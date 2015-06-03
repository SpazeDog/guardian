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

import com.spazedog.guardian.db.AlertsDB;
import com.spazedog.guardian.db.AlertsDB.EntityRow;
import com.spazedog.guardian.utils.AbstractFragment;

public class FragmentAlertList extends AbstractFragment {
	
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
		return inflater.inflate(Common.resolveAttr(getActivity(), R.attr.layout_fragmentAlertListLayout), container, false);
	}
	
	@Override 
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		
		mRecyclerView = (RecyclerView) view.findViewById(R.id.alert_list);
		mRecyclerLayoutManager = new LinearLayoutManager(getActivity());
		mRecyclerAdapter = new AdapterAlertList(getController());
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
			case R.id.menu_btn_remove:
				if (mRecyclerAdapter.getItemCount() > 0) {
					AlertsDB db = new AlertsDB(getActivity());
					db.clearProcessEntities();
					db.close();
					
					mRecyclerAdapter.updateDataSet( new EntityRow[0] );
				}
				
				return true;
		}
		
		return super.onOptionsItemSelected(item);
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		List<EntityRow> list = new ArrayList<EntityRow>();
		AlertsDB db = new AlertsDB(getActivity());
		
		for (EntityRow row : db) {
			list.add(row);
		}
		
		db.close();
		
		mRecyclerAdapter.updateDataSet( list.toArray(new EntityRow[list.size()]) );
	}
}
