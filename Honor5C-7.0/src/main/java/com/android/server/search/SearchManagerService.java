package com.android.server.search;

import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.AppGlobals;
import android.app.ISearchManager.Stub;
import android.app.SearchableInfo;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.IPackageManager;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings.Secure;
import android.util.Log;
import android.util.SparseArray;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.content.PackageMonitor;
import com.android.internal.os.BackgroundThread;
import com.android.internal.util.IndentingPrintWriter;
import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.statusbar.StatusBarManagerInternal;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.List;

public class SearchManagerService extends Stub {
    private static final String TAG = "SearchManagerService";
    private final Context mContext;
    final Handler mHandler;
    @GuardedBy("mSearchables")
    private final SparseArray<Searchables> mSearchables;

    class GlobalSearchProviderObserver extends ContentObserver {
        private final ContentResolver mResolver;

        public GlobalSearchProviderObserver(ContentResolver resolver) {
            super(null);
            this.mResolver = resolver;
            this.mResolver.registerContentObserver(Secure.getUriFor("search_global_search_activity"), false, this);
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public void onChange(boolean selfChange) {
            synchronized (SearchManagerService.this.mSearchables) {
                int i = 0;
                while (true) {
                    if (i < SearchManagerService.this.mSearchables.size()) {
                        ((Searchables) SearchManagerService.this.mSearchables.valueAt(i)).updateSearchableList();
                        i++;
                    }
                }
            }
            Intent intent = new Intent("android.search.action.GLOBAL_SEARCH_ACTIVITY_CHANGED");
            intent.addFlags(536870912);
            SearchManagerService.this.mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
        }
    }

    public static class Lifecycle extends SystemService {
        private SearchManagerService mService;

        /* renamed from: com.android.server.search.SearchManagerService.Lifecycle.1 */
        class AnonymousClass1 implements Runnable {
            final /* synthetic */ int val$userId;

            AnonymousClass1(int val$userId) {
                this.val$userId = val$userId;
            }

            public void run() {
                Lifecycle.this.mService.onUnlockUser(this.val$userId);
            }
        }

        public Lifecycle(Context context) {
            super(context);
        }

        public void onStart() {
            this.mService = new SearchManagerService(getContext());
            publishBinderService("search", this.mService);
        }

        public void onUnlockUser(int userId) {
            this.mService.mHandler.post(new AnonymousClass1(userId));
        }

        public void onCleanupUser(int userHandle) {
            this.mService.onCleanupUser(userHandle);
        }
    }

    class MyPackageMonitor extends PackageMonitor {
        MyPackageMonitor() {
        }

        public void onSomePackagesChanged() {
            updateSearchables();
        }

        public void onPackageModified(String pkg) {
            updateSearchables();
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        private void updateSearchables() {
            int changingUserId = getChangingUserId();
            synchronized (SearchManagerService.this.mSearchables) {
                int i = 0;
                while (true) {
                    if (i >= SearchManagerService.this.mSearchables.size()) {
                        break;
                    } else if (changingUserId == SearchManagerService.this.mSearchables.keyAt(i)) {
                        break;
                    } else {
                        i++;
                    }
                }
            }
            Intent intent = new Intent("android.search.action.SEARCHABLES_CHANGED");
            intent.addFlags(603979776);
            SearchManagerService.this.mContext.sendBroadcastAsUser(intent, new UserHandle(changingUserId));
        }
    }

    public SearchManagerService(Context context) {
        this.mSearchables = new SparseArray();
        this.mContext = context;
        new MyPackageMonitor().register(context, null, UserHandle.ALL, true);
        GlobalSearchProviderObserver globalSearchProviderObserver = new GlobalSearchProviderObserver(context.getContentResolver());
        this.mHandler = BackgroundThread.getHandler();
    }

    private Searchables getSearchables(int userId) {
        return getSearchables(userId, false);
    }

