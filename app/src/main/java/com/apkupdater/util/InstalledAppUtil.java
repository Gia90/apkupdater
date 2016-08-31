package com.apkupdater.util;

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.apkupdater.model.InstalledApp;

import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.EBean;

import java.util.ArrayList;
import java.util.List;

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

@EBean(scope = EBean.Scope.Singleton)
public class InstalledAppUtil {

	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	public List<InstalledApp> getInstalledApps(
		Context context
	) {
		PackageManager pm = context.getPackageManager();
		ArrayList<InstalledApp> items = new ArrayList<>();
		List<PackageInfo> apps = pm.getInstalledPackages(0);

		for (PackageInfo i : apps) {
			// Check it it's a system app
			if ((i.applicationInfo.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) {
				continue;
			}

			// Check if the app was installed by something (Google Play, Amazon, etc)
			String installer = pm.getInstallerPackageName(i.packageName);
			if (installer != null && !installer.isEmpty()) {
				//continue;
			}

			// Get the data and add it to the list
			InstalledApp app = new InstalledApp();
			app.setName(i.applicationInfo.loadLabel(pm).toString());
			app.setPname(i.packageName);
			app.setVersion(i.versionName);
			items.add(app);
		}

		return items;
	}

	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	@Background
	public void getInstalledAppsAsync(
		final Context context,
		final GenericCallback<List<InstalledApp>> callback
	) {
		List<InstalledApp> items = getInstalledApps(context);
		callback.onResult(items);
	}

	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////