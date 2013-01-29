package com.moneydesktop.finance.shared;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.AsyncTask;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.moneydesktop.finance.ApplicationContext;
import com.moneydesktop.finance.R;
import com.moneydesktop.finance.animation.AnimationFactory.FlipDirection;
import com.moneydesktop.finance.animation.FlipXAnimation;
import com.moneydesktop.finance.data.Constant;
import com.moneydesktop.finance.database.Transactions;
import com.moneydesktop.finance.model.EventMessage;
import com.moneydesktop.finance.tablet.fragment.TransactionsDetailTabletFragment;

import de.greenrobot.event.EventBus;

@TargetApi(11)
public class TransactionController {
    
    public final String TAG = this.getClass().getSimpleName();
    
    private static final int MOVE_DURATION = 400;

    private RelativeLayout mContainer;
    private FrameLayout mDetail;
    private ImageView mFakeCell;
    private View mCellView;
    private int mCenterX, mCellX, mCellY, mHeight;
    private TransactionsDetailTabletFragment mDetailFragment;
    private float mContainerOffset;
    private boolean mShowing = false;
    
    private AnimatorListener mListenerShow = new AnimatorListener() {
        
        @Override
        public void onAnimationStart(Animator animation) {}
        
        @Override
        public void onAnimationRepeat(Animator animation) {}
        
        @Override
        public void onAnimationEnd(Animator animation) {

            Animation cellFlip = flipCell(true);
            Animation detailFlip = flipDetailView(true);
            detailFlip.setAnimationListener(mShowFinished);

            mFakeCell.startAnimation(cellFlip);
            
            mDetail.setVisibility(View.VISIBLE);
            mDetail.startAnimation(detailFlip);
        }
        
        @Override
        public void onAnimationCancel(Animator animation) {}
    };
    
    private AnimationListener mShowFinished = new AnimationListener() {
        
        @Override
        public void onAnimationStart(Animation animation) {}
        
        @Override
        public void onAnimationRepeat(Animation animation) {}
        
        @Override
        public void onAnimationEnd(Animation animation) {
            
            mFakeCell.setVisibility(View.INVISIBLE);
            
            mAnimating = false;
        }
    };
    
    private AnimationListener mListenerHide = new AnimationListener() {
        
        @Override
        public void onAnimationStart(Animation animation) {}
        
        @Override
        public void onAnimationRepeat(Animation animation) {}
        
        @Override
        public void onAnimationEnd(Animation animation) {

            mDetail.setVisibility(View.INVISIBLE);
            
            AnimatorSet set = moveCell(false);
            set.addListener(mHideFinished);
            
            set.start();
        }
    };
    
    private AnimatorListener mHideFinished = new AnimatorListener() {
        
        @Override
        public void onAnimationStart(Animator animation) {}
        
        @Override
        public void onAnimationRepeat(Animator animation) {}
        
        @Override
        public void onAnimationEnd(Animator animation) {
            
            mContainer.setVisibility(View.INVISIBLE);
            mFakeCell.setVisibility(View.INVISIBLE);
            mCellView.setVisibility(View.VISIBLE);
            
            mAnimating = false;
            mShowing = false;
            EventBus.getDefault().post(new EventMessage().new DataUpdateEvent());
        }
        
        @Override
        public void onAnimationCancel(Animator animation) {}
    };
    
    private Interpolator mInterpolator = new Interpolator() {
        
        @Override
        public float getInterpolation(float t) {
            if ((t) < (1 / 2.75)) {
                return (7.5625f * t * t);
            } else if (t < (2 / 2.75)) {
                return (7.5625f * (t -= (1.5f / 2.75f)) * t + .75f);
            } else if (t < (2.5 / 2.75)) {
                return (7.5625f * (t -= (2.25f / 2.75f)) * t + .9375f);
            } else {
                return (7.5625f * (t -= (2.625f / 2.75f)) * t + .984375f);
            }
        }
    };
    
    public TransactionController(RelativeLayout container, ImageView fakeCell, FrameLayout detail, float containerOffset) {
        mContainer = container;
        mFakeCell = fakeCell;
        mDetail = detail;
        mContainerOffset = containerOffset;
        
        setupView();
    }
    
    protected void addDetailFragment(FragmentManager manager) {
        
        setDetailFragment(TransactionsDetailTabletFragment.newInstance());

        FragmentTransaction ft = manager.beginTransaction();
        ft = manager.beginTransaction();
        ft.replace(R.id.detail_fragment, mDetailFragment);
        ft.commit();
    }
    
    public void setDetailFragment(TransactionsDetailTabletFragment fragment) {
        mDetailFragment = fragment;
    }
    
    public TransactionsDetailTabletFragment getDetailFragment() {
        return mDetailFragment;
    }

    private void setupView() {
        
        if (ApplicationContext.isLargeTablet()) {
            mDetail.getLayoutParams().width *= Constant.LARGE_TABLET_SCALE;
            mDetail.getLayoutParams().height *= Constant.LARGE_TABLET_SCALE;
        }
        
        mContainer.setOnClickListener(new OnClickListener() {
            
            @Override
            public void onClick(View v) {
                configureDetailView();
            }
        });
    }
    
    protected boolean mAnimating = false;
    
