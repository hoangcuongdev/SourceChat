package org.awesomeapp.messenger.ui;

import im.zom.messenger.R;

import org.awesomeapp.messenger.ImUrlActivity;
import org.awesomeapp.messenger.ui.widgets.MessageViewHolder;
import org.awesomeapp.messenger.util.SecureMediaStore;
import org.awesomeapp.messenger.ui.legacy.DatabaseUtils;
import org.awesomeapp.messenger.ImApp;
import org.awesomeapp.messenger.ui.legacy.Markup;
import org.awesomeapp.messenger.model.Presence;
import org.awesomeapp.messenger.plugin.xmpp.XmppAddress;
import org.awesomeapp.messenger.provider.Imps;
import org.awesomeapp.messenger.ui.widgets.ImageViewActivity;
import org.awesomeapp.messenger.ui.widgets.LetterAvatar;
import org.awesomeapp.messenger.ui.widgets.RoundedAvatarDrawable;
import org.awesomeapp.messenger.util.LinkifyHelper;
import org.ocpsoft.prettytime.PrettyTime;

import java.io.File;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.Browser;
import android.provider.MediaStore;
import android.support.v4.util.LruCache;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.ImageSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

public class MessageListItem extends FrameLayout {

    public enum DeliveryState {
        NEUTRAL, DELIVERED, UNDELIVERED
    }

    public enum EncryptionState {
        NONE, ENCRYPTED, ENCRYPTED_AND_VERIFIED

    }

    private String lastMessage = null;
    private Uri mediaUri = null;
    private String mimeType = null;

    private Context context;
    private boolean linkify = false;

    private MessageViewHolder mHolder = null;

    private static PrettyTime sPrettyTime = null;

    public MessageListItem(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;

        sPrettyTime = new PrettyTime(getCurrentLocale());


    }


    /**
     * This trickery is needed in order to have clickable links that open things
     * in a new {@code Task} rather than in ChatSecure's {@code Task.} Thanks to @commonsware
     * https://stackoverflow.com/a/11417498
     *
     */
    class NewTaskUrlSpan extends ClickableSpan {

        private String urlString;

        NewTaskUrlSpan(String urlString) {
            this.urlString = urlString;
        }

