package org.awesomeapp.messenger.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.design.widget.Snackbar;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import org.awesomeapp.messenger.ImApp;
import org.awesomeapp.messenger.MainActivity;
import org.awesomeapp.messenger.crypto.IOtrChatSession;
import org.awesomeapp.messenger.crypto.omemo.Omemo;
import org.awesomeapp.messenger.crypto.otr.OtrChatManager;
import org.awesomeapp.messenger.model.Contact;
import org.awesomeapp.messenger.model.ImErrorInfo;
import org.awesomeapp.messenger.plugin.xmpp.XmppAddress;
import org.awesomeapp.messenger.provider.Imps;
import org.awesomeapp.messenger.service.IChatSession;
import org.awesomeapp.messenger.service.IChatSessionManager;
import org.awesomeapp.messenger.service.IContactListManager;
import org.awesomeapp.messenger.service.IImConnection;
import org.awesomeapp.messenger.tasks.ChatSessionInitTask;
import org.awesomeapp.messenger.ui.legacy.DatabaseUtils;
import org.awesomeapp.messenger.ui.onboarding.OnboardingManager;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import im.zom.messenger.R;


public class ContactDisplayActivity extends BaseActivity {

    private int mContactId = -1;
    private String mNickname = null;
    private String mUsername = null;
    private long mProviderId = -1;
    private long mAccountId = -1;
    private IImConnection mConn;
    private String mRemoteFingerprint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.awesome_activity_contact);

        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mContactId = (int)getIntent().getLongExtra("contactId",-1);

        mNickname = getIntent().getStringExtra("nickname");
        mUsername = getIntent().getStringExtra("address");
        mProviderId = getIntent().getLongExtra("provider", -1);
        mAccountId = getIntent().getLongExtra("account", -1);
        mRemoteFingerprint = getIntent().getStringExtra("fingerprint");

        mConn = ((ImApp)getApplication()).getConnection(mProviderId,mAccountId);

        if (TextUtils.isEmpty(mNickname)) {
            mNickname = mUsername;
            mNickname = mNickname.split("@")[0].split("\\.")[0];
        }

        if (mRemoteFingerprint == null)
        {
            mRemoteFingerprint = OtrChatManager.getInstance().getRemoteKeyFingerprint(mUsername);
        }

        setTitle("");

        TextView tv = (TextView)findViewById(R.id.tvNickname);
        tv = (TextView)findViewById(R.id.tvNickname);
        tv.setText(mNickname);

        tv = (TextView)findViewById(R.id.tvUsername);
        tv.setText(mUsername);

        if (!TextUtils.isEmpty(mUsername)) {
            try {
                Drawable avatar = DatabaseUtils.getAvatarFromAddress(getContentResolver(), mUsername, ImApp.DEFAULT_AVATAR_WIDTH, ImApp.DEFAULT_AVATAR_HEIGHT, false);
                if (avatar != null) {
                    ImageView iv = (ImageView) findViewById(R.id.imageAvatar);
                    iv.setImageDrawable(avatar);
                    iv.setVisibility(View.VISIBLE);
                    findViewById(R.id.imageSpacer).setVisibility(View.GONE);
                }
            } catch (Exception e) {
            }
        }

        View btn = findViewById(R.id.btnStartChat);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                startChat();

            }
        });


        displayOtrFingerprints ();
        displayOmemoFingerprints ();

    }

    private void displayOtrFingerprints ()
    {

        try {

            TextView tv = (TextView)findViewById(R.id.tvFingerprint);

            Button btnVerify = (Button)findViewById(R.id.btnVerify);


            ArrayList<String> fingerprints = OtrChatManager.getInstance().getRemoteKeyFingerprints(mUsername);

            if (!fingerprints.contains(mRemoteFingerprint))
            {
                throw new Exception("Invalid key: " + mRemoteFingerprint);
            }



            if (mRemoteFingerprint != null) {

                if (!TextUtils.isEmpty(mRemoteFingerprint)) {
                    tv.setText(prettyPrintFingerprint(mRemoteFingerprint));

                    if (OtrChatManager.getInstance().isRemoteKeyVerified(mUsername, mRemoteFingerprint))
                        btnVerify.setVisibility(View.GONE);


                } else {
                    tv.setVisibility(View.GONE);
                    btnVerify.setVisibility(View.GONE);

                }
            }
            else {
                tv.setVisibility(View.GONE);
                btnVerify.setVisibility(View.GONE);
            }
        }
        catch (Exception e)
        {
            Log.e(ImApp.LOG_TAG,"error displaying contact",e);
        }
    }

    private void displayOmemoFingerprints ()
    {
        try {

            List omemoFps = mConn.getFingerprints(mUsername);

            if (omemoFps != null && omemoFps.size() > 0)
            {
                findViewById(R.id.listOmemo).setVisibility(View.VISIBLE);

                TextView tv = (TextView)findViewById(R.id.tvFingerprintOmemo);
                tv.setText((String)omemoFps.get(0));

            }

        }
        catch (Exception xe)
        {
            xe.printStackTrace();
        }
    }



    public void verifyClicked (View view)
    {
        verifyRemoteFingerprint();
        findViewById(R.id.btnVerify).setVisibility(View.GONE);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

       getMenuInflater().inflate(R.menu.menu_contact_detail, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.menu_verify_or_view:
                verifyRemoteFingerprint();
                return true;
            case R.id.menu_remove_contact:
                deleteContact();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private String prettyPrintFingerprint(String fingerprint) {
        StringBuffer spacedFingerprint = new StringBuffer();

        for (int i = 0; i + 8 <= fingerprint.length(); i += 8) {
            spacedFingerprint.append(fingerprint.subSequence(i, i + 8));
            spacedFingerprint.append(' ');
        }

        return spacedFingerprint.toString();
    }

    void deleteContact ()
    {
        new android.support.v7.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.menu_remove_contact))
                .setMessage(getString(R.string.confirm_delete_contact, mNickname))
                .setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        doDeleteContact();
                        finish();
                        startActivity(new Intent(ContactDisplayActivity.this, MainActivity.class));
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();

    }

    void doDeleteContact ()
    {
        try {

            IImConnection mConn;
            mConn = ((ImApp)getApplication()).getConnection(mProviderId, mAccountId);

            IContactListManager manager = mConn.getContactListManager();

            int res = manager.removeContact(mUsername);
            if (res != ImErrorInfo.NO_ERROR) {
                //mHandler.showAlert(R.string.error,
                //      ErrorResUtils.getErrorRes(getResources(), res, address));
            }

        }
        catch (RemoteException re)
        {

        }
    }

    public void startChat ()
    {
        boolean startCrypto = true;

        new ChatSessionInitTask(((ImApp)getApplication()),mProviderId, mAccountId, Imps.Contacts.TYPE_NORMAL, startCrypto)
        {
            @Override
            protected void onPostExecute(Long chatId) {

                if (chatId != -1) {
                    Intent intent = new Intent(ContactDisplayActivity.this, ConversationDetailActivity.class);
                    intent.putExtra("id", chatId);
                    startActivity(intent);
                }

                super.onPostExecute(chatId);
            }
        }.executeOnExecutor(ImApp.sThreadPoolExecutor,mUsername);

        finish();

    }

    private void initSmp(String question, String answer) {
        try {


                IChatSessionManager manager = mConn.getChatSessionManager();
                IChatSession session = manager.getChatSession(mUsername);
                IOtrChatSession iOtrSession = session.getDefaultOtrChatSession();
                iOtrSession.initSmpVerification(question, answer);


        } catch (RemoteException e) {
            Log.e(ImApp.LOG_TAG, "error init SMP", e);

        }
    }

    private void verifyRemoteFingerprint() {


        try {
            if (mConn != null) {
                IContactListManager listManager = mConn.getContactListManager();

                if (listManager != null)
                    listManager.approveSubscription(new Contact(new XmppAddress(mUsername), mNickname));

                IChatSessionManager manager = mConn.getChatSessionManager();

                if (manager != null) {
                    IChatSession session = manager.getChatSession(mUsername);

                    if (session != null) {
                        IOtrChatSession otrChatSession = session.getDefaultOtrChatSession();

                        if (otrChatSession != null) {
                            otrChatSession.verifyKey(otrChatSession.getRemoteUserId());
                            Snackbar.make(findViewById(R.id.main_content), getString(R.string.action_verified), Snackbar.LENGTH_LONG).show();
                        }
                    }
                }

            }

        } catch (RemoteException e) {
            Log.e(ImApp.LOG_TAG, "error init otr", e);

        }

    }
}
