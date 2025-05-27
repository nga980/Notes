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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    public RecyclerView notesRecyclerView;
    private List<Note> noteList; // Đổi tên từ notelist để theo quy ước chung
    private NotesAdapter notesAdapter;

    ActivityResultLauncher<Intent> addNoteLauncher;
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
                addNoteLauncher.launch(intent);
            }
        });

        notesRecyclerView = findViewById(R.id.noteRecyclerView); // Đảm bảo ID này khớp với file XML của bạn
        // emptyViewText = findViewById(R.id.emptyViewText); // Tùy chọn: Khởi tạo nếu bạn có view cho trạng thái rỗng

        setupRecyclerView();

        addNoteLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            Log.d("MainActivity", "Thao tác ghi chú thành công, làm mới danh sách.");
                            getNotes(); // Tải lại danh sách ghi chú
                        } else if (result.getResultCode() == Activity.RESULT_CANCELED) {
                            Log.d("MainActivity", "Thao tác ghi chú đã bị hủy.");
                        }
                        // Bạn có thể muốn xử lý các mã kết quả khác nếu CreateNoteActivity có thể trả về chúng
                    }
                });

        getNotes(); // Tải danh sách ghi chú lần đầu khi Activity được tạo
    }

    private void setupRecyclerView() {
        notesRecyclerView.setLayoutManager(
                new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        );
        noteList = new ArrayList<>();
        notesAdapter = new NotesAdapter(noteList); // Giả sử constructor của Adapter nhận List<Note>
        // Nếu adapter của bạn cần một listener cho sự kiện click, hãy truyền nó vào đây, ví dụ:
        // notesAdapter = new NotesAdapter(noteList, this); // nếu MainActivity implement một interface click listener
        notesRecyclerView.setAdapter(notesAdapter);
    }

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
                    // Đưa kết quả lên luồng UI (main thread)
                    ContextCompat.getMainExecutor(MainActivity.this).execute(new Runnable() {
                        @Override
                        public void run() {
                            handleGetNotesResult(notesFromDb);
                        }
                    });
                } catch (Exception e) {
                    Log.e("GetNotes", "Lỗi khi lấy danh sách ghi chú từ database", e);
                    // Đưa trạng thái lỗi (danh sách null) lên luồng UI
                    ContextCompat.getMainExecutor(MainActivity.this).execute(new Runnable() {
                        @Override
                        public void run() {
                            handleGetNotesResult(null); // Truyền null để chỉ báo đã có lỗi
                        }
                    });
                }
            }
        });
    }

    // Phương thức riêng để xử lý kết quả trả về từ luồng nền
    private void handleGetNotesResult(List<Note> notes) {
        if (notes != null) { // Lấy dữ liệu thành công (danh sách có thể rỗng)
            Log.d("MY_NOTES", "Đã lấy được " + notes.size() + " ghi chú.");

            noteList.clear();
            noteList.addAll(notes);
            notesAdapter.notifyDataSetChanged();

            if (notes.isEmpty()) {
                Log.d("MY_NOTES", "Không có ghi chú nào để hiển thị.");
                Toast.makeText(MainActivity.this, "Không tìm thấy ghi chú nào.", Toast.LENGTH_SHORT).show();
                // Tùy chọn: Hiển thị text báo rỗng và ẩn RecyclerView
                // notesRecyclerView.setVisibility(View.GONE);
                // emptyViewText.setVisibility(View.VISIBLE);
            } else {
                // Tùy chọn: Đảm bảo RecyclerView hiển thị và ẩn text báo rỗng
                // notesRecyclerView.setVisibility(View.VISIBLE);
                // emptyViewText.setVisibility(View.GONE);
                // Toast.makeText(MainActivity.this, "Đã tải " + notes.size() + " ghi chú.", Toast.LENGTH_SHORT).show(); // Hơi thừa nếu đã có Toast "Không tìm thấy ghi chú"
            }
        } else { // Đã xảy ra lỗi trong quá trình lấy dữ liệu
            Log.e("MY_NOTES", "Không thể tải danh sách ghi chú do có lỗi.");
            Toast.makeText(MainActivity.this, "Lỗi khi tải danh sách ghi chú!", Toast.LENGTH_SHORT).show();
            // Tùy chọn: Hiển thị view báo rỗng với thông báo lỗi
            // notesRecyclerView.setVisibility(View.GONE);
            // emptyViewText.setText("Lỗi tải ghi chú. Vui lòng thử lại.");
            // emptyViewText.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onDestroy() { // Sửa lại đúng tên phương thức
        super.onDestroy();
        // Quan trọng: Đóng ExecutorService khi Activity bị hủy
        if (executorService != null && !executorService.isShutdown()) {
            Log.d("MainActivity", "Đang tắt ExecutorService.");
            executorService.shutdown();
        }
    }
}