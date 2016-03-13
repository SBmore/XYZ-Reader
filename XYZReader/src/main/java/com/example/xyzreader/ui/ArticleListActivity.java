package com.example.xyzreader.ui;

import android.app.ActivityOptions;
import android.app.LoaderManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.app.SharedElementCallback;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;
import com.example.xyzreader.data.ItemsContract;
import com.example.xyzreader.data.UpdaterService;

import java.util.List;
import java.util.Map;

/**
 * An activity representing a list of Articles. This activity has different presentations for
 * handset and tablet-size devices. On handsets, the activity presents a list of items, which when
 * touched, lead to a {@link ArticleDetailActivity} representing item details. On tablets, the
 * activity presents a grid of items as cards.
 */
public class ArticleListActivity extends AppCompatActivity implements
        LoaderManager.LoaderCallbacks<Cursor> {
    private static final String TAG = "ArticleDetailActivity";

    public static final String EXTRA_STARTING_ID = "start_id";
    public static final String EXTRA_CURRENT_ID = "current_id";

    private SwipeRefreshLayout mSwipeRefreshLayout;
    private RecyclerView mRecyclerView;
    private Bundle mReenterBundle;
    private Typeface robotoReg;
    private Typeface robotoThin;
    private Long mStartId;
    private boolean mIsRefreshing = false;
    private final BroadcastReceiver mRefreshingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (UpdaterService.BROADCAST_ACTION_STATE_CHANGE.equals(intent.getAction())) {
                mIsRefreshing = intent.getBooleanExtra(UpdaterService.EXTRA_REFRESHING, false);
                updateRefreshingUI();
            }
        }
    };

    // This technique to reverse shared element transitions even after swiping to a different fragment
    // was learned from this tutorial: http://stackoverflow.com/questions/27304834/viewpager-fragments-shared-element-transitions
    // and the linked example: https://github.com/alexjlockwood/activity-transitions
    private final SharedElementCallback mCallback = new SharedElementCallback() {
               // adjust the mapping of shared element names to Views so that the return shared transition
        // will match up even after changing fragments in ArticleDetailActivity
        @Override
        public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {
            if (mReenterBundle != null) {
                long startingId = mReenterBundle.getLong(EXTRA_STARTING_ID);
                long currentId = mReenterBundle.getLong(EXTRA_CURRENT_ID);

//                Log.i(TAG, "01. statingId: " + startingId + " currentId: " + currentId);

                if (startingId != currentId) {
                    // the user has swiped to a different fragment in ArticleDetailActivity so
                    // update the shared element
                    View sharedElement = mRecyclerView.findViewWithTag(getString(R.string.photo_tag) + currentId);
                    if (sharedElement != null) {
                        String transitionName = sharedElement.getTransitionName();
                        names.clear();
                        names.add(transitionName);
                        sharedElements.clear();
                        sharedElements.put(transitionName, sharedElement);
                    }
                }
                mReenterBundle = null;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_article_list);

        // set the callback created above so return shared transitions match up
        setExitSharedElementCallback(mCallback);

        mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refresh();
            }
        });

        // App uses fonts that are either the Android defaults, are complementary, and aren't otherwise distracting.
        robotoReg = Typeface.createFromAsset(getResources().getAssets(), "Roboto-Regular.ttf");
        robotoThin = Typeface.createFromAsset(getResources().getAssets(), "Roboto-Light.ttf");

        getLoaderManager().initLoader(0, null, this);

        if (savedInstanceState == null) {
            refresh();
        }
    }

    // prepare data when coming back from ArticleDetailActivity so the correct return transition
    // is performed
    @Override
    public void onActivityReenter(int requestCode, Intent data) {
        super.onActivityReenter(requestCode, data);
        mReenterBundle = new Bundle(data.getExtras());

        mStartId = mReenterBundle.getLong(EXTRA_STARTING_ID);
        long currentId = mReenterBundle.getLong(EXTRA_CURRENT_ID);

//        Log.i(TAG, "02. statingId: " + mStartId + " currentId: " + currentId);

        if (mStartId != currentId) {
            mRecyclerView.scrollToPosition((int) currentId);
        }

        postponeEnterTransition();
        mRecyclerView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                mRecyclerView.getViewTreeObserver().removeOnPreDrawListener(this);
                mRecyclerView.requestLayout();
                startPostponedEnterTransition();
                return true;
            }
        });
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return ArticleLoader.newAllArticlesInstance(this);
    }

    private void refresh() {
        startService(new Intent(this, UpdaterService.class));
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(mRefreshingReceiver,
                new IntentFilter(UpdaterService.BROADCAST_ACTION_STATE_CHANGE));
    }

    private void updateRefreshingUI() {
        mSwipeRefreshLayout.setRefreshing(mIsRefreshing);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        Adapter adapter;
        adapter = new Adapter(cursor);
        adapter.setHasStableIds(true);
        mRecyclerView.setAdapter(adapter);

        int columnCount = getResources().getInteger(R.integer.list_column_count);
        StaggeredGridLayoutManager sglm =
                new StaggeredGridLayoutManager(columnCount, StaggeredGridLayoutManager.VERTICAL);
        mRecyclerView.setLayoutManager(sglm);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mRecyclerView.setAdapter(null);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mRefreshingReceiver);
    }

    private class Adapter extends RecyclerView.Adapter<ViewHolder> {
        private final Cursor cursor;
        public Adapter(Cursor cursor) {
            this.cursor = cursor;
        }

        @Override
        public long getItemId(int position) {
            this.cursor.moveToPosition(position);
            return this.cursor.getLong(ArticleLoader.Query._ID);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.list_item_article, parent, false);

            final ViewHolder vh = new ViewHolder(view);

            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mStartId = (long) vh.getLayoutPosition();
                    Bundle bundle = ActivityOptions.makeSceneTransitionAnimation(
                            ArticleListActivity.this, view.findViewById(R.id.thumbnail),
                            view.findViewById(R.id.thumbnail).getTransitionName())
                            .toBundle();
                    Intent intent = new Intent(Intent.ACTION_VIEW,
                            ItemsContract.Items.buildItemUri(getItemId(vh.getAdapterPosition())));
                    // Add the ID to the intent for the shared transitions to identify if the user
                    // has swiped to a different fragment so the return animation works
                    intent.putExtra(EXTRA_STARTING_ID, mStartId);
                    intent.putExtra(EXTRA_CURRENT_ID, mStartId);

//                    Log.i(TAG, "03. statingId: " + mStartId);

                    startActivity(intent, bundle);
                }
            });
            return vh;
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            this.cursor.moveToPosition(position);

            holder.titleView.setText(this.cursor.getString(ArticleLoader.Query.TITLE));
            holder.titleView.setTypeface(robotoThin);
            holder.subtitleView.setText(
                    DateUtils.getRelativeTimeSpanString(
                            this.cursor.getLong(ArticleLoader.Query.PUBLISHED_DATE),
                            System.currentTimeMillis(), DateUtils.HOUR_IN_MILLIS,
                            DateUtils.FORMAT_ABBREV_ALL).toString()
                            + " by "
                            + this.cursor.getString(ArticleLoader.Query.AUTHOR));
            holder.subtitleView.setTypeface(robotoReg);

            // App uses images that are high quality, specific, and full bleed.
            if (getResources().getBoolean(R.bool.is_tablet)) {
                holder.thumbnailView.setImageUrl(
                        this.cursor.getString(ArticleLoader.Query.PHOTO_URL),
                        ImageLoaderHelper.getInstance(ArticleListActivity.this).getImageLoader());
            } else {
                holder.thumbnailView.setImageUrl(
                        this.cursor.getString(ArticleLoader.Query.THUMB_URL),
                        ImageLoaderHelper.getInstance(ArticleListActivity.this).getImageLoader());
            }

            holder.thumbnailView.setAspectRatio(this.cursor.getFloat(ArticleLoader.Query.ASPECT_RATIO));

            // Make the transition names on the thumbnail view unique
            String transitionName = getString(R.string.photo_transition) + position;

//            Log.i(TAG, "04. transitionName: " + transitionName);

            holder.thumbnailView.setTransitionName(transitionName);
            holder.thumbnailView.setTag(getString(R.string.photo_tag) + position);
        }

        @Override
        public int getItemCount() {
            return this.cursor.getCount();
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final DynamicHeightNetworkImageView thumbnailView;
        public final TextView titleView;
        public final TextView subtitleView;

        public ViewHolder(View view) {
            super(view);
            thumbnailView = (DynamicHeightNetworkImageView) view.findViewById(R.id.thumbnail);
            titleView = (TextView) view.findViewById(R.id.article_title);
            subtitleView = (TextView) view.findViewById(R.id.article_subtitle);
        }
    }
}
