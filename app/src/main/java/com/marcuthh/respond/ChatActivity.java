package com.marcuthh.respond;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.support.v7.widget.Toolbar;
import android.widget.Toast;

import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;

public class ChatActivity extends AppCompatActivity {

    //region ////GLOBALS////
    ////CONSTANTS////
    private static final String TAG = "ChatActivity";

    private static final String LOC_CHATS = "chats";
    private static final String LOC_MESSAGES = "messages";
    private static final String LOC_USERS = "users";
    private static final String LOC_EVENTS = "events";
    private static final String LOC_INVITES = "eventInvites";
    ////CONSTANTS////

    //Firebase components
    private FirebaseAnalytics mFirebaseAnalytics;
    private FirebaseAuth mAuth;
    private FirebaseDatabase mDatabase;
    private DatabaseReference mDbRefRoot;
    private DatabaseReference mDbRefChats;
    private DatabaseReference mDbRefMessages;
    private DatabaseReference mDbRefUsers;
    private DatabaseReference mDbRefEvents;
    //file transfer to database
    private StorageReference mStorageRef;
    //interfaces
    private FirebaseAuth.AuthStateListener mAuthListener;
    //Firebase components

    private String mCurrentUserId;
    private String mChatId;

    FloatingActionButton btnSendMsg;
    EditText input;
    Toolbar toolbar;
    private RecyclerView message_list;

    String defaultResponse;
    //endregion

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        //get Firebase connections
        mAuth = FirebaseAuth.getInstance();
        mCurrentUserId = mAuth.getCurrentUser().getUid();
        mDatabase = FirebaseDatabase.getInstance();
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        //set up reference to Firebase file transfer
        //and check permissions to access files
        mStorageRef = FirebaseStorage.getInstance().getReference();

        //setup auth processes and callback behaviour
        mAuthListener = getAuthListener();

        input = (EditText) findViewById(R.id.input);
        btnSendMsg = (FloatingActionButton) findViewById(R.id.btnSendMsg);
        input.addTextChangedListener(onTextChanged());
        btnSendMsg.setOnClickListener(sendChatMessage());

        message_list = (RecyclerView) findViewById(R.id.message_list);
        message_list.setHasFixedSize(true);
        message_list.setLayoutManager(new LinearLayoutManager(this));

        mChatId = getIntent().getStringExtra("CHAT_ID");

        mDbRefRoot = mDatabase.getReference();
//        mDbRefRoot.keepSynced(true);
        mDbRefChats = mDatabase.getReference(LOC_CHATS);
//        mDbRefChats.keepSynced(true);
        mDbRefChats.addValueEventListener(onChatChangeListener());
        mDbRefMessages = mDatabase.getReference(LOC_MESSAGES);
//        mDbRefMessages.keepSynced(true);
        mDbRefMessages.addValueEventListener(onMessagesChangeListener());
        mDbRefUsers = mDatabase.getReference(LOC_USERS);
//        mDbRefUsers.keepSynced(true);
        mDbRefEvents = mDatabase.getReference(LOC_EVENTS);
//        mDbRefEvents.keepSynced(true);

