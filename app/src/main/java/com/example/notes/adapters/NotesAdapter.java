package com.example.notes.adapters;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
// Uri import không cần thiết ở đây vì chúng ta dùng file path
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.notes.R;
import com.example.notes.entities.Note;
import com.example.notes.listeners.NotesListener;
import com.makeramen.roundedimageview.RoundedImageView;

// InputStream và IOException không còn cần thiết ở đây nếu chỉ dùng decodeFile
import java.util.List;

public class NotesAdapter extends RecyclerView.Adapter<NotesAdapter.NoteViewHolder> {

    private static final String TAG = "NotesAdapter";
    private List<Note> notes;
    private NotesListener notesListener;


    public NotesAdapter(List<Note> notes, NotesListener notesListener) {
        this.notes = notes;
        this.notesListener = notesListener;
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(
                R.layout.item_container_note,
                parent,
                false
        );
        return new NoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        holder.setNote(notes.get(position));
        holder.layoutNote.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                notesListener.onNoteClicked(notes.get(position),position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return notes != null ? notes.size() : 0;
    }

    static class NoteViewHolder extends RecyclerView.ViewHolder {

        TextView textTitle, textSubTitle, textDateTime;
        LinearLayout layoutNote;
        RoundedImageView imageNoteItem;

        NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            textTitle = itemView.findViewById(R.id.textTitle);
            textSubTitle = itemView.findViewById(R.id.textSubTitle);
            textDateTime = itemView.findViewById(R.id.textDateTime);
            layoutNote = itemView.findViewById(R.id.layoutNote);
            imageNoteItem = itemView.findViewById(R.id.imageNote); // ID của RoundedImageView trong item_container_note.xml

            if (textTitle == null) Log.e(TAG, "NoteViewHolder: textTitle is null.");
            if (textSubTitle == null) Log.e(TAG, "NoteViewHolder: textSubTitle is null.");
            if (textDateTime == null) Log.e(TAG, "NoteViewHolder: textDateTime is null.");
            if (layoutNote == null) Log.e(TAG, "NoteViewHolder: layoutNote is null.");
            if (imageNoteItem == null) Log.e(TAG, "NoteViewHolder: imageNoteItem is null. Check R.id.imageNote in item_container_note.xml");
        }

        void setNote(Note note) {
            if (note == null) {
                Log.w(TAG, "Attempting to set a null note to ViewHolder");
                if (layoutNote != null) layoutNote.setVisibility(View.GONE);
                return;
            }
            if (layoutNote != null) layoutNote.setVisibility(View.VISIBLE);


            if (textTitle != null) {
                textTitle.setText(note.getTitle());
            }

            if (textSubTitle != null) {
                if (note.getSubtitle() != null && !note.getSubtitle().trim().isEmpty()) {
                    textSubTitle.setText(note.getSubtitle());
                    textSubTitle.setVisibility(View.VISIBLE);
                } else {
                    textSubTitle.setVisibility(View.GONE);
                }
            }

            if (textDateTime != null) {
                if (note.getDateTime() != null && !note.getDateTime().trim().isEmpty()) {
                    textDateTime.setText(note.getDateTime());
                    textDateTime.setVisibility(View.VISIBLE);
                } else {
                    textDateTime.setVisibility(View.GONE);
                }
            }

            if (layoutNote != null) {
                Object background = layoutNote.getBackground();
                if (background instanceof GradientDrawable) {
                    GradientDrawable gradientDrawable = (GradientDrawable) background;
                    String colorStr = note.getColor();
                    if (colorStr != null && !colorStr.trim().isEmpty()) {
                        try {
                            gradientDrawable.setColor(Color.parseColor(colorStr));
                        } catch (IllegalArgumentException e) {
                            Log.e(TAG, "Mã màu không hợp lệ: " + colorStr, e);
                            gradientDrawable.setColor(Color.parseColor("#333333"));
                        }
                    } else {
                        gradientDrawable.setColor(Color.parseColor("#333333"));
                    }
                } else {
                    String colorStr = note.getColor();
                    try {
                        if (colorStr != null && !colorStr.trim().isEmpty()) {
                            layoutNote.setBackgroundColor(Color.parseColor(colorStr));
                        } else {
                            layoutNote.setBackgroundColor(Color.parseColor("#333333"));
                        }
                    } catch (IllegalArgumentException e) {
                        Log.e(TAG, "Mã màu không hợp lệ (fallback): " + colorStr, e);
                        layoutNote.setBackgroundColor(Color.parseColor("#333333"));
                    }
                }
            }

            // Hiển thị ảnh từ đường dẫn file trong bộ nhớ trong
            if (imageNoteItem != null) {
                if (note.getImagePath() != null && !note.getImagePath().trim().isEmpty()) {
                    try {
                        Bitmap bitmap = BitmapFactory.decodeFile(note.getImagePath()); // Sử dụng decodeFile vì imagePath là đường dẫn file
                        if (bitmap != null) {
                            imageNoteItem.setImageBitmap(bitmap);
                            imageNoteItem.setVisibility(View.VISIBLE);
                        } else {
                            Log.w(TAG, "Bitmap rỗng từ file path: " + note.getImagePath() + " cho note: " + note.getTitle());
                            imageNoteItem.setVisibility(View.GONE);
                        }
                    } catch (Exception e) { // Bắt OutOfMemoryError hoặc các lỗi khác
                        Log.e(TAG, "Lỗi khi decode file ảnh: " + note.getImagePath() + " cho note: " + note.getTitle(), e);
                        imageNoteItem.setVisibility(View.GONE);
                    }
                } else {
                    imageNoteItem.setVisibility(View.GONE);
                }
            }
        }
    }
    // public interface NoteListener { ... }
}