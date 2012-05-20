package net.simonvt.widget;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.ListView;

/**
 * @author Simon Vig Therkildsen <simonvt@gmail.com>
 */
public class CalendarListView extends ListView {

    private final Handler mHandler = new Handler();

    private SmoothScrollRunnable mCurrentSmoothScrollRunnable;

    public CalendarListView(Context context) {
        super(context);
        init(context);
    }

    public CalendarListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CalendarListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    void init(Context context) {

    }

    @Override
    public void smoothScrollBy(int distance, int duration) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
            super.smoothScrollBy(distance, duration);
        } else {
            if (null != mCurrentSmoothScrollRunnable) {
                mCurrentSmoothScrollRunnable.stop();
            }

            mCurrentSmoothScrollRunnable = new SmoothScrollRunnable(mHandler, getScrollY(), distance, duration);
            mHandler.post(mCurrentSmoothScrollRunnable);
        }
    }

    PositionScroller mPositionScroller;

    @Override
    public void smoothScrollToPositionFromTop(int position, int offset, int duration) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            super.smoothScrollToPositionFromTop(position, offset, duration);
        } else {
            if (mPositionScroller == null) mPositionScroller = new PositionScroller();

            mPositionScroller.startWithOffset(position, offset, duration);
        }
    }

    /**
     * Based on Chris Banes (https://github.com/chrisbanes) PullToRefreshListView.
     */
    final class SmoothScrollRunnable implements Runnable {

        static final int ANIMATION_FPS = 1000 / 60;

        private int mAnimationDuration;

        private final Interpolator mInterpolator;
        private final int mScrollFromY;
        private final int mScrollBy;
        private final int mScrollToY;
        private final Handler mHandler;

        private boolean mContinueRunning = true;
        private long mStartTime = -1;
        private int mCurrentY = -1;

        public SmoothScrollRunnable(Handler handler, int fromY, int by, int duration) {
            mHandler = handler;
            mScrollFromY = fromY;
            mScrollBy = by;
            mScrollToY = mScrollFromY + mScrollBy;
            mInterpolator = new AccelerateDecelerateInterpolator();
            mAnimationDuration = duration;
        }

        @Override
        public void run() {

            /**
             * Only set mStartTime if this is the first time we're starting,
             * else actually calculate the Y delta
             */
            if (mStartTime == -1) {
                mStartTime = System.currentTimeMillis();
            } else {

                /**
                 * We do do all calculations in long to reduce software float
                 * calculations. We use 1000 as it gives us good accuracy and
                 * small rounding errors
                 */
                long normalizedTime = (1000 * (System.currentTimeMillis() - mStartTime)) / mAnimationDuration;
                normalizedTime = Math.max(Math.min(normalizedTime, 1000), 0);

                final int deltaY = Math.round((mScrollFromY + mScrollBy)
                        * mInterpolator.getInterpolation(normalizedTime / 1000f));
                mCurrentY = mScrollFromY - deltaY;
                setHeaderScroll(mCurrentY);
            }

            // If we're not at the target Y, keep going...
            if (mContinueRunning && mScrollToY != mCurrentY) {
                mHandler.postDelayed(this, ANIMATION_FPS);
            }
        }

        public void stop() {
            mContinueRunning = false;
            mHandler.removeCallbacks(this);
        }
    }

    protected final void setHeaderScroll(int y) {
        scrollTo(0, y);
    }

    class PositionScroller implements Runnable {

        private static final int SCROLL_DURATION = 400;

        private static final int MOVE_DOWN_POS = 1;
        private static final int MOVE_UP_POS = 2;
        private static final int MOVE_DOWN_BOUND = 3;
        private static final int MOVE_UP_BOUND = 4;
        private static final int MOVE_OFFSET = 5;

        private int mMode;
        private int mTargetPos;
        private int mBoundPos;
        private int mLastSeenPos;
        private int mScrollDuration;
        private final int mExtraScroll;

        private int mOffsetFromTop;

        PositionScroller() {
            mExtraScroll = ViewConfiguration.get(getContext()).getScaledFadingEdgeLength();
        }

        void start(int position) {
            stop();

            final int firstPos = getFirstVisiblePosition();
            final int lastPos = firstPos + getChildCount() - 1;

            int viewTravelCount;
            if (position <= firstPos) {
                viewTravelCount = firstPos - position + 1;
                mMode = MOVE_UP_POS;
            } else if (position >= lastPos) {
                viewTravelCount = position - lastPos + 1;
                mMode = MOVE_DOWN_POS;
            } else {
                // Already on screen, nothing to do
                return;
            }

            if (viewTravelCount > 0) {
                mScrollDuration = SCROLL_DURATION / viewTravelCount;
            } else {
                mScrollDuration = SCROLL_DURATION;
            }
            mTargetPos = position;
            mBoundPos = INVALID_POSITION;
            mLastSeenPos = INVALID_POSITION;

            post(this);
        }

        void start(int position, int boundPosition) {
            stop();

            if (boundPosition == INVALID_POSITION) {
                start(position);
                return;
            }

            final int firstPos = getFirstVisiblePosition();
            final int lastPos = firstPos + getChildCount() - 1;

            int viewTravelCount;
            if (position <= firstPos) {
                final int boundPosFromLast = lastPos - boundPosition;
                if (boundPosFromLast < 1) {
                    // Moving would shift our bound position off the screen. Abort.
                    return;
                }

                final int posTravel = firstPos - position + 1;
                final int boundTravel = boundPosFromLast - 1;
                if (boundTravel < posTravel) {
                    viewTravelCount = boundTravel;
                    mMode = MOVE_UP_BOUND;
                } else {
                    viewTravelCount = posTravel;
                    mMode = MOVE_UP_POS;
                }
            } else if (position >= lastPos) {
                final int boundPosFromFirst = boundPosition - firstPos;
                if (boundPosFromFirst < 1) {
                    // Moving would shift our bound position off the screen. Abort.
                    return;
                }

                final int posTravel = position - lastPos + 1;
                final int boundTravel = boundPosFromFirst - 1;
                if (boundTravel < posTravel) {
                    viewTravelCount = boundTravel;
                    mMode = MOVE_DOWN_BOUND;
                } else {
                    viewTravelCount = posTravel;
                    mMode = MOVE_DOWN_POS;
                }
            } else {
                // Already on screen, nothing to do
                return;
            }

            if (viewTravelCount > 0) {
                mScrollDuration = SCROLL_DURATION / viewTravelCount;
            } else {
                mScrollDuration = SCROLL_DURATION;
            }
            mTargetPos = position;
            mBoundPos = boundPosition;
            mLastSeenPos = INVALID_POSITION;

            post(this);
        }

        void startWithOffset(int position, int offset) {
            startWithOffset(position, offset, SCROLL_DURATION);
        }

        void startWithOffset(int position, int offset, int duration) {
            stop();

            mTargetPos = position;
            mOffsetFromTop = offset;
            mBoundPos = INVALID_POSITION;
            mLastSeenPos = INVALID_POSITION;
            mMode = MOVE_OFFSET;

            final int firstPos = getFirstVisiblePosition();
            final int childCount = getChildCount();
            final int lastPos = firstPos + childCount - 1;

            int viewTravelCount;
            if (position < firstPos) {
                viewTravelCount = firstPos - position;
            } else if (position > lastPos) {
                viewTravelCount = position - lastPos;
            } else {
                // On-screen, just scroll.
                final int targetTop = getChildAt(position - firstPos).getTop();
                smoothScrollBy(targetTop - offset, duration);
                return;
            }

            // Estimate how many screens we should travel
            final float screenTravelCount = (float) viewTravelCount / childCount;
            mScrollDuration = screenTravelCount < 1 ? (int) (screenTravelCount * duration) :
                    (int) (duration / screenTravelCount);
            mLastSeenPos = INVALID_POSITION;

            post(this);
        }

        void stop() {
            removeCallbacks(this);
        }

        public void run() {
            //            if (mTouchMode != TOUCH_MODE_FLING && mLastSeenPos != INVALID_POSITION) {
            //                return;
            //            }

            final int listHeight = getHeight();
            final int firstPos = getFirstVisiblePosition();

            switch (mMode) {
                case MOVE_DOWN_POS: {
                    final int lastViewIndex = getChildCount() - 1;
                    final int lastPos = firstPos + lastViewIndex;

                    if (lastViewIndex < 0) {
                        return;
                    }

                    if (lastPos == mLastSeenPos) {
                        // No new views, let things keep going.
                        post(this);
                        return;
                    }

                    final View lastView = getChildAt(lastViewIndex);
                    final int lastViewHeight = lastView.getHeight();
                    final int lastViewTop = lastView.getTop();
                    final int lastViewPixelsShowing = listHeight - lastViewTop;
                    final int extraScroll = lastPos < getCount() - 1 ? mExtraScroll : getListPaddingBottom();

                    smoothScrollBy(lastViewHeight - lastViewPixelsShowing + extraScroll,
                            mScrollDuration);

                    mLastSeenPos = lastPos;
                    if (lastPos < mTargetPos) {
                        post(this);
                    }
                    break;
                }

                case MOVE_DOWN_BOUND: {
                    final int nextViewIndex = 1;
                    final int childCount = getChildCount();

                    if (firstPos == mBoundPos || childCount <= nextViewIndex
                            || firstPos + childCount >= getCount()) {
                        return;
                    }
                    final int nextPos = firstPos + nextViewIndex;

                    if (nextPos == mLastSeenPos) {
                        // No new views, let things keep going.
                        post(this);
                        return;
                    }

                    final View nextView = getChildAt(nextViewIndex);
                    final int nextViewHeight = nextView.getHeight();
                    final int nextViewTop = nextView.getTop();
                    final int extraScroll = mExtraScroll;
                    if (nextPos < mBoundPos) {
                        smoothScrollBy(Math.max(0, nextViewHeight + nextViewTop - extraScroll),
                                mScrollDuration);

                        mLastSeenPos = nextPos;

                        post(this);
                    } else {
                        if (nextViewTop > extraScroll) {
                            smoothScrollBy(nextViewTop - extraScroll, mScrollDuration);
                        }
                    }
                    break;
                }

                case MOVE_UP_POS: {
                    if (firstPos == mLastSeenPos) {
                        // No new views, let things keep going.
                        post(this);
                        return;
                    }

                    final View firstView = getChildAt(0);
                    if (firstView == null) {
                        return;
                    }
                    final int firstViewTop = firstView.getTop();
                    final int extraScroll = firstPos > 0 ? mExtraScroll : getListPaddingTop();

                    smoothScrollBy(firstViewTop - extraScroll, mScrollDuration);

                    mLastSeenPos = firstPos;

                    if (firstPos > mTargetPos) {
                        post(this);
                    }
                    break;
                }

                case MOVE_UP_BOUND: {
                    final int lastViewIndex = getChildCount() - 2;
                    if (lastViewIndex < 0) {
                        return;
                    }
                    final int lastPos = firstPos + lastViewIndex;

                    if (lastPos == mLastSeenPos) {
                        // No new views, let things keep going.
                        post(this);
                        return;
                    }

                    final View lastView = getChildAt(lastViewIndex);
                    final int lastViewHeight = lastView.getHeight();
                    final int lastViewTop = lastView.getTop();
                    final int lastViewPixelsShowing = listHeight - lastViewTop;
                    mLastSeenPos = lastPos;
                    if (lastPos > mBoundPos) {
                        smoothScrollBy(-(lastViewPixelsShowing - mExtraScroll), mScrollDuration);
                        post(this);
                    } else {
                        final int bottom = listHeight - mExtraScroll;
                        final int lastViewBottom = lastViewTop + lastViewHeight;
                        if (bottom > lastViewBottom) {
                            smoothScrollBy(-(bottom - lastViewBottom), mScrollDuration);
                        }
                    }
                    break;
                }

                case MOVE_OFFSET: {
                    if (mLastSeenPos == firstPos) {
                        // No new views, let things keep going.
                        post(this);
                        return;
                    }

                    mLastSeenPos = firstPos;

                    final int childCount = getChildCount();
                    final int position = mTargetPos;
                    final int lastPos = firstPos + childCount - 1;

                    int viewTravelCount = 0;
                    if (position < firstPos) {
                        viewTravelCount = firstPos - position + 1;
                    } else if (position > lastPos) {
                        viewTravelCount = position - lastPos;
                    }

                    // Estimate how many screens we should travel
                    final float screenTravelCount = (float) viewTravelCount / childCount;

                    final float modifier = Math.min(Math.abs(screenTravelCount), 1.f);
                    if (position < firstPos) {
                        smoothScrollBy((int) (-getHeight() * modifier), mScrollDuration);
                        post(this);
                    } else if (position > lastPos) {
                        smoothScrollBy((int) (getHeight() * modifier), mScrollDuration);
                        post(this);
                    } else {
                        // On-screen, just scroll.
                        final int targetTop = getChildAt(position - firstPos).getTop();
                        final int distance = targetTop - mOffsetFromTop;
                        smoothScrollBy(distance,
                                (int) (mScrollDuration * ((float) distance / getHeight())));
                    }
                    break;
                }

                default:
                    break;
            }
        }
    }
}