    public void showTransactionDetails(final View view, final int offset, final Transactions transaction) {
        
        if (mDetailFragment != null && !mShowing) {
            mShowing = true;
            mDetailFragment.updateTransaction(transaction);
        } else {
            return;
        }
        
        mCellView = view;
        
        new AsyncTask<Void, Void, Bitmap>() {

            @Override
            protected Bitmap doInBackground(Void... params) {

                final int[] location = new int[2];
                mCellView.getLocationOnScreen(location);
                
                mHeight = view.getHeight();
                
                mCellY = (location[1] - offset);
                mCellX = location[0];
                
                mCenterX = (int) (view.getWidth() / 2.0f);
                
                final Bitmap b = Bitmap.createBitmap(mCellView.getWidth(), mCellView.getHeight(), Bitmap.Config.ARGB_8888);                
                final Canvas c = new Canvas(b);
                
                mCellView.draw(c);
                
                return b;
            }

            @Override
            protected void onPostExecute(Bitmap b) {

                mFakeCell.setImageBitmap(b);
                mFakeCell.setX(mCellX);
                mFakeCell.setY(mCellY);
                
                configureDetailView();
            }

        }.execute();
    }
    
    public void configureDetailView() {
        
        if (mAnimating) {
            return;
        }
        
        if (mCellView.getVisibility() == View.VISIBLE) {

            mAnimating = true;
            
            final AnimatorSet set = moveCell(true);
            set.addListener(mListenerShow);
            
            mCellView.setVisibility(View.INVISIBLE);
            mFakeCell.setVisibility(View.VISIBLE);
            mContainer.setVisibility(View.VISIBLE);

            set.start();
            
        } else {

            mAnimating = true;
            
            if (mDetailFragment != null) {
                mDetailFragment.updateTransaction(null);
            }
            
            Animation cellFlip = flipCell(false);
            Animation detailFlip = flipDetailView(false);
            detailFlip.setAnimationListener(mListenerHide);
            
            mFakeCell.setVisibility(View.VISIBLE);
            mFakeCell.startAnimation(cellFlip);
            mDetail.startAnimation(detailFlip);
        }
    }
    
    private AnimatorSet moveCell(final boolean out) {
        
        final float startY = out ? mCellY : mDetail.getY() - mHeight + mContainerOffset;
        final float endY = out ? mDetail.getY() - mHeight + mContainerOffset : mCellY;
        
        final float startX = out ? mCellX : mDetail.getX();
        final float endX = out ? mDetail.getX() : mCellX;
        
        final float startAlpha = out ? 0 : 1;
        final float endAlpha = out ? 1 : 0;
        
        final ObjectAnimator moveY = ObjectAnimator.ofFloat(mFakeCell, "y", startY, endY);
        moveY.setInterpolator(new DecelerateInterpolator(3));
        moveY.setDuration(MOVE_DURATION);
        final ObjectAnimator moveX = ObjectAnimator.ofFloat(mFakeCell, "x", startX, endX);
        moveX.setInterpolator(new DecelerateInterpolator(3));
        moveY.setDuration(MOVE_DURATION);
        final ObjectAnimator fade = ObjectAnimator.ofFloat(mContainer, "alpha", startAlpha, endAlpha);
        fade.setDuration(out ? 300 : 400);
        
        final AnimatorSet set = new AnimatorSet();
        
        if (out) {
            set.play(moveX).with(moveY).after(fade);
        } else {
            set.play(moveX).with(moveY).before(fade);
        }
        
        return set;
    }
    
    private Animation flipCell(boolean up) {
        
        int duration = up ? 800 : 375;
        
        FlipDirection dir = up ? FlipDirection.TOP_BOTTOM : FlipDirection.BOTTOM_TOP;
        
        Animation flip = new FlipXAnimation(dir, (int) (mCenterX + mDetail.getX()), (int) mDetail.getY());
        flip.setDuration(duration);
        
        if (up) {
            flip.setInterpolator(mInterpolator);
        }
        
        return flip;
    }
    
    private Animation flipDetailView(boolean up) {

        int duration = up ? 800 : 375;
        
        FlipDirection dir = up ? FlipDirection.IN_TOP_BOTTOM : FlipDirection.OUT_BOTTOM_TOP;
        
        Animation flip = new FlipXAnimation(dir, (int) (mDetail.getWidth() / 2.0f), 0);
        flip.setDuration(duration);
        
        if (up) {
            flip.setInterpolator(mInterpolator);
        }
        
        return flip;
    }
    
    public void parentOnActivityResult(int requestCode, int resultCode, Intent data) {
        
        if (resultCode == Activity.RESULT_OK) {
            
            long categoryId = data.getLongExtra(Constant.EXTRA_CATEGORY_ID, -1);
            
            if (getDetailFragment() != null && categoryId != -1) {
                getDetailFragment().updateTransactionCategory(categoryId);
            }
            
            return;
        }
    }
    
    public interface ParentTransactionInterface {
        public void parentOnActivityResult(int requestCode, int resultCode, Intent data);
        public void showTransactionDetails(View view, int offset, Transactions transaction);
        public void setDetailFragment(TransactionsDetailTabletFragment fragment);
    }
}
