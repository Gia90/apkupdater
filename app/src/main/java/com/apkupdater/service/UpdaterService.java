package com.apkupdater.service;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;

import com.apkupdater.R;
import com.apkupdater.event.UpdateFinalProgressEvent;
import com.apkupdater.event.UpdateProgressEvent;
import com.apkupdater.event.UpdateStartEvent;
import com.apkupdater.event.UpdateStopEvent;
import com.apkupdater.installedapp.InstalledApp;
import com.apkupdater.installedapp.InstalledAppUtil;
import com.apkupdater.updater.IUpdater;
import com.apkupdater.updater.Update;
import com.apkupdater.updater.UpdaterAPKMirror;
import com.apkupdater.updater.UpdaterAPKPure;
import com.apkupdater.updater.UpdaterGooglePlay;
import com.apkupdater.updater.UpdaterNotification;
import com.apkupdater.updater.UpdaterOptions;
import com.apkupdater.updater.UpdaterStatus;
import com.apkupdater.util.MyBus;

import org.androidannotations.annotations.Bean;
import org.androidannotations.annotations.EService;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.apkupdater.model.AppState;

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

@EService
public class UpdaterService
	extends IntentService
{
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	@Bean
	MyBus mBus;

	@Bean
	InstalledAppUtil mInstalledAppUtil;

	@Bean
	AppState mAppState;

	private final Lock mMutex = new ReentrantLock(true);
	private List<Update> mUpdates = new ArrayList<>();
	private UpdaterNotification mNotification;

	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	public UpdaterService(
	) {
		super(UpdaterService.class.getSimpleName());
	}

	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	public IUpdater createUpdater(
		String type,
		Context context,
		String s1,
		String s2
	) {
		switch (type) {
			case "APKMirror":
				return new UpdaterAPKMirror(context, s1, s2);
			case "APKPure":
				return new UpdaterAPKPure(context, s1, s2);
			case "GooglePlay":
				return new UpdaterGooglePlay(context, s1, s2);
			default:
				return null;
		}
	}

	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	private void updateSource(
		Executor executor,
		final String type,
		final InstalledApp app,
		final Queue<Integer> errors
	) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				IUpdater upd = createUpdater(type, getBaseContext(), app.getPname(), app.getVersion());
				if (upd.getResultStatus() == UpdaterStatus.STATUS_UPDATE_FOUND) {
					Update u = new Update(app, upd.getResultUrl());
					mUpdates.add(u);
					mBus.post(new UpdateProgressEvent(u));
				} else if (upd.getResultStatus() == UpdaterStatus.STATUS_ERROR){
					errors.add(0);
				}
				mNotification.increaseProgress();
			}
		});
	}

	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	//@Background(id="cancellable_task")
	public void checkForUpdates(
	) {
		String exit_message;

		try {
			// Lock mutex to avoid multiple update requests
			if (!mMutex.tryLock()) {
				mBus.post(new UpdateStopEvent(getBaseContext().getString(R.string.already_updating)));
				return;
			}

			// Check if we have at least one update source
			UpdaterOptions options = new UpdaterOptions(getBaseContext());
			if (!options.useAPKMirror() && !options.useGooglePlay() && !options.useAPKPure()) {
				mBus.post(new UpdateStopEvent(getBaseContext().getString(R.string.update_no_sources)));
				mMutex.unlock();
				return;
			}

			mAppState.clearUpdates();

			// Send start event
			mBus.post(new UpdateStartEvent());

			// Retrieve installed apps
			List<InstalledApp> installedApps = mInstalledAppUtil.getInstalledApps(getBaseContext());

			// Create the notification
			int multiplier = (options.useAPKMirror() ? 1 : 0) + (options.useAPKPure() ? 1 : 0)  + (options.useGooglePlay() ? 1 : 0) ;
			mNotification = new UpdaterNotification(getBaseContext(), installedApps.size() * multiplier);

			// Create an executor with 10 threads to perform the requests
			ExecutorService executor = Executors.newFixedThreadPool(10);
			final ConcurrentLinkedQueue<Integer> errors = new ConcurrentLinkedQueue<>();

			// Iterate through installed apps and check for updates
			for (final InstalledApp app: installedApps) {
				// Check if this app is on the ignore list
				if (options.getIgnoreList().contains(app.getPname())) {
					continue;
				}
				if (options.useAPKMirror()) {
					updateSource(executor, "APKMirror", app, errors);
				}
				if (options.useGooglePlay()) {
					updateSource(executor, "GooglePlay", app, errors);
				}
				if (options.useAPKPure()) {
					updateSource(executor, "APKPure", app, errors);
				}
			}

			// Wait until all threads are done
			executor.shutdown();
			while (!executor.isTerminated()) {
				Thread.sleep(1);
			}

			// If we got some errors
			if (errors.size() > 0) {
				exit_message = getBaseContext().getString(R.string.update_finished_with_errors);
				exit_message = exit_message.replace("$1", String.valueOf(errors.size()));
			} else {
				exit_message = getBaseContext().getString(R.string.update_finished);
			}

			// Notify that the update check is over
			mAppState.setUpdates(mUpdates);
			mNotification.finishNotification(mUpdates.size());
			mBus.post(new UpdateFinalProgressEvent(mUpdates));
			mBus.post(new UpdateStopEvent(exit_message));
			mMutex.unlock();
		} catch (Exception e) {
			exit_message = getBaseContext().getString(R.string.update_failed).replace("$1", e.getClass().getSimpleName());
			mBus.post(new UpdateStopEvent(exit_message));
			mNotification.failNotification();
			mMutex.unlock();
		}
	}

	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	@Override
	protected void onHandleIntent(
		Intent intent
	) {
		checkForUpdates();
	}

	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
