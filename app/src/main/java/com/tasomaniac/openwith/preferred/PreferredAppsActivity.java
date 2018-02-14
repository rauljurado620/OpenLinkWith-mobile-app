package com.tasomaniac.openwith.preferred;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import butterknife.BindView;
import butterknife.ButterKnife;
import com.tasomaniac.openwith.R;
import com.tasomaniac.openwith.data.Analytics;
import com.tasomaniac.openwith.data.PreferredApp;
import com.tasomaniac.openwith.data.PreferredAppDao;
import com.tasomaniac.openwith.resolver.DisplayActivityInfo;
import com.tasomaniac.openwith.resolver.ItemClickListener;
import com.tasomaniac.openwith.resolver.ResolveListAdapter;
import com.tasomaniac.openwith.rx.SchedulingStrategy;
import dagger.android.support.DaggerAppCompatActivity;
import io.reactivex.Completable;
import io.reactivex.disposables.Disposable;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public class PreferredAppsActivity extends DaggerAppCompatActivity implements
        ItemClickListener,
        AppRemoveDialogFragment.Callbacks {

    @Inject Analytics analytics;
    @Inject ResolveListAdapter adapter;
    @Inject PreferredAppDao appDao;
    @Inject SchedulingStrategy scheduling;
    @Inject PackageManager packageManager;

    @BindView(R.id.recycler_view) RecyclerView recyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preferred_apps);
        ButterKnife.bind(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        //noinspection ConstantConditions
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        adapter.setItemClickListener(this);
        recyclerView.setAdapter(new PreferredAppsAdapter(adapter));

        Disposable disposable = appDao.allPreferredApps()
                .map(this::onLoadFinished)
                .compose(scheduling.forFlowable())
                .subscribe(adapter::setApplications);

        if (savedInstanceState == null) {
            analytics.sendScreenView("Preferred Apps");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        adapter.setItemClickListener(null);
    }

    @Override
    public void onItemClick(DisplayActivityInfo activityInfo) {
        AppRemoveDialogFragment.newInstance(activityInfo)
                .show(getSupportFragmentManager(), AppRemoveDialogFragment.class.getSimpleName());
    }

    public List<DisplayActivityInfo> onLoadFinished(List<PreferredApp> preferredApps) {
        List<DisplayActivityInfo> apps = new ArrayList<>(preferredApps.size());
        for (PreferredApp app : preferredApps) {
            Intent intent = new Intent();
            intent.setComponent(ComponentName.unflattenFromString(app.getComponent()));
            final ResolveInfo resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);

            if (resolveInfo != null) {
                CharSequence roLabel = resolveInfo.loadLabel(packageManager);
                final DisplayActivityInfo info = new DisplayActivityInfo(resolveInfo.activityInfo, roLabel, app.getHost());
                apps.add(info);
            }
        }
        return apps;
    }

    @Override
    public void onAppRemoved(DisplayActivityInfo info) {
        Completable.fromAction(() -> appDao.deleteHost(info.extendedInfo().toString()))
                .compose(scheduling.forCompletable())
                .subscribe();

        notifyItemRemoval(info);

        analytics.sendEvent(
                "Preferred",
                "Removed",
                info.displayLabel().toString()
        );
    }

    private void notifyItemRemoval(DisplayActivityInfo info) {
        recyclerView.postDelayed(() -> {
            adapter.remove(info);

            notifyHeaderChanged();
        }, 300);
    }

    private void notifyHeaderChanged() {
        recyclerView.postDelayed(() -> recyclerView.getAdapter().notifyItemChanged(0), 200);
    }

}
