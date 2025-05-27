package com.example.notes.activities;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
// import android.database.Cursor; // Không dùng getPathFromUri nữa
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.notes.R;
import com.example.notes.database.NoteDatabase;
import com.example.notes.entities.Note;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CreateNoteActivity extends AppCompatActivity {

    private static final String TAG = "CreateNoteActivity";

    private ImageView imageBack, imageSave;
    private EditText inputNoteTitle, inputNoteSubtitle, inputNote;
    private TextView textDateTime;
    private View viewSubtitleIndicator;
    private ImageView imageNotePreview;

    private ExecutorService executorService;
    private LinearLayout layoutNoteColor; // LinearLayout chứa các ô màu trong BottomSheet

    private String selectedNoteColor;
    private String selectedImagePath = "";

    private ActivityResultLauncher<Intent> pickImageLauncher;
    private ActivityResultLauncher<String> requestPermissionLauncher;

    private BottomSheetBehavior<LinearLayout> bottomSheetBehavior;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_note);

        executorService = Executors.newSingleThreadExecutor();

        imageBack = findViewById(R.id.imageBack);
        imageSave = findViewById(R.id.imageSave);
        inputNoteTitle = findViewById(R.id.inputNoteTitle);
        inputNoteSubtitle = findViewById(R.id.inputNoteSubtitle);
        inputNote = findViewById(R.id.inputNote);
        textDateTime = findViewById(R.id.textDateTime);
        viewSubtitleIndicator = findViewById(R.id.viewSubtitleIndicator);
        imageNotePreview = findViewById(R.id.imageNote); // Đảm bảo ID này đúng trong activity_create_note.xml

        layoutNoteColor = findViewById(R.id.layoutNoteColor); // Nằm trong layout_miscellaneous qua include

        // Màu mặc định và đường dẫn ảnh mặc định được thiết lập trong initMiscellaneous và setupActivityLaunchers
        // selectedNoteColor sẽ được đặt trong initMiscellaneous
        // selectedImagePath đã được khởi tạo là ""

        setupActivityLaunchers();
        initMiscellaneous(); // initMiscellaneous sẽ đặt selectedNoteColor mặc định và cập nhật UI
        // setSubtitleIndicatorColor(); // Đã được gọi ở cuối initMiscellaneous

        if (textDateTime != null) {
            textDateTime.setText(
                    new SimpleDateFormat("EEEE, dd MMMM yyyy HH:mm a", Locale.getDefault()) // Thêm yyyy cho năm
                            .format(new Date())
            );
        }

        imageBack.setOnClickListener(view -> getOnBackPressedDispatcher().onBackPressed());
        imageSave.setOnClickListener(view -> saveNote());
    }

    private void setupActivityLaunchers() {
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        selectImage();
                    } else {
                        Toast.makeText(this, "Quyền truy cập bộ nhớ bị từ chối!", Toast.LENGTH_SHORT).show();
                    }
                });

        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        Uri sourceUri = data.getData();
                        if (sourceUri != null) {
                            String fileNameSuffix = "";
                            String mimeType = getContentResolver().getType(sourceUri);
                            if (mimeType != null) {
                                if (mimeType.contains("jpeg") || mimeType.contains("jpg")) fileNameSuffix = ".jpg";
                                else if (mimeType.contains("png")) fileNameSuffix = ".png";
                                else if (mimeType.contains("webp")) fileNameSuffix = ".webp";
                                // Thêm các kiểu file khác nếu cần
                            } else {
                                // Fallback nếu không lấy được mimeType (hiếm khi xảy ra với MediaStore)
                                String path = sourceUri.getPath();
                                if (path != null) {
                                    if (path.toLowerCase().endsWith(".jpg") || path.toLowerCase().endsWith(".jpeg")) fileNameSuffix = ".jpg";
                                    else if (path.toLowerCase().endsWith(".png")) fileNameSuffix = ".png";
                                    else if (path.toLowerCase().endsWith(".webp")) fileNameSuffix = ".webp";
                                }
                            }
                            if (fileNameSuffix.isEmpty()) fileNameSuffix = ".jpg"; // Mặc định là jpg nếu không xác định được

                            String fileName = "note_image_" + System.currentTimeMillis() + fileNameSuffix;
                            String copiedImagePath = copyFileToInternalStorage(sourceUri, fileName);

                            if (copiedImagePath != null) {
                                selectedImagePath = copiedImagePath;
                                Bitmap bitmap = BitmapFactory.decodeFile(selectedImagePath);
                                if (imageNotePreview != null && bitmap != null) {
                                    imageNotePreview.setImageBitmap(bitmap);
                                    imageNotePreview.setVisibility(View.VISIBLE);
                                } else if (bitmap == null) {
                                    Log.e(TAG, "Không thể decode bitmap từ file đã sao chép: " + selectedImagePath);
                                    Toast.makeText(this, "Lỗi hiển thị ảnh preview.", Toast.LENGTH_SHORT).show();
                                    selectedImagePath = "";
                                }
                            } else {
                                Toast.makeText(this, "Lỗi khi sao chép ảnh vào bộ nhớ.", Toast.LENGTH_SHORT).show();
                                selectedImagePath = "";
                            }
                        }
                    }
                });
    }

    private String copyFileToInternalStorage(Uri sourceUri, String destinationFileName) {
        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            File imageDir = new File(getFilesDir(), "images");
            if (!imageDir.exists()) {
                if (!imageDir.mkdirs()) {
                    Log.e(TAG, "Không thể tạo thư mục 'images'.");
                    return null;
                }
            }
            File destinationFile = new File(imageDir, destinationFileName);
            inputStream = getContentResolver().openInputStream(sourceUri);
            if (inputStream == null) {
                Log.e(TAG, "Không thể mở InputStream từ Uri: " + sourceUri);
                return null;
            }
            outputStream = new FileOutputStream(destinationFile);
            byte[] buffer = new byte[1024 * 4]; // 4KB buffer
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            Log.d(TAG, "Ảnh đã sao chép vào: " + destinationFile.getAbsolutePath());
            return destinationFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Lỗi sao chép file", e);
            return null;
        } finally {
            try {
                if (inputStream != null) inputStream.close();
                if (outputStream != null) {
                    outputStream.flush();
                    outputStream.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Lỗi đóng streams", e);
            }
        }
    }

    private void setSubtitleIndicatorColor() {
        if (viewSubtitleIndicator != null && selectedNoteColor != null && !selectedNoteColor.isEmpty()) {
            if (viewSubtitleIndicator.getBackground() instanceof GradientDrawable) {
                GradientDrawable gradientDrawable = (GradientDrawable) viewSubtitleIndicator.getBackground();
                try {
                    gradientDrawable.setColor(Color.parseColor(selectedNoteColor));
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Mã màu không hợp lệ cho indicator: " + selectedNoteColor, e);
                }
            } else {
                try {
                    viewSubtitleIndicator.setBackgroundColor(Color.parseColor(selectedNoteColor));
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Mã màu không hợp lệ cho setBackgroundColor indicator: " + selectedNoteColor, e);
                }
            }
        }
    }

    private void selectImage() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        pickImageLauncher.launch(intent);
    }

    private void saveNote() {
        final String noteTitle = inputNoteTitle.getText().toString().trim();
        final String noteSubtitle = inputNoteSubtitle.getText().toString().trim();
        final String noteContent = inputNote.getText().toString().trim();
        final String currentDateTime = (textDateTime != null && textDateTime.getText() != null) ?
                textDateTime.getText().toString() :
                new SimpleDateFormat("EEEE, dd MMMM yyyy HH:mm a", Locale.getDefault()).format(new Date());

        if (TextUtils.isEmpty(noteTitle)) {
            Toast.makeText(this, "Tiêu đề ghi chú không được để trống!", Toast.LENGTH_SHORT).show();
            inputNoteTitle.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(noteContent)) {
            Toast.makeText(this, "Nội dung ghi chú không được để trống!", Toast.LENGTH_SHORT).show();
            inputNote.requestFocus();
            return;
        }

        final Note note = new Note();
        note.setTitle(noteTitle);
        note.setSubtitle(noteSubtitle);
        note.setNoteText(noteContent);
        note.setDateTime(currentDateTime);
        note.setColor(selectedNoteColor);
        note.setImagePath(selectedImagePath);

        executorService.execute(() -> {
            boolean success = false;
            try {
                NoteDatabase.getDatabase(getApplicationContext()).noteDao().insertNote(note);
                success = true;
            } catch (Exception e) {
                Log.e(TAG, "Lỗi khi lưu ghi chú vào database", e);
                success = false;
            }

            final boolean finalSuccess = success;
            ContextCompat.getMainExecutor(CreateNoteActivity.this).execute(() -> {
                if (finalSuccess) {
                    Toast.makeText(CreateNoteActivity.this, "Đã lưu ghi chú!", Toast.LENGTH_SHORT).show();
                    Intent intentResult = new Intent();
                    setResult(Activity.RESULT_OK, intentResult);
                    finish();
                } else {
                    Toast.makeText(CreateNoteActivity.this, "Lỗi khi lưu ghi chú!", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }

    private void initMiscellaneous() {
        final LinearLayout layoutMiscellaneousRoot = findViewById(R.id.layoutMiscellaneous);
        bottomSheetBehavior = BottomSheetBehavior.from(layoutMiscellaneousRoot);

        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        bottomSheetBehavior.setHideable(false);
        bottomSheetBehavior.setDraggable(true);

        if (layoutNoteColor != null && bottomSheetBehavior.getState() != BottomSheetBehavior.STATE_EXPANDED) {
            layoutNoteColor.setVisibility(View.GONE);
            layoutNoteColor.setAlpha(0f);
        }

        TextView textMiscellaneousTitle = findViewById(R.id.textMiscellaneous); // textMiscellaneous là con của layoutMiscellaneousRoot
        textMiscellaneousTitle.setOnClickListener(view -> {
            if (bottomSheetBehavior.getState() != BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                hideKeyboard();
            } else {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        });

        bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (layoutNoteColor == null) return;
                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    layoutNoteColor.setVisibility(View.VISIBLE);
                    layoutNoteColor.setAlpha(1f);
                } else if (newState == BottomSheetBehavior.STATE_COLLAPSED || newState == BottomSheetBehavior.STATE_HIDDEN) {
                    layoutNoteColor.setVisibility(View.GONE);
                    layoutNoteColor.setAlpha(0f);
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                if (layoutNoteColor != null) {
                    float effectiveAlpha = Math.max(0f, slideOffset);
                    layoutNoteColor.setAlpha(effectiveAlpha);
                    if (effectiveAlpha > 0 && layoutNoteColor.getVisibility() == View.GONE) {
                        layoutNoteColor.setVisibility(View.VISIBLE);
                    }
                }
            }
        });

        // ----- BẮT ĐẦU LOGIC CHỌN MÀU MỚI -----
        final ImageView[] checkImages = {
                layoutMiscellaneousRoot.findViewById(R.id.imageColor1),
                layoutMiscellaneousRoot.findViewById(R.id.imageColor2),
                layoutMiscellaneousRoot.findViewById(R.id.imageColor3),
                layoutMiscellaneousRoot.findViewById(R.id.imageColor4),
                layoutMiscellaneousRoot.findViewById(R.id.imageColor5)
        };

        final View[] colorViews = {
                layoutMiscellaneousRoot.findViewById(R.id.viewColor1),
                layoutMiscellaneousRoot.findViewById(R.id.viewColor2),
                layoutMiscellaneousRoot.findViewById(R.id.viewColor3),
                layoutMiscellaneousRoot.findViewById(R.id.viewColor4),
                layoutMiscellaneousRoot.findViewById(R.id.viewColor5)
        };

        // Gán tag màu cho từng viewColor nếu chưa có trong XML
        // Nếu bạn đã đặt tag trong XML, dòng này không cần thiết hoặc sẽ ghi đè
        colorViews[0].setTag("#333333"); // Màu mặc định của Note
        colorViews[1].setTag("#FDBE3B"); // Yellow
        colorViews[2].setTag("#FF4842"); // Red
        colorViews[3].setTag("#3A52FC"); // Blue (lưu ý mã màu này khác với #3F51B5 bạn dùng trước đó)
        colorViews[4].setTag("#000000"); // Black

        for (int i = 0; i < colorViews.length; i++) {
            final int index = i; // Cần biến final để dùng trong lambda
            if (colorViews[i] != null) { // Kiểm tra null
                colorViews[i].setOnClickListener(view -> {
                    Object tag = view.getTag();
                    if (tag instanceof String && !((String) tag).trim().isEmpty()) {
                        selectedNoteColor = (String) tag;
                    } else {
                        Log.w(TAG, "View màu không có tag hợp lệ, sử dụng màu mặc định của vòng lặp.");
                        // Fallback an toàn nếu tag bị thiếu
                        selectedNoteColor = (String) colorViews[index].getTag(); // Lấy lại tag từ mảng
                    }

                    for (ImageView checkImage : checkImages) {
                        if (checkImage != null) checkImage.setVisibility(View.GONE);
                    }
                    if (checkImages[index] != null) checkImages[index].setVisibility(View.VISIBLE);

                    setSubtitleIndicatorColor();
                });
            }
        }

        // Thiết lập trạng thái ban đầu cho màu mặc định (màu đầu tiên)
        if (checkImages[0] != null) checkImages[0].setVisibility(View.VISIBLE);
        // selectedNoteColor đã được khởi tạo ở onCreate hoặc lấy từ tag của viewColor1 ở trên
        // setSubtitleIndicatorColor(); // Đã được gọi ở onCreate sau initMiscellaneous

        // ----- KẾT THÚC LOGIC CHỌN MÀU MỚI -----


        // Xử lý click cho layoutAddImage
        layoutMiscellaneousRoot.findViewById(R.id.layoutAddImage).setOnClickListener(view -> {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            hideKeyboard();
            String permissionToRequest;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionToRequest = Manifest.permission.READ_MEDIA_IMAGES;
            } else {
                permissionToRequest = Manifest.permission.READ_EXTERNAL_STORAGE;
            }
            if (ContextCompat.checkSelfPermission(getApplicationContext(), permissionToRequest) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(permissionToRequest);
            } else {
                selectImage();
            }
        });
    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
            view.clearFocus();
        }
    }
}