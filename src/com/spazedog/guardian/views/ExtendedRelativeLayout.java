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

package com.spazedog.guardian.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

import com.spazedog.guardian.R;

public class ExtendedRelativeLayout extends RelativeLayout {
	
	protected Integer mMaxWidth = 0;
	protected Integer mMaxHeight = 0;
	protected Integer mMinWidth = 0;
	protected Integer mMinHeight = 0;
	
	protected Boolean mAdoptWidth = false;
	protected Boolean mAdoptHeight = false;
	
	public ExtendedRelativeLayout(Context context) {
		super(context);
	}
	
	public ExtendedRelativeLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
		
		TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ViewDimensionOptions);
		mMaxWidth = a.getDimensionPixelSize(R.styleable.ViewDimensionOptions_layout_dimens_maxWidth, 0);
		mMaxHeight = a.getDimensionPixelSize(R.styleable.ViewDimensionOptions_layout_dimens_maxHeight, 0);
		mMinWidth = a.getDimensionPixelSize(R.styleable.ViewDimensionOptions_layout_dimens_minWidth, 0);
		mMinHeight = a.getDimensionPixelSize(R.styleable.ViewDimensionOptions_layout_dimens_minHeight, 0);
		
		mAdoptWidth = a.getBoolean(R.styleable.ViewDimensionOptions_layout_dimens_setHeightAsWidth, false);
		mAdoptHeight = a.getBoolean(R.styleable.ViewDimensionOptions_layout_dimens_setWidthAsHeight, false);
		a.recycle();
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		Integer width = MeasureSpec.getSize(widthMeasureSpec);
		Integer height = MeasureSpec.getSize(heightMeasureSpec);
		
		if (mAdoptWidth && width != height) {
			width = height;
			widthMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.getMode(widthMeasureSpec));
		}
		
		if (mAdoptWidth && width != height) {
			height = width;
			heightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.getMode(widthMeasureSpec));
		}
		
		if ((mMaxWidth > 0 && width > mMaxWidth) || (mMinWidth > 0 && width < mMinWidth)) {
			width = mMaxWidth > 0 && width > mMaxWidth ? mMaxWidth : mMinWidth;
			widthMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.getMode(widthMeasureSpec));
		}
		
		if ((mMaxHeight > 0 && height > mMaxHeight) || (mMinHeight > 0 && height < mMinHeight)) {
			height = mMaxHeight > 0 && height > mMaxHeight ? mMaxHeight : mMinHeight;
			heightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.getMode(heightMeasureSpec));
		}
		
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	}
}
