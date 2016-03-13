package com.example.xyzreader.ui;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.LoaderManager;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v13.app.FragmentStatePagerAdapter;
import android.support.v4.app.SharedElementCallback;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;
import com.example.xyzreader.data.ItemsContract;

import java.util.List;
import java.util.Map;

/**
 * An activity representing a single Article detail screen, letting you swipe between articles.
 */
public class ArticleDetailActivity extends AppCompatActivity
        implements LoaderManager.LoaderCallbacks<Cursor> {
    private static final String TAG = "ArticleDetailActivity";

    private static final int SHARE_REQUEST_CODE = 1;
    private Cursor mCursor;
    private long mItemId;
    private long mStartId;
    private long mCurrentId;

    private ViewPager mPager;
    private MyPagerAdapter mPagerAdapter;

    private String mTitleText;
    private boolean mIsReturning;
    private ArticleDetailFragment mCurrentFragment;


    private final SharedElementCallback mCallback = new SharedElementCallback() {
        @Override
        public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {
//            Log.i(TAG, "05. statingId: " + mStartId + " currentId: " + mCurrentId);

            if (mIsReturning) {
                ImageView sharedElement = mCurrentFragment.getTransitionImage();
                if (sharedElement == null) {
                    names.clear();
                    sharedElements.clear();
                } else if (mStartId != mCurrentId) {
                    names.clear();
                    names.add(sharedElement.getTransitionName());
                    sharedElements.clear();
                    sharedElements.put(sharedElement.getTransitionName(), sharedElement);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        postponeEnterTransition();
        setEnterSharedElementCallback(mCallback);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
        setContentView(R.layout.activity_article_detail);

        getLoaderManager().initLoader(0, null, this);

        mPagerAdapter = new MyPagerAdapter(getFragmentManager());
        mPager = (ViewPager) findViewById(R.id.pager);
        mPager.setAdapter(mPagerAdapter);
        mPager.setPageMargin((int) TypedValue
                .applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics()));
        mPager.setPageMarginDrawable(new ColorDrawable(0x22000000));

        mPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                if (mCursor != null) {
                    mCursor.moveToPosition(position);
                    mCurrentId = position;
                    mTitleText = mCursor.getString(ArticleLoader.Query.TITLE);

//                    Log.i(TAG, "06. statingId: " + mStartId + " currentId: " + mCurrentId);
                }
            }
        });

        if (savedInstanceState == null) {
            if (getIntent() != null && getIntent().getData() != null) {
                mItemId = ItemsContract.Items.getItemId(getIntent().getData());
                mStartId = getIntent().getLongExtra(ArticleListActivity.EXTRA_STARTING_ID, 0);
                mCurrentId = getIntent().getLongExtra(ArticleListActivity.EXTRA_CURRENT_ID, 0);

//                Log.i(TAG, "07. statingId: " + mStartId + " currentId: " + mCurrentId + " itemId: " + mItemId);
            }
        } else {
            mCurrentId = savedInstanceState.getLong(ArticleListActivity.EXTRA_CURRENT_ID);
            mStartId = savedInstanceState.getLong(ArticleListActivity.EXTRA_STARTING_ID);

//            Log.i(TAG, "08. statingId: " + mStartId + " currentId: " + mCurrentId + " itemId: " + mItemId);
        }

        this.findViewById(R.id.share_fab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_text) + mTitleText);
                intent.setType("text/plain");
                startActivityForResult(Intent.createChooser(intent, getString(R.string.action_share)), SHARE_REQUEST_CODE);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SHARE_REQUEST_CODE) {
            View view = findViewById(R.id.share_fab);
            Snackbar snackbar = Snackbar
                    .make(view, getString(R.string.share_snack_text), Snackbar.LENGTH_SHORT);
            View snackView = snackbar.getView();

            snackbar.show();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

//        Log.i(TAG, "09. statingId: " + mStartId + " currentId: " + mCurrentId + " itemId: " + mItemId);

        outState.putLong(ArticleListActivity.EXTRA_CURRENT_ID, mCurrentId);
        outState.putLong(ArticleListActivity.EXTRA_STARTING_ID, mStartId);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return ArticleLoader.newAllArticlesInstance(this);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        mCursor = cursor;
        mPagerAdapter.notifyDataSetChanged();

//        Log.i(TAG, "10. itemId: " + mItemId);

        // Select the start ID
        if (mItemId > 0) {
            mCursor.moveToFirst();
            // TODO: optimize
            while (!mCursor.isAfterLast()) {
                if (mCursor.getLong(ArticleLoader.Query._ID) == mItemId) {
                    final int position = mCursor.getPosition();
                    mPager.setCurrentItem(position, false);
                    break;
                }
                mCursor.moveToNext();
            }
            mItemId = 0;
        }
    }

    @Override
    public void finishAfterTransition() {
        // Reverses the shared element transition
        mIsReturning = true;
        Intent intent = new Intent();

//        Log.i(TAG, "11.statingId: " + mStartId + " currentId: " + mCurrentId);

        intent.putExtra(ArticleListActivity.EXTRA_STARTING_ID, mStartId);
        intent.putExtra(ArticleListActivity.EXTRA_CURRENT_ID, mCurrentId);
        setResult(RESULT_OK, intent);
        super.finishAfterTransition();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        mCursor = null;
        mPagerAdapter.notifyDataSetChanged();
    }

    public void setPreDrawListener(final View view) {
        // https://plus.google.com/+AlexLockwood/posts/FJsp1N9XNLS
        final ViewTreeObserver observer = view.getViewTreeObserver();
        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                view.getViewTreeObserver().removeOnPreDrawListener(this);
                view.requestLayout();
                startPostponedEnterTransition();
                return true;
            }
        });
    }

    private class MyPagerAdapter extends FragmentStatePagerAdapter {
        public MyPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public void setPrimaryItem(ViewGroup container, int position, Object object) {
            super.setPrimaryItem(container, position, object);
            mCurrentFragment = (ArticleDetailFragment) object;
        }

        @Override
        public Fragment getItem(int position) {
            mCursor.moveToPosition(position);

//            Log.i(TAG, "12. currentId: " + mCurrentId);

            return ArticleDetailFragment.newInstance(position, mCursor.getLong(ArticleLoader.Query._ID));
        }

        @Override
        public int getCount() {
            return (mCursor != null) ? mCursor.getCount() : 0;
        }
    }
}
