package com.manuelmaly.hn;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.ViewSwitcher.ViewFactory;

import com.googlecode.androidannotations.annotations.AfterViews;
import com.googlecode.androidannotations.annotations.EActivity;
import com.googlecode.androidannotations.annotations.SystemService;
import com.googlecode.androidannotations.annotations.ViewById;
import com.manuelmaly.hn.SettingsActivity.FONTSIZE;
import com.manuelmaly.hn.model.HNComment;
import com.manuelmaly.hn.model.HNPost;
import com.manuelmaly.hn.model.HNPostComments;
import com.manuelmaly.hn.reuse.ImageViewFader;
import com.manuelmaly.hn.reuse.ViewRotator;
import com.manuelmaly.hn.task.HNPostCommentsTask;
import com.manuelmaly.hn.task.ITaskFinishedHandler;
import com.manuelmaly.hn.util.DisplayHelper;
import com.manuelmaly.hn.util.FileUtil;
import com.manuelmaly.hn.util.FontHelper;
import com.manuelmaly.hn.util.Run;

@EActivity(R.layout.comments_activity)
public class CommentsActivity extends Activity implements ITaskFinishedHandler<HNPostComments> {

    public static final String EXTRA_HNPOST = "HNPOST";

    @SystemService
    LayoutInflater mInflater;

    @ViewById(R.id.comments_list)
    ListView mCommentsList;

    @ViewById(R.id.actionbar)
    FrameLayout mActionbarLayout;

    @ViewById(R.id.actionbar_title_button)
    Button mActionbarTitleButton;

    @ViewById(R.id.actionbar_refresh)
    ImageView mRefreshImageView;

    @ViewById(R.id.actionbar_share)
    ImageView mShareImageView;

    @ViewById(R.id.actionbar_back)
    ImageView mBackImageView;

    HNPost mPost;
    HNPostComments mComments;
    CommentsAdapter mCommentsListAdapter;

    Integer mFontSizeMetadata;
    Integer mFontSizeText;
    
    int mCommentLevelIndentPx;