    private Searchables getSearchables(int userId, boolean forceUpdate) {
        long token = Binder.clearCallingIdentity();
        try {
            UserManager um = (UserManager) this.mContext.getSystemService(UserManager.class);
            if (um.getUserInfo(userId) == null) {
                throw new IllegalStateException("User " + userId + " doesn't exist");
            } else if (um.isUserUnlockingOrUnlocked(userId)) {
                Searchables searchables;
                synchronized (this.mSearchables) {
                    searchables = (Searchables) this.mSearchables.get(userId);
                    if (searchables == null) {
                        searchables = new Searchables(this.mContext, userId);
                        searchables.updateSearchableList();
                        this.mSearchables.append(userId, searchables);
                    } else if (forceUpdate) {
                        searchables.updateSearchableList();
                    }
                }
                return searchables;
            } else {
                throw new IllegalStateException("User " + userId + " isn't unlocked");
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private void onUnlockUser(int userId) {
        getSearchables(userId, true);
    }

    private void onCleanupUser(int userId) {
        synchronized (this.mSearchables) {
            this.mSearchables.remove(userId);
        }
    }

    public SearchableInfo getSearchableInfo(ComponentName launchActivity) {
        if (launchActivity != null) {
            return getSearchables(UserHandle.getCallingUserId()).getSearchableInfo(launchActivity);
        }
        Log.e(TAG, "getSearchableInfo(), activity == null");
        return null;
    }

    public List<SearchableInfo> getSearchablesInGlobalSearch() {
        return getSearchables(UserHandle.getCallingUserId()).getSearchablesInGlobalSearchList();
    }

    public List<SearchableInfo> getOnlineSearchablesInGlobalSearch() {
        return getSearchables(UserHandle.getCallingUserId()).getOnlineSearchablesInGlobalSearchList();
    }

    public List<ResolveInfo> getGlobalSearchActivities() {
        return getSearchables(UserHandle.getCallingUserId()).getGlobalSearchActivities();
    }

    public ComponentName getGlobalSearchActivity() {
        return getSearchables(UserHandle.getCallingUserId()).getGlobalSearchActivity();
    }

    public ComponentName getWebSearchActivity() {
        return getSearchables(UserHandle.getCallingUserId()).getWebSearchActivity();
    }

    public void launchAssist(Bundle args) {
        if (args != null) {
            StatusBarManagerInternal statusBarManager = (StatusBarManagerInternal) LocalServices.getService(StatusBarManagerInternal.class);
            if (statusBarManager != null) {
                statusBarManager.startAssist(args);
            }
        }
    }

    private ComponentName getLegacyAssistComponent(int userHandle) {
        try {
            userHandle = ActivityManager.handleIncomingUser(Binder.getCallingPid(), Binder.getCallingUid(), userHandle, true, false, "getLegacyAssistComponent", null);
            IPackageManager pm = AppGlobals.getPackageManager();
            Intent assistIntent = new Intent("android.intent.action.ASSIST");
            ResolveInfo info = pm.resolveIntent(assistIntent, assistIntent.resolveTypeIfNeeded(this.mContext.getContentResolver()), DumpState.DUMP_INSTALLS, userHandle);
            if (info != null) {
                return new ComponentName(info.activityInfo.applicationInfo.packageName, info.activityInfo.name);
            }
        } catch (RemoteException re) {
            Log.e(TAG, "RemoteException in getLegacyAssistComponent: " + re);
        } catch (Exception e) {
            Log.e(TAG, "Exception in getLegacyAssistComponent: " + e);
        }
        return null;
    }

    public boolean launchLegacyAssist(String hint, int userHandle, Bundle args) {
        boolean z = false;
        ComponentName comp = getLegacyAssistComponent(userHandle);
        if (comp == null) {
            return z;
        }
        long ident = Binder.clearCallingIdentity();
        try {
            Intent intent = new Intent("android.intent.action.ASSIST");
            intent.setComponent(comp);
            z = ActivityManagerNative.getDefault().launchAssistIntent(intent, 0, hint, userHandle, args);
            return z;
        } catch (RemoteException e) {
            return true;
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        this.mContext.enforceCallingOrSelfPermission("android.permission.DUMP", TAG);
        IndentingPrintWriter ipw = new IndentingPrintWriter(pw, "  ");
        synchronized (this.mSearchables) {
            int i = 0;
            while (true) {
                if (i < this.mSearchables.size()) {
                    ipw.print("\nUser: ");
                    ipw.println(this.mSearchables.keyAt(i));
                    ipw.increaseIndent();
                    ((Searchables) this.mSearchables.valueAt(i)).dump(fd, ipw, args);
                    ipw.decreaseIndent();
                    i++;
                }
            }
        }
    }
}
