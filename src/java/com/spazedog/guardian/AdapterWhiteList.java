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
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.spazedog.guardian.application.Controller;
import com.spazedog.guardian.scanner.containers.ProcEntity;
import com.spazedog.guardian.scanner.containers.ProcEntity.DataLoader;

import java.lang.ref.WeakReference;
import java.util.List;

public class AdapterWhiteList extends RecyclerView.Adapter<AdapterWhiteList.ViewHolder> {

	protected static class ViewHolder extends RecyclerView.ViewHolder {
		public TextView textLabel;
		public TextView textName;
		public ImageView image;

		public ViewHolder(View view) {
			super(view);

			Common.setTypeFace(view, Common.TYPEFACE.DefaultRegular(view.getContext()));

			textLabel = (TextView) view.findViewById(R.id.process_item_label);
			textName = (TextView) view.findViewById(R.id.process_item_name);
			image = (ImageView) view.findViewById(R.id.process_item_img);
		}
	}

	protected WeakReference<Controller> mController;
	protected LruCache<String, Bitmap> mImageCache;
	protected List<ProcEntity<?>> mEntities;

	public AdapterWhiteList(Controller controller) {
		mController = new WeakReference<Controller>(controller);
		mImageCache = new LruCache<String, Bitmap>( Math.round(0.25f * Runtime.getRuntime().maxMemory() / 1024) ) {
			@Override
			protected int sizeOf(String key, Bitmap value) {
				return (value.getRowBytes() * value.getHeight()) / 1024;
			}
		};
	}

	@Override
	public int getItemCount() {
		return mEntities != null ? mEntities.size() : 0;
	}

	@Override
	public void onBindViewHolder(ViewHolder holder, int position) {
		Controller controller = mController.get();
		ProcEntity<?> entity = mEntities.get(position);
        DataLoader entityData = entity.getDataLoader(controller);

		holder.textLabel.setText(entityData.getPackageLabel());
		holder.textName.setText(entity.getProcessName());
		holder.image.setImageBitmap(loadIcon(entityData));
	}

    @Override
    public void onViewRecycled(ViewHolder holder) {
        synchronized(this) {
			/*
			 * Release the bitmap to allow LruCache to decide it's fate
			 */
            holder.image.setImageDrawable(null);
        }
    }

	@Override
	public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		return new ViewHolder((ViewGroup) LayoutInflater.from(parent.getContext()).inflate(Common.resolveAttr(parent.getContext(), R.attr.layout_adapterWhitelistListItem), parent, false));
	}
	
	public void updateDataSet(List<ProcEntity<?>> entities) {
        mEntities = entities;
		
		notifyDataSetChanged();
	}

    public void removeFromDataSet(int position) {
        mEntities.remove(position);
        notifyItemRemoved(position);
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
