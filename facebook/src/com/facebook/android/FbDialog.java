/*
 * Copyright 2010 Facebook, Inc.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.facebook.android;


import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Display;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;


import com.facebook.android.Facebook.DialogListener;


public class FbDialog extends Dialog {

	private class FbWebViewClient extends WebViewClient {
		@Override
		public void onPageFinished(final WebView view, final String url) {
			super.onPageFinished(view, url);
			try {
				final String title = mWebView.getTitle();
				if (title != null && title.length() > 0) {
					mTitle.setText(title);
				}
				mSpinner.dismiss();
			}
			catch (final Exception e) {
				e.printStackTrace();
			}
			mSpinner.dismiss();

			// Now that the WebView has loaded some content, we can set its
			// height to WRAP_CONTENT. This is important because it will allow
			// the framework to resize the window when the soft keyboard is
			// shown without messing up the display.
			final ViewGroup.LayoutParams lp = mWebView.getLayoutParams();
			lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
			mWebView.setLayoutParams(lp);
		}


		@Override
		public void onPageStarted(final WebView view, final String url, final Bitmap favicon) {
			// Log.d("Facebook-WebView", "Webview loading URL: " + url);
			super.onPageStarted(view, url, favicon);
			try {
				mSpinner.show();
			}
			catch (final Exception e) {
				e.printStackTrace();
			}
		}


		@Override
		public void onReceivedError(final WebView view, final int errorCode, final String description, final String failingUrl) {
			super.onReceivedError(view, errorCode, description, failingUrl);
			mListener.onError(new DialogError(description, errorCode, failingUrl));
			dismiss();
		}


		@Override
		public boolean shouldOverrideUrlLoading(final WebView view, final String url) {
			// Log.d("Facebook-WebView", "Redirect URL: " + url);
			if (url.startsWith(Facebook.REDIRECT_URI)) {
				final Bundle values = Util.parseUrl(url);

				String error = values.getString("error");
				if (error == null) {
					error = values.getString("error_type");
				}

				if (error == null) {
					mListener.onComplete(values);
				}
				else if (error.equals("access_denied") || error.equals("OAuthAccessDeniedException")) {
					mListener.onCancel();
				}
				else {
					mListener.onFacebookError(new FacebookError(error));
				}

				dismiss();
				return true;
			}
			else if (url.startsWith(Facebook.CANCEL_URI)) {
				mListener.onCancel();
				dismiss();
				return true;
			}
			else if (url.contains(FbDialog.DISPLAY_STRING)) {
				return false;
			}
			// launch non-dialog URLs in a full browser
			getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
			return true;
		}
	}


	static final float[]					DIMENSIONS_DIFF_LANDSCAPE	= { 20, 60 };


	static final float[]					DIMENSIONS_DIFF_PORTRAIT	= { 40, 60 };


	static final String						DISPLAY_STRING				= "touch";


	static final int						FB_BLUE						= 0xFF6D84B4;


	static final String						FB_ICON						= "icon.png";


	static final int						MARGIN						= 4;


	static final int						PADDING						= 2;


	static final FrameLayout.LayoutParams	WRAP						= new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);


	private LinearLayout					mContent;


	private final DialogListener			mListener;


	private ProgressDialog					mSpinner;


	private TextView						mTitle;


	private final String					mUrl;


	private WebView							mWebView;


	public FbDialog(final Context context, final String url, final DialogListener listener) {
		super(context);
		mUrl = url;
		mListener = listener;
	}


	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mSpinner = new ProgressDialog(getContext());
		mSpinner.requestWindowFeature(Window.FEATURE_NO_TITLE);
		mSpinner.setMessage("Loading...");

		mContent = new LinearLayout(getContext());
		mContent.setOrientation(LinearLayout.VERTICAL);
		setUpTitle();
		setUpWebView();
		addContentView(mContent, FbDialog.WRAP);

		getWindow().setSoftInputMode(LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

		setOnCancelListener(new OnCancelListener() {
			@Override
			public void onCancel(final DialogInterface dialog) {
				mWebView.stopLoading();
				mListener.onCancel();
				dismiss();
			}
		});
	}


	private void setUpTitle() {
		requestWindowFeature(Window.FEATURE_NO_TITLE);

		final Context context = getContext();
		final Resources resources = context.getResources();

		final int iconId = resources.getIdentifier("facebook_icon", "drawable", context.getPackageName());
		final Drawable icon = resources.getDrawable(iconId);
		mTitle = new TextView(getContext());
		mTitle.setText("Facebook");
		mTitle.setTextColor(Color.WHITE);
		mTitle.setTypeface(Typeface.DEFAULT_BOLD);
		mTitle.setBackgroundColor(FbDialog.FB_BLUE);
		mTitle.setPadding(FbDialog.MARGIN + FbDialog.PADDING, FbDialog.MARGIN, FbDialog.MARGIN, FbDialog.MARGIN);
		mTitle.setCompoundDrawablePadding(FbDialog.MARGIN + FbDialog.PADDING);
		mTitle.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
		mContent.addView(mTitle);
	}


	private void setUpWebView() {
		try {
			getContext().deleteDatabase("webview.db");
		}
		catch (final Exception e) {
			e.printStackTrace();
		}
		try {
			getContext().deleteDatabase("webviewCache.db");
		}
		catch (final Exception e) {
			e.printStackTrace();
		}

		mWebView = new WebView(getContext());
		mWebView.setVerticalScrollBarEnabled(false);
		mWebView.setHorizontalScrollBarEnabled(false);
		mWebView.setWebViewClient(new FbDialog.FbWebViewClient());
		mWebView.getSettings().setJavaScriptEnabled(true);
		mWebView.loadUrl(mUrl);

		// Fix the initial size of the window. We can't set it to WRAP_CONTENT
		// yet because the web view doesn't take up any space until it loads
		// some content.
		final Display display = getWindow().getWindowManager().getDefaultDisplay();
		final float scale = getContext().getResources().getDisplayMetrics().density;
		final int orientation = getContext().getResources().getConfiguration().orientation;
		final float[] dimensions = orientation == Configuration.ORIENTATION_LANDSCAPE ? FbDialog.DIMENSIONS_DIFF_LANDSCAPE : FbDialog.DIMENSIONS_DIFF_PORTRAIT;

		mWebView.setLayoutParams(new LinearLayout.LayoutParams(display.getWidth() - (int) (dimensions[0] * scale + 0.5f), display.getHeight() - (int) (dimensions[1] * scale + 0.5f)));
		mContent.addView(mWebView);
	}
}
