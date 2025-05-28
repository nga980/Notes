package com.example.notes.activities;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
// Import một TextView nếu bạn muốn hiển thị thông báo trạng thái rỗng
// import android.widget.TextView;
import android.widget.Toast;

import com.example.notes.R;
import com.example.notes.adapters.NotesAdapter;
import com.example.notes.entities.Note;
import com.example.notes.database.NoteDatabase;
import com.example.notes.listeners.NotesListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements NotesListener {
    // Hằng số này không còn cần thiết
    // public static final int REQUEST_CODE_SHOW_NOTE = 3;

    public RecyclerView notesRecyclerView;
    private List<Note> noteList;
    private NotesAdapter notesAdapter;
    private int noteClickedPosition = -1;

    ActivityResultLauncher<Intent> noteActivityLauncher;
    private ExecutorService executorService;

    // Tùy chọn: Để hiển thị thông báo trạng thái rỗng
    // private TextView emptyViewText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        executorService = Executors.newSingleThreadExecutor();

        ImageView imageAddNoteMain = findViewById(R.id.imageAddNoteMain);
        imageAddNoteMain.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, CreateNoteActivity.class);
                // Khi thêm mới, không cần đặt isViewOrUpdate
                noteActivityLauncher.launch(intent);
            }
        });

        notesRecyclerView = findViewById(R.id.noteRecyclerView);
        // emptyViewText = findViewById(R.id.emptyViewText);

        setupRecyclerView();

        noteActivityLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            // CreateNoteActivity đã kết thúc thành công (lưu note mới, cập nhật note cũ, hoặc có thể xóa note)
                            // Cách đơn giản và an toàn nhất là tải lại toàn bộ danh sách.
                            Log.d("MainActivity", "Thao tác ghi chú thành công, làm mới danh sách.");
                            getNotes(); // Tải lại danh sách ghi chú, không cần requestCode

                            // Để xử lý cụ thể hơn (ví dụ: chỉ cập nhật item đã thay đổi thay vì cả list),
                            // CreateNoteActivity cần trả về thêm thông tin trong Intent result.getData().
                            // Ví dụ:
                            // Intent data = result.getData();
                            // if (data != null && data.getBooleanExtra(CreateNoteActivity.EXTRA_NOTE_UPDATED_OR_ADDED, false)) {
                            //    // Nếu là update và bạn có note đã update cùng vị trí:
                            //    // if (noteClickedPosition != -1 && data.hasExtra(CreateNoteActivity.EXTRA_NOTE)) {
                            //    // Note updatedNote = data.getSerializableExtra(CreateNoteActivity.EXTRA_NOTE, Note.class); // Hoặc Parcelable
                            //    // if (updatedNote != null) {
                            //    // noteList.set(noteClickedPosition, updatedNote);
                            //    // notesAdapter.notifyItemChanged(noteClickedPosition);
                            //    // noteClickedPosition = -1; // Reset
                            //    // return; // Không cần getNotes() nữa
                            //    // }
                            //    // }
                            //    getNotes(); // Nếu không có đủ thông tin để cập nhật cụ thể, tải lại cả list
                            // } else if (data != null && data.getBooleanExtra(CreateNoteActivity.EXTRA_IS_NOTE_DELETED, false)) {
                            //    // Xử lý xóa note khỏi list và notifyItemRemoved, hoặc getNotes()
                            //    getNotes();
                            // } else {
                            //    // Trường hợp thêm mới hoặc không có thông tin cụ thể
                            //    getNotes();
                            // }
                        } else if (result.getResultCode() == Activity.RESULT_CANCELED) {
                            Log.d("MainActivity", "Thao tác ghi chú đã bị hủy.");
                        }
                    }
                });

        getNotes(); // Tải danh sách ghi chú lần đầu, không cần requestCode
    }

    @Override
    public void onNoteClicked(Note note, int position) {
        noteClickedPosition = position;
        Intent intent = new Intent(getApplicationContext(), CreateNoteActivity.class);
        // Báo cho CreateNoteActivity biết đây là thao tác xem/cập nhật
        // và truyền dữ liệu của note hiện tại.
        // Đảm bảo các key EXTRA_* này được định nghĩa và sử dụng nhất quán trong CreateNoteActivity.
        intent.putExtra(CreateNoteActivity.EXTRA_IS_VIEW_OR_UPDATE, true); // Sử dụng hằng số từ CreateNoteActivity
        intent.putExtra(CreateNoteActivity.EXTRA_NOTE, note);           // Sử dụng hằng số từ CreateNoteActivity

        noteActivityLauncher.launch(intent);
    }

    private void setupRecyclerView() {
        notesRecyclerView.setLayoutManager(
                new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        );
        noteList = new ArrayList<>();
        notesAdapter = new NotesAdapter(noteList, this);
        notesRecyclerView.setAdapter(notesAdapter);
    }

    // Xóa tham số requestCode không sử dụng
    private void getNotes() {
        Log.d("MainActivity", "Đang cố gắng lấy danh sách ghi chú từ database.");
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                final List<Note> notesFromDb;
                try {
                    notesFromDb = NoteDatabase
                            .getDatabase(getApplicationContext())
                            .noteDao().getAllNotes();
                    ContextCompat.getMainExecutor(MainActivity.this).execute(new Runnable() {
                        @Override
                        public void run() {
                            handleGetNotesResult(notesFromDb);
                        }
                    });
                } catch (Exception e) {
                    Log.e("GetNotes", "Lỗi khi lấy danh sách ghi chú từ database", e);
                    ContextCompat.getMainExecutor(MainActivity.this).execute(new Runnable() {
                        @Override
                        public void run() {
                            handleGetNotesResult(null);
                        }
                    });
                }
            }
        });
    }

    private void handleGetNotesResult(List<Note> notes) {
        if (notes != null) {
            // Để tối ưu hơn, bạn có thể dùng DiffUtil ở đây thay vì clear và addAll
            // và notifyDataSetChanged(). Tuy nhiên, với số lượng note không quá lớn,
            // cách này vẫn chấp nhận được.
            noteList.clear();
            noteList.addAll(notes);
            notesAdapter.notifyDataSetChanged();

            // Scroll đến vị trí note vừa được click (nếu là update và bạn muốn)
            // if (noteClickedPosition != -1 && noteClickedPosition < noteList.size()) {
            //    notesRecyclerView.smoothScrollToPosition(noteClickedPosition);
            //    noteClickedPosition = -1; // Reset sau khi dùng
            // }


            if (notes.isEmpty()) {
                Log.d("MY_NOTES", "Không có ghi chú nào để hiển thị.");
                Toast.makeText(MainActivity.this, "Không tìm thấy ghi chú nào.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Log.e("MY_NOTES", "Không thể tải danh sách ghi chú do có lỗi.");
            Toast.makeText(MainActivity.this, "Lỗi khi tải danh sách ghi chú!", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            Log.d("MainActivity", "Đang tắt ExecutorService.");
            executorService.shutdown();
        }
    }
}