    @AfterViews
    public void init() {
        mPost = (HNPost) getIntent().getSerializableExtra(EXTRA_HNPOST);
        if (mPost == null || mPost.getPostID() == null) {
            finish();
            return;
        }

        mCommentLevelIndentPx = Math.min(DisplayHelper.getScreenHeight(this), DisplayHelper.getScreenWidth(this)) / 30;

        mComments = new HNPostComments();
        mCommentsListAdapter = new CommentsAdapter();
        mCommentsList.setAdapter(mCommentsListAdapter);

        mRefreshImageView.setImageDrawable(getResources().getDrawable(R.drawable.refresh));

        mActionbarTitleButton.setTypeface(FontHelper.getComfortaa(this, true));
        mActionbarTitleButton.setText(getString(R.string.comments));
        mActionbarTitleButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(CommentsActivity.this, ArticleReaderActivity_.class);
                i.putExtra(CommentsActivity.EXTRA_HNPOST, mPost);
                startActivity(i);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                finish();
            }
        });

        mActionbarLayout.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mCommentsList.smoothScrollToPosition(0);
            }
        });

        mBackImageView.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });

        mShareImageView.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_article_url));
                i.putExtra(Intent.EXTRA_TEXT, mPost.getURL());
                startActivity(Intent.createChooser(i, getString(R.string.share_article_url)));
            }
        });

        mRefreshImageView.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (HNPostCommentsTask.isRunning(mPost.getPostID()))
                    HNPostCommentsTask.stopCurrent(mPost.getPostID());
                else
                    startFeedLoading();
            }
        });

        loadIntermediateCommentsFromStore();
        startFeedLoading();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshFontSizes();
        mCommentsListAdapter.notifyDataSetChanged();
    }
    
    @Override
    public void onTaskFinished(int taskCode, TaskResultCode code, HNPostComments result) {
        if (code.equals(TaskResultCode.Success) && mCommentsListAdapter != null)
            showComments(result);
        updateStatusIndicatorOnLoadingFinished(code);
    }

    private void showComments(HNPostComments comments) {
        mComments = comments;
        mCommentsListAdapter.notifyDataSetChanged();
    }

    private void loadIntermediateCommentsFromStore() {
        long start = System.currentTimeMillis();
        HNPostComments commentsFromStore = FileUtil.getLastHNPostComments(mPost.getPostID());
        if (commentsFromStore == null) {
            // TODO: display "Loading..." instead
        } else
            showComments(commentsFromStore);
        Log.i("", "Loading intermediate feed took ms:" + (System.currentTimeMillis() - start));
    }

    private void updateStatusIndicatorOnLoadingStarted() {
        mRefreshImageView.setImageResource(R.drawable.refresh);
        ViewRotator.stopRotating(mRefreshImageView);
        ViewRotator.startRotating(mRefreshImageView);
    }

    private void updateStatusIndicatorOnLoadingFinished(TaskResultCode code) {
        ViewRotator.stopRotating(mRefreshImageView);
        if (code == TaskResultCode.Success) {
            ImageViewFader.startFadeOverToImage(mRefreshImageView, R.drawable.refresh_ok, 100, this);
            Run.delayed(new Runnable() {
                public void run() {
                    ImageViewFader.startFadeOverToImage(mRefreshImageView, R.drawable.refresh, 300,
                        CommentsActivity.this);
                }
            }, 2000);
        }
    }

    private void startFeedLoading() {
        HNPostCommentsTask.startOrReattach(this, this, mPost.getPostID(), 0);
        updateStatusIndicatorOnLoadingStarted();
    }
    
    private void refreshFontSizes() {
        FONTSIZE fontSize = SettingsActivity.getFontSize(this);
        switch (fontSize) {
            case FONTSIZE_SMALL:
                mFontSizeText = 14;
                mFontSizeMetadata = 12;
                break;
            case FONTSIZE_NORMAL:
                mFontSizeText = 16;
                mFontSizeMetadata = 14;
                break;
            case FONTSIZE_BIG:
                mFontSizeText = 20;
                mFontSizeMetadata = 18;
                break;
            default:
                break;
        }
    }

    class CommentsAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return mComments.getComments().size();
        }

        @Override
        public HNComment getItem(int position) {
            return mComments.getComments().get(position);
        }

        @Override
        public long getItemId(int position) {
            // Item ID not needed here:
            return 0;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = (FrameLayout) mInflater.inflate(R.layout.comments_list_item, null);
                CommentViewHolder holder = new CommentViewHolder();
                holder.textView = (TextView) convertView.findViewById(R.id.comments_list_item_text);
                holder.spacersContainer = (LinearLayout) convertView
                    .findViewById(R.id.comments_list_item_spacerscontainer);
                holder.authorView = (TextView) convertView.findViewById(R.id.comments_list_item_author);
                holder.timeAgoView = (TextView) convertView.findViewById(R.id.comments_list_item_timeago);
                holder.expandView = (ImageView) convertView.findViewById(R.id.comments_list_item_expand);
                convertView.setTag(holder);
            }
            HNComment comment = getItem(position);
            CommentViewHolder holder = (CommentViewHolder) convertView.getTag();
            holder.setComment(comment, mCommentLevelIndentPx, CommentsActivity.this, mFontSizeText, mFontSizeMetadata);
            holder.textView.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    if (getItem(position).getTreeNode().hasChildren()) {
                        mComments.toggleCommentExpanded(getItem(position));
                        mCommentsListAdapter.notifyDataSetChanged();
                    }
                }
            });
            return convertView;
        }

    }

    static class CommentViewHolder {
        TextView textView;
        TextView authorView;
        TextView timeAgoView;
        ImageView expandView;
        LinearLayout spacersContainer;

        public void setComment(HNComment comment, int commentLevelIndentPx, Context c, int commentTextSize, int metadataTextSize) {
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, commentTextSize);
            textView.setText(Html.fromHtml(comment.getText()));
            textView.setMovementMethod(LinkMovementMethod.getInstance());
            authorView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, metadataTextSize);
            authorView.setText(comment.getAuthor());
            timeAgoView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, metadataTextSize);
            timeAgoView.setText(", " + comment.getTimeAgo());
            expandView.setVisibility(comment.getTreeNode().isExpanded() ? View.INVISIBLE : View.VISIBLE);
            spacersContainer.removeAllViews();
            for (int i = 0; i < comment.getCommentLevel(); i++) {
                View spacer = new View(c);
                spacer.setLayoutParams(new android.widget.LinearLayout.LayoutParams(commentLevelIndentPx,
                    LayoutParams.FILL_PARENT));
                int spacerAlpha = Math.max(70 - i * 10, 10);
                spacer.setBackgroundColor(Color.argb(spacerAlpha, 0, 0, 0));
                spacersContainer.addView(spacer, i);
            }
        }
    }

    class RefreshButtonViewFactory implements ViewFactory {

        @Override
        public View makeView() {
            ImageView iView = new ImageView(CommentsActivity.this);
            iView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iView.setLayoutParams(new ImageSwitcher.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
            iView.setBackgroundColor(Color.TRANSPARENT);
            return iView;
        }

    }

}