        @Override
        public void onClick(View widget) {
            Uri uri = Uri.parse(urlString);
            Context context = widget.getContext();
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.putExtra(Browser.EXTRA_APPLICATION_ID, context.getPackageName());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    class URLSpanConverter implements LinkifyHelper.SpanConverter<URLSpan, ClickableSpan> {
        @Override
        public NewTaskUrlSpan convert(URLSpan span) {
            return (new NewTaskUrlSpan(span.getURL()));
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
    }

    public void setLinkify(boolean linkify) {
        this.linkify = linkify;
    }

    public URLSpan[] getMessageLinks() {
        return mHolder.mTextViewForMessages.getUrls();
    }


    public String getLastMessage () {
        return lastMessage;
    }

    public void bindIncomingMessage(MessageViewHolder holder, int id, int messageType, String address, String nickname, final String mimeType, final String body, Date date, Markup smileyRes,
            boolean scrolling, EncryptionState encryption, boolean showContact, int presenceStatus) {

        mHolder = holder;
        mHolder.mTextViewForMessages.setVisibility(View.VISIBLE);
        mHolder.mMediaContainer.setVisibility(View.GONE);

        if (nickname == null)
            nickname = address;

        lastMessage = formatMessage(body);
        showAvatar(address, nickname, true, presenceStatus);

        mHolder.resetOnClickListenerMediaThumbnail();

        if( mimeType != null ) {

            Uri mediaUri = Uri.parse(body);
            lastMessage = "";

            if (mimeType.startsWith("image"))
            {
                mHolder.mTextViewForMessages.setVisibility(View.GONE);

                showMediaThumbnail(mimeType, mediaUri, id, mHolder);

                mHolder.mMediaContainer.setVisibility(View.VISIBLE);

            }

        }
        else if ((!TextUtils.isEmpty(lastMessage)) && (lastMessage.charAt(0) == '/'||lastMessage.charAt(0) == ':'))
        {
            boolean cmdSuccess = false;

            if (lastMessage.startsWith("/sticker:"))
            {
                String[] cmds = lastMessage.split(":");

                String mimeTypeSticker = "image/png";

                try {

                    String assetPath = cmds[1].split(" ")[0].toLowerCase();//just get up to any whitespace;

                    //make sure sticker exists
                    AssetFileDescriptor afd = getContext().getAssets().openFd(assetPath);
                    afd.getLength();
                    afd.close();

                    //now setup the new URI for loading local sticker asset
                    Uri mediaUri = Uri.parse("asset://localhost/" + assetPath);

                    //now load the thumbnail
                    cmdSuccess = showMediaThumbnail(mimeTypeSticker, mediaUri, id, mHolder);
                }
                catch (Exception e)
                {
                    Log.e(ImApp.LOG_TAG, "error loading sticker bitmap: " + cmds[1],e);
                    cmdSuccess = false;
                }

            }
            else if (lastMessage.startsWith(":"))
            {
                String[] cmds = lastMessage.split(":");

                String mimeTypeSticker = "image/png";
                try {
                    String[] stickerParts = cmds[1].split("-");
                    String stickerPath = "stickers/" + stickerParts[0].toLowerCase() + "/" + stickerParts[1].toLowerCase() + ".png";

                    //make sure sticker exists
                    AssetFileDescriptor afd = getContext().getAssets().openFd(stickerPath);
                    afd.getLength();
                    afd.close();

                    //now setup the new URI for loading local sticker asset
                    Uri mediaUri = Uri.parse("asset://localhost/" + stickerPath);

                    //now load the thumbnail
                    cmdSuccess = showMediaThumbnail(mimeTypeSticker, mediaUri, id, mHolder);
                } catch (Exception e) {
                    cmdSuccess = false;
                }
            }

            if (!cmdSuccess)
            {
                mHolder.mTextViewForMessages.setText(new SpannableString(lastMessage));
            }
            else
            {
                mHolder.mContainer.setBackgroundResource(android.R.color.transparent);
                lastMessage = "";
            }

        }


        if (lastMessage.length() > 0)
        {
            mHolder.mTextViewForMessages.setText(new SpannableString(lastMessage));
        }
        else
        {
            mHolder.mTextViewForMessages.setText(lastMessage);
        }


        if (date != null)
        {

            String contact = null;
            if (showContact) {
                String[] nickParts = nickname.split("/");
                contact = nickParts[nickParts.length-1];
            }

           CharSequence tsText = formatTimeStamp(date,messageType, null, encryption, contact);


         mHolder.mTextViewForTimestamp.setText(tsText);
         mHolder.mTextViewForTimestamp.setVisibility(View.VISIBLE);

        }
        else
        {

            mHolder.mTextViewForTimestamp.setText("");

        }
        if (linkify)
            LinkifyHelper.addLinks(mHolder.mTextViewForMessages, new URLSpanConverter());
        LinkifyHelper.addTorSafeLinks(mHolder.mTextViewForMessages);
    }

    private boolean showMediaThumbnail (String mimeType, Uri mediaUri, int id, MessageViewHolder holder)
    {
        this.mediaUri = mediaUri;
        this.mimeType = mimeType;

        /* Guess the MIME type in case we received a file that we can display or play*/
        if (TextUtils.isEmpty(mimeType) || mimeType.startsWith("application")) {
            String guessed = URLConnection.guessContentTypeFromName(mediaUri.toString());
            if (!TextUtils.isEmpty(guessed)) {
                if (TextUtils.equals(guessed, "video/3gpp"))
                    mimeType = "audio/3gpp";
                else
                    mimeType = guessed;
            }
        }

        holder.setOnClickListenerMediaThumbnail(mimeType, mediaUri);

        holder.mTextViewForMessages.setText(lastMessage);
        holder.mTextViewForMessages.setVisibility(View.GONE);

        if( mimeType.startsWith("image/") ) {
            setImageThumbnail( getContext().getContentResolver(), id, holder, mediaUri );
            holder.mMediaThumbnail.setBackgroundResource(android.R.color.transparent);

        }
        else
        {

            Glide.clear(holder.mMediaThumbnail);

            try {
                Glide.with(context)
                        .load(R.drawable.ic_file)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .into(holder.mMediaThumbnail);
            }
            catch (Exception e)
            {
                Log.e(ImApp.LOG_TAG,"unable to load thumbnail",e);
            }
            holder.mMediaThumbnail.setImageResource(R.drawable.ic_file); // generic file icon
            holder.mTextViewForMessages.setText(mediaUri.getLastPathSegment());
            holder.mTextViewForMessages.setVisibility(View.VISIBLE);

        }

        holder.mMediaContainer.setVisibility(View.VISIBLE);
        holder.mContainer.setBackgroundResource(android.R.color.transparent);

        return true;

    }
    protected String convertMediaUriToPath(Uri uri) {
        String path = null;

        String [] proj={MediaStore.Images.Media.DATA};
        Cursor cursor = getContext().getContentResolver().query(uri, proj,  null, null, null);
        if (cursor != null && (!cursor.isClosed()))
        {
            if (cursor.isBeforeFirst())
            {
                int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                cursor.moveToFirst();
                path = cursor.getString(column_index);
            }

            cursor.close();
        }

        return path;
    }

//    private AudioPlayer mAudioPlayer;

    public void onClickMediaIcon(String mimeType, Uri mediaUri) {


        if (mimeType.startsWith("image")) {

            if (mimeType.equals("image/jpeg")) {
                Intent intent = new Intent(context, ImageViewActivity.class);
                intent.putExtra(ImageViewActivity.URI, mediaUri.toString());
                intent.putExtra(ImageViewActivity.MIMETYPE, mimeType);
                intent.putExtra(ImageViewActivity.MIMETYPE, mimeType);

                context.startActivity(intent);
            }
        }
    }
    public static boolean isIntentAvailable(Context context, Intent intent) {
        final PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> list =
                packageManager.queryIntentActivities(intent,
                        PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
    }


    /**
     * @param contentResolver
     * @param id
     * @param aHolder
     * @param mediaUri
     */
    private void setImageThumbnail(final ContentResolver contentResolver, final int id, final MessageViewHolder aHolder, final Uri mediaUri) {

        //if the same URI, we don't need to reload
        if (aHolder.mMediaUri != null && aHolder.mMediaUri.getPath().equals(mediaUri.getPath()))
            return;

        // pair this holder to the uri. if the holder is recycled, the pairing is broken
        aHolder.mMediaUri = mediaUri;
        // if a content uri - already scanned

        Glide.clear(aHolder.mMediaThumbnail);
        if(SecureMediaStore.isVfsUri(mediaUri))
        {
            try {
                info.guardianproject.iocipher.File fileImage = new info.guardianproject.iocipher.File(mediaUri.getPath());
                if (fileImage.exists())
                {
                    Glide.with(context)
                            .load(new info.guardianproject.iocipher.FileInputStream(fileImage))
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .into(aHolder.mMediaThumbnail);
                }
            }
            catch (Exception e)
            {
                Log.w(ImApp.LOG_TAG,"unable to load thumbnail: " + mediaUri.toString());
            }
        }
        else if (mediaUri.getScheme().equals("asset"))
        {
            String assetPath = "file:///android_asset/" + mediaUri.getPath().substring(1);
            Glide.with(context)
                    .load(assetPath)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .into(aHolder.mMediaThumbnail);
        }
        else
        {
            Glide.with(context)
                    .load(mediaUri)
                    .into(aHolder.mMediaThumbnail);
        }


    }


    private String formatMessage (String body)
    {

        if (body != null)
            try {
                return (android.text.Html.fromHtml(body).toString()); //this happens on Xiaomi sometimes
            }
            catch (RuntimeException re){
                return "";
            }
        else
            return "";
    }

    public void bindOutgoingMessage(MessageViewHolder holder, int id, int messageType, String address, final String mimeType, final String body, Date date, Markup smileyRes, boolean scrolling,
            DeliveryState delivery, EncryptionState encryption) {

        mHolder = holder;

        mHolder.mTextViewForMessages.setVisibility(View.VISIBLE);
        mHolder.mMediaContainer.setVisibility(View.GONE);

        mHolder.resetOnClickListenerMediaThumbnail();

        lastMessage = body;

        if( mimeType != null ) {

            lastMessage = "";

            Uri mediaUri = Uri.parse( body ) ;

            if (mimeType.startsWith("image"))
            {
                mHolder.mTextViewForMessages.setVisibility(View.GONE);

                mHolder.mMediaContainer.setVisibility(View.VISIBLE);
                showMediaThumbnail(mimeType, mediaUri, id, mHolder);
            }

        }
        else if ((!TextUtils.isEmpty(lastMessage)) && (lastMessage.charAt(0) == '/'||lastMessage.charAt(0) == ':')) {
//            String cmd = lastMessage.toString().substring(1);
            boolean cmdSuccess = false;

            if (lastMessage.startsWith("/sticker:")) {
                String[] cmds = lastMessage.split(":");

                String mimeTypeSticker = "image/png";
                try {
                    //make sure sticker exists
                    AssetFileDescriptor afd = getContext().getAssets().openFd(cmds[1]);
                    afd.getLength();
                    afd.close();

                    //now setup the new URI for loading local sticker asset
                    Uri mediaUri = Uri.parse("asset://localhost/" + cmds[1].toLowerCase());

                    //now load the thumbnail
                    cmdSuccess = showMediaThumbnail(mimeTypeSticker, mediaUri, id, mHolder);
                } catch (Exception e) {
                    cmdSuccess = false;
                }

            }
            else if (lastMessage.startsWith(":"))
            {
                String[] cmds = lastMessage.split(":");

                String mimeTypeSticker = "image/png";
                try {
                    String[] stickerParts = cmds[1].split("-");
                    String stickerPath = "stickers/" + stickerParts[0].toLowerCase() + "/" + stickerParts[1].toLowerCase() + ".png";

                    //make sure sticker exists
                    AssetFileDescriptor afd = getContext().getAssets().openFd(stickerPath);
                    afd.getLength();
                    afd.close();

                    //now setup the new URI for loading local sticker asset
                    Uri mediaUri = Uri.parse("asset://localhost/" + stickerPath);

                    //now load the thumbnail
                    cmdSuccess = showMediaThumbnail(mimeTypeSticker, mediaUri, id, mHolder);
                } catch (Exception e) {
                    cmdSuccess = false;
                }
            }

            if (!cmdSuccess)
            {
                mHolder.mTextViewForMessages.setText(new SpannableString(lastMessage));
            }
            else
            {
                holder.mContainer.setBackgroundResource(android.R.color.transparent);
                lastMessage = "";
            }

        }
        else {
            mHolder.mTextViewForMessages.setText(new SpannableString(lastMessage));
        }

        if (date != null)
        {

            CharSequence tsText = formatTimeStamp(date,messageType, delivery, encryption, null);

            mHolder.mTextViewForTimestamp.setText(tsText);

            mHolder.mTextViewForTimestamp.setVisibility(View.VISIBLE);

        }
        else
        {
            mHolder.mTextViewForTimestamp.setText("");

        }
        if (linkify)
            LinkifyHelper.addLinks(mHolder.mTextViewForMessages, new URLSpanConverter());
        LinkifyHelper.addTorSafeLinks(mHolder.mTextViewForMessages);
    }

    private void showAvatar (String address, String nickname, boolean isLeft, int presenceStatus)
    {
        if (mHolder.mAvatar == null)
            return;

        mHolder.mAvatar.setVisibility(View.GONE);

        if (address != null && isLeft)
        {

            RoundedAvatarDrawable avatar = null;

            try { avatar = (RoundedAvatarDrawable)DatabaseUtils.getAvatarFromAddress(this.getContext().getContentResolver(), XmppAddress.stripResource(address), ImApp.SMALL_AVATAR_WIDTH, ImApp.SMALL_AVATAR_HEIGHT);}
            catch (Exception e){}

            if (avatar != null)
            {
                mHolder.mAvatar.setVisibility(View.VISIBLE);
                mHolder.mAvatar.setImageDrawable(avatar);
            }
            else
            {
                int padding = 24;

                if (nickname.length() > 0) {
                    LetterAvatar lavatar = new LetterAvatar(getContext(), nickname, padding);

                    mHolder.mAvatar.setVisibility(View.VISIBLE);
                    mHolder.mAvatar.setImageDrawable(lavatar);
                }
            }
        }
    }

    public void bindPresenceMessage(MessageViewHolder holder, String contact, int type, Date date, boolean isGroupChat, boolean scrolling) {

        mHolder = holder;
        mHolder.mContainer.setBackgroundResource(android.R.color.transparent);
        mHolder.mTextViewForMessages.setVisibility(View.GONE);
        mHolder.mTextViewForTimestamp.setVisibility(View.VISIBLE);

        CharSequence message = formatPresenceUpdates(contact, type, date, isGroupChat, scrolling);
        mHolder.mTextViewForTimestamp.setText(message);


    }

    public void bindErrorMessage(int errCode) {

        mHolder = (MessageViewHolder)getTag();

        mHolder.mTextViewForMessages.setText(R.string.msg_sent_failed);
        mHolder.mTextViewForMessages.setTextColor(getResources().getColor(R.color.error));

    }

    private SpannableString formatTimeStamp(Date date, int messageType, MessageListItem.DeliveryState delivery, EncryptionState encryptionState, String nickname) {


        StringBuilder deliveryText = new StringBuilder();

        if (nickname != null)
        {
            deliveryText.append(nickname);
            deliveryText.append(' ');
        }

        deliveryText.append(sPrettyTime.format(date));

        SpannableString spanText = null;

        spanText = new SpannableString(deliveryText.toString());

        if (delivery != null)
        {
            deliveryText.append(' ');
            //this is for delivery

            if (messageType == Imps.MessageType.POSTPONED)
            {
                //do nothing
                deliveryText.append("X");
                spanText = new SpannableString(deliveryText.toString());
                int len = spanText.length();
                spanText.setSpan(new ImageSpan(getContext(), R.drawable.ic_message_wait_grey), len-1, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            else if (delivery == DeliveryState.DELIVERED) {

                if (encryptionState == EncryptionState.ENCRYPTED || encryptionState == EncryptionState.ENCRYPTED_AND_VERIFIED)
                {
                    deliveryText.append("XX");
                    spanText = new SpannableString(deliveryText.toString());
                    int len = spanText.length();

                    spanText.setSpan(new ImageSpan(getContext(), R.drawable.ic_delivered_grey), len - 2, len - 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    spanText.setSpan(new ImageSpan(getContext(), R.drawable.ic_encrypted_grey), len - 1, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                } else{
                    deliveryText.append("X");
                    spanText = new SpannableString(deliveryText.toString());
                    int len = spanText.length();
                    spanText.setSpan(new ImageSpan(getContext(), R.drawable.ic_delivered_grey), len-1, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                }



            } else if (delivery == DeliveryState.UNDELIVERED) {

                if (encryptionState == EncryptionState.ENCRYPTED||encryptionState == EncryptionState.ENCRYPTED_AND_VERIFIED) {
                    deliveryText.append("XX");
                    spanText = new SpannableString(deliveryText.toString());
                    int len = spanText.length();
                    spanText.setSpan(new ImageSpan(getContext(), R.drawable.ic_sent_grey),len-2,len-1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    spanText.setSpan(new ImageSpan(getContext(), R.drawable.ic_encrypted_grey), len - 1, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                else
                {
                    deliveryText.append("XX");
                    spanText = new SpannableString(deliveryText.toString());
                    int len = spanText.length();
                    spanText.setSpan(new ImageSpan(getContext(), R.drawable.ic_sent_grey),len-1,len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                }


            }
            else if (delivery == DeliveryState.NEUTRAL) {

                if (encryptionState == EncryptionState.ENCRYPTED||encryptionState == EncryptionState.ENCRYPTED_AND_VERIFIED) {
                    deliveryText.append("XX");
                    spanText = new SpannableString(deliveryText.toString());
                    int len = spanText.length();
                    spanText.setSpan(new ImageSpan(getContext(), R.drawable.ic_sent_grey),len-2,len-1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    spanText.setSpan(new ImageSpan(getContext(), R.drawable.ic_encrypted_grey), len - 1, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                else
                {
                    deliveryText.append("X");
                    spanText = new SpannableString(deliveryText.toString());
                    int len = spanText.length();
                    spanText.setSpan(new ImageSpan(getContext(), R.drawable.ic_sent_grey),len-1,len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                }

            }


        }
        else
        {
            if (encryptionState == EncryptionState.ENCRYPTED||encryptionState == EncryptionState.ENCRYPTED_AND_VERIFIED)
            {
                deliveryText.append('X');
                spanText = new SpannableString(deliveryText.toString());
                int len = spanText.length();

                if (encryptionState == EncryptionState.ENCRYPTED||encryptionState == EncryptionState.ENCRYPTED_AND_VERIFIED)
                    spanText.setSpan(new ImageSpan(getContext(), R.drawable.ic_encrypted_grey), len-1, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            else if (messageType == Imps.MessageType.OUTGOING)
            {
                //do nothing
                deliveryText.append("X");
                spanText = new SpannableString(deliveryText.toString());
                int len = spanText.length();
                spanText.setSpan(new ImageSpan(getContext(), R.drawable.ic_message_wait_grey), len-1, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        return spanText;
    }

    private CharSequence formatPresenceUpdates(String contact, int type, Date date, boolean isGroupChat,
            boolean scrolling) {
        String body;

        Resources resources =getResources();

        switch (type) {
        case Imps.MessageType.PRESENCE_AVAILABLE:
            body = resources.getString(isGroupChat ? R.string.contact_joined
                                                   : R.string.contact_online, contact);
            break;

        case Imps.MessageType.PRESENCE_AWAY:
            body = resources.getString(R.string.contact_away, contact);
            break;

        case Imps.MessageType.PRESENCE_DND:
            body = resources.getString(R.string.contact_busy, contact);
            break;

        case Imps.MessageType.PRESENCE_UNAVAILABLE:
            body = resources.getString(isGroupChat ? R.string.contact_left
                                                   : R.string.contact_offline, contact);
            break;

        default:
            return null;
        }

        body += " - ";
        body += formatTimeStamp(date,type, null, EncryptionState.NONE, null);

        if (scrolling) {
            return body;
        } else {
            SpannableString spanText = new SpannableString(body);
            int len = spanText.length();
            spanText.setSpan(new StyleSpan(Typeface.ITALIC), 0, len,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            spanText.setSpan(new RelativeSizeSpan((float) 0.8), 0, len,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            return spanText;
        }
    }
    @TargetApi(Build.VERSION_CODES.N)
    public Locale getCurrentLocale(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
            return getResources().getConfiguration().getLocales().get(0);
        } else{
            //noinspection deprecation
            return getResources().getConfiguration().locale;
        }
    }
}
