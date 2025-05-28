package com.example.notes.activities; // Thay thế bằng package name của bạn

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.notes.R; // Đảm bảo R được import đúng
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
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CreateNoteActivity extends AppCompatActivity {

    private static final String TAG = "CreateNoteActivity";

    public static final String EXTRA_IS_VIEW_OR_UPDATE = "isViewOrUpdate";
    public static final String EXTRA_NOTE = "note";
    public static final String EXTRA_NOTE_UPDATED_OR_ADDED = "noteUpdatedOrAdded";
    public static final String EXTRA_IS_NOTE_DELETED = "isNoteDeleted";

    private ImageView imageBack, imageSave;
    private EditText inputNoteTitle, inputNoteSubtitle, inputNote;
    private TextView textDateTime;
    private View viewSubtitleIndicator;
    private ImageView imageNotePreview;
    private TextView textWebURL;
    private LinearLayout layoutWebURL;
    private ImageView imageRemoveWebURL, imageRemoveImage;

    private ExecutorService executorService;
    private LinearLayout layoutNoteColorOptions;
    private LinearLayout layoutAddImageSection;
    private LinearLayout layoutAddUrlSection;
    private LinearLayout layoutDeleteNoteSection;

    private String selectedNoteColor;
    private String selectedImagePath = "";

    private ActivityResultLauncher<Intent> pickImageLauncher;
    private ActivityResultLauncher<String> requestPermissionLauncher;

    private BottomSheetBehavior<LinearLayout> bottomSheetBehavior;
    private AlertDialog dialogAddURL;
    private AlertDialog dialogDeleteNote;
    private Note alreadyAvailableNote;
    private boolean isNoteChanged = false;

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
        imageNotePreview = findViewById(R.id.imageNote);
        textWebURL = findViewById(R.id.textWebURL);
        layoutWebURL = findViewById(R.id.layoutWebURL);
        imageRemoveWebURL = findViewById(R.id.imageRemoveWebURL);
        imageRemoveImage = findViewById(R.id.imageRemoveImage);

        selectedNoteColor = "#333333"; // Màu mặc định

        setupActivityLaunchers();

        if (textDateTime != null) {
            textDateTime.setText(
                    // Sửa lỗi typo "書館" thành "yyyy" cho năm
                    new SimpleDateFormat("EEEE, dd MMMM yyyy HH:mm a", Locale.getDefault())
                            .format(new Date())
            );
        }

        imageBack.setOnClickListener(view -> handleBackPress());
        imageSave.setOnClickListener(view -> saveNote());

        if (getIntent().getBooleanExtra(EXTRA_IS_VIEW_OR_UPDATE, false)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                alreadyAvailableNote = getIntent().getSerializableExtra(EXTRA_NOTE, Note.class);
            } else {
                //noinspection deprecation
                alreadyAvailableNote = (Note) getIntent().getSerializableExtra(EXTRA_NOTE);
            }
            if (alreadyAvailableNote != null) {
                setViewOrUpdateNote();
            } else {
                Log.e(TAG, "Error: isViewOrUpdate is true but no Note object found.");
                Toast.makeText(this, "Lỗi tải dữ liệu ghi chú.", Toast.LENGTH_SHORT).show();
                finish();
            }
        } else {
            // Tạo note mới
            setSubtitleIndicatorColor();
            if (imageRemoveImage != null) imageRemoveImage.setVisibility(View.GONE);
            if (layoutWebURL != null) layoutWebURL.setVisibility(View.GONE);
            if (imageNotePreview != null) imageNotePreview.setVisibility(View.GONE);
        }

        initMiscellaneous();
        if (alreadyAvailableNote == null) {
            updateColorSelectionUI(selectedNoteColor);
        }

        setupTextChangedListeners();

        if (imageRemoveWebURL != null) {
            imageRemoveWebURL.setOnClickListener(v -> {
                if (textWebURL != null) textWebURL.setText("");
                if (layoutWebURL != null) layoutWebURL.setVisibility(View.GONE);
                isNoteChanged = true;
            });
        }

        if (imageRemoveImage != null) {
            imageRemoveImage.setOnClickListener(v -> {
                resetImageData();
                isNoteChanged = true;
            });
        }
    }

    private void setupTextChangedListeners() {
        TextWatcher textWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { isNoteChanged = true; }
            @Override public void afterTextChanged(Editable s) {}
        };
        inputNoteTitle.addTextChangedListener(textWatcher);
        inputNoteSubtitle.addTextChangedListener(textWatcher);
        inputNote.addTextChangedListener(textWatcher);
    }

    private void handleBackPress() {
        boolean hasMeaningfulContentForNewNote = alreadyAvailableNote == null &&
                (!TextUtils.isEmpty(inputNoteTitle.getText().toString().trim()) ||
                        !TextUtils.isEmpty(inputNoteSubtitle.getText().toString().trim()) ||
                        !TextUtils.isEmpty(inputNote.getText().toString().trim()) ||
                        !selectedNoteColor.equals("#333333") ||
                        !selectedImagePath.isEmpty() ||
                        (layoutWebURL != null && layoutWebURL.getVisibility() == View.VISIBLE && textWebURL != null && !TextUtils.isEmpty(textWebURL.getText())));

        if (!isNoteChanged && (alreadyAvailableNote != null || !hasMeaningfulContentForNewNote)) {
            setResult(Activity.RESULT_CANCELED);
        }
        getOnBackPressedDispatcher().onBackPressed();
    }

    private void setViewOrUpdateNote() {
        if (alreadyAvailableNote == null) return;

        inputNoteTitle.setText(alreadyAvailableNote.getTitle());
        inputNoteSubtitle.setText(alreadyAvailableNote.getSubtitle());
        inputNote.setText(alreadyAvailableNote.getNoteText());
        if (textDateTime != null) textDateTime.setText(alreadyAvailableNote.getDateTime());

        if (alreadyAvailableNote.getImagePath() != null && !alreadyAvailableNote.getImagePath().trim().isEmpty()) {
            selectedImagePath = alreadyAvailableNote.getImagePath();
            File imgFile = new File(selectedImagePath);
            if(imgFile.exists() && imageNotePreview != null){
                Bitmap myBitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
                imageNotePreview.setImageBitmap(myBitmap);
                imageNotePreview.setVisibility(View.VISIBLE);
                if (imageRemoveImage != null) imageRemoveImage.setVisibility(View.VISIBLE);
            } else {
                if (imageNotePreview != null) imageNotePreview.setVisibility(View.GONE);
                if (imageRemoveImage != null) imageRemoveImage.setVisibility(View.GONE);
                selectedImagePath = "";
            }
        } else {
            resetImageData();
        }

        if (alreadyAvailableNote.getWebLink() != null && !alreadyAvailableNote.getWebLink().trim().isEmpty()) {
            if (textWebURL != null) textWebURL.setText(alreadyAvailableNote.getWebLink());
            if (layoutWebURL != null) layoutWebURL.setVisibility(View.VISIBLE);
        } else {
            if (layoutWebURL != null) layoutWebURL.setVisibility(View.GONE);
        }

        if (alreadyAvailableNote.getColor() != null && !alreadyAvailableNote.getColor().trim().isEmpty()) {
            selectedNoteColor = alreadyAvailableNote.getColor();
        }
        setSubtitleIndicatorColor();
        isNoteChanged = false;
    }

    private void setupActivityLaunchers() {
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) { selectImage(); }
                    else { Toast.makeText(this, "Quyền truy cập bộ nhớ bị từ chối!", Toast.LENGTH_SHORT).show(); }
                });

        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri sourceUri = result.getData().getData();
                        if (sourceUri != null) {
                            isNoteChanged = true;
                            String fileNameSuffix = "";
                            String mimeType = getContentResolver().getType(sourceUri);
                            if (mimeType != null) {
                                if (mimeType.contains("jpeg") || mimeType.contains("jpg")) fileNameSuffix = ".jpg";
                                else if (mimeType.contains("png")) fileNameSuffix = ".png";
                                else if (mimeType.contains("webp")) fileNameSuffix = ".webp";
                            } else {
                                String path = sourceUri.getPath();
                                if (path != null) {
                                    String lowerPath = path.toLowerCase();
                                    if (lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg")) fileNameSuffix = ".jpg";
                                    else if (lowerPath.endsWith(".png")) fileNameSuffix = ".png";
                                    else if (lowerPath.endsWith(".webp")) fileNameSuffix = ".webp";
                                }
                            }
                            if (fileNameSuffix.isEmpty()) fileNameSuffix = ".jpg";
                            String fileName = "note_image_" + System.currentTimeMillis() + fileNameSuffix;
                            String copiedImagePath = copyFileToInternalStorage(sourceUri, fileName);
                            if (copiedImagePath != null) {
                                // Cân nhắc xóa file ảnh cũ nếu người dùng chọn ảnh mới
                                // if (!selectedImagePath.isEmpty() && !selectedImagePath.equals(copiedImagePath)) {
                                //     new File(selectedImagePath).delete();
                                // }
                                selectedImagePath = copiedImagePath;
                                Bitmap bitmap = BitmapFactory.decodeFile(selectedImagePath);
                                if (imageNotePreview != null && bitmap != null) {
                                    imageNotePreview.setImageBitmap(bitmap);
                                    imageNotePreview.setVisibility(View.VISIBLE);
                                    if (imageRemoveImage != null) imageRemoveImage.setVisibility(View.VISIBLE);
                                } else { resetImageData(); }
                            } else { resetImageData(); }
                        }
                    }
                });
    }

    private void resetImageData() {
        selectedImagePath = "";
        if (imageNotePreview != null) {
            imageNotePreview.setImageBitmap(null);
            imageNotePreview.setVisibility(View.GONE);
        }
        if (imageRemoveImage != null) imageRemoveImage.setVisibility(View.GONE);
    }

    private String copyFileToInternalStorage(Uri sourceUri, String destinationFileName) {
        InputStream inputStream = null; OutputStream outputStream = null;
        try {
            File imageDir = new File(getFilesDir(), "images");
            if (!imageDir.exists() && !imageDir.mkdirs()) {
                Log.e(TAG, "Không thể tạo thư mục 'images'.");
                return null;
            }
            File destinationFile = new File(imageDir, destinationFileName);
            inputStream = getContentResolver().openInputStream(sourceUri);
            if (inputStream == null) { Log.e(TAG, "InputStream is null for URI: " + sourceUri); return null; }
            outputStream = new FileOutputStream(destinationFile);
            byte[] buffer = new byte[1024 * 4]; int length;
            while ((length = inputStream.read(buffer)) > 0) outputStream.write(buffer, 0, length);
            Log.d(TAG, "Ảnh đã sao chép vào: " + destinationFile.getAbsolutePath());
            return destinationFile.getAbsolutePath();
        } catch (Exception e) { Log.e(TAG, "Lỗi sao chép file: " + e.getMessage(), e); return null;
        } finally {
            try {
                if (inputStream != null) inputStream.close();
                if (outputStream != null) { outputStream.flush(); outputStream.close(); }
            } catch (IOException e) { Log.e(TAG, "Lỗi đóng streams: " + e.getMessage(), e); }
        }
    }

    private void setSubtitleIndicatorColor() {
        if (viewSubtitleIndicator != null && selectedNoteColor != null && !selectedNoteColor.isEmpty()) {
            try {
                int color = Color.parseColor(selectedNoteColor);
                if (viewSubtitleIndicator.getBackground() instanceof GradientDrawable) {
                    ((GradientDrawable) viewSubtitleIndicator.getBackground()).setColor(color);
                } else { viewSubtitleIndicator.setBackgroundColor(color); }
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Mã màu không hợp lệ cho indicator: " + selectedNoteColor, e);
                int defaultColor = Color.parseColor("#333333");
                if (viewSubtitleIndicator.getBackground() instanceof GradientDrawable) {
                    ((GradientDrawable) viewSubtitleIndicator.getBackground()).setColor(defaultColor);
                } else { viewSubtitleIndicator.setBackgroundColor(defaultColor); }
            }
        }
    }

    private void selectImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT); intent.setType("image/*");
        if (intent.resolveActivity(getPackageManager()) != null) { pickImageLauncher.launch(intent); }
        else { Toast.makeText(this, "Không tìm thấy ứng dụng để chọn ảnh.", Toast.LENGTH_SHORT).show(); }
    }

    private void saveNote() {
        final String noteTitle = inputNoteTitle.getText().toString().trim();
        final String noteSubtitle = inputNoteSubtitle.getText().toString().trim();
        final String noteContent = inputNote.getText().toString().trim();
        final String noteTime = textDateTime.getText().toString();

        if (TextUtils.isEmpty(noteTitle) && TextUtils.isEmpty(noteSubtitle) && TextUtils.isEmpty(noteContent) &&
                selectedImagePath.isEmpty() && (layoutWebURL == null || layoutWebURL.getVisibility() == View.GONE || textWebURL == null || TextUtils.isEmpty(textWebURL.getText()))) {
            Toast.makeText(this, "Ghi chú không thể hoàn toàn trống!", Toast.LENGTH_SHORT).show();
            return;
        }

        final Note note = new Note();
        note.setTitle(noteTitle); note.setSubtitle(noteSubtitle); note.setNoteText(noteContent);
        note.setDateTime(noteTime); note.setColor(selectedNoteColor);
        note.setImagePath(selectedImagePath.isEmpty() ? null : selectedImagePath);
        if (layoutWebURL != null && layoutWebURL.getVisibility() == View.VISIBLE && textWebURL != null && !TextUtils.isEmpty(textWebURL.getText())) {
            note.setWebLink(textWebURL.getText().toString());
        } else { note.setWebLink(null); }
        if (alreadyAvailableNote != null) { note.setId(alreadyAvailableNote.getId()); }

        executorService.execute(() -> {
            boolean success;
            try { NoteDatabase.getDatabase(getApplicationContext()).noteDao().insertNote(note); success = true; }
            catch (Exception e) { Log.e(TAG, "Lỗi lưu database: " + e.getMessage(), e); success = false; }
            final boolean finalSuccess = success;
            runOnUiThread(() -> {
                if (finalSuccess) {
                    Toast.makeText(CreateNoteActivity.this, "Đã lưu ghi chú!", Toast.LENGTH_SHORT).show();
                    Intent intentResult = new Intent();
                    intentResult.putExtra(EXTRA_NOTE_UPDATED_OR_ADDED, true);
                    setResult(Activity.RESULT_OK, intentResult);
                    finish();
                } else { Toast.makeText(CreateNoteActivity.this, "Lỗi khi lưu ghi chú!", Toast.LENGTH_SHORT).show(); }
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) executorService.shutdown();
        if (dialogAddURL != null && dialogAddURL.isShowing()) dialogAddURL.dismiss();
        if (dialogDeleteNote != null && dialogDeleteNote.isShowing()) dialogDeleteNote.dismiss();
    }

    private void initMiscellaneous() {
        final LinearLayout layoutMiscellaneousRoot = findViewById(R.id.layoutMiscellaneous);
        if (layoutMiscellaneousRoot == null) { Log.e(TAG, "CRITICAL: layoutMiscellaneous is null."); return; }

        layoutNoteColorOptions = layoutMiscellaneousRoot.findViewById(R.id.layoutNoteColor);
        layoutAddImageSection = layoutMiscellaneousRoot.findViewById(R.id.layoutAddImage);
        layoutAddUrlSection = layoutMiscellaneousRoot.findViewById(R.id.layoutAddUrl);
        layoutDeleteNoteSection = layoutMiscellaneousRoot.findViewById(R.id.layoutDeleteNote);

        bottomSheetBehavior = BottomSheetBehavior.from(layoutMiscellaneousRoot);
        if (bottomSheetBehavior == null) { Log.e(TAG, "CRITICAL: bottomSheetBehavior is null."); return; }

        bottomSheetBehavior.setPeekHeight(BottomSheetBehavior.PEEK_HEIGHT_AUTO, true);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        bottomSheetBehavior.setHideable(false);
        bottomSheetBehavior.setDraggable(true);

        if (layoutDeleteNoteSection != null) {
            if (alreadyAvailableNote != null) {
                layoutDeleteNoteSection.setVisibility(View.VISIBLE);
                layoutDeleteNoteSection.setOnClickListener(view -> {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                    showDeleteNoteDialog();
                });
            } else {
                layoutDeleteNoteSection.setVisibility(View.GONE);
            }
        } else { Log.w(TAG, "layoutDeleteNoteSection is null."); }

        LinearLayout[] expandableSections = {layoutNoteColorOptions, layoutAddImageSection, layoutAddUrlSection, layoutDeleteNoteSection};
        if (bottomSheetBehavior.getState() != BottomSheetBehavior.STATE_EXPANDED) {
            for (LinearLayout section : expandableSections) {
                if (section != null) {
                    if (section.getVisibility() == View.VISIBLE) {
                        section.setVisibility(View.GONE);
                    }
                    section.setAlpha(0f);
                }
            }
        } else {
            for (LinearLayout section : expandableSections) {
                if (section != null && section.getVisibility() == View.VISIBLE) {
                    section.setAlpha(1f);
                }
            }
        }

        TextView textMiscellaneousTitle = layoutMiscellaneousRoot.findViewById(R.id.textMiscellaneous);
        if (textMiscellaneousTitle != null) {
            textMiscellaneousTitle.setOnClickListener(view -> {
                Log.d(TAG, "textMiscellaneous clicked. Current state: " + bottomSheetBehavior.getState());
                if (bottomSheetBehavior.getState() != BottomSheetBehavior.STATE_EXPANDED) {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                    Log.d(TAG, "Setting state to EXPANDED");
                    hideKeyboard();
                } else {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                    Log.d(TAG, "Setting state to COLLAPSED");
                }
            });
        } else { Log.e(TAG, "CRITICAL: textMiscellaneousTitle is null."); }

        bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                Log.d(TAG, "BottomSheet state changed to: " + newState);
                LinearLayout[] sections = {layoutNoteColorOptions, layoutAddImageSection, layoutAddUrlSection, layoutDeleteNoteSection};
                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    for (LinearLayout section : sections) {
                        if (section != null) {
                            boolean shouldDisplaySection = true;
                            if (section == layoutDeleteNoteSection && alreadyAvailableNote == null) {
                                shouldDisplaySection = false;
                            }
                            if (shouldDisplaySection) {
                                section.setVisibility(View.VISIBLE);
                                section.animate().alpha(1f).setDuration(150).start();
                            } else {
                                section.setVisibility(View.GONE);
                                section.setAlpha(0f);
                            }
                        }
                    }
                } else if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    for (LinearLayout section : sections) {
                        if (section != null) {
                            section.animate().alpha(0f).setDuration(150)
                                    .withEndAction(() -> section.setVisibility(View.GONE))
                                    .start();
                        }
                    }
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                float effectiveAlpha = Math.max(0f, slideOffset);
                LinearLayout[] sections = {layoutNoteColorOptions, layoutAddImageSection, layoutAddUrlSection, layoutDeleteNoteSection};
                for (LinearLayout section : sections) {
                    if (section != null) {
                        boolean shouldDisplaySectionDuringSlide = true;
                        if (section == layoutDeleteNoteSection && alreadyAvailableNote == null) {
                            shouldDisplaySectionDuringSlide = false;
                        }
                        if (shouldDisplaySectionDuringSlide) {
                            if (effectiveAlpha > 0 && section.getVisibility() == View.GONE ) {
                                section.setVisibility(View.VISIBLE);
                            }
                            section.setAlpha(effectiveAlpha);
                            if (slideOffset == 0f && section.getVisibility() == View.VISIBLE) {
                                section.setVisibility(View.GONE);
                            }
                        } else {
                            section.setVisibility(View.GONE);
                            section.setAlpha(0f);
                        }
                    }
                }
            }
        });

        updateColorSelectionUI(selectedNoteColor);

        final View[] colorViews = {
                layoutMiscellaneousRoot.findViewById(R.id.viewColor1), layoutMiscellaneousRoot.findViewById(R.id.viewColor2),
                layoutMiscellaneousRoot.findViewById(R.id.viewColor3), layoutMiscellaneousRoot.findViewById(R.id.viewColor4),
                layoutMiscellaneousRoot.findViewById(R.id.viewColor5)
        };
        for (View colorView : colorViews) {
            if (colorView != null) {
                // Đảm bảo tag màu đã được set trong layout_miscellaneous.xml cho mỗi viewColorX
                colorView.setOnClickListener(view -> {
                    Object tag = view.getTag();
                    if (tag instanceof String && !((String) tag).trim().isEmpty()) {
                        String newColor = (String) tag;
                        if (!Objects.equals(selectedNoteColor, newColor)) { isNoteChanged = true; }
                        selectedNoteColor = newColor;
                        updateColorSelectionUI(selectedNoteColor);
                        setSubtitleIndicatorColor();
                    } else { Log.w(TAG, "View màu (ID: " + view.getId() + ") không có tag hợp lệ. Hãy đặt android:tag trong XML."); }
                });
            }
        }

        if (layoutAddImageSection != null) {
            layoutAddImageSection.setOnClickListener(view -> {
                if (bottomSheetBehavior != null) bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                hideKeyboard();
                String permissionToRequest;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissionToRequest = Manifest.permission.READ_MEDIA_IMAGES;
                else permissionToRequest = Manifest.permission.READ_EXTERNAL_STORAGE;
                if (ContextCompat.checkSelfPermission(this, permissionToRequest) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissionLauncher.launch(permissionToRequest);
                } else { selectImage(); }
            });
        }  else { Log.w(TAG, "layoutAddImageSection is null."); }


        if (layoutAddUrlSection != null) {
            layoutAddUrlSection.setOnClickListener(view -> {
                if (bottomSheetBehavior != null) bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                showAddURLDialog();
            });
        } else { Log.w(TAG, "layoutAddUrlSection is null."); }
    }

    private void updateColorSelectionUI(String activeColorCode) {
        LinearLayout layoutMiscellaneousRoot = findViewById(R.id.layoutMiscellaneous);
        if (layoutMiscellaneousRoot == null || activeColorCode == null) return;
        final ImageView[] checkImages = {
                layoutMiscellaneousRoot.findViewById(R.id.imageColor1), layoutMiscellaneousRoot.findViewById(R.id.imageColor2),
                layoutMiscellaneousRoot.findViewById(R.id.imageColor3), layoutMiscellaneousRoot.findViewById(R.id.imageColor4),
                layoutMiscellaneousRoot.findViewById(R.id.imageColor5)
        };
        final View[] colorViews = {
                layoutMiscellaneousRoot.findViewById(R.id.viewColor1), layoutMiscellaneousRoot.findViewById(R.id.viewColor2),
                layoutMiscellaneousRoot.findViewById(R.id.viewColor3), layoutMiscellaneousRoot.findViewById(R.id.viewColor4),
                layoutMiscellaneousRoot.findViewById(R.id.viewColor5)
        };
        for (int i = 0; i < colorViews.length; i++) {
            if (colorViews[i] != null && checkImages[i] != null) {
                Object tag = colorViews[i].getTag();
                if (tag instanceof String && activeColorCode.equalsIgnoreCase((String) tag)) {
                    checkImages[i].setVisibility(View.VISIBLE);
                } else { checkImages[i].setVisibility(View.GONE); }
            }
        }
    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void showAddURLDialog() {
        if (dialogAddURL == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            // SỬA: Truyền null cho parent khi inflate layout cho dialog
            View view = LayoutInflater.from(this).inflate(R.layout.layout_add_url, null);
            builder.setView(view);
            dialogAddURL = builder.create();
            if (dialogAddURL.getWindow() != null) dialogAddURL.getWindow().setBackgroundDrawable(new ColorDrawable(0));
            final EditText inputURL = view.findViewById(R.id.inputUrl);
            view.findViewById(R.id.textAdd).setOnClickListener(v -> {
                String url = inputURL.getText().toString().trim();
                if (url.isEmpty()) Toast.makeText(this, "URL không được để trống!", Toast.LENGTH_SHORT).show();
                else if (!Patterns.WEB_URL.matcher(url).matches()) Toast.makeText(this, "Nhập URL hợp lệ!", Toast.LENGTH_SHORT).show();
                else {
                    if (textWebURL != null && !Objects.equals(textWebURL.getText().toString(), url)) isNoteChanged = true;
                    if (textWebURL != null) textWebURL.setText(url);
                    if (layoutWebURL != null) layoutWebURL.setVisibility(View.VISIBLE);
                    dialogAddURL.dismiss();
                }
            });
            view.findViewById(R.id.textCancel).setOnClickListener(v -> dialogAddURL.dismiss());
        }
        EditText inputURL = dialogAddURL.findViewById(R.id.inputUrl); // Lấy lại tham chiếu để cập nhật nếu dialog đã có
        if (inputURL != null) {
            if (layoutWebURL != null && layoutWebURL.getVisibility() == View.VISIBLE && textWebURL != null && !TextUtils.isEmpty(textWebURL.getText())) {
                inputURL.setText(textWebURL.getText()); inputURL.setSelection(inputURL.getText().length());
            } else { inputURL.setText(""); }
            inputURL.requestFocus();
        }
        dialogAddURL.show();
    }

    private void showDeleteNoteDialog() {
        if (alreadyAvailableNote == null) return;
        if (dialogDeleteNote == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(CreateNoteActivity.this);
            // SỬA: Truyền null cho parent khi inflate layout cho dialog
            // Đảm bảo R.layout.layout_delete_note_container là tên file XML chính xác
            View view = LayoutInflater.from(this).inflate(
                    R.layout.layout_delete_note, // Sử dụng tên file XML bạn đã cung cấp cho dialog xóa
                    null // SỬA: Truyền null cho parent
            );
            builder.setView(view);
            dialogDeleteNote = builder.create();
            if (dialogDeleteNote.getWindow() != null) {
                dialogDeleteNote.getWindow().setBackgroundDrawable(new ColorDrawable(0));
            }
            TextView deleteButton = view.findViewById(R.id.textDeleteNote);
            TextView cancelButton = view.findViewById(R.id.textCancel);

            if (deleteButton != null) {
                deleteButton.setOnClickListener(v -> {
                    dialogDeleteNote.dismiss();
                    performDeleteNote();
                });
            } else { Log.e(TAG, "Delete button (textDeleteNote) not found in dialog layout"); }

            if (cancelButton != null) {
                cancelButton.setOnClickListener(v -> dialogDeleteNote.dismiss());
            } else { Log.e(TAG, "Cancel button (textCancel) not found in dialog layout"); }
        }
        dialogDeleteNote.show();
    }

    private void performDeleteNote() {
        if (alreadyAvailableNote == null) return;
        executorService.execute(() -> {
            NoteDatabase.getDatabase(getApplicationContext()).noteDao().deleteNote(alreadyAvailableNote);
            runOnUiThread(() -> {
                Toast.makeText(CreateNoteActivity.this, "Đã xóa ghi chú", Toast.LENGTH_SHORT).show();
                Intent intentResult = new Intent();
                intentResult.putExtra(EXTRA_IS_NOTE_DELETED, true);
                setResult(Activity.RESULT_OK, intentResult);
                finish();
            });
        });
    }
}