        getUserDefaultResponse();
    }

    @Override
    public void onStart() {
        super.onStart();
        mAuth.addAuthStateListener(mAuthListener);

        if (mChatId != null && !mChatId.equals("")) {
            Query qChatMessages = mDbRefMessages
                    .orderByChild("messageChat")
                    .equalTo(mChatId);
            qChatMessages.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    displayChatMessages(dataSnapshot
                            .getRef()
                            .orderByChild("messageTimestamp")
                    );
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    Log.d(TAG, "error getting messages: " + databaseError.getMessage());
                }
            });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);

        return true;
    }

    @Override
    public void onStop() {
        super.onStop();
        mAuth.removeAuthStateListener(mAuthListener);
    }

    private TextWatcher onTextChanged() {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                //...
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() == 0) {
                    input.setHint("\"" + defaultResponse + "\"");
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
                //...
            }
        };
    }

    private FirebaseAuth.AuthStateListener getAuthListener() {
        return new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null) {
                    //...
                } else {
                    //...
                }
            }
        };
    }

    private ValueEventListener onChatChangeListener() {
        return new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (!dataSnapshot.hasChild(mChatId)) {

                    HashMap<String, Object> chatAddMap = new HashMap<String, Object>();
                    chatAddMap.put("seen", false);
                    chatAddMap.put("timestamp", ServerValue.TIMESTAMP);

                    HashMap<String, Object> chatUserMap = new HashMap<String, Object>();
                    chatUserMap.put(mCurrentUserId + "/" + mChatId, chatAddMap);
                    chatUserMap.put(mChatId + "/" + mCurrentUserId, chatAddMap);

                    mDbRefChats.updateChildren(chatUserMap, new DatabaseReference.CompletionListener() {
                        @Override
                        public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
                            if (databaseError != null) {
                                Log.d(TAG, "Error creating chat record: " + databaseError.getMessage());
                            }
                        }
                    });
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.d(TAG, "Operation cancelled: " + databaseError.getMessage());
            }
        };
    }

    private ValueEventListener onMessagesChangeListener() {
        return new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (mChatId != null && !mChatId.equals("")) {
                    mDbRefMessages.orderByChild("mChatId").equalTo(mChatId);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        };
    }

    private View.OnClickListener sendChatMessage() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final EditText input = (EditText) findViewById(R.id.input);
                String message = input.getText().toString();

                if (!message.equals("")) {
                    if (mChatId == null || mChatId.equals("")) {
                        mChatId = mDbRefChats.push().getKey();
                    }

                    String messageKey = mDbRefMessages.push().getKey();

                    HashMap<String, Object> messageMap = new HashMap<String, Object>();
                    messageMap.put("messageText", message);
                    messageMap.put("messageTimestamp", ServerValue.TIMESTAMP);
                    messageMap.put("messageSender", mCurrentUserId);
                    messageMap.put("messageChat", mChatId);

                    HashMap<String, Object> messageChatMap = new HashMap<String, Object>();
                    messageChatMap.put(messageKey, messageMap);

                    mDbRefMessages.updateChildren(messageChatMap, messagesSentListener());
                } else {
                    //load default response text into field to allow sending
                    input.setText(defaultResponse);
                }
            }
        };
    }

    private DatabaseReference.CompletionListener messagesSentListener() {
        return new DatabaseReference.CompletionListener() {
            @Override
            public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
                if (databaseError != null) {
                    Log.d(TAG, databaseError.getMessage());
                    Toast.makeText(
                            getApplicationContext(),
                            "Error sending message!",
                            Toast.LENGTH_SHORT
                    ).show();
                } else {
                    mDbRefChats.child(mChatId).addListenerForSingleValueEvent(updateChatStatus());

                    //clear for subsequent input
                    input.setText("");
                }
            }
        };
    }

    private ValueEventListener updateChatStatus() {
        return new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot chatSnapshot) {
                if (chatSnapshot.child("members").hasChildren()) {

                    HashMap<String, Object> statusMap = new HashMap<String, Object>();
                    statusMap.put("timestamp", ServerValue.TIMESTAMP);

                    //loop through all users in the chat
                    for (DataSnapshot member : chatSnapshot.child("members").getChildren()) {
                        //all will be flagged as having not seen the chat
                        //apart from the sender
                        if (member.getKey().equals(mCurrentUserId)) {
                            statusMap.put("seen", true);
                        } else {
                            statusMap.put("seen", false);
                        }

                        String chatUserRef = LOC_CHATS + "/" + mChatId + "/members/" + member.getKey();
                        String userChatRef = LOC_USERS + "/" + member.getKey() + "/member/" + mChatId;

                        HashMap<String, Object> chatStatusMap = new HashMap<String, Object>();
                        chatStatusMap.put(chatUserRef, statusMap);
                        chatStatusMap.put(userChatRef, statusMap);

                        mDbRefRoot.updateChildren(chatStatusMap, new DatabaseReference.CompletionListener() {
                            @Override
                            public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
                                if (databaseError != null) {
                                    Log.d(TAG, "error updating chat: " + databaseError.getMessage());
                                }
                            }
                        });
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.d(TAG, "Error updating chat record: " + databaseError.getMessage());
            }
        };
    }

    private void displayChatMessages(Query query) {
        final FirebaseRecyclerAdapter<Message, MessagesViewHolder> recyclerAdapter =
                new FirebaseRecyclerAdapter<Message, MessagesViewHolder>(
                        Message.class,
                        R.layout.message_item,
                        MessagesViewHolder.class,
                        query
                ) {
                    @Override
                    protected void populateViewHolder(final MessagesViewHolder viewHolder, final Message model, int position) {
                        viewHolder.setMessageDisplay((model.getMessageSender().equals(mCurrentUserId)));
                        //all messages have at least a sender, time and text//
                        viewHolder.setMessageTime(model.getMessageTimestamp());
                        viewHolder.setMessageText(model.getMessageText());

                        mDbRefUsers.child(model.getMessageSender()).addValueEventListener(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot dataSnapshot) {
                                String senderName = "";
                                if (dataSnapshot.hasChild("displayName")) {
                                    senderName = dataSnapshot.child("displayName").getValue().toString();
                                } else {
                                    String firstName = dataSnapshot.child("firstName").getValue().toString();
                                    String surname = dataSnapshot.child("surname").getValue().toString();
                                    senderName = firstName + " " + surname;
                                }
                                viewHolder.setDisplayName(senderName);
                            }

                            @Override
                            public void onCancelled(DatabaseError databaseError) {
                            }
                        });
                        //end general

                        //messages can have either an event stub or an image attached
                        if (model.hasEvent()) {
                            mDbRefEvents.child(model.getEventKey()).addValueEventListener(new ValueEventListener() {
                                @Override
                                public void onDataChange(DataSnapshot dataSnapshot) {
                                    if (dataSnapshot != null) {
                                        //set visible if events found
                                        viewHolder.setEventVisible(View.VISIBLE);

                                        //text fields
                                        String eventTitle = dataSnapshot.child("eventTitle").getValue().toString();
                                        viewHolder.setEventTitle(eventTitle);
                                        long eventTimestamp = Long.parseLong(dataSnapshot.child("eventTimestamp").getValue().toString());
                                        viewHolder.setEventDate(eventTimestamp);

                                        //get image from firebase storage and display
                                        if (dataSnapshot.child("eventImage").getValue() != null) {
                                            String eventImage = dataSnapshot.child("eventImage").getValue().toString();
                                            if (eventImage != null && !eventImage.equals("")) {
                                                FirebaseStorage.getInstance().getReference(
                                                        "images/events/" + model.getEventKey() + "/" + eventImage
                                                ).getDownloadUrl().addOnCompleteListener(new OnCompleteListener<Uri>() {
                                                    @Override
                                                    public void onComplete(@NonNull Task<Uri> task) {
                                                        if (task.isSuccessful()) {
                                                            viewHolder.setMessageEventImage(getApplicationContext(), task.getResult());
                                                        } else {
                                                            Log.d(TAG, "unable to fetch image at location");
                                                        }
                                                    }
                                                });
                                            }
                                        }

                                        //attendance for user viewing
                                        if (dataSnapshot.child(LOC_INVITES).hasChild(mCurrentUserId)) {
                                            long status = Long.parseLong(dataSnapshot
                                                    .child(LOC_INVITES)
                                                    .child(mCurrentUserId)
                                                    .child("status")
                                                    .getValue().toString());
                                            long sentTimestamp = Long.parseLong(dataSnapshot
                                                    .child(LOC_INVITES)
                                                    .child(mCurrentUserId)
                                                    .child("sentTimestamp")
                                                    .getValue().toString()
                                            );

                                            viewHolder.setEventAttending((status == EventInvite.ATTENDING));
                                            viewHolder.setAttendingListener(
                                                    attendSwitchListener(model.getEventKey(), sentTimestamp));
                                        }

                                        viewHolder.setViewOnClickListener(itemClickListener(model.getEventKey()));
                                    } else {
                                        //don't display if event not found
                                        viewHolder.setEventVisible(View.GONE);
                                    }
                                }

                                @Override
                                public void onCancelled(DatabaseError databaseError) {
                                    viewHolder.setEventVisible(View.GONE);
                                }
                            });
                        } else if (model.hasPhoto()) {
                            FirebaseStorage.getInstance()
                                    .getReference(model.getPhotoLoc()).getDownloadUrl()
                                    .addOnCompleteListener(new OnCompleteListener<Uri>() {
                                        @Override
                                        public void onComplete(@NonNull Task<Uri> task) {
                                            if (task.isSuccessful()) {
                                                viewHolder.setPhotoVisible(View.VISIBLE);
                                                viewHolder.setMessageImage(
                                                        getApplicationContext(),
                                                        task.getResult()
                                                );
                                            } else {
                                                Log.d(TAG, "unable to fetch photo at this location");
                                                viewHolder.setPhotoVisible(View.GONE);
                                            }
                                        }
                                    });
                        }
                    }
                };

        recyclerAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                super.onItemRangeInserted(positionStart, itemCount);
                int friendlyMessageCount = recyclerAdapter.getItemCount();
                int lastVisiblePosition =
                        ((LinearLayoutManager) message_list.getLayoutManager())
                                .findLastCompletelyVisibleItemPosition();
                // If the recycler view is initially being loaded or the
                // user is at the bottom of the list, scroll to the bottom
                // of the list to show the newly added message.
                if (lastVisiblePosition == -1 ||
                        (positionStart >= (friendlyMessageCount - 1) &&
                                lastVisiblePosition == (positionStart - 1))) {
                    ((LinearLayoutManager) message_list.getLayoutManager())
                            .scrollToPosition(positionStart);
                }
            }
        });

        message_list.setAdapter(recyclerAdapter);
    }

    private CompoundButton.OnCheckedChangeListener attendSwitchListener(final String eventKey, final long sentTimestamp) {
        return new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                int response;
                if (b) {
                    response = EventInvite.ATTENDING;
                } else {
                    response = EventInvite.NOT_ATTENDING;
                }

                //data to be stored on event response
                HashMap<String, Object> mapResponse = new HashMap<String, Object>();
                mapResponse.put("status", response);
                mapResponse.put("sentTimestamp", sentTimestamp);
                mapResponse.put("responseTimestamp", ServerValue.TIMESTAMP);

                //locations at which data is stored, relating to user and event
                HashMap<String, Object> mapInvite = new HashMap<String, Object>();
                String inviteLocEvent = LOC_EVENTS + "/" + eventKey + "/" + LOC_INVITES + "/" + mCurrentUserId;
                String inviteLocUser = LOC_USERS + "/" + mCurrentUserId + "/" + LOC_INVITES + "/" + eventKey;
                mapInvite.put(inviteLocEvent, mapResponse);
                mapInvite.put(inviteLocUser, mapResponse);

                //must use database root to reach both event and user nodes
                DatabaseReference refRoot = FirebaseDatabase.getInstance().getReference();

                //write changes to database
                refRoot.updateChildren(mapInvite, new DatabaseReference.CompletionListener() {
                    @Override
                    public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
                        if (databaseError != null) {
                            Log.d(TAG, "error responding to event: " + databaseError.getMessage());
                        }/* else {
                            Snackbar.make(
                                    findViewById(R.id.event_layout),
                                    "Your status for this event has been updated",
                                    Snackbar.LENGTH_LONG
                            ).show();
                        }*/
                    }
                });
            }
        };
    }

    private View.OnClickListener itemClickListener(final String eventKey) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int viewId = view.getId();

                if (viewId == R.id.swch_event_attending) {

                } else {
                    Intent evIntent = new Intent(
                            ChatActivity.this,
                            EventDetailsActivity.class
                    );
                    evIntent.putExtra("EVENT_ID", eventKey);
                    startActivity(evIntent);
                }
            }
        };
    }

    private void getUserDefaultResponse() {
        mDatabase.getReference(LOC_USERS).child(mCurrentUserId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.hasChild("defaultResponse")) {
                    defaultResponse = dataSnapshot.child("defaultResponse").getValue().toString();
                } else {
                    defaultResponse = getString(R.string.user_default_response);
                }

                //set text as initial hint
                input.setHint("\"" + defaultResponse + "\"");
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }
}
