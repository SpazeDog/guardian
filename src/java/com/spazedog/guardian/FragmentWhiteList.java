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
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.support.v7.widget.RecyclerView.LayoutManager;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.spazedog.guardian.db.WhiteListDB;
import com.spazedog.guardian.scanner.containers.ProcEntity;
import com.spazedog.guardian.utils.AbstractFragment;
import com.spazedog.lib.utilsLib.utils.Conversion;

import java.util.ArrayList;
import java.util.List;

public class FragmentWhiteList extends AbstractFragment {
	
	protected RecyclerView mRecyclerView;
	protected AdapterWhiteList mRecyclerAdapter;
	protected LayoutManager mRecyclerLayoutManager;
    protected ItemTouchHelper mItemTouchHelper;

    protected List<ProcEntity<?>> mEntities = new ArrayList<ProcEntity<?>>();

	@Override 
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}
	
	@Override 
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(Conversion.attrToRes(getActivity(), R.attr.layout_fragmentWhitelistListLayout), container, false);
	}
	
	@Override 
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		
		mRecyclerView = (RecyclerView) view.findViewById(R.id.whitelist_list);
		mRecyclerLayoutManager = new LinearLayoutManager(getActivity());
		mRecyclerAdapter = new AdapterWhiteList(getController());
		mRecyclerView.setLayoutManager(mRecyclerLayoutManager);
		mRecyclerView.setAdapter(mRecyclerAdapter);

        mItemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT|ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder viewHolder1) {
                return false;
            }

            @Override
            public void onSwiped(ViewHolder viewHolder, int swipeDirection) {
                int position = viewHolder.getAdapterPosition();
                ProcEntity<?> entity = mEntities.get(position);
                WhiteListDB db = getSettings().getWhiteListDatabase();

                db.removeEntity(entity.getProcessName());

                mRecyclerAdapter.removeFromDataSet(position);
            }
        });

        mItemTouchHelper.attachToRecyclerView(mRecyclerView);
	}
	
	@Override
	public void onResume() {
		super.onResume();

        WhiteListDB db = getSettings().getWhiteListDatabase();

        mEntities.clear();

		for (ProcEntity<?> entity : db) {
			if (entity != null) {
                mEntities.add(entity);
			}
		}
		
		mRecyclerAdapter.updateDataSet(mEntities);
	}
}
