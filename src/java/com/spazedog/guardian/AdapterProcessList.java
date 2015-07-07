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

import android.graphics.Bitmap;
import android.support.v4.util.LruCache;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.spazedog.guardian.application.Controller;
import com.spazedog.guardian.backend.xposed.WakeLockService.ProcessLockInfo;
import com.spazedog.guardian.scanner.EntityAndroid;
import com.spazedog.guardian.scanner.containers.ProcEntity;
import com.spazedog.guardian.scanner.containers.ProcEntity.DataLoader;
import com.spazedog.guardian.scanner.containers.ProcList;

import java.lang.ref.WeakReference;

public class AdapterProcessList extends RecyclerView.Adapter<AdapterProcessList.ViewHolder> implements OnClickListener {

	protected static class ViewHolder extends RecyclerView.ViewHolder {
		public TextView textLabel;
		public TextView textName;
		public TextView textUsage;
		public TextView textLock;
		public TextView textImportance;
		public ImageView image;
		public View rootView;
		public Integer position;
		
		public ViewHolder(ViewGroup view) {
			super(view);
			
			Common.setTypeFace(view, Common.TYPEFACE.DefaultRegular(view.getContext()));

			rootView = view.findViewById(R.id.process_item_clickable);
			textLabel = (TextView) view.findViewById(R.id.process_item_label);
			textName = (TextView) view.findViewById(R.id.process_item_name);
			textUsage = (TextView) view.findViewById(R.id.process_item_usage);
			textLock = (TextView) view.findViewById(R.id.process_item_lock);
			textImportance = (TextView) view.findViewById(R.id.process_item_importance);
			image = (ImageView) view.findViewById(R.id.process_item_img);
		}
	}
	
	public static interface OnItemClickListener {
		public void onItemClick(View view, Integer position);
	}
	
	protected WeakReference<Controller> mController;
	protected OnItemClickListener mListener;
	protected LruCache<String, Bitmap> mImageCache;
	protected ProcList<?> mProcessList;
	
	public AdapterProcessList(Controller controller) {
		mController = new WeakReference<Controller>(controller);
		mImageCache = new LruCache<String, Bitmap>( Math.round(0.25f * Runtime.getRuntime().maxMemory() / 1024) ) {
			@Override
			protected int sizeOf(String key, Bitmap value) {
				return (value.getRowBytes() * value.getHeight()) / 1024;
			}
		};
	}
	
	@Override
	public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		synchronized(this) {
			return new ViewHolder((ViewGroup) LayoutInflater.from(parent.getContext()).inflate(Common.resolveAttr(parent.getContext(), R.attr.layout_adapterProcessListItem), parent, false));
		}
	}

	@Override
	public int getItemCount() {
		synchronized(this) {
			return mProcessList != null ? mProcessList.getEntitySize() : 0;
		}
	}

	@Override
	public void onBindViewHolder(ViewHolder holder, int position) {
		synchronized(this) {
			Controller controller = mController.get();
			ProcEntity<?> entity = mProcessList.getEntity(position);
            DataLoader entityData = entity.getDataLoader(controller);
	
			holder.textUsage.setText( (entity.getCpuUsage() + "%") );
			holder.textLabel.setText(entityData.getPackageLabel());
			holder.textName.setText(entity.getProcessName());
			holder.image.setImageBitmap( loadIcon(entityData) );
			holder.textImportance.setText(entityData.getImportanceLabel());
			
			holder.rootView.setOnClickListener(this);
			holder.rootView.setTag(position);
			
			ProcessLockInfo lockInfo = null;
			EntityAndroid androidEntity = EntityAndroid.cast(entity);
			if (androidEntity != null) {
				lockInfo = androidEntity.getProcessLockInfo();
				
				if (lockInfo != null) {
					holder.textLock.setText(Common.convertTime(lockInfo.getLockTime()));
				}
			}
			
			holder.textLock.setVisibility( lockInfo != null ? View.VISIBLE : View.GONE );
		}
	}
	
	@Override
	public void onViewRecycled(ViewHolder holder) {
		synchronized(this) {
			/*
			 * Release the bitmap to allow LruCache to decide it's fate
			 */
			holder.image.setImageDrawable(null);
			holder.rootView.setOnClickListener(null);
		}
	}
	
	@Override
	public void onClick(View view) {
		mListener.onItemClick(view, (Integer) view.getTag());
	}
	
	public void setOnItemClickListener(OnItemClickListener listener) {
		mListener = listener;
	}
	
	public void updateDataSet(ProcList<?> processList) {
		synchronized(this) {
			Integer curSize = getItemCount();			
			Integer newSize = processList.getEntitySize();
			
			mProcessList = processList;

			if (curSize > 0) {
				notifyItemRangeChanged(0, curSize > newSize ? newSize : curSize);
			}
			
			if (curSize > newSize) {
				notifyItemRangeRemoved(newSize, curSize - newSize);
				
			} else {
				notifyItemRangeInserted(curSize, newSize - curSize);
			}
		}
	}
	
	protected Bitmap loadIcon(DataLoader entityData) {
		String cacheId = entityData.getImportance() > 0 ? entityData.getPackageName() : "linux:process";
		Bitmap bitmap = mImageCache.get(cacheId);
		
		if (bitmap == null) {
			mImageCache.put(cacheId, (bitmap = entityData.getPackageBitmap(60f, 60f)));
		}
		
		return bitmap;
	}
}
