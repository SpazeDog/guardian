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

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.support.annotation.AttrRes;
import android.support.annotation.RawRes;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;

public class Common {
	
	public static class LOG {
		public static final int DEBUG = Log.DEBUG;
		public static final int INFO = Log.INFO;
		public static final int ERROR = Log.ERROR;
		
		public static void Debug(Object caller, String msg, Throwable tr) {
			Print(DEBUG, caller.getClass().getName(), msg, tr);
		}
		
		public static void Debug(Object caller, String msg) {
			Print(DEBUG, caller.getClass().getName(), msg, null);
		}
		
		public static void Debug(String tag, String msg, Throwable tr) {
			Print(DEBUG, tag, msg, tr);
		}
		
		public static void Debug(String tag, String msg) {
			Print(DEBUG, tag, msg, null);
		}

		public static void Info(Object caller, String msg, Throwable tr) {
			Print(INFO, caller.getClass().getName(), msg, tr);
		}
		
		public static void Info(Object caller, String msg) {
			Print(INFO, caller.getClass().getName(), msg, null);
		}
		
		public static void Info(String tag, String msg, Throwable tr) {
			Print(INFO, tag, msg, tr);
		}
		
		public static void Info(String tag, String msg) {
			Print(INFO, tag, msg, null);
		}

		public static void Error(Object caller, String msg, Throwable tr) {
			Print(ERROR, caller.getClass().getName(), msg, tr);
		}
		
		public static void Error(Object caller, String msg) {
			Print(ERROR, caller.getClass().getName(), msg, null);
		}
		
		public static void Error(String tag, String msg, Throwable tr) {
			Print(ERROR, tag, msg, tr);
		}
		
		public static void Error(String tag, String msg) {
			Print(ERROR, tag, msg, null);
		}
		
		public static void Trace(int level, Object caller, String msg) {
			Trace(level, caller.getClass().getName(), msg);
		}
		
		public static void Trace(int level, String tag, String msg) {
			msg += "\n";
			
			for (StackTraceElement stack : Thread.currentThread().getStackTrace()) {
				msg += "\t\t";
				msg += stack.toString();
				msg += "\n";
			}
			
			Print(level, tag, msg.toString(), null);
		}

		public static void Print(int level, String tag, String msg, Throwable tr) {
			switch (level) {
				case DEBUG: 
					if (Constants.ENABLE_DEBUG) {
						Log.d(tag, msg, tr);
					}
					
					break;
					
				case INFO: Log.i(tag, msg, tr); break;
				case ERROR: Log.e(tag, msg, tr); break;
				default: Log.v(tag, msg, tr);
			}
		}
	}
	
	public static class TYPEFACE {
		public static final Typeface DefaultRegular(Context context) {
			return RobotoRegular(context);
		}
		
		public static final Typeface RobotoRegular(Context context) {
			return Typeface.createFromAsset(context.getAssets(), "fonts/Roboto-Regular.ttf");
		}
		
		public static final Typeface RobotoBlack(Context context) {
			return Typeface.createFromAsset(context.getAssets(), "fonts/Roboto-Black.ttf");
		}
	}
	
	public static void setTypeFace(View view, Typeface typeFace) {
		if (view instanceof ViewGroup) {
			for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
				setTypeFace(((ViewGroup) view).getChildAt(i), typeFace);
			}
			
		} else if (view instanceof TextView) {
			Typeface currentTf = ((TextView) view).getTypeface();
			Integer type = 0;
			
			if (currentTf != null) {
				type = currentTf.getStyle();
			}
			
			((TextView) view).setTypeface(typeFace, type);
		}
	}
	
	public static @RawRes int resolveAttr(Context context, @AttrRes int attr) {
		TypedArray a = context.getTheme().obtainStyledAttributes(new int[]{attr});
		int res = a.getResourceId(0, 0);
		a.recycle();
		
		return res;
	}
	
	public static int dipToPixels(Resources resources, float dips) {
	    return (int) (dips * resources.getDisplayMetrics().density + 0.5f);
	}
	
	public static float pixelsToDip(Resources resources, int pixels) {
		return (float) (pixels / resources.getDisplayMetrics().density + 0.5f);
	}
	
	public static float getDisplaySW(Context context) {
		WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		Display display = wm.getDefaultDisplay();
		DisplayMetrics metrics = new DisplayMetrics();
		
		display.getMetrics(metrics);
		
		if (metrics.heightPixels >= metrics.widthPixels) {
			return pixelsToDip(context.getResources(), metrics.widthPixels);
			
		} else {
			return pixelsToDip(context.getResources(), metrics.heightPixels);
		}
	}